import Foundation

// MARK: - Models

struct PterodactylServerList: Codable {
    let data: [PterodactylServerEntry]
}

struct PterodactylServerEntry: Codable {
    let attributes: PterodactylServer
}

struct PterodactylServer: Codable, Identifiable, Hashable {
    let identifier: String
    let uuid: String
    let name: String
    let node: String
    let description: String
    let isNodeUnderMaintenance: Bool
    let status: String?
    let isSuspended: Bool
    let isInstalling: Bool
    let limits: PterodactylLimits

    var id: String { identifier }

    enum CodingKeys: String, CodingKey {
        case identifier, uuid, name, node, description, status, limits
        case isNodeUnderMaintenance = "is_node_under_maintenance"
        case isSuspended = "is_suspended"
        case isInstalling = "is_installing"
    }
}

struct PterodactylLimits: Codable, Hashable {
    let memory: Int
    let disk: Int
    let cpu: Int
}

struct PterodactylResourcesResponse: Codable {
    let attributes: PterodactylResources
}

struct PterodactylResources: Codable, Hashable {
    let currentState: String
    let isSuspended: Bool
    let resources: PterodactylResourceUsage

    enum CodingKeys: String, CodingKey {
        case isSuspended = "is_suspended"
        case currentState = "current_state"
        case resources
    }
}

struct PterodactylResourceUsage: Codable, Hashable {
    let memoryBytes: Int
    let cpuAbsolute: Double
    let diskBytes: Int
    let networkRxBytes: Int
    let networkTxBytes: Int
    let uptime: Int

    enum CodingKeys: String, CodingKey {
        case memoryBytes = "memory_bytes"
        case cpuAbsolute = "cpu_absolute"
        case diskBytes = "disk_bytes"
        case networkRxBytes = "network_rx_bytes"
        case networkTxBytes = "network_tx_bytes"
        case uptime
    }
}

enum PterodactylPowerSignal: String {
    case start, stop, restart, kill
}

// MARK: - API Client

actor PterodactylAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .pterodactyl, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey

        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .pterodactyl, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
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
            "\(baseURL)/api/client",
            extraHeaders: authHeaders()
        )
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL(
            "\(fallbackURL)/api/client",
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
        let _: PterodactylServerList = try await engine.request(
            baseURL: cleanURL,
            fallbackURL: Self.cleanURL(fallbackUrl ?? ""),
            path: "/api/client",
            headers: headers
        )
    }

    // MARK: - Servers

    func getServers() async throws -> [PterodactylServer] {
        let response: PterodactylServerList = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/client",
            headers: authHeaders()
        )
        return response.data.map(\.attributes)
    }

    func getServerResources(identifier: String) async throws -> PterodactylResources {
        let response: PterodactylResourcesResponse = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/client/servers/\(identifier)/resources",
            headers: authHeaders()
        )
        return response.attributes
    }

    // MARK: - Power actions

    func sendPowerSignal(identifier: String, signal: PterodactylPowerSignal) async throws {
        struct PowerBody: Encodable { let signal: String }
        let body = try JSONEncoder().encode(PowerBody(signal: signal.rawValue))
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/client/servers/\(identifier)/power",
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
