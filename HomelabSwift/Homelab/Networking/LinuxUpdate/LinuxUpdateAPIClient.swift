import Foundation

struct LinuxUpdateActionOutcome: Sendable {
    let success: Bool
    let message: String
}

actor LinuxUpdateAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiToken: String = ""

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .linuxUpdate, instanceId: instanceId)
    }

    func configure(url: String, apiToken: String, fallbackUrl: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiToken = Self.cleanToken(apiToken)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiToken.isEmpty else { return false }
        let primary = await engine.pingURL("\(baseURL)/api/dashboard/stats", extraHeaders: authHeaders())
        if primary { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL("\(fallbackURL)/api/dashboard/stats", extraHeaders: authHeaders())
    }

    func authenticate(url: String, apiToken: String, fallbackUrl: String? = nil) async throws {
        let cleanedURL = Self.cleanURL(url)
        let cleanedFallback = Self.cleanURL(fallbackUrl ?? "")
        let cleanedToken = Self.cleanToken(apiToken)
        guard !cleanedURL.isEmpty, !cleanedToken.isEmpty else {
            throw APIError.notConfigured
        }

        _ = try await engine.requestData(
            baseURL: cleanedURL,
            fallbackURL: cleanedFallback,
            path: "/api/dashboard/stats",
            headers: authHeaders(for: cleanedToken)
        )
    }

    func getDashboardStats() async throws -> LinuxUpdateDashboardStats {
        let response: LinuxUpdateDashboardStatsResponse = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/dashboard/stats",
            headers: authHeaders()
        )
        return response.stats
    }

    func getDashboardSystems() async throws -> [LinuxUpdateSystem] {
        let response: LinuxUpdateDashboardSystemsResponse = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/dashboard/systems",
            headers: authHeaders()
        )

        return response.systems.sorted { lhs, rhs in
            if lhs.sortOrder != rhs.sortOrder {
                return lhs.sortOrder < rhs.sortOrder
            }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
    }

    func getSystemDetail(systemId: Int) async throws -> LinuxUpdateSystemDetailResponse {
        let response: LinuxUpdateSystemDetailResponse = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/systems/\(systemId)",
            headers: authHeaders()
        )

        let sortedUpdates = response.updates.sorted {
            if $0.isSecurityFlag != $1.isSecurityFlag { return $0.isSecurityFlag && !$1.isSecurityFlag }
            if $0.isKeptBackFlag != $1.isKeptBackFlag { return $0.isKeptBackFlag && !$1.isKeptBackFlag }
            return $0.packageName.localizedCaseInsensitiveCompare($1.packageName) == .orderedAscending
        }

        let sortedHidden = response.hiddenUpdates.sorted {
            if $0.isSecurityFlag != $1.isSecurityFlag { return $0.isSecurityFlag && !$1.isSecurityFlag }
            return $0.packageName.localizedCaseInsensitiveCompare($1.packageName) == .orderedAscending
        }

        let sortedHistory = response.history.sorted { lhs, rhs in
            (lhs.startedAt ?? "") > (rhs.startedAt ?? "")
        }

        return LinuxUpdateSystemDetailResponse(
            system: response.system,
            updates: sortedUpdates,
            hiddenUpdates: sortedHidden,
            history: sortedHistory
        )
    }

    func runCheck(systemId: Int) async throws -> LinuxUpdateActionOutcome {
        try await runAsyncAction(actionLabel: "Check") {
            try await self.startCheck(systemId: systemId)
        }
    }

    func runCheckAll() async throws -> LinuxUpdateActionOutcome {
        try await runAsyncAction(actionLabel: "Check all systems") {
            try await self.startCheckAll()
        }
    }

    func runUpgradeAll(systemId: Int) async throws -> LinuxUpdateActionOutcome {
        try await runAsyncAction(actionLabel: "Upgrade") {
            try await self.startUpgradeAll(systemId: systemId)
        }
    }

    func runFullUpgrade(systemId: Int) async throws -> LinuxUpdateActionOutcome {
        try await runAsyncAction(actionLabel: "Full upgrade") {
            try await self.startFullUpgrade(systemId: systemId)
        }
    }

    func runRefreshCache() async throws -> LinuxUpdateActionOutcome {
        try await runAsyncAction(actionLabel: "Refresh cache") {
            try await self.startRefreshCache()
        }
    }

    func runUpgradePackage(systemId: Int, packageName: String) async throws -> LinuxUpdateActionOutcome {
        let normalized = packageName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else {
            throw APIError.custom("Package name is required")
        }

        do {
            return try await runUpgradePackages(systemId: systemId, packageNames: [normalized])
        } catch let error as APIError {
            if case .httpError(let statusCode, _) = error, [400, 404, 405].contains(statusCode) {
                return try await runAsyncAction(actionLabel: "Package upgrade") {
                    try await self.startUpgradePackageAlias(systemId: systemId, packageName: normalized)
                }
            }
            throw error
        }
    }

    func runUpgradePackages(systemId: Int, packageNames: [String]) async throws -> LinuxUpdateActionOutcome {
        let normalized = packageNames
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }

        guard !normalized.isEmpty else {
            throw APIError.custom("At least one package is required")
        }

        let body = try JSONSerialization.data(withJSONObject: [
            "packageNames": normalized,
            "packages": normalized
        ])
        let actionLabel = normalized.count == 1 ? "Package upgrade" : "Package upgrades"
        return try await runAsyncAction(actionLabel: actionLabel) {
            try await self.startUpgradePackages(systemId: systemId, body: body)
        }
    }

    func runReboot(systemId: Int) async throws -> LinuxUpdateActionOutcome {
        let response: LinuxUpdateRebootResponse = try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/systems/\(systemId)/reboot",
            method: "POST",
            headers: authHeaders()
        )

        let fallback = response.success ? "Reboot command sent" : "Reboot failed"
        return LinuxUpdateActionOutcome(
            success: response.success,
            message: Self.firstNonEmpty(response.message, response.error, fallback)
        )
    }

    private func startCheck(systemId: Int) async throws -> LinuxUpdateJobStartResponse {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/systems/\(systemId)/check",
            method: "POST",
            headers: authHeaders()
        )
    }

    private func startCheckAll() async throws -> LinuxUpdateJobStartResponse {
        try await startDashboardAction(
            candidatePaths: [
                "/api/systems/check-all",
                "/api/updates/check-all",
                "/api/check-all"
            ],
            fallbackStatus: "checking_all"
        )
    }

    private func startUpgradeAll(systemId: Int) async throws -> LinuxUpdateJobStartResponse {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/systems/\(systemId)/upgrade",
            method: "POST",
            headers: authHeaders()
        )
    }

    private func startFullUpgrade(systemId: Int) async throws -> LinuxUpdateJobStartResponse {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/systems/\(systemId)/full-upgrade",
            method: "POST",
            headers: authHeaders()
        )
    }

    private func startRefreshCache() async throws -> LinuxUpdateJobStartResponse {
        try await startDashboardAction(
            candidatePaths: [
                "/api/cache/refresh",
                "/api/updates/cache/refresh",
                "/api/refresh-cache"
            ],
            fallbackStatus: "refreshing"
        )
    }

    private func startDashboardAction(
        candidatePaths: [String],
        fallbackStatus: String
    ) async throws -> LinuxUpdateJobStartResponse {
        var lastError: Error = APIError.custom("Action failed to start")

        for path in candidatePaths {
            do {
                let data = try await engine.requestData(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: path,
                    method: "POST",
                    headers: authHeaders()
                )

                if data.isEmpty {
                    return LinuxUpdateJobStartResponse(status: fallbackStatus)
                }

                if let decoded = try? JSONDecoder().decode(LinuxUpdateJobStartResponse.self, from: data) {
                    if decoded.status.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
                        decoded.error?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty != false {
                        return LinuxUpdateJobStartResponse(
                            status: fallbackStatus,
                            job: decoded.job,
                            jobAlias: decoded.jobAlias,
                            id: decoded.id,
                            jobId: decoded.jobId,
                            error: decoded.error,
                            message: decoded.message
                        )
                    }
                    return decoded
                }

                let rawText = String(data: data, encoding: .utf8)?
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                return LinuxUpdateJobStartResponse(
                    status: fallbackStatus,
                    message: rawText.isEmpty ? nil : rawText
                )
            } catch let error as APIError {
                if case .httpError(let statusCode, _) = error, [404, 405].contains(statusCode) {
                    lastError = error
                    continue
                }
                throw error
            } catch {
                lastError = error
            }
        }

        throw lastError
    }

    private func startUpgradePackages(systemId: Int, body: Data) async throws -> LinuxUpdateJobStartResponse {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/systems/\(systemId)/upgrade-packages",
            method: "POST",
            headers: authHeaders(),
            body: body
        )
    }

    private func startUpgradePackageAlias(systemId: Int, packageName: String) async throws -> LinuxUpdateJobStartResponse {
        let encodedPackage = Self.encodePathComponent(packageName)
        return try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/systems/\(systemId)/upgrade/\(encodedPackage)",
            method: "POST",
            headers: authHeaders()
        )
    }

    private func getJobStatus(jobId: String) async throws -> LinuxUpdateJobStatusResponse {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/api/jobs/\(jobId)",
            headers: authHeaders()
        )
    }

    private func runAsyncAction(
        actionLabel: String,
        start: @escaping () async throws -> LinuxUpdateJobStartResponse
    ) async throws -> LinuxUpdateActionOutcome {
        let started = try await start()
        if let error = started.error?.trimmingCharacters(in: .whitespacesAndNewlines), !error.isEmpty {
            throw APIError.custom(error)
        }

        if let jobId = started.resolvedJobId {
            let terminal = try await pollJob(jobId: jobId, actionLabel: actionLabel)
            return interpretJobResult(terminal, actionLabel: actionLabel)
        }

        if started.isAcceptedWithoutJobId {
            let fallback = started.startFallbackMessage(actionLabel: actionLabel)
            return LinuxUpdateActionOutcome(
                success: true,
                message: Self.firstNonEmpty(started.message, fallback)
            )
        }

        throw APIError.custom(Self.firstNonEmpty(started.message, "\(actionLabel) failed to start"))
    }

    fileprivate static let acceptedStartStatuses: Set<String> = [
        "accepted",
        "checking_all",
        "done",
        "ok",
        "queued",
        "refreshing",
        "running",
        "started",
        "success"
    ]

    fileprivate static func normalizedStatus(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    fileprivate static func fallbackStartMessage(status: String, actionLabel: String) -> String {
        switch status {
        case "checking_all":
            return "Check all systems started"
        case "refreshing":
            return "Refresh cache started"
        default:
            return "\(actionLabel) started"
        }
    }

    private func pollJob(jobId: String, actionLabel: String) async throws -> LinuxUpdateJobStatusResponse {
        let deadline = Date().addingTimeInterval(180)

        while Date() < deadline {
            let response = try await getJobStatus(jobId: jobId)
            let status = response.status.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

            switch status {
            case "running":
                try await Task.sleep(nanoseconds: 1_200_000_000)
            case "done", "failed":
                return response
            default:
                if let error = response.error, !error.isEmpty {
                    throw APIError.custom(error)
                }
                try await Task.sleep(nanoseconds: 1_200_000_000)
            }
        }

        throw APIError.custom("\(actionLabel) timed out")
    }

    private func interpretJobResult(_ response: LinuxUpdateJobStatusResponse, actionLabel: String) -> LinuxUpdateActionOutcome {
        let status = response.status.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let nestedStatus = response.result?.status?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""

        let success = status != "failed" && nestedStatus != "failed"
        let packageName = response.result?.packageName

        let fallback: String
        if success {
            if let packageName, !packageName.isEmpty {
                fallback = "\(actionLabel) completed for \(packageName)"
            } else {
                fallback = "\(actionLabel) completed"
            }
        } else {
            fallback = "\(actionLabel) failed"
        }

        let message = Self.firstNonEmpty(
            response.result?.error,
            response.error,
            response.result?.output,
            response.result?.message,
            fallback
        )

        return LinuxUpdateActionOutcome(success: success, message: message)
    }

    private func authHeaders(for token: String? = nil) -> [String: String] {
        [
            "Authorization": "Bearer \(token ?? apiToken)",
            "Content-Type": "application/json"
        ]
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func cleanToken(_ raw: String) -> String {
        let token = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !token.isEmpty else { return "" }

        if token.lowercased().hasPrefix("bearer ") {
            return String(token.dropFirst(7)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return token
    }

    private static func encodePathComponent(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? value
    }

    private static func firstNonEmpty(_ values: String?...) -> String {
        for value in values {
            let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !trimmed.isEmpty {
                return String(trimmed.prefix(240))
            }
        }
        return ""
    }
}

private extension LinuxUpdateJobStartResponse {
    var isAcceptedWithoutJobId: Bool {
        let status = LinuxUpdateAPIClient.normalizedStatus(self.status)
        if LinuxUpdateAPIClient.acceptedStartStatuses.contains(status) {
            return true
        }
        if !status.isEmpty && status != "failed" && status != "error" {
            return true
        }
        let hasMessage = !(message?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
        let hasError = !(error?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
        return hasMessage && !hasError
    }

    func startFallbackMessage(actionLabel: String) -> String {
        LinuxUpdateAPIClient.fallbackStartMessage(
            status: LinuxUpdateAPIClient.normalizedStatus(status),
            actionLabel: actionLabel
        )
    }
}

// MARK: - Dockhand

struct DockhandQuickOverview: Sendable {
    let runningContainers: Int
    let totalContainers: Int
}

struct DockhandEnvironmentInfo: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let isDefault: Bool
}

struct DockhandContainerInfo: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let image: String
    let state: String
    let status: String
    let portsSummary: String
    let health: String?
    let environmentId: String?

    var isRunning: Bool {
        state.lowercased() == "running" || status.lowercased().contains("up")
    }

    var isIssue: Bool {
        let value = "\(state) \(status) \(health ?? "")".lowercased()
        return value.contains("dead") || value.contains("error") || value.contains("exited") || value.contains("unhealthy")
    }
}

struct DockhandStackInfo: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let status: String
    let services: Int
    let source: String?
    let environmentId: String?
}

struct DockhandResourceInfo: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let details: String?
}

struct DockhandActivityInfo: Identifiable, Hashable, Sendable {
    let id: String
    let action: String
    let target: String
    let status: String
    let createdAt: String?
}

struct DockhandScheduleInfo: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let enabled: Bool
    let schedule: String?
    let environmentId: String?
    let nextRun: String?
    let lastRun: String?
}

