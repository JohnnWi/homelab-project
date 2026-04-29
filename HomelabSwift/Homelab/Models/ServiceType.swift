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
    case dockmon
    case komodo
    case maltrail
    case uptimeKuma = "uptime_kuma"
    case craftyController = "crafty_controller"
    case unifiNetwork = "unifi_network"
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
    case wakapi
    case proxmox

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
        case "dockmon":
            return .dockmon
        case "komodo":
            return .komodo
        case "maltrail":
            return .maltrail
        case "uptimekuma", "uptime_kuma":
            return .uptimeKuma
        case "pangolin":
            return .pangolin
        case "crafty", "crafty_controller":
            return .craftyController
        case "unifi", "ubiquiti", "unifi_network", "unifinetwork":
            return .unifiNetwork
        case "proxmox", "proxmox_ve", "proxmoxve", "pve":
            return .proxmox
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
        .radarr,
        .sonarr,
        .lidarr,
        .qbittorrent,
        .jellyseerr,
        .prowlarr,
        .bazarr,
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
        case .dockmon:                 return "DockMon"
        case .komodo:                  return "Komodo"
        case .maltrail:                return "Maltrail"
        case .uptimeKuma:              return "Uptime Kuma"
        case .craftyController:        return "Crafty Controller"
        case .unifiNetwork:            return "Ubiquiti Network"
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
        case .wakapi:             return "Wakapi"
        case .proxmox:            return "Proxmox VE"
        }
    }

    func localizedDescription(using t: Translations) -> String {
        switch self {
        case .portainer:          return t.servicePortainerDesc
        case .pihole:             return t.servicePiholeDesc
        case .adguardHome:        return t.serviceAdguardDesc
        case .technitium:         return t.serviceTechnitiumDesc
        case .beszel:             return t.serviceBeszelDesc
        case .healthchecks:       return t.serviceHealthchecksDesc
        case .linuxUpdate:             return t.serviceLinuxUpdateDesc
        case .dockhand:                return t.serviceDockhandDesc
        case .dockmon:                 return t.serviceDockmonDesc
        case .komodo:                  return t.serviceKomodoDesc
        case .maltrail:                return t.serviceMaltrailDesc
        case .uptimeKuma:              return t.serviceUptimeKumaDesc
        case .craftyController:        return t.serviceCraftyControllerDesc
        case .unifiNetwork:            return t.serviceUnifiNetworkDesc
        case .gitea:              return t.serviceGiteaDesc
        case .nginxProxyManager:  return t.serviceNpmDesc
        case .pangolin:           return t.servicePangolinDesc
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
        case .wakapi:             return t.serviceWakapiDesc
        case .proxmox:            return t.serviceProxmoxDesc
        }
    }

    @MainActor
    public var description: String {
        localizedDescription(using: Translations.current())
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
        case .dockmon:                 return "arrow.triangle.2.circlepath.circle.fill"
        case .komodo:                  return "shippingbox.fill"
        case .maltrail:                return "network.badge.shield.half.filled"
        case .uptimeKuma:              return "heart.text.square.fill"
        case .craftyController:        return "gamecontroller.fill"
        case .unifiNetwork:            return "dot.radiowaves.left.and.right"
        case .gitea:              return "arrow.triangle.branch"
        case .nginxProxyManager:  return "globe"
        case .pangolin:           return "point.3.connected.trianglepath.dotted"
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
        case .wakapi:             return "timer"
        case .proxmox:            return "cpu"
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
        case .dockmon:                 return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/dockmon.png"
        case .komodo:                  return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/komodo.png"
        case .maltrail:                return "https://raw.githubusercontent.com/stamparm/maltrail/master/html/images/mlogo.png"
        case .uptimeKuma:              return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/uptime-kuma.png"
        case .craftyController:        return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/crafty-controller.png"
        case .unifiNetwork:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/ubiquiti-unifi.png"
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
        case .wakapi:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/wakapi.png"
        case .proxmox:            return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/proxmox.png"
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
        case .dockmon:                 slug = "dockmon"
        case .komodo:                  slug = "komodo"
        case .maltrail:                slug = "maltrail"
        case .uptimeKuma:              slug = "uptime-kuma"
        case .craftyController:        slug = "crafty-controller"
        case .unifiNetwork:            slug = "unifi"
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
        case .wakapi:             slug = "wakapi"
        case .proxmox:            slug = "proxmox"
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
        case .dockmon:                 return "service-dockmon"
        case .komodo:                  return "service-komodo"
        case .maltrail:                return "service-maltrail"
        case .uptimeKuma:              return "service-uptime-kuma"
        case .craftyController:        return "service-crafty-controller"
        case .unifiNetwork:            return "service-unifi"
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
        case .wakapi:             return "service-wakapi"
        case .proxmox:            return "service-proxmox"
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
        case .dockmon:                 return ServiceColorSet(primary: Color(hex: "#0EA5E9"), dark: Color(hex: "#0369A1"), bg: Color(hex: "#0EA5E9").opacity(0.09))
        case .komodo:                  return ServiceColorSet(primary: Color(hex: "#F97316"), dark: Color(hex: "#C2410C"), bg: Color(hex: "#F97316").opacity(0.08))
        case .maltrail:                return ServiceColorSet(primary: Color(hex: "#DC2626"), dark: Color(hex: "#991B1B"), bg: Color(hex: "#DC2626").opacity(0.08))
        case .uptimeKuma:              return ServiceColorSet(primary: Color(hex: "#22C55E"), dark: Color(hex: "#15803D"), bg: Color(hex: "#22C55E").opacity(0.09))
        case .craftyController:        return ServiceColorSet(primary: Color(hex: "#2E86FF"), dark: Color(hex: "#1E63C6"), bg: Color(hex: "#2E86FF").opacity(0.09))
        case .unifiNetwork:            return ServiceColorSet(primary: Color(hex: "#006FFF"), dark: Color(hex: "#0057D8"), bg: Color(hex: "#006FFF").opacity(0.09))
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
        case .wakapi:             return ServiceColorSet(primary: Color(hex: "#2563EB"), dark: Color(hex: "#1D4ED8"), bg: Color(hex: "#2563EB").opacity(0.09))
        case .proxmox:            return ServiceColorSet(primary: Color(hex: "#D97706"), dark: Color(hex: "#B45309"), bg: Color(hex: "#D97706").opacity(0.06))
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
