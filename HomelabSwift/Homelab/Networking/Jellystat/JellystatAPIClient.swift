import Foundation

struct JellystatLibraryTypeViews: Decodable, Sendable {
    let audio: Int
    let movie: Int
    let series: Int
    let other: Int

    var totalViews: Int { audio + movie + series + other }

    enum CodingKeys: String, CodingKey {
        case audio = "Audio"
        case movie = "Movie"
        case series = "Series"
        case other = "Other"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        audio = Self.decodeInt(container, forKey: .audio)
        movie = Self.decodeInt(container, forKey: .movie)
        series = Self.decodeInt(container, forKey: .series)
        other = Self.decodeInt(container, forKey: .other)
    }

    private static func decodeInt(_ container: KeyedDecodingContainer<CodingKeys>, forKey key: CodingKeys) -> Int {
        if let value = try? container.decode(Int.self, forKey: key) {
            return value
        }
        if let string = try? container.decode(String.self, forKey: key), let value = Int(string) {
            return value
        }
        if let double = try? container.decode(Double.self, forKey: key) {
            return Int(double)
        }
        return 0
    }
}

struct JellystatCountDuration: Sendable {
    let count: Int
    let durationSeconds: Double
}

struct JellystatSeriesPoint: Identifiable, Sendable {
    let key: String
    let totalViews: Int
    let totalDurationSeconds: Double
    let breakdown: [String: JellystatCountDuration]

    var id: String { key }
}

struct JellystatViewsOverTime: Sendable {
    let points: [JellystatSeriesPoint]
}

struct JellystatWatchSummary: Sendable {
    let days: Int
    let totalHours: Double
    let totalViews: Int
    let activeDays: Int
    let topLibraryName: String?
    let topLibraryHours: Double
    let viewsByType: JellystatLibraryTypeViews
    let points: [JellystatSeriesPoint]
}