struct DockhandStatsInfo: Sendable {
    let totalContainers: Int
    let runningContainers: Int
    let stoppedContainers: Int
    let issueContainers: Int
    let stacks: Int
    let images: Int
    let volumes: Int
    let networks: Int
}

struct DockhandDashboardData: Sendable {
    let stats: DockhandStatsInfo
    let environments: [DockhandEnvironmentInfo]
    let containers: [DockhandContainerInfo]
    let stacks: [DockhandStackInfo]
    let images: [DockhandResourceInfo]
    let volumes: [DockhandResourceInfo]
    let networks: [DockhandResourceInfo]
    let activity: [DockhandActivityInfo]
    let schedules: [DockhandScheduleInfo]
}

struct DockhandContainerDetailData: Sendable {
    let container: DockhandContainerInfo
    let details: [(String, String)]
    let logs: String
}

struct DockhandStackDetailData: Sendable {
    let stack: DockhandStackInfo
    let details: [(String, String)]
    let compose: String
}

struct DockhandScheduleDetailData: Sendable {
    let schedule: DockhandScheduleInfo
    let details: [(String, String)]
}

struct DockhandActionOutcome: Sendable {
    let success: Bool
    let message: String
}

enum DockhandContainerActionKind: Sendable {
    case start
    case stop
    case restart
}

