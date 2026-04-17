import Foundation

actor QbittorrentAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var sid: String = ""
    private var username: String = ""
    private var password: String = ""

    private var onTokenRefresh: (@Sendable (String) -> Void)? = nil

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .qbittorrent, instanceId: instanceId)
    }

    func setTokenRefreshCallback(_ callback: @escaping @Sendable (String) -> Void) {
        self.onTokenRefresh = callback
    }

    func configure(
        url: String,
        sid: String,
        fallbackUrl: String? = nil,
        username: String? = nil,
        password: String? = nil
    , allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.sid = sid
        self.username = Self.cleanCredential(username)
        self.password = Self.cleanCredential(password)
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .qbittorrent, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let path = "/api/v2/app/version"
        if !sid.isEmpty {
            let primary = await engine.pingURL(baseURL + path, extraHeaders: authHeaders())
            if primary { return true }
            if !fallbackURL.isEmpty {
                let fallback = await engine.pingURL(fallbackURL + path, extraHeaders: authHeaders())
                if fallback { return true }
            }
        }

        guard canRefreshSession else { return false }
        do {
            try await refreshSession()
        } catch {
            return false
        }

        let primary = await engine.pingURL(baseURL + path, extraHeaders: authHeaders())
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL(fallbackURL + path, extraHeaders: authHeaders())
    }

    // Authenticate and return the SID cookie value
    func authenticate(url: String, username: String, password: String, fallbackUrl: String? = nil) async throws -> String {
        let cleanedURL = Self.cleanURL(url)
        let cleanedFallback = Self.cleanURL(fallbackUrl ?? "")

        do {
            return try await authenticateAgainst(url: cleanedURL, username: username, password: password)
        } catch {
            guard !cleanedFallback.isEmpty else { throw error }
            return try await authenticateAgainst(url: cleanedFallback, username: username, password: password)
        }
    }

    func getTransferInfo() async throws -> QbittorrentTransferInfo {
        try await requestWithSessionRefresh {
            try await engine.request(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/transfer/info",
                headers: authHeaders()
            )
        }
    }

    func getTorrents(filter: String = "all") async throws -> [QbittorrentTorrent] {
        try await requestWithSessionRefresh {
            try await engine.request(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/torrents/info?filter=\(filter)",
                headers: authHeaders()
            )
        }
    }

    func pauseAll() async throws {
        try await requestVoidWithSessionRefresh {
            try await engine.requestVoid(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/torrents/pause",
                method: "POST",
                headers: formHeaders(),
                body: formBody(["hashes": "all"])
            )
        }
    }

    func resumeAll() async throws {
        try await requestVoidWithSessionRefresh {
            try await engine.requestVoid(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/torrents/resume",
                method: "POST",
                headers: formHeaders(),
                body: formBody(["hashes": "all"])
            )
        }
    }

    func pauseTorrent(hash: String) async throws {
        try await requestVoidWithSessionRefresh {
            try await engine.requestVoid(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/torrents/pause",
                method: "POST",
                headers: formHeaders(),
                body: formBody(["hashes": hash])
            )
        }
    }

    func resumeTorrent(hash: String) async throws {
        try await requestVoidWithSessionRefresh {
            try await engine.requestVoid(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/torrents/resume",
                method: "POST",
                headers: formHeaders(),
                body: formBody(["hashes": hash])
            )
        }
    }

    func recheckTorrent(hash: String) async throws {
        try await requestVoidWithSessionRefresh {
            try await engine.requestVoid(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/torrents/recheck",
                method: "POST",
                headers: formHeaders(),
                body: formBody(["hashes": hash])
            )
        }
    }

    func reannounceTorrent(hash: String) async throws {
        try await requestVoidWithSessionRefresh {
            try await engine.requestVoid(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/torrents/reannounce",
                method: "POST",
                headers: formHeaders(),
                body: formBody(["hashes": hash])
            )
        }
    }

    func deleteTorrent(hash: String, deleteFiles: Bool) async throws {
        try await requestVoidWithSessionRefresh {
            try await engine.requestVoid(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/torrents/delete",
                method: "POST",
                headers: formHeaders(),
                body: formBody(["hashes": hash, "deleteFiles": deleteFiles ? "true" : "false"])
            )
        }
    }

    func toggleAlternativeSpeedLimits() async throws {
        try await requestVoidWithSessionRefresh {
            try await engine.requestVoid(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/transfer/toggleSpeedLimitsMode",
                method: "POST",
                headers: formHeaders()
            )
        }
    }

    private func authHeaders() -> [String: String] {
        return [
            "Cookie": "SID=\(self.sid)"
        ]
    }

    private func formHeaders() -> [String: String] {
        var headers = authHeaders()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
        return headers
    }

    private func formBody(_ params: [String: String]) -> Data? {
        var components = URLComponents()
        components.queryItems = params.map { URLQueryItem(name: $0.key, value: $0.value) }
        return components.query?.data(using: .utf8)
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func cleanCredential(_ value: String?) -> String {
        value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }
    
    private func parseSIDCookie(from setCookieHeader: String) -> String? {
        guard let range = setCookieHeader.range(of: "SID=") else { return nil }
        let substring = setCookieHeader[range.upperBound...]
        if let endItem = substring.firstIndex(where: { $0 == ";" || $0 == "," }) {
            return String(substring[..<endItem])
        }
        return String(substring)
    }

    private func authenticateAgainst(url: String, username: String, password: String) async throws -> String {
        let loginPath = "/api/v2/auth/login"
        guard let fullUrl = URL(string: url + loginPath) else { throw APIError.invalidURL }

        var req = URLRequest(url: fullUrl)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        req.setValue(url, forHTTPHeaderField: "Origin")
        req.setValue(url + "/", forHTTPHeaderField: "Referer")

        var components = URLComponents()
        components.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "password", value: password)
        ]
        req.httpBody = components.query?.data(using: .utf8)

        let session = BaseNetworkEngine.authSession(allowSelfSigned: storedAllowSelfSigned, timeout: 10)

        AppLogger.shared.network("--> POST \(fullUrl.absoluteString)", source: "qBittorrent")
        let (data, response) = try await session.data(for: req)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.custom("Invalid response from qBittorrent server")
        }
        logResponse(httpResponse, data: data)

        let cookies = httpResponse.value(forHTTPHeaderField: "Set-Cookie") ?? ""
        if let extractedSid = parseSIDCookie(from: cookies), !extractedSid.isEmpty {
            return extractedSid
        }

        if httpResponse.statusCode == 403 || httpResponse.statusCode == 401 {
            throw APIError.unauthorized
        }
        throw APIError.custom("Failed to retrieve authentication cookie (SID). Please check your credentials.")
    }

    private var canRefreshSession: Bool {
        !baseURL.isEmpty && !username.isEmpty && !password.isEmpty
    }

    private func requestWithSessionRefresh<T>(
        _ operation: () async throws -> T
    ) async throws -> T {
        do {
            return try await operation()
        } catch let error as APIError {
            guard shouldRefreshSession(for: error) else { throw error }
            try await refreshSession()
            return try await operation()
        }
    }

    private func requestVoidWithSessionRefresh(
        _ operation: () async throws -> Void
    ) async throws {
        do {
            try await operation()
        } catch let error as APIError {
            guard shouldRefreshSession(for: error) else { throw error }
            try await refreshSession()
            try await operation()
        }
    }

    private func refreshSession() async throws {
        guard canRefreshSession else { throw APIError.unauthorized }
        let newSid = try await authenticate(
            url: baseURL,
            username: username,
            password: password,
            fallbackUrl: fallbackURL.isEmpty ? nil : fallbackURL
        )
        sid = newSid
        onTokenRefresh?(newSid)
    }

    private func shouldRefreshSession(for error: APIError) -> Bool {
        guard canRefreshSession else { return false }
        switch error {
        case .unauthorized:
            return true
        case .httpError(let statusCode, _):
            return statusCode == 401 || statusCode == 403
        case .bothURLsFailed(let primaryError, let fallbackError):
            return shouldRefreshSession(forAny: primaryError) || shouldRefreshSession(forAny: fallbackError)
        default:
            return false
        }
    }

    private func shouldRefreshSession(forAny error: Error) -> Bool {
        guard let apiError = error as? APIError else { return false }
        return shouldRefreshSession(for: apiError)
    }
    
    private func logResponse(_ response: URLResponse, data: Data?) {
        guard let http = response as? HTTPURLResponse else { return }
        let url = response.url?.absoluteString ?? "unknown"
        let status = http.statusCode
        let size = data?.count ?? 0
        let msg = "<-- \(status) \(url) (\(size) bytes)"
        if status >= 400 {
            AppLogger.shared.warn(msg, source: "qBittorrent")
        } else {
            AppLogger.shared.network(msg, source: "qBittorrent")
        }
    }
}
