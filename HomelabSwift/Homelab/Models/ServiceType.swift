import SwiftUI

enum ServiceType: String, CaseIterable, Identifiable, Codable, Hashable {
    case portainer
    case pihole
    case beszel
    case gitea

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .portainer: return "Portainer"
        case .pihole:    return "Pi-hole"
        case .beszel:    return "Beszel"
        case .gitea:     return "Gitea"
        }
    }

    var description: String {
        switch self {
        case .portainer: return "Docker container management"
        case .pihole:    return "Network-wide ad blocking"
        case .beszel:    return "Server monitoring"
        case .gitea:     return "Self-hosted Git hosting"
        }
    }

    var symbolName: String {
        switch self {
        case .portainer: return "shippingbox.fill"
        case .pihole:    return "shield.fill"
        case .beszel:    return "server.rack"
        case .gitea:     return "arrow.triangle.branch"
        }
    }

    var colors: ServiceColorSet {
        switch self {
        case .portainer: return ServiceColorSet(primary: Color(hex: "#13B5EA"), dark: Color(hex: "#0D8ECF"), bg: Color(hex: "#13B5EA").opacity(0.09))
        case .pihole:    return ServiceColorSet(primary: Color(hex: "#CD2326"), dark: Color(hex: "#9B1B1E"), bg: Color(hex: "#CD2326").opacity(0.09))
        case .beszel:    return ServiceColorSet(primary: Color(hex: "#0EA5E9"), dark: Color(hex: "#0284C7"), bg: Color(hex: "#0EA5E9").opacity(0.09))
        case .gitea:     return ServiceColorSet(primary: Color(hex: "#609926"), dark: Color(hex: "#4A7A1E"), bg: Color(hex: "#609926").opacity(0.09))
        }
    }
}

struct ServiceColorSet {
    let primary: Color
    let dark: Color
    let bg: Color
}

