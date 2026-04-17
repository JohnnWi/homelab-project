import Foundation

struct SonarrLookupSeries: Identifiable, Sendable {
    let id: String
    let title: String
    let subtitle: String?
    let supporting: String?
    let status: String?
    let posterURL: String?
    let detailsURL: String?
    let details: [String: String]
    let requestTvdbId: Int?
}

actor SonarrAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .sonarr, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .sonarr, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiKey.isEmpty else { return false }
        let path = "/api/v3/system/status"
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
        
        let path = "/api/v3/system/status"
        _ = try await engine.requestData(
            baseURL: cleanedURL,
            fallbackURL: cleanedFallback,
            path: path,
            headers: ["X-Api-Key": trimmedKey]
        )
    }

    func getSystemStatus() async throws -> SonarrSystemStatus {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/system/status",
            headers: authHeaders()
        )
    }

    func getSeries() async throws -> [SonarrSeries] {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/series",
            headers: authHeaders()
        )
    }

    func getQueue() async throws -> SonarrQueueResponse {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/queue?page=1&pageSize=20&sortKey=timeLeft&sortDirection=ascending",
            headers: authHeaders()
        )
    }

    func getHistory() async throws -> SonarrHistoryResponse {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/history?page=1&pageSize=20&sortKey=date&sortDirection=descending",
            headers: authHeaders()
        )
    }

    func getHealthMessages() async -> [String] {
        do {
            let data = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/api/v3/health",
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
        let path = "/api/v3/calendar?start=\(Self.dateFormatter.string(from: now))&end=\(Self.dateFormatter.string(from: end))"

        do {
            let data = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                headers: authHeaders()
            )
            guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
            return rows.compactMap { row in
                if let title = stringValue(row["title"]) {
                    return title
                }
                if let series = row["series"] as? [String: Any] {
                    return stringValue(series["title"])
                }
                return nil
            }
            .prefix(limit)
            .map { $0 }
        } catch {
            return []
        }
    }

    func triggerSeriesSearch() async throws {
        try await runCommand(candidates: ["MissingEpisodeSearch", "SeriesSearch", "EpisodeSearch"])
    }

    func triggerRSSSync() async throws {
        try await runCommand(candidates: ["RssSync", "RSSSync"])
    }

    func refreshSeriesIndex() async throws {
        try await runCommand(candidates: ["RefreshSeries", "RefreshMonitoredDownloads"])
    }

    func rescanSeriesFolders() async throws {
        try await runCommand(candidates: ["RescanSeries", "RescanSeriesPaths", "RescanFolders"])
    }

    func triggerDownloadedEpisodesScan() async throws {
        try await runCommand(candidates: ["DownloadedEpisodesScan", "CheckForFinishedDownload"])
    }

    func triggerHealthCheck() async throws {
        try await runCommand(candidates: ["HealthCheck", "CheckHealth"])
    }

    func searchSeries(term: String, limit: Int = 20) async throws -> [SonarrLookupSeries] {
        let query = term.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return [] }

        let encoded = Self.encodeQuery(query)
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/series/lookup?term=\(encoded)",
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }

        return rows.prefix(max(1, limit)).compactMap { row in
            let title = stringValue(row["title"]) ?? stringValue(row["titleSlug"])
            guard let title else { return nil }

            let year = intValue(row["year"]).map(String.init)
            let network = stringValue(row["network"])
            let tvdbId = intValue(row["tvdbId"])
            let tvdbSubtitle = tvdbId.map { "TVDB \($0)" }
            let subtitleText = [year, network, tvdbSubtitle].compactMap { $0 }.joined(separator: " • ")
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
            let id = stringValue(row["tvdbId"])
                ?? stringValue(row["tvMazeId"])
                ?? stringValue(row["titleSlug"])
                ?? title
            let detailsURL = tvdbId.map { "https://thetvdb.com/dereferrer/series/\($0)" }
            let tvMazeId = intValue(row["tvMazeId"]).map(String.init)
            let runtime = intValue(row["runtime"]).map { "\($0) min" }
            let seasonCount = intValue(row["seasonCount"]).map(String.init)
            let genresText = (row["genres"] as? [Any])?
                .compactMap(stringValue)
                .prefix(3)
                .joined(separator: ", ")
            let genres = genresText?.isEmpty == false ? genresText : nil
            let overview = stringValue(row["overview"])
            let details = compactDetails([
                ("Year", year),
                ("TVDB", tvdbId.map(String.init)),
                ("TVMaze", tvMazeId),
                ("Network", network),
                ("Seasons", seasonCount),
                ("Runtime", runtime),
                ("Genres", genres),
                ("Overview", overview)
            ])

            return SonarrLookupSeries(
                id: id,
                title: title,
                subtitle: subtitle,
                supporting: supporting,
                status: status,
                posterURL: resolvePosterURL(from: row),
                detailsURL: detailsURL,
                details: details,
                requestTvdbId: tvdbId
            )
        }
    }

    func requestSeriesFromLookup(_ series: SonarrLookupSeries, selection: ArrRequestSelection? = nil) async throws {
        guard let tvdbId = series.requestTvdbId else {
            throw APIError.custom("Missing TVDB id for Sonarr request")
        }
        let configuration = try await requestConfiguration(for: series)
        let resolved = try resolveSelection(configuration: configuration, selection: selection)
        guard let qualityProfileId = resolved.qualityProfile?.idValue else {
            throw APIError.custom("No Sonarr quality profile configured")
        }
        guard let rootFolderPath = resolved.rootFolder?.pathValue else {
            throw APIError.custom("No Sonarr root folder configured")
        }
        let languageProfileId = resolved.languageProfile?.idValue

        var payload: [String: Any] = [
            "title": series.title,
            "tvdbId": tvdbId,
            "qualityProfileId": qualityProfileId,
            "rootFolderPath": rootFolderPath,
            "monitored": true,
            "seasonFolder": true,
            "addOptions": [
                "searchForMissingEpisodes": true
            ]
        ]
        if let languageProfileId {
            payload["languageProfileId"] = languageProfileId
        }

        let body = try JSONSerialization.data(withJSONObject: payload)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/series",
            method: "POST",
            headers: authHeaders().merging(["Content-Type": "application/json"]) { _, new in new },
            body: body
        )
    }

    func requestConfiguration(for series: SonarrLookupSeries) async throws -> ArrRequestConfiguration {
        ArrRequestConfiguration(
            title: series.title,
            qualityProfiles: try await requestOptions(path: "/api/v3/qualityprofile"),
            rootFolders: try await requestOptions(path: "/api/v3/rootfolder"),
            languageProfiles: try await requestOptions(path: "/api/v3/languageprofile"),
            metadataProfiles: []
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
                    path: "/api/v3/command",
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
            languageProfile: try selectOption(configuration.languageProfiles, selected: selection?.languageProfile, configuration: configuration),
            metadataProfile: nil
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
            if type == "poster" {
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
