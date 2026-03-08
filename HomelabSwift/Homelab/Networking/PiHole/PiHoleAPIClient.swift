import Foundation

actor PiHoleAPIClient {
    private let engine = BaseNetworkEngine(serviceType: .pihole)
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var sid: String = ""

    // MARK: - Configuration

    func configure(url: String, sid: String, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.sid = sid
    }

    private func authHeaders() -> [String: String] {
        ["Content-Type": "application/json", "X-FTL-SID": sid]
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

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, http.statusCode == 200 else {
            throw APIError.custom("Authentication failed. Check your password and URL.")
        }

        let decoded = try JSONDecoder().decode(PiholeAuthResponse.self, from: data)
        return decoded.session.sid
    }

    // MARK: - Stats

    func getStats() async throws -> PiholeStats {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/stats/summary", headers: authHeaders())
    }

    func getBlockingStatus() async throws -> PiholeBlockingStatus {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/dns/blocking", headers: authHeaders())
    }

    func setBlocking(enabled: Bool, timer: Int? = nil) async throws {
        struct BlockBody: Encodable {
            let blocking: Bool
            let timer: Int?
        }
        let body = try BlockBody(blocking: enabled, timer: timer).toJSONData()
        try await engine.requestVoid(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/dns/blocking", method: "POST", headers: authHeaders(), body: body)
    }

    // MARK: - Domains (v6 API)

    private struct DomainListResponse: Codable, Sendable {
        let domains: [PiholeDomain]
    }

    func getDomains() async throws -> [PiholeDomain] {
        let response: DomainListResponse = try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/domains", headers: authHeaders())
        return response.domains
    }
    
    func addDomain(domain: String, to list: PiholeDomainListType) async throws {
        struct AddDomainBody: Encodable {
            let domain: String
        }
        let body = try AddDomainBody(domain: domain).toJSONData()
        let path = "/api/domains/\(list.rawValue)/exact"
        try await engine.requestVoid(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: "POST", headers: authHeaders(), body: body)
    }
    
    func removeDomain(domain: String, from list: PiholeDomainListType) async throws {
        // v6 API for exact domains
        let path = "/api/domains/\(list.rawValue)/exact/\(domain)"
        try await engine.requestVoid(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: "DELETE", headers: authHeaders())
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
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/history", headers: authHeaders())
    }

    func getUpstreams() async throws -> PiholeUpstream {
        return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: "/api/stats/upstreams", headers: authHeaders())
    }

    // MARK: - Private helpers

    private func requestRaw(path: String) async throws -> [String: Any] {
        let data = try await engine.requestData(baseURL: baseURL, fallbackURL: fallbackURL, path: path, headers: authHeaders())
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw APIError.decodingError(NSError(domain: "PiHole", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid JSON"]))
        }
        return json
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

    // MARK: - Helpers

    private static func cleanURL(_ url: String) -> String {
        url.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }
}
