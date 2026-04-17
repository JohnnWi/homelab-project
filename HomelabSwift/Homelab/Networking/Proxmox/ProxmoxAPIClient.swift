import Foundation

struct ProxmoxConsoleSession: Sendable {
    let url: URL
    let cookieName: String
    let cookieValue: String
    let cookieDomain: String
    let isSecure: Bool
}

actor ProxmoxAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var storedAllowSelfSigned = true

    // Ticket-based auth
    private var ticket: String = ""
    private var csrfToken: String = ""
    private var storedUsername: String = ""
    private var storedPassword: String = ""
    private var storedOTP: String?
    private var storedRealm: String = "pam"
    private var ticketIssuedAt: Date?

    // API Token auth
    private var apiTokenString: String = ""

    private var isRefreshing = false
    private var onTokenRefreshed: (@Sendable (String, String) -> Void)?
    private let ticketLifetime: TimeInterval = 2 * 60 * 60
    private let refreshLeadTime: TimeInterval = 10 * 60

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .proxmox, instanceId: instanceId)
    }

    // MARK: - Configuration

    func configure(
        url: String,
        fallbackUrl: String? = nil,
        ticket: String? = nil,
        csrfToken: String? = nil,
        apiTokenString: String? = nil,
        username: String? = nil,
        password: String? = nil,
        otp: String? = nil,
        realm: String? = nil,
        allowSelfSigned: Bool? = nil
    ) {
        self.baseURL = Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.ticket = ticket ?? ""
        self.csrfToken = csrfToken ?? ""
        self.apiTokenString = apiTokenString ?? ""
        self.storedUsername = username ?? ""
        self.storedPassword = password ?? ""
        self.storedOTP = otp?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == true ? nil : otp
        self.storedRealm = (realm?.isEmpty == false ? realm : nil) ?? "pam"
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .proxmox, instanceId: instanceId, allowSelfSigned: storedAllowSelfSigned)
        ticketIssuedAt = Self.ticketIssuedAt(from: self.ticket)
    }

    func setTokenRefreshCallback(_ callback: @escaping @Sendable (String, String) -> Void) {
        self.onTokenRefreshed = callback
    }

    private var usesApiToken: Bool {
        !apiTokenString.isEmpty
    }

    private func authHeaders(forWrite: Bool = false) -> [String: String] {
        var headers: [String: String] = ["Content-Type": "application/json"]
        if usesApiToken {
            headers["Authorization"] = "PVEAPIToken=\(apiTokenString)"
        } else if !ticket.isEmpty {
            headers["Cookie"] = "PVEAuthCookie=\(ticket)"
            if forWrite && !csrfToken.isEmpty {
                headers["CSRFPreventionToken"] = csrfToken
            }
        }
        return headers
    }

    private func formURLEncodedBody(from params: [String: String]) -> Data? {
        let bodyString = params
            .sorted(by: { $0.key < $1.key })
            .map {
                let value = $0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.value
                return "\($0.key)=\(value)"
            }
            .joined(separator: "&")
        return bodyString.data(using: .utf8)
    }

    private func authenticationSession() -> URLSession {
        BaseNetworkEngine.authSession(allowSelfSigned: storedAllowSelfSigned, timeout: 10)
    }

    private func markAuthenticated(ticket: String, csrf: String) {
        self.ticket = ticket
        csrfToken = csrf
        ticketIssuedAt = Self.ticketIssuedAt(from: ticket) ?? Date()
        onTokenRefreshed?(ticket, csrf)
    }

    private var activeURL: String {
        baseURL.isEmpty ? fallbackURL : baseURL
    }

    private var canRefreshWithCurrentTicket: Bool {
        !ticket.isEmpty && !storedUsername.isEmpty
    }

    private var needsProactiveRefresh: Bool {
        guard !usesApiToken, !ticket.isEmpty else { return false }
        guard let ticketIssuedAt else { return false }
        return Date().timeIntervalSince(ticketIssuedAt) >= (ticketLifetime - refreshLeadTime)
    }

    private func ensureFreshTicketIfNeeded() async {
        guard needsProactiveRefresh else { return }
        _ = await refreshTokenWithContinuation()
    }

    // MARK: - Authentication (Ticket-based)

    func authenticate(
        url: String,
        username: String,
        password: String,
        otp: String? = nil,
        realm: String = "pam"
    ) async throws -> (ticket: String, csrf: String) {
        let cleanURL = Self.cleanURL(url)
        guard let authURL = URL(string: "\(cleanURL)/api2/json/access/ticket") else {
            throw APIError.invalidURL
        }

        let fullUsername = username.contains("@") ? username : "\(username)@\(realm)"

        var bodyParts: [String: String] = [
            "username": fullUsername,
            "password": password,
            "new-format": "1"
        ]
        if let otp, !otp.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            bodyParts["otp"] = otp.trimmingCharacters(in: .whitespacesAndNewlines)
        }

        let bodyString = bodyParts
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.value)" }
            .joined(separator: "&")

        var req = URLRequest(url: authURL)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        req.httpBody = bodyString.data(using: .utf8)
        req.timeoutInterval = 10

        let session = authenticationSession()

        let (data, resp) = try await session.data(for: req)
        guard let http = resp as? HTTPURLResponse else {
            throw APIError.custom("Invalid response from Proxmox server")
        }

        let responseBody = String(data: data, encoding: .utf8) ?? ""
        let lowercasedBody = responseBody.lowercased()

        if http.statusCode == 401 {
            if lowercasedBody.contains("webauthn") || lowercasedBody.contains("u2f") || lowercasedBody.contains("tfa-challenge") {
                throw APIError.custom("This Proxmox account requires a WebAuthn or U2F challenge. Use an API token, a recovery key, or a TOTP-enabled account for app access.")
            }
            if lowercasedBody.contains("two") || lowercasedBody.contains("tfa") || lowercasedBody.contains("otp") {
                if let otp, !otp.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    throw APIError.custom("The second-factor code was rejected. Verify your TOTP or recovery key and try again.")
                }
                throw APIError.custom("2FA code required. Please enter your TOTP or recovery key.")
            }
            throw APIError.custom("Authentication failed. Check your credentials and realm.")
        }

        guard http.statusCode == 200 else {
            throw APIError.httpError(statusCode: http.statusCode, body: responseBody)
        }

        do {
            let response = try JSONDecoder().decode(ProxmoxAPIResponse<ProxmoxAuthTicket>.self, from: data)
            if let challenge = Self.decodeTfaChallenge(from: response.data.ticket) {
                let secondFactor = otp?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                guard !secondFactor.isEmpty else {
                    if challenge.requiresWebAuthnOnly {
                        throw APIError.custom("This Proxmox account requires a WebAuthn or U2F challenge. Use an API token, a recovery key, or a TOTP-enabled account for app access.")
                    }
                    throw APIError.custom("2FA code required. Please enter your TOTP or recovery key.")
                }
                return try await respondToTfaChallenge(
                    url: cleanURL,
                    username: fullUsername,
                    challengeTicket: response.data.ticket,
                    challenge: challenge,
                    secondFactor: secondFactor
                )
            }
            return (ticket: response.data.ticket, csrf: response.data.CSRFPreventionToken)
        } catch {
            if lowercasedBody.contains("webauthn") || lowercasedBody.contains("u2f") || lowercasedBody.contains("tfa-challenge") {
                throw APIError.custom("This Proxmox account requires a WebAuthn or U2F challenge. Use an API token, a recovery key, or a TOTP-enabled account for app access.")
            }
            if lowercasedBody.contains("tfa") || lowercasedBody.contains("otp") {
                throw APIError.custom("Proxmox requested a second-factor challenge that could not be completed. Try a TOTP or recovery key, or switch to an API token.")
            }
            throw error
        }
    }

    private func respondToTfaChallenge(
        url: String,
        username: String,
        challengeTicket: String,
        challenge: ProxmoxTfaChallenge,
        secondFactor: String
    ) async throws -> (ticket: String, csrf: String) {
        guard let authURL = URL(string: "\(url)/api2/json/access/ticket") else {
            throw APIError.invalidURL
        }

        guard let body = formURLEncodedBody(from: [
            "username": username,
            "password": "\(Self.tfaResponsePrefix(for: secondFactor, challenge: challenge)):\(secondFactor)",
            "tfa-challenge": challengeTicket,
            "new-format": "1"
        ]) else {
            throw APIError.custom("Failed to prepare Proxmox 2FA request.")
        }

        var req = URLRequest(url: authURL)
        req.httpMethod = "POST"
        req.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        req.httpBody = body
        req.timeoutInterval = 10

        let (data, resp) = try await authenticationSession().data(for: req)
        guard let http = resp as? HTTPURLResponse else {
            throw APIError.custom("Invalid response from Proxmox server")
        }

        let responseBody = String(data: data, encoding: .utf8) ?? ""
        if http.statusCode == 401 {
            throw APIError.custom("The second-factor code was rejected. Verify your TOTP or recovery key and try again.")
        }
        guard http.statusCode == 200 else {
            throw APIError.httpError(statusCode: http.statusCode, body: responseBody)
        }

        let response = try JSONDecoder().decode(ProxmoxAPIResponse<ProxmoxAuthTicket>.self, from: data)
        return (ticket: response.data.ticket, csrf: response.data.CSRFPreventionToken)
    }

    /// Authenticate with pre-formed API token string
    func authenticateWithApiToken(url: String, apiToken: String) async throws {
        let cleanURL = Self.cleanURL(url)
        guard let versionURL = URL(string: "\(cleanURL)/api2/json/version") else {
            throw APIError.invalidURL
        }

        var req = URLRequest(url: versionURL)
        req.httpMethod = "GET"
        req.setValue("PVEAPIToken=\(apiToken)", forHTTPHeaderField: "Authorization")
        req.timeoutInterval = 10

        let session = authenticationSession()

        let (_, resp) = try await session.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200...399).contains(http.statusCode) else {
            throw APIError.custom("API Token authentication failed. Verify the token format: USER@REALM!TOKENID=SECRET")
        }
        baseURL = cleanURL
        apiTokenString = apiToken
    }

    // MARK: - Token Refresh

    private func refreshToken() async -> Bool {
        guard !storedUsername.isEmpty, !isRefreshing else { return false }
        isRefreshing = true
        defer { isRefreshing = false }

        let refreshURL = activeURL
        guard !refreshURL.isEmpty else { return false }

        do {
            if canRefreshWithCurrentTicket {
                let renewed = try await authenticate(
                    url: refreshURL,
                    username: storedUsername,
                    password: ticket,
                    realm: storedRealm
                )
                markAuthenticated(ticket: renewed.ticket, csrf: renewed.csrf)
                return true
            }
        } catch {
            // Fall back to stored credentials below.
        }

        guard !storedPassword.isEmpty else { return false }

        do {
            let result = try await authenticate(
                url: refreshURL,
                username: storedUsername,
                password: storedPassword,
                otp: storedOTP,
                realm: storedRealm
            )
            markAuthenticated(ticket: result.ticket, csrf: result.csrf)
            return true
        } catch {
            return false
        }
    }

    // MARK: - Token Refresh with Waiting

    private var refreshInProgress = false

    /// Refreshes the Proxmox ticket, or waits for an in-progress refresh to complete.
    /// This avoids concurrent refresh attempts and deadlocks.
    private func refreshTokenWithContinuation() async -> Bool {
        // If a refresh is already in progress, wait for it.
        // We use a simple polling approach since we can't use task identity with actors.
        if refreshInProgress {
            while refreshInProgress {
                try? await Task.sleep(nanoseconds: 250_000_000) // 250ms polling
            }
            // The refresh completed — check if we have valid credentials now.
            return !ticket.isEmpty || !apiTokenString.isEmpty
        }

        refreshInProgress = true
        defer { refreshInProgress = false }

        return await refreshToken()
    }

    func refreshAuthenticatedSession() async -> Bool {
        guard !usesApiToken else { return !apiTokenString.isEmpty }
        return await refreshTokenWithContinuation()
    }

    // MARK: - Authenticated Requests

    private func authenticatedRequest<T: Decodable>(
        path: String,
        method: String = "GET",
        body: Data? = nil
    ) async throws -> T {
        await ensureFreshTicketIfNeeded()
        let isWrite = method != "GET"
        let h = authHeaders(forWrite: isWrite)
        do {
            return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: h, body: body)
        } catch {
            if isAuthError(error), !usesApiToken, await refreshTokenWithContinuation() {
                return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: authHeaders(forWrite: isWrite), body: body)
            }
            throw error
        }
    }

    private func authenticatedVoidRequest(
        path: String,
        method: String = "POST",
        body: Data? = nil
    ) async throws {
        await ensureFreshTicketIfNeeded()
        let h = authHeaders(forWrite: true)
        do {
            try await engine.requestVoid(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: h, body: body)
        } catch {
            if isAuthError(error), !usesApiToken, await refreshTokenWithContinuation() {
                try await engine.requestVoid(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: authHeaders(forWrite: true), body: body)
            } else {
                throw error
            }
        }
    }

    private func authenticatedStringRequest(
        path: String,
        method: String = "GET",
        body: Data? = nil
    ) async throws -> String {
        await ensureFreshTicketIfNeeded()
        let isWrite = method != "GET"
        let h = authHeaders(forWrite: isWrite)
        do {
            return try await engine.requestString(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: h, body: body)
        } catch {
            if isAuthError(error), !usesApiToken, await refreshTokenWithContinuation() {
                return try await engine.requestString(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: authHeaders(forWrite: isWrite), body: body)
            }
            throw error
        }
    }

    private func authenticatedFormVoidRequest(
        path: String,
        method: String = "POST",
        params: [String: String]
    ) async throws {
        await ensureFreshTicketIfNeeded()
        let body = formURLEncodedBody(from: params)
        func makeHeaders() -> [String: String] {
            var headers = authHeaders(forWrite: true)
            headers["Content-Type"] = "application/x-www-form-urlencoded"
            return headers
        }

        do {
            try await engine.requestVoid(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: makeHeaders(), body: body)
        } catch {
            if isAuthError(error), !usesApiToken, await refreshTokenWithContinuation() {
                try await engine.requestVoid(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: makeHeaders(), body: body)
            } else {
                throw error
            }
        }
    }

    private func authenticatedFormRequest<T: Decodable>(
        path: String,
        method: String = "POST",
        params: [String: String]
    ) async throws -> T {
        await ensureFreshTicketIfNeeded()
        let body = formURLEncodedBody(from: params)
        func makeHeaders() -> [String: String] {
            var headers = authHeaders(forWrite: true)
            headers["Content-Type"] = "application/x-www-form-urlencoded"
            return headers
        }

        do {
            return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: makeHeaders(), body: body)
        } catch {
            if isAuthError(error), !usesApiToken, await refreshTokenWithContinuation() {
                return try await engine.request(baseURL: baseURL, fallbackURL: fallbackURL, path: path, method: method, headers: makeHeaders(), body: body)
            }
            throw error
        }
    }

    private func authenticatedTaskRequest(
        path: String,
        method: String = "POST"
    ) async throws -> ProxmoxTaskReference {
        let response: ProxmoxAPIResponse<String> = try await authenticatedRequest(path: path, method: method)
        return ProxmoxTaskReference(upid: response.data)
    }

    private func authenticatedFormTaskRequest(
        path: String,
        method: String = "POST",
        params: [String: String]
    ) async throws -> ProxmoxTaskReference {
        let response: ProxmoxAPIResponse<String> = try await authenticatedFormRequest(path: path, method: method, params: params)
        return ProxmoxTaskReference(upid: response.data)
    }

    private func isAuthError(_ error: Error) -> Bool {
        guard let apiError = error as? APIError else { return false }
        switch apiError {
        case .httpError(let code, _): return code == 401 || code == 403
        case .unauthorized: return true
        case .bothURLsFailed(let primary, let fallback):
            return isAuthError(primary) || isAuthError(fallback)
        default: return false
        }
    }

    // MARK: - Ping

    func ping() async -> Bool {
        if baseURL.isEmpty { return false }
        if !usesApiToken, needsProactiveRefresh {
            _ = await refreshTokenWithContinuation()
        }
        let headers = authHeaders()
        if await engine.pingURL("\(baseURL)/api2/json/version", extraHeaders: headers) { return true }
        if !fallbackURL.isEmpty {
            if await engine.pingURL("\(fallbackURL)/api2/json/version", extraHeaders: headers) {
                return true
            }
        }
        if !usesApiToken, await refreshTokenWithContinuation() {
            let refreshedHeaders = authHeaders()
            if await engine.pingURL("\(baseURL)/api2/json/version", extraHeaders: refreshedHeaders) { return true }
            if !fallbackURL.isEmpty {
                return await engine.pingURL("\(fallbackURL)/api2/json/version", extraHeaders: refreshedHeaders)
            }
        }
        return false
    }

    // MARK: - Version

    func getVersion() async throws -> ProxmoxVersion {
        let response: ProxmoxAPIResponse<ProxmoxVersion> = try await authenticatedRequest(path: "/api2/json/version")
        return response.data
    }

    // MARK: - Nodes

    func getNodes() async throws -> [ProxmoxNode] {
        let response: ProxmoxAPIResponse<[ProxmoxNode]> = try await authenticatedRequest(path: "/api2/json/nodes")
        return response.data
    }

    func getNodeStatus(node: String) async throws -> ProxmoxNodeStatus {
        let response: ProxmoxAPIResponse<ProxmoxNodeStatus> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/status")
        return response.data
    }

    func getNodeRRDData(node: String, timeframe: ProxmoxRRDTimeframe = .day) async throws -> [ProxmoxRRDData] {
        let response: ProxmoxAPIResponse<[ProxmoxRRDData]> = try await authenticatedRequest(
            path: "/api2/json/nodes/\(node)/rrddata?timeframe=\(timeframe.apiValue)&cf=AVERAGE"
        )
        return response.data
            .filter(\.hasData)
            .sorted { ($0.time ?? 0) < ($1.time ?? 0) }
    }

    // MARK: - Virtual Machines (QEMU)

    func getVMs(node: String, includeTemplates: Bool = false) async throws -> [ProxmoxVM] {
        let response: ProxmoxAPIResponse<[ProxmoxVM]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu")
        return includeTemplates ? response.data : response.data.filter { $0.template != 1 }
    }

    func getVMStatus(node: String, vmid: Int) async throws -> ProxmoxVM {
        let response: ProxmoxAPIResponse<ProxmoxVM> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/status/current")
        return response.data
    }

    func getVMConfig(node: String, vmid: Int) async throws -> ProxmoxGuestConfig {
        let response: ProxmoxAPIResponse<ProxmoxGuestConfig> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/config")
        return response.data
    }

    func updateVMConfig(node: String, vmid: Int, params: [String: String]) async throws -> ProxmoxTaskReference {
        return try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/config", params: params)
    }

    func getVMGuestAgentInfo(node: String, vmid: Int) async throws -> ProxmoxGuestAgentInfo {
        let response: ProxmoxAPIResponse<ProxmoxGuestAgentInfo> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/agent/info")
        return response.data
    }

    func getVMGuestAgentOSInfo(node: String, vmid: Int) async throws -> ProxmoxGuestAgentOSInfo {
        let response: ProxmoxAPIResponse<ProxmoxGuestAgentOSInfo> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/agent/get-osinfo")
        return response.data
    }

    func getVMGuestAgentHostname(node: String, vmid: Int) async throws -> ProxmoxGuestAgentHostname {
        let response: ProxmoxAPIResponse<ProxmoxGuestAgentHostname> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/agent/get-host-name")
        return response.data
    }

    func getVMGuestAgentUsers(node: String, vmid: Int) async throws -> [ProxmoxGuestAgentUser] {
        let response: ProxmoxAPIResponse<[ProxmoxGuestAgentUser]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/agent/get-users")
        return response.data
    }

    func getVMGuestAgentFilesystems(node: String, vmid: Int) async throws -> [ProxmoxGuestAgentFilesystem] {
        let response: ProxmoxAPIResponse<[ProxmoxGuestAgentFilesystem]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/agent/get-fsinfo")
        return response.data
    }

    func getVMGuestAgentTimezone(node: String, vmid: Int) async throws -> ProxmoxGuestAgentTimezone {
        let response: ProxmoxAPIResponse<ProxmoxGuestAgentTimezone> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/agent/get-timezone")
        return response.data
    }

    func getVMGuestAgentNetworkInterfaces(node: String, vmid: Int) async throws -> [ProxmoxGuestAgentNetworkInterface] {
        let response: ProxmoxAPIResponse<[ProxmoxGuestAgentNetworkInterface]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/agent/network-get-interfaces")
        return response.data
    }

    func startVM(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/status/start")
    }

    func stopVM(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/status/stop")
    }

    func shutdownVM(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/status/shutdown")
    }

    func rebootVM(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/status/reboot")
    }

    func suspendVM(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/status/suspend")
    }

    func resumeVM(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/status/resume")
    }

    // MARK: - LXC Containers

    func getLXCs(node: String, includeTemplates: Bool = false) async throws -> [ProxmoxLXC] {
        let response: ProxmoxAPIResponse<[ProxmoxLXC]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/lxc")
        return includeTemplates ? response.data : response.data.filter { $0.template != 1 }
    }

    func getLXCStatus(node: String, vmid: Int) async throws -> ProxmoxLXC {
        let response: ProxmoxAPIResponse<ProxmoxLXC> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/status/current")
        return response.data
    }

    func getGuestRRDData(node: String, vmid: Int, guestType: String, timeframe: ProxmoxRRDTimeframe = .day) async throws -> [ProxmoxRRDData] {
        let response: ProxmoxAPIResponse<[ProxmoxRRDData]> = try await authenticatedRequest(
            path: "/api2/json/nodes/\(node)/\(guestType)/\(vmid)/rrddata?timeframe=\(timeframe.apiValue)&cf=AVERAGE"
        )
        return response.data
            .filter(\.hasData)
            .sorted { ($0.time ?? 0) < ($1.time ?? 0) }
    }

    func getLXCConfig(node: String, vmid: Int) async throws -> ProxmoxGuestConfig {
        let response: ProxmoxAPIResponse<ProxmoxGuestConfig> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/config")
        return response.data
    }

    func updateLXCConfig(node: String, vmid: Int, params: [String: String]) async throws -> ProxmoxTaskReference {
        return try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/config", params: params)
    }

    func startLXC(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/status/start")
    }

    func stopLXC(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/status/stop")
    }

    func shutdownLXC(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/status/shutdown")
    }

    func rebootLXC(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/status/reboot")
    }

    // MARK: - Storage

    func getStorage(node: String) async throws -> [ProxmoxStorage] {
        let response: ProxmoxAPIResponse<[ProxmoxStorage]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/storage")
        return response.data
    }

    // MARK: - Snapshots

    func getVMSnapshots(node: String, vmid: Int) async throws -> [ProxmoxSnapshot] {
        let response: ProxmoxAPIResponse<[ProxmoxSnapshot]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/snapshot")
        return response.data
    }

    func createVMSnapshot(node: String, vmid: Int, name: String, description: String? = nil, includeRAM: Bool = false) async throws -> ProxmoxTaskReference {
        var params: [String: String] = ["snapname": name]
        if let description, !description.isEmpty { params["description"] = description }
        if includeRAM { params["vmstate"] = "1" }
        return try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/snapshot", params: params)
    }

    func rollbackVMSnapshot(node: String, vmid: Int, snapname: String) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/snapshot/\(snapname)/rollback")
    }

    func deleteVMSnapshot(node: String, vmid: Int, snapname: String) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/snapshot/\(snapname)", method: "DELETE")
    }

    func getLXCSnapshots(node: String, vmid: Int) async throws -> [ProxmoxSnapshot] {
        let response: ProxmoxAPIResponse<[ProxmoxSnapshot]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/snapshot")
        return response.data
    }

    func createLXCSnapshot(node: String, vmid: Int, name: String, description: String? = nil) async throws -> ProxmoxTaskReference {
        var params: [String: String] = ["snapname": name]
        if let description, !description.isEmpty { params["description"] = description }
        return try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/snapshot", params: params)
    }

    func rollbackLXCSnapshot(node: String, vmid: Int, snapname: String) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/snapshot/\(snapname)/rollback")
    }

    func deleteLXCSnapshot(node: String, vmid: Int, snapname: String) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/snapshot/\(snapname)", method: "DELETE")
    }

    // MARK: - Cluster Resources

    func getClusterResources(type: String? = nil) async throws -> [ProxmoxClusterResource] {
        var path = "/api2/json/cluster/resources"
        if let type { path += "?type=\(type)" }
        let response: ProxmoxAPIResponse<[ProxmoxClusterResource]> = try await authenticatedRequest(path: path)
        return response.data
    }

    // MARK: - Tasks

    func getTasks(node: String, limit: Int = 20) async throws -> [ProxmoxTask] {
        let response: ProxmoxAPIResponse<[ProxmoxTask]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/tasks?limit=\(limit)")
        return response.data
    }

    func getTaskStatus(node: String, upid: String) async throws -> ProxmoxTask {
        let encodedUpid = upid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? upid
        let response: ProxmoxAPIResponse<ProxmoxTask> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/tasks/\(encodedUpid)/status")
        return response.data
    }

    func getTaskLog(node: String, upid: String, limit: Int = 100) async throws -> [ProxmoxTaskLogEntry] {
        let encodedUpid = upid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? upid
        let response: ProxmoxAPIResponse<[ProxmoxTaskLogEntry]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/tasks/\(encodedUpid)/log?limit=\(limit)")
        return response.data
    }

    // MARK: - Console URL builder

    func consoleSession(node: String, vmid: Int, type: String) async throws -> ProxmoxConsoleSession {
        guard !usesApiToken else {
            throw APIError.custom("Embedded console requires credential-based Proxmox login because the web UI expects a PVEAuthCookie session.")
        }
        if ticket.isEmpty, !storedUsername.isEmpty, !storedPassword.isEmpty {
            _ = await refreshTokenWithContinuation()
        }
        guard !ticket.isEmpty else {
            throw APIError.custom("Proxmox console session is not available. Re-authenticate with username and password.")
        }

        let base = baseURL.isEmpty ? fallbackURL : baseURL
        guard var components = URLComponents(string: base),
              let host = components.host else {
            throw APIError.invalidURL
        }

        components.path = "/"
        components.queryItems = [
            URLQueryItem(name: "console", value: type),
            URLQueryItem(name: "vmid", value: String(vmid)),
            URLQueryItem(name: "node", value: node),
            URLQueryItem(name: "resize", value: "off")
        ]

        guard let url = components.url else {
            throw APIError.invalidURL
        }

        return ProxmoxConsoleSession(
            url: url,
            cookieName: "PVEAuthCookie",
            cookieValue: ticket,
            cookieDomain: host,
            isSecure: components.scheme?.lowercased() == "https"
        )
    }

    // MARK: - Storage Content

    func getStorageContent(node: String, storage: String) async throws -> [ProxmoxStorageContent] {
        let response: ProxmoxAPIResponse<[ProxmoxStorageContent]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/storage/\(storage)/content")
        return response.data
    }

    func deleteStorageContent(node: String, storage: String, volume: String) async throws {
        let encoded = volume.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? volume
        try await authenticatedVoidRequest(
            path: "/api2/json/nodes/\(node)/storage/\(storage)/content/\(encoded)",
            method: "DELETE"
        )
    }

    // MARK: - Firewall

    func getClusterFirewallRules() async throws -> [ProxmoxFirewallRule] {
        let response: ProxmoxAPIResponse<[ProxmoxFirewallRule]> = try await authenticatedRequest(path: "/api2/json/cluster/firewall/rules")
        return response.data
    }

    func getNodeFirewallRules(node: String) async throws -> [ProxmoxFirewallRule] {
        let response: ProxmoxAPIResponse<[ProxmoxFirewallRule]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/firewall/rules")
        return response.data
    }

    func getGuestFirewallRules(node: String, vmid: Int, guestType: String) async throws -> [ProxmoxFirewallRule] {
        let response: ProxmoxAPIResponse<[ProxmoxFirewallRule]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/\(guestType)/\(vmid)/firewall/rules")
        return response.data
    }

    func getClusterFirewallOptions() async throws -> ProxmoxFirewallOptions {
        let response: ProxmoxAPIResponse<ProxmoxFirewallOptions> = try await authenticatedRequest(path: "/api2/json/cluster/firewall/options")
        return response.data
    }

    func setClusterFirewallEnable(_ enabled: Bool) async throws {
        try await authenticatedFormVoidRequest(
            path: "/api2/json/cluster/firewall/options",
            method: "PUT",
            params: ["enable": enabled ? "1" : "0"]
        )
    }

    func createFirewallRule(path: String, params: [String: String]) async throws {
        try await authenticatedFormVoidRequest(path: path, params: params)
    }

    func deleteFirewallRule(path: String, pos: Int) async throws {
        try await authenticatedVoidRequest(path: "\(path)/\(pos)", method: "DELETE")
    }

    // MARK: - Backup

    func createBackup(node: String, vmid: Int, guestType _: String, storage: String, mode: String = "snapshot", compress: String = "zstd") async throws -> ProxmoxTaskReference {
        let params: [String: String] = [
            "vmid": "\(vmid)",
            "storage": storage,
            "mode": mode,
            "compress": compress
        ]
        return try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/vzdump", params: params)
    }

    func getBackupJobs() async throws -> [ProxmoxBackupJob] {
        let response: ProxmoxAPIResponse<[ProxmoxBackupJob]> = try await authenticatedRequest(path: "/api2/json/cluster/backup")
        return response.data
    }

    func triggerBackupJob(jobId: String) async throws -> ProxmoxTaskReference {
        let encodedId = jobId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? jobId
        return try await authenticatedTaskRequest(path: "/api2/json/cluster/backup/\(encodedId)", method: "POST")
    }

    // MARK: - Networks

    func getNetworks(node: String) async throws -> [ProxmoxNetwork] {
        let response: ProxmoxAPIResponse<[ProxmoxNetwork]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/network")
        return response.data
    }

    // MARK: - Pools

    func getPools() async throws -> [ProxmoxPool] {
        let response: ProxmoxAPIResponse<[ProxmoxPool]> = try await authenticatedRequest(path: "/api2/json/pools")
        return response.data
    }

    func getNextAvailableVmid() async throws -> Int {
        let response: ProxmoxAPIResponse<ProxmoxFlexibleInt> = try await authenticatedRequest(path: "/api2/json/cluster/nextid")
        return response.data.value
    }

    func getPoolMembers(poolid: String) async throws -> ProxmoxPoolDetail {
        let encoded = poolid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? poolid
        let response: ProxmoxAPIResponse<ProxmoxPoolDetail> = try await authenticatedRequest(path: "/api2/json/pools/\(encoded)")
        return response.data
    }

    func updatePoolComment(poolid: String, comment: String) async throws {
        let encoded = poolid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? poolid
        try await authenticatedFormVoidRequest(
            path: "/api2/json/pools/\(encoded)",
            method: "PUT",
            params: ["comment": comment]
        )
    }

    func deletePool(poolid: String) async throws {
        let encoded = poolid.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? poolid
        try await authenticatedVoidRequest(path: "/api2/json/pools/\(encoded)", method: "DELETE")
    }

    // MARK: - HA (High Availability)

    func getHAResources() async throws -> [ProxmoxHAResource] {
        let response: ProxmoxAPIResponse<[ProxmoxHAResource]> = try await authenticatedRequest(path: "/api2/json/cluster/ha/resources")
        return response.data
    }

    func getHAGroups() async throws -> [ProxmoxHAGroup] {
        let response: ProxmoxAPIResponse<[ProxmoxHAGroup]> = try await authenticatedRequest(path: "/api2/json/cluster/ha/groups")
        return response.data
    }

    // MARK: - Replication

    func getReplicationJobs() async throws -> [ProxmoxReplicationJob] {
        let response: ProxmoxAPIResponse<[ProxmoxReplicationJob]> = try await authenticatedRequest(path: "/api2/json/cluster/replication")
        return response.data
    }

    // MARK: - DNS

    func getNodeDNS(node: String) async throws -> ProxmoxDNS {
        let response: ProxmoxAPIResponse<ProxmoxDNS> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/dns")
        return response.data
    }

    // MARK: - Apt Updates

    func getNodeAptUpdates(node: String) async throws -> [ProxmoxAptPackage] {
        let response: ProxmoxAPIResponse<[ProxmoxAptPackage]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/apt/update")
        return response.data
    }

    // MARK: - Services

    func getNodeServices(node: String) async throws -> [ProxmoxService] {
        let response: ProxmoxAPIResponse<[ProxmoxService]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/services")
        return response.data
    }

    func controlService(node: String, service: String, action: String) async throws {
        let encodedService = service.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? service
        try await authenticatedVoidRequest(path: "/api2/json/nodes/\(node)/services/\(encodedService)/\(action)")
    }

    // MARK: - Ceph (if present)

    func getCephStatus(node: String) async throws -> ProxmoxCephStatus {
        let response: ProxmoxAPIResponse<ProxmoxCephStatus> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/ceph/status")
        return response.data
    }

    func getCephOSDs(node: String) async throws -> ProxmoxCephOSDTree {
        let response: ProxmoxAPIResponse<ProxmoxCephOSDTree> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/ceph/osd")
        return response.data
    }

    func getCephPools(node: String) async throws -> [ProxmoxCephPool] {
        let response: ProxmoxAPIResponse<[ProxmoxCephPool]> = try await authenticatedRequest(path: "/api2/json/nodes/\(node)/ceph/pool")
        return response.data
    }

    // MARK: - Migrate

    func migrateVM(node: String, vmid: Int, targetNode: String, online: Bool = true) async throws -> ProxmoxTaskReference {
        let params: [String: String] = [
            "target": targetNode,
            "online": online ? "1" : "0"
        ]
        return try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/migrate", params: params)
    }

    func migrateLXC(node: String, vmid: Int, targetNode: String, online: Bool = true) async throws -> ProxmoxTaskReference {
        let params: [String: String] = [
            "target": targetNode,
            "online": online ? "1" : "0"
        ]
        return try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/migrate", params: params)
    }

    // MARK: - Clone

    func createVM(node: String, request: ProxmoxVMCreationRequest) async throws -> ProxmoxTaskReference {
        try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/qemu", params: request.formParameters())
    }

    func createLXC(node: String, request: ProxmoxLXCCreationRequest) async throws -> ProxmoxTaskReference {
        try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/lxc", params: request.formParameters())
    }

    func restoreVM(node: String, request: ProxmoxVMRestoreRequest) async throws -> ProxmoxTaskReference {
        try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/qemu", params: request.formParameters())
    }

    func restoreLXC(node: String, request: ProxmoxLXCRestoreRequest) async throws -> ProxmoxTaskReference {
        try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/lxc", params: request.formParameters())
    }

    func cloneVM(
        node: String,
        vmid: Int,
        newVmid: Int,
        name: String? = nil,
        full: Bool = true,
        targetNode: String? = nil,
        storage: String? = nil,
        pool: String? = nil
    ) async throws -> ProxmoxTaskReference {
        var params: [String: String] = [
            "newid": "\(newVmid)",
            "full": full ? "1" : "0"
        ]
        if let name, !name.isEmpty { params["name"] = name }
        if let targetNode, !targetNode.isEmpty { params["target"] = targetNode }
        if let storage, !storage.isEmpty { params["storage"] = storage }
        if let pool, !pool.isEmpty { params["pool"] = pool }
        return try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/clone", params: params)
    }

    func cloneLXC(
        node: String,
        vmid: Int,
        newVmid: Int,
        name: String? = nil,
        full: Bool = true,
        targetNode: String? = nil,
        storage: String? = nil,
        pool: String? = nil
    ) async throws -> ProxmoxTaskReference {
        var params: [String: String] = [
            "newid": "\(newVmid)",
            "full": full ? "1" : "0"
        ]
        if let name, !name.isEmpty { params["hostname"] = name }
        if let targetNode, !targetNode.isEmpty { params["target"] = targetNode }
        if let storage, !storage.isEmpty { params["storage"] = storage }
        if let pool, !pool.isEmpty { params["pool"] = pool }
        return try await authenticatedFormTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/clone", params: params)
    }

    func convertVMToTemplate(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/qemu/\(vmid)/template")
    }

    func convertLXCToTemplate(node: String, vmid: Int) async throws -> ProxmoxTaskReference {
        try await authenticatedTaskRequest(path: "/api2/json/nodes/\(node)/lxc/\(vmid)/template")
    }

    // MARK: - Helpers

    private static func cleanURL(_ url: String) -> String {
        url.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func ticketIssuedAt(from ticket: String) -> Date? {
        let parts = ticket.split(separator: ":", omittingEmptySubsequences: false)
        guard parts.count >= 3,
              let issuedAt = Int(parts[2], radix: 16),
              issuedAt > 0 else {
            return nil
        }
        return Date(timeIntervalSince1970: TimeInterval(issuedAt))
    }

    private static func decodeTfaChallenge(from ticket: String) -> ProxmoxTfaChallenge? {
        let parts = ticket.split(separator: ":", omittingEmptySubsequences: false)
        guard parts.count >= 2 else { return nil }
        let rawChallenge = String(parts[1])
        guard rawChallenge.hasPrefix("!tfa!") else { return nil }
        let encoded = String(rawChallenge.dropFirst("!tfa!".count))
        guard let decoded = encoded.removingPercentEncoding,
              let data = decoded.data(using: .utf8) else {
            return nil
        }
        return try? JSONDecoder().decode(ProxmoxTfaChallenge.self, from: data)
    }

    private static func tfaResponsePrefix(for secondFactor: String, challenge: ProxmoxTfaChallenge) -> String {
        if challenge.supportsTotp && !challenge.supportsRecovery {
            return "totp"
        }
        if challenge.supportsRecovery && !challenge.supportsTotp {
            return "recovery"
        }

        let trimmed = secondFactor.trimmingCharacters(in: .whitespacesAndNewlines)
        let digitOnly = trimmed.unicodeScalars.allSatisfy(CharacterSet.decimalDigits.contains)
        if digitOnly && (6...8).contains(trimmed.count) {
            return "totp"
        }
        return "recovery"
    }
}
