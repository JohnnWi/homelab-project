import Foundation

// MARK: - Plex Data Models

struct PlexServerInfo: Sendable {
    let name: String
    let version: String
    let platform: String
    let platformVersion: String
    let machineIdentifier: String
}

struct PlexLibrary: Identifiable, Sendable {
    let key: String
    let title: String
    let type: String
    let itemCount: Int     // fetched from /library/sections/{key}/all with size=0
    let episodeCount: Int  // for shows: total episodes
    let icon: String

    var id: String { key }

    var sfSymbol: String {
        switch type {
        case "movie":  return "film.fill"
        case "show":   return "tv.fill"
        case "artist": return "music.note"
        case "photo":  return "photo.fill"
        default:       return "folder.fill"
        }
    }
}

struct PlexStats: Sendable {
    let totalMovies: Int
    let totalShows: Int
    let totalEpisodes: Int
    let totalMusic: Int     // albums or artists
    let totalPhotos: Int
    let totalItems: Int
}

struct PlexSession: Identifiable, Sendable {
    let sessionId: String
    let title: String
    let type: String
    let username: String
    let playerName: String
    let playerPlatform: String
    let playerState: String
    let isLocal: Bool
    let bandwidth: Int      // kbps
    let viewOffset: Int     // ms
    let duration: Int       // ms
    let grandparentTitle: String?
    let parentIndex: Int?
    let index: Int?
    let year: Int?
    let videoResolution: String?
    let audioChannels: Int?
    let transcodeDecision: String?

    var id: String { sessionId }

    var progressRatio: Double {
        guard duration > 0 else { return 0 }
        return min(1.0, Double(viewOffset) / Double(duration))
    }

    var displayTitle: String {
        if let grandparent = grandparentTitle, let season = parentIndex, let episode = index {
            return "\(grandparent) — S\(season)E\(episode)"
        }
        return title
    }

    var displaySubtitle: String {
        if grandparentTitle != nil { return title }
        if let year { return "\(year)" }
        return ""
    }

    var bandwidthMbps: Double { Double(bandwidth) / 1000.0 }

    var isTranscoding: Bool { transcodeDecision?.lowercased() == "transcode" }

    var resolutionLabel: String {
        switch videoResolution?.lowercased() {
        case "4k": return "4K"
        case "1080": return "1080p"
        case "720": return "720p"
        case "480": return "480p"
        default: return videoResolution?.uppercased() ?? ""
        }
    }
}

struct PlexRecentItem: Identifiable, Sendable {
    let ratingKey: String
    let title: String
    let type: String
    let addedAt: Date
    let year: Int?
    let grandparentTitle: String?

    var id: String { ratingKey }

    var displayTitle: String {
        if let g = grandparentTitle { return "\(g) — \(title)" }
        return title
    }

    var sfSymbol: String {
        switch type {
        case "movie":   return "film.fill"
        case "episode": return "tv.fill"
        case "track":   return "music.note"
        default:        return "play.rectangle.fill"
        }
    }
}

struct PlexHistoryItem: Identifiable, Sendable {
    let historyId: Int
    let title: String
    let type: String
    let viewedAt: Date
    let grandparentTitle: String?
    let parentIndex: Int?
    let index: Int?

    var id: Int { historyId }

    var displayTitle: String {
        if let g = grandparentTitle, let s = parentIndex, let e = index {
            return "\(g) — S\(s)E\(e)"
        }
        return title
    }
}

struct PlexDashboardData: Sendable {
    let serverInfo: PlexServerInfo
    let libraries: [PlexLibrary]
    let stats: PlexStats
    let activeSessions: [PlexSession]
    let recentlyAdded: [PlexRecentItem]
    let watchHistory: [PlexHistoryItem]
}

// MARK: - Plex API Client

