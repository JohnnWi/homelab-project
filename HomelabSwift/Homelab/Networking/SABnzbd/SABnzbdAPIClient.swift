import Foundation

struct SABnzbdQueueInfo: Sendable {
    let status: String
    let speedBytes: Double
    let sizeLeft: String
    let timeLeft: String
    let totalSlots: Int
    let mbLeft: Double
    let diskSpaceFree: String?
}

struct SABnzbdHistoryEntry: Sendable {
    let name: String
    let status: String
    let size: String
    let completedAt: String?
    let category: String?
}

struct SABnzbdFullStatus: Sendable {
    let uptime: String?
    let downloadDir: String?
    let completeDir: String?
    let cacheUsed: String?
}

actor SABnzbdAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .sabnzbd, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiKey.isEmpty else { return false }
        let path = "/api?mode=version&output=json&apikey=\(apiKey)"
        let primary = await engine.pingURL(baseURL + path)
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL(fallbackURL + path)
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
            path: "/api?mode=version&output=json&apikey=\(trimmedKey)"
        )
    }

    func getQueue() async throws -> SABnzbdQueueInfo {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api?mode=queue&output=json&apikey=\(apiKey)"
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let queue = json["queue"] as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "SABnzbd", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        let slots = queue["slots"] as? [[String: Any]] ?? []
        return SABnzbdQueueInfo(
            status: stringValue(queue["status"]) ?? "Unknown",
            speedBytes: doubleValue(queue["kbpersec"]) * 1024,
            sizeLeft: stringValue(queue["sizeleft"]) ?? "0 B",
            timeLeft: stringValue(queue["timeleft"]) ?? "0:00:00",
            totalSlots: slots.count,
            mbLeft: doubleValue(queue["mbleft"]),
            diskSpaceFree: stringValue(queue["diskspace1"])
        )
    }

    func getHistory(limit: Int = 10) async throws -> [SABnzbdHistoryEntry] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api?mode=history&output=json&limit=\(limit)&apikey=\(apiKey)"
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let history = json["history"] as? [String: Any],
              let slots = history["slots"] as? [[String: Any]] else {
            return []
        }
        return slots.compactMap { slot in
            SABnzbdHistoryEntry(
                name: stringValue(slot["name"]) ?? "Unknown",
                status: stringValue(slot["status"]) ?? "Unknown",
                size: stringValue(slot["size"]) ?? "0 B",
                completedAt: stringValue(slot["completed"]),
                category: stringValue(slot["category"])
            )
        }
    }

    func getFullStatus() async throws -> SABnzbdFullStatus {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api?mode=fullstatus&output=json&apikey=\(apiKey)"
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let status = json["status"] as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "SABnzbd", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return SABnzbdFullStatus(
            uptime: stringValue(status["uptime"]),
            downloadDir: stringValue(status["download_dir"]),
            completeDir: stringValue(status["complete_dir"]),
            cacheUsed: stringValue(status["cache_art"])
        )
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

    private func doubleValue(_ value: Any?) -> Double {
        if let v = value as? Double { return v }
        if let v = value as? Int { return Double(v) }
        if let v = value as? String, let d = Double(v) { return d }
        return 0
    }
}
