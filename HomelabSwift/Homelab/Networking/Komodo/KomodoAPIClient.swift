import Foundation

actor KomodoAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var storedAllowSelfSigned = true
    private var baseURL: String = ""
    private var fallbackURL: String = ""
    private var apiKey: String = ""
    private var apiSecret: String = ""

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .komodo, instanceId: instanceId)
    }

    func configure(url: String, apiKey: String, apiSecret: String, fallbackUrl: String? = nil, allowSelfSigned: Bool? = nil) {
        baseURL = Self.cleanURL(url)
        fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        self.apiSecret = apiSecret.trimmingCharacters(in: .whitespacesAndNewlines)
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .komodo, instanceId: instanceId, allowSelfSigned: storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty, !apiKey.isEmpty, !apiSecret.isEmpty else { return false }
        return (try? await readJSON(path: "/read/GetVersion")) != nil
    }

    func authenticate(url: String, apiKey: String, apiSecret: String, fallbackUrl: String? = nil) async throws {
        let previousBase = baseURL
        let previousFallback = fallbackURL
        let previousKey = self.apiKey
        let previousSecret = self.apiSecret

        baseURL = Self.cleanURL(url)
        fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        self.apiSecret = apiSecret.trimmingCharacters(in: .whitespacesAndNewlines)

        do {
            _ = try await getVersion()
        } catch {
            baseURL = previousBase
            fallbackURL = previousFallback
            self.apiKey = previousKey
            self.apiSecret = previousSecret
            throw mapError(error)
        }
    }

    func getVersion() async throws -> String? {
        let json = try await readJSON(path: "/read/GetVersion")
        return KomodoJSON.stringValue(json, keys: ["version", "tag", "commit", "build"])
            ?? KomodoJSON.unwrapString(json)
    }

    func getDashboard() async throws -> KomodoDashboardData {
        let version = try? await getVersion()
        let servers = try? await readJSON(path: "/read/GetServersSummary")
        let deployments = try? await readJSON(path: "/read/GetDeploymentsSummary")
        let stacks = try? await readJSON(path: "/read/GetStacksSummary")
        let containers = try? await readJSON(path: "/read/GetDockerContainersSummary")

        return KomodoDashboardData(
            version: version ?? nil,
            servers: KomodoJSON.resourceSummary(from: servers ?? [:]),
            deployments: KomodoJSON.resourceSummary(from: deployments ?? [:]),
            stacks: KomodoJSON.resourceSummary(from: stacks ?? [:]),
            containers: KomodoJSON.containerSummary(from: containers ?? [:]),
            generatedAt: Date()
        )
    }

    func getSummary() async throws -> KomodoSummary {
        let dashboard = try await getDashboard()
        return KomodoSummary(
            runningContainers: dashboard.containers.running,
            totalContainers: dashboard.containers.total,
            deployments: dashboard.deployments.total,
            servers: dashboard.servers.total
        )
    }

    private func readJSON(path: String) async throws -> Any {
        do {
            let data = try await engine.requestData(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: path,
                method: "POST",
                headers: authHeaders(),
                body: Data("{}".utf8)
            )
            guard !data.isEmpty else { return [:] }
            return try JSONSerialization.jsonObject(with: data)
        } catch {
            throw mapError(error)
        }
    }

    private func authHeaders() -> [String: String] {
        [
            "X-Api-Key": apiKey,
            "X-Api-Secret": apiSecret,
            "Content-Type": "application/json",
            "Accept": "application/json"
        ]
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private func mapError(_ error: Error) -> APIError {
        let t = Translations.current()
        if let apiError = error as? APIError {
            switch apiError {
            case .httpError(let statusCode, _):
                if statusCode == 401 || statusCode == 403 {
                    return .custom(t.komodoErrorInvalidCredentials)
                }
            case .bothURLsFailed(let primary, let fallback):
                return .bothURLsFailed(primaryError: mapError(primary), fallbackError: mapError(fallback))
            default:
                break
            }
            return apiError
        }
        return .custom(error.localizedDescription)
    }
}

