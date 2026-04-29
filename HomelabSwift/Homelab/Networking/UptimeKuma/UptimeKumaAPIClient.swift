import Foundation

actor UptimeKumaAPIClient {
    private let instanceId: UUID
    private var baseURL = ""
    private var fallbackURL = ""
    private var username: String?
    private var password: String?
    private var engine: BaseNetworkEngine

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .uptimeKuma, instanceId: instanceId)
    }

    func configure(
        url: String,
        fallbackUrl: String? = nil,
        username: String? = nil,
        password: String? = nil,
        allowSelfSigned: Bool = true
    ) async {
        self.baseURL = Self.normalizeURL(url)
        self.fallbackURL = Self.normalizeURL(fallbackUrl ?? "")
        self.username = Self.clean(username)
        self.password = Self.clean(password)
        self.engine = BaseNetworkEngine(serviceType: .uptimeKuma, instanceId: instanceId, allowSelfSigned: allowSelfSigned)
    }

    func authenticate(url: String, username: String?, password: String?, fallbackUrl: String?) async throws {
        let oldBase = baseURL
        let oldFallback = fallbackURL
        let oldUsername = self.username
        let oldPassword = self.password

        baseURL = Self.normalizeURL(url)
        fallbackURL = Self.normalizeURL(fallbackUrl ?? "")
        self.username = Self.clean(username)
        self.password = Self.clean(password)

        do {
            let metrics = try await getMetricsText()
            guard Self.looksLikeUptimeKumaMetrics(metrics) else {
                throw APIError.custom(Translations.current().uptimeKumaInvalidMetrics)
            }
        } catch {
            baseURL = oldBase
            fallbackURL = oldFallback
            self.username = oldUsername
            self.password = oldPassword
            throw error
        }
    }

    func ping() async -> Bool {
        guard !baseURL.isEmpty else { return false }
        do {
            let metrics = try await getMetricsText()
            return Self.looksLikeUptimeKumaMetrics(metrics)
        } catch {
            return false
        }
    }

    func getDashboard() async throws -> UptimeKumaDashboardData {
        let metrics = try await getMetricsText()
        return UptimeKumaMetricsParser.parse(metrics)
    }

    func getSummary() async throws -> UptimeKumaSummary {
        let dashboard = try await getDashboard()
        return UptimeKumaSummary(up: dashboard.up, total: dashboard.total, down: dashboard.down)
    }

    private func getMetricsText() async throws -> String {
        guard !baseURL.isEmpty else { throw APIError.notConfigured }
        return try await engine.requestString(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: "/metrics",
            headers: requestHeaders()
        )
    }

    private func requestHeaders() -> [String: String] {
        var headers = [
            "Accept": "text/plain, */*"
        ]
        if let secret = password, !secret.isEmpty {
            let identity = username ?? ""
            let token = "\(identity):\(secret)"
            if let data = token.data(using: .utf8) {
                headers["Authorization"] = "Basic \(data.base64EncodedString())"
            }
        }
        return headers
    }

    private static func normalizeURL(_ raw: String) -> String {
        var clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return "" }
        let trailing = CharacterSet(charactersIn: ")]},;")
        while let last = clean.unicodeScalars.last, trailing.contains(last) {
            clean = String(clean.dropLast())
        }
        if !clean.hasPrefix("http://") && !clean.hasPrefix("https://") {
            clean = "https://" + clean
        }
        return clean.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func clean(_ value: String?) -> String? {
        guard let value else { return nil }
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private static func looksLikeUptimeKumaMetrics(_ text: String) -> Bool {
        text.localizedCaseInsensitiveContains("uptime_kuma")
            || text.localizedCaseInsensitiveContains("monitor_status")
            || text.localizedCaseInsensitiveContains("monitor_response_time")
    }
}

