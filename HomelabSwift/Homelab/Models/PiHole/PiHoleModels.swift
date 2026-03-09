import Foundation

struct PiholeStats: Codable {
    let queries: PiholeQueryStats
    let gravity: PiholeGravityStats
}

struct PiholeQueryStats: Codable {
    let total: Int
    let blocked: Int
    let percent_blocked: Double
    let unique_domains: Int
    let forwarded: Int
    let cached: Int
    let types: [String: Int]?
}

struct PiholeGravityStats: Codable {
    let domains_being_blocked: Int
    let last_update: Int
}

struct PiholeTopItem: Identifiable {
    let id = UUID()
    let domain: String
    let count: Int
}

struct PiholeTopClient: Identifiable {
    let id = UUID()
    let name: String
    let ip: String
    let count: Int
}

struct PiholeBlockingStatus: Codable {
    let blocking: String

    var isEnabled: Bool { blocking == "enabled" }
}

struct PiholeQueryHistory: Codable {
    let history: [PiholeHistoryEntry]
}

struct PiholeHistoryEntry: Codable, Identifiable {
    var id = UUID()
    let timestamp: Int
    let total: Int
    let blocked: Int

    enum CodingKeys: String, CodingKey {
        case timestamp, total, blocked
    }
}

struct PiholeQueryLogEntry: Identifiable, Sendable, Hashable {
    let id: String
    let timestamp: Int
    let domain: String
    let client: String
    let status: String

    var isBlocked: Bool {
        let raw = status.lowercased()
        return raw.contains("block") || raw.contains("deny") || raw.contains("gravity")
    }
}

struct PiholeUpstream: Codable {
    let upstreams: [String: PiholeUpstreamEntry]
    let total_queries: Int
    let forwarded_queries: Int
}

struct PiholeUpstreamEntry: Codable {
    let count: Int
    let ip: String
    let name: String
    let port: Int
}

// MARK: - Auth response

struct PiholeAuthResponse: Codable {
    struct Session: Codable {
        let sid: String
        let valid: Bool
        let totp: Bool?
    }
    let session: Session
}

// MARK: - Top Domains response (handles both object and array formats)

struct PiholeTopDomainsResponse: Codable {
    let top_domains: [String: Int]
}

struct PiholeTopBlockedResponse: Codable {
    let top_blocked: [String: Int]
}

struct PiholeTopClientsResponse: Codable {
    struct ClientEntry: Codable {
        let name: String
        let ip: String
        let count: Int
    }
    let top_clients: [String: ClientEntry]
}
