import Foundation

actor GiteaAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var token: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .gitea, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, token: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.token = token
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .gitea, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    /// Token can be "basic:base64string" or a plain API token
    private func authHeader() -> String {
        if token.hasPrefix("basic:") {
            return "Basic \(token.dropFirst(6))"
        }
        return "token \(token)"
    }

    private func authHeaders() -> [String: String] {
        ["Content-Type": "application/json", "Authorization": authHeader()]
    }

    // MARK: - Ping

    func ping() async -> Bool {
        if baseURL.isEmpty { return false }
        // /api/v1/version is public — no auth required
        if await engine.pingURL("\(baseURL)/api/v1/version") { return true }
        if !fallbackURL.isEmpty {
            return await engine.pingURL("\(fallbackURL)/api/v1/version")
        }
        return false
    }

    // MARK: - Authentication

    func authenticate(url: String, username: String, password: String, fallbackUrl: String? = nil) async throws -> (token: String, username: String) {
        let cleanURL = Self.cleanURL(url)
        do {
            return try await authenticateAgainst(url: cleanURL, username: username, password: password)
        } catch {
            let cleanFallback = Self.cleanURL(fallbackUrl ?? "")
            guard !cleanFallback.isEmpty, cleanFallback != cleanURL else { throw error }
            return try await authenticateAgainst(url: cleanFallback, username: username, password: password)
        }
    }

    private func authenticateAgainst(url: String, username: String, password: String) async throws -> (token: String, username: String) {
        let basicAuth = "Basic " + Data("\(username):\(password)".utf8).base64EncodedString()
        let session = BaseNetworkEngine.authSession(allowSelfSigned: storedAllowSelfSigned, timeout: 8)

        // Verify credentials via /user
        guard let userURL = URL(string: "\(url)/api/v1/user") else { throw APIError.invalidURL }
        var userReq = URLRequest(url: userURL)
        userReq.setValue(basicAuth, forHTTPHeaderField: "Authorization")
        userReq.timeoutInterval = 8

        let (_, userResp) = try await session.data(for: userReq)
        guard let http = userResp as? HTTPURLResponse, http.statusCode == 200 else {
            throw APIError.custom("Authentication failed. Check your credentials and URL.")
        }

        // Try to create a long-lived API token
        if let tokensURL = URL(string: "\(url)/api/v1/users/\(username)/tokens") {
            struct TokenBody: Encodable {
                let name: String
                let scopes: [String]
            }
            let body = try TokenBody(name: "homelab-\(Int(Date().timeIntervalSince1970))", scopes: ["read:repository", "read:user", "read:issue", "read:notification"]).toJSONData()
            var tokReq = URLRequest(url: tokensURL)
            tokReq.httpMethod = "POST"
            tokReq.setValue(basicAuth, forHTTPHeaderField: "Authorization")
            tokReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
            tokReq.httpBody = body
            tokReq.timeoutInterval = 8

            if let (tokData, tokResp) = try? await session.data(for: tokReq),
               let tokHttp = tokResp as? HTTPURLResponse,
               tokHttp.statusCode == 201,
               let decoded = try? JSONDecoder().decode(GiteaTokenResponse.self, from: tokData) {
                return (token: decoded.sha1, username: username)
            }
        }

        // Fallback: store basic auth credentials
        let encoded = Data("\(username):\(password)".utf8).base64EncodedString()
        return (token: "basic:\(encoded)", username: username)
    }

    // MARK: - User

    func getCurrentUser() async throws -> GiteaUser {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/v1/user", headers: authHeaders())
    }

    func getUserRepos(page: Int = 1, limit: Int = 20) async throws -> [GiteaRepo] {
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/v1/user/repos?page=\(page)&limit=\(limit)&sort=updated",
            headers: authHeaders()
        )
    }

    func getOrgs() async throws -> [GiteaOrg] {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/v1/user/orgs", headers: authHeaders())
    }

    func getNotifications() async throws -> [GiteaNotification] {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/v1/notifications?limit=20", headers: authHeaders())
    }

    func getVersion() async throws -> String {
        let v: GiteaServerVersion = try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/v1/version", headers: authHeaders())
        return v.version
    }

    func getUserHeatmap(username: String) async throws -> [GiteaHeatmapItem] {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/v1/users/\(username)/heatmap", headers: authHeaders())
    }

    // MARK: - Repository

    func getRepo(owner: String, repo: String) async throws -> GiteaRepo {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/v1/repos/\(owner)/\(repo)", headers: authHeaders())
    }

    func getRepoContents(owner: String, repo: String, path: String = "", ref: String? = nil) async throws -> [GiteaFileContent] {
        let encodedPath = path.isEmpty ? "" : "/\(path.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? path)"
        let refQuery = ref.map { "?ref=\($0.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0)" } ?? ""
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/v1/repos/\(owner)/\(repo)/contents\(encodedPath)\(refQuery)",
            headers: authHeaders()
        )
    }

    func getFileContent(owner: String, repo: String, path: String, ref: String? = nil) async throws -> GiteaFileContent {
        let encodedPath = path.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? path
        let refQuery = ref.map { "?ref=\($0.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0)" } ?? ""
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/v1/repos/\(owner)/\(repo)/contents/\(encodedPath)\(refQuery)",
            headers: authHeaders()
        )
    }

    func getRepoCommits(owner: String, repo: String, page: Int = 1, limit: Int = 20, ref: String? = nil) async throws -> [GiteaCommit] {
        let refQuery = ref.map { "&sha=\($0.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0)" } ?? ""
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/v1/repos/\(owner)/\(repo)/commits?page=\(page)&limit=\(limit)\(refQuery)",
            headers: authHeaders()
        )
    }

    func getRepoIssues(owner: String, repo: String, state: String = "open", page: Int = 1, limit: Int = 20) async throws -> [GiteaIssue] {
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/v1/repos/\(owner)/\(repo)/issues?state=\(state)&type=issues&page=\(page)&limit=\(limit)",
            headers: authHeaders()
        )
    }

    func getRepoBranches(owner: String, repo: String) async throws -> [GiteaBranch] {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/v1/repos/\(owner)/\(repo)/branches", headers: authHeaders())
    }

    func getRepoReadme(owner: String, repo: String, ref: String? = nil) async throws -> GiteaFileContent {
        let refQuery = ref.map { "?ref=\($0.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0)" } ?? ""
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/v1/repos/\(owner)/\(repo)/contents/README.md\(refQuery)",
            headers: authHeaders()
        )
    }

    // MARK: - Helpers

    private static func cleanURL(_ url: String) -> String {
        url.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }
}