enum UptimeKumaMetricsParser {
    static func parse(_ text: String) -> UptimeKumaDashboardData {
        var monitors: [String: UptimeKumaMonitor] = [:]

        for line in text.split(whereSeparator: \.isNewline) {
            let raw = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !raw.isEmpty, !raw.hasPrefix("#") else { continue }
            guard let sample = parseSample(raw) else { continue }
            guard sample.name.hasPrefix("monitor_") || sample.name.hasPrefix("uptime_kuma_monitor_") else { continue }

            let monitorName = firstNonEmpty(
                sample.labels["monitor_name"],
                sample.labels["name"],
                sample.labels["monitor"]
            ) ?? "Monitor"
            let monitorId = firstNonEmpty(
                sample.labels["monitor_id"],
                sample.labels["id"],
                monitorName
            ) ?? monitorName

            var monitor = monitors[monitorId] ?? UptimeKumaMonitor(
                id: monitorId,
                name: monitorName,
                type: firstNonEmpty(sample.labels["monitor_type"], sample.labels["type"]),
                url: firstNonEmpty(sample.labels["monitor_url"], sample.labels["url"]),
                hostname: firstNonEmpty(sample.labels["monitor_hostname"], sample.labels["hostname"]),
                state: .unknown,
                responseTimeMs: nil,
                certDaysRemaining: nil
            )

            switch sample.name {
            case "monitor_status", "uptime_kuma_monitor_status":
                monitor.state = UptimeKumaMonitorState(metricValue: sample.value)
            case "monitor_response_time", "uptime_kuma_monitor_response_time":
                monitor.responseTimeMs = sample.value.isFinite ? sample.value : nil
            case "monitor_cert_days_remaining", "uptime_kuma_monitor_cert_days_remaining":
                monitor.certDaysRemaining = sample.value.isFinite ? Int(sample.value.rounded()) : nil
            default:
                break
            }

            monitors[monitorId] = monitor
        }

        let sorted = monitors.values.sorted { lhs, rhs in
            if lhs.state != rhs.state {
                return stateRank(lhs.state) < stateRank(rhs.state)
            }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
        return UptimeKumaDashboardData(monitors: sorted, scrapedAt: Date())
    }

    private static func parseSample(_ line: String) -> UptimeKumaMetricSample? {
        guard let splitIndex = line.lastIndex(where: { $0 == " " || $0 == "\t" }),
              splitIndex > line.startIndex else {
            return nil
        }

        let metric = String(line[..<splitIndex]).trimmingCharacters(in: .whitespacesAndNewlines)
        let valueText = String(line[line.index(after: splitIndex)...]).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !metric.isEmpty, let value = Double(valueText) else { return nil }

        if let labelStart = metric.firstIndex(of: "{"),
           let labelEnd = metric.lastIndex(of: "}") {
            let name = String(metric[..<labelStart])
            let labelText = String(metric[metric.index(after: labelStart)..<labelEnd])
            return UptimeKumaMetricSample(name: name, labels: parseLabels(labelText), value: value)
        }

        return UptimeKumaMetricSample(name: metric, labels: [:], value: value)
    }

    private static func parseLabels(_ text: String) -> [String: String] {
        var labels: [String: String] = [:]
        var key = ""
        var value = ""
        var readingKey = true
        var insideQuotes = false
        var escaping = false

        func commit() {
            let cleanKey = key.trimmingCharacters(in: .whitespacesAndNewlines)
            if !cleanKey.isEmpty {
                labels[cleanKey] = value
            }
            key = ""
            value = ""
            readingKey = true
        }

        for char in text {
            if escaping {
                value.append(char)
                escaping = false
                continue
            }
            if insideQuotes, char == "\\" {
                escaping = true
                continue
            }
            if char == "\"" {
                insideQuotes.toggle()
                continue
            }
            if !insideQuotes, char == "=" {
                readingKey = false
                continue
            }
            if !insideQuotes, char == "," {
                commit()
                continue
            }
            if readingKey {
                key.append(char)
            } else {
                value.append(char)
            }
        }
        commit()
        return labels
    }

    private static func firstNonEmpty(_ values: String?...) -> String? {
        values.compactMap { value in
            let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed?.isEmpty == false ? trimmed : nil
        }.first
    }

    private static func stateRank(_ state: UptimeKumaMonitorState) -> Int {
        switch state {
        case .down: return 0
        case .pending: return 1
        case .maintenance: return 2
        case .unknown: return 3
        case .up: return 4
        }
    }

    private struct UptimeKumaMetricSample {
        let name: String
        let labels: [String: String]
        let value: Double
    }
}