enum DockhandStackActionKind: Sendable {
    case start
    case stop
    case restart
}

actor DockhandAPIClient {
    private let engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var sessionCookie: String = ""
    private var username: String = ""
    private var storedPassword: String = ""

    private static let authSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 8
        config.timeoutIntervalForResource = 8
        config.httpShouldSetCookies = false
        config.httpCookieAcceptPolicy = .never
        return URLSession(configuration: config, delegate: BaseNetworkEngine.insecureDelegateForPortainerAuth, delegateQueue: nil)
    }()

    init(instanceId: UUID) {
        self.engine = BaseNetworkEngine(serviceType: .dockhand, instanceId: instanceId)
    }

    func configure(url: String, sessionCookie: String, fallbackUrl: String? = nil, username: String? = nil, password: String? = nil) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.sessionCookie = sessionCookie.trimmingCharacters(in: .whitespacesAndNewlines)
        if let username, !username.isEmpty {
            self.username = username
        }
        if let password, !password.isEmpty {
            self.storedPassword = password
        }
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        let headers = authHeaders()
        if await engine.pingURL("\(baseURL)/api/dashboard/stats", extraHeaders: headers) { return true }
        guard !fallbackURL.isEmpty else { return false }
        return await engine.pingURL("\(fallbackURL)/api/dashboard/stats", extraHeaders: headers)
    }

    func authenticate(
        url: String,
        username: String,
        password: String,
        mfaCode: String,
        fallbackUrl: String? = nil
    ) async throws -> String {
        let cleanURL = Self.cleanURL(url)
        let cleanFallback = Self.cleanURL(fallbackUrl ?? "")

        do {
            return try await authenticateAgainst(baseURL: cleanURL, username: username, password: password, mfaCode: mfaCode)
        } catch {
            guard !cleanFallback.isEmpty, cleanFallback != cleanURL else { throw error }
            return try await authenticateAgainst(baseURL: cleanFallback, username: username, password: password, mfaCode: mfaCode)
        }
    }

    func getQuickOverview(environmentId: String?) async throws -> DockhandQuickOverview {
        let environments = (try? await getEnvironments()) ?? []
        let requestedEnvironmentId = Self.normalizeEnvironmentId(environmentId)
        let scopes = resolveScopes(
            requestedEnvironmentId: requestedEnvironmentId,
            environments: environments
        )
        let fallbackScopes = requestedEnvironmentId == nil
            ? environments.map { DockhandScope(environmentId: $0.id) }
            : []
        let containers = try await getContainers(for: scopes, fallbackScopes: fallbackScopes)
        return DockhandQuickOverview(
            runningContainers: containers.filter { $0.isRunning }.count,
            totalContainers: containers.count
        )
    }

    func getDashboard(environmentId: String?) async throws -> DockhandDashboardData {
        let environments = (try? await getEnvironments()) ?? []
        let requestedEnvironmentId = Self.normalizeEnvironmentId(environmentId)
        let scopes = resolveScopes(
            requestedEnvironmentId: requestedEnvironmentId,
            environments: environments
        )
        let fallbackScopes = requestedEnvironmentId == nil
            ? environments.map { DockhandScope(environmentId: $0.id) }
            : []

        let containers = try await getContainers(for: scopes, fallbackScopes: fallbackScopes)
        let stacks = try await getStacks(for: scopes, fallbackScopes: fallbackScopes)
        let images = (try? await getResources(for: scopes, fallbackScopes: fallbackScopes, resourcePath: "/api/images", kind: "image")) ?? []
        let volumes = (try? await getResources(for: scopes, fallbackScopes: fallbackScopes, resourcePath: "/api/volumes", kind: "volume")) ?? []
        let networks = (try? await getResources(for: scopes, fallbackScopes: fallbackScopes, resourcePath: "/api/networks", kind: "network")) ?? []
        let activity = (try? await getActivity(for: scopes, fallbackScopes: fallbackScopes)) ?? []
        let schedules = (try? await getSchedules(for: scopes, fallbackScopes: fallbackScopes)) ?? []

        let stats: DockhandStatsInfo
        if scopes.count == 1, let only = scopes.first?.environmentId {
            stats = (try? await getStats(environmentId: only, containers: containers, stacks: stacks, images: images, volumes: volumes, networks: networks))
                ?? synthesizeStats(containers: containers, stacks: stacks, images: images, volumes: volumes, networks: networks)
        } else {
            stats = synthesizeStats(containers: containers, stacks: stacks, images: images, volumes: volumes, networks: networks)
        }

        return DockhandDashboardData(
            stats: stats,
            environments: environments,
            containers: containers,
            stacks: stacks,
            images: images,
            volumes: volumes,
            networks: networks,
            activity: activity,
            schedules: schedules
        )
    }

    func getContainerDetail(id: String, environmentId: String?) async throws -> DockhandContainerDetailData {
        let detailData = try await requestData(path: envPath("/api/containers/\(id)", env: environmentId))
        let rootObject = DockhandJSON.object(from: detailData) ?? [:]
        let detailObject = normalizePrimaryObject(rootObject, preferredKeys: ["container", "item", "data"])
        let container = parseContainer(detailObject, environmentId: environmentId) ?? parseContainer(rootObject, environmentId: environmentId) ?? DockhandContainerInfo(
            id: id,
            name: id,
            image: "-",
            state: "unknown",
            status: "unknown",
            portsSummary: "-",
            health: nil,
            environmentId: environmentId
        )

        let detailPairs = compactDetails(
            detailObject,
            maxItems: 14,
            excludedKeys: [
                "id", "Id", "config", "Config", "hostConfig", "HostConfig", "networkSettings",
                "NetworkSettings", "graphDriver", "GraphDriver", "mounts", "Mounts", "labels", "Labels",
                "args", "Args", "logPath", "LogPath"
            ]
        )

        let logsData = try? await requestData(path: envPath("/api/containers/\(id)/logs", env: environmentId, extra: ["tail": "140"]))
        let logs = parseContainerLogs(logsData)

        return DockhandContainerDetailData(
            container: container,
            details: detailPairs,
            logs: logs
        )
    }

    func getStackDetail(name: String, environmentId: String?) async throws -> DockhandStackDetailData {
        let encodedName = name.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? name
        let detailObject = (try? await findStackObject(name: name, environmentId: environmentId)) ?? [:]

        let stack = parseStack(detailObject, fallbackName: name, environmentId: environmentId)
        let details = compactDetails(
            detailObject,
            maxItems: 14,
            excludedKeys: ["compose", "dockerCompose", "content", "yaml", "stackFile"]
        )

        let compose = try await fetchStackCompose(encodedName: encodedName, stackName: name, environmentId: environmentId)

        return DockhandStackDetailData(
            stack: stack,
            details: details,
            compose: compose
        )
    }

    func getScheduleDetail(id: String, environmentId: String?) async throws -> DockhandScheduleDetailData {
        let object = (try? await findScheduleObject(id: id, environmentId: environmentId)) ?? [:]

        let schedule = parseSchedule(
            object,
            fallbackId: id,
            fallbackName: "Schedule",
            environmentId: environmentId
        )
        return DockhandScheduleDetailData(
            schedule: schedule,
            details: compactDetails(object)
        )
    }

    func updateStackCompose(name: String, compose: String, environmentId: String?) async throws -> DockhandActionOutcome {
        let encodedName = name.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? name
        let candidatePaths = [
            "/api/stacks/\(encodedName)/compose",
            "/api/stacks/\(encodedName)/docker-compose",
            "/api/stacks/\(encodedName)/file",
            "/api/stacks/\(encodedName)/update"
        ]
        let payloads = composePayloads(compose)

        var lastError: Error = APIError.custom("Compose update failed")
        var hasNonCompatibilityResponse = false
        for path in candidatePaths {
            for method in ["PUT", "POST"] {
                for payload in payloads {
                    do {
                        let data = try await requestData(
                            path: envPath(path, env: environmentId),
                            method: method,
                            includeSynchronousAccept: true,
                            body: payload
                        )
                        let outcome = try await resolveActionResult(data: data, label: "Compose update")
                        if outcome.success {
                            return outcome
                        }
                        hasNonCompatibilityResponse = true
                        lastError = APIError.custom(outcome.message)
                    } catch let error as APIError {
                        if case .httpError(let statusCode, _) = error, [404, 405].contains(statusCode) {
                            lastError = error
                            continue
                        }
                        hasNonCompatibilityResponse = true
                        lastError = error
                    } catch {
                        hasNonCompatibilityResponse = true
                        lastError = error
                    }
                }
            }
        }

        if !hasNonCompatibilityResponse {
            throw APIError.custom("Compose editing is not supported by this Dockhand API version")
        }
        throw lastError
    }

    func runContainerAction(id: String, action: DockhandContainerActionKind, environmentId: String?) async throws -> DockhandActionOutcome {
        let path: String
        let label: String
        switch action {
        case .start:
            path = "/api/containers/\(id)/start"
            label = "Start"
        case .stop:
            path = "/api/containers/\(id)/stop"
            label = "Stop"
        case .restart:
            path = "/api/containers/\(id)/restart"
            label = "Restart"
        }

        let data = try await requestData(path: envPath(path, env: environmentId), method: "POST", includeSynchronousAccept: true)
        return try await resolveActionResult(data: data, label: label)
    }

    func runStackAction(name: String, action: DockhandStackActionKind, environmentId: String?) async throws -> DockhandActionOutcome {
        let encodedName = name.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? name
        let path: String
        let label: String

        switch action {
        case .start:
            path = "/api/stacks/\(encodedName)/start"
            label = "Stack start"
        case .stop:
            path = "/api/stacks/\(encodedName)/stop"
            label = "Stack stop"
        case .restart:
            path = "/api/stacks/\(encodedName)/restart"
            label = "Stack restart"
        }

        let data = try await requestData(path: envPath(path, env: environmentId), method: "POST", includeSynchronousAccept: true)
        return try await resolveActionResult(data: data, label: label)
    }

    private func authenticateAgainst(baseURL: String, username: String, password: String, mfaCode: String) async throws -> String {
        let cleanUser = username.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanPassword = password

        if cleanUser.isEmpty && cleanPassword.isEmpty {
            if try await canAccessDashboard(baseURL: baseURL, cookie: nil) {
                return ""
            }
            throw APIError.custom("Username and password are required")
        }

        let paths = ["/api/auth/login", "/api/auth/local/login", "/api/login"]
        let payloads = Self.loginPayloads(username: cleanUser, password: cleanPassword, mfaCode: mfaCode)

        var mfaRequired = false
        var localLoginDisabled = false
        var lastBody = ""

        for path in paths {
            for payload in payloads {
                let (data, response) = try await performAuthRequest(urlString: baseURL + path, body: payload)
                let body = String(data: data, encoding: .utf8) ?? ""
                if !body.isEmpty { lastBody = body }
                let lowered = body.lowercased()

                if response.statusCode == 403, lowered.contains("local login") {
                    localLoginDisabled = true
                }
                if lowered.contains("mfa") || lowered.contains("2fa") || lowered.contains("totp") || lowered.contains("backup code") {
                    mfaRequired = true
                }

                if (200...299).contains(response.statusCode) {
                    let cookie = extractCookie(from: response)
                    if !cookie.isEmpty {
                        if try await canAccessDashboard(baseURL: baseURL, cookie: cookie) {
                            return cookie
                        }
                    } else if try await canAccessDashboard(baseURL: baseURL, cookie: nil) {
                        return ""
                    }
                }
            }
        }

        if localLoginDisabled {
            throw APIError.custom("Local login is disabled on this Dockhand instance")
        }

        if mfaRequired && mfaCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            throw APIError.custom("Two-factor authentication code required")
        }

        let message = lastBody.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.first { !$0.isEmpty }
        throw APIError.custom(message.map { String($0) } ?? "Dockhand authentication failed")
    }

    private func performAuthRequest(urlString: String, body: Data) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: urlString) else { throw APIError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 8
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        let (data, response) = try await Self.authSession.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw APIError.custom("Authentication failed")
        }
        return (data, http)
    }

    private func extractCookie(from response: HTTPURLResponse) -> String {
        let headers = response.value(forHTTPHeaderField: "Set-Cookie") ?? ""
        guard !headers.isEmpty else { return "" }

        let cookies = headers
            .components(separatedBy: ",")
            .flatMap { $0.components(separatedBy: ";") }
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.contains("=") }
            .filter { !$0.lowercased().hasPrefix("path=") && !$0.lowercased().hasPrefix("expires=") && !$0.lowercased().hasPrefix("max-age=") && !$0.lowercased().hasPrefix("httponly") && !$0.lowercased().hasPrefix("secure") && !$0.lowercased().hasPrefix("samesite") }

        return Array(Set(cookies)).joined(separator: "; ")
    }

    private func canAccessDashboard(baseURL: String, cookie: String?) async throws -> Bool {
        var headers = ["Accept": "application/json"]
        if let cookie, !cookie.isEmpty {
            headers["Cookie"] = cookie
        }

        do {
            _ = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: "",
                path: "/api/dashboard/stats",
                headers: headers
            )
            return true
        } catch {
            return false
        }
    }

    private func getEnvironments() async throws -> [DockhandEnvironmentInfo] {
        let data = try await requestData(path: "/api/environments")
        let entries = DockhandJSON.array(from: data, keys: ["environments", "items", "data"])
        return entries.enumerated().map { index, item in
            let id = DockhandJSON.string(item["id"]) ?? DockhandJSON.int(item["id"]).map(String.init) ?? DockhandJSON.string(item["env"]) ?? "\(index)"
            return DockhandEnvironmentInfo(
                id: id,
                name: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["name"]), DockhandJSON.string(item["label"]), "Environment \(id)"]),
                isDefault: DockhandJSON.bool(item["isDefault"]) ?? DockhandJSON.bool(item["default"]) ?? (index == 0)
            )
        }
    }

    private struct DockhandScope: Sendable {
        let environmentId: String?
    }

    private func resolveScopes(requestedEnvironmentId: String?, environments: [DockhandEnvironmentInfo]) -> [DockhandScope] {
        if let requestedEnvironmentId = Self.normalizeEnvironmentId(requestedEnvironmentId),
           !requestedEnvironmentId.isEmpty {
            return [DockhandScope(environmentId: requestedEnvironmentId)]
        }
        return [DockhandScope(environmentId: nil)]
    }

    private func getContainers(
        for scopes: [DockhandScope],
        fallbackScopes: [DockhandScope] = []
    ) async throws -> [DockhandContainerInfo] {
        var merged: [String: DockhandContainerInfo] = [:]
        try await withThrowingTaskGroup(of: [DockhandContainerInfo].self) { group in
            for scope in scopes {
                group.addTask {
                    let data = try await self.requestData(path: self.envPath("/api/containers", env: scope.environmentId))
                    return DockhandJSON.array(from: data, keys: ["containers", "items", "data"])
                        .compactMap { self.parseContainer($0, environmentId: scope.environmentId) }
                }
            }
            for try await list in group {
                for item in list {
                    merged["\(item.environmentId ?? "all")|\(item.id)"] = item
                }
            }
        }
        if merged.isEmpty && !fallbackScopes.isEmpty {
            try await withThrowingTaskGroup(of: [DockhandContainerInfo].self) { group in
                for scope in fallbackScopes {
                    group.addTask {
                        let data = try await self.requestData(path: self.envPath("/api/containers", env: scope.environmentId))
                        return DockhandJSON.array(from: data, keys: ["containers", "items", "data"])
                            .compactMap { self.parseContainer($0, environmentId: scope.environmentId) }
                    }
                }
                for try await list in group {
                    for item in list {
                        merged["\(item.environmentId ?? "all")|\(item.id)"] = item
                    }
                }
            }
        }

        return Array(merged.values)
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private nonisolated func parseContainer(_ item: [String: Any], environmentId: String?) -> DockhandContainerInfo? {
        guard let id = DockhandJSON.firstNonEmptyOptional([
            DockhandJSON.string(item["id"]),
            DockhandJSON.string(item["Id"]),
            DockhandJSON.string(item["containerId"])
        ]) else {
            return nil
        }

        let rawName = DockhandJSON.firstNonEmpty([
            DockhandJSON.string(item["name"]),
            DockhandJSON.string(item["Names"]),
            DockhandJSON.string(item["Name"]),
            id
        ])

        let name = rawName.trimmingCharacters(in: CharacterSet(charactersIn: "/")).isEmpty ? id : rawName.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let image = DockhandJSON.firstNonEmpty([DockhandJSON.string(item["image"]), DockhandJSON.string(item["Image"]), "-"])
        let state = DockhandJSON.firstNonEmpty([DockhandJSON.string(item["state"]), DockhandJSON.string(item["State"]), "unknown"])
        let status = DockhandJSON.firstNonEmpty([DockhandJSON.string(item["status"]), DockhandJSON.string(item["Status"]), state])
        let health = DockhandJSON.firstNonEmptyOptional([DockhandJSON.string(item["health"]), DockhandJSON.string(item["Health"])])

        let portsSummary = DockhandJSON.portsSummary(from: item)

        return DockhandContainerInfo(
            id: id,
            name: name,
            image: image,
            state: state,
            status: status,
            portsSummary: portsSummary,
            health: health,
            environmentId: resolvedEnvironmentId(from: item, fallback: environmentId)
        )
    }

    private nonisolated func parseStack(_ item: [String: Any], fallbackName: String = "stack", environmentId: String?) -> DockhandStackInfo {
        let name = DockhandJSON.firstNonEmpty([
            DockhandJSON.string(item["name"]),
            DockhandJSON.string(item["Name"]),
            DockhandJSON.string(item["stack"]),
            fallbackName
        ])
        return DockhandStackInfo(
            id: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["id"]), DockhandJSON.int(item["id"]).map(String.init), name]),
            name: name,
            status: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["status"]), DockhandJSON.string(item["state"]), "unknown"]),
            services: DockhandJSON.int(item["services"]) ?? DockhandJSON.int(item["serviceCount"]) ?? 0,
            source: DockhandJSON.firstNonEmptyOptional([DockhandJSON.string(item["source"]), DockhandJSON.string(item["type"])]),
            environmentId: resolvedEnvironmentId(from: item, fallback: environmentId)
        )
    }

    private func getStacks(
        for scopes: [DockhandScope],
        fallbackScopes: [DockhandScope] = []
    ) async throws -> [DockhandStackInfo] {
        var merged: [String: DockhandStackInfo] = [:]
        try await withThrowingTaskGroup(of: [DockhandStackInfo].self) { group in
            for scope in scopes {
                group.addTask {
                    let data = try await self.requestData(path: self.envPath("/api/stacks", env: scope.environmentId))
                    return DockhandJSON.array(from: data, keys: ["stacks", "items", "data"])
                        .map { self.parseStack($0, environmentId: scope.environmentId) }
                }
            }
            for try await list in group {
                for item in list {
                    merged["\(item.environmentId ?? "all")|\(item.id)"] = item
                }
            }
        }
        if merged.isEmpty && !fallbackScopes.isEmpty {
            try await withThrowingTaskGroup(of: [DockhandStackInfo].self) { group in
                for scope in fallbackScopes {
                    group.addTask {
                        let data = try await self.requestData(path: self.envPath("/api/stacks", env: scope.environmentId))
                        return DockhandJSON.array(from: data, keys: ["stacks", "items", "data"])
                            .map { self.parseStack($0, environmentId: scope.environmentId) }
                    }
                }
                for try await list in group {
                    for item in list {
                        merged["\(item.environmentId ?? "all")|\(item.id)"] = item
                    }
                }
            }
        }
        return Array(merged.values)
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private func getResources(
        for scopes: [DockhandScope],
        fallbackScopes: [DockhandScope] = [],
        resourcePath: String,
        kind: String
    ) async throws -> [DockhandResourceInfo] {
        var merged: [String: DockhandResourceInfo] = [:]
        try await withThrowingTaskGroup(of: [DockhandResourceInfo].self) { group in
            for scope in scopes {
                group.addTask {
                    let data = try await self.requestData(path: self.envPath(resourcePath, env: scope.environmentId))
                    return DockhandJSON.array(from: data, keys: ["\(kind)s", "items", "data"]).enumerated().map { index, item in
                        let id = DockhandJSON.firstNonEmpty([
                            DockhandJSON.string(item["id"]),
                            DockhandJSON.string(item["name"]),
                            DockhandJSON.int(item["id"]).map(String.init),
                            "\(kind)_\(index)"
                        ])
                        let name = DockhandJSON.firstNonEmpty([
                            DockhandJSON.string(item["name"]),
                            DockhandJSON.string(item["repoTags"]),
                            DockhandJSON.string(item["driver"]),
                            id
                        ])
                        let details = DockhandJSON.firstNonEmptyOptional([
                            DockhandJSON.string(item["size"]),
                            DockhandJSON.string(item["driver"]),
                            DockhandJSON.string(item["scope"]),
                            DockhandJSON.string(item["created"])
                        ])
                        return DockhandResourceInfo(id: "\(scope.environmentId ?? "all")|\(id)", name: name, details: details)
                    }
                }
            }
            for try await list in group {
                for item in list {
                    merged[item.id] = item
                }
            }
        }
        if merged.isEmpty && !fallbackScopes.isEmpty {
            try await withThrowingTaskGroup(of: [DockhandResourceInfo].self) { group in
                for scope in fallbackScopes {
                    group.addTask {
                        let data = try await self.requestData(path: self.envPath(resourcePath, env: scope.environmentId))
                        return DockhandJSON.array(from: data, keys: ["\(kind)s", "items", "data"]).enumerated().map { index, item in
                            let id = DockhandJSON.firstNonEmpty([
                                DockhandJSON.string(item["id"]),
                                DockhandJSON.string(item["name"]),
                                DockhandJSON.int(item["id"]).map(String.init),
                                "\(kind)_\(index)"
                            ])
                            let name = DockhandJSON.firstNonEmpty([
                                DockhandJSON.string(item["name"]),
                                DockhandJSON.string(item["repoTags"]),
                                DockhandJSON.string(item["driver"]),
                                id
                            ])
                            let details = DockhandJSON.firstNonEmptyOptional([
                                DockhandJSON.string(item["size"]),
                                DockhandJSON.string(item["driver"]),
                                DockhandJSON.string(item["scope"]),
                                DockhandJSON.string(item["created"])
                            ])
                            let environmentId = self.resolvedEnvironmentId(from: item, fallback: scope.environmentId)
                            return DockhandResourceInfo(id: "\(environmentId ?? "all")|\(id)", name: name, details: details)
                        }
                    }
                }
                for try await list in group {
                    for item in list {
                        merged[item.id] = item
                    }
                }
            }
        }
        return Array(merged.values)
    }

    private func getActivity(
        for scopes: [DockhandScope],
        fallbackScopes: [DockhandScope] = []
    ) async throws -> [DockhandActivityInfo] {
        var items: [DockhandActivityInfo] = []
        try await withThrowingTaskGroup(of: [DockhandActivityInfo].self) { group in
            for scope in scopes {
                group.addTask {
                    let data = try await self.requestData(path: self.envPath("/api/activity", env: scope.environmentId))
                    return DockhandJSON.array(from: data, keys: ["activity", "items", "data"]).enumerated().map { index, item in
                        DockhandActivityInfo(
                            id: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["id"]), DockhandJSON.int(item["id"]).map(String.init), "activity_\(index)"]),
                            action: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["action"]), DockhandJSON.string(item["event"]), "event"]),
                            target: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["target"]), DockhandJSON.string(item["resource"]), DockhandJSON.string(item["name"]), "-"]),
                            status: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["status"]), DockhandJSON.string(item["level"]), "info"]),
                            createdAt: DockhandJSON.firstNonEmptyOptional([DockhandJSON.string(item["createdAt"]), DockhandJSON.string(item["timestamp"]), DockhandJSON.string(item["time"])])
                        )
                    }
                }
            }
            for try await list in group {
                items.append(contentsOf: list)
            }
        }
        if items.isEmpty && !fallbackScopes.isEmpty {
            try await withThrowingTaskGroup(of: [DockhandActivityInfo].self) { group in
                for scope in fallbackScopes {
                    group.addTask {
                        let data = try await self.requestData(path: self.envPath("/api/activity", env: scope.environmentId))
                        return DockhandJSON.array(from: data, keys: ["activity", "items", "data"]).enumerated().map { index, item in
                            DockhandActivityInfo(
                                id: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["id"]), DockhandJSON.int(item["id"]).map(String.init), "activity_\(index)"]),
                                action: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["action"]), DockhandJSON.string(item["event"]), "event"]),
                                target: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["target"]), DockhandJSON.string(item["resource"]), DockhandJSON.string(item["name"]), "-"]),
                                status: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["status"]), DockhandJSON.string(item["level"]), "info"]),
                                createdAt: DockhandJSON.firstNonEmptyOptional([DockhandJSON.string(item["createdAt"]), DockhandJSON.string(item["timestamp"]), DockhandJSON.string(item["time"])])
                            )
                        }
                    }
                }
                for try await list in group {
                    items.append(contentsOf: list)
                }
            }
        }
        return items.sorted { ($0.createdAt ?? "") > ($1.createdAt ?? "") }
    }

    private nonisolated func parseSchedule(
        _ item: [String: Any],
        fallbackId: String,
        fallbackName: String,
        environmentId: String?
    ) -> DockhandScheduleInfo {
        DockhandScheduleInfo(
            id: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["id"]), DockhandJSON.int(item["id"]).map(String.init), fallbackId]),
            name: DockhandJSON.firstNonEmpty([DockhandJSON.string(item["name"]), DockhandJSON.string(item["task"]), fallbackName]),
            enabled: DockhandJSON.bool(item["enabled"]) ?? DockhandJSON.bool(item["isEnabled"]) ?? true,
            schedule: DockhandJSON.firstNonEmptyOptional([DockhandJSON.string(item["cron"]), DockhandJSON.string(item["schedule"]), DockhandJSON.string(item["interval"])]),
            environmentId: resolvedEnvironmentId(from: item, fallback: environmentId),
            nextRun: DockhandJSON.firstNonEmptyOptional([DockhandJSON.string(item["nextRun"]), DockhandJSON.string(item["nextExecution"]), DockhandJSON.string(item["next"])]),
            lastRun: DockhandJSON.firstNonEmptyOptional([DockhandJSON.string(item["lastRun"]), DockhandJSON.string(item["lastExecution"]), DockhandJSON.string(item["last"])])
        )
    }

    private func getSchedules(
        for scopes: [DockhandScope],
        fallbackScopes: [DockhandScope] = []
    ) async throws -> [DockhandScheduleInfo] {
        var merged: [String: DockhandScheduleInfo] = [:]
        try await withThrowingTaskGroup(of: [DockhandScheduleInfo].self) { group in
            for scope in scopes {
                group.addTask {
                    let data = try await self.requestData(path: self.envPath("/api/schedules", env: scope.environmentId))
                    return DockhandJSON.array(from: data, keys: ["schedules", "items", "data"]).enumerated().map { index, item in
                        self.parseSchedule(
                            item,
                            fallbackId: "schedule_\(index)",
                            fallbackName: "Schedule \(index + 1)",
                            environmentId: scope.environmentId
                        )
                    }
                }
            }
            for try await list in group {
                for item in list {
                    merged["\(item.environmentId ?? "all")|\(item.id)"] = item
                }
            }
        }
        if merged.isEmpty && !fallbackScopes.isEmpty {
            try await withThrowingTaskGroup(of: [DockhandScheduleInfo].self) { group in
                for scope in fallbackScopes {
                    group.addTask {
                        let data = try await self.requestData(path: self.envPath("/api/schedules", env: scope.environmentId))
                        return DockhandJSON.array(from: data, keys: ["schedules", "items", "data"]).enumerated().map { index, item in
                            self.parseSchedule(
                                item,
                                fallbackId: "schedule_\(index)",
                                fallbackName: "Schedule \(index + 1)",
                                environmentId: scope.environmentId
                            )
                        }
                    }
                }
                for try await list in group {
                    for item in list {
                        merged["\(item.environmentId ?? "all")|\(item.id)"] = item
                    }
                }
            }
        }
        return Array(merged.values).sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    private nonisolated func resolvedEnvironmentId(from item: [String: Any], fallback: String?) -> String? {
        DockhandJSON.firstNonEmptyOptional([
            DockhandJSON.string(item["environmentId"]),
            DockhandJSON.string(item["environment_id"]),
            DockhandJSON.string(item["envId"]),
            DockhandJSON.string(item["env"]),
            fallback
        ])
    }

    private func getStats(
        environmentId: String?,
        containers: [DockhandContainerInfo],
        stacks: [DockhandStackInfo],
        images: [DockhandResourceInfo],
        volumes: [DockhandResourceInfo],
        networks: [DockhandResourceInfo]
    ) async throws -> DockhandStatsInfo {
        let data = try await requestData(path: envPath("/api/dashboard/stats", env: environmentId))
        let root = DockhandJSON.object(from: data) ?? [:]
        let nested = (root["stats"] as? [String: Any]) ?? root

        let totalContainers = DockhandJSON.int(nested["containers"])
            ?? DockhandJSON.int(nested["totalContainers"])
            ?? DockhandJSON.int(nested["total_containers"])
            ?? containers.count

        let runningContainers = DockhandJSON.int(nested["running"])
            ?? DockhandJSON.int(nested["runningContainers"])
            ?? DockhandJSON.int(nested["running_containers"])
            ?? containers.filter { $0.isRunning }.count

        let stoppedContainers = DockhandJSON.int(nested["stopped"])
            ?? DockhandJSON.int(nested["stoppedContainers"])
            ?? DockhandJSON.int(nested["stopped_containers"])
            ?? max(0, totalContainers - runningContainers)

        let issueContainers = DockhandJSON.int(nested["issues"])
            ?? DockhandJSON.int(nested["issueContainers"])
            ?? DockhandJSON.int(nested["issue_containers"])
            ?? containers.filter { $0.isIssue }.count

        return DockhandStatsInfo(
            totalContainers: totalContainers,
            runningContainers: runningContainers,
            stoppedContainers: stoppedContainers,
            issueContainers: issueContainers,
            stacks: DockhandJSON.int(nested["stacks"]) ?? stacks.count,
            images: DockhandJSON.int(nested["images"]) ?? images.count,
            volumes: DockhandJSON.int(nested["volumes"]) ?? volumes.count,
            networks: DockhandJSON.int(nested["networks"]) ?? networks.count
        )
    }

    private func compactDetails(
        _ object: [String: Any],
        maxItems: Int = 18,
        excludedKeys: Set<String> = []
    ) -> [(String, String)] {
        let lowercasedExcludedKeys = Set(excludedKeys.map { $0.lowercased() })
        let preferredKeys = [
            "name", "image", "state", "status", "created", "createdAt",
            "command", "entrypoint", "restartPolicy", "networkMode",
            "platform", "runtime", "health", "ports", "mounts", "labels"
        ]

        var output: [(String, String)] = []
        for key in preferredKeys {
            if lowercasedExcludedKeys.contains(key.lowercased()) {
                continue
            }
            guard let value = object[key] else { continue }
            let normalized = DockhandJSON.string(from: value).trimmingCharacters(in: .whitespacesAndNewlines)
            if normalized.isEmpty || normalized == "{}" || normalized == "[]" { continue }
            output.append((key, String(normalized.replacingOccurrences(of: "\n", with: " ").prefix(220))))
        }

        if output.count < maxItems {
            let existing = Set(output.map { $0.0.lowercased() })
            var extra: [(String, String)] = []
            for (key, value) in object {
                if lowercasedExcludedKeys.contains(key.lowercased()) {
                    continue
                }
                if existing.contains(key.lowercased()) {
                    continue
                }
                let normalized = DockhandJSON.string(from: value).trimmingCharacters(in: .whitespacesAndNewlines)
                if normalized.isEmpty || normalized == "{}" || normalized == "[]" {
                    continue
                }
                extra.append((key, normalized))
            }
            extra.sort { $0.0.localizedCaseInsensitiveCompare($1.0) == .orderedAscending }
            let limit = max(0, maxItems - output.count)
            output.append(contentsOf: extra.prefix(limit).map { ($0.0, String($0.1.replacingOccurrences(of: "\n", with: " ").prefix(220))) })
        }

        return output
    }

    private func fetchStackCompose(encodedName: String, stackName: String, environmentId: String?) async throws -> String {
        let candidatePaths = [
            "/api/stacks/\(encodedName)/compose",
            "/api/stacks/\(encodedName)/docker-compose",
            "/api/stacks/\(encodedName)/file",
            "/api/stacks/\(encodedName)/yaml"
        ]

        for path in candidatePaths {
            if let value = try? await requestData(path: envPath(path, env: environmentId)),
               let compose = extractCompose(from: value),
               !compose.isEmpty {
                return compose
            }
        }

        return "Compose not available for \(stackName)."
    }

    private func extractCompose(from data: Data) -> String? {
        if let root = DockhandJSON.object(from: data) {
            let candidates: [Any?] = [
                root["compose"], root["dockerCompose"], root["content"], root["yaml"], root["stackFile"]
            ]
            for candidate in candidates {
                if let text = DockhandJSON.string(candidate), !text.isEmpty {
                    return text
                }
            }
        }

        guard let text = String(data: data, encoding: .utf8)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
              !text.isEmpty else {
            return nil
        }

        if text.hasPrefix("{") || text.hasPrefix("[") {
            return nil
        }
        return text
    }

    private func synthesizeStats(
        containers: [DockhandContainerInfo],
        stacks: [DockhandStackInfo],
        images: [DockhandResourceInfo],
        volumes: [DockhandResourceInfo],
        networks: [DockhandResourceInfo]
    ) -> DockhandStatsInfo {
        let total = containers.count
        let running = containers.filter { $0.isRunning }.count
        return DockhandStatsInfo(
            totalContainers: total,
            runningContainers: running,
            stoppedContainers: max(0, total - running),
            issueContainers: containers.filter { $0.isIssue }.count,
            stacks: stacks.count,
            images: images.count,
            volumes: volumes.count,
            networks: networks.count
        )
    }

    private func resolveActionResult(data: Data, label: String) async throws -> DockhandActionOutcome {
        let root = DockhandJSON.object(from: data) ?? [:]
        let jobId = DockhandJSON.firstNonEmptyOptional([
            DockhandJSON.string(root["jobId"]),
            DockhandJSON.string(root["job_id"])
        ])

        if let jobId {
            return try await pollJob(jobId: jobId, label: label)
        }

        let success = DockhandJSON.bool(root["success"]) ?? (DockhandJSON.string(root["status"])?.lowercased() != "failed")
        let message = DockhandJSON.firstNonEmpty([
            DockhandJSON.string(root["message"]),
            DockhandJSON.string(root["output"]),
            DockhandJSON.string(root["error"]),
            "\(label) completed"
        ])

        return DockhandActionOutcome(success: success, message: message)
    }

    private func pollJob(jobId: String, label: String) async throws -> DockhandActionOutcome {
        let deadline = Date().addingTimeInterval(180)

        while Date() < deadline {
            let data = try await requestData(path: "/api/jobs/\(jobId)")
            let root = DockhandJSON.object(from: data) ?? [:]
            let status = DockhandJSON.string(root["status"])?.lowercased() ?? ""
            let result = root["result"] as? [String: Any] ?? [:]
            let nestedStatus = DockhandJSON.string(result["status"])?.lowercased() ?? ""

            if status == "running" || nestedStatus == "running" {
                try await Task.sleep(nanoseconds: 1_200_000_000)
                continue
            }

            let failed = status == "failed" || nestedStatus == "failed"
            let message = DockhandJSON.firstNonEmpty([
                DockhandJSON.string(result["message"]),
                DockhandJSON.string(result["output"]),
                DockhandJSON.string(result["error"]),
                DockhandJSON.string(root["error"]),
                DockhandJSON.string(root["message"]),
                failed ? "\(label) failed" : "\(label) completed"
            ])

            return DockhandActionOutcome(success: !failed, message: message)
        }

        throw APIError.custom("\(label) timed out")
    }

    private func requestData(
        path: String,
        method: String = "GET",
        includeSynchronousAccept: Bool = false,
        body: Data? = nil
    ) async throws -> Data {
        var headers = authHeaders()
        if includeSynchronousAccept {
            headers["Accept"] = "application/json"
        }

        return try await engine.requestData(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            method: method,
            headers: headers,
            body: body
        )
    }

    private func findStackObject(name: String, environmentId: String?) async throws -> [String: Any]? {
        let normalizedName = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        func matchStack(in data: Data, env: String?) -> [String: Any]? {
            DockhandJSON.array(from: data, keys: ["stacks", "items", "data"]).first { item in
                let candidateName = DockhandJSON.firstNonEmpty([
                    DockhandJSON.string(item["name"]),
                    DockhandJSON.string(item["Name"]),
                    DockhandJSON.string(item["stack"])
                ])
                let candidateId = DockhandJSON.firstNonEmpty([
                    DockhandJSON.string(item["id"]),
                    DockhandJSON.int(item["id"]).map(String.init)
                ])
                return candidateName.lowercased() == normalizedName || candidateId.lowercased() == normalizedName
            }.map { normalizePrimaryObject($0, preferredKeys: ["stack", "item", "data"]) }
        }

        if let data = try? await requestData(path: envPath("/api/stacks", env: environmentId)),
           let found = matchStack(in: data, env: environmentId) {
            return found
        }

        if environmentId != nil,
           let data = try? await requestData(path: envPath("/api/stacks", env: nil)),
           let found = matchStack(in: data, env: nil) {
            return found
        }

        return nil
    }

    private func findScheduleObject(id: String, environmentId: String?) async throws -> [String: Any]? {
        let normalizedId = id.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        func matchSchedule(in data: Data) -> [String: Any]? {
            DockhandJSON.array(from: data, keys: ["schedules", "items", "data"]).first { item in
                let candidateId = DockhandJSON.firstNonEmpty([
                    DockhandJSON.string(item["id"]),
                    DockhandJSON.int(item["id"]).map(String.init)
                ])
                return candidateId.lowercased() == normalizedId
            }.map { normalizePrimaryObject($0, preferredKeys: ["schedule", "item", "data"]) }
        }

        if let data = try? await requestData(path: envPath("/api/schedules", env: environmentId)),
           let found = matchSchedule(in: data) {
            return found
        }

        if environmentId != nil,
           let data = try? await requestData(path: envPath("/api/schedules", env: nil)),
           let found = matchSchedule(in: data) {
            return found
        }

        return nil
    }

    private func mergeDetailObjects(_ base: [String: Any], _ override: [String: Any]) -> [String: Any] {
        guard !override.isEmpty else { return base }
        guard !base.isEmpty else { return override }

        var merged = base
        for (key, value) in override {
            let normalized = DockhandJSON.string(from: value).trimmingCharacters(in: .whitespacesAndNewlines)
            if normalized.isEmpty || normalized == "{}" || normalized == "[]" {
                continue
            }
            merged[key] = value
        }
        return merged
    }

    private func normalizePrimaryObject(_ object: [String: Any], preferredKeys: [String]) -> [String: Any] {
        for key in preferredKeys {
            if let nested = object[key] as? [String: Any], !nested.isEmpty {
                return nested
            }
        }
        return object
    }

    private func parseContainerLogs(_ data: Data?) -> String {
        guard let data else { return "" }
        if let root = DockhandJSON.object(from: data) {
            let candidates: [String?] = [
                DockhandJSON.string(root["logs"]),
                DockhandJSON.string(root["output"]),
                DockhandJSON.string(root["message"])
            ]
            if let logs = candidates.compactMap({ $0 }).first(where: { !$0.isEmpty }) {
                return logs.trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    private func composePayloads(_ compose: String) -> [Data] {
        let payloads: [[String: String]] = [
            ["compose": compose],
            ["dockerCompose": compose],
            ["content": compose],
            ["yaml": compose],
            ["stackFile": compose]
        ]
        return payloads.compactMap { try? JSONSerialization.data(withJSONObject: $0) }
    }

    private func envPath(_ path: String, env: String?, extra: [String: String] = [:]) -> String {
        var components = URLComponents()
        components.path = path
        var items: [URLQueryItem] = []
        if let env = Self.normalizeEnvironmentId(env), !env.isEmpty {
            items.append(URLQueryItem(name: "env", value: env))
        }
        for (key, value) in extra {
            items.append(URLQueryItem(name: key, value: value))
        }
        components.queryItems = items.isEmpty ? nil : items
        return components.string ?? path
    }

    private func authHeaders() -> [String: String] {
        var headers: [String: String] = ["Content-Type": "application/json"]
        if !sessionCookie.isEmpty {
            headers["Cookie"] = sessionCookie
        }
        return headers
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func normalizeEnvironmentId(_ raw: String?) -> String? {
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty else {
            return nil
        }
        switch trimmed.lowercased() {
        case "all", "*", "any":
            return nil
        default:
            return trimmed
        }
    }

    private static func loginPayloads(username: String, password: String, mfaCode: String) -> [Data] {
        let cleanCode = mfaCode.trimmingCharacters(in: .whitespacesAndNewlines)
        var payloads: [[String: String]] = [
            ["username": username, "password": password],
            ["identity": username, "secret": password],
            ["email": username, "password": password]
        ]

        if !cleanCode.isEmpty {
            payloads += [
                ["username": username, "password": password, "mfaToken": cleanCode],
                ["username": username, "password": password, "code": cleanCode],
                ["username": username, "password": password, "totp": cleanCode],
                ["username": username, "password": password, "otp": cleanCode]
            ]
        }

        return payloads.compactMap { try? JSONSerialization.data(withJSONObject: $0) }
    }
}

enum DockhandJSON {
    static func object(from data: Data) -> [String: Any]? {
        (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
    }

    static func array(from data: Data, keys: [String]) -> [[String: Any]] {
        if let arr = (try? JSONSerialization.jsonObject(with: data)) as? [[String: Any]] {
            return arr
        }

        guard let root = object(from: data) else { return [] }

        for key in keys {
            if let arr = root[key] as? [[String: Any]] {
                return arr
            }
            if let dict = root[key] as? [String: Any] {
                let mapped = objectMapValues(dict)
                if !mapped.isEmpty {
                    return mapped
                }
            }
        }

        if let arr = root.values.first(where: { $0 is [[String: Any]] }) as? [[String: Any]] {
            return arr
        }

        let rootMapped = objectMapValues(root)
        if !rootMapped.isEmpty {
            return rootMapped
        }

        for value in root.values {
            if let nested = value as? [String: Any] {
                let mapped = objectMapValues(nested)
                if !mapped.isEmpty {
                    return mapped
                }
            }
        }

        return []
    }

    private static func objectMapValues(_ value: [String: Any]) -> [[String: Any]] {
        guard !value.isEmpty else { return [] }
        guard value.values.allSatisfy({ $0 is [String: Any] }) else { return [] }
        return value.compactMap { entry -> [String: Any]? in
            guard let obj = entry.value as? [String: Any] else { return nil }
            return withSyntheticId(obj, fallbackId: entry.key)
        }
    }

    private static func withSyntheticId(_ value: [String: Any], fallbackId: String) -> [String: Any] {
        guard value["id"] == nil, value["Id"] == nil, !fallbackId.isEmpty else {
            return value
        }
        var copy = value
        copy["id"] = fallbackId
        return copy
    }

    static func string(_ value: Any?) -> String? {
        if let value = value as? String {
            let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
            return cleaned.isEmpty ? nil : cleaned
        }
        if let value = value as? NSNumber {
            return value.stringValue
        }
        return nil
    }

    static func int(_ value: Any?) -> Int? {
        if let value = value as? Int { return value }
        if let value = value as? NSNumber { return value.intValue }
        if let value = value as? String { return Int(value) }
        return nil
    }

    static func bool(_ value: Any?) -> Bool? {
        if let value = value as? Bool { return value }
        if let value = value as? NSNumber { return value.boolValue }
        if let value = value as? String {
            switch value.lowercased() {
            case "1", "true", "yes", "on": return true
            case "0", "false", "no", "off": return false
            default: return nil
            }
        }
        return nil
    }

    static func firstNonEmpty(_ values: [String?], allowEmpty: Bool = true) -> String {
        let first = values.compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }.first(where: { !$0.isEmpty })
        if let first {
            return first
        }
        return allowEmpty ? "" : ""
    }

    static func firstNonEmptyOptional(_ values: [String?]) -> String? {
        let first = values.compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }.first(where: { !$0.isEmpty })
        return first
    }

    static func string(from value: Any) -> String {
        if let dict = value as? [String: Any] {
            return dict.map { "\($0.key): \(string(from: $0.value))" }
                .sorted()
                .joined(separator: ", ")
                .wrapped(prefix: "{", suffix: "}")
        }
        if let arr = value as? [Any] {
            return arr.map { string(from: $0) }.joined(separator: ", ").wrapped(prefix: "[", suffix: "]")
        }
        if let value = value as? String {
            return value
        }
        if let value = value as? NSNumber {
            return value.stringValue
        }
        return ""
    }

    static func portsSummary(from item: [String: Any]) -> String {
        let ports = (item["ports"] as? [[String: Any]]) ?? (item["Ports"] as? [[String: Any]]) ?? []
        guard !ports.isEmpty else { return "-" }

        let chunks = ports.compactMap { port -> String? in
            let privatePort = int(port["privatePort"]) ?? int(port["PrivatePort"])
            let publicPort = int(port["publicPort"]) ?? int(port["PublicPort"])
            let typ = string(port["type"]) ?? string(port["Type"])

            if let privatePort, let publicPort {
                return "\(publicPort):\(privatePort)\(typ.map { "/\($0)" } ?? "")"
            }
            if let privatePort {
                return "\(privatePort)\(typ.map { "/\($0)" } ?? "")"
            }
            return nil
        }

        return chunks.isEmpty ? "-" : chunks.prefix(3).joined(separator: ", ")
    }
}

private extension String {
    func wrapped(prefix: String, suffix: String) -> String {
        prefix + self + suffix
    }
}
