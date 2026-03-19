import Foundation

actor HealthchecksAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .healthchecks, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey
    }

    // MARK: - Ping

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let primary = await engine.pingURL("\(baseURL)/api/v3/checks/", extraHeaders: authHeaders())
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL("\(fallbackURL)/api/v3/checks/", extraHeaders: authHeaders())
    }

    // MARK: - Auth

    func authenticate(url: String, apiKey: String, fallbackUrl: String? = nil) async throws {
        let cleanURL = Self.cleanURL(url)
        _ = try await engine.requestData(
            baseURL: cleanURL,
            fallbackURL: Self.cleanURL(fallbackUrl ?? ""),
            path: "/api/v3/checks/",
            headers: ["X-Api-Key": apiKey]
        )
    }

    // MARK: - Pinging API

    enum HealthchecksPingSignal: Hashable {
        case success
        case start
        case fail
        case log
        case exitStatus(Int)
    }

    func signal(check: HealthchecksCheck, kind: HealthchecksPingSignal, body: String? = nil, runId: UUID? = nil) async throws {
        guard let pingUrl = check.pingUrl else { throw APIError.invalidURL }
        try await signal(pingUrl: pingUrl, kind: kind, body: body, runId: runId, create: nil)
    }

    func signal(pingUrl: String, kind: HealthchecksPingSignal, body: String? = nil, runId: UUID? = nil, create: Bool? = nil) async throws {
        let url = buildPingURL(base: pingUrl, signal: kind)
        _ = try await sendPing(urlString: url, body: body, runId: runId, create: create)
    }

    func signal(pingKey: String, slug: String, kind: HealthchecksPingSignal, body: String? = nil, runId: UUID? = nil, create: Bool? = nil) async throws {
        let cleanKey = pingKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanSlug = slug.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanKey.isEmpty, !cleanSlug.isEmpty else { throw APIError.invalidURL }
        let base = "https://hc-ping.com/\(cleanKey)/\(cleanSlug)"
        let url = buildPingURL(base: base, signal: kind)
        _ = try await sendPing(urlString: url, body: body, runId: runId, create: create)
    }

    // MARK: - Checks

    func listChecks(slug: String? = nil, tags: [String] = []) async throws -> [HealthchecksCheck] {
        let path = buildChecksPath(slug: slug, tags: tags)
        let response: HealthchecksChecksResponse = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: authHeaders()
        )
        return response.checks
    }

    func getCheck(idOrKey: String) async throws -> HealthchecksCheck {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/checks/\(idOrKey)",
            headers: authHeaders()
        )
    }

    func createCheck(_ payload: HealthchecksCheckPayload) async throws {
        let data = try JSONEncoder().encode(payload)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/checks/",
            method: "POST",
            headers: authHeaders(json: true),
            body: data
        )
    }

    func updateCheck(id: String, payload: HealthchecksCheckPayload) async throws {
        let data = try JSONEncoder().encode(payload)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/checks/\(id)",
            method: "POST",
            headers: authHeaders(json: true),
            body: data
        )
    }

    func pauseCheck(id: String) async throws {
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/checks/\(id)/pause",
            method: "POST",
            headers: authHeaders()
        )
    }

    func resumeCheck(id: String) async throws {
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/checks/\(id)/resume",
            method: "POST",
            headers: authHeaders()
        )
    }

    func deleteCheck(id: String) async throws {
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/checks/\(id)",
            method: "DELETE",
            headers: authHeaders()
        )
    }

    // MARK: - Pings

    func listPings(checkId: String) async throws -> [HealthchecksPing] {
        let response: HealthchecksPingResponse = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/checks/\(checkId)/pings/",
            headers: authHeaders()
        )
        return response.pings
    }

    func getPingBody(checkId: String, n: Int) async throws -> String {
        try await engine.requestString(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/checks/\(checkId)/pings/\(n)/body",
            headers: authHeaders()
        )
    }

    // MARK: - Flips

    func listFlips(checkIdOrKey: String) async throws -> [HealthchecksFlip] {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/checks/\(checkIdOrKey)/flips/",
            headers: authHeaders()
        )
    }

    // MARK: - Channels

    func listChannels() async throws -> [HealthchecksChannel] {
        let response: HealthchecksChannelsResponse = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/channels/",
            headers: authHeaders()
        )
        return response.channels
    }

    // MARK: - Badges

    func listBadges() async throws -> HealthchecksBadgesResponse {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/badges/",
            headers: authHeaders()
        )
    }

    // MARK: - Helpers

    private func buildPingURL(base: String, signal: HealthchecksPingSignal) -> String {
        let trimmed = base
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
        switch signal {
        case .success:
            return trimmed
        case .start:
            return trimmed + "/start"
        case .fail:
            return trimmed + "/fail"
        case .log:
            return trimmed + "/log"
        case .exitStatus(let status):
            return trimmed + "/\(status)"
        }
    }

    private func sendPing(urlString: String, body: String?, runId: UUID?, create: Bool?) async throws -> String {
        guard var components = URLComponents(string: urlString) else { throw APIError.invalidURL }
        var items = components.queryItems ?? []
        if let runId {
            items.append(URLQueryItem(name: "rid", value: runId.uuidString))
        }
        if let create {
            items.append(URLQueryItem(name: "create", value: create ? "1" : "0"))
        }
        components.queryItems = items.isEmpty ? nil : items
        guard let url = components.url else { throw APIError.invalidURL }

        let (base, path) = splitURL(url)
        let data = body?.data(using: .utf8)
        var headers: [String: String] = [:]
        if body != nil {
            headers["Content-Type"] = "text/plain"
        }
        return try await engine.requestString(
            baseURL: base,
            fallbackURL: "",
            path: path,
            method: body == nil ? "GET" : "POST",
            headers: headers,
            body: data
        )
    }

    private func splitURL(_ url: URL) -> (String, String) {
        var base = "\(url.scheme ?? "https")://\(url.host ?? "")"
        if let port = url.port {
            base += ":\(port)"
        }
        var path = url.path
        if let query = url.query, !query.isEmpty {
            path += "?\(query)"
        }
        if path.isEmpty { path = "/" }
        return (base, path)
    }

    private func authHeaders(json: Bool = false) -> [String: String] {
        var headers = ["X-Api-Key": apiKey]
        if json {
            headers["Content-Type"] = "application/json"
        }
        return headers
    }

    private func buildChecksPath(slug: String?, tags: [String]) -> String {
        var components = URLComponents()
        components.path = "/api/v3/checks/"
        var items: [URLQueryItem] = []
        if let slug, !slug.isEmpty {
            items.append(URLQueryItem(name: "slug", value: slug))
        }
        for tag in tags where !tag.isEmpty {
            items.append(URLQueryItem(name: "tag", value: tag))
        }
        if !items.isEmpty {
            components.queryItems = items
        }
        return components.url?.absoluteString ?? "/api/v3/checks/"
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }
}
