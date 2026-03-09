import Foundation

actor PiHoleAPIClient {
    private let engine = BaseNetworkEngine(serviceType: .pihole)
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var sid: String = ""
    private var authMode: PiHoleAuthMode?

    // MARK: - Configuration

    func configure(url: String, sid: String, authMode: PiHoleAuthMode? = nil, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.sid = sid
        self.authMode = authMode
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

    func authenticate(url: String, password: String) async throws -> String {
        let cleanURL = Self.cleanURL(url)
        guard let authURL = URL(string: "\(cleanURL)/api/auth") else { throw APIError.invalidURL }

        let body = try JSONEncoder().encode(["password": password])
        var req = URLRequest(url: authURL)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = body
        req.timeoutInterval = 8

        var authError: Error?

        do {
            let (data, resp) = try await URLSession.shared.data(for: req)
            guard let http = resp as? HTTPURLResponse else {
                throw APIError.custom("Authentication failed. Check your password and URL.")
            }

            if http.statusCode == 200 {
                let decoded = try JSONDecoder().decode(PiholeAuthResponse.self, from: data)
                return decoded.session.sid
            }

            authError = APIError.custom("Authentication failed. Check your password and URL.")
        } catch {
            authError = error
        }

        if try await validateLegacyAuth(baseURL: cleanURL, secret: password) {
            return password
        }

        throw authError ?? APIError.custom("Authentication failed. Check your password and URL.")
    }

    // MARK: - Stats

    func getStats() async throws -> PiholeStats {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/stats/summary"), headers: authHeaders())
    }

    func getBlockingStatus() async throws -> PiholeBlockingStatus {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/dns/blocking"), headers: authHeaders())
    }

    func setBlocking(enabled: Bool, timer: Int? = nil) async throws {
        struct BlockBody: Encodable {
            let blocking: Bool
            let timer: Int?
        }
        let body = try BlockBody(blocking: enabled, timer: timer).toJSONData()
        try await engine.requestVoid(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/dns/blocking"), method: "POST", headers: authHeaders(), body: body)
    }

    // MARK: - Domains (v6 + legacy v5)

    func getDomains() async throws -> [PiholeDomain] {
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
    
    func addDomain(domain: String, to list: PiholeDomainListType) async throws {
        struct AddDomainBody: Encodable {
            let domain: String
        }
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
    
    func removeDomain(domain: String, from list: PiholeDomainListType) async throws {
        let encodedDomain = domain.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? domain
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

    // MARK: - Top lists (handles varying API response formats)

    func getTopDomains(count: Int = 10) async throws -> [PiholeTopItem] {
        // Try v6 endpoint first, fallback to v5
        do {
            let raw = try await requestRaw(path: "/api/stats/top_domains?count=\(count)")
            return parseTopItems(from: raw, rootKeys: ["top_domains", "top_queries", "domains", "queries"])
        } catch {
            let raw = try await requestRaw(path: "/api/stats/top_queries?count=\(count)")
            return parseTopItems(from: raw, rootKeys: ["top_domains", "top_queries", "domains", "queries"])
        }
    }

    func getTopBlocked(count: Int = 10) async throws -> [PiholeTopItem] {
        do {
            let raw = try await requestRaw(path: "/api/stats/top_blocked?count=\(count)")
            return parseTopItems(from: raw, rootKeys: ["top_blocked", "top_ads", "blocked", "ads"])
        } catch {
            let raw = try await requestRaw(path: "/api/stats/top_ads?count=\(count)")
            return parseTopItems(from: raw, rootKeys: ["top_blocked", "top_ads", "blocked", "ads"])
        }
    }

    func getTopClients(count: Int = 10) async throws -> [PiholeTopClient] {
        do {
            let raw = try await requestRaw(path: "/api/stats/top_clients?count=\(count)")
            return parseTopClients(from: raw)
        } catch {
            let raw = try await requestRaw(path: "/api/stats/top_sources?count=\(count)")
            return parseTopClients(from: raw)
        }
    }

    func getQueryHistory() async throws -> PiholeQueryHistory {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/history"), headers: authHeaders())
    }

    func getQueries(from: Date, until: Date) async throws -> [PiholeQueryLogEntry] {
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

    func getUpstreams() async throws -> PiholeUpstream {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: authorizedPath("/api/stats/upstreams"), headers: authHeaders())
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

    private func validateLegacyAuth(baseURL: String, secret: String) async throws -> Bool {
        let encodedSecret = secret.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? secret
        guard let url = URL(string: "\(baseURL)/admin/api.php?summaryRaw&auth=\(encodedSecret)") else {
            return false
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 8

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
                return false
            }
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
}

private extension Array {
    subscript(safe index: Int) -> Element? {
        guard indices.contains(index) else { return nil }
        return self[index]
    }
}
