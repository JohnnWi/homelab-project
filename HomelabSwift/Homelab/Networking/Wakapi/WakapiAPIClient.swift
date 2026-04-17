import Foundation

actor WakapiAPIClient {
    private struct CacheEntry<Value> {
        let value: Value
        let timestamp: Date
    }

    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""
    private var summaryCache: [String: CacheEntry<WakapiSummary>] = [:]
    private var dailySummaryCache: [String: CacheEntry<WakapiDailySummariesResponse>] = [:]
    private let cacheTTL: TimeInterval = 120

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .wakapi, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, apiKey: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey
        self.summaryCache.removeAll()
        self.dailySummaryCache.removeAll()
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .wakapi, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    private func authHeaders() -> [String: String] {
        let authString = Data("\(apiKey)".utf8).base64EncodedString()
        return [
            "Authorization": "Basic \(authString)",
            "Content-Type": "application/json"
        ]
    }

    // MARK: - Ping / Auth

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let primary = await engine.pingURL("\(baseURL)/api/health")
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL("\(fallbackURL)/api/health")
    }

    func authenticate(url: String, apiKey: String, fallbackUrl: String? = nil) async throws {
        let cleanURL = Self.cleanURL(url)
        let authString = Data("\(apiKey)".utf8).base64EncodedString()
        let headers = ["Authorization": "Basic \(authString)", "Content-Type": "application/json"]
        
        // Use the same explicit interval as Android for a deterministic auth check.
        let paths = [
            "/api/summary?interval=today",
            "/summary?interval=today"
        ]
        _ = try await requestDataWithFallbackPaths(
            baseURL: cleanURL,
            fallbackURL: Self.cleanURL(fallbackUrl ?? ""),
            paths: paths,
            headers: headers
        )
    }

    // MARK: - API Methods

    func getSummary(
        interval: String = "today",
        filter: WakapiSummaryFilter? = nil,
        forceRefresh: Bool = false
    ) async throws -> WakapiSummary {
        let cacheKey = summaryCacheKey(interval: interval, filter: filter)
        if !forceRefresh, let cached = summaryCache[cacheKey], Date().timeIntervalSince(cached.timestamp) <= cacheTTL {
            return cached.value
        }

        let response: WakapiSummary = try await requestWithFallbackPaths(
            paths: summaryPaths(interval: interval, filter: filter),
            headers: authHeaders()
        )
        summaryCache[cacheKey] = CacheEntry(value: response, timestamp: Date())
        return response
    }

    func getDailySummaries(
        range: String = "last_6_months",
        filter: WakapiSummaryFilter? = nil,
        forceRefresh: Bool = false
    ) async throws -> WakapiDailySummariesResponse {
        let cacheKey = dailySummaryCacheKey(range: range, filter: filter)
        if !forceRefresh, let cached = dailySummaryCache[cacheKey], Date().timeIntervalSince(cached.timestamp) <= cacheTTL {
            return cached.value
        }

        let response: WakapiDailySummariesResponse = try await requestWithFallbackPaths(
            paths: dailySummariesPaths(range: range, filter: filter),
            headers: authHeaders()
        )
        dailySummaryCache[cacheKey] = CacheEntry(value: response, timestamp: Date())
        return response
    }

    // MARK: - Helpers

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private func summaryPaths(interval: String, filter: WakapiSummaryFilter?) -> [String] {
        var components = URLComponents()
        components.path = "/api/summary"
        var queryItems = [URLQueryItem(name: "interval", value: interval)]
        if let filter {
            queryItems.append(URLQueryItem(name: filter.dimension.queryItemName, value: filter.value))
        }
        components.queryItems = queryItems
        let apiPath = components.string ?? "/api/summary?interval=\(interval)"

        components.path = "/summary"
        let rootPath = components.string ?? "/summary?interval=\(interval)"

        return [apiPath, rootPath]
    }

    private func dailySummariesPaths(range: String, filter: WakapiSummaryFilter?) -> [String] {
        var components = URLComponents()
        components.path = "/api/compat/wakatime/v1/users/current/summaries"
        var queryItems = [URLQueryItem(name: "range", value: range)]
        if let filter {
            queryItems.append(URLQueryItem(name: filter.dimension.queryItemName, value: filter.value))
        }
        components.queryItems = queryItems
        let apiPath = components.string ?? "/api/compat/wakatime/v1/users/current/summaries?range=\(range)"

        components.path = "/compat/wakatime/v1/users/current/summaries"
        let rootPath = components.string ?? "/compat/wakatime/v1/users/current/summaries?range=\(range)"

        return [apiPath, rootPath]
    }

    private func summaryCacheKey(interval: String, filter: WakapiSummaryFilter?) -> String {
        "\(interval)|\(filter?.cacheKey ?? "none")"
    }

    private func dailySummaryCacheKey(range: String, filter: WakapiSummaryFilter?) -> String {
        "\(range)|\(filter?.cacheKey ?? "none")"
    }

    private func requestWithFallbackPaths<T: Decodable>(
        paths: [String],
        headers: [String: String]
    ) async throws -> T {
        var lastError: Error?
        for (index, path) in paths.enumerated() {
            do {
                return try await engine.request(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: path,
                    headers: headers
                )
            } catch {
                lastError = error
                guard index < paths.count - 1, Self.isPathFallbackCandidate(error) else {
                    throw error
                }
            }
        }

        throw lastError ?? APIError.custom("Unknown Wakapi request failure")
    }

    private func requestDataWithFallbackPaths(
        baseURL: String,
        fallbackURL: String,
        paths: [String],
        headers: [String: String]
    ) async throws -> Data {
        var lastError: Error?
        for (index, path) in paths.enumerated() {
            do {
                return try await engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: path,
                    headers: headers
                )
            } catch {
                lastError = error
                guard index < paths.count - 1, Self.isPathFallbackCandidate(error) else {
                    throw error
                }
            }
        }

        throw lastError ?? APIError.custom("Unknown Wakapi auth failure")
    }

    private static func isPathFallbackCandidate(_ error: Error) -> Bool {
        switch error {
        case APIError.httpError(let statusCode, _):
            return statusCode == 404
        case APIError.bothURLsFailed(let primaryError, let fallbackError):
            return isPathFallbackCandidate(primaryError) && isPathFallbackCandidate(fallbackError)
        default:
            return false
        }
    }
}

