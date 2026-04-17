import Foundation

enum TechnitiumStatsRange: String, CaseIterable, Sendable {
    case lastHour = "LastHour"
    case lastDay = "LastDay"
    case lastWeek = "LastWeek"
    case lastMonth = "LastMonth"
}

struct TechnitiumOverview: Sendable {
    let totalQueries: Int
    let totalBlocked: Int
    let totalClients: Int
    let blockedZones: Int
}

struct TechnitiumTopClient: Identifiable, Hashable, Sendable {
    let name: String
    let domain: String?
    let hits: Int
    let rateLimited: Bool

    var id: String {
        [name, domain ?? "-"].joined(separator: "|")
    }
}

struct TechnitiumTopDomain: Identifiable, Hashable, Sendable {
    let name: String
    let hits: Int

    var id: String { name }
}

struct TechnitiumChartSeries: Identifiable, Hashable, Sendable {
    let label: String
    let values: [Double]
    let colorHex: String?

    var id: String { label }
}

struct TechnitiumSummary: Sendable {
    let totalQueries: Int
    let totalBlocked: Int
    let totalClients: Int
    let blockedZones: Int
    let cacheEntries: Int
    let zones: Int
    let blockListZones: Int
    let totalNoError: Int
    let totalNxDomain: Int
    let totalServerFailure: Int
}

struct TechnitiumSettingsSnapshot: Sendable {
    let enableBlocking: Bool
    let blockListUrls: [String]
    let blockListNextUpdatedOn: String?
    let temporaryDisableBlockingTill: String?
    let version: String?
}

struct TechnitiumLogFile: Identifiable, Hashable, Sendable {
    let fileName: String
    let size: String

    var id: String { fileName }
}

struct TechnitiumDashboardData: Sendable {
    let range: TechnitiumStatsRange
    let summary: TechnitiumSummary
    let chartLabels: [String]
    let chartSeries: [TechnitiumChartSeries]
    let topClients: [TechnitiumTopClient]
    let topDomains: [TechnitiumTopDomain]
    let topBlockedDomains: [TechnitiumTopDomain]
    let blockedDomains: [String]
    let zoneCount: Int
    let cacheRecordCount: Int
    let logFiles: [TechnitiumLogFile]
    let settings: TechnitiumSettingsSnapshot
}

struct TechnitiumActionOutcome: Sendable {
    let success: Bool
    let message: String
}

