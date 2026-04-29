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
    let proxmoxAuthMode: String?
    let proxmoxRealm: String?
    let unifiAuthMode: String?
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
        case .technitium:        return "technitium"
        case .beszel:            return "beszel"
        case .healthchecks:      return "healthchecks"
        case .linuxUpdate:            return "linux_update"
        case .dockhand:               return "dockhand"
        case .dockmon:                return "dockmon"
        case .komodo:                 return "komodo"
        case .maltrail:               return "maltrail"
        case .uptimeKuma:             return "uptime_kuma"
        case .craftyController:       return "crafty_controller"
        case .unifiNetwork:           return "unifi_network"
        case .gitea:             return "gitea"
        case .nginxProxyManager: return "nginx_proxy_manager"
        case .pangolin:          return "pangolin"
        case .patchmon:          return "patchmon"
        case .jellystat:         return "jellystat"
        case .plex:              return "plex"
        case .radarr:            return "radarr"
        case .sonarr:            return "sonarr"
        case .lidarr:            return "lidarr"
        case .qbittorrent:       return "qbittorrent"
        case .jellyseerr:        return "jellyseerr"
        case .prowlarr:          return "prowlarr"
        case .bazarr:            return "bazarr"
        case .gluetun:           return "gluetun"
        case .flaresolverr:      return "flaresolverr"
        case .wakapi:            return "wakapi"
        case .proxmox:           return "proxmox"
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
        case "technitium",
             "technitium_dns",
             "technitium-dns":       return .technitium
        case "beszel":               return .beszel
        case "healthchecks":         return .healthchecks
        case "linux_update",
             "linuxupdate",
             "linux-update":          return .linuxUpdate
        case "dockhand":              return .dockhand
        case "dockmon":               return .dockmon
        case "komodo":                return .komodo
        case "maltrail":              return .maltrail
        case "uptime_kuma",
             "uptime-kuma",
             "uptimekuma":            return .uptimeKuma
        case "crafty_controller",
             "crafty-controller",
             "crafty":                return .craftyController
        case "unifi_network",
             "unifi-network",
             "unifinetwork",
             "unifi",
             "ubiquiti":              return .unifiNetwork
        case "gitea":                return .gitea
        case "nginx_proxy_manager",
             "nginxproxymanager":     return .nginxProxyManager
        case "pangolin":             return .pangolin
        case "patchmon":             return .patchmon
        case "jellystat":            return .jellystat
        case "plex":                 return .plex
        case "radarr":               return .radarr
        case "sonarr":               return .sonarr
        case "lidarr":               return .lidarr
        case "qbittorrent":          return .qbittorrent
        case "jellyseerr":           return .jellyseerr
        case "prowlarr":             return .prowlarr
        case "bazarr":               return .bazarr
        case "gluetun":              return .gluetun
        case "flaresolverr":         return .flaresolverr
        case "wakapi":               return .wakapi
        case "proxmox",
             "proxmox_ve",
             "pve":                  return .proxmox
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

    static func proxmoxAuthMode(from string: String?) -> ProxmoxAuthMode? {
        guard let string else { return nil }
        switch string.lowercased() {
        case "credentials": return .credentials
        case "api_token", "apitoken": return .apiToken
        default: return nil
        }
    }

    static func backupAuthMode(for mode: ProxmoxAuthMode?) -> String? {
        guard let mode else { return nil }
        switch mode {
        case .credentials: return "credentials"
        case .apiToken: return "api_token"
        }
    }

    static func unifiAuthMode(from string: String?) -> UniFiAuthMode? {
        guard let string else { return nil }
        switch string.lowercased() {
        case "site_manager", "sitemanager", "cloud": return .siteManager
        case "local_network", "localnetwork", "network", "local": return .localNetwork
        default: return nil
        }
    }

    static func backupAuthMode(for mode: UniFiAuthMode?) -> String? {
        guard let mode else { return nil }
        return mode.rawValue
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
            proxmoxAuthMode: BackupServiceTypeMapper.backupAuthMode(for: proxmoxAuthMode),
            proxmoxRealm: proxmoxRealm,
            unifiAuthMode: BackupServiceTypeMapper.backupAuthMode(for: unifiAuthMode),
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
            proxmoxAuthMode: BackupServiceTypeMapper.proxmoxAuthMode(from: proxmoxAuthMode),
            proxmoxRealm: proxmoxRealm,
            unifiAuthMode: BackupServiceTypeMapper.unifiAuthMode(from: unifiAuthMode),
            fallbackUrl: fallbackUrl,
            allowSelfSigned: allowSelfSigned,
            password: password
        )
    }
}
