import Foundation

struct ImmichServerInfo: Sendable {
    let version: String
    let isInitialized: Bool
}

struct ImmichServerStatistics: Sendable {
    let totalPhotos: Int
    let totalVideos: Int
    let totalSize: Int64
    let usageByUser: [ImmichUserUsage]
}

struct ImmichUserUsage: Sendable {
    let userName: String
    let photos: Int
    let videos: Int
    let usage: Int64
}

struct ImmichAssetStatistics: Sendable {
    let images: Int
    let videos: Int
    let total: Int
}

actor ImmichAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .immich, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiKey.isEmpty else { return false }
        let path = "/api/server/info"
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
            path: "/api/server/info",
            headers: ["x-api-key": trimmedKey, "Accept": "application/json"]
        )
    }

    func getServerInfo() async throws -> ImmichServerInfo {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/server/info",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "Immich", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return ImmichServerInfo(
            version: stringValue(json["version"]) ?? "Unknown",
            isInitialized: boolValue(json["isInitialized"]) ?? true
        )
    }

    func getServerStatistics() async throws -> ImmichServerStatistics {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/server/statistics",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "Immich", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }

        let usageByUser: [ImmichUserUsage]
        if let users = json["usageByUser"] as? [[String: Any]] {
            usageByUser = users.compactMap { user in
                let name = stringValue(user["userName"]) ?? stringValue(user["userFirstName"]) ?? "Unknown"
                return ImmichUserUsage(
                    userName: name,
                    photos: intValue(user["photos"]),
                    videos: intValue(user["videos"]),
                    usage: int64Value(user["usage"])
                )
            }
        } else {
            usageByUser = []
        }

        return ImmichServerStatistics(
            totalPhotos: intValue(json["photos"]),
            totalVideos: intValue(json["videos"]),
            totalSize: int64Value(json["usage"]),
            usageByUser: usageByUser
        )
    }

    func getAssetStatistics() async throws -> ImmichAssetStatistics {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/assets/statistics",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "Immich", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        let images = intValue(json["images"])
        let videos = intValue(json["videos"])
        let total = intValue(json["total"])
        return ImmichAssetStatistics(
            images: images,
            videos: videos,
            total: total > 0 ? total : images + videos
        )
    }

    private func authHeaders() -> [String: String] {
        return [
            "x-api-key": self.apiKey,
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

    private func boolValue(_ value: Any?) -> Bool? {
        if let v = value as? Bool { return v }
        if let v = value as? Int { return v != 0 }
        return nil
    }
}