// MARK: - Models

struct WakapiSummaryFilter: Equatable, Sendable {
    enum Dimension: String, Sendable {
        case project
        case language
        case editor
        case operatingSystem
        case machine
        case label

        var queryItemName: String {
            switch self {
            case .project: return "project"
            case .language: return "language"
            case .editor: return "editor"
            case .operatingSystem: return "operating_system"
            case .machine: return "machine"
            case .label: return "label"
            }
        }
    }

    let dimension: Dimension
    let value: String

    var cacheKey: String {
        "\(dimension.rawValue):\(value)"
    }
}

struct WakapiSummary: Codable, Sendable {
    let userId: String?
    let from: String?
    let to: String?
    let grandTotal: GrandTotal?
    let projects: [StatItem]?
    let languages: [StatItem]?
    let machines: [StatItem]?
    let operatingSystems: [StatItem]?
    let editors: [StatItem]?
    let labels: [StatItem]?
    let categories: [StatItem]?
    let branches: [StatItem]?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case from, to
        case grandTotal = "grand_total"
        case projects, languages, machines, editors, labels, categories, branches
        case operatingSystems = "operating_systems"
    }

    var effectiveGrandTotal: GrandTotal {
        grandTotal ?? GrandTotal(totalSeconds: inferredTotalSeconds)
    }

    var inferredTotalSeconds: Double {
        [
            projects,
            languages,
            machines,
            operatingSystems,
            editors,
            labels,
            categories,
            branches
        ]
        .compactMap { items in
            guard let items, !items.isEmpty else { return nil }
            return items.reduce(0) { $0 + $1.effectiveTotalSeconds }
        }
        .max() ?? 0
    }
}

struct GrandTotal: Codable, Sendable {
    let digital: String?
    let hours: Int?
    let minutes: Int?
    let text: String?
    let totalSeconds: Double?

    enum CodingKeys: String, CodingKey {
        case digital, hours, minutes, text
        case totalSeconds = "total_seconds"
    }

    init(
        digital: String? = nil,
        hours: Int? = nil,
        minutes: Int? = nil,
        text: String? = nil,
        totalSeconds: Double? = nil
    ) {
        self.digital = digital
        self.hours = hours
        self.minutes = minutes
        self.text = text
        self.totalSeconds = totalSeconds
    }

