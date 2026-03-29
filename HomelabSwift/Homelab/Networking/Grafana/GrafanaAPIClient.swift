import Foundation

struct GrafanaHealthInfo: Sendable {
    let version: String
    let commit: String?
    let database: String?
}

struct GrafanaDashboardSummary: Sendable {
    let id: Int
    let uid: String
    let title: String
    let type: String?
    let url: String?
}

struct GrafanaAlert: Sendable {
    let name: String
    let state: String
    let severity: String?
}

actor GrafanaAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .grafana, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiKey.isEmpty else { return false }
        let path = "/api/health"
        let primary = await engine.pingURL(baseURL + path, extraHeaders: authHeaders())
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL(fallbackURL + path, extraHeaders: authHeaders())
    }

    func authenticate(url: String, apiKey: String, fallbackUrl: String? = nil) async throws {
        let cleanedURL = Self.cleanURL(url)
        let cleanedFallback = Self.cleanURL(fallbackUrl ?? "")
        let trimmedKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedURL.isEmpty, !trimmedKey.isEmpty else {
            throw APIError.notConfigured
        }

        _ = try await engine.requestData(
            baseURL: cleanedURL,
            fallbackURL: cleanedFallback,
            path: "/api/health",
            headers: ["Authorization": "Bearer \(trimmedKey)", "Accept": "application/json"]
        )
    }

    func getHealth() async throws -> GrafanaHealthInfo {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/health",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "Grafana", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return GrafanaHealthInfo(
            version: stringValue(json["version"]) ?? "Unknown",
            commit: stringValue(json["commit"]),
            database: stringValue(json["database"])
        )
    }

    func getDashboards() async throws -> [GrafanaDashboardSummary] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/search?type=dash-db",
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            guard let id = intValue(row["id"]), id > 0 else { return nil }
            return GrafanaDashboardSummary(
                id: id,
                uid: stringValue(row["uid"]) ?? "",
                title: stringValue(row["title"]) ?? "Untitled",
                type: stringValue(row["type"]),
                url: stringValue(row["url"])
            )
        }
    }

    func getAlerts() async throws -> [GrafanaAlert] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/alertmanager/grafana/api/v2/alerts",
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            let labels = row["labels"] as? [String: Any]
            let name = stringValue(labels?["alertname"]) ?? stringValue(row["alertname"]) ?? "Unknown"
            let status = row["status"] as? [String: Any]
            let state = stringValue(status?["state"]) ?? stringValue(row["state"]) ?? "unknown"
            let severity = stringValue(labels?["severity"])
            return GrafanaAlert(name: name, state: state, severity: severity)
        }
    }

    private func authHeaders() -> [String: String] {
        return [
            "Authorization": "Bearer \(self.apiKey)",
            "Accept": "application/json"
        ]
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private func stringValue(_ value: Any?) -> String? {
        guard let text = value as? String else { return nil }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private func intValue(_ value: Any?) -> Int? {
        if let v = value as? Int { return v }
        if let v = value as? Double { return Int(v) }
        if let v = value as? String { return Int(v) }
        return nil
    }
}
