import Foundation

struct PBSDatastore: Sendable {
    let name: String
    let comment: String?
}

struct PBSDatastoreUsage: Sendable {
    let store: String
    let total: Int64
    let used: Int64
    let avail: Int64
}

struct PBSTask: Sendable {
    let upid: String
    let worker_type: String
    let status: String?
    let startTime: Int?
    let endTime: Int?
    let node: String?
}

actor PBSAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .proxmoxBackupServer, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiKey.isEmpty else { return false }
        let path = "/api2/json/version"
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
            path: "/api2/json/version",
            headers: ["Authorization": "PBSAPIToken=\(trimmedKey)", "Accept": "application/json"]
        )
    }

    func getDatastores() async throws -> [PBSDatastore] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api2/json/admin/datastore",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rows = json["data"] as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            guard let name = stringValue(row["name"]) else { return nil }
            return PBSDatastore(
                name: name,
                comment: stringValue(row["comment"])
            )
        }
    }

    func getDatastoreUsage() async throws -> [PBSDatastoreUsage] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api2/json/status/datastore-usage",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rows = json["data"] as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            guard let store = stringValue(row["store"]) else { return nil }
            return PBSDatastoreUsage(
                store: store,
                total: int64Value(row["total"]),
                used: int64Value(row["used"]),
                avail: int64Value(row["avail"])
            )
        }
    }

    func getRecentTasks(limit: Int = 10) async throws -> [PBSTask] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api2/json/nodes/localhost/tasks?limit=\(limit)",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rows = json["data"] as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            guard let upid = stringValue(row["upid"]) else { return nil }
            return PBSTask(
                upid: upid,
                worker_type: stringValue(row["worker_type"]) ?? "unknown",
                status: stringValue(row["status"]),
                startTime: intValue(row["starttime"]),
                endTime: intValue(row["endtime"]),
                node: stringValue(row["node"])
            )
        }
    }

    private func authHeaders() -> [String: String] {
        return [
            "Authorization": "PBSAPIToken=\(self.apiKey)",
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

    private func int64Value(_ value: Any?) -> Int64 {
        if let v = value as? Int64 { return v }
        if let v = value as? Int { return Int64(v) }
        if let v = value as? Double { return Int64(v) }
        if let v = value as? String, let i = Int64(v) { return i }
        return 0
    }
}
