import Foundation

actor DockmonAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .dockmon, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        baseURL = Self.cleanURL(url)
        fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .dockmon, instanceId: instanceId, allowSelfSigned: storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let headers = authHeaders(apiKey: apiKey)
        let primary = await engine.pingURL("\(baseURL)/api/hosts", extraHeaders: headers)
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL("\(fallbackURL)/api/hosts", extraHeaders: headers)
    }

    func authenticate(url: String, apiKey: String, fallbackUrl: String? = nil) async throws {
        do {
            _ = try await requestData(
                baseURL: Self.cleanURL(url),
                fallbackURL: Self.cleanURL(fallbackUrl ?? ""),
                path: "/api/hosts",
                headers: authHeaders(apiKey: apiKey)
            )
        } catch {
            throw mapError(error)
        }
    }

    func getHosts() async throws -> [DockmonHost] {
        let response: DockmonHostsResponse = try await request(path: "/api/hosts")
        return response.hosts
    }

    func getContainers(hostId: String? = nil) async throws -> [DockmonContainer] {
        let response: DockmonContainersResponse = try await request(path: containersPath(hostId: hostId))
        return response.containers
    }

    func getDashboard() async throws -> DockmonDashboardData {
        let hosts = try await getHosts()
        var containersByHost: [String: [DockmonContainer]] = [:]

        if hosts.isEmpty {
            let containers = (try? await getContainers(hostId: nil)) ?? []
            return DockmonDashboardData(hosts: [], containersByHost: ["__all__": containers])
        }

        for host in hosts {
            containersByHost[host.id] = (try? await getContainers(hostId: host.id)) ?? []
        }

        return DockmonDashboardData(hosts: hosts, containersByHost: containersByHost)
    }

    func getSummary() async throws -> DockmonSummary {
        let dashboard = try await getDashboard()
        return DockmonSummary(
            runningContainers: dashboard.runningContainers,
            totalContainers: dashboard.totalContainers,
            updateCount: dashboard.updateCount
        )
    }

    func restartContainer(id: String) async throws -> DockmonActionResponse {
        try await requestAction(path: containerPath(id: id, suffix: "restart"))
    }

    func updateContainer(id: String, image: String?) async throws -> DockmonActionResponse {
        let body: Data?
        if let image = image?.trimmingCharacters(in: .whitespacesAndNewlines), !image.isEmpty {
            body = try JSONSerialization.data(withJSONObject: ["image": image], options: [])
        } else {
            body = nil
        }
        return try await requestAction(path: containerPath(id: id, suffix: "update"), body: body)
    }

    func getContainerLogs(id: String, tail: Int = 200) async throws -> String {
        try await requestString(path: containerPath(id: id, suffix: "logs", queryItems: [
            URLQueryItem(name: "tail", value: "\(max(1, tail))")
        ]))
    }

    private func containersPath(hostId: String?) -> String {
        guard let hostId = hostId?.trimmingCharacters(in: .whitespacesAndNewlines), !hostId.isEmpty else {
            return "/api/containers"
        }
        var components = URLComponents()
        components.path = "/api/containers"
        components.queryItems = [URLQueryItem(name: "host_id", value: hostId)]
        return components.url?.absoluteString ?? "/api/containers?host_id=\(hostId)"
    }

    private func containerPath(id: String, suffix: String? = nil, queryItems: [URLQueryItem] = []) -> String {
        let encodedId = id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? id
        var components = URLComponents()
        if let suffix, !suffix.isEmpty {
            components.path = "/api/containers/\(encodedId)/\(suffix)"
        } else {
            components.path = "/api/containers/\(encodedId)"
        }
        if !queryItems.isEmpty {
            components.queryItems = queryItems
        }
        return components.url?.absoluteString ?? components.path
    }

    private func authHeaders(apiKey: String) -> [String: String] {
        [
            "Authorization": "Bearer \(apiKey.trimmingCharacters(in: .whitespacesAndNewlines))",
            "Content-Type": "application/json",
            "Accept": "application/json"
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
            return try await engine.request(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                method: method,
                headers: authHeaders(apiKey: apiKey),
                body: body
            )
        } catch {
            throw mapError(error)
        }
    }

    private func requestString(path: String) async throws -> String {
        do {
            return try await engine.requestString(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                headers: authHeaders(apiKey: apiKey)
            )
        } catch {
            throw mapError(error)
        }
    }

    private func requestAction(path: String, body: Data? = nil) async throws -> DockmonActionResponse {
        do {
            let data = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                method: "POST",
                headers: authHeaders(apiKey: apiKey),
                body: body
            )
            guard !data.isEmpty else {
                return DockmonActionResponse()
            }
            return (try? JSONDecoder().decode(DockmonActionResponse.self, from: data))
                ?? DockmonActionResponse(message: String(data: data, encoding: .utf8))
        } catch {
            throw mapError(error)
        }
    }

    private func requestData(
        baseURL: String,
        fallbackURL: String,
        path: String,
        headers: [String: String]
    ) async throws -> Data {
        do {
            return try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                headers: headers
            )
        } catch {
            throw mapError(error)
        }
    }

    private func mapError(_ error: Error) -> APIError {
        let t = Translations.current()
        if let apiError = error as? APIError {
            switch apiError {
            case .httpError(let statusCode, _):
                if statusCode == 401 || statusCode == 403 {
                    return .custom(t.dockmonErrorInvalidCredentials)
                }
            case .bothURLsFailed(let primary, let fallback):
                return .bothURLsFailed(primaryError: mapError(primary), fallbackError: mapError(fallback))
            default:
                break
            }
            return apiError
        }
        return .custom(error.localizedDescription)
    }
}
