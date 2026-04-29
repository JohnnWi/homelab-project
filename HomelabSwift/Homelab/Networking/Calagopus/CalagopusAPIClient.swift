import Foundation

// MARK: - Models

struct CalagopusServerList: Codable {
    let servers: CalagopusServerPage
}

struct CalagopusServerPage: Codable {
    let total: Int
    let perPage: Int
    let page: Int
    let data: [CalagopusServer]

    enum CodingKeys: String, CodingKey {
        case total, page, data
        case perPage = "per_page"
    }
}

struct CalagopusServer: Codable, Identifiable, Hashable {
    let uuid: String
    let uuidShort: String
    let name: String
    let description: String?
    let status: String?
    let isSuspended: Bool
    let isOwner: Bool
    let nodeName: String
    let locationName: String
    let limits: CalagopusLimits

    var id: String { uuidShort }

    enum CodingKeys: String, CodingKey {
        case uuid, name, description, status, limits
        case uuidShort = "uuid_short"
        case isSuspended = "is_suspended"
        case isOwner = "is_owner"
        case nodeName = "node_name"
        case locationName = "location_name"
    }
}

struct CalagopusLimits: Codable, Hashable {
    let memory: Int
    let disk: Int
    let cpu: Int
    let swap: Int
}

struct CalagopusResourcesResponse: Codable {
    let resources: CalagopusResources
}

struct CalagopusResources: Codable, Hashable {
    let state: String
    let memoryBytes: Int
    let memoryLimitBytes: Int
    let diskBytes: Int
    let cpuAbsolute: Double
    let uptime: Int
    let network: CalagopusNetwork

    enum CodingKeys: String, CodingKey {
        case state, uptime, network
        case memoryBytes = "memory_bytes"
        case memoryLimitBytes = "memory_limit_bytes"
        case diskBytes = "disk_bytes"
        case cpuAbsolute = "cpu_absolute"
    }
}

struct CalagopusNetwork: Codable, Hashable {
    let rxBytes: Int
    let txBytes: Int

    enum CodingKeys: String, CodingKey {
        case rxBytes = "rx_bytes"
        case txBytes = "tx_bytes"
    }
}

enum CalagopusPowerSignal: String {
    case start, stop, restart, kill
}

// MARK: - API Client

actor CalagopusAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .calagopus, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .calagopus, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    // MARK: - Auth headers

    private func authHeaders() -> [String: String] {
        [
            "Authorization": "Bearer \(apiKey)",
            "Accept": "application/json",
            "Content-Type": "application/json"
        ]
    }

    // MARK: - Ping

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let primary = await engine.pingURL(
            "\(baseURL)/api/client/servers",
            extraHeaders: authHeaders()
        )
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL(
            "\(fallbackURL)/api/client/servers",
            extraHeaders: authHeaders()
        )
    }

    // MARK: - Validation (used at login)

    func authenticate(url: String, apiKey: String, fallbackUrl: String? = nil) async throws {
        let cleanURL = Self.cleanURL(url)
        let headers = [
            "Authorization": "Bearer \(apiKey)",
            "Accept": "application/json",
            "Content-Type": "application/json"
        ]
        let _: CalagopusServerList = try await engine.request(
            baseURL: cleanURL,
            fallbackURL: Self.cleanURL(fallbackUrl ?? ""),
            path: "/api/client/servers",
            headers: headers
        )
    }

    // MARK: - Servers

    func getServers() async throws -> [CalagopusServer] {
        let response: CalagopusServerList = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/client/servers",
            headers: authHeaders()
        )
        return response.servers.data
    }

    func getServerResources(uuidShort: String) async throws -> CalagopusResources {
        let response: CalagopusResourcesResponse = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/client/servers/\(uuidShort)/resources",
            headers: authHeaders()
        )
        return response.resources
    }

    // MARK: - Power actions

    func sendPowerSignal(uuidShort: String, signal: CalagopusPowerSignal) async throws {
        struct PowerBody: Encodable { let signal: String }
        let body = try JSONEncoder().encode(PowerBody(signal: signal.rawValue))
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/client/servers/\(uuidShort)/power",
            method: "POST",
            headers: authHeaders(),
            body: body
        )
    }

    // MARK: - Helpers

    private static func cleanURL(_ url: String) -> String {
        var s = url.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.hasSuffix("/") { s = String(s.dropLast()) }
        return s
    }
}
