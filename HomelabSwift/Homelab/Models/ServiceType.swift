import SwiftUI

public enum ServiceType: String, CaseIterable, Identifiable, Codable, Hashable, Sendable {
    case portainer
    case pihole
    case adguardHome
    case beszel
    case healthchecks
    case gitea
    case nginxProxyManager

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .portainer:          return "Portainer"
        case .pihole:             return "Pi-hole"
        case .adguardHome:        return "AdGuard Home"
        case .beszel:             return "Beszel"
        case .healthchecks:       return "Healthchecks"
        case .gitea:              return "Gitea"
        case .nginxProxyManager:  return "Nginx Proxy Manager"
        }
    }

    @MainActor
    public var description: String {
        let t = Localizer.shared.t
        switch self {
        case .portainer:          return t.servicePortainerDesc
        case .pihole:             return t.servicePiholeDesc
        case .adguardHome:        return t.serviceAdguardDesc
        case .beszel:             return t.serviceBeszelDesc
        case .healthchecks:       return t.serviceHealthchecksDesc
        case .gitea:              return t.serviceGiteaDesc
        case .nginxProxyManager:  return t.serviceNpmDesc
        }
    }

    public var symbolName: String {
        switch self {
        case .portainer:          return "shippingbox.fill"
        case .pihole:             return "shield.fill"
        case .adguardHome:        return "shield.lefthalf.filled"
        case .beszel:             return "server.rack"
        case .healthchecks:       return "heart.text.square.fill"
        case .gitea:              return "arrow.triangle.branch"
        case .nginxProxyManager:  return "globe"
        }
    }

    public var iconUrl: String {
        switch self {
        case .portainer:          return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/portainer.png"
        case .pihole:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/pi-hole.png"
        case .adguardHome:        return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/adguard-home.png"
        case .beszel:             return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/beszel.png"
        case .healthchecks:       return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/healthchecks.png"
        case .gitea:              return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/gitea.png"
        case .nginxProxyManager:  return "https://cdn.jsdelivr.net/gh/selfhst/icons/png/nginx-proxy-manager.png"
        }
    }

    public var iconCandidates: [URL] {
        let slug: String
        switch self {
        case .portainer:          slug = "portainer"
        case .pihole:             slug = "pi-hole"
        case .adguardHome:        slug = "adguard-home"
        case .beszel:             slug = "beszel"
        case .healthchecks:       slug = "healthchecks"
        case .gitea:              slug = "gitea"
        case .nginxProxyManager:  slug = "nginx-proxy-manager"
        }
        let urls = [
            "https://cdn.jsdelivr.net/gh/selfhst/icons/png/\(slug).png",
            "https://raw.githubusercontent.com/selfhst/icons/main/png/\(slug).png"
        ]
        return urls.compactMap(URL.init(string:))
    }

    public var colors: ServiceColorSet {
        switch self {
        case .portainer:          return ServiceColorSet(primary: Color(hex: "#13B5EA"), dark: Color(hex: "#0D8ECF"), bg: Color(hex: "#13B5EA").opacity(0.09))
        case .pihole:             return ServiceColorSet(primary: Color(hex: "#CD2326"), dark: Color(hex: "#9B1B1E"), bg: Color(hex: "#CD2326").opacity(0.09))
        case .adguardHome:        return ServiceColorSet(primary: Color(hex: "#68BC71"), dark: Color(hex: "#4C9A56"), bg: Color(hex: "#68BC71").opacity(0.09))
        case .beszel:             return ServiceColorSet(primary: Color(hex: "#8B5CF6"), dark: Color(hex: "#6D28D9"), bg: Color(hex: "#8B5CF6").opacity(0.09))
        case .healthchecks:       return ServiceColorSet(primary: Color(hex: "#16A34A"), dark: Color(hex: "#15803D"), bg: Color(hex: "#16A34A").opacity(0.09))
        case .gitea:              return ServiceColorSet(primary: Color(hex: "#609926"), dark: Color(hex: "#4A7A1E"), bg: Color(hex: "#609926").opacity(0.09))
        case .nginxProxyManager:  return ServiceColorSet(primary: Color(hex: "#F15B2A"), dark: Color(hex: "#C9481F"), bg: Color(hex: "#F15B2A").opacity(0.09))
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
