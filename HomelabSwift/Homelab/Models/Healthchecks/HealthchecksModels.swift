import Foundation

struct HealthchecksChecksResponse: Codable {
    let checks: [HealthchecksCheck]
}

struct HealthchecksCheck: Codable, Identifiable, Hashable {
    let name: String
    let slug: String?
    let tags: String?
    let desc: String?
    let grace: Int?
    let nPings: Int?
    let status: String
    let started: Bool?
    let lastPing: String?
    let nextPing: String?
    let manualResume: Bool?
    let methods: String?
    let startKw: String?
    let successKw: String?
    let failureKw: String?
    let filterSubject: Bool?
    let filterBody: Bool?
    let filterHttpBody: Bool?
    let filterDefaultFail: Bool?
    let badgeUrl: String?
    let uuid: String?
    let uniqueKey: String?
    let pingUrl: String?
    let updateUrl: String?
    let pauseUrl: String?
    let resumeUrl: String?
    let channels: String?
    let timeout: Int?
    let schedule: String?
    let tz: String?

    var id: String {
        uuid ?? uniqueKey ?? "\(name)-\(slug ?? "")-\(lastPing ?? "")"
    }

    var tagsList: [String] {
        tags?.split(separator: " ").map { String($0) } ?? []
    }

    var channelsList: [String] {
        channels?.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } ?? []
    }

    var apiIdentifier: String? { uuid ?? uniqueKey }
    var hasUUID: Bool { uuid != nil }
    var isPaused: Bool { status == "paused" }
    var isGrace: Bool { status == "grace" }
    var isDown: Bool { status == "down" }

    enum CodingKeys: String, CodingKey {
        case name, slug, tags, desc, grace, status, started, methods, schedule, tz, channels, timeout
        case nPings = "n_pings"
        case lastPing = "last_ping"
        case nextPing = "next_ping"
        case manualResume = "manual_resume"
        case startKw = "start_kw"
        case successKw = "success_kw"
        case failureKw = "failure_kw"
        case filterSubject = "filter_subject"
        case filterBody = "filter_body"
        case filterHttpBody = "filter_http_body"
        case filterDefaultFail = "filter_default_fail"
        case badgeUrl = "badge_url"
        case uuid
        case uniqueKey = "unique_key"
        case pingUrl = "ping_url"
        case updateUrl = "update_url"
        case pauseUrl = "pause_url"
        case resumeUrl = "resume_url"
    }
}

struct HealthchecksPingResponse: Codable {
    let pings: [HealthchecksPing]
}

struct HealthchecksPing: Codable, Identifiable, Hashable {
    let type: String
    let date: String
    let n: Int
    let scheme: String?
    let remoteAddr: String?
    let method: String?
    let userAgent: String?
    let runId: String?
    let duration: Double?
    let bodyUrl: String?

    var id: Int { n }

    enum CodingKeys: String, CodingKey {
        case type, date, n, scheme, method, duration
        case remoteAddr = "remote_addr"
        case userAgent = "ua"
        case runId = "rid"
        case bodyUrl = "body_url"
    }
}

struct HealthchecksFlip: Codable, Identifiable, Hashable {
    let timestamp: String
    let up: Int

    var id: String { timestamp }
    var isUp: Bool { up == 1 }
}

struct HealthchecksChannelsResponse: Codable {
    let channels: [HealthchecksChannel]
}

struct HealthchecksChannel: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let kind: String
}

struct HealthchecksBadgesResponse: Codable {
    let badges: [String: HealthchecksBadgeFormats]
}

struct HealthchecksBadgeFormats: Codable, Hashable {
    let svg: String?
    let svg3: String?
    let json: String?
    let json3: String?
    let shields: String?
    let shields3: String?
}

struct HealthchecksCheckPayload: Encodable {
    let name: String?
    let slug: String?
    let tags: String?
    let desc: String?
    let timeout: Int?
    let grace: Int?
    let schedule: String?
    let tz: String?
    let manualResume: Bool?
    let methods: String?
    let channels: String?

    enum CodingKeys: String, CodingKey {
        case name, slug, tags, desc, timeout, grace, schedule, tz, methods, channels
        case manualResume = "manual_resume"
    }
}
