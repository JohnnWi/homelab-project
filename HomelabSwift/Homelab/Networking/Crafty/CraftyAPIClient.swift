import Foundation

actor CraftyAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var username: String = ""
    private var password: String = ""
    private var token: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .craftyController, instanceId: instanceId)
    }

    func configure(
        url: String,
        username: String,
        password: String,
        token: String,
        fallbackUrl: String? = nil
    , allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.username = username
        self.password = password
        self.token = token
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .craftyController, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let primary = await engine.pingURL(
            "\(baseURL)/api/v2/servers",
            extraHeaders: authHeaders()
        )
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL(
            "\(fallbackURL)/api/v2/servers",
            extraHeaders: authHeaders()
        )
    }

    func authenticate(
        url: String,
        username: String,
        password: String,
        fallbackUrl: String? = nil
    ) async throws -> String {
        let cleanURL = Self.cleanURL(url)
        let body = try JSONEncoder().encode(CraftyLoginRequest(username: username, password: password))
        let response: CraftyEnvelope<CraftyLoginData> = try await engine.request(
            baseURL: cleanURL,
            fallbackURL: Self.cleanURL(fallbackUrl ?? ""),
            path: "/api/v2/auth/login",
            method: "POST",
            headers: ["Content-Type": "application/json"],
            body: body
        )
        guard let token = response.data.token, !token.isEmpty else {
            throw APIError.custom("Crafty login failed")
        }
        return token
    }

    func getServers() async throws -> [CraftyServer] {
        let response: CraftyEnvelope<[CraftyServer]> = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/servers",
            headers: authHeaders()
        )
        return response.data
    }

    func getServerStats(serverId: Int) async throws -> CraftyServerStats {
        let response: CraftyEnvelope<CraftyServerStats> = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/servers/\(serverId)/stats",
            headers: authHeaders()
        )
        return response.data
    }

    func getServerLogs(serverId: Int, file: Bool = false, raw: Bool = false) async throws -> [String] {
        let response: CraftyEnvelope<[String]> = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/servers/\(serverId)/logs?file=\(file)&raw=\(raw)",
            headers: authHeaders()
        )
        return response.data
    }

    func sendCommand(serverId: Int, command: String) async throws {
        let trimmed = command
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"^/+"#, with: "", options: .regularExpression)
        guard !trimmed.isEmpty else { return }

        var headers = authHeaders()
        headers["Content-Type"] = "text/plain"

        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/servers/\(serverId)/stdin",
            method: "POST",
            headers: headers,
            body: Data(trimmed.utf8)
        )
    }

    func sendAction(serverId: Int, action: CraftyAction) async throws {
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/servers/\(serverId)/action/\(action.rawValue)",
            method: "POST",
            headers: authHeaders()
        )
    }

    private func authHeaders() -> [String: String] {
        guard !token.isEmpty else { return [:] }
        return ["Authorization": "Bearer \(token)"]
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }
}

struct CraftyEnvelope<T: Codable>: Codable {
    let status: String
    let data: T
}

struct CraftyLoginRequest: Codable {
    let username: String
    let password: String
}

struct CraftyLoginData: Codable {
    let token: String?
    let userID: String?

    enum CodingKeys: String, CodingKey {
        case token
        case userID = "user_id"
    }
}

struct CraftyServer: Codable, Identifiable, Hashable {
    let serverID: Int
    let serverUUID: String?
    let serverName: String
    let type: String?
    let serverPort: Int?

    var id: Int { serverID }

    enum CodingKeys: String, CodingKey {
        case serverID = "server_id"
        case serverUUID = "server_uuid"
        case serverName = "server_name"
        case type
        case serverPort = "server_port"
    }
}

struct CraftyServerStats: Codable, Hashable {
    let running: Bool
    let cpu: Double?
    let mem: String?
    let memPercent: Double?
    let online: Int?
    let max: Int?
    let worldName: String?
    let version: String?
    let updating: Bool
    let waitingStart: Bool
    let crashed: Bool
    let downloading: Bool

    enum CodingKeys: String, CodingKey {
        case running
        case cpu
        case mem
        case memPercent = "mem_percent"
        case online
        case max
        case worldName = "world_name"
        case version
        case updating
        case waitingStart = "waiting_start"
        case crashed
        case downloading
    }
}

enum CraftyAction: String {
    case start = "start_server"
    case stop = "stop_server"
    case restart = "restart_server"
    case backup = "backup_server"
    case kill = "kill_server"
    case updateExecutable = "update_executable"
}
