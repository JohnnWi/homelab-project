import Foundation

actor PatchmonAPIClient {
    private struct HostsCacheEntry {
        let response: PatchmonHostsResponse
        let savedAt: Date
    }

    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var tokenKey: String = ""
    private var tokenSecret: String = ""
    private let maxRateLimitAttempts = 5
    private let hostsCacheTTL: TimeInterval = 45
    private var hostsCache: [String: HostsCacheEntry] = [:]

    /// Tracks the current rate-limit retry task to avoid spawning multiple
    /// concurrent retry chains when several requests hit a 429 at the same time.
    private var activeRateLimitRetryTask: Task<Void, Never>?

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .patchmon, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, tokenKey: String, tokenSecret: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.tokenKey = tokenKey.trimmingCharacters(in: .whitespacesAndNewlines)
        self.tokenSecret = tokenSecret
        self.hostsCache.removeAll()
        self.activeRateLimitRetryTask?.cancel()
        self.activeRateLimitRetryTask = nil
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .patchmon, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    // MARK: - Ping

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let headers = authHeaders(tokenKey: tokenKey, tokenSecret: tokenSecret)
        let primary = await engine.pingURL("\(baseURL)\(hostsPath(hostGroup: nil))", extraHeaders: headers)
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL("\(fallbackURL)\(hostsPath(hostGroup: nil))", extraHeaders: headers)
    }

    // MARK: - Authentication

    func authenticate(url: String, tokenKey: String, tokenSecret: String, fallbackUrl: String? = nil) async throws {
        do {
            _ = try await requestData(
                baseURL: Self.cleanURL(url),
                fallbackURL: Self.cleanURL(fallbackUrl ?? ""),
                path: hostsPath(hostGroup: nil),
                headers: authHeaders(tokenKey: tokenKey, tokenSecret: tokenSecret)
            )
        } catch {
            throw mapError(error, exhaustedRateLimit: false)
        }
    }

    // MARK: - Hosts

    func getHosts(hostGroup: String? = nil) async throws -> PatchmonHostsResponse {
        let path = hostsPath(hostGroup: hostGroup)
        let response: PatchmonHostsResponse = try await request(path: path)
        hostsCache[hostsCacheKey(hostGroup: hostGroup)] = HostsCacheEntry(response: response, savedAt: Date())
        return response
    }

    func peekHosts(hostGroup: String? = nil) -> PatchmonHostsResponse? {
        let key = hostsCacheKey(hostGroup: hostGroup)
        guard let entry = hostsCache[key] else { return nil }
        guard Date().timeIntervalSince(entry.savedAt) <= hostsCacheTTL else {
            hostsCache.removeValue(forKey: key)
            return nil
        }
        return entry.response
    }

    func getHostInfo(hostId: String) async throws -> PatchmonHostInfo {
        try await request(path: hostPath(hostId: hostId, endpoint: "info"))
    }

    func getHostStats(hostId: String) async throws -> PatchmonHostStats {
        try await request(path: hostPath(hostId: hostId, endpoint: "stats"))
    }

    func getHostSystem(hostId: String) async throws -> PatchmonHostSystem {
        try await request(path: hostPath(hostId: hostId, endpoint: "system"))
    }

    func getHostNetwork(hostId: String) async throws -> PatchmonHostNetwork {
        try await request(path: hostPath(hostId: hostId, endpoint: "network"))
    }

    func getHostPackages(hostId: String, updatesOnly: Bool = true) async throws -> PatchmonPackagesResponse {
        var queryItems: [URLQueryItem] = []
        if updatesOnly {
            queryItems.append(URLQueryItem(name: "updates_only", value: "true"))
        }
        return try await request(path: hostPath(hostId: hostId, endpoint: "packages", queryItems: queryItems))
    }

    func getHostReports(hostId: String, limit: Int = 10) async throws -> PatchmonReportsResponse {
        try await request(
            path: hostPath(
                hostId: hostId,
                endpoint: "package_reports",
                queryItems: [URLQueryItem(name: "limit", value: "\(max(1, limit))")]
            )
        )
    }

    func getHostAgentQueue(hostId: String, limit: Int = 10) async throws -> PatchmonAgentQueueResponse {
        try await request(
            path: hostPath(
                hostId: hostId,
                endpoint: "agent_queue",
                queryItems: [URLQueryItem(name: "limit", value: "\(max(1, limit))")]
            )
        )
    }

    func getHostNotes(hostId: String) async throws -> PatchmonNotesResponse {
        try await request(path: hostPath(hostId: hostId, endpoint: "notes"))
    }

    func getHostIntegrations(hostId: String) async throws -> PatchmonIntegrationsResponse {
        try await request(path: hostPath(hostId: hostId, endpoint: "integrations"))
    }

    func deleteHost(hostId: String) async throws -> PatchmonDeleteResponse {
        let response: PatchmonDeleteResponse = try await request(path: hostPath(hostId: hostId), method: "DELETE")
        hostsCache.removeAll()
        return response
    }

    // MARK: - Helpers

    private func hostsPath(hostGroup: String?) -> String {
        var components = URLComponents()
        components.path = "/api/v1/api/hosts"

        var items: [URLQueryItem] = [URLQueryItem(name: "include", value: "stats")]
        if let hostGroup {
            let clean = hostGroup.trimmingCharacters(in: .whitespacesAndNewlines)
            if !clean.isEmpty {
                items.append(URLQueryItem(name: "hostgroup", value: clean))
            }
        }
        components.queryItems = items

        return components.url?.absoluteString ?? "/api/v1/api/hosts?include=stats"
    }

    private func hostsCacheKey(hostGroup: String?) -> String {
        hostGroup?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? "__all__"
    }

    private func hostPath(hostId: String, endpoint: String? = nil, queryItems: [URLQueryItem] = []) -> String {
        var components = URLComponents()
        let cleanId = hostId.trimmingCharacters(in: .whitespacesAndNewlines)
        if let endpoint, !endpoint.isEmpty {
            components.path = "/api/v1/api/hosts/\(cleanId)/\(endpoint)"
        } else {
            components.path = "/api/v1/api/hosts/\(cleanId)"
        }
        if !queryItems.isEmpty {
            components.queryItems = queryItems
        }
        return components.url?.absoluteString ?? components.path
    }

    private func authHeaders(tokenKey: String, tokenSecret: String) -> [String: String] {
        let credentials = "\(tokenKey):\(tokenSecret)"
        let encoded = Data(credentials.utf8).base64EncodedString()
        return [
            "Authorization": "Basic \(encoded)",
            "Content-Type": "application/json"
        ]
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private func request<T: Decodable>(
        path: String,
        method: String = "GET",
        body: Data? = nil
    ) async throws -> T {
        do {
            return try await requestWithRateLimitRetry { [self] in
                try await self.engine.request(
                    baseURL: self.baseURL,
                    fallbackURL: self.fallbackURL,
                    path: path,
                    method: method,
                    headers: self.authHeaders(tokenKey: self.tokenKey, tokenSecret: self.tokenSecret),
                    body: body
                )
            }
        } catch {
            throw mapError(error, exhaustedRateLimit: isRateLimitError(error))
        }
    }

    private func requestData(
        baseURL: String,
        fallbackURL: String,
        path: String,
        headers: [String: String]
    ) async throws -> Data {
        do {
            return try await requestWithRateLimitRetry { [self] in
                try await self.engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: path,
                    headers: headers
                )
            }
        } catch {
            throw mapError(error, exhaustedRateLimit: isRateLimitError(error))
        }
    }

    private func requestWithRateLimitRetry<T>(
        _ operation: @escaping () async throws -> T
    ) async throws -> T {
        var lastError: Error?

        for attempt in 1...maxRateLimitAttempts {
            while let activeTask = activeRateLimitRetryTask {
                _ = await activeTask.value
            }

            do {
                return try await operation()
            } catch {
                lastError = error
                guard isRateLimitError(error) else {
                    throw error
                }
                guard attempt < maxRateLimitAttempts else {
                    throw error
                }

                let seconds = rateLimitDelaySeconds(for: error, attempt: attempt)
                AppLogger.shared.warn("PatchMon rate limit hit (attempt \(attempt)). Retrying in \(seconds)s.", source: "PatchMon")

                if activeRateLimitRetryTask == nil {
                    let retryTask: Task<Void, Never> = Task {
                        try? await Task.sleep(nanoseconds: UInt64(seconds) * 1_000_000_000)
                    }
                    activeRateLimitRetryTask = retryTask
                    _ = await retryTask.value
                    activeRateLimitRetryTask = nil
                }
            }
        }

        throw lastError ?? APIError.custom(Translations.current().patchmonErrorRateLimited)
    }

    private func isRateLimitError(_ error: Error) -> Bool {
        guard let apiError = error as? APIError else { return false }
        if case APIError.httpError(let status, _) = apiError {
            return status == 429
        }
        if case APIError.bothURLsFailed(let primaryError, let fallbackError) = apiError {
            return isRateLimitError(primaryError) || isRateLimitError(fallbackError)
        }
        return false
    }

    private func rateLimitDelaySeconds(for error: Error, attempt: Int) -> Int {
        let defaultDelay = min(8, Int(pow(2.0, Double(max(0, attempt - 1)))))
        guard let apiError = error as? APIError else { return max(1, defaultDelay) }
        switch apiError {
        case .httpError(_, let body):
            if let delay = extractRetryAfter(from: body) {
                return max(1, min(delay, 60))
            }
        case .bothURLsFailed(let primaryError, _):
            return rateLimitDelaySeconds(for: primaryError, attempt: attempt)
        default:
            break
        }
        return max(1, defaultDelay)
    }

    private func extractRetryAfter(from body: String) -> Int? {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        if let data = trimmed.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            if let retry = object["retryAfter"] as? Int {
                return retry
            }
            if let retry = object["retry_after"] as? Int {
                return retry
            }
            if let retry = object["retryAfter"] as? String, let value = Int(retry) {
                return value
            }
            if let retry = object["retry_after"] as? String, let value = Int(retry) {
                return value
            }
        }

        let digits = trimmed
            .components(separatedBy: CharacterSet.decimalDigits.inverted)
            .filter { !$0.isEmpty }
        if let first = digits.first, let value = Int(first) {
            return value
        }

        return nil
    }

    private func mapError(_ error: Error, exhaustedRateLimit: Bool) -> APIError {
        let t = Translations.current()

        guard let apiError = error as? APIError else {
            return .networkError(error)
        }

        switch apiError {
        case .bothURLsFailed(let primaryError, let fallbackError):
            if isRateLimitError(primaryError) && !isRateLimitError(fallbackError) {
                return mapError(fallbackError, exhaustedRateLimit: exhaustedRateLimit)
            }
            return mapError(primaryError, exhaustedRateLimit: exhaustedRateLimit)

        case .unauthorized:
            return .custom(t.patchmonErrorInvalidCredentials)

        case .httpError(let status, let body):
            let detail = normalizeAPIBody(body)
            let detailLower = detail.lowercased()

            switch status {
            case 401:
                return .custom(t.patchmonErrorInvalidCredentials)

            case 400:
                if detailLower.contains("invalid host id format") {
                    return .custom(t.patchmonErrorInvalidHostId)
                }
                if detailLower.contains("foreign key constraints") {
                    return .custom(t.patchmonErrorDeleteConstraint)
                }
                return .custom(t.patchmonErrorBadRequest)

            case 403:
                if detailLower.contains("ip address not allowed") {
                    return .custom(t.patchmonErrorIpNotAllowed)
                }
                if detailLower.contains("access denied") {
                    return .custom(t.patchmonErrorAccessDenied)
                }
                return .custom(t.patchmonErrorForbidden)

            case 404:
                if detailLower.contains("host not found") {
                    return .custom(t.patchmonErrorHostNotFound)
                }
                return .custom(t.patchmonErrorNotFound)

            case 429:
                return .custom(exhaustedRateLimit ? t.patchmonErrorRateLimited : t.patchmonErrorRetrying)

            case 500...599:
                return .custom(t.patchmonErrorServer)

            default:
                return .httpError(statusCode: status, body: detail)
            }

        default:
            return apiError
        }
    }

    private func normalizeAPIBody(_ body: String) -> String {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return trimmed }

        if let data = trimmed.data(using: .utf8),
           let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            for key in ["message", "error", "detail"] {
                if let value = object[key] as? String, !value.isEmpty {
                    return value
                }
            }
        }

        return trimmed
    }
}
