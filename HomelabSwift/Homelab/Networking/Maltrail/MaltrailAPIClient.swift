import CryptoKit
import Foundation

actor MaltrailAPIClient {
    private let instanceId: UUID
    private var storedAllowSelfSigned = true
    private var baseURL = ""
    private var fallbackURL = ""
    private var username = ""
    private var password = ""
    private var sessionCookie = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
    }

    func configure(
        url: String,
        fallbackUrl: String? = nil,
        username: String? = nil,
        password: String? = nil,
        sessionCookie: String? = nil,
        allowSelfSigned: Bool? = nil
    ) {
        baseURL = Self.cleanURL(url)
        fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.username = username?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.password = password ?? ""
        self.sessionCookie = sessionCookie?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        do {
            _ = try await requestData(path: "/counts")
            return true
        } catch {
            return false
        }
    }

    func authenticate(
        url: String,
        username: String? = nil,
        password: String? = nil,
        fallbackUrl: String? = nil
    ) async throws -> String {
        let cleanURL = Self.cleanURL(url)
        let cleanFallback = Self.cleanURL(fallbackUrl ?? "")
        let resolvedUsername = username?.trimmingCharacters(in: .whitespacesAndNewlines) ?? self.username
        let resolvedPassword = password ?? self.password

        if resolvedUsername.isEmpty && resolvedPassword.isEmpty {
            do {
                _ = try await requestDataAgainst(baseURL: cleanURL, path: "/counts", cookie: nil)
                sessionCookie = ""
                return ""
            } catch let primaryError {
                guard !cleanFallback.isEmpty else { throw mapError(primaryError) }
                do {
                    _ = try await requestDataAgainst(baseURL: cleanFallback, path: "/counts", cookie: nil)
                    sessionCookie = ""
                    return ""
                } catch let fallbackError {
                    throw mapError(APIError.bothURLsFailed(primaryError: primaryError, fallbackError: fallbackError))
                }
            }
        }

        guard !resolvedUsername.isEmpty, !resolvedPassword.isEmpty else {
            throw APIError.custom(Translations.current().loginErrorCredentials)
        }

        do {
            let cookie = try await authenticateAgainst(baseURL: cleanURL, username: resolvedUsername, password: resolvedPassword)
            self.username = resolvedUsername
            self.password = resolvedPassword
            self.sessionCookie = cookie
            return cookie
        } catch let primaryError {
            guard !cleanFallback.isEmpty else { throw mapError(primaryError) }
            do {
                let cookie = try await authenticateAgainst(baseURL: cleanFallback, username: resolvedUsername, password: resolvedPassword)
                self.username = resolvedUsername
                self.password = resolvedPassword
                self.sessionCookie = cookie
                return cookie
            } catch let fallbackError {
                throw mapError(APIError.bothURLsFailed(primaryError: primaryError, fallbackError: fallbackError))
            }
        }
    }

    func getCounts() async throws -> [MaltrailCountPoint] {
        do {
            let data = try await requestData(path: "/counts")
            return try parseCounts(from: data)
        } catch {
            throw mapError(error)
        }
    }

    func getEvents(date: Date) async throws -> [MaltrailEvent] {
        do {
            let data = try await requestData(path: eventsPath(for: date))
            return try parseEvents(from: data)
        } catch {
            throw mapError(error)
        }
    }

    func getDashboard(selectedDate: Date? = nil) async throws -> MaltrailDashboardData {
        let counts = try await getCounts()
        let fallbackDate = counts.sorted { $0.timestamp > $1.timestamp }.first?.date ?? Date()
        let date = selectedDate ?? fallbackDate
        let events = (try? await getEvents(date: date)) ?? []
        return MaltrailDashboardData(counts: counts, selectedDate: date, events: events)
    }

    func getSummary() async throws -> MaltrailSummary {
        let counts = try await getCounts()
        let latest = counts.sorted { $0.timestamp > $1.timestamp }.first
        return MaltrailSummary(
            latestCount: latest?.count ?? 0,
            latestDayLabel: latest?.displayDate ?? Translations.current().notAvailable,
            totalFindings: counts.reduce(0) { $0 + $1.count }
        )
    }

    private var headers: [String: String] {
        var result = ["Accept": "application/json"]
        if !sessionCookie.isEmpty {
            result["Cookie"] = sessionCookie
        }
        return result
    }

    private func requestData(path: String) async throws -> Data {
        if !username.isEmpty && !password.isEmpty && sessionCookie.isEmpty {
            _ = try await authenticate(url: baseURL, username: username, password: password, fallbackUrl: fallbackURL)
        }

        do {
            return try await requestDataAgainst(baseURL: baseURL, fallbackURL: fallbackURL, path: path, cookie: sessionCookie)
        } catch let error as APIError {
            guard case .unauthorized = error else { throw error }
            guard !username.isEmpty, !password.isEmpty else { throw error }
            let newCookie = try await authenticate(url: baseURL, username: username, password: password, fallbackUrl: fallbackURL)
            return try await requestDataAgainst(baseURL: baseURL, fallbackURL: fallbackURL, path: path, cookie: newCookie)
        }
    }

    private func eventsPath(for date: Date) -> String {
        var components = URLComponents()
        components.path = "/events"
        components.queryItems = [
            URLQueryItem(name: "date", value: MaltrailDateFormatting.apiDayString(from: date))
        ]
        return components.url?.absoluteString ?? "/events?date=\(MaltrailDateFormatting.apiDayString(from: date))"
    }

    private func parseCounts(from data: Data) throws -> [MaltrailCountPoint] {
        let json = try JSONSerialization.jsonObject(with: data)
        let dictionary: [String: Any]

        if let direct = json as? [String: Any] {
            dictionary = direct
        } else if let wrapped = (json as? [Any])?.first as? [String: Any] {
            dictionary = wrapped
        } else {
            throw APIError.custom(Translations.current().loginErrorFailed)
        }

        return dictionary.compactMap { key, value in
            guard let timestamp = Int(key), let count = Self.intValue(value) else { return nil }
            return MaltrailCountPoint(timestamp: timestamp, count: count)
        }
        .sorted { $0.timestamp > $1.timestamp }
    }

    private func parseEvents(from data: Data) throws -> [MaltrailEvent] {
        let json = try JSONSerialization.jsonObject(with: data)
        let rows = eventRows(from: json)
        return rows.enumerated().map { index, row in
            let raw = row.reduce(into: [String: String]()) { partial, item in
                if let text = Self.stringValue(item.value), !text.isEmpty {
                    partial[item.key] = text
                }
            }
            let id = Self.firstString(in: row, keys: ["id", "event_id", "uid", "uuid"])
                ?? "\(index)-\(raw.hashValue)"
            return MaltrailEvent(
                id: id,
                timestamp: Self.firstString(in: row, keys: ["timestamp", "time", "datetime", "date"]),
                source: Self.firstString(in: row, keys: ["src_ip", "srcip", "source_ip", "source", "src", "attacker"]),
                destination: Self.firstString(in: row, keys: ["dst_ip", "dstip", "destination_ip", "destination", "dst", "target"]),
                protocolName: Self.firstString(in: row, keys: ["proto", "protocol"]),
                trail: Self.firstString(in: row, keys: ["trail", "indicator", "ioc", "signature", "threat"]),
                severity: Self.firstString(in: row, keys: ["severity", "level", "risk", "priority"]),
                sensor: Self.firstString(in: row, keys: ["sensor", "sensor_name", "host"]),
                info: Self.firstString(in: row, keys: ["info", "details", "description", "message", "event"]),
                rawFields: raw
            )
        }
    }

    private func eventRows(from json: Any) -> [[String: Any]] {
        if let rows = json as? [[String: Any]] {
            return rows
        }

        if let dictionary = json as? [String: Any] {
            for key in ["events", "data", "items", "results"] {
                if let rows = dictionary[key] as? [[String: Any]] {
                    return rows
                }
            }

            let nestedArrays = dictionary.values.compactMap { $0 as? [[String: Any]] }
            if let first = nestedArrays.first {
                return first
            }
        }

        return []
    }

    private static func firstString(in dictionary: [String: Any], keys: [String]) -> String? {
        for key in keys {
            if let value = stringValue(dictionary[key]), !value.isEmpty {
                return value
            }
        }
        return nil
    }

    private static func stringValue(_ value: Any?) -> String? {
        switch value {
        case let value as String:
            return value.trimmingCharacters(in: .whitespacesAndNewlines)
        case let value as Int:
            return String(value)
        case let value as Double:
            return value.truncatingRemainder(dividingBy: 1) == 0 ? String(Int(value)) : String(value)
        case let value as Bool:
            return value ? "true" : "false"
        case let values as [Any]:
            return values.compactMap { stringValue($0) }.joined(separator: ", ")
        default:
            return nil
        }
    }

    private static func intValue(_ value: Any) -> Int? {
        switch value {
        case let value as Int:
            return value
        case let value as Double:
            return Int(value)
        case let value as String:
            return Int(value.trimmingCharacters(in: .whitespacesAndNewlines))
        default:
            return nil
        }
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private func requestDataAgainst(
        baseURL: String,
        fallbackURL: String = "",
        path: String,
        cookie: String?
    ) async throws -> Data {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }

        do {
            return try await performDataRequest(baseURL: baseURL, path: path, cookie: cookie)
        } catch let primaryError {
            guard !fallbackURL.isEmpty else { throw primaryError }
            do {
                return try await performDataRequest(baseURL: fallbackURL, path: path, cookie: cookie)
            } catch let fallbackError {
                throw APIError.bothURLsFailed(primaryError: primaryError, fallbackError: fallbackError)
            }
        }
    }

    private func performDataRequest(baseURL: String, path: String, cookie: String?) async throws -> Data {
        let urlString = path.hasPrefix("http://") || path.hasPrefix("https://") ? path : baseURL + path
        guard let url = URL(string: urlString) else { throw APIError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 10
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let cookie, !cookie.isEmpty {
            request.setValue(cookie, forHTTPHeaderField: "Cookie")
        }

        let session = BaseNetworkEngine.authSession(allowSelfSigned: storedAllowSelfSigned, timeout: 10)
        let (data, response) = try await session.data(for: request)
        try Self.validate(response: response, data: data)
        return data
    }

    private func authenticateAgainst(baseURL: String, username: String, password: String) async throws -> String {
        guard let url = URL(string: "\(baseURL)/login") else { throw APIError.invalidURL }

        let nonce = Self.nonce()
        let payload = Self.loginPayload(username: username, password: password, nonce: nonce)

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("application/x-www-form-urlencoded; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("text/plain", forHTTPHeaderField: "Accept")
        request.httpBody = payload.data(using: .utf8)

        let session = BaseNetworkEngine.authSession(allowSelfSigned: storedAllowSelfSigned, timeout: 10)
        let (data, response) = try await session.data(for: request)
        let http = try Self.httpResponse(from: response)

        if http.statusCode == 401 {
            throw APIError.unauthorized
        }
        if http.statusCode >= 400 {
            throw APIError.httpError(statusCode: http.statusCode, body: String(data: data, encoding: .utf8) ?? "")
        }

        guard let cookie = Self.extractCookie(from: http), !cookie.isEmpty else {
            throw APIError.custom("Maltrail login succeeded without a session cookie.")
        }

        _ = try await performDataRequest(baseURL: baseURL, path: "/counts", cookie: cookie)
        return cookie
    }

    private static func loginPayload(username: String, password: String, nonce: String) -> String {
        let passwordHash = sha256(password)
        let responseHash = sha256(passwordHash + nonce)
        var components = URLComponents()
        components.queryItems = [
            URLQueryItem(name: "username", value: username),
            URLQueryItem(name: "nonce", value: nonce),
            URLQueryItem(name: "hash", value: responseHash)
        ]
        return components.percentEncodedQuery ?? ""
    }

    private static func nonce() -> String {
        let bytes = (0..<16).map { _ in UInt8.random(in: .min ... .max) }
        return Data(bytes).map { String(format: "%02x", $0) }.joined()
    }

    private static func sha256(_ value: String) -> String {
        let digest = SHA256.hash(data: Data(value.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private static func extractCookie(from response: HTTPURLResponse) -> String? {
        let cookies = response.value(forHTTPHeaderField: "Set-Cookie") ?? response.allHeaderFields.first { key, _ in
            String(describing: key).caseInsensitiveCompare("Set-Cookie") == .orderedSame
        }.map { String(describing: $0.value) }

        guard let rawCookie = cookies else { return nil }
        return rawCookie
            .split(separator: ";", maxSplits: 1)
            .first
            .map(String.init)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func validate(response: URLResponse, data: Data) throws {
        let http = try httpResponse(from: response)
        if http.statusCode == 401 {
            throw APIError.unauthorized
        }
        if http.statusCode >= 400 {
            throw APIError.httpError(statusCode: http.statusCode, body: String(data: data, encoding: .utf8) ?? "")
        }
        if let contentType = http.value(forHTTPHeaderField: "Content-Type")?.lowercased(),
           contentType.contains("text/html"),
           let body = String(data: data.prefix(500), encoding: .utf8),
           body.lowercased().contains("<html") {
            throw APIError.custom("Received an HTML response instead of JSON. This often happens when authentication is still required.")
        }
    }

    private static func httpResponse(from response: URLResponse) throws -> HTTPURLResponse {
        guard let http = response as? HTTPURLResponse else {
            throw APIError.custom("Invalid response")
        }
        return http
    }

    private func mapError(_ error: Error) -> APIError {
        if let apiError = error as? APIError {
            return apiError
        }
        return .custom(error.localizedDescription)
    }
}
