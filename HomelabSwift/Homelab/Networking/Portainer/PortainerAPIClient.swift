import Foundation

actor PortainerAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var jwt: String = ""
    private var apiKey: String = ""
    private var useApiKey: Bool = false

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .portainer, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, jwt: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.jwt = jwt
        self.apiKey = ""
        self.useApiKey = false
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .portainer, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func configureWithApiKey(url: String, apiKey: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey
        self.jwt = ""
        self.useApiKey = true
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .portainer, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func isUsingApiKey() -> Bool { useApiKey }
    func getApiKey() -> String { apiKey }

    // MARK: - Auth headers

    private func authHeaders() -> [String: String] {
        var h: [String: String] = ["Content-Type": "application/json"]
        if useApiKey {
            h["X-API-Key"] = apiKey
        } else {
            h["Authorization"] = "Bearer \(jwt)"
        }
        return h
    }

    // MARK: - Ping

    func ping() async -> Bool {
        if baseURL.isEmpty { return false }
        let headers = authHeaders()
        if await engine.pingURL("\(baseURL)/api/status", extraHeaders: headers) { return true }
        if !fallbackURL.isEmpty {
            return await engine.pingURL("\(fallbackURL)/api/status", extraHeaders: headers)
        }
        return false
    }

    // MARK: - Authentication

    func authenticate(url: String, username: String, password: String, fallbackUrl: String? = nil) async throws -> String {
        let cleanURL = Self.cleanURL(url)
        do {
            return try await authenticateAgainst(url: cleanURL, username: username, password: password)
        } catch {
            let cleanFallback = Self.cleanURL(fallbackUrl ?? "")
            guard !cleanFallback.isEmpty, cleanFallback != cleanURL else { throw error }
            return try await authenticateAgainst(url: cleanFallback, username: username, password: password)
        }
    }

    private func authenticateAgainst(url: String, username: String, password: String) async throws -> String {
        let authURL = "\(url)/api/auth"
        guard let url = URL(string: authURL) else { throw APIError.invalidURL }

        // Try lowercase keys first (modern Portainer)
        let body1 = try JSONEncoder().encode(["username": username, "password": password])
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = body1
        req.timeoutInterval = 8

        let session = BaseNetworkEngine.authSession(allowSelfSigned: storedAllowSelfSigned, timeout: 8)
        let (data1, resp1) = try await session.data(for: req)

        if let http = resp1 as? HTTPURLResponse, http.statusCode == 200 {
            let decoded = try JSONDecoder().decode(PortainerAuthResponse.self, from: data1)
            return decoded.jwt
        }

        // Fallback: uppercase keys (older Portainer)
        let body2 = try JSONEncoder().encode(["Username": username, "Password": password])
        req.httpBody = body2
        let (data2, resp2) = try await session.data(for: req)

        guard let http2 = resp2 as? HTTPURLResponse, http2.statusCode == 200 else {
            let status = (resp2 as? HTTPURLResponse)?.statusCode ?? 0
            if status == 401 || status == 403 || status == 422 {
                throw APIError.custom("Invalid credentials. Check your username and password.")
            }
            throw APIError.custom("Authentication failed (\(status)). Check the URL and credentials.")
        }

        let decoded = try JSONDecoder().decode(PortainerAuthResponse.self, from: data2)
        return decoded.jwt
    }

    func authenticateWithApiKey(url: String, apiKey: String) async throws {
        let cleanURL = Self.cleanURL(url)
        let headers = ["X-API-Key": apiKey]
        
        // We use requestData to benefit from interceptResponse logic (HTML detection, 401/403 handling)
        _ = try await engine.requestData(
            baseURL: cleanURL,
            fallbackURL: "",
            path: "/api/endpoints",
            method: "GET",
            headers: headers
        )
    }

    // MARK: - Endpoints

    func getEndpoints() async throws -> [PortainerEndpoint] {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/endpoints", headers: authHeaders())
    }

    // MARK: - Containers

    func getContainers(endpointId: Int, all: Bool = true) async throws -> [PortainerContainer] {
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/endpoints/\(endpointId)/docker/containers/json?all=\(all ? "true" : "false")",
            headers: authHeaders()
        )
    }

    func getContainerDetail(endpointId: Int, containerId: String) async throws -> ContainerDetail {
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/endpoints/\(endpointId)/docker/containers/\(containerId)/json",
            headers: authHeaders()
        )
    }

    func getContainerStats(endpointId: Int, containerId: String) async throws -> ContainerStats {
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/endpoints/\(endpointId)/docker/containers/\(containerId)/stats?stream=false",
            headers: authHeaders()
        )
    }

    func getContainerLogs(endpointId: Int, containerId: String, tail: Int = 100) async throws -> String {
        return try await engine.requestString(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/endpoints/\(endpointId)/docker/containers/\(containerId)/logs?stdout=true&stderr=true&tail=\(tail)&timestamps=true",
            headers: authHeaders()
        )
    }

    private func handleForbidden(error: Error) throws {
        if case APIError.httpError(let code, let body) = error, code == 403 {
            let desc = body.isEmpty ? "Forbidden (403). Your account may have a read-only role or lacks permission to perform this action." : body
            throw APIError.custom(desc)
        }
        throw error
    }

    func containerAction(endpointId: Int, containerId: String, action: ContainerAction) async throws {
        do {
            try await engine.requestVoid(
                baseURL: baseURL, fallbackURL: fallbackURL,
                path: "/api/endpoints/\(endpointId)/docker/containers/\(containerId)/\(action.rawValue)",
                method: "POST",
                headers: authHeaders()
            )
        } catch {
            try handleForbidden(error: error)
        }
    }

    func removeContainer(endpointId: Int, containerId: String, force: Bool = false) async throws {
        do {
            try await engine.requestVoid(
                baseURL: baseURL, fallbackURL: fallbackURL,
                path: "/api/endpoints/\(endpointId)/docker/containers/\(containerId)?force=\(force ? "true" : "false")",
                method: "DELETE",
                headers: authHeaders()
            )
        } catch {
            try handleForbidden(error: error)
        }
    }

    func renameContainer(endpointId: Int, containerId: String, newName: String) async throws {
        let encodedName = newName.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? newName
        do {
            try await engine.requestVoid(
                baseURL: baseURL, fallbackURL: fallbackURL,
                path: "/api/endpoints/\(endpointId)/docker/containers/\(containerId)/rename?name=\(encodedName)",
                method: "POST",
                headers: authHeaders()
            )
        } catch {
            try handleForbidden(error: error)
        }
    }

    // MARK: - Stacks

    func getStacks(endpointId: Int) async throws -> [PortainerStack] {
        let filterEncoded = "{\"EndpointID\":\(endpointId)}".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/stacks?filters=\(filterEncoded)",
            headers: authHeaders()
        )
    }

    func getStackFile(stackId: Int) async throws -> String {
        let response: PortainerStackFile = try await engine.request(
            baseURL: baseURL, fallbackURL: fallbackURL,
            path: "/api/stacks/\(stackId)/file",
            headers: authHeaders()
        )
        return response.StackFileContent
    }

    func updateStackFile(stackId: Int, endpointId: Int, stackFileContent: String) async throws {
        struct UpdateBody: Encodable {
            let stackFileContent: String
            let env: [String]
            let prune: Bool
        }
        let body = try UpdateBody(stackFileContent: stackFileContent, env: [], prune: false).toJSONData()
        do {
            try await engine.requestVoid(
                baseURL: baseURL, fallbackURL: fallbackURL,
                path: "/api/stacks/\(stackId)?endpointId=\(endpointId)",
                method: "PUT",
                headers: authHeaders(),
                body: body
            )
        } catch {
            try handleForbidden(error: error)
        }
    }

    // MARK: - Helpers

    private static func cleanURL(_ url: String) -> String {
        url.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }
}
