import Foundation

struct LidarrLookupArtist: Identifiable, Sendable {
    let id: String
    let title: String
    let subtitle: String?
    let supporting: String?
    let status: String?
    let posterURL: String?
    let detailsURL: String?
    let details: [String: String]
    let requestForeignArtistId: String?
}

actor LidarrAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .lidarr, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .lidarr, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiKey.isEmpty else { return false }
        let path = "/api/v1/system/status"
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
        
        let path = "/api/v1/system/status"
        _ = try await engine.requestData(
            baseURL: cleanedURL,
            fallbackURL: cleanedFallback,
            path: path,
            headers: ["X-Api-Key": trimmedKey]
        )
    }

    func getSystemStatus() async throws -> LidarrSystemStatus {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/system/status",
            headers: authHeaders()
        )
    }

    func getAlbums() async throws -> [LidarrAlbum] {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/album",
            headers: authHeaders()
        )
    }

    func getQueue() async throws -> LidarrQueueResponse {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/queue?page=1&pageSize=20&sortKey=timeLeft&sortDirection=ascending",
            headers: authHeaders()
        )
    }

    func getHistory() async throws -> LidarrHistoryResponse {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/history?page=1&pageSize=20&sortKey=date&sortDirection=descending",
            headers: authHeaders()
        )
    }

    func getHealthMessages() async -> [String] {
        do {
            let data = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v1/health",
                headers: authHeaders()
            )
            guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
            return rows.compactMap { row in
                stringValue(row["message"]) ?? stringValue(row["type"]) ?? stringValue(row["source"])
            }
        } catch {
            return []
        }
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    func getUpcomingTitles(limit: Int = 8) async -> [String] {
        let now = Date()
        let end = Calendar.current.date(byAdding: .day, value: 14, to: now) ?? now
        let path = "/api/v1/calendar?start=\(Self.dateFormatter.string(from: now))&end=\(Self.dateFormatter.string(from: end))"

        do {
            let data = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                headers: authHeaders()
            )
            guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
            return rows.compactMap { row in
                stringValue(row["title"]) ?? stringValue(row["albumTitle"])
            }
            .prefix(limit)
            .map { $0 }
        } catch {
            return []
        }
    }

    func triggerAlbumSearch() async throws {
        try await runCommand(candidates: ["MissingAlbumSearch", "AlbumSearch"])
    }

    func refreshArtistIndex() async throws {
        try await runCommand(candidates: ["RefreshArtist", "RefreshMonitoredDownloads"])
    }

    func triggerRSSSync() async throws {
        try await runCommand(candidates: ["RssSync", "RSSSync"])
    }

    func rescanFolders() async throws {
        try await runCommand(candidates: ["RescanFolders", "RescanArtist", "RescanArtists"])
    }

    func triggerDownloadedAlbumsScan() async throws {
        try await runCommand(candidates: ["DownloadedAlbumsScan", "CheckForFinishedDownload"])
    }

    func triggerHealthCheck() async throws {
        try await runCommand(candidates: ["HealthCheck", "CheckHealth"])
    }

    func searchArtists(term: String, limit: Int = 20) async throws -> [LidarrLookupArtist] {
        let query = term.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return [] }

        let encoded = Self.encodeQuery(query)
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/artist/lookup?term=\(encoded)",
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }

        return rows.prefix(max(1, limit)).compactMap { row in
            let title = stringValue(row["artistName"]) ?? stringValue(row["sortName"])
            guard let title else { return nil }

            let disambiguation = stringValue(row["disambiguation"])
            let artistType = stringValue(row["artistType"])
            let subtitleText = [disambiguation, artistType].compactMap { $0 }.joined(separator: " • ")
            let subtitle = subtitleText.isEmpty ? nil : subtitleText

            let monitored = boolValue(row["monitored"]) ?? false
            let ended = boolValue(row["ended"]) ?? false
            let status: String
            if ended {
                status = "Ended"
            } else if monitored {
                status = "Monitored"
            } else {
                status = "Unmonitored"
            }

            let supporting = stringValue(row["status"])
            let foreignArtistId = stringValue(row["foreignArtistId"])
            let id = foreignArtistId ?? stringValue(row["artistName"]) ?? title
            let country = stringValue(row["country"])
            let sortName = stringValue(row["sortName"])
            let genresText = (row["genres"] as? [Any])?
                .compactMap(stringValue)
                .prefix(3)
                .joined(separator: ", ")
            let genres = genresText?.isEmpty == false ? genresText : nil
            let overview = stringValue(row["overview"])
            let details = compactDetails([
                ("MusicBrainz", foreignArtistId),
                ("Type", artistType),
                ("Country", country),
                ("Sort Name", sortName),
                ("Genres", genres),
                ("Overview", overview)
            ])

            return LidarrLookupArtist(
                id: id,
                title: title,
                subtitle: subtitle,
                supporting: supporting,
                status: status,
                posterURL: resolvePosterURL(from: row),
                detailsURL: foreignArtistId.map { "https://musicbrainz.org/artist/\($0)" },
                details: details,
                requestForeignArtistId: foreignArtistId
            )
        }
    }

    func requestArtistFromLookup(_ artist: LidarrLookupArtist, selection: ArrRequestSelection? = nil) async throws {
        guard let foreignArtistId = artist.requestForeignArtistId, !foreignArtistId.isEmpty else {
            throw APIError.custom("Missing artist id for Lidarr request")
        }
        let configuration = try await requestConfiguration(for: artist)
        let resolved = try resolveSelection(configuration: configuration, selection: selection)
        guard let qualityProfileId = resolved.qualityProfile?.idValue else {
            throw APIError.custom("No Lidarr quality profile configured")
        }
        guard let metadataProfileId = resolved.metadataProfile?.idValue else {
            throw APIError.custom("No Lidarr metadata profile configured")
        }
        guard let rootFolderPath = resolved.rootFolder?.pathValue else {
            throw APIError.custom("No Lidarr root folder configured")
        }

        let payload: [String: Any] = [
            "artistName": artist.title,
            "foreignArtistId": foreignArtistId,
            "qualityProfileId": qualityProfileId,
            "metadataProfileId": metadataProfileId,
            "rootFolderPath": rootFolderPath,
            "monitored": true,
            "addOptions": [
                "searchForMissingAlbums": true
            ]
        ]

        let body = try JSONSerialization.data(withJSONObject: payload)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/artist",
            method: "POST",
            headers: authHeaders().merging(["Content-Type": "application/json"]) { _, new in new },
            body: body
        )
    }

    func requestConfiguration(for artist: LidarrLookupArtist) async throws -> ArrRequestConfiguration {
        ArrRequestConfiguration(
            title: artist.title,
            qualityProfiles: try await requestOptions(path: "/api/v1/qualityprofile"),
            rootFolders: try await requestOptions(path: "/api/v1/rootfolder"),
            languageProfiles: [],
            metadataProfiles: try await requestOptions(path: "/api/v1/metadataprofile")
        )
    }

    private func runCommand(candidates: [String]) async throws {
        var lastError: Error?
        for name in candidates {
            do {
                let body = try JSONSerialization.data(withJSONObject: ["name": name])
                try await engine.requestVoid(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: "/api/v1/command",
                    method: "POST",
                    headers: authHeaders().merging(["Content-Type": "application/json"]) { _, new in new },
                    body: body
                )
                return
            } catch {
                lastError = error
            }
        }
        throw lastError ?? APIError.custom("Failed to run command")
    }

    private func authHeaders() -> [String: String] {
        return [
            "X-Api-Key": self.apiKey,
            "Accept": "application/json"
        ]
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func encodeQuery(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
    }

    private func firstId(path: String) async throws -> Int? {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }
        for row in rows {
            if let id = intValue(row["id"]), id > 0 {
                return id
            }
        }
        return nil
    }

    private func firstPath(path: String) async throws -> String? {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return nil
        }
        for row in rows {
            if let rootPath = stringValue(row["path"]) {
                return rootPath
            }
        }
        return nil
    }

    private func requestOptions(path: String) async throws -> [ArrRequestOption] {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }
        return rows.compactMap { row in
            let id = intValue(row["id"])
            let folderPath = stringValue(row["path"]) ?? stringValue(row["defaultPath"]) ?? stringValue(row["rootFolderPath"])
            let label = stringValue(row["name"])
                ?? stringValue(row["title"])
                ?? stringValue(row["language"])
                ?? stringValue(row["profileName"])
                ?? folderPath
                ?? id.map(String.init)
            guard let label else { return nil }
            return ArrRequestOption(
                key: "\(path):\(id.map(String.init) ?? folderPath ?? label)",
                label: label,
                idValue: id,
                pathValue: folderPath
            )
        }
    }

    private func resolveSelection(
        configuration: ArrRequestConfiguration,
        selection: ArrRequestSelection?
    ) throws -> ArrRequestSelection {
        if configuration.requiresExplicitSelection && selection == nil {
            throw APIError.requestConfigurationRequired(configuration)
        }

        return ArrRequestSelection(
            qualityProfile: try selectOption(configuration.qualityProfiles, selected: selection?.qualityProfile, configuration: configuration),
            rootFolder: try selectOption(configuration.rootFolders, selected: selection?.rootFolder, configuration: configuration),
            languageProfile: nil,
            metadataProfile: try selectOption(configuration.metadataProfiles, selected: selection?.metadataProfile, configuration: configuration)
        )
    }

    private func selectOption(
        _ options: [ArrRequestOption],
        selected: ArrRequestOption?,
        configuration: ArrRequestConfiguration
    ) throws -> ArrRequestOption? {
        if options.isEmpty { return nil }
        if let selected {
            if let match = options.first(where: {
                $0.key == selected.key ||
                ($0.idValue != nil && $0.idValue == selected.idValue) ||
                ($0.pathValue != nil && $0.pathValue == selected.pathValue)
            }) {
                return match
            }
        }
        if options.count == 1 {
            return options[0]
        }
        throw APIError.requestConfigurationRequired(configuration)
    }

    private func resolvePosterURL(from row: [String: Any]) -> String? {
        let directCandidates = [
            stringValue(row["poster"]),
            stringValue(row["posterUrl"]),
            stringValue(row["posterURL"]),
            stringValue(row["image"]),
            stringValue(row["imageUrl"]),
            stringValue(row["cover"]),
            stringValue(row["thumbnail"])
        ]
        for candidate in directCandidates {
            if let resolved = resolvedServiceArtworkURL(
                candidate,
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                apiKey: apiKey
            ) {
                return resolved
            }
        }

        guard let images = row["images"] as? [[String: Any]] else { return nil }
        var fallback: String?
        for image in images {
            let type = (stringValue(image["coverType"]) ?? "").lowercased()
            let resolved = resolvedServiceArtworkURL(
                stringValue(image["remoteUrl"]),
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                apiKey: apiKey
            ) ?? resolvedServiceArtworkURL(
                stringValue(image["url"]),
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                apiKey: apiKey
            )
            guard let resolved else { continue }
            if type == "poster" || type == "cover" {
                return resolved
            }
            if fallback == nil {
                fallback = resolved
            }
        }
        return fallback
    }

    private func stringValue(_ value: Any?) -> String? {
        guard let text = value as? String else { return nil }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private func boolValue(_ value: Any?) -> Bool? {
        switch value {
        case let bool as Bool:
            return bool
        case let number as NSNumber:
            if CFGetTypeID(number) == CFBooleanGetTypeID() {
                return number.boolValue
            }
            return nil
        case let text as String:
            let normalized = text.lowercased()
            if normalized == "true" || normalized == "1" || normalized == "yes" { return true }
            if normalized == "false" || normalized == "0" || normalized == "no" { return false }
            return nil
        default:
            return nil
        }
    }

    private func intValue(_ value: Any?) -> Int? {
        switch value {
        case let number as NSNumber:
            return number.intValue
        case let text as String:
            return Int(text)
        default:
            return nil
        }
    }

    private func compactDetails(_ pairs: [(String, String?)]) -> [String: String] {
        var details: [String: String] = [:]
        for (label, value) in pairs {
            guard let value else { continue }
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { continue }
            details[label] = trimmed
        }
        return details
    }
}
