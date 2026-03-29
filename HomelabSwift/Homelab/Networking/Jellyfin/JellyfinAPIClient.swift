import Foundation

struct JellyfinSystemInfo: Sendable {
    let serverName: String
    let version: String
    let operatingSystem: String?
    let hasUpdateAvailable: Bool
}

struct JellyfinSession: Sendable {
    let id: String
    let userName: String?
    let client: String?
    let deviceName: String?
    let nowPlayingItem: String?
}

struct JellyfinLibrary: Sendable {
    let id: String
    let name: String
    let collectionType: String?
}

struct JellyfinItemCounts: Sendable {
    let movieCount: Int
    let seriesCount: Int
}

actor JellyfinAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .jellyfin, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiKey.isEmpty else { return false }
        let path = "/System/Info"
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
            path: "/System/Info",
            headers: ["X-Emby-Token": trimmedKey, "Accept": "application/json"]
        )
    }

    func getSystemInfo() async throws -> JellyfinSystemInfo {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/System/Info",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "Jellyfin", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return JellyfinSystemInfo(
            serverName: stringValue(json["ServerName"]) ?? "Jellyfin",
            version: stringValue(json["Version"]) ?? "Unknown",
            operatingSystem: stringValue(json["OperatingSystem"]),
            hasUpdateAvailable: boolValue(json["HasUpdateAvailable"]) ?? false
        )
    }

    func getSessions() async throws -> [JellyfinSession] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/Sessions",
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            let id = stringValue(row["Id"]) ?? UUID().uuidString
            let nowPlaying = (row["NowPlayingItem"] as? [String: Any]).flatMap { stringValue($0["Name"]) }
            return JellyfinSession(
                id: id,
                userName: stringValue(row["UserName"]),
                client: stringValue(row["Client"]),
                deviceName: stringValue(row["DeviceName"]),
                nowPlayingItem: nowPlaying
            )
        }
    }

    func getLibraries() async throws -> [JellyfinLibrary] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/Library/MediaFolders",
            headers: authHeaders()
        )
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let items = json["Items"] as? [[String: Any]] else {
            return []
        }
        return items.compactMap { item in
            guard let id = stringValue(item["Id"]) else { return nil }
            return JellyfinLibrary(
                id: id,
                name: stringValue(item["Name"]) ?? "Unknown",
                collectionType: stringValue(item["CollectionType"])
            )
        }
    }

    func getItemCounts() async throws -> JellyfinItemCounts {
        let movieData = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/Items?Recursive=true&IncludeItemTypes=Movie&Limit=0",
            headers: authHeaders()
        )
        let movieJson = try JSONSerialization.jsonObject(with: movieData) as? [String: Any]
        let movieCount = intValue(movieJson?["TotalRecordCount"])

        let seriesData = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/Items?Recursive=true&IncludeItemTypes=Series&Limit=0",
            headers: authHeaders()
        )
        let seriesJson = try JSONSerialization.jsonObject(with: seriesData) as? [String: Any]
        let seriesCount = intValue(seriesJson?["TotalRecordCount"])

        return JellyfinItemCounts(movieCount: movieCount, seriesCount: seriesCount)
    }

    private func authHeaders() -> [String: String] {
        return [
            "X-Emby-Token": self.apiKey,
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

    private func boolValue(_ value: Any?) -> Bool? {
        if let v = value as? Bool { return v }
        if let v = value as? Int { return v != 0 }
        return nil
    }
}
