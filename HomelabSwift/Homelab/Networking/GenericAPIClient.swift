import Foundation

struct GenericStatusDetail: Identifiable, Sendable {
    let id = UUID()
    let label: String
    let value: String
}

struct JellyseerrRequestSummary: Identifiable, Sendable {
    let id: Int
    let title: String
    let status: String
    let isPending: Bool
    let requestedBy: String?
    let requestedAt: String?
}

struct JellyseerrSnapshot: Sendable {
    let version: String?
    let totalRequests: Int
    let pendingRequests: Int
    let approvedRequests: Int
    let availableRequests: Int
    let recentRequests: [JellyseerrRequestSummary]
}

struct ProwlarrIndexerSummary: Identifiable, Sendable {
    let id: Int
    let name: String
    let enabled: Bool
    let status: String
}

struct ProwlarrSnapshot: Sendable {
    let version: String?
    let indexers: [ProwlarrIndexerSummary]
    let applications: [String]
    let unhealthyCount: Int
    let healthIssues: [String]
    let recentHistory: [String]
}

struct BazarrSnapshot: Sendable {
    let version: String?
    let badges: [GenericStatusDetail]
    let issues: [String]
    let tasks: [String]
}

struct GluetunSnapshot: Sendable {
    let connectionStatus: String?
    let publicIP: String?
    let serverName: String?
    let country: String?
    let forwardedPort: String?
    let vpnProvider: String?
}

struct FlaresolverrSnapshot: Sendable {
    let version: String?
    let status: String?
    let message: String?
    let sessions: [String]
}

struct GenericLookupResult: Identifiable, Sendable {
    let id: String
    let title: String
    let subtitle: String?
    let supporting: String?
    let status: String?
    let posterURL: String?
    let detailsURL: String?
    let details: [String: String]
    let requestMediaType: String?
    let requestMediaId: Int?
}

enum GenericServiceSnapshot: Sendable {
    case jellyseerr(JellyseerrSnapshot)
    case prowlarr(ProwlarrSnapshot)
    case bazarr(BazarrSnapshot)
    case gluetun(GluetunSnapshot)
    case flaresolverr(FlaresolverrSnapshot)
}

