import Foundation

struct RadarrLookupMovie: Identifiable, Sendable {
    let id: String
    let title: String
    let subtitle: String?
    let supporting: String?
    let status: String?
    let posterURL: String?
    let detailsURL: String?
    let details: [String: String]
    let requestTmdbId: Int?
}

actor RadarrAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .radarr, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .radarr, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
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

    func getSystemStatus() async throws -> RadarrSystemStatus {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/system/status",
            headers: authHeaders()
        )
    }

    func getMovies() async throws -> [RadarrMovie] {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/movie",
            headers: authHeaders()
        )
    }

    func getQueue() async throws -> RadarrQueueResponse {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/queue?page=1&pageSize=20&sortKey=timeLeft&sortDirection=ascending",
            headers: authHeaders()
        )
    }

    func getHistory() async throws -> RadarrHistoryResponse {
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
                if let movie = row["movie"] as? [String: Any] {
                    return stringValue(movie["title"])
                }
                return nil
            }
            .prefix(limit)
            .map { $0 }
        } catch {
            return []
        }
    }

    func triggerMoviesSearch() async throws {
        try await runCommand(candidates: ["MissingMoviesSearch", "MoviesSearch", "MovieSearch"])
    }

    func refreshMovieIndex() async throws {
        try await runCommand(candidates: ["RefreshMovie", "RefreshMovies", "RefreshMonitoredDownloads"])
    }

    func triggerRSSSync() async throws {
        try await runCommand(candidates: ["RssSync", "RSSSync"])
    }

    func rescanMovieFolders() async throws {
        try await runCommand(candidates: ["RescanFolders", "RescanMovie", "RescanMovieFiles"])
    }

    func triggerDownloadedMoviesScan() async throws {
        try await runCommand(candidates: ["DownloadedMoviesScan", "CheckForFinishedDownload"])
    }

    func triggerHealthCheck() async throws {
        try await runCommand(candidates: ["HealthCheck", "CheckHealth"])
    }

    func searchMovies(term: String, limit: Int = 20) async throws -> [RadarrLookupMovie] {
        let query = term.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return [] }

        let encoded = Self.encodeQuery(query)
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/movie/lookup?term=\(encoded)",
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }

        return rows.prefix(max(1, limit)).compactMap { row in
            let title = stringValue(row["title"]) ?? stringValue(row["titleSlug"])
            guard let title else { return nil }

            let year = intValue(row["year"]).map(String.init)
            let tmdbId = intValue(row["tmdbId"])
            let tmdbSubtitle = tmdbId.map { "TMDb \($0)" }
            let subtitle = [year, tmdbSubtitle].compactMap { $0 }.joined(separator: " • ").nilIfEmpty

            let monitored = boolValue(row["monitored"]) ?? false
            let hasFile = boolValue(row["hasFile"]) ?? false
            let status: String
            if hasFile {
                status = "In Library"
            } else if monitored {
                status = "Monitored"
            } else {
                status = "Unmonitored"
            }

            let supporting = stringValue(row["status"])
            let id = stringValue(row["tmdbId"])
                ?? stringValue(row["imdbId"])
                ?? stringValue(row["titleSlug"])
                ?? title
            let imdbId = stringValue(row["imdbId"])
            let detailsURL: String?
            if let tmdbId {
                detailsURL = "https://www.themoviedb.org/movie/\(tmdbId)"
            } else if let imdbId {
                detailsURL = "https://www.imdb.com/title/\(imdbId)/"
            } else {
                detailsURL = nil
            }
            let studio = stringValue(row["studio"])
            let originalTitle = stringValue(row["originalTitle"])
            let runtime = intValue(row["runtime"]).map { "\($0) min" }
            let availability = stringValue(row["minimumAvailability"])
            let overview = stringValue(row["overview"])
            let genres = (row["genres"] as? [Any])?
                .compactMap(stringValue)
                .prefix(3)
                .joined(separator: ", ")
                .nilIfEmpty
            let details = compactDetails([
                ("Year", year),
                ("TMDb", tmdbId.map(String.init)),
                ("IMDb", imdbId),
                ("Studio", studio),
                ("Original", originalTitle),
                ("Runtime", runtime),
                ("Genres", genres),
                ("Availability", availability),
                ("Overview", overview)
            ])

            return RadarrLookupMovie(
                id: id,
                title: title,
                subtitle: subtitle,
                supporting: supporting,
                status: status,
                posterURL: resolvePosterURL(from: row),
                detailsURL: detailsURL,
                details: details,
                requestTmdbId: tmdbId
            )
        }
    }

    func requestMovieFromLookup(_ movie: RadarrLookupMovie, selection: ArrRequestSelection? = nil) async throws {
        guard let tmdbId = movie.requestTmdbId else {
            throw APIError.custom("Missing TMDB id for Radarr request")
        }
        let configuration = try await requestConfiguration(for: movie)
        let resolved = try resolveSelection(configuration: configuration, selection: selection)
        guard let qualityProfileId = resolved.qualityProfile?.idValue else {
            throw APIError.custom("No Radarr quality profile configured")
        }
        guard let rootFolderPath = resolved.rootFolder?.pathValue else {
            throw APIError.custom("No Radarr root folder configured")
        }

        let payload: [String: Any] = [
            "title": movie.title,
            "tmdbId": tmdbId,
            "qualityProfileId": qualityProfileId,
            "rootFolderPath": rootFolderPath,
            "monitored": true,
            "minimumAvailability": "released",
            "addOptions": [
                "searchForMovie": true
            ]
        ]

        let body = try JSONSerialization.data(withJSONObject: payload)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v3/movie",
            method: "POST",
            headers: authHeaders().merging(["Content-Type": "application/json"]) { _, new in new },
            body: body
        )
    }

    func requestConfiguration(for movie: RadarrLookupMovie) async throws -> ArrRequestConfiguration {
        ArrRequestConfiguration(
            title: movie.title,
            qualityProfiles: try await requestOptions(path: "/api/v3/qualityprofile"),
            rootFolders: try await requestOptions(path: "/api/v3/rootfolder"),
            languageProfiles: [],
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
            languageProfile: nil,
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

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
