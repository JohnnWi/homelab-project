import Foundation

// MARK: - Backup Import Result

enum BackupImportResult {
    case success(count: Int)
    case failure(message: String)
}

// MARK: - BackupManager

/// Orchestrates export and import of service configuration backups.
/// All public methods must be called on the main actor because they
/// interact with ServicesStore, which is @MainActor.
@MainActor
final class BackupManager {

    private let servicesStore: ServicesStore

    @MainActor
    init(servicesStore: ServicesStore) {
        self.servicesStore = servicesStore
    }

    // MARK: - Export

    /// Exports service instances to an encrypted .homelab file.
    /// Returns the URL of the temporary file ready for sharing.
    func exportBackup(password: String, includedTypes: Set<ServiceType>? = nil) async throws -> URL {
        let envelope = buildEnvelope(includedTypes: includedTypes)
        let encrypted = try await Task.detached {
            let jsonData = try JSONEncoder().encode(envelope)
            return try BackupCrypto.encrypt(data: jsonData, password: password)
        }.value

        let dateFormatter = DateFormatter()
        dateFormatter.dateFormat = "yyyy-MM-dd_HHmmss"
        let timestamp = dateFormatter.string(from: Date())
        let filename = "homelab_backup_\(timestamp).homelab"

        let tempDir = FileManager.default.temporaryDirectory
        let fileURL = tempDir.appendingPathComponent(filename)

        // Clean up any previous file with the same name
        try? FileManager.default.removeItem(at: fileURL)
        try encrypted.write(to: fileURL)

        return fileURL
    }

    // MARK: - Preview (decrypt + parse without applying)

    /// Decrypts and parses a .homelab file, returning the envelope for preview.
    nonisolated func previewBackup(from url: URL, password: String) throws -> BackupPreviewResult {
        let data = try Data(contentsOf: url)
        let decrypted = try BackupCrypto.decrypt(data: data, password: password)

        let decoder = JSONDecoder()
        let envelope = try decoder.decode(BackupEnvelope.self, from: decrypted)

        // Separate known vs unknown service types
        var knownEntries: [BackupServiceEntry] = []
        var unknownTypes: [String] = []

        for entry in envelope.services {
            if BackupServiceTypeMapper.serviceType(from: entry.type) != nil {
                knownEntries.append(entry)
            } else {
                unknownTypes.append(entry.type)
            }
        }

        return BackupPreviewResult(
            envelope: envelope,
            knownServices: knownEntries,
            unknownServiceTypes: unknownTypes,
            totalCount: envelope.services.count
        )
    }

    // MARK: - Apply

    /// Applies selected service types from a backup envelope and leaves other services untouched.
    /// Uses a validate-then-commit pattern: if any entry cannot be converted to a valid
    /// ServiceInstance, the entire import is aborted without modifying the store.
    @MainActor
    func applyBackup(
        _ envelope: BackupEnvelope,
        includedTypes: Set<ServiceType>
    ) async -> BackupImportResult {
        guard !includedTypes.isEmpty else { return .success(count: 0) }

        let entriesByType = envelope.services
            .compactMap { entry -> (ServiceType, BackupServiceEntry)? in
                guard let type = BackupServiceTypeMapper.serviceType(from: entry.type),
                      includedTypes.contains(type) else {
                    return nil
                }
                return (type, entry)
            }
            .reduce(into: [ServiceType: [BackupServiceEntry]]()) { partial, element in
                partial[element.0, default: []].append(element.1)
            }

        let typesToReplace = Set(entriesByType.keys)
        guard !typesToReplace.isEmpty else { return .success(count: 0) }

        // MARK: - Phase 1: Validate all entries before touching the store
        var instancesToImport: [(type: ServiceType, instance: ServiceInstance, isPreferred: Bool)] = []
        var validationErrors: [String] = []

        for type in typesToReplace.sorted(by: { $0.rawValue < $1.rawValue }) {
            let entries = entriesByType[type] ?? []
            for entry in entries {
                guard let instance = entry.toServiceInstance() else {
                    validationErrors.append("Invalid service entry for \(type.displayName)")
                    continue
                }
                instancesToImport.append((type: type, instance: instance, isPreferred: entry.isPreferred))
            }
        }

        if !validationErrors.isEmpty {
            return .failure(message: "Cannot import: \(validationErrors.joined(separator: "; "))")
        }

        guard !instancesToImport.isEmpty else { return .success(count: 0) }

        // MARK: - Phase 2: Save previous state for potential rollback
        let previousInstances = servicesStore.allInstances
            .filter { typesToReplace.contains($0.type) }
            .map { ($0.id, $0.type) }

        // MARK: - Phase 3: Delete old instances
        for (id, _) in previousInstances {
            servicesStore.deleteInstance(id: id)
        }

        // MARK: - Phase 4: Import new instances
        var importedCount = 0
        var preferredIdsByType: [ServiceType: UUID] = [:]

        for item in instancesToImport {
            await servicesStore.saveInstance(item.instance, triggerReachabilityCheck: false)
            importedCount += 1
            if item.isPreferred {
                preferredIdsByType[item.type] = item.instance.id
            }
        }

        // Apply preferred instance IDs
        for (type, id) in preferredIdsByType {
            servicesStore.setPreferredInstance(id: id, for: type)
        }

        await servicesStore.checkAllReachability(force: true)
        return .success(count: importedCount)
    }

    // MARK: - Private

    @MainActor
    private func buildEnvelope(includedTypes: Set<ServiceType>? = nil) -> BackupEnvelope {
        let preferredIds = servicesStore.preferredInstanceIdByType
        let included = includedTypes ?? []
        let instancesToExport: [ServiceInstance]
        if included.isEmpty {
            instancesToExport = servicesStore.allInstances
        } else {
            instancesToExport = servicesStore.allInstances.filter { included.contains($0.type) }
        }

        let entries: [BackupServiceEntry] = instancesToExport.map { instance in
            let isPreferred = preferredIds[instance.type] == instance.id
            return instance.toBackupEntry(isPreferred: isPreferred)
        }

        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.0.0"

        let dateFormatter = ISO8601DateFormatter()
        let now = dateFormatter.string(from: Date())

        return BackupEnvelope(
            version: BackupEnvelope.currentVersion,
            exportedAt: now,
            appVersion: version,
            services: entries
        )
    }
}

// MARK: - Preview Result

struct BackupPreviewResult {
    let envelope: BackupEnvelope
    let knownServices: [BackupServiceEntry]
    let unknownServiceTypes: [String]
    let totalCount: Int

    var knownCount: Int { knownServices.count }
    var unknownCount: Int { unknownServiceTypes.count }

    var knownServiceTypes: Set<ServiceType> {
        Set(knownServices.compactMap { BackupServiceTypeMapper.serviceType(from: $0.type) })
    }

    /// Group known services by type for display.
    var servicesByType: [(type: String, displayName: String, count: Int)] {
        var grouped: [String: Int] = [:]
        for entry in knownServices {
            grouped[entry.type, default: 0] += 1
        }
        return grouped
            .sorted { $0.key < $1.key }
            .compactMap { key, count in
                guard let serviceType = BackupServiceTypeMapper.serviceType(from: key) else { return nil }
                return (type: key, displayName: serviceType.displayName, count: count)
            }
    }
}