private enum KomodoJSON {
    static func resourceSummary(from json: Any) -> KomodoResourceSummary {
        let payload = payload(from: json)
        let running = intValue(in: payload, keys: ["running", "running_count", "ok", "enabled", "online"])
        let stopped = intValue(in: payload, keys: ["stopped", "stopped_count", "disabled", "offline", "down", "not_deployed", "notDeployed"])
        let healthy = intValue(in: payload, keys: ["healthy", "healthy_count"])
        let unhealthy = intValue(in: payload, keys: ["unhealthy", "unhealthy_count", "critical", "failed"])
        let unknown = intValue(in: payload, keys: ["unknown", "unknown_count"])
        let total = intValue(in: payload, keys: ["total", "total_count", "count"])
            ?? [running, stopped, healthy, unhealthy, unknown].compactMap { $0 }.reduce(0, +)

        return KomodoResourceSummary(
            total: total,
            running: running ?? healthy ?? 0,
            stopped: stopped ?? 0,
            healthy: healthy ?? running ?? 0,
            unhealthy: unhealthy ?? 0,
            unknown: unknown ?? max(0, total - [running, stopped, healthy, unhealthy].compactMap { $0 }.reduce(0, +))
        )
    }

    static func containerSummary(from json: Any) -> KomodoContainerSummary {
        let payload = payload(from: json)
        let running = intValue(in: payload, keys: ["running", "running_count"])
        let stopped = intValue(in: payload, keys: ["stopped", "stopped_count"])
        let unhealthy = intValue(in: payload, keys: ["unhealthy", "unhealthy_count", "dead", "dead_count"])
        let exited = intValue(in: payload, keys: ["exited", "exited_count"])
        let paused = intValue(in: payload, keys: ["paused", "paused_count"])
        let restarting = intValue(in: payload, keys: ["restarting", "restarting_count"])
        let unknown = intValue(in: payload, keys: ["unknown", "unknown_count", "removing", "removing_count"])
        let known = [running, stopped, unhealthy, exited, paused, restarting, unknown].compactMap { $0 }.reduce(0, +)
        let total = intValue(in: payload, keys: ["total", "total_count", "count"]) ?? known

        return KomodoContainerSummary(
            total: total,
            running: running ?? 0,
            stopped: stopped ?? exited ?? 0,
            unhealthy: unhealthy ?? restarting ?? 0,
            exited: exited ?? 0,
            paused: paused ?? 0,
            restarting: restarting ?? 0,
            unknown: unknown ?? max(0, total - known)
        )
    }

    static func unwrapString(_ json: Any) -> String? {
        if let string = json as? String, !string.isEmpty { return string }
        if let dict = json as? [String: Any] {
            return stringValue(dict, keys: ["data", "response", "value"])
        }
        return nil
    }

    static func stringValue(_ json: Any, keys: [String]) -> String? {
        guard let dict = payload(from: json) as? [String: Any] else { return nil }
        for key in keys {
            if let value = dict.first(where: { $0.key.caseInsensitiveCompare(key) == .orderedSame })?.value {
                if let string = value as? String, !string.isEmpty { return string }
                if let number = value as? NSNumber { return number.stringValue }
            }
        }
        return nil
    }

    private static func payload(from json: Any) -> Any {
        guard let dict = json as? [String: Any] else { return json }
        for key in ["data", "response", "summary", "stats", "result"] {
            if let value = dict.first(where: { $0.key.caseInsensitiveCompare(key) == .orderedSame })?.value {
                if value is [String: Any] || value is [Any] || value is String || value is NSNumber {
                    return payload(from: value)
                }
            }
        }
        return dict
    }

    private static func intValue(in json: Any, keys: [String]) -> Int? {
        if let dict = json as? [String: Any] {
            for key in keys {
                if let match = dict.first(where: { $0.key.caseInsensitiveCompare(key) == .orderedSame })?.value,
                   let int = int(from: match) {
                    return int
                }
            }
            for value in dict.values {
                if let nested = intValue(in: value, keys: keys) {
                    return nested
                }
            }
        } else if let array = json as? [Any] {
            let values = array.compactMap { intValue(in: $0, keys: keys) }
            if !values.isEmpty {
                return values.reduce(0, +)
            }
        }
        return nil
    }

    private static func int(from value: Any) -> Int? {
        if let int = value as? Int { return int }
        if let double = value as? Double { return Int(double) }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String { return Int(string) }
        return nil
    }
}
