import Foundation

// MARK: - Backup Envelope

struct BackupEnvelope: Codable {
    let version: Int
    let exportedAt: String
    let appVersion: String
    let services: [BackupServiceEntry]

    static let currentVersion = 1
}

// MARK: - Backup Service Entry

struct BackupServiceEntry: Codable {
    let type: String
    let label: String
    let url: String
    let token: String?
    let username: String?
    let apiKey: String?
    let piholePassword: String?
    let piholeAuthMode: String?
    let fallbackUrl: String?
    let allowSelfSigned: Bool
    let password: String?
    let isPreferred: Bool
}

// MARK: - ServiceType ↔ Backup String Mapping

enum BackupServiceTypeMapper {

    /// Canonical lowercase backup key for a given ServiceType.
    static func backupKey(for type: ServiceType) -> String {
        switch type {
        case .portainer:         return "portainer"
        case .pihole:            return "pihole"
        case .adguardHome:       return "adguard_home"
        case .beszel:            return "beszel"
        case .healthchecks:      return "healthchecks"
        case .gitea:             return "gitea"
        case .nginxProxyManager: return "nginx_proxy_manager"
        case .patchmon:          return "patchmon"
        case .jellystat:         return "jellystat"
        case .plex:              return "plex"
        }
    }

    /// Resolve a backup key string back to a ServiceType, or nil if unknown.
    static func serviceType(from key: String) -> ServiceType? {
        let normalized = key.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        switch normalized {
        case "portainer":            return .portainer
        case "pihole":               return .pihole
        case "adguard_home",
             "adguardhome":          return .adguardHome
        case "beszel":               return .beszel
        case "healthchecks":         return .healthchecks
        case "gitea":                return .gitea
        case "nginx_proxy_manager",
             "nginxproxymanager":     return .nginxProxyManager
        case "patchmon":             return .patchmon
        case "jellystat":            return .jellystat
        case "plex":                 return .plex
        default:                     return nil
        }
    }

    /// PiHoleAuthMode string mapping.
    static func piholeAuthMode(from string: String?) -> PiHoleAuthMode? {
        guard let string else { return nil }
        switch string.lowercased() {
        case "session": return .session
        case "legacy":  return .legacy
        default:        return nil
        }
    }

    static func backupAuthMode(for mode: PiHoleAuthMode?) -> String? {
        guard let mode else { return nil }
        switch mode {
        case .session: return "session"
        case .legacy:  return "legacy"
        }
    }
}

// MARK: - Conversion Helpers

extension ServiceInstance {
    /// Create a BackupServiceEntry from this instance.
    func toBackupEntry(isPreferred: Bool) -> BackupServiceEntry {
        BackupServiceEntry(
            type: BackupServiceTypeMapper.backupKey(for: type),
            label: displayLabel,
            url: url,
            token: token.isEmpty ? nil : token,
            username: username,
            apiKey: apiKey,
            piholePassword: piholePassword,
            piholeAuthMode: BackupServiceTypeMapper.backupAuthMode(for: piholeAuthMode),
            fallbackUrl: fallbackUrl,
            allowSelfSigned: allowSelfSigned,
            password: password,
            isPreferred: isPreferred
        )
    }
}

extension BackupServiceEntry {
    /// Convert back to a ServiceInstance. Returns nil if the service type is unknown.
    func toServiceInstance() -> ServiceInstance? {
        guard let serviceType = BackupServiceTypeMapper.serviceType(from: type) else { return nil }
        return ServiceInstance(
            id: UUID(),
            type: serviceType,
            label: label,
            url: url,
            token: token ?? "",
            username: username,
            apiKey: apiKey,
            piholePassword: piholePassword,
            piholeAuthMode: BackupServiceTypeMapper.piholeAuthMode(from: piholeAuthMode),
            fallbackUrl: fallbackUrl,
            allowSelfSigned: allowSelfSigned,
            password: password
        )
    }
}