    init(totalSeconds: Double) {
        let roundedSeconds = max(0, Int(totalSeconds.rounded()))
        let derivedHours = roundedSeconds / 3600
        let derivedMinutes = (roundedSeconds % 3600) / 60
        self.digital = String(format: "%d:%02d", derivedHours, derivedMinutes)
        self.hours = derivedHours
        self.minutes = derivedMinutes
        self.text = Self.format(totalSeconds: totalSeconds)
        self.totalSeconds = totalSeconds
    }

    static func format(totalSeconds: Double) -> String {
        let roundedSeconds = max(0, Int(totalSeconds.rounded()))
        let hours = roundedSeconds / 3600
        let minutes = (roundedSeconds % 3600) / 60
        if hours > 0 {
            return "\(hours) hrs \(minutes) mins"
        } else {
            return "\(minutes) mins"
        }
    }
}

struct WakapiDailySummariesResponse: Codable, Sendable {
    let data: [WakapiDailySummary]
    let end: String?
    let start: String?
    let cumulativeTotal: WakapiCumulativeTotal?
    let dailyAverage: WakapiDailyAverage?

    enum CodingKeys: String, CodingKey {
        case data, end, start
        case cumulativeTotal = "cumulative_total"
        case dailyAverage = "daily_average"
    }
}

struct WakapiDailySummary: Codable, Sendable {
    let categories: [StatItem]?
    let dependencies: [StatItem]?
    let editors: [StatItem]?
    let languages: [StatItem]?
    let machines: [StatItem]?
    let operatingSystems: [StatItem]?
    let projects: [StatItem]?
    let branches: [StatItem]?
    let entities: [StatItem]?
    let grandTotal: GrandTotal?
    let range: WakapiDailyRange?

    enum CodingKeys: String, CodingKey {
        case categories, dependencies, editors, languages, machines, projects, branches, entities, range
        case operatingSystems = "operating_systems"
        case grandTotal = "grand_total"
    }

    var totalSeconds: Double {
        grandTotal?.totalSeconds
            ?? Double((grandTotal?.hours ?? 0) * 3600 + (grandTotal?.minutes ?? 0) * 60)
    }
}

struct WakapiDailyRange: Codable, Sendable {
    let date: String?
    let end: String?
    let start: String?
    let text: String?
    let timezone: String?
}

struct WakapiCumulativeTotal: Codable, Sendable {
    let decimal: String?
    let digital: String?
    let seconds: Double?
    let text: String?
}

struct WakapiDailyAverage: Codable, Sendable {
    let daysIncludingHolidays: Int?
    let daysMinusHolidays: Int?
    let holidays: Int?
    let seconds: Int?
    let secondsIncludingOtherLanguage: Int?
    let text: String?
    let textIncludingOtherLanguage: String?

    enum CodingKeys: String, CodingKey {
        case holidays, seconds, text
        case daysIncludingHolidays = "days_including_holidays"
        case daysMinusHolidays = "days_minus_holidays"
        case secondsIncludingOtherLanguage = "seconds_including_other_language"
        case textIncludingOtherLanguage = "text_including_other_language"
    }
}

struct StatItem: Codable, Sendable, Identifiable {
    let name: String?
    let key: String?
    let totalSeconds: Double?
    let total: Double?
    let percent: Double?
    let digital: String?
    let text: String?
    let hours: Int?
    let minutes: Int?

    var id: String {
        displayName ?? "unknown-\(Int(effectiveTotalSeconds.rounded()))"
    }

    var displayName: String? {
        name ?? key
    }

    var effectiveTotalSeconds: Double {
        totalSeconds ?? total ?? 0
    }

    var effectiveHours: Int {
        hours ?? max(0, Int(effectiveTotalSeconds.rounded(.down))) / 3600
    }

    var effectiveMinutes: Int {
        minutes ?? (max(0, Int(effectiveTotalSeconds.rounded(.down))) % 3600) / 60
    }

    var displayText: String? {
        if let text, !text.isEmpty {
            return text
        }
        guard effectiveTotalSeconds > 0 else {
            return nil
        }
        return GrandTotal.format(totalSeconds: effectiveTotalSeconds)
    }

    func resolvedPercent(sectionTotalSeconds: Double) -> Double {
        if let percent {
            return percent
        }
        guard sectionTotalSeconds > 0 else {
            return 0
        }
        return (effectiveTotalSeconds / sectionTotalSeconds) * 100
    }

    enum CodingKeys: String, CodingKey {
        case name, key, percent, digital, text, hours, minutes, total
        case totalSeconds = "total_seconds"
    }
}
