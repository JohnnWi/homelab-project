import Foundation

actor AdGuardHomeAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var username: String = ""
    private var password: String = ""

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .adguardHome, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(url: String, username: String, password: String, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanControlURL(url)
        self.fallbackURL = Self.cleanControlURL(fallbackUrl ?? "")
        self.username = username
        self.password = password
    }

    // MARK: - Ping

    func ping() async -> Bool {
        if baseURL.isEmpty { return false }
        if await engine.pingURL("\(baseURL)/status", extraHeaders: authHeaders()) { return true }
        if !fallbackURL.isEmpty {
            return await engine.pingURL("\(fallbackURL)/status", extraHeaders: authHeaders())
        }
        return false
    }

    // MARK: - Authentication

    func authenticate(url: String, username: String, password: String) async throws {
        let cleanURL = Self.cleanControlURL(url)
        let data = try await engine.requestData(
            baseURL: cleanURL,
            fallbackURL: "",
            path: "/status",
            headers: Self.basicAuthHeaders(username: username, password: password)
        )
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "AdGuard", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        _ = parseStatus(json)
    }

    // MARK: - Status & Stats

    func getStatus() async throws -> AdGuardStatus {
        let data = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: "/status", headers: authHeaders())
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "AdGuard", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return parseStatus(json)
    }

    func getStats(recentMs: Int? = nil) async throws -> AdGuardStats {
        let path: String
        if let recentMs {
            path = "/stats?interval=\(recentMs)"
        } else {
            path = "/stats"
        }
        let data = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: path, headers: authHeaders())
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "AdGuard", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return parseStats(json)
    }

    func setProtection(enabled: Bool, durationSeconds: Int? = nil) async throws {
        let durationMs = durationSeconds.map { max(0, $0) * 1000 }
        let body: [String: Any] = [
            "enabled": enabled,
            "duration": durationMs as Any
        ]
        let data = try JSONSerialization.data(withJSONObject: body)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/protection",
            method: "POST",
            headers: authHeaders(),
            body: data
        )
    }

    // MARK: - Query Log

    func getQueryLog(limit: Int = 200, search: String? = nil, responseStatus: String? = nil, offset: Int? = nil) async throws -> AdGuardQueryLogPage {
        var items: [URLQueryItem] = [
            URLQueryItem(name: "limit", value: String(limit))
        ]
        if let search, !search.isEmpty { items.append(URLQueryItem(name: "search", value: search)) }
        if let responseStatus, !responseStatus.isEmpty { items.append(URLQueryItem(name: "response_status", value: responseStatus)) }
        if let offset { items.append(URLQueryItem(name: "offset", value: String(offset))) }

        var components = URLComponents()
        components.path = "/querylog"
        components.queryItems = items
        let path = components.url?.absoluteString ?? "/querylog"

        let data = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: path, headers: authHeaders())
        let root = try JSONSerialization.jsonObject(with: data)
        let (entries, total, page, pages, oldest) = parseQueryLogPayload(root)
        return AdGuardQueryLogPage(items: entries, total: total, page: page, pages: pages, oldest: oldest)
    }

    // MARK: - Filtering

    func getFilteringStatus() async throws -> AdGuardFilteringStatus {
        let data = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: "/filtering/status", headers: authHeaders())
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "AdGuard", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        let userRules = json["user_rules"] as? [String] ?? []
        let filters = parseFilters(json["filters"])
        let whitelistFilters = parseFilters(json["whitelist_filters"])
        return AdGuardFilteringStatus(userRules: userRules, filters: filters, whitelistFilters: whitelistFilters)
    }

    func setUserRules(_ rules: [String]) async throws {
        let body = ["rules": rules]
        let data = try JSONSerialization.data(withJSONObject: body)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/filtering/set_rules",
            method: "POST",
            headers: authHeaders(),
            body: data
        )
    }

    func addFilter(name: String, url: String, whitelist: Bool, enabled: Bool = true) async throws {
        let body: [String: Any] = [
            "name": name,
            "url": url,
            "whitelist": whitelist,
            "enabled": enabled
        ]
        let data = try JSONSerialization.data(withJSONObject: body)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/filtering/add_url",
            method: "POST",
            headers: authHeaders(),
            body: data
        )
    }

    func setFilter(_ filter: AdGuardFilter, enabled: Bool, whitelist: Bool) async throws {
        let dataPayload: [String: Any] = [
            "enabled": enabled,
            "name": filter.name,
            "url": filter.url,
            "whitelist": whitelist
        ]
        let body: [String: Any] = [
            "data": dataPayload,
            "url": filter.url,
            "whitelist": whitelist
        ]
        let data = try JSONSerialization.data(withJSONObject: body)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/filtering/set_url",
            method: "POST",
            headers: authHeaders(),
            body: data
        )
    }

    func removeFilter(url: String, whitelist: Bool) async throws {
        let body: [String: Any] = [
            "url": url,
            "whitelist": whitelist
        ]
        let data = try JSONSerialization.data(withJSONObject: body)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/filtering/remove_url",
            method: "POST",
            headers: authHeaders(),
            body: data
        )
    }

    // MARK: - Blocked Services

    func getBlockedServicesAll() async throws -> AdGuardBlockedServicesAll {
        let data = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: "/blocked_services/all", headers: authHeaders())
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "AdGuard", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        let servicesRaw = json["blocked_services"] as? [[String: Any]] ?? []
        let services = servicesRaw.compactMap { dict -> AdGuardBlockedService? in
            let id = dict["id"] as? String ?? ""
            let name = dict["name"] as? String ?? id
            let rules = dict["rules"] as? [String] ?? []
            let groupId = dict["group_id"] as? String
            let iconSvg = dict["icon_svg"] as? String
            guard !id.isEmpty else { return nil }
            return AdGuardBlockedService(id: id, name: name, rules: rules, groupId: groupId, iconSvg: iconSvg)
        }
        let groupsRaw = json["groups"] as? [[String: Any]] ?? []
        let groups = groupsRaw.compactMap { dict -> AdGuardServiceGroup? in
            let id = dict["id"] as? String ?? ""
            let name = dict["name"] as? String ?? id
            guard !id.isEmpty else { return nil }
            return AdGuardServiceGroup(id: id, name: name)
        }
        return AdGuardBlockedServicesAll(services: services, groups: groups)
    }

    func getBlockedServicesSchedule() async throws -> AdGuardBlockedServicesSchedule {
        let data = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: "/blocked_services/get", headers: authHeaders())
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "AdGuard", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        let ids = json["ids"] as? [String] ?? []
        let schedule = parseSchedule(json["schedule"])
        return AdGuardBlockedServicesSchedule(ids: ids, schedule: schedule)
    }

    func updateBlockedServices(ids: [String], schedule: [String: AdGuardJSONValue]?) async throws {
        var body: [String: Any] = ["ids": ids]
        if let schedule { body["schedule"] = schedule.mapValues { $0.toAny } }
        let data = try JSONSerialization.data(withJSONObject: body)
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/blocked_services/update",
            method: "PUT",
            headers: authHeaders(),
            body: data
        )
    }

    // MARK: - Rewrites

    func getRewrites() async throws -> [AdGuardRewriteEntry] {
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/rewrite/list",
            headers: authHeaders()
        )
    }

    func addRewrite(domain: String, answer: String) async throws {
        let body = try JSONSerialization.data(withJSONObject: [
            "domain": domain,
            "answer": answer
        ])
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/rewrite/add",
            method: "POST",
            headers: authHeaders(),
            body: body
        )
    }

    func deleteRewrite(domain: String, answer: String) async throws {
        let body = try JSONSerialization.data(withJSONObject: [
            "domain": domain,
            "answer": answer
        ])
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/rewrite/delete",
            method: "POST",
            headers: authHeaders(),
            body: body
        )
    }

    func updateRewrite(target: AdGuardRewriteEntry, update: AdGuardRewriteEntry) async throws {
        let body = try JSONSerialization.data(withJSONObject: [
            "target": ["domain": target.domain, "answer": target.answer],
            "update": ["domain": update.domain, "answer": update.answer]
        ])
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/rewrite/update",
            method: "POST",
            headers: authHeaders(),
            body: body
        )
    }

    // MARK: - Helpers

    private func authHeaders() -> [String: String] {
        Self.basicAuthHeaders(username: username, password: password)
    }

    private static func basicAuthHeaders(username: String, password: String) -> [String: String] {
        var headers = ["Content-Type": "application/json"]
        let creds = "\(username):\(password)"
        guard !username.isEmpty || !password.isEmpty else { return headers }
        let token = Data(creds.utf8).base64EncodedString()
        headers["Authorization"] = "Basic \(token)"
        return headers
    }

    private func parseStats(_ json: [String: Any]) -> AdGuardStats {
        let total = intValue(json["num_dns_queries"])
        let blocked = intValue(json["num_blocked_filtering"])
        let safebrowsing = intValue(json["num_replaced_safebrowsing"])
        let safesearch = intValue(json["num_replaced_safesearch"])
        let parental = intValue(json["num_replaced_parental"])
        let avg = doubleValue(json["avg_processing_time"])

        let topQueried = parseTopItems(json["top_queried_domains"])
        let topBlocked = parseTopItems(json["top_blocked_domains"])
        let topClients = parseTopItems(json["top_clients"])

        let dnsQueries = intArray(json["dns_queries"])
        let blockedSeries = intArray(json["blocked_filtering"])

        return AdGuardStats(
            totalQueries: total,
            blockedFiltering: blocked,
            replacedSafebrowsing: safebrowsing,
            replacedSafesearch: safesearch,
            replacedParental: parental,
            avgProcessingTime: avg,
            topQueried: topQueried,
            topBlocked: topBlocked,
            topClients: topClients,
            dnsQueries: dnsQueries,
            blockedSeries: blockedSeries
        )
    }

    private func parseTopItems(_ any: Any?) -> [AdGuardTopItem] {
        guard let arr = any as? [[String: Any]] else { return [] }
        return arr.compactMap { dict in
            guard let (key, value) = dict.first else { return nil }
            return AdGuardTopItem(name: key, count: intValue(value))
        }
    }

    private func parseStatus(_ json: [String: Any]) -> AdGuardStatus {
        let version = json["version"] as? String
        let language = json["language"] as? String
        let running = boolValue(json["running"])
        let protection = boolValue(json["protection_enabled"]) ?? false
        let disabledUntil = intValue(json["protection_disabled_until"])
        let dnsAddresses = stringArray(json["dns_addresses"])
        let dnsPort = intValue(json["dns_port"])
        let httpPort = intValue(json["http_port"])
        let startTime = json["start_time"] as? String
        return AdGuardStatus(
            version: version,
            language: language,
            running: running,
            protection_enabled: protection,
            protection_disabled_until: disabledUntil == 0 ? nil : disabledUntil,
            dns_addresses: dnsAddresses,
            dns_port: dnsPort == 0 ? nil : dnsPort,
            http_port: httpPort == 0 ? nil : httpPort,
            start_time: startTime
        )
    }

    private func parseFilters(_ any: Any?) -> [AdGuardFilter] {
        guard let arr = any as? [[String: Any]] else { return [] }
        return arr.compactMap { dict in
            let id = intValue(dict["id"])
            let name = dict["name"] as? String ?? dict["title"] as? String ?? "Filter \(id)"
            let url = dict["url"] as? String ?? ""
            let enabled: Bool
            if let b = dict["enabled"] as? Bool { enabled = b }
            else { enabled = intValue(dict["enabled"]) == 1 }
            let rulesCount = intValue(dict["rules_count"])
            let lastUpdated = dict["last_updated"] as? String
            return AdGuardFilter(id: id, name: name, url: url, enabled: enabled, rulesCount: rulesCount, lastUpdated: lastUpdated)
        }
    }

    private func parseQueryEntry(_ item: [String: Any]) -> AdGuardQueryLogEntry {
        let id = item["id"] as? String ?? UUID().uuidString
        let time = item["time"] as? String ?? ""
        let question = item["question"] as? [String: Any]
        let domain = question?["name"] as? String ?? item["domain"] as? String ?? ""
        let clientIp = item["client"] as? String ?? ""
        let clientInfo = item["client_info"] as? [String: Any]
        let clientName = clientInfo?["name"] as? String
        let clientDisplay: String
        if let clientName, !clientName.isEmpty, !clientIp.isEmpty, clientName != clientIp {
            clientDisplay = "\(clientName) (\(clientIp))"
        } else if let clientName, !clientName.isEmpty {
            clientDisplay = clientName
        } else {
            clientDisplay = clientIp
        }
        let responseStatusAny = item["response_status"]
        let responseStatusString = responseStatusAny as? String
        let status = responseStatusString ?? (item["status"] as? String ?? "")
        let reason = item["reason"] as? String
        var blockedFlag = boolValue(item["blocked"])
        if blockedFlag == nil {
            if let responseStatusString {
                let lower = responseStatusString.lowercased()
                if lower.contains("blocked") || lower.contains("filtered") {
                    blockedFlag = true
                } else if lower.contains("allowed") {
                    blockedFlag = false
                }
            } else if let responseStatusInt = responseStatusAny as? Int {
                blockedFlag = responseStatusInt != 0
            }
            if blockedFlag == nil, let reason {
                if reason.hasPrefix("Filtered") {
                    blockedFlag = true
                } else if reason.hasPrefix("NotFiltered") || reason.hasPrefix("Rewrite") {
                    blockedFlag = false
                }
            }
        }
        return AdGuardQueryLogEntry(id: id, time: time, domain: domain, client: clientDisplay, status: status, reason: reason, blocked: blockedFlag)
    }

    private func parseQueryLogPayload(_ payload: Any) -> ([AdGuardQueryLogEntry], Int, Int, Int, String?) {
        if let array = payload as? [[String: Any]] {
            return (array.map(parseQueryEntry), 0, 0, 0, nil)
        }

        guard let json = payload as? [String: Any] else {
            return ([], 0, 0, 0, nil)
        }

        let dataArray: [[String: Any]] = {
            if let arr = json["data"] as? [[String: Any]] { return arr }
            if let arr = json["items"] as? [[String: Any]] { return arr }
            if let arr = (json["data"] as? [Any])?.compactMap({ $0 as? [String: Any] }) { return arr }
            if let inner = json["data"] as? [String: Any] {
                if let arr = inner["data"] as? [[String: Any]] { return arr }
                if let arr = inner["items"] as? [[String: Any]] { return arr }
            }
            return []
        }()

        let entries = dataArray.map(parseQueryEntry)
        let total = intValue(json["total"])
        let page = intValue(json["page"])
        let pages = intValue(json["pages"])
        let oldest = json["oldest"] as? String ?? (json["data"] as? [String: Any])?["oldest"] as? String
        return (entries, total, page, pages, oldest)
    }

    private func intValue(_ any: Any?) -> Int {
        if let v = any as? Int { return v }
        if let v = any as? Double { return Int(v) }
        if let v = any as? String, let i = Int(v) { return i }
        return 0
    }

    private func boolValue(_ any: Any?) -> Bool? {
        if let v = any as? Bool { return v }
        if let v = any as? Int { return v != 0 }
        if let v = any as? Double { return v != 0 }
        if let v = any as? String {
            let lower = v.lowercased()
            if ["true", "1", "yes", "enabled", "on"].contains(lower) { return true }
            if ["false", "0", "no", "disabled", "off"].contains(lower) { return false }
        }
        return nil
    }

    private func stringArray(_ any: Any?) -> [String]? {
        if let arr = any as? [String] { return arr }
        if let arr = any as? [Any] {
            let mapped = arr.compactMap { $0 as? String }
            return mapped.isEmpty ? nil : mapped
        }
        if let str = any as? String, !str.isEmpty {
            return [str]
        }
        return nil
    }

    private func doubleValue(_ any: Any?) -> Double {
        if let v = any as? Double { return v }
        if let v = any as? Int { return Double(v) }
        if let v = any as? String, let d = Double(v) { return d }
        return 0
    }

    private func intArray(_ any: Any?) -> [Int] {
        guard let arr = any as? [Any] else { return [] }
        return arr.map(intValue)
    }

    private static func cleanControlURL(_ url: String) -> String {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let cleaned = trimmed.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
        if cleaned.lowercased().hasSuffix("/control") {
            return cleaned
        }
        return cleaned + "/control"
    }

    private func parseSchedule(_ any: Any?) -> [String: AdGuardJSONValue]? {
        guard let dict = any as? [String: Any] else { return nil }
        var output: [String: AdGuardJSONValue] = [:]
        for (key, value) in dict {
            if let parsed = parseJSONValue(value) {
                output[key] = parsed
            }
        }
        return output.isEmpty ? nil : output
    }

    private func parseJSONValue(_ any: Any?) -> AdGuardJSONValue? {
        guard let any else { return nil }
        if any is NSNull { return .null }
        if let value = any as? String { return .string(value) }
        if let value = any as? Bool { return .bool(value) }
        if let value = any as? Int { return .number(Double(value)) }
        if let value = any as? Double { return .number(value) }
        if let array = any as? [Any] {
            let parsed = array.compactMap(parseJSONValue)
            return .array(parsed)
        }
        if let dict = any as? [String: Any] {
            var output: [String: AdGuardJSONValue] = [:]
            for (key, value) in dict {
                if let parsed = parseJSONValue(value) {
                    output[key] = parsed
                }
            }
            return .object(output)
        }
        return nil
    }
}
