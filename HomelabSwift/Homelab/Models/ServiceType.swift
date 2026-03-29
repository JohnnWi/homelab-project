import SwiftUI

public enum ServiceType: String, CaseIterable, Identifiable, Codable, Hashable, Sendable {
    case portainer
    case pihole
    case adguardHome
    case technitium
    case beszel
    case healthchecks
    case linuxUpdate = "linux_update"
    case dockhand
    case gitea
    case nginxProxyManager
    case pangolin
    case patchmon
    case jellystat
    case plex
    case radarr
    case sonarr
    case lidarr
    case qbittorrent
    case jellyseerr
    case prowlarr
    case bazarr
    case gluetun
    case flaresolverr
    case jellyfin
    case immich
    case grafana
    case sabnzbd
    case proxmox
    case proxmoxBackupServer = "proxmox_backup_server"
    case tdarr

    public var id: String { rawValue }

    public static func fromStoredRawValue(_ rawValue: String) -> ServiceType? {
        if let direct = ServiceType(rawValue: rawValue) {
            return direct
        }

        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        if let caseInsensitive = ServiceType.allCases.first(where: {
            $0.rawValue.caseInsensitiveCompare(trimmed) == .orderedSame
        }) {
            return caseInsensitive
        }

        let normalized = trimmed
            .replacingOccurrences(of: "-", with: "_")
            .lowercased()

        switch normalized {
        case "linuxupdate", "linux_update":
            return .linuxUpdate
        case "technitium", "technitium_dns", "technitiumdns":
            return .technitium
        case "dockhand":
            return .dockhand
        case "pangolin":
            return .pangolin
        case "proxmox_backup_server", "proxmoxbackupserver", "pbs":
            return .proxmoxBackupServer
        default:
            return nil
        }
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        let rawValue = try container.decode(String.self)
        guard let mapped = ServiceType.fromStoredRawValue(rawValue) else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Unknown ServiceType raw value: \(rawValue)"
            )
        }
        self = mapped
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

    public static let mediaServices: [ServiceType] = [
        .jellyfin,
        .radarr,
        .sonarr,
        .lidarr,
        .qbittorrent,
        .sabnzbd,
        .jellyseerr,
        .prowlarr,
        .bazarr,
        .tdarr,
        .gluetun,
        .flaresolverr
    ]

    public static var homeServices: [ServiceType] {
        allCases.filter { !mediaServices.contains($0) }
    }

    public var isMediaService: Bool {
        Self.mediaServices.contains(self)
    }

    public var displayName: String {
        switch self {
        case .portainer:          return "Portainer"
        case .pihole:             return "Pi-hole"
        case .adguardHome:        return "AdGuard Home"
        case .technitium:         return "Technitium DNS"
        case .beszel:             return "Beszel"
        case .healthchecks:       return "Healthchecks"
        case .linuxUpdate:             return "Linux Update"
        case .dockhand:                return "Dockhand"
        case .gitea:              return "Gitea"
        case .nginxProxyManager:  return "Nginx Proxy Manager"
        case .pangolin:           return "Pangolin"
        case .patchmon:           return "PatchMon"
        case .jellystat:          return "Jellystat"
        case .plex:               return "Plex"
        case .radarr:             return "Radarr"
        case .sonarr:             return "Sonarr"
        case .lidarr:             return "Lidarr"
        case .qbittorrent:        return "qBittorrent"
        case .jellyseerr:         return "Jellyseerr"
        case .prowlarr:           return "Prowlarr"
        case .bazarr:             return "Bazarr"
        case .gluetun:            return "Gluetun"
        case .flaresolverr:       return "FlareSolverr"
        case .jellyfin:           return "Jellyfin"
        case .immich:             return "Immich"
        case .grafana:            return "Grafana"
        case .sabnzbd:            return "SABnzbd"
        case .proxmox:            return "Proxmox VE"
        case .proxmoxBackupServer: return "Proxmox Backup Server"
        case .tdarr:              return "Tdarr"
        }
    }

    @MainActor
    public var description: String {
        let t = Localizer.shared.t
        switch self {
        case .portainer:          return t.servicePortainerDesc
        case .pihole:             return t.servicePiholeDesc
        case .adguardHome:        return t.serviceAdguardDesc
        case .technitium:         return "Technitium DNS dashboard"
        case .beszel:             return t.serviceBeszelDesc
        case .healthchecks:       return t.serviceHealthchecksDesc
        case .linuxUpdate:             return "Linux Update dashboard"
        case .dockhand:                return "Docker management dashboard"
        case .gitea:              return t.serviceGiteaDesc
        case .nginxProxyManager:  return t.serviceNpmDesc
        case .pangolin:           return PangolinStrings.forLanguage(Localizer.shared.language).serviceDescription
        case .patchmon:           return t.servicePatchmonDesc
        case .jellystat:          return t.serviceJellystatDesc
        case .plex:               return t.servicePlexDesc
        case .radarr:             return t.serviceRadarrDesc
        case .sonarr:             return t.serviceSonarrDesc
        case .lidarr:             return t.serviceLidarrDesc
        case .qbittorrent:        return t.serviceQbittorrentDesc
        case .jellyseerr:         return t.serviceJellyseerrDesc
        case .prowlarr:           return t.serviceProwlarrDesc
        case .bazarr:             return t.serviceBazarrDesc
        case .gluetun:            return t.serviceGluetunDesc
        case .flaresolverr:       return t.serviceFlaresolverrDesc
        case .jellyfin:           return "Media server dashboard"
        case .immich:             return "Photo and video backup"
        case .grafana:            return "Monitoring and observability"
        case .sabnzbd:            return "Usenet download client"
        case .proxmox:            return "Virtualization management"
        case .proxmoxBackupServer: return "Backup server dashboard"
        case .tdarr:              return "Media transcoding manager"
        }
    }

    public var symbolName: String {
        switch self {
        case .portainer:          return "shippingbox.fill"
        case .pihole:             return "shield.fill"
        case .adguardHome:        return "shield.lefthalf.filled"
        case .technitium:         return "network.badge.shield.half.filled"
        case .beszel:             return "server.rack"
        case .healthchecks:       return "heart.text.square.fill"
        case .linuxUpdate:             return "chevron.left.forwardslash.chevron.right"
        case .dockhand:                return "shippingbox.circle.fill"
        case .gitea:              return "arrow.triangle.branch"
        case .nginxProxyManager:  return "globe"
        case .pangolin:           return "tunnel.fill"
        case .patchmon:           return "shippingbox.circle.fill"
        case .jellystat:          return "chart.line.uptrend.xyaxis"
        case .plex:               return "play.tv"
        case .radarr:             return "film.fill"
        case .sonarr:             return "tv.fill"
        case .lidarr:             return "music.note.list"
        case .qbittorrent:        return "arrow.down.circle.fill"
        case .jellyseerr:         return "star.fill"
        case .prowlarr:           return "magnifyingglass.circle.fill"
        case .bazarr:             return "text.bubble.fill"
        case .gluetun:            return "lock.shield.fill"
        case .flaresolverr:       return "flame.fill"
        case .jellyfin:           return "play.rectangle.fill"
        case .immich:             return "photo.on.rectangle.angled"
        case .grafana:            return "chart.bar.xaxis"
        case .sabnzbd:            return "arrow.down.doc.fill"
        case .proxmox:            return "cpu.fill"
        case .proxmoxBackupServer: return "externaldrive.fill.badge.checkmark"
        case .tdarr:              return "waveform.path"
        }
    }

    public var iconUrl: String {
        switch self {
        case .portainer:          return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/portainer.png"
        case .pihole:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/pi-hole.png"
        case .adguardHome:        return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/adguard-home.png"
        case .technitium:         return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/technitium.png"
        case .beszel:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/beszel.png"
        case .healthchecks:       return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/healthchecks.png"
        case .linuxUpdate:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/linux-update-dashboard.png"
        case .dockhand:                return "https://dockhand.pro/favicon.ico"
        case .gitea:              return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/gitea.png"
        case .nginxProxyManager:  return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/nginx-proxy-manager.png"
        case .pangolin:           return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/pangolin.png"
        case .patchmon:           return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/patchmon.png"
        case .jellystat:          return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/jellystat.png"
        case .plex:               return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/plex.png"
        case .radarr:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/radarr.png"
        case .sonarr:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/sonarr.png"
        case .lidarr:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/lidarr.png"
        case .qbittorrent:        return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/qbittorrent.png"
        case .jellyseerr:         return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/jellyseerr.png"
        case .prowlarr:           return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/prowlarr.png"
        case .bazarr:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/bazarr.png"
        case .gluetun:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/gluetun.png"
        case .flaresolverr:       return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/flaresolverr.png"
        case .jellyfin:           return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/jellyfin.png"
        case .immich:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/immich.png"
        case .grafana:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/grafana.png"
        case .sabnzbd:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/sabnzbd.png"
        case .proxmox:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/proxmox.png"
        case .proxmoxBackupServer: return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/proxmox-backup-server.png"
        case .tdarr:              return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/tdarr.png"
        }
    }

    public var iconCandidates: [URL] {
        let slug: String
        switch self {
        case .portainer:          slug = "portainer"
        case .pihole:             slug = "pi-hole"
        case .adguardHome:        slug = "adguard-home"
        case .technitium:         slug = "technitium"
        case .beszel:             slug = "beszel"
        case .healthchecks:       slug = "healthchecks"
        case .linuxUpdate:             slug = "linux-update"
        case .dockhand:                slug = "dockhand"
        case .gitea:              slug = "gitea"
        case .nginxProxyManager:  slug = "nginx-proxy-manager"
        case .pangolin:           slug = "pangolin"
        case .patchmon:           slug = "patchmon"
        case .jellystat:          slug = "jellystat"
        case .plex:               slug = "plex"
        case .radarr:             slug = "radarr"
        case .sonarr:             slug = "sonarr"
        case .lidarr:             slug = "lidarr"
        case .qbittorrent:        slug = "qbittorrent"
        case .jellyseerr:         slug = "jellyseerr"
        case .prowlarr:           slug = "prowlarr"
        case .bazarr:             slug = "bazarr"
        case .gluetun:            slug = "gluetun"
        case .flaresolverr:       slug = "flaresolverr"
        case .jellyfin:           slug = "jellyfin"
        case .immich:             slug = "immich"
        case .grafana:            slug = "grafana"
        case .sabnzbd:            slug = "sabnzbd"
        case .proxmox:            slug = "proxmox"
        case .proxmoxBackupServer: slug = "proxmox-backup-server"
        case .tdarr:              slug = "tdarr"
        }
        var orderedCandidates: [String] = []
        let primary = iconUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        if !primary.isEmpty {
            orderedCandidates.append(primary)
        }
        orderedCandidates.append("https://cdn.jsdelivr.net/gh/selfhst/icons/png/\(slug).png")
        orderedCandidates.append("https://raw.githubusercontent.com/selfhst/icons/main/png/\(slug).png")
        if self == .technitium {
            orderedCandidates.append("https://cdn.jsdelivr.net/gh/selfhst/icons/png/technitium-dns-server.png")
            orderedCandidates.append("https://raw.githubusercontent.com/selfhst/icons/main/png/technitium-dns-server.png")
        }

        var seen = Set<String>()
        let deduped = orderedCandidates.filter { seen.insert($0).inserted }
        return deduped.compactMap(URL.init(string:))
    }

    public var localIconAssetName: String {
        switch self {
        case .portainer:          return "service-portainer"
        case .pihole:             return "service-pi-hole"
        case .adguardHome:        return "service-adguard-home"
        case .technitium:         return "service-technitium-dns-server"
        case .beszel:             return "service-beszel"
        case .healthchecks:       return "service-healthchecks"
        case .linuxUpdate:             return "service-linux-update"
        case .dockhand:                return "service-dockhand"
        case .gitea:              return "service-gitea"
        case .nginxProxyManager:  return "service-nginx-proxy-manager"
        case .pangolin:           return "service-pangolin"
        case .patchmon:           return "service-patchmon"
        case .jellystat:          return "service-jellystat"
        case .plex:               return "service-plex"
        case .radarr:             return "service-radarr"
        case .sonarr:             return "service-sonarr"
        case .lidarr:             return "service-lidarr"
        case .qbittorrent:        return "service-qbittorrent"
        case .jellyseerr:         return "service-jellyseerr"
        case .prowlarr:           return "service-prowlarr"
        case .bazarr:             return "service-bazarr"
        case .gluetun:            return "service-gluetun"
        case .flaresolverr:       return "service-flaresolverr"
        case .jellyfin:           return "service-jellyfin"
        case .immich:             return "service-immich"
        case .grafana:            return "service-grafana"
        case .sabnzbd:            return "service-sabnzbd"
        case .proxmox:            return "service-proxmox"
        case .proxmoxBackupServer: return "service-proxmox-backup-server"
        case .tdarr:              return "service-tdarr"
        }
    }

    public var colors: ServiceColorSet {
        switch self {
        case .portainer:          return ServiceColorSet(primary: Color(hex: "#13B5EA"), dark: Color(hex: "#0D8ECF"), bg: Color(hex: "#13B5EA").opacity(0.09))
        case .pihole:             return ServiceColorSet(primary: Color(hex: "#CD2326"), dark: Color(hex: "#9B1B1E"), bg: Color(hex: "#CD2326").opacity(0.09))
        case .adguardHome:        return ServiceColorSet(primary: Color(hex: "#68BC71"), dark: Color(hex: "#4C9A56"), bg: Color(hex: "#68BC71").opacity(0.09))
        case .technitium:         return ServiceColorSet(primary: Color(hex: "#2D9CDB"), dark: Color(hex: "#1D74A6"), bg: Color(hex: "#2D9CDB").opacity(0.09))
        case .beszel:             return ServiceColorSet(primary: Color(hex: "#8B5CF6"), dark: Color(hex: "#6D28D9"), bg: Color(hex: "#8B5CF6").opacity(0.09))
        case .healthchecks:       return ServiceColorSet(primary: Color(hex: "#16A34A"), dark: Color(hex: "#15803D"), bg: Color(hex: "#16A34A").opacity(0.09))
        case .linuxUpdate:             return ServiceColorSet(primary: Color(hex: "#14B8A6"), dark: Color(hex: "#0F766E"), bg: Color(hex: "#14B8A6").opacity(0.09))
        case .dockhand:                return ServiceColorSet(primary: Color(hex: "#1E88E5"), dark: Color(hex: "#1565C0"), bg: Color(hex: "#1E88E5").opacity(0.09))
        case .gitea:              return ServiceColorSet(primary: Color(hex: "#609926"), dark: Color(hex: "#4A7A1E"), bg: Color(hex: "#609926").opacity(0.09))
        case .nginxProxyManager:  return ServiceColorSet(primary: Color(hex: "#F15B2A"), dark: Color(hex: "#C9481F"), bg: Color(hex: "#F15B2A").opacity(0.09))
        case .pangolin:           return ServiceColorSet(primary: Color(hex: "#FF8A3D"), dark: Color(hex: "#D96A22"), bg: Color(hex: "#FF8A3D").opacity(0.10))
        case .patchmon:           return ServiceColorSet(primary: Color(hex: "#2563EB"), dark: Color(hex: "#1D4ED8"), bg: Color(hex: "#2563EB").opacity(0.09))
        case .jellystat:          return ServiceColorSet(primary: Color(hex: "#C93DF6"), dark: Color(hex: "#A92ED0"), bg: Color(hex: "#C93DF6").opacity(0.11))
        case .plex:               return ServiceColorSet(primary: Color(hex: "#E5A00D"), dark: Color(hex: "#CC8E0A"), bg: Color(hex: "#E5A00D").opacity(0.09))
        case .radarr:             return ServiceColorSet(primary: Color(hex: "#FFC230"), dark: Color(hex: "#E5A00D"), bg: Color(hex: "#FFC230").opacity(0.09))
        case .sonarr:             return ServiceColorSet(primary: Color(hex: "#89C5CF"), dark: Color(hex: "#0084A1"), bg: Color(hex: "#89C5CF").opacity(0.09))
        case .lidarr:             return ServiceColorSet(primary: Color(hex: "#006B3E"), dark: Color(hex: "#004B2B"), bg: Color(hex: "#006B3E").opacity(0.09))
        case .qbittorrent:        return ServiceColorSet(primary: Color(hex: "#2C86C1"), dark: Color(hex: "#1B5D8B"), bg: Color(hex: "#2C86C1").opacity(0.09))
        case .jellyseerr:         return ServiceColorSet(primary: Color(hex: "#6C63FF"), dark: Color(hex: "#5548CC"), bg: Color(hex: "#6C63FF").opacity(0.09))
        case .prowlarr:           return ServiceColorSet(primary: Color(hex: "#F97316"), dark: Color(hex: "#C95712"), bg: Color(hex: "#F97316").opacity(0.09))
        case .bazarr:             return ServiceColorSet(primary: Color(hex: "#2563EB"), dark: Color(hex: "#1D4ED8"), bg: Color(hex: "#2563EB").opacity(0.09))
        case .gluetun:            return ServiceColorSet(primary: Color(hex: "#06B6D4"), dark: Color(hex: "#0891B2"), bg: Color(hex: "#06B6D4").opacity(0.09))
        case .flaresolverr:       return ServiceColorSet(primary: Color(hex: "#FF4500"), dark: Color(hex: "#CC3700"), bg: Color(hex: "#FF4500").opacity(0.09))
        case .jellyfin:           return ServiceColorSet(primary: Color(hex: "#00A4DC"), dark: Color(hex: "#0082B0"), bg: Color(hex: "#00A4DC").opacity(0.09))
        case .immich:             return ServiceColorSet(primary: Color(hex: "#4250AF"), dark: Color(hex: "#333F8C"), bg: Color(hex: "#4250AF").opacity(0.09))
        case .grafana:            return ServiceColorSet(primary: Color(hex: "#F46800"), dark: Color(hex: "#C35300"), bg: Color(hex: "#F46800").opacity(0.09))
        case .sabnzbd:            return ServiceColorSet(primary: Color(hex: "#EAB12B"), dark: Color(hex: "#C89422"), bg: Color(hex: "#EAB12B").opacity(0.09))
        case .proxmox:            return ServiceColorSet(primary: Color(hex: "#E57000"), dark: Color(hex: "#B85A00"), bg: Color(hex: "#E57000").opacity(0.09))
        case .proxmoxBackupServer: return ServiceColorSet(primary: Color(hex: "#E57000"), dark: Color(hex: "#B85A00"), bg: Color(hex: "#E57000").opacity(0.09))
        case .tdarr:              return ServiceColorSet(primary: Color(hex: "#6366F1"), dark: Color(hex: "#4F46E5"), bg: Color(hex: "#6366F1").opacity(0.09))
        }
    }
}

public struct ServiceColorSet {
    public let primary: Color
    public let dark: Color
    public let bg: Color

    public init(primary: Color, dark: Color, bg: Color) {
        self.primary = primary
        self.dark = dark
        self.bg = bg
    }
}
