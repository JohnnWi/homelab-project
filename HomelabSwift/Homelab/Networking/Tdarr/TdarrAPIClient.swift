import Foundation

struct TdarrNode: Identifiable, Sendable {
    let id: String
    let name: String
    let online: Bool
    let workers: Int
}

struct TdarrStats: Sendable {
    let totalFileCount: Int
    let totalTranscodeCount: Int
    let totalHealthCheckCount: Int
    let sizeDiffGB: Double
    let tdarrScore: String?
    let healthCheckScore: String?
    let filesNotInTrash: Int
}

actor TdarrAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String?

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .tdarr, instanceId: instanceId)
    }

    func configure(url: String, fallbackUrl: String? = nil, apiKey: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey?.trimmingCharacters(in: .whitespacesAndNewlines)
        if self.apiKey?.isEmpty == true { self.apiKey = nil }
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        do {
            _ = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v2/get-nodes",
                method: "POST",
                headers: authHeaders(),
                body: "{}".data(using: .utf8)
            )
            return true
        } catch {
            return false
        }
    }

    func authenticate(url: String, fallbackUrl: String? = nil, apiKey: String? = nil) async throws {
        let cleanedURL = Self.cleanURL(url)
        let cleanedFallback = Self.cleanURL(fallbackUrl ?? "")
        guard !cleanedURL.isEmpty else {
            throw APIError.notConfigured
        }

        var headers = baseHeaders()
        if let key = apiKey?.trimmingCharacters(in: .whitespacesAndNewlines), !key.isEmpty {
            headers["x-api-key"] = key
        }

        _ = try await engine.requestData(
            baseURL: cleanedURL,
            fallbackURL: cleanedFallback,
            path: "/api/v2/get-nodes",
            method: "POST",
            headers: headers,
            body: "{}".data(using: .utf8)
        )
    }

    func getNodes() async throws -> [TdarrNode] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/get-nodes",
            method: "POST",
            headers: authHeaders(),
            body: "{}".data(using: .utf8)
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return []
        }
        return json.compactMap { key, value in
            guard let nodeInfo = value as? [String: Any] else { return nil }
            let name = stringValue(nodeInfo["nodeName"]) ?? key
            let paused = boolValue(nodeInfo["nodePaused"]) ?? false
            let workers = intValue(nodeInfo["workers"])
            return TdarrNode(
                id: key,
                name: name,
                online: !paused,
                workers: workers
            )
        }
    }

    func getStats() async throws -> TdarrStats {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v2/get-stats",
            method: "POST",
            headers: authHeaders(),
            body: "{}".data(using: .utf8)
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "Tdarr", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return TdarrStats(
            totalFileCount: intValue(json["totalFileCount"]),
            totalTranscodeCount: intValue(json["totalTranscodeCount"]),
            totalHealthCheckCount: intValue(json["totalHealthCheckCount"]),
            sizeDiffGB: doubleValue(json["sizeDiff"]),
            tdarrScore: stringValue(json["tdarrScore"]),
            healthCheckScore: stringValue(json["healthCheckScore"]),
            filesNotInTrash: intValue(json["filesNotInTrash"])
        )
    }

    private func baseHeaders() -> [String: String] {
        return [
            "Content-Type": "application/json",
            "Accept": "application/json"
        ]
    }

    private func authHeaders() -> [String: String] {
        var headers = baseHeaders()
        if let key = apiKey, !key.isEmpty {
            headers["x-api-key"] = key
        }
        return headers
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

    private func intValue(_ value: Any?) -> Int {
        if let v = value as? Int { return v }
        if let v = value as? Double { return Int(v) }
        if let v = value as? String, let i = Int(v) { return i }
        return 0
    }

    private func doubleValue(_ value: Any?) -> Double {
        if let v = value as? Double { return v }
        if let v = value as? Int { return Double(v) }
        if let v = value as? String, let d = Double(v) { return d }
        return 0
    }

    private func boolValue(_ value: Any?) -> Bool? {
        if let v = value as? Bool { return v }
        if let v = value as? Int { return v != 0 }
        return nil
    }
}
