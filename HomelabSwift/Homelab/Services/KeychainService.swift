import Foundation
import Security

enum KeychainService {
    private static let service = "com.homelab.homelab.services"
    private static let legacyConnectionsAccount = "homelab_user"
    private static let serviceStateV2Account = "homelab_service_state_v2"
    private static let pinAccount = "homelab_pin"
    nonisolated(unsafe) static var backend: any KeychainBackend = SecurityKeychainBackend()

    static func saveServiceState(_ state: ServiceStateV2) {
        guard let data = try? JSONEncoder().encode(state) else { return }
        backend.save(data: data, service: service, account: serviceStateV2Account)
    }

    static func loadServiceState() -> ServiceStateV2 {
        if let data = backend.load(service: service, account: serviceStateV2Account),
           let state = try? JSONDecoder().decode(ServiceStateV2.self, from: data) {
            return state
        }

        let legacyConnections = loadLegacyConnections()
        guard !legacyConnections.isEmpty else { return .empty }

        var preferredByType: [ServiceType: UUID] = [:]
        let instances = legacyConnections
            .sorted { $0.key.rawValue < $1.key.rawValue }
            .map { type, connection -> ServiceInstance in
                let migrated = connection.migratedInstance()
                preferredByType[type] = migrated.id
                return migrated
            }

        let migratedState = ServiceStateV2(instances: instances, preferredInstanceIdByType: preferredByType)
        saveServiceState(migratedState)
        if let saved = backend.load(service: service, account: serviceStateV2Account),
           (try? JSONDecoder().decode(ServiceStateV2.self, from: saved)) != nil {
            backend.delete(service: service, account: legacyConnectionsAccount)
        }
        return migratedState
    }

    static func deleteAll() {
        backend.delete(service: service, account: serviceStateV2Account)
        backend.delete(service: service, account: legacyConnectionsAccount)
    }

    // MARK: - PIN Storage

    static func savePin(_ pin: String) {
        guard let data = pin.data(using: .utf8) else { return }
        backend.save(data: data, service: service, account: pinAccount)
    }

    static func loadPin() -> String? {
        guard let data = backend.load(service: service, account: pinAccount),
              let pin = String(data: data, encoding: .utf8)
        else { return nil }

        return pin
    }

    static func deletePin() {
        backend.delete(service: service, account: pinAccount)
    }

    private static func loadLegacyConnections() -> [ServiceType: ServiceConnection] {
        guard let data = backend.load(service: service, account: legacyConnectionsAccount),
              let connections = try? JSONDecoder().decode([ServiceType: ServiceConnection].self, from: data)
        else { return [:] }

        return connections
    }
}

protocol KeychainBackend: Sendable {
    func save(data: Data, service: String, account: String)
    func load(service: String, account: String) -> Data?
    func delete(service: String, account: String)
}

struct SecurityKeychainBackend: KeychainBackend {
    func save(data: Data, service: String, account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlocked
        ]
        SecItemDelete(query as CFDictionary)

        var addQuery = query
        addQuery[kSecValueData as String] = data
        SecItemAdd(addQuery as CFDictionary, nil)
    }

    func load(service: String, account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlocked,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess else { return nil }
        return result as? Data
    }

    func delete(service: String, account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlocked
        ]
        SecItemDelete(query as CFDictionary)
    }
}
