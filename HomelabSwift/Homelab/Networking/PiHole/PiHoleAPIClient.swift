import Foundation

actor PiHoleAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var sid: String = ""
    private var authMode: PiHoleAuthMode?
    private var storedPassword: String = ""
    private var isRefreshing = false
    private var onTokenRefreshed: (@Sendable (String, PiHoleAuthMode) -> Void)?

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .pihole, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, sid: String, authMode: PiHoleAuthMode? = nil, fallbackUrl: String? = nil, password: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.sid = sid
        self.authMode = authMode
        if let password, !password.isEmpty { self.storedPassword = password }
    }

    /// Set a callback invoked after successful token refresh so the store can persist it
    func setTokenRefreshCallback(_ callback: @escaping @Sendable (String, PiHoleAuthMode) -> Void) {
        self.onTokenRefreshed = callback
    }

    /// Attempts to refresh the session using stored credentials
    private func refreshToken() async -> Bool {
        guard !storedPassword.isEmpty, !isRefreshing else { return false }
        isRefreshing = true
        defer { isRefreshing = false }

        do {
            let newSid = try await authenticate(url: baseURL.isEmpty ? fallbackURL : baseURL, password: storedPassword, fallbackUrl: fallbackURL.isEmpty ? nil : fallbackURL)
            let newMode: PiHoleAuthMode = newSid == storedPassword ? .legacy : .session
            sid = newSid
            authMode = newMode
            onTokenRefreshed?(newSid, newMode)
            return true
        } catch {
            return false
        }
    }

    /// Wrapper that retries once after token refresh on auth failure
    private func withAuthRetry<T>(_ operation: () async throws -> T) async throws -> T {
        do {
            return try await operation()
        } catch {
            if isAuthError(error), await refreshToken() {
                return try await operation()
            }
            throw error
        }
    }

    private func isAuthError(_ error: Error) -> Bool {
        guard let apiError = error as? APIError else { return false }
        switch apiError {
        case .httpError(let code, _): return code == 401
        case .unauthorized: return true
        case .bothURLsFailed(let primary, let fallback):
            return isAuthError(primary) || isAuthError(fallback)
        default: return false
        }
    }

    private func authHeaders() -> [String: String] {
        var headers = ["Content-Type": "application/json"]
        if authMode != .legacy, !sid.isEmpty {
            headers["X-FTL-SID"] = sid
        }
        return headers
    }

    private func authorizedPath(_ path: String) -> String {
        if authMode == .session {
            return path
        }
        guard !sid.isEmpty, !path.contains("auth=") else { return path }
        let separator = path.contains("?") ? "&" : "?"
        let encodedSid = sid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? sid
        return "\(path)\(separator)auth=\(encodedSid)"
    }

    // MARK: - Ping
    // Pi-hole: ANY HTTP response = reachable (401 = auth needed, still alive)

    func ping() async -> Bool {
        if baseURL.isEmpty { return false }
        if await engine.pingURL("\(baseURL)/api/info/version", extraHeaders: authHeaders()) { return true }
        if !fallbackURL.isEmpty {
            return await engine.pingURL("\(fallbackURL)/api/info/version", extraHeaders: authHeaders())
        }
        return false
    }

    // MARK: - Authentication

    func authenticate(url: String, password: String, fallbackUrl: String? = nil) async throws -> String {
        let cleanURL = Self.cleanURL(url)
        let cleanFallback = Self.cleanURL(fallbackUrl ?? "")

        let body = try JSONEncoder().encode(["password": password])

        var authError: Error?

        do {
            let response: PiholeAuthResponse = try await engine.request(
                baseURL: cleanURL,
                fallbackURL: cleanFallback,
                path: "/api/auth",
                method: "POST",
                headers: ["Content-Type": "application/json"],
                body: body
            )
            return response.session.sid
        } catch {
            authError = error
        }

        if try await validateLegacyAuth(baseURL: cleanURL, fallbackURL: cleanFallback, secret: password) {
            return password
        }

        throw authError ?? APIError.custom("Authentication failed. Check your password and URL.")
    }

    // MARK: - Stats

    func getStats() async throws -> PiholeStats {
        try await withAuthRetry {
            do {
                return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/stats/summary"), headers: authHeaders())
            } catch {
                // Legacy fallback (Pi-hole v5)
                let data = try await engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: legacyAuthPath("/admin/api.php?summaryRaw"),
                    headers: authHeaders()
                )
                guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    throw error
                }
                return parseLegacyStats(json)
            }
        }
    }

    func getBlockingStatus() async throws -> PiholeBlockingStatus {
        try await withAuthRetry {
            do {
                return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/dns/blocking"), headers: authHeaders())
            } catch {
                // Legacy fallback (Pi-hole v5)
                let data = try await engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: legacyAuthPath("/admin/api.php?status"),
                    headers: authHeaders()
                )
                guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    throw error
                }
                let status = (json["status"] as? String) ?? (json["blocking"] as? String) ?? "unknown"
                return PiholeBlockingStatus(blocking: status == "enabled" ? "enabled" : "disabled")
            }
        }
    }

    func setBlocking(enabled: Bool, timer: Int? = nil) async throws {
        struct BlockBody: Encodable {
            let blocking: Bool
            let timer: Int?
        }
        try await withAuthRetry {
            do {
                let body = try BlockBody(blocking: enabled, timer: timer).toJSONData()
                try await engine.requestVoid(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/dns/blocking"), method: "POST", headers: authHeaders(), body: body)
            } catch {
                // Legacy fallback (Pi-hole v5)
                let query: String
                if enabled {
                    query = "enable"
                } else {
                    let duration = timer ?? 0
                    query = "disable=\(duration)"
                }
                _ = try await engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: legacyAuthPath("/admin/api.php?\(query)"),
                    headers: authHeaders()
                )
            }
        }
    }

    // MARK: - Domains (v6 + legacy v5)

    func getDomains() async throws -> [PiholeDomain] {
        try await withAuthRetry {
            do {
                let response: PiholeDomainListResponse = try await engine.request(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: authorizedPath("/api/domains"),
                    headers: authHeaders()
                )
                return response.domains
            } catch {
                // Legacy fallback (Pi-hole v5)
                let encodedSid = sid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? sid
                let data = try await engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: "/admin/api.php?list=all&auth=\(encodedSid)",
                    headers: authHeaders()
                )
                let response = try JSONDecoder().decode(PiholeDomainListResponse.self, from: data)
                return response.domains
            }
        }
    }
    
    func addDomain(domain: String, to list: PiholeDomainListType) async throws {
        struct AddDomainBody: Encodable {
            let domain: String
        }
        try await withAuthRetry {
            do {
                let body = try AddDomainBody(domain: domain).toJSONData()
                let path = authorizedPath("/api/domains/\(list.rawValue)/exact")
                try await engine.requestVoid(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: path,
                    method: "POST",
                    headers: authHeaders(),
                    body: body
                )
            } catch {
                // Legacy fallback (Pi-hole v5)
                let encodedDomain = domain.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? domain
                let encodedSid = sid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? sid
                let listParam = list == .allow ? "white" : "black"
                let path = "/admin/api.php?list=\(listParam)&add=\(encodedDomain)&auth=\(encodedSid)"
                _ = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: path, headers: authHeaders())
            }
        }
    }
    
    func removeDomain(domain: String, from list: PiholeDomainListType) async throws {
        let encodedDomain = domain.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? domain
        try await withAuthRetry {
            do {
                // v6 API for exact domains
                let path = authorizedPath("/api/domains/\(list.rawValue)/exact/\(encodedDomain)")
                try await engine.requestVoid(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: path,
                    method: "DELETE",
                    headers: authHeaders()
                )
            } catch {
                // Legacy fallback (Pi-hole v5)
                let queryDomain = domain.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? domain
                let encodedSid = sid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? sid
                let listParam = list == .allow ? "white" : "black"
                let path = "/admin/api.php?list=\(listParam)&sub=\(queryDomain)&auth=\(encodedSid)"
                _ = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: path, headers: authHeaders())
            }
        }
    }

    // MARK: - Top lists (handles varying API response formats)

    func getTopDomains(count: Int = 10) async throws -> [PiholeTopItem] {
        try await withAuthRetry {
            do {
                let raw = try await requestRaw(path: "/api/stats/top_domains?count=\(count)")
                return parseTopItems(from: raw, rootKeys: ["top_domains", "top_queries", "domains", "queries"])
            } catch {
                let raw = try await requestRaw(path: "/api/stats/top_queries?count=\(count)")
                return parseTopItems(from: raw, rootKeys: ["top_domains", "top_queries", "domains", "queries"])
            }
        }
    }

    func getTopBlocked(count: Int = 10) async throws -> [PiholeTopItem] {
        try await withAuthRetry {
            do {
                let raw = try await requestRaw(path: "/api/stats/top_blocked?count=\(count)")
                return parseTopItems(from: raw, rootKeys: ["top_blocked", "top_ads", "blocked", "ads"])
            } catch {
                let raw = try await requestRaw(path: "/api/stats/top_ads?count=\(count)")
                return parseTopItems(from: raw, rootKeys: ["top_blocked", "top_ads", "blocked", "ads"])
            }
        }
    }

    func getTopClients(count: Int = 10) async throws -> [PiholeTopClient] {
        try await withAuthRetry {
            do {
                let raw = try await requestRaw(path: "/api/stats/top_clients?count=\(count)")
                return parseTopClients(from: raw)
            } catch {
                let raw = try await requestRaw(path: "/api/stats/top_sources?count=\(count)")
                return parseTopClients(from: raw)
            }
        }
    }

    func getQueryHistory() async throws -> PiholeQueryHistory {
        try await withAuthRetry {
            do {
                return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/history"), headers: authHeaders())
            } catch {
                let data = try await engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: legacyAuthPath("/admin/api.php?overTimeData10mins"),
                    headers: authHeaders()
                )
                guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                    throw error
                }
                let history = parseLegacyHistory(json)
                if history.isEmpty { throw error }
                return PiholeQueryHistory(history: history)
            }
        }
    }

    func getQueries(from: Date, until: Date) async throws -> [PiholeQueryLogEntry] {
        try await withAuthRetry {
            let fromTs = Int(from.timeIntervalSince1970)
            let untilTs = Int(until.timeIntervalSince1970)

            do {
                let any = try await requestAny(path: "/api/queries?from=\(fromTs)&until=\(untilTs)")
                let parsed = parseQueryEntries(from: any)
                if !parsed.isEmpty {
                    return parsed.sorted { $0.timestamp > $1.timestamp }
                }
            } catch {
                // Continue with legacy fallback.
            }

            let encodedSid = sid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? sid
            let legacyPath = "/admin/api.php?getAllQueriesRaw&from=\(fromTs)&until=\(untilTs)&auth=\(encodedSid)"
            let any = try await requestAny(path: legacyPath)
            return parseQueryEntries(from: any).sorted { $0.timestamp > $1.timestamp }
        }
    }

    func getUpstreams() async throws -> PiholeUpstream {
        try await withAuthRetry {
            return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/stats/upstreams"), headers: authHeaders())
        }
    }

    // MARK: - Private helpers

    private func requestRaw(path: String) async throws -> [String: Any] {
        let data = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath(path), headers: authHeaders())
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "PiHole", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return json
    }

    private func requestAny(path: String) async throws -> Any {
        let data = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath(path), headers: authHeaders())
        return try JSONSerialization.jsonObject(with: data)
    }

    private func validateLegacyAuth(baseURL: String, fallbackURL: String, secret: String) async throws -> Bool {
        let encodedSecret = secret.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? secret
        let path = "/admin/api.php?summaryRaw&auth=\(encodedSecret)"
        do {
            let data = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                headers: authHeaders()
            )
            let json = try JSONSerialization.jsonObject(with: data)
            if let dict = json as? [String: Any] {
                return !dict.isEmpty
            }
            if let array = json as? [Any] {
                return !array.isEmpty
            }
            return false
        } catch {
            return false
        }
    }

    private func parseTopItems(from json: [String: Any], rootKeys: [String]) -> [PiholeTopItem] {
        for key in rootKeys {
            if let dict = json[key] as? [String: Int] {
                return dict.map { PiholeTopItem(domain: $0.key, count: $0.value) }
                    .sorted { $0.count > $1.count }
            }
            if let dict = json[key] as? [String: Double] {
                return dict.map { PiholeTopItem(domain: $0.key, count: Int($0.value)) }
                    .sorted { $0.count > $1.count }
            }
            if let arr = json[key] as? [[String: Any]] {
                return arr.compactMap { item -> PiholeTopItem? in
                    guard let domain = item["domain"] as? String ?? item["query"] as? String ?? item["name"] as? String,
                          let count = item["count"] as? Int ?? item["hits"] as? Int else { return nil }
                    return PiholeTopItem(domain: domain, count: count)
                }
            }
        }
        return []
    }

    private func parseTopClients(from json: [String: Any]) -> [PiholeTopClient] {
        let rootKeys = ["top_clients", "top_sources", "clients", "sources"]
        for key in rootKeys {
            if let dict = json[key] as? [String: Int] {
                return dict.map { (ipStr, count) -> PiholeTopClient in
                    // Format: "hostname|ip" or just "ip"
                    if ipStr.contains("|") {
                        let parts = ipStr.split(separator: "|")
                        return PiholeTopClient(name: String(parts[0]), ip: parts.count > 1 ? String(parts[1]) : String(parts[0]), count: count)
                    }
                    return PiholeTopClient(name: ipStr, ip: ipStr, count: count)
                }.sorted { $0.count > $1.count }
            }
            if let arr = json[key] as? [[String: Any]] {
                return arr.compactMap { item -> PiholeTopClient? in
                    let name = item["name"] as? String ?? item["ip"] as? String ?? "Unknown"
                    let ip = item["ip"] as? String ?? name
                    let count = item["count"] as? Int ?? 0
                    if count == 0 { return nil }
                    return PiholeTopClient(name: name, ip: ip, count: count)
                }.sorted { $0.count > $1.count }
            }
        }
        return []
    }

    private func parseQueryEntries(from payload: Any) -> [PiholeQueryLogEntry] {
        if let dict = payload as? [String: Any] {
            let keys = ["queries", "data", "query_log", "results"]
            for key in keys {
                if let array = dict[key] as? [Any] {
                    let parsed = parseQueryArray(array)
                    if !parsed.isEmpty { return parsed }
                }
            }
            return []
        }

        if let array = payload as? [Any] {
            return parseQueryArray(array)
        }

        return []
    }

    private func parseQueryArray(_ array: [Any]) -> [PiholeQueryLogEntry] {
        var output: [PiholeQueryLogEntry] = []

        for item in array {
            if let dict = item as? [String: Any], let entry = parseQueryDictionary(dict) {
                output.append(entry)
                continue
            }

            if let legacy = item as? [Any], let entry = parseLegacyQueryArray(legacy) {
                output.append(entry)
            }
        }

        return output
    }

    private func parseQueryDictionary(_ dict: [String: Any]) -> PiholeQueryLogEntry? {
        var timestamp = 0
        for key in ["timestamp", "time", "t", "date"] {
            if let parsed = parseTimestamp(dict[key]) {
                timestamp = parsed
                break
            }
        }

        var domain = "unknown"
        for key in ["domain", "query", "name"] {
            if let parsed = parseString(dict[key]) {
                domain = parsed
                break
            }
        }

        var client = "unknown"
        for key in ["client", "client_name", "client_ip", "source"] {
            if let parsed = parseString(dict[key]) {
                client = parsed
                break
            }
        }

        var statusRaw = "unknown"
        for key in ["status", "reply", "type", "result"] {
            if let parsed = parseString(dict[key]) {
                statusRaw = parsed
                break
            }
        }

        if domain == "unknown" && client == "unknown" && timestamp == 0 {
            return nil
        }

        let status = normalizeStatus(statusRaw)
        let id = "\(timestamp)|\(domain)|\(client)|\(status)"
        return PiholeQueryLogEntry(id: id, timestamp: timestamp, domain: domain, client: client, status: status)
    }

    private func parseLegacyQueryArray(_ entry: [Any]) -> PiholeQueryLogEntry? {
        // Legacy shape is generally:
        // [timestamp, type/status, domain, client, replyStatus, ...]
        guard entry.count >= 4 else { return nil }

        let timestamp = parseTimestamp(entry[safe: 0]) ?? 0
        let domain = parseString(entry[safe: 2]) ?? "unknown"
        let client = parseString(entry[safe: 3]) ?? "unknown"

        var statusSource = "unknown"
        if let explicitStatus = parseString(entry[safe: 4]) {
            statusSource = explicitStatus
        } else if let typeStatus = parseString(entry[safe: 1]) {
            statusSource = typeStatus
        }
        let status = normalizeStatus(statusSource)

        let id = "\(timestamp)|\(domain)|\(client)|\(status)"
        return PiholeQueryLogEntry(id: id, timestamp: timestamp, domain: domain, client: client, status: status)
    }

    private func parseString(_ value: Any?) -> String? {
        if let str = value as? String {
            return str
        }

        if let int = value as? Int {
            return String(int)
        }

        if let dbl = value as? Double {
            return String(Int(dbl))
        }

        if let dict = value as? [String: Any] {
            let preferredKeys = ["domain", "query", "name", "ip", "client", "id"]
            for key in preferredKeys {
                if let found = dict[key] as? String, !found.isEmpty {
                    return found
                }
            }
        }

        return nil
    }

    private func parseTimestamp(_ value: Any?) -> Int? {
        if let int = value as? Int {
            return int
        }

        if let dbl = value as? Double {
            return Int(dbl)
        }

        if let str = value as? String, let int = Int(str) {
            return int
        }

        return nil
    }

    private func normalizeStatus(_ raw: String) -> String {
        let lower = raw.lowercased()
        if lower.contains("block") || lower.contains("deny") || lower.contains("gravity") {
            return "blocked"
        }
        if lower.contains("cache") {
            return "cached"
        }
        if lower.contains("forward") || lower.contains("ok") || lower.contains("allow") {
            return "allowed"
        }
        if let code = Int(raw) {
            // Best-effort mapping when only numeric status is available.
            switch code {
            case 0: return "unknown"
            case 1: return "blocked"
            case 2: return "allowed"
            case 3: return "cached"
            default: return "code \(code)"
            }
        }
        return raw
    }

    // MARK: - Helpers

    private static func cleanURL(_ url: String) -> String {
        url.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private func legacyAuthPath(_ basePath: String) -> String {
        let encodedSid = sid.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? sid
        guard !encodedSid.isEmpty else { return basePath }
        let separator = basePath.contains("?") ? "&" : "?"
        return "\(basePath)\(separator)auth=\(encodedSid)"
    }

    private func parseLegacyStats(_ json: [String: Any]) -> PiholeStats {
        let total = legacyInt(json, keys: ["dns_queries_today", "queries_today", "total_queries", "dns_queries"]) ?? 0
        let blocked = legacyInt(json, keys: ["ads_blocked_today", "blocked_queries", "ads_blocked"]) ?? 0
        let percent = legacyDouble(json, keys: ["ads_percentage_today", "percent_blocked", "ads_percentage"]) ?? 0
        let unique = legacyInt(json, keys: ["unique_domains", "unique_domains_today"]) ?? 0
        let forwarded = legacyInt(json, keys: ["queries_forwarded", "forwarded_queries", "forwarded"]) ?? 0
        let cached = legacyInt(json, keys: ["queries_cached", "cached_queries", "cached"]) ?? 0
        let gravityDomains = legacyInt(json, keys: ["domains_being_blocked", "gravity_domains", "domains_blocked"]) ?? 0
        let lastUpdate = parseLegacyTimestamp(json["gravity_last_updated"]) ?? 0

        let queries = PiholeQueryStats(
            total: total,
            blocked: blocked,
            percent_blocked: percent,
            unique_domains: unique,
            forwarded: forwarded,
            cached: cached,
            types: nil
        )
        let gravity = PiholeGravityStats(domains_being_blocked: gravityDomains, last_update: lastUpdate)
        return PiholeStats(queries: queries, gravity: gravity)
    }

    private func parseLegacyHistory(_ json: [String: Any]) -> [PiholeHistoryEntry] {
        let totals = parseLegacySeries(json, keys: ["domains_over_time", "queries_over_time", "over_time"])
        let blocked = parseLegacySeries(json, keys: ["ads_over_time", "blocked_over_time"])

        let allKeys = Set(totals.keys).union(blocked.keys)
        let entries = allKeys.compactMap { key -> PiholeHistoryEntry? in
            guard let timestamp = Int(key) else { return nil }
            return PiholeHistoryEntry(
                timestamp: timestamp,
                total: totals[key] ?? 0,
                blocked: blocked[key] ?? 0
            )
        }

        return entries.sorted { $0.timestamp < $1.timestamp }
    }

    private func parseLegacySeries(_ json: [String: Any], keys: [String]) -> [String: Int] {
        for key in keys {
            if let dict = json[key] as? [String: Any] {
                var output: [String: Int] = [:]
                for (entryKey, value) in dict {
                    if let parsed = legacyInt(value) {
                        output[entryKey] = parsed
                    }
                }
                if !output.isEmpty {
                    return output
                }
            }
        }
        return [:]
    }

    private func legacyInt(_ json: [String: Any], keys: [String]) -> Int? {
        for key in keys {
            if let value = json[key], let parsed = legacyInt(value) {
                return parsed
            }
        }
        return nil
    }

    private func legacyDouble(_ json: [String: Any], keys: [String]) -> Double? {
        for key in keys {
            if let value = json[key], let parsed = legacyDouble(value) {
                return parsed
            }
        }
        return nil
    }

    private func legacyInt(_ value: Any) -> Int? {
        if let int = value as? Int { return int }
        if let double = value as? Double { return Int(double) }
        if let string = value as? String { return Int(string) }
        return nil
    }

    private func legacyDouble(_ value: Any) -> Double? {
        if let double = value as? Double { return double }
        if let int = value as? Int { return Double(int) }
        if let string = value as? String { return Double(string.replacingOccurrences(of: ",", with: ".")) }
        return nil
    }

    private func parseLegacyTimestamp(_ value: Any?) -> Int? {
        guard let value else { return nil }
        if let int = legacyInt(value) { return int }
        if let dict = value as? [String: Any] {
            if let raw = dict["timestamp"] ?? dict["absolute"] ?? dict["file_time"],
               let ts = legacyInt(raw) {
                return ts
            }
        }
        return nil
    }
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        guard indices.contains(index) else { return nil }
        return self[index]
    }
}
