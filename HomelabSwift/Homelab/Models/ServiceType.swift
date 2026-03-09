import SwiftUI

public enum ServiceType: String, CaseIterable, Identifiable, Codable, Hashable, Sendable {
    case portainer
    case pihole
    case beszel
    case gitea

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .portainer: return "Portainer"
        case .pihole:    return "Pi-hole"
        case .beszel:    return "Beszel"
        case .gitea:     return "Gitea"
        }
    }

    @MainActor
    public var description: String {
        let t = Localizer.shared.t
        switch self {
        case .portainer: return t.servicePortainerDesc
        case .pihole:    return t.servicePiholeDesc
        case .beszel:    return t.serviceBeszelDesc
        case .gitea:     return t.serviceGiteaDesc
        }
    }

    public var symbolName: String {
        switch self {
        case .portainer: return "shippingbox.fill"
        case .pihole:    return "shield.fill"
        case .beszel:    return "server.rack"
        case .gitea:     return "arrow.triangle.branch"
        }
    }

    public var colors: ServiceColorSet {
        switch self {
        case .portainer: return ServiceColorSet(primary: Color(hex: "#13B5EA"), dark: Color(hex: "#0D8ECF"), bg: Color(hex: "#13B5EA").opacity(0.09))
        case .pihole:    return ServiceColorSet(primary: Color(hex: "#CD2326"), dark: Color(hex: "#9B1B1E"), bg: Color(hex: "#CD2326").opacity(0.09))
        case .beszel:    return ServiceColorSet(primary: Color(hex: "#0EA5E9"), dark: Color(hex: "#0284C7"), bg: Color(hex: "#0EA5E9").opacity(0.09))
        case .gitea:     return ServiceColorSet(primary: Color(hex: "#609926"), dark: Color(hex: "#4A7A1E"), bg: Color(hex: "#609926").opacity(0.09))
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