actor PlexAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var token: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .plex, instanceId: instanceId)
    }

    func configure(url: String, token: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.token = token.trimmingCharacters(in: .whitespacesAndNewlines)
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .plex, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !token.isEmpty else { return false }
        let path = "/identity"
        let primary = await engine.pingURL(baseURL + path, extraHeaders: authHeaders())
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL(fallbackURL + path, extraHeaders: authHeaders())
    }

    func authenticate(url: String, token: String, fallbackUrl: String? = nil) async throws {
        let cleanedURL = Self.cleanURL(url)
        let cleanedFallback = Self.cleanURL(fallbackUrl ?? "")
        let trimmedToken = token.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedURL.isEmpty, !trimmedToken.isEmpty else {
            throw APIError.notConfigured
        }
        _ = try await engine.requestData(
            baseURL: cleanedURL,
            fallbackURL: cleanedFallback,
            path: "/identity",
            headers: authHeaders(for: trimmedToken)
        )
    }

    // MARK: - Server Info

    func getServerInfo() async throws -> PlexServerInfo {
        let data = try await requestJSON(path: "/")
        guard let mc = data["MediaContainer"] as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "Plex", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid server info"]))
        }
        return PlexServerInfo(
            name: mc["friendlyName"] as? String ?? "Plex",
            version: mc["version"] as? String ?? "—",
            platform: mc["platform"] as? String ?? "—",
            platformVersion: mc["platformVersion"] as? String ?? "",
            machineIdentifier: mc["machineIdentifier"] as? String ?? ""
        )
    }

    // MARK: - Libraries (with real item counts)

    func getLibraries() async throws -> [PlexLibrary] {
        // Step 1: get section list
        let data = try await requestJSON(path: "/library/sections")
        guard let mc = data["MediaContainer"] as? [String: Any],
              let dirs = mc["Directory"] as? [[String: Any]] else {
            return []
        }

        // Step 2: for each section, fetch /library/sections/{key}/all?size=0 to get totalSize
        var libraries: [PlexLibrary] = []
        for dir in dirs {
            let key = dir["key"] as? String ?? ""
            let type = dir["type"] as? String ?? "unknown"
            let title = dir["title"] as? String ?? "Unknown"

            // Fetch total items count
            var itemCount = 0
            var episodeCount = 0
            if !key.isEmpty {
                let countPath = "/library/sections/\(key)/all?X-Plex-Container-Start=0&X-Plex-Container-Size=0"
                if let countData = try? await requestJSON(path: countPath),
                   let countMC = countData["MediaContainer"] as? [String: Any] {
                    if let ts = countMC["totalSize"] as? Int { itemCount = ts }
                    else if let s = countMC["size"] as? Int { itemCount = s }
                }

                // For shows, also get total episodes
                if type == "show" {
                    let epPath = "/library/sections/\(key)/all?type=4&X-Plex-Container-Start=0&X-Plex-Container-Size=0"
                    if let epData = try? await requestJSON(path: epPath),
                       let epMC = epData["MediaContainer"] as? [String: Any] {
                        if let ts = epMC["totalSize"] as? Int { episodeCount = ts }
                        else if let s = epMC["size"] as? Int { episodeCount = s }
                    }
                }
            }

            libraries.append(PlexLibrary(
                key: key,
                title: title,
                type: type,
                itemCount: itemCount,
                episodeCount: episodeCount,
                icon: dir["thumb"] as? String ?? ""
            ))
        }
        return libraries
    }

    // MARK: - Computed Stats from libraries

    func computeStats(from libraries: [PlexLibrary]) -> PlexStats {
        let movies = libraries.filter { $0.type == "movie" }.reduce(0) { $0 + $1.itemCount }
        let shows  = libraries.filter { $0.type == "show" }.reduce(0) { $0 + $1.itemCount }
        let eps    = libraries.filter { $0.type == "show" }.reduce(0) { $0 + $1.episodeCount }
        let music  = libraries.filter { $0.type == "artist" }.reduce(0) { $0 + $1.itemCount }
        let photos = libraries.filter { $0.type == "photo" }.reduce(0) { $0 + $1.itemCount }
        let total  = movies + shows + music + photos
        return PlexStats(
            totalMovies: movies,
            totalShows: shows,
            totalEpisodes: eps,
            totalMusic: music,
            totalPhotos: photos,
            totalItems: total
        )
    }

    // MARK: - Active Sessions

    func getActiveSessions() async throws -> [PlexSession] {
        let data = try await requestJSON(path: "/status/sessions")
        guard let mc = data["MediaContainer"] as? [String: Any],
              let metadata = mc["Metadata"] as? [[String: Any]] else {
            return []
        }
        return metadata.map { parseSession($0) }
    }

    // MARK: - Recently Added

    func getRecentlyAdded(limit: Int = 20) async throws -> [PlexRecentItem] {
        let data = try await requestJSON(path: "/library/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=\(limit)")
        guard let mc = data["MediaContainer"] as? [String: Any],
              let metadata = mc["Metadata"] as? [[String: Any]] else {
            return []
        }
        return metadata.compactMap { item in
            PlexRecentItem(
                ratingKey: item["ratingKey"] as? String ?? UUID().uuidString,
                title: item["title"] as? String ?? "Unknown",
                type: item["type"] as? String ?? "unknown",
                addedAt: Date(timeIntervalSince1970: TimeInterval(item["addedAt"] as? Int ?? 0)),
                year: item["year"] as? Int,
                grandparentTitle: item["grandparentTitle"] as? String
            )
        }
    }

    // MARK: - Watch History

    func getWatchHistory(limit: Int = 30) async throws -> [PlexHistoryItem] {
        let data = try await requestJSON(path: "/status/sessions/history/all?sort=viewedAt:desc&X-Plex-Container-Start=0&X-Plex-Container-Size=\(limit)")
        guard let mc = data["MediaContainer"] as? [String: Any],
              let metadata = mc["Metadata"] as? [[String: Any]] else {
            return []
        }
        return metadata.compactMap { item in
            let viewedAtInt = item["viewedAt"] as? Int ?? 0
            let historyId: Int
            if let hk = item["historyKey"] as? String, let last = hk.split(separator: "/").last, let id = Int(last) {
                historyId = id
            } else {
                historyId = viewedAtInt
            }
            return PlexHistoryItem(
                historyId: historyId,
                title: item["title"] as? String ?? "Unknown",
                type: item["type"] as? String ?? "unknown",
                viewedAt: Date(timeIntervalSince1970: TimeInterval(viewedAtInt)),
                grandparentTitle: item["grandparentTitle"] as? String,
                parentIndex: item["parentIndex"] as? Int,
                index: item["index"] as? Int
            )
        }
    }

    // MARK: - Dashboard

    func getDashboard() async throws -> PlexDashboardData {
        async let serverTask   = getServerInfo()
        async let libraryTask  = getLibraries()
        async let sessionTask  = getActiveSessions()
        async let recentTask   = getRecentlyAdded(limit: 16)
        async let historyTask  = getWatchHistory(limit: 30)

        let server    = try await serverTask
        let libraries = try await libraryTask
        let sessions  = try await sessionTask
        let recent    = try await recentTask
        let history   = try await historyTask
        let stats     = computeStats(from: libraries)

        return PlexDashboardData(
            serverInfo: server,
            libraries: libraries,
            stats: stats,
            activeSessions: sessions,
            recentlyAdded: recent,
            watchHistory: history
        )
    }

    // MARK: - Private Helpers

    private func requestJSON(path: String) async throws -> [String: Any] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: authHeaders()
        )
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "Plex", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return object
    }

    private func parseSession(_ item: [String: Any]) -> PlexSession {
        let user    = item["User"]    as? [String: Any] ?? [:]
        let player  = item["Player"]  as? [String: Any] ?? [:]
        let session = item["Session"] as? [String: Any] ?? [:]
        let media   = (item["Media"]  as? [[String: Any]])?.first ?? [:]
        let stream  = (media["Part"]  as? [[String: Any]])?.first

        return PlexSession(
            sessionId:         session["id"]          as? String ?? UUID().uuidString,
            title:             item["title"]           as? String ?? "Unknown",
            type:              item["type"]            as? String ?? "unknown",
            username:          user["title"]           as? String ?? "Unknown",
            playerName:        player["title"]         as? String ?? player["product"] as? String ?? "Unknown",
            playerPlatform:    player["platform"]      as? String ?? "",
            playerState:       player["state"]         as? String ?? "unknown",
            isLocal:           player["local"]         as? Bool   ?? false,
            bandwidth:         session["bandwidth"]    as? Int    ?? 0,
            viewOffset:        item["viewOffset"]      as? Int    ?? 0,
            duration:          item["duration"]        as? Int    ?? 0,
            grandparentTitle:  item["grandparentTitle"] as? String,
            parentIndex:       item["parentIndex"]     as? Int,
            index:             item["index"]           as? Int,
            year:              item["year"]            as? Int,
            videoResolution:   media["videoResolution"] as? String,
            audioChannels:     media["audioChannels"]  as? Int,
            transcodeDecision: stream.flatMap { $0["decision"] as? String }
        )
    }

    private func authHeaders(for token: String? = nil) -> [String: String] {
        let resolved = (token ?? self.token).trimmingCharacters(in: .whitespacesAndNewlines)
        return [
            "X-Plex-Token":             resolved,
            "Accept":                    "application/json",
            "X-Plex-Client-Identifier": "homelab-ios"
        ]
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }
}
