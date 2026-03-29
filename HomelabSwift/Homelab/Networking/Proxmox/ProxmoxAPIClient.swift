import Foundation

struct ProxmoxNodeInfo: Sendable {
    let node: String
    let status: String
    let cpuUsage: Double
    let memoryUsed: Int64
    let memoryTotal: Int64
    let uptime: Int
}

struct ProxmoxVM: Sendable {
    let vmid: Int
    let name: String
    let status: String
    let type: String
    let node: String
    let cpuUsage: Double
    let memoryUsed: Int64
    let memoryMax: Int64
}

struct ProxmoxStorage: Sendable {
    let storage: String
    let type: String
    let used: Int64
    let total: Int64
    let node: String?
    let status: String
}

actor ProxmoxAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .proxmox, instanceId: instanceId)
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
            headers: ["Authorization": "PVEAPIToken=\(trimmedKey)", "Accept": "application/json"]
        )
    }

    func getNodes() async throws -> [ProxmoxNodeInfo] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api2/json/nodes",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rows = json["data"] as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            guard let node = stringValue(row["node"]) else { return nil }
            return ProxmoxNodeInfo(
                node: node,
                status: stringValue(row["status"]) ?? "unknown",
                cpuUsage: doubleValue(row["cpu"]),
                memoryUsed: int64Value(row["mem"]),
                memoryTotal: int64Value(row["maxmem"]),
                uptime: intValue(row["uptime"])
            )
        }
    }

    func getVMs() async throws -> [ProxmoxVM] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api2/json/cluster/resources?type=vm",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rows = json["data"] as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            let vmid = intValue(row["vmid"])
            guard vmid > 0 else { return nil }
            return ProxmoxVM(
                vmid: vmid,
                name: stringValue(row["name"]) ?? "VM \(vmid)",
                status: stringValue(row["status"]) ?? "unknown",
                type: stringValue(row["type"]) ?? "qemu",
                node: stringValue(row["node"]) ?? "unknown",
                cpuUsage: doubleValue(row["cpu"]),
                memoryUsed: int64Value(row["mem"]),
                memoryMax: int64Value(row["maxmem"])
            )
        }
    }

    func getStorages() async throws -> [ProxmoxStorage] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api2/json/cluster/resources?type=storage",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rows = json["data"] as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            guard let storage = stringValue(row["storage"]) else { return nil }
            return ProxmoxStorage(
                storage: storage,
                type: stringValue(row["plugintype"]) ?? stringValue(row["type"]) ?? "unknown",
                used: int64Value(row["disk"]),
                total: int64Value(row["maxdisk"]),
                node: stringValue(row["node"]),
                status: stringValue(row["status"]) ?? "unknown"
            )
        }
    }

    private func authHeaders() -> [String: String] {
        return [
            "Authorization": "PVEAPIToken=\(self.apiKey)",
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

    private func intValue(_ value: Any?) -> Int {
        if let v = value as? Int { return v }
        if let v = value as? Double { return Int(v) }
        if let v = value as? String, let i = Int(v) { return i }
        return 0
    }

    private func int64Value(_ value: Any?) -> Int64 {
        if let v = value as? Int64 { return v }
        if let v = value as? Int { return Int64(v) }
        if let v = value as? Double { return Int64(v) }
        if let v = value as? String, let i = Int64(v) { return i }
        return 0
    }

    private func doubleValue(_ value: Any?) -> Double {
        if let v = value as? Double { return v }
        if let v = value as? Int { return Double(v) }
        if let v = value as? String, let d = Double(v) { return d }
        return 0
    }
}