actor JellystatAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .jellystat, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .jellystat, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiKey.isEmpty else { return false }
        let path = statsPath(endpoint: "getViewsByLibraryType", queryItems: [URLQueryItem(name: "days", value: "1")])
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
            path: statsPath(endpoint: "getViewsByLibraryType", queryItems: [URLQueryItem(name: "days", value: "1")]),
            headers: authHeaders(for: trimmedKey)
        )
    }

    func getViewsByLibraryType(days: Int = 30) async throws -> JellystatLibraryTypeViews {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: statsPath(endpoint: "getViewsByLibraryType", queryItems: [URLQueryItem(name: "days", value: "\(normalizeDays(days))")]),
            headers: authHeaders()
        )
    }

    func getViewsOverTime(days: Int = 30) async throws -> JellystatViewsOverTime {
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: statsPath(endpoint: "getViewsOverTime", queryItems: [URLQueryItem(name: "days", value: "\(normalizeDays(days))")]),
            headers: authHeaders()
        )
        return try parseViewsOverTimeResponse(data)
    }

    func getWatchSummary(days: Int = 30) async throws -> JellystatWatchSummary {
        let safeDays = normalizeDays(days)
        async let typeStatsTask = getViewsByLibraryType(days: safeDays)
        async let overTimeTask = getViewsOverTime(days: safeDays)

        let typeStats = try await typeStatsTask
        let overTime = try await overTimeTask

        let totalDurationSeconds = overTime.points.reduce(0.0) { $0 + $1.totalDurationSeconds }
        let totalHours = totalDurationSeconds / 3600.0
        let activeDays = overTime.points.filter { $0.totalViews > 0 || $0.totalDurationSeconds > 0 }.count

        var durationByLibrary: [String: Double] = [:]
        for point in overTime.points {
            for (library, stats) in point.breakdown {
                durationByLibrary[library, default: 0] += stats.durationSeconds
            }
        }

        let topLibrary = durationByLibrary.max { lhs, rhs in lhs.value < rhs.value }

        return JellystatWatchSummary(
            days: safeDays,
            totalHours: totalHours,
            totalViews: typeStats.totalViews,
            activeDays: activeDays,
            topLibraryName: topLibrary?.key,
            topLibraryHours: (topLibrary?.value ?? 0) / 3600.0,
            viewsByType: typeStats,
            points: overTime.points
        )
    }

    private func parseViewsOverTimeResponse(_ data: Data) throws -> JellystatViewsOverTime {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rawPoints = object["stats"] as? [[String: Any]] else {
            throw APIError.decodingError(NSError(domain: "Jellystat", code: -1, userInfo: [NSLocalizedDescriptionKey: "Invalid views over time response"]))
        }

        let points: [JellystatSeriesPoint] = rawPoints.map { point in
            let key = Self.stringValue(point["Key"]) ?? Self.stringValue(point["key"]) ?? "Unknown"
            var breakdown: [String: JellystatCountDuration] = [:]
            var totalViews = 0
            var totalDuration = 0.0

            for (field, rawValue) in point {
                if field.caseInsensitiveCompare("key") == .orderedSame {
                    continue
                }
                guard let metric = rawValue as? [String: Any] else {
                    continue
                }

                let count = Self.intValue(metric["count"] ?? metric["Count"])
                let durationMinutes = Self.doubleValue(metric["duration"] ?? metric["Duration"])
                let durationSeconds = durationMinutes * 60.0
                breakdown[field] = JellystatCountDuration(count: count, durationSeconds: durationSeconds)
                totalViews += count
                totalDuration += durationSeconds
            }

            return JellystatSeriesPoint(
                key: key,
                totalViews: totalViews,
                totalDurationSeconds: totalDuration,
                breakdown: breakdown
            )
        }
        .sorted { lhs, rhs in
            switch (Self.parseDateKey(lhs.key), Self.parseDateKey(rhs.key)) {
            case let (.some(leftDate), .some(rightDate)):
                return leftDate < rightDate
            default:
                return lhs.key.localizedCaseInsensitiveCompare(rhs.key) == .orderedAscending
            }
        }

        return JellystatViewsOverTime(points: points)
    }

    private func normalizeDays(_ raw: Int) -> Int {
        max(1, min(raw, 3650))
    }

    private func authHeaders(for apiKey: String? = nil) -> [String: String] {
        let resolvedKey = (apiKey ?? self.apiKey).trimmingCharacters(in: .whitespacesAndNewlines)
        return [
            "X-API-Token": resolvedKey,
            "Content-Type": "application/json"
        ]
    }

    private func statsPath(endpoint: String, queryItems: [URLQueryItem] = []) -> String {
        var components = URLComponents()
        components.path = "/stats/\(endpoint)"
        if !queryItems.isEmpty {
            components.queryItems = queryItems
        }
        return components.url?.absoluteString ?? components.path
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func intValue(_ raw: Any?) -> Int {
        switch raw {
        case let value as Int:
            return value
        case let value as Double:
            return Int(value)
        case let value as NSNumber:
            return value.intValue
        case let value as String:
            if let intValue = Int(value) {
                return intValue
            }
            if let doubleValue = Double(value) {
                return Int(doubleValue)
            }
            return 0
        default:
            return 0
        }
    }

    private static func doubleValue(_ raw: Any?) -> Double {
        switch raw {
        case let value as Double:
            return value
        case let value as Int:
            return Double(value)
        case let value as NSNumber:
            return value.doubleValue
        case let value as String:
            return Double(value) ?? 0
        default:
            return 0
        }
    }

    private static func stringValue(_ raw: Any?) -> String? {
        switch raw {
        case let value as String:
            return value
        case let value as NSNumber:
            return value.stringValue
        default:
            return nil
        }
    }

    private static let formatters: [DateFormatter] = {
        let formats = [
            "MMM d, yyyy",
            "MMM dd, yyyy",
            "yyyy-MM-dd",
            "yyyy/MM/dd"
        ]
        return formats.map { format in
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.timeZone = TimeZone(secondsFromGMT: 0)
            formatter.dateFormat = format
            return formatter
        }
    }()

    private static func parseDateKey(_ key: String) -> Date? {
        for formatter in formatters {
            if let date = formatter.date(from: key) {
                return date
            }
        }
        return nil
    }
}