actor TechnitiumAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var sessionToken: String = ""
    private var username: String = ""
    private var storedPassword: String = ""
    private var tokenRefreshCallback: (@Sendable (String) -> Void)?

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .technitium, instanceId: instanceId)
    }

    func configure(
        url: String,
        token: String,
        fallbackUrl: String? = nil,
        username: String? = nil,
        password: String? = nil
    , allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.sessionToken = token.trimmingCharacters(in: .whitespacesAndNewlines)
        if let username {
            self.username = username.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let password {
            self.storedPassword = password
        }
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .technitium, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func setTokenRefreshCallback(_ callback: (@Sendable (String) -> Void)?) {
        self.tokenRefreshCallback = callback
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }

        do {
            _ = try await withValidToken { token in
                try await self.requestPayload(path: "/api/user/session/get", query: [
                    "token": token
                ])
            }
            return true
        } catch {
            let primary = await engine.pingURL("\(baseURL)/api/user/login")
            if primary { return true }
            guard !fallbackURL.isEmpty else { return false }
            return await engine.pingURL("\(fallbackURL)/api/user/login")
        }
    }

    func authenticate(
        url: String,
        username: String,
        password: String,
        totp: String = "",
        fallbackUrl: String? = nil
    ) async throws -> String {
        let cleanURL = Self.cleanURL(url)
        let cleanFallback = Self.cleanURL(fallbackUrl ?? "")
        let cleanUser = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPassword = password
        let cleanTotp = totp.trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleanURL.isEmpty else { throw APIError.notConfigured }
        guard !cleanUser.isEmpty && !cleanPassword.isEmpty else {
            throw APIError.custom("Username and password are required")
        }

        let token = try await authenticateAgainst(
            baseURL: cleanURL,
            fallbackURL: cleanFallback,
            username: cleanUser,
            password: cleanPassword,
            totp: cleanTotp
        )

        self.baseURL = cleanURL
        self.fallbackURL = cleanFallback
        self.sessionToken = token
        self.username = cleanUser
        self.storedPassword = cleanPassword
        return token
    }

    func getOverview() async throws -> TechnitiumOverview {
        try await withValidToken { token in
            let payload = try await self.requestPayload(path: "/api/dashboard/stats/get", query: [
                "token": token,
                "type": TechnitiumStatsRange.lastHour.rawValue,
                "utc": "true"
            ])
            let stats = Self.object(payload["stats"])
            return TechnitiumOverview(
                totalQueries: Self.int(stats["totalQueries"]),
                totalBlocked: Self.int(stats["totalBlocked"]),
                totalClients: Self.int(stats["totalClients"]),
                blockedZones: Self.int(stats["blockedZones"])
            )
        }
    }

    func getDashboard(range: TechnitiumStatsRange) async throws -> TechnitiumDashboardData {
        try await withValidToken { token in
            let statsPayload = try await self.requestPayload(path: "/api/dashboard/stats/get", query: [
                "token": token,
                "type": range.rawValue,
                "utc": "true"
            ])
            let topClientsPayload = try? await self.requestPayload(path: "/api/dashboard/stats/getTop", query: [
                "token": token,
                "type": range.rawValue,
                "statsType": "TopClients",
                "limit": "20"
            ])
            let topDomainsPayload = try? await self.requestPayload(path: "/api/dashboard/stats/getTop", query: [
                "token": token,
                "type": range.rawValue,
                "statsType": "TopDomains",
                "limit": "20"
            ])
            let topBlockedPayload = try? await self.requestPayload(path: "/api/dashboard/stats/getTop", query: [
                "token": token,
                "type": range.rawValue,
                "statsType": "TopBlockedDomains",
                "limit": "20"
            ])
            let settingsPayload = (try? await self.requestPayload(path: "/api/settings/get", query: [
                "token": token
            ])) ?? [:]
            let blockedPayload = (try? await self.requestPayload(path: "/api/blocked/list", query: [
                "token": token,
                "domain": ""
            ])) ?? [:]
            let zonesPayload = (try? await self.requestPayload(path: "/api/zones/list", query: [
                "token": token,
                "pageNumber": "1",
                "zonesPerPage": "1"
            ])) ?? [:]
            let cachePayload = (try? await self.requestPayload(path: "/api/cache/list", query: [
                "token": token,
                "domain": ""
            ])) ?? [:]
            let logsPayload = (try? await self.requestPayload(path: "/api/logs/list", query: [
                "token": token
            ])) ?? [:]
            let stats = Self.object(statsPayload["stats"])
            let mainChart = Self.object(statsPayload["mainChartData"])

            let labels = Self.stringArray(mainChart["labels"])
            let chartSeries = Self.array(mainChart["datasets"]).compactMap { dataset -> TechnitiumChartSeries? in
                let values = Self.numberArray(dataset["data"])
                guard !values.isEmpty else { return nil }
                let label = Self.firstNonEmpty([Self.string(dataset["label"]), "Series"]) ?? "Series"
                return TechnitiumChartSeries(
                    label: label,
                    values: values,
                    colorHex: Self.string(dataset["borderColor"])
                )
            }

            let statsTopClients = Self.parseTopClients(from: statsPayload, key: "topClients")
            let statsTopDomains = Self.parseTopDomains(from: statsPayload, key: "topDomains")
            let statsTopBlockedDomains = Self.parseTopDomains(from: statsPayload, key: "topBlockedDomains")

            let topClients = topClientsPayload.map { Self.parseTopClients(from: $0, key: "topClients") }
            let topDomains = topDomainsPayload.map { Self.parseTopDomains(from: $0, key: "topDomains") }
            let topBlockedDomains = topBlockedPayload.map { Self.parseTopDomains(from: $0, key: "topBlockedDomains") }
            let resolvedTopClients = (topClients?.isEmpty == false ? topClients : nil) ?? statsTopClients
            let resolvedTopDomains = (topDomains?.isEmpty == false ? topDomains : nil) ?? statsTopDomains
            let resolvedTopBlockedDomains = (topBlockedDomains?.isEmpty == false ? topBlockedDomains : nil) ?? statsTopBlockedDomains

            let settings = TechnitiumSettingsSnapshot(
                enableBlocking: Self.bool(settingsPayload["enableBlocking"], defaultValue: true),
                blockListUrls: Self.stringArray(settingsPayload["blockListUrls"]),
                blockListNextUpdatedOn: Self.string(settingsPayload["blockListNextUpdatedOn"])?.nonEmpty,
                temporaryDisableBlockingTill: Self.string(settingsPayload["temporaryDisableBlockingTill"])?.nonEmpty,
                version: Self.string(settingsPayload["version"])?.nonEmpty
            )

            let blockedDomains = Self.collectBlockedDomains(payload: blockedPayload)

            let zoneCount = max(
                Self.int(zonesPayload["totalZones"]),
                Self.array(zonesPayload["zones"]).count
            )

            let cacheCount = Self.array(cachePayload["records"]).count + Self.array(cachePayload["zones"]).count

            let logFiles = Self.array(logsPayload["logFiles"])
                .compactMap { item -> TechnitiumLogFile? in
                    let fileName = Self.string(item["fileName"]) ?? ""
                    guard !fileName.isEmpty else { return nil }
                    return TechnitiumLogFile(fileName: fileName, size: Self.string(item["size"]) ?? "")
                }
                .sorted { $0.fileName > $1.fileName }

            let summary = TechnitiumSummary(
                totalQueries: Self.int(stats["totalQueries"]),
                totalBlocked: Self.int(stats["totalBlocked"]),
                totalClients: Self.int(stats["totalClients"]),
                blockedZones: Self.int(stats["blockedZones"]),
                cacheEntries: Self.int(stats["cachedEntries"]),
                zones: Self.int(stats["zones"]),
                blockListZones: Self.int(stats["blockListZones"]),
                totalNoError: Self.int(stats["totalNoError"]),
                totalNxDomain: Self.int(stats["totalNxDomain"]),
                totalServerFailure: Self.int(stats["totalServerFailure"])
            )

            return TechnitiumDashboardData(
                range: range,
                summary: summary,
                chartLabels: labels,
                chartSeries: chartSeries,
                topClients: resolvedTopClients,
                topDomains: resolvedTopDomains,
                topBlockedDomains: resolvedTopBlockedDomains,
                blockedDomains: blockedDomains,
                zoneCount: zoneCount,
                cacheRecordCount: cacheCount,
                logFiles: logFiles,
                settings: settings
            )
        }
    }

    func setBlockingEnabled(_ enabled: Bool) async throws -> TechnitiumActionOutcome {
        try await withValidToken { token in
            _ = try await self.requestPayload(path: "/api/settings/set", query: [
                "token": token,
                "enableBlocking": enabled ? "true" : "false"
            ])
            return TechnitiumActionOutcome(
                success: true,
                message: enabled ? "DNS blocking enabled" : "DNS blocking disabled"
            )
        }
    }

    func forceUpdateBlockLists() async throws -> TechnitiumActionOutcome {
        try await withValidToken { token in
            _ = try await self.requestPayload(path: "/api/settings/forceUpdateBlockLists", query: [
                "token": token
            ])
            return TechnitiumActionOutcome(success: true, message: "Block lists update started")
        }
    }

    func temporaryDisableBlocking(minutes: Int) async throws -> TechnitiumActionOutcome {
        let safeMinutes = max(1, min(minutes, 240))
        return try await withValidToken { token in
            let payload = try await self.requestPayload(path: "/api/settings/temporaryDisableBlocking", query: [
                "token": token,
                "minutes": "\(safeMinutes)"
            ])
            let until = Self.string(payload["temporaryDisableBlockingTill"])?.nonEmpty
            let message: String = {
                if until != nil { return "Blocking disabled for \(safeMinutes) min" }
                return "Blocking temporarily disabled"
            }()
            return TechnitiumActionOutcome(success: true, message: message)
        }
    }

    func addBlockedDomain(_ domain: String) async throws -> TechnitiumActionOutcome {
        let normalized = Self.normalizeDomain(domain)
        guard !normalized.isEmpty else { throw APIError.custom("Domain is required") }
        return try await withValidToken { token in
            _ = try await self.requestPayload(path: "/api/blocked/add", query: [
                "token": token,
                "domain": normalized
            ])
            return TechnitiumActionOutcome(success: true, message: "\(normalized) blocked")
        }
    }

    func removeBlockedDomain(_ domain: String) async throws -> TechnitiumActionOutcome {
        let normalized = Self.normalizeDomain(domain)
        guard !normalized.isEmpty else { throw APIError.custom("Domain is required") }
        return try await withValidToken { token in
            _ = try await self.requestPayload(path: "/api/blocked/delete", query: [
                "token": token,
                "domain": normalized
            ])
            return TechnitiumActionOutcome(success: true, message: "\(normalized) unblocked")
        }
    }

    private func withValidToken<T>(_ operation: (String) async throws -> T) async throws -> T {
        let token = sessionToken.trimmingCharacters(in: .whitespacesAndNewlines)
        if !token.isEmpty {
            do {
                return try await operation(token)
            } catch APIError.unauthorized {
                // Token refresh fallback.
            } catch {
                throw error
            }
        }

        let refreshedToken = try await refreshToken()
        return try await operation(refreshedToken)
    }

    private func refreshToken() async throws -> String {
        let cleanUser = username.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !baseURL.isEmpty, !cleanUser.isEmpty, !storedPassword.isEmpty else {
            throw APIError.custom("Technitium session expired. Please login again.")
        }

        let refreshed = try await authenticateAgainst(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            username: cleanUser,
            password: storedPassword,
            totp: ""
        )
        sessionToken = refreshed
        tokenRefreshCallback?(refreshed)
        return refreshed
    }

    private func authenticateAgainst(
        baseURL: String,
        fallbackURL: String,
        username: String,
        password: String,
        totp: String
    ) async throws -> String {
        var query: [String: String] = [
            "user": username,
            "pass": password,
            "includeInfo": "true"
        ]
        if !totp.isEmpty {
            query["totp"] = totp
        }

        let loginPath = Self.buildPath(path: "/api/user/login", query: query)
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: loginPath
        )
        let root = try Self.jsonObject(data: data)
        let status = (Self.string(root["status"]) ?? "").lowercased()

        switch status {
        case "ok":
            let token = (Self.string(root["token"]) ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !token.isEmpty else {
                throw APIError.custom("Technitium login returned no session token")
            }
            return token
        case "2fa-required":
            throw APIError.custom("Two-factor authentication code required")
        case "invalid-token":
            throw APIError.custom("Invalid token received from Technitium")
        default:
            throw APIError.custom(Self.errorMessage(from: root) ?? "Technitium authentication failed")
        }
    }

    private func requestPayload(path: String, query: [String: String]) async throws -> [String: Any] {
        let requestPath = Self.buildPath(path: path, query: query)
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: requestPath
        )
        let root = try Self.jsonObject(data: data)
        return try Self.requireSuccess(root)
    }

    private static func requireSuccess(_ root: [String: Any]) throws -> [String: Any] {
        let status = (string(root["status"]) ?? "").lowercased()
        switch status {
        case "ok":
            if let response = root["response"] as? [String: Any] {
                return response
            }
            return root
        case "invalid-token":
            throw APIError.unauthorized
        case "2fa-required":
            throw APIError.custom("Two-factor authentication code required")
        default:
            throw APIError.custom(errorMessage(from: root) ?? "Technitium request failed")
        }
    }

    private static func errorMessage(from root: [String: Any]) -> String? {
        firstNonEmpty([
            string(root["errorMessage"]),
            string(root["innerErrorMessage"])
        ])
    }

    private static func buildPath(path: String, query: [String: String]) -> String {
        guard !query.isEmpty else { return path }
        var components = URLComponents()
        components.path = path
        components.queryItems = query
            .sorted { $0.key.localizedCaseInsensitiveCompare($1.key) == .orderedAscending }
            .map { URLQueryItem(name: $0.key, value: $0.value) }
        guard let encoded = components.percentEncodedQuery, !encoded.isEmpty else {
            return path
        }
        return "\(path)?\(encoded)"
    }

    private static func jsonObject(data: Data) throws -> [String: Any] {
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.custom("Invalid Technitium response")
        }
        return object
    }

    private static func parseTopClients(from payload: [String: Any], key: String) -> [TechnitiumTopClient] {
        array(payload[key])
            .map {
                TechnitiumTopClient(
                    name: string($0["name"]) ?? "",
                    domain: string($0["domain"])?.nonEmpty,
                    hits: int($0["hits"]),
                    rateLimited: bool($0["rateLimited"], defaultValue: false)
                )
            }
            .filter { !$0.name.isEmpty }
            .sorted { $0.hits > $1.hits }
            .prefix(20)
            .map { $0 }
    }

    private static func parseTopDomains(from payload: [String: Any], key: String) -> [TechnitiumTopDomain] {
        array(payload[key])
            .map {
                TechnitiumTopDomain(
                    name: string($0["name"]) ?? "",
                    hits: int($0["hits"])
                )
            }
            .filter { !$0.name.isEmpty }
            .sorted { $0.hits > $1.hits }
            .prefix(20)
            .map { $0 }
    }

    private static func collectBlockedDomains(payload: [String: Any]) -> [String] {
        var result: [String] = []
        for item in array(payload["zones"]) {
            if let name = string(item["name"])?.nonEmpty {
                result.append(name)
            }
        }
        for item in array(payload["records"]) {
            if let name = string(item["name"])?.nonEmpty {
                result.append(name)
            }
        }
        return Array(Set(result.map { $0.lowercased() })).sorted()
    }

    private static func object(_ value: Any?) -> [String: Any] {
        value as? [String: Any] ?? [:]
    }

    private static func array(_ value: Any?) -> [[String: Any]] {
        value as? [[String: Any]] ?? []
    }

    private static func stringArray(_ value: Any?) -> [String] {
        if let strings = value as? [String] {
            return strings.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        }
        if let anyArray = value as? [Any] {
            return anyArray.compactMap { string($0)?.nonEmpty }
        }
        return []
    }

    private static func numberArray(_ value: Any?) -> [Double] {
        guard let array = value as? [Any] else { return [] }
        return array.compactMap { element in
            if let double = element as? Double { return double }
            if let int = element as? Int { return Double(int) }
            if let string = element as? String { return Double(string) }
            return nil
        }
    }

    private static func string(_ value: Any?) -> String? {
        if let value = value as? String {
            return value.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        if let number = value as? NSNumber {
            return number.stringValue
        }
        return nil
    }

    private static func int(_ value: Any?) -> Int {
        if let value = value as? Int { return value }
        if let value = value as? Double { return Int(value) }
        if let value = value as? NSNumber { return value.intValue }
        if let value = value as? String {
            if let parsed = Int(value) { return parsed }
            if let parsedDouble = Double(value) { return Int(parsedDouble) }
            switch value.lowercased() {
            case "true", "yes": return 1
            case "false", "no": return 0
            default: return 0
            }
        }
        return 0
    }

    private static func bool(_ value: Any?, defaultValue: Bool) -> Bool {
        guard let value else { return defaultValue }
        if let bool = value as? Bool { return bool }
        if let number = value as? NSNumber { return number.intValue != 0 }
        if let value = value as? String {
            switch value.lowercased() {
            case "1", "true", "yes": return true
            case "0", "false", "no": return false
            default: return defaultValue
            }
        }
        return defaultValue
    }

    private static func firstNonEmpty(_ values: [String?]) -> String? {
        values.compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }.first { !$0.isEmpty }
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func normalizeDomain(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "."))
            .lowercased()
    }
}

private extension String {
    var nonEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