actor GenericAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private let serviceType: ServiceType
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""

    init(serviceType: ServiceType, instanceId: UUID) {
        self.serviceType = serviceType
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: serviceType, instanceId: instanceId)
    }

    func configure(url: String, fallbackUrl: String? = nil, apiKey: String? = nil, allowSelfSigned: Bool? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: serviceType, instanceId: self.instanceId, allowSelfSigned: self.storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let headers = authHeaders()

        for path in candidatePaths() {
            do {
                _ = try await engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: path,
                    headers: headers
                )
                return true
            } catch {
                continue
            }
        }
        return false
    }

    func statusDetails() async -> [GenericStatusDetail] {
        guard !baseURL.isEmpty else { return [] }
        let headers = authHeaders()

        for path in candidatePaths() {
            do {
                let data = try await engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: path,
                    headers: headers
                )
                guard let object = try? JSONSerialization.jsonObject(with: data) else { continue }
                let details = extractDetails(from: object)
                if !details.isEmpty {
                    return details
                }
            } catch {
                continue
            }
        }
        return []
    }

    func serviceSnapshot() async -> GenericServiceSnapshot? {
        switch serviceType {
        case .jellyseerr:
            return await jellyseerrSnapshot().map(GenericServiceSnapshot.jellyseerr)
        case .prowlarr:
            return await prowlarrSnapshot().map(GenericServiceSnapshot.prowlarr)
        case .bazarr:
            return await bazarrSnapshot().map(GenericServiceSnapshot.bazarr)
        case .gluetun:
            return await gluetunSnapshot().map(GenericServiceSnapshot.gluetun)
        case .flaresolverr:
            return await flaresolverrSnapshot().map(GenericServiceSnapshot.flaresolverr)
        default:
            return nil
        }
    }

    func approveJellyseerrRequest(_ requestId: Int) async throws {
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/request/\(requestId)/approve",
            method: "POST",
            headers: authHeaders()
        )
    }

    func declineJellyseerrRequest(_ requestId: Int) async throws {
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/request/\(requestId)/decline",
            method: "POST",
            headers: authHeaders()
        )
    }

    func approveOldestPendingJellyseerrRequest() async throws -> String? {
        let pending = try await oldestPendingJellyseerrRequest()
        try await approveJellyseerrRequest(pending.id)
        return pending.title
    }

    func declineOldestPendingJellyseerrRequest() async throws -> String? {
        let pending = try await oldestPendingJellyseerrRequest()
        try await declineJellyseerrRequest(pending.id)
        return pending.title
    }

    func triggerJellyseerrRecentScanJob() async throws -> String {
        try await runJellyseerrJob(keywordCandidates: ["recently", "added", "new"])
    }

    func triggerJellyseerrFullScanJob() async throws -> String {
        try await runJellyseerrJob(keywordCandidates: ["scan", "sync", "plex", "jellyfin", "emby", "radarr", "sonarr"])
    }

    func triggerProwlarrIndexerTest() async throws {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        var lastError: Error?
        for path in ["/api/v1/indexer/testall", "/api/v1/indexer/test"] {
            do {
                try await engine.requestVoid(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: path,
                    method: "POST",
                    headers: authHeaders()
                )
                return
            } catch {
                lastError = error
            }
        }
        do {
            try await runProwlarrCommand(candidates: ["TestAllIndexers", "IndexerSync", "ApplicationIndexerSync"])
            return
        } catch {
            throw lastError ?? error
        }
    }

    func triggerProwlarrAppSync() async throws {
        try await runProwlarrCommand(candidates: ["ApplicationIndexerSync", "ApplicationSync", "IndexerSync"])
    }

    func triggerProwlarrHealthCheck() async throws {
        try await runProwlarrCommand(candidates: ["HealthCheck", "CheckHealth"])
    }

    func triggerGluetunRestart() async throws {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        var lastError: Error?
        for method in ["POST", "PUT"] {
            do {
                try await engine.requestVoid(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: "/v1/openvpn/restart",
                    method: method,
                    headers: authHeaders()
                )
                return
            } catch {
                lastError = error
            }
        }
        throw lastError ?? APIError.custom("Failed to restart VPN tunnel")
    }

    func createFlaresolverrSession() async throws -> String {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        let generatedSession = "homelab-\(UUID().uuidString.lowercased().prefix(8))"
        let body = try Self.jsonData([
            "cmd": "sessions.create",
            "session": generatedSession
        ])
        let response = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/v1",
            method: "POST",
            headers: jsonActionHeaders(),
            body: body
        )
        guard let object = try? JSONSerialization.jsonObject(with: response) as? [String: Any] else {
            return String(generatedSession)
        }
        guard let status = stringValue(object["status"])?.lowercased(), status == "ok" else {
            throw APIError.custom(stringValue(object["message"]) ?? "Failed to create session")
        }
        return stringValue(object["session"]) ?? String(generatedSession)
    }

    func destroyFlaresolverrSession(_ session: String) async throws {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        let body = try Self.jsonData([
            "cmd": "sessions.destroy",
            "session": session
        ])
        let response = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/v1",
            method: "POST",
            headers: jsonActionHeaders(),
            body: body
        )
        guard let object = try? JSONSerialization.jsonObject(with: response) as? [String: Any] else {
            return
        }
        guard let status = stringValue(object["status"])?.lowercased(), status == "ok" else {
            throw APIError.custom(stringValue(object["message"]) ?? "Failed to destroy session")
        }
    }

    func requestJellyseerrContent(mediaType: String, mediaId: Int) async throws {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        let normalizedMediaType = mediaType.lowercased()
        guard normalizedMediaType == "movie" || normalizedMediaType == "tv" else {
            throw APIError.custom("Unsupported Jellyseerr media type")
        }
        let body = try Self.jsonData([
            "mediaType": normalizedMediaType,
            "mediaId": mediaId
        ])
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/request",
            method: "POST",
            headers: jsonActionHeaders(),
            body: body
        )
    }

    func searchContent(query: String, limit: Int = 20) async throws -> [GenericLookupResult] {
        let normalized = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return [] }

        switch serviceType {
        case .jellyseerr:
            return try await searchJellyseerrContent(query: normalized, limit: limit)
        case .prowlarr:
            return try await searchProwlarrContent(query: normalized, limit: limit)
        default:
            return []
        }
    }

    // MARK: - Service snapshots

    private func jellyseerrSnapshot() async -> JellyseerrSnapshot? {
        let headers = authHeaders()
        let status = await requestJSONObject(path: "/api/v1/status", headers: headers) as? [String: Any]
        let requestsObject = await requestJSONObject(path: "/api/v1/request?take=25&skip=0&sort=added&filter=all", headers: headers)
        let requests = parseJellyseerrRequests(from: requestsObject)
        guard status != nil || !requests.isEmpty else { return nil }

        let pendingCount = requests.filter(\.isPending).count
        let approvedCount = requests.filter { item in
            let normalized = item.status.lowercased()
            return normalized.contains("approved") || normalized.contains("processing")
        }.count
        let availableCount = requests.filter { $0.status.lowercased().contains("available") }.count
        let totalCount = totalJellyseerrRequests(from: requestsObject) ?? requests.count
        let version = valueForPath("version", in: status as Any) ?? valueForPath("appData.version", in: status as Any)
        return JellyseerrSnapshot(
            version: version,
            totalRequests: totalCount,
            pendingRequests: pendingCount,
            approvedRequests: approvedCount,
            availableRequests: availableCount,
            recentRequests: requests
        )
    }

    private func prowlarrSnapshot() async -> ProwlarrSnapshot? {
        let headers = authHeaders()
        let status = await requestJSONObject(path: "/api/v1/system/status", headers: headers)
        let healthObject = await requestJSONObject(path: "/api/v1/health", headers: headers)
        let indexersObject = await requestJSONObject(path: "/api/v1/indexer", headers: headers)
        let appsObject = await requestJSONObject(path: "/api/v1/applications", headers: headers)
        let historyObject = await requestJSONObject(path: "/api/v1/history?page=1&pageSize=10&sortDirection=descending", headers: headers)

        let indexers = parseProwlarrIndexers(from: indexersObject)
        let applications = parseNameList(from: appsObject)
        let healthIssues = parseHealthMessages(from: healthObject)
        let recentHistory = parseProwlarrHistory(from: historyObject)
        guard status != nil || !indexers.isEmpty || !applications.isEmpty || !healthIssues.isEmpty || !recentHistory.isEmpty else { return nil }

        let unhealthyCount = indexers.filter {
            let status = $0.status.lowercased()
            return status.contains("error") || status.contains("down") || status.contains("unhealthy")
        }.count + healthIssues.count

        return ProwlarrSnapshot(
            version: valueForPath("version", in: status as Any),
            indexers: indexers,
            applications: applications,
            unhealthyCount: unhealthyCount,
            healthIssues: healthIssues,
            recentHistory: recentHistory
        )
    }

    private func bazarrSnapshot() async -> BazarrSnapshot? {
        let headers = authHeaders()
        let status = await requestJSONObject(path: "/api/system/status", headers: headers)
        let badgesObject = await requestJSONObject(path: "/api/badges", headers: headers)
        let healthObject = await requestJSONObject(path: "/api/system/health", headers: headers)
        let tasksObject = await requestJSONObject(path: "/api/system/tasks", headers: headers)

        let badges = parseBazarrBadges(from: badgesObject)
        let issues = parseHealthMessages(from: healthObject)
        let tasks = parseBazarrTasks(from: tasksObject)
        guard status != nil || !badges.isEmpty || !issues.isEmpty || !tasks.isEmpty else { return nil }

        return BazarrSnapshot(
            version: valueForPath("version", in: status as Any) ?? valueForPath("data.version", in: status as Any),
            badges: badges,
            issues: issues,
            tasks: tasks
        )
    }

    private func gluetunSnapshot() async -> GluetunSnapshot? {
        let headers = authHeaders()
        let vpnObject = await requestJSONObject(path: "/v1/openvpn/status", headers: headers)
        let publicIPObject = await requestJSONObject(path: "/v1/publicip/ip", headers: headers)
        let forwardedObject = await requestJSONObject(path: "/v1/openvpn/portforwarded", headers: headers)
        let forwardedString = await requestString(path: "/v1/openvpn/portforwarded", headers: headers)
        let publicIPString = await requestString(path: "/v1/publicip/ip", headers: headers)
        guard vpnObject != nil || publicIPObject != nil || publicIPString != nil || forwardedObject != nil || forwardedString != nil else { return nil }

        let connectionStatus = valueForPath("status", in: vpnObject as Any) ?? valueForPath("openvpn.status", in: vpnObject as Any)
        let serverName = valueForPath("server_name", in: vpnObject as Any) ?? valueForPath("openvpn.server_name", in: vpnObject as Any)
        let vpnProvider = valueForPath("provider", in: vpnObject as Any)
            ?? valueForPath("vpn.provider", in: vpnObject as Any)
            ?? valueForPath("openvpn.provider", in: vpnObject as Any)
        let publicIP = valueForPath("public_ip", in: publicIPObject as Any)
            ?? valueForPath("ip", in: publicIPObject as Any)
            ?? publicIPString
        let country = valueForPath("country", in: publicIPObject as Any) ?? valueForPath("location.country", in: publicIPObject as Any)
        let forwardedPort = valueForPath("port", in: forwardedObject as Any)
            ?? valueForPath("port_forwarded", in: forwardedObject as Any)
            ?? forwardedString

        return GluetunSnapshot(
            connectionStatus: connectionStatus,
            publicIP: publicIP,
            serverName: serverName,
            country: country,
            forwardedPort: forwardedPort,
            vpnProvider: vpnProvider
        )
    }

    private func flaresolverrSnapshot() async -> FlaresolverrSnapshot? {
        let headers = authHeaders()
        let healthObject = await requestJSONObject(path: "/health", headers: headers)
        let rootObject = await requestJSONObject(path: "/", headers: headers)
        let sessionsObject = await requestJSONObject(
            path: "/v1",
            headers: jsonActionHeaders(),
            method: "POST",
            body: try? Self.jsonData(["cmd": "sessions.list"])
        )
        let sessions = parseFlaresolverrSessions(from: sessionsObject)
        guard healthObject != nil || rootObject != nil || sessionsObject != nil else { return nil }

        return FlaresolverrSnapshot(
            version: valueForPath("version", in: healthObject as Any) ?? valueForPath("version", in: rootObject as Any),
            status: valueForPath("status", in: healthObject as Any)
                ?? valueForPath("status", in: rootObject as Any)
                ?? valueForPath("status", in: sessionsObject as Any),
            message: valueForPath("message", in: healthObject as Any)
                ?? valueForPath("msg", in: healthObject as Any)
                ?? valueForPath("message", in: rootObject as Any)
                ?? valueForPath("message", in: sessionsObject as Any),
            sessions: sessions
        )
    }

    // MARK: - Status detail extraction

    private func extractDetails(from object: Any) -> [GenericStatusDetail] {
        switch serviceType {
        case .jellyseerr:
            return detailPairs(
                from: object,
                pairs: [
                    ("Version", ["version", "appData.version"]),
                    ("Commit", ["commitTag", "appData.commitTag"]),
                    ("DB", ["dbType", "appData.dbType"])
                ]
            )
        case .prowlarr:
            return detailPairs(
                from: object,
                pairs: [
                    ("Version", ["version", "appVersion"]),
                    ("Branch", ["branch"]),
                    ("Package", ["packageVersion"])
                ]
            )
        case .bazarr:
            return detailPairs(
                from: object,
                pairs: [
                    ("Version", ["version", "data.version"]),
                    ("Package", ["packageVersion", "data.packageVersion"]),
                    ("Branch", ["branch", "data.branch"])
                ]
            )
        case .gluetun:
            return detailPairs(
                from: object,
                pairs: [
                    ("Status", ["status", "openvpn.status", "vpn.status"]),
                    ("IP", ["public_ip", "publicIP", "ip"]),
                    ("Server", ["server_name", "serverName", "openvpn.server_name"])
                ]
            )
        case .flaresolverr:
            return detailPairs(
                from: object,
                pairs: [
                    ("Version", ["version"]),
                    ("User Agent", ["userAgent"]),
                    ("Message", ["msg", "message"])
                ]
            )
        default:
            return []
        }
    }

    private func detailPairs(from object: Any, pairs: [(String, [String])]) -> [GenericStatusDetail] {
        pairs.compactMap { label, paths in
            for path in paths {
                if let value = valueForPath(path, in: object) {
                    return GenericStatusDetail(label: label, value: value)
                }
            }
            return nil
        }
    }

    // MARK: - Parsing helpers

    private func parseJellyseerrRequests(from object: Any?) -> [JellyseerrRequestSummary] {
        let rows: [[String: Any]]
        if let dict = object as? [String: Any], let results = dict["results"] as? [[String: Any]] {
            rows = results
        } else if let array = object as? [[String: Any]] {
            rows = array
        } else {
            return []
        }

        return rows.compactMap { row in
            guard let id = intValue(row["id"]) else { return nil }
            let media = row["media"] as? [String: Any]
            let title = stringValue(media?["title"]) ?? stringValue(media?["name"]) ?? stringValue(row["subject"]) ?? "Request #\(id)"
            let status = jellyseerrStatusText(from: row["status"])
            let requester = requesterName(from: row["requestedBy"])
            let requestedAt = stringValue(row["createdAt"]) ?? stringValue(row["updatedAt"])
            return JellyseerrRequestSummary(
                id: id,
                title: title,
                status: status,
                isPending: status.lowercased().contains("pending"),
                requestedBy: requester,
                requestedAt: requestedAt
            )
        }
    }

    private func totalJellyseerrRequests(from object: Any?) -> Int? {
        guard let dict = object as? [String: Any] else { return nil }
        return intValue(dict["totalResults"]) ?? intValue(dict["total"])
    }

    private func jellyseerrStatusText(from raw: Any?) -> String {
        if let number = intValue(raw) {
            switch number {
            case 1: return "Pending"
            case 2: return "Approved"
            case 3: return "Declined"
            case 4: return "Processing"
            case 5: return "Available"
            default: return "Status \(number)"
            }
        }
        if let text = stringValue(raw), !text.isEmpty {
            return text.capitalized
        }
        return "Unknown"
    }

    private func requesterName(from raw: Any?) -> String? {
        guard let dict = raw as? [String: Any] else { return nil }
        return stringValue(dict["displayName"]) ?? stringValue(dict["username"]) ?? stringValue(dict["email"])
    }

    private func parseProwlarrIndexers(from object: Any?) -> [ProwlarrIndexerSummary] {
        guard let rows = object as? [[String: Any]] else { return [] }
        return rows.compactMap { row in
            let fallbackName = stringValue(row["name"]) ?? "Indexer"
            let id = intValue(row["id"]) ?? abs(fallbackName.hashValue % 100_000)
            let name = stringValue(row["name"]) ?? "Indexer \(id)"
            let enabled = boolValue(row["enable"]) ?? boolValue(row["enabled"]) ?? true
            let status = stringValue(row["status"])
                ?? valueForPath("status.status", in: row)
                ?? valueForPath("status.message", in: row)
                ?? (enabled ? "Enabled" : "Disabled")
            return ProwlarrIndexerSummary(id: id, name: name, enabled: enabled, status: status)
        }
    }

    private func parseNameList(from object: Any?) -> [String] {
        guard let rows = object as? [[String: Any]] else { return [] }
        var seen = Set<String>()
        return rows.compactMap { row in
            stringValue(row["name"]) ?? stringValue(row["implementation"]) ?? stringValue(row["syncLevel"])
        }
        .filter { item in
            seen.insert(item).inserted
        }
    }

    private func parseProwlarrHistory(from object: Any?) -> [String] {
        let rows: [[String: Any]]
        if let dict = object as? [String: Any], let records = dict["records"] as? [[String: Any]] {
            rows = records
        } else if let arr = object as? [[String: Any]] {
            rows = arr
        } else {
            return []
        }

        return rows.compactMap { row in
            let title = stringValue(row["sourceTitle"]) ?? stringValue(row["title"]) ?? stringValue(row["eventType"])
            let event = stringValue(row["eventType"]) ?? stringValue(row["type"])
            if let title, let event {
                return "\(event): \(title)"
            }
            return title ?? event
        }
    }

    private func parseBazarrBadges(from object: Any?) -> [GenericStatusDetail] {
        guard let dict = object as? [String: Any] else { return [] }

        let keys: [(String, [String])] = [
            ("Wanted", ["wanted", "data.wanted"]),
            ("Missing", ["missing", "data.missing"]),
            ("Movies", ["movies", "data.movies"]),
            ("Series", ["series", "data.series"]),
            ("Providers", ["providers", "data.providers"])
        ]

        let mapped = keys.compactMap { label, paths in
            for path in paths {
                if let value = valueForPath(path, in: dict) {
                    return GenericStatusDetail(label: label, value: value)
                }
            }
            return nil
        }

        if !mapped.isEmpty {
            return mapped
        }

        return dict.compactMap { key, value in
            guard let numeric = normalizedValue(value), Int(numeric) != nil else { return nil }
            return GenericStatusDetail(label: key.replacingOccurrences(of: "_", with: " ").capitalized, value: numeric)
        }
    }

    private func parseBazarrTasks(from object: Any?) -> [String] {
        let rows: [[String: Any]]
        if let dict = object as? [String: Any], let data = dict["data"] as? [[String: Any]] {
            rows = data
        } else if let arr = object as? [[String: Any]] {
            rows = arr
        } else {
            return []
        }

        return rows.compactMap { row in
            let name = stringValue(row["name"]) ?? stringValue(row["task"]) ?? stringValue(row["job"])
            let isRunning = boolValue(row["running"]) ?? boolValue(row["is_running"]) ?? false
            guard let name else { return nil }
            return isRunning ? "\(name) (running)" : name
        }
    }

    private func parseHealthMessages(from object: Any?) -> [String] {
        let rows: [[String: Any]]
        if let dict = object as? [String: Any], let data = dict["data"] as? [[String: Any]] {
            rows = data
        } else if let arr = object as? [[String: Any]] {
            rows = arr
        } else {
            return []
        }

        return rows.compactMap { row in
            stringValue(row["message"]) ?? stringValue(row["description"]) ?? stringValue(row["type"])
        }
    }

    private func parseFlaresolverrSessions(from object: Any?) -> [String] {
        guard let dict = object as? [String: Any] else { return [] }

        if let sessions = dict["sessions"] as? [String] {
            return sessions
        }

        if let rows = dict["sessions"] as? [[String: Any]] {
            return rows.compactMap { row in
                stringValue(row["id"]) ?? stringValue(row["session"])
            }
        }

        return []
    }

    private struct JellyseerrJobCandidate {
        let id: String
        let name: String?
        let searchableName: String
        let isRunnable: Bool
    }

    private func oldestPendingJellyseerrRequest() async throws -> (id: Int, title: String?) {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        let headers = authHeaders()
        guard let requestsObject = await requestJSONObject(
            path: "/api/v1/request?take=50&skip=0&sort=added&filter=all",
            headers: headers
        ) else {
            throw APIError.custom("Unable to load Jellyseerr requests")
        }

        let requests = parseJellyseerrRequests(from: requestsObject)
        guard let pending = requests.first(where: { $0.isPending }) else {
            throw APIError.custom("No pending Jellyseerr requests found")
        }
        return (pending.id, pending.title)
    }

    private func runJellyseerrJob(keywordCandidates: [String]) async throws -> String {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        let headers = authHeaders()
        guard let jobsObject = await requestJSONObject(path: "/api/v1/settings/jobs", headers: headers) else {
            throw APIError.custom("Unable to load Jellyseerr jobs")
        }

        let jobs = parseJellyseerrJobs(from: jobsObject)
        guard !jobs.isEmpty else {
            throw APIError.custom("No Jellyseerr jobs available")
        }

        let normalizedKeywords = keywordCandidates.map { $0.lowercased() }
        let runnableKeywordJob = jobs.first { candidate in
            normalizedKeywords.contains { keyword in
                !keyword.isEmpty && candidate.searchableName.contains(keyword) && candidate.isRunnable
            }
        }
        let keywordFallbackJob = jobs.first { candidate in
            normalizedKeywords.contains { keyword in
                !keyword.isEmpty && candidate.searchableName.contains(keyword)
            }
        }
        let runnableFallbackJob = jobs.first(where: \.isRunnable)
        let chosenJob = runnableKeywordJob ?? keywordFallbackJob ?? runnableFallbackJob ?? jobs[0]

        try await runJellyseerrJobById(chosenJob.id)
        return chosenJob.name ?? chosenJob.id
    }

    private func parseJellyseerrJobs(from object: Any?) -> [JellyseerrJobCandidate] {
        let rows: [[String: Any]]
        if let dict = object as? [String: Any], let jobs = dict["jobs"] as? [[String: Any]] {
            rows = jobs
        } else if let dict = object as? [String: Any], let jobs = dict["results"] as? [[String: Any]] {
            rows = jobs
        } else if let dict = object as? [String: Any], let jobs = dict["data"] as? [[String: Any]] {
            rows = jobs
        } else if let array = object as? [[String: Any]] {
            rows = array
        } else {
            return []
        }

        return rows.compactMap { row in
            let idValue = row["id"] ?? row["jobId"]
            let id = stringValue(idValue) ?? intValue(idValue).map(String.init)
            guard let id, !id.isEmpty else { return nil }

            let name = stringValue(row["name"]) ?? stringValue(row["type"]) ?? stringValue(row["id"])
            let searchableName = (name ?? id).lowercased()
            let enabled: Bool = {
                if let value = row["enabled"] as? Bool { return value }
                if let value = row["isEnabled"] as? Bool { return value }
                return true
            }()
            let running: Bool = {
                if let value = row["running"] as? Bool { return value }
                if let value = row["isRunning"] as? Bool { return value }
                let status = (stringValue(row["status"]) ?? "").lowercased()
                return status.contains("running") || status.contains("in_progress")
            }()
            return JellyseerrJobCandidate(
                id: id,
                name: name,
                searchableName: searchableName,
                isRunnable: enabled && !running
            )
        }
    }

    private func runJellyseerrJobById(_ jobId: String) async throws {
        try await engine.requestVoid(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/settings/jobs/\(jobId)/run",
            method: "POST",
            headers: authHeaders()
        )
    }

    // MARK: - Raw request helpers

    private func requestJSONObject(
        path: String,
        headers: [String: String],
        method: String = "GET",
        body: Data? = nil
    ) async -> Any? {
        do {
            let data = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                method: method,
                headers: headers,
                body: body
            )
            return try JSONSerialization.jsonObject(with: data)
        } catch {
            return nil
        }
    }

    private func requestString(path: String, headers: [String: String]) async -> String? {
        do {
            let value = try await engine.requestString(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                headers: headers
            )
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        } catch {
            return nil
        }
    }

    private func authHeaders() -> [String: String] {
        guard !apiKey.isEmpty else { return ["Accept": "application/json"] }
        return [
            "Accept": "application/json",
            "X-Api-Key": apiKey,
            "Authorization": "Bearer \(apiKey)"
        ]
    }

    private func jsonActionHeaders() -> [String: String] {
        authHeaders().merging(["Content-Type": "application/json"]) { _, new in new }
    }

    private func candidatePaths() -> [String] {
        switch serviceType {
        case .jellyseerr:
            return ["/api/v1/status", "/api/v1/settings/public", "/api/v1/request?take=1"]
        case .prowlarr:
            return ["/api/v1/system/status", "/api/v1/health", "/api/v1/indexer?page=1&pageSize=1", "/api/v1/applications?page=1&pageSize=1", "/api/v1/history?page=1&pageSize=1"]
        case .bazarr:
            return ["/api/system/status", "/api/badges", "/api/system/health", "/api/system/tasks"]
        case .gluetun:
            return ["/v1/openvpn/status", "/v1/publicip/ip", "/v1/openvpn/portforwarded", "/"]
        case .flaresolverr:
            return ["/health", "/v1", "/"]
        default:
            return ["/api", "/"]
        }
    }

    private func runProwlarrCommand(candidates: [String]) async throws {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        var lastError: Error?
        for command in candidates {
            do {
                let body = try Self.jsonData(["name": command])
                try await engine.requestVoid(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: "/api/v1/command",
                    method: "POST",
                    headers: jsonActionHeaders(),
                    body: body
                )
                return
            } catch {
                lastError = error
            }
        }
        throw lastError ?? APIError.custom("Prowlarr command failed")
    }

    private func searchJellyseerrContent(query: String, limit: Int) async throws -> [GenericLookupResult] {
        let encoded = Self.encodeQuery(query)
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/search?query=\(encoded)&page=1",
            headers: authHeaders()
        )
        guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let rows = object["results"] as? [[String: Any]] else {
            return []
        }

        return rows.prefix(max(1, limit)).compactMap { row in
            let title = stringValue(row["title"]) ?? stringValue(row["name"])
            guard let title else { return nil }
            let mediaType = (stringValue(row["mediaType"]) ?? stringValue(row["media_type"]))?.uppercased()
            let year = (stringValue(row["releaseDate"]) ?? stringValue(row["firstAirDate"]))?.prefix(4).description
            let subtitle = [mediaType, year].compactMap { $0 }.joined(separator: " • ").nilIfEmpty

            let mediaInfo = row["mediaInfo"] as? [String: Any]
            let status = jellyseerrStatusText(from: mediaInfo?["status"])
            let supporting = stringValue(mediaInfo?["status4k"]) ?? stringValue(mediaInfo?["mediaAddedAt"])
            let id = stringValue(row["id"]) ?? title
            let mediaId = intValue(row["id"])
            let mediaTypeRaw = (stringValue(row["mediaType"]) ?? stringValue(row["media_type"]))?.lowercased()
            let requestMediaType: String?
            switch mediaTypeRaw {
            case "movie":
                requestMediaType = "movie"
            case "tv", "show", "series":
                requestMediaType = "tv"
            default:
                requestMediaType = nil
            }
            let posterPath = stringValue(row["posterPath"]) ?? stringValue(row["poster_path"])
            let posterURL: String?
            if let posterPath {
                let normalized = posterPath.hasPrefix("/") ? posterPath : "/\(posterPath)"
                posterURL = "https://image.tmdb.org/t/p/w500\(normalized)"
            } else {
                posterURL = nil
            }
            let detailsURL: String?
            if let mediaType = requestMediaType, let mediaId {
                detailsURL = "https://www.themoviedb.org/\(mediaType)/\(mediaId)"
            } else {
                detailsURL = nil
            }
            let language = stringValue(row["originalLanguage"])?.uppercased()
                ?? stringValue(row["original_language"])?.uppercased()
            let voteAverage: String? = {
                if let number = row["voteAverage"] as? NSNumber {
                    return String(format: "%.1f", number.doubleValue)
                }
                if let number = row["vote_average"] as? NSNumber {
                    return String(format: "%.1f", number.doubleValue)
                }
                return nil
            }()
            let voteCount = intValue(row["voteCount"]) ?? intValue(row["vote_count"])
            let overview = stringValue(row["overview"])
            let details = compactDetails([
                ("Media", mediaType),
                ("Year", year),
                ("TMDB", mediaId.map(String.init)),
                ("Language", language),
                ("Rating", voteAverage),
                ("Votes", voteCount.map(String.init)),
                ("Overview", overview)
            ])

            return GenericLookupResult(
                id: id,
                title: title,
                subtitle: subtitle,
                supporting: supporting,
                status: status == "Unknown" ? nil : status,
                posterURL: posterURL,
                detailsURL: detailsURL,
                details: details,
                requestMediaType: requestMediaType,
                requestMediaId: mediaId
            )
        }
    }

    private func searchProwlarrContent(query: String, limit: Int) async throws -> [GenericLookupResult] {
        let encoded = Self.encodeQuery(query)
        let data = try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/v1/search?query=\(encoded)&type=search&limit=\(max(1, limit))&offset=0",
            headers: authHeaders()
        )
        guard let rows = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            return []
        }

        return rows.prefix(max(1, limit)).compactMap { row in
            guard let title = stringValue(row["title"]) else { return nil }
            let indexer = stringValue(row["indexer"])
            let protocolType = stringValue(row["protocol"])?.uppercased()
            let subtitle = [indexer, protocolType].compactMap { $0 }.joined(separator: " • ").nilIfEmpty

            let size = intValue(row["size"]).map { Formatters.formatBytes(Double($0)) }
            let seeders = intValue(row["seeders"]).map(String.init)
            let leechers = intValue(row["leechers"]).map(String.init)
            let peers = (seeders != nil || leechers != nil) ? "S:\(seeders ?? "-") L:\(leechers ?? "-")" : nil
            let supporting = [size, peers].compactMap { $0 }.joined(separator: " • ").nilIfEmpty

            let ageHours = intValue(row["ageHours"])
            let ageDays = intValue(row["age"])
            let status = ageHours.map { "\($0)h" } ?? ageDays.map { "\($0)d" }
            let id = stringValue(row["guid"]) ?? stringValue(row["id"]) ?? title
            let detailsURL = toAbsoluteURL(stringValue(row["guid"]))
                ?? toAbsoluteURL(stringValue(row["infoUrl"]))
                ?? toAbsoluteURL(stringValue(row["comments"]))
            let posterURL = extractProwlarrPosterURL(from: row)
            let grabs = intValue(row["grabs"])
            let category = stringValue(row["categoryDesc"])
                ?? stringValue(row["category"])
            let details = compactDetails([
                ("Indexer", indexer),
                ("Protocol", protocolType),
                ("Size", size),
                ("Seeders", seeders),
                ("Leechers", leechers),
                ("Age", status),
                ("Grabs", grabs.map(String.init)),
                ("Category", category),
                ("Info URL", detailsURL)
            ])

            return GenericLookupResult(
                id: id,
                title: title,
                subtitle: subtitle,
                supporting: supporting,
                status: status,
                posterURL: posterURL,
                detailsURL: detailsURL,
                details: details,
                requestMediaType: nil,
                requestMediaId: nil
            )
        }
    }

    private func valueForPath(_ path: String, in object: Any) -> String? {
        let parts = path.split(separator: ".").map(String.init)
        var current: Any? = object

        for part in parts {
            guard let dict = current as? [String: Any] else { return nil }
            current = dict[part]
        }
        return normalizedValue(current)
    }

    private func normalizedValue(_ value: Any?) -> String? {
        guard let value else { return nil }
        switch value {
        case let text as String:
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        case let number as NSNumber:
            if CFGetTypeID(number) == CFBooleanGetTypeID() {
                return number.boolValue ? "Yes" : "No"
            }
            return number.stringValue
        case let array as [Any]:
            return array.isEmpty ? nil : "\(array.count)"
        default:
            return nil
        }
    }

    private func stringValue(_ value: Any?) -> String? {
        guard let value else { return nil }
        if let text = value as? String {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        return nil
    }

    private func intValue(_ value: Any?) -> Int? {
        switch value {
        case let number as NSNumber:
            return number.intValue
        case let string as String:
            return Int(string)
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
            if normalized == "true" || normalized == "yes" || normalized == "1" { return true }
            if normalized == "false" || normalized == "no" || normalized == "0" { return false }
            return nil
        default:
            return nil
        }
    }

    private func toAbsoluteURL(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty else { return nil }
        if raw.hasPrefix("magnet:") {
            return raw
        }
        return resolvedServiceArtworkURL(
            raw,
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            apiKey: serviceType == .prowlarr ? apiKey : nil
        )
    }

    private func extractProwlarrPosterURL(from row: [String: Any]) -> String? {
        let directCandidates: [String?] = [
            stringValue(row["poster"]),
            stringValue(row["posterUrl"]),
            stringValue(row["posterURL"]),
            stringValue(row["image"]),
            stringValue(row["imageUrl"]),
            stringValue(row["cover"]),
            stringValue(row["thumbnail"])
        ]
        for candidate in directCandidates {
            if let resolved = toAbsoluteURL(candidate) {
                return resolved
            }
        }

        if let images = row["images"] as? [[String: Any]] {
            for image in images {
                let candidate = stringValue(image["remoteUrl"])
                    ?? stringValue(image["url"])
                    ?? stringValue(image["src"])
                    ?? stringValue(image["link"])
                if let resolved = toAbsoluteURL(candidate) {
                    return resolved
                }
            }
        }
        if let images = row["images"] as? [Any] {
            for image in images {
                if let candidate = stringValue(image),
                   let resolved = toAbsoluteURL(candidate) {
                    return resolved
                }
            }
        }

        return nil
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

    private static func cleanURL(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func encodeQuery(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
    }

    private static func jsonData(_ payload: [String: Any]) throws -> Data {
        try JSONSerialization.data(withJSONObject: payload, options: [])
    }
}

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
