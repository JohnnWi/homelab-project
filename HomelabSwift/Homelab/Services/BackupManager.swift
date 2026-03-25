import Foundation

// MARK: - BackupManager

/// Orchestrates export and import of service configuration backups.
final class BackupManager: @unchecked Sendable {

    private let servicesStore: ServicesStore

    @MainActor
    init(servicesStore: ServicesStore) {
        self.servicesStore = servicesStore
    }

    // MARK: - Export

    /// Exports all service instances to an encrypted .homelab file.
    /// Returns the URL of the temporary file ready for sharing.
    func exportBackup(password: String) async throws -> URL {
        let envelope = await buildEnvelope()
        let jsonData = try JSONEncoder().encode(envelope)
        let encrypted = try BackupCrypto.encrypt(data: jsonData, password: password)

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

    /// Applies a backup envelope: replaces all existing service instances.
    @MainActor
    func applyBackup(_ envelope: BackupEnvelope) async {
        // Delete all existing instances
        for instance in servicesStore.allInstances {
            servicesStore.deleteInstance(id: instance.id)
        }

        // Track preferred instances per type
        var preferredByType: [ServiceType: UUID] = [:]

        // Import each service entry
        for entry in envelope.services {
            guard let instance = entry.toServiceInstance() else { continue }
            await servicesStore.saveInstance(instance)

            if entry.isPreferred {
                preferredByType[instance.type] = instance.id
            }
        }

        // Set preferred instances
        for (type, id) in preferredByType {
            servicesStore.setPreferredInstance(id: id, for: type)
        }
    }

    // MARK: - Private

    @MainActor
    private func buildEnvelope() -> BackupEnvelope {
        let preferredIds = servicesStore.preferredInstanceIdByType

        let entries: [BackupServiceEntry] = servicesStore.allInstances.map { instance in
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
