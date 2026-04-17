import Foundation

actor BeszelAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var token: String = ""
    private var email: String = ""
    private var storedPassword: String = ""
    private var isRefreshing = false
    private var onTokenRefreshed: (@Sendable (String) -> Void)?

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .beszel, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, token: String, fallbackUrl: String? = nil, email: String? = nil, password: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.token = token
        if let email, !email.isEmpty { self.email = email }
        if let password, !password.isEmpty { self.storedPassword = password }
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .beszel, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    /// Set a callback invoked after successful token refresh so the store can persist it
    func setTokenRefreshCallback(_ callback: @escaping @Sendable (String) -> Void) {
        self.onTokenRefreshed = callback
    }

    private func authHeaders() -> [String: String] {
        ["Content-Type": "application/json", "Authorization": "Bearer \(token)"]
    }

    /// Attempts to refresh the token using stored credentials
    private func refreshToken() async -> Bool {
        guard !email.isEmpty, !storedPassword.isEmpty, !isRefreshing else { return false }
        isRefreshing = true
        defer { isRefreshing = false }

        do {
            let newToken = try await authenticate(url: baseURL.isEmpty ? fallbackURL : baseURL, email: email, password: storedPassword)
            token = newToken
            onTokenRefreshed?(newToken)
            return true
        } catch {
            return false
        }
    }

    /// Wrapper that retries once after token refresh on auth failure (400/401)
    private func authenticatedRequest<T: Decodable>(path: String, method: String = "GET", headers: [String: String]? = nil, body: Data? = nil) async throws -> T {
        let h = headers ?? authHeaders()
        do {
            return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: h, body: body)
        } catch {
            if isAuthError(error), await refreshToken() {
                return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: authHeaders(), body: body)
            }
            throw error
        }
    }

    private func isAuthError(_ error: Error) -> Bool {
        guard let apiError = error as? APIError else { return false }
        switch apiError {
        case .httpError(let code, _): return code == 401
        case .unauthorized: return true
        case .bothURLsFailed(let primary, let fallback):
            return isAuthError(primary) || isAuthError(fallback)
        default: return false
        }
    }

    // MARK: - Ping

    func ping() async -> Bool {
        if baseURL.isEmpty { return false }
        if await engine.pingURL("\(baseURL)/api/health", extraHeaders: authHeaders()) { return true }
        if !fallbackURL.isEmpty {
            return await engine.pingURL("\(fallbackURL)/api/health", extraHeaders: authHeaders())
        }
        return false
    }

    // MARK: - Authentication (PocketBase)

    func authenticate(url: String, email: String, password: String, fallbackUrl: String? = nil) async throws -> String {
        let cleanURL = Self.cleanURL(url)
        do {
            return try await authenticateAgainst(url: cleanURL, email: email, password: password)
        } catch {
            let cleanFallback = Self.cleanURL(fallbackUrl ?? "")
            guard !cleanFallback.isEmpty, cleanFallback != cleanURL else { throw error }
            return try await authenticateAgainst(url: cleanFallback, email: email, password: password)
        }
    }

    private func authenticateAgainst(url: String, email: String, password: String) async throws -> String {
        guard let authURL = URL(string: "\(url)/api/collections/users/auth-with-password") else {
            throw APIError.invalidURL
        }

        let body = try JSONEncoder().encode(["identity": email, "password": password])
        var req = URLRequest(url: authURL)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = body
        req.timeoutInterval = 8

        let session = BaseNetworkEngine.authSession(allowSelfSigned: storedAllowSelfSigned, timeout: 8)
        let (data, resp) = try await session.data(for: req)
        guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else {
            throw APIError.custom("Authentication failed. Check your credentials and URL.")
        }

        let decoded = try JSONDecoder().decode(BeszelAuthResponse.self, from: data)
        return decoded.token
    }

    // MARK: - Systems

    func getSystems() async throws -> BeszelSystemsResponse {
        return try await authenticatedRequest(path: "/api/collections/systems/records?sort=-updated&perPage=50")
    }

    func getSystem(id: String) async throws -> BeszelSystem {
        return try await authenticatedRequest(path: "/api/collections/systems/records/\(id)")
    }

    func getSystemRecords(systemId: String, limit: Int = 60) async throws -> BeszelRecordsResponse {
        let filter = "system='\(systemId)'".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        return try await authenticatedRequest(path: "/api/collections/system_stats/records?filter=(\(filter))&sort=-created&perPage=\(limit)")
    }

    // MARK: - System Details

    func getSystemDetails(systemId: String) async throws -> BeszelSystemDetails? {
        let filter = "system='\(systemId)'".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let response: BeszelSystemDetailsResponse = try await authenticatedRequest(path: "/api/collections/system_details/records?filter=(\(filter))&perPage=1")
        return response.items.first
    }

    // MARK: - S.M.A.R.T. Devices

    func getSmartDevices(systemId: String) async throws -> [BeszelSmartDevice] {
        let filter = "system='\(systemId)'".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let response: BeszelSmartDevicesResponse = try await authenticatedRequest(path: "/api/collections/smart_devices/records?filter=\(filter)&perPage=50")
        return response.items
    }

    // MARK: - Containers

    func getContainers(systemId: String) async throws -> [BeszelContainerRecord] {
        let filter = "system='\(systemId)'".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let response: BeszelContainersResponse = try await authenticatedRequest(path: "/api/collections/containers/records?filter=(\(filter))&perPage=200")
        return response.items
    }

    func getContainerStats(systemId: String, limit: Int = 60) async throws -> [BeszelContainerStatsRecord] {
        let filter = "system='\(systemId)'".addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        let response: BeszelContainerStatsResponse = try await authenticatedRequest(path: "/api/collections/container_stats/records?filter=(\(filter))&sort=-created&perPage=\(limit)")
        return response.items
    }

    func getContainerLogs(systemId: String, containerId: String) async throws -> String {
        let system = systemId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? systemId
        let container = containerId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? containerId
        let path = "/api/beszel/containers/logs?system=\(system)&container=\(container)"
        do {
            return try await engine.requestString(
                baseURL: baseURL, fallbackURL: fallbackURL,
                path: path,
                headers: authHeaders()
            )
        } catch {
            if isAuthError(error), await refreshToken() {
                return try await engine.requestString(
                    baseURL: baseURL, fallbackURL: fallbackURL,
                    path: path,
                    headers: authHeaders()
                )
            }
            throw error
        }
    }

    func getContainerInfo(systemId: String, containerId: String) async throws -> String {
        let system = systemId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? systemId
        let container = containerId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? containerId
        let path = "/api/beszel/containers/info?system=\(system)&container=\(container)"
        let data: Data
        do {
            data = try await engine.requestData(
                baseURL: baseURL, fallbackURL: fallbackURL,
                path: path,
                headers: authHeaders()
            )
        } catch {
            if isAuthError(error), await refreshToken() {
                let retryData = try await engine.requestData(
                    baseURL: baseURL, fallbackURL: fallbackURL,
                    path: path,
                    headers: authHeaders()
                )
                if let json = try? JSONSerialization.jsonObject(with: retryData),
                   let pretty = try? JSONSerialization.data(withJSONObject: json, options: [.prettyPrinted, .sortedKeys]),
                   let prettyString = String(data: pretty, encoding: .utf8) {
                    return prettyString
                }
                return String(data: retryData, encoding: .utf8) ?? ""
            }
            throw error
        }

        if let json = try? JSONSerialization.jsonObject(with: data),
           let pretty = try? JSONSerialization.data(withJSONObject: json, options: [.prettyPrinted, .sortedKeys]),
           let prettyString = String(data: pretty, encoding: .utf8) {
            return prettyString
        }
        return String(data: data, encoding: .utf8) ?? ""
    }

    // MARK: - Helpers

    private static func cleanURL(_ url: String) -> String {
        url.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }
}
