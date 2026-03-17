import Foundation

struct AdGuardStatus {
    let version: String?
    let language: String?
    let running: Bool?
    let protection_enabled: Bool
    let protection_disabled_until: Int?
    let dns_addresses: [String]?
    let dns_port: Int?
    let http_port: Int?
    let start_time: String?
}

struct AdGuardTopItem: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let count: Int
}

struct AdGuardStats {
    let totalQueries: Int
    let blockedFiltering: Int
    let replacedSafebrowsing: Int
    let replacedSafesearch: Int
    let replacedParental: Int
    let avgProcessingTime: Double
    let topQueried: [AdGuardTopItem]
    let topBlocked: [AdGuardTopItem]
    let topClients: [AdGuardTopItem]
    let dnsQueries: [Int]
    let blockedSeries: [Int]
}

struct AdGuardQueryLogPage {
    let items: [AdGuardQueryLogEntry]
    let total: Int
    let page: Int
    let pages: Int
    let oldest: String?
}

struct AdGuardQueryLogEntry: Identifiable, Hashable {
    let id: String
    let time: String
    let domain: String
    let client: String
    let status: String
    let reason: String?
    let blocked: Bool?

    var isBlocked: Bool {
        if let blocked { return blocked }
        if let reason {
            if reason.hasPrefix("Filtered") { return true }
            if reason.hasPrefix("NotFiltered") { return false }
            if reason.hasPrefix("Rewrite") { return false }
        }
        let lower = status.lowercased()
        if lower.contains("blocked") || lower.contains("filtered") { return true }
        return false
    }
}

struct AdGuardFilteringStatus {
    let userRules: [String]
    let filters: [AdGuardFilter]
    let whitelistFilters: [AdGuardFilter]
}

struct AdGuardFilter: Identifiable, Hashable {
    let id: Int
    let name: String
    let url: String
    let enabled: Bool
    let rulesCount: Int
    let lastUpdated: String?
}

struct AdGuardBlockedService: Identifiable, Hashable {
    let id: String
    let name: String
    let rules: [String]
    let groupId: String?
    let iconSvg: String?
}

struct AdGuardServiceGroup: Identifiable, Hashable {
    let id: String
    let name: String
}

struct AdGuardBlockedServicesAll {
    let services: [AdGuardBlockedService]
    let groups: [AdGuardServiceGroup]
}

struct AdGuardBlockedServicesSchedule {
    let ids: [String]
    let schedule: [String: AdGuardJSONValue]?
}

struct AdGuardRewriteEntry: Codable, Identifiable, Hashable {
    let domain: String
    let answer: String
    let enabled: Bool?

    var id: String { "\(domain)|\(answer)" }
}

enum AdGuardJSONValue: Sendable, Equatable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: AdGuardJSONValue])
    case array([AdGuardJSONValue])
    case null

    var toAny: Any {
        switch self {
        case .string(let value): return value
        case .number(let value): return value
        case .bool(let value): return value
        case .object(let value): return value.mapValues { $0.toAny }
        case .array(let value): return value.map { $0.toAny }
        case .null: return NSNull()
        }
    }
}
