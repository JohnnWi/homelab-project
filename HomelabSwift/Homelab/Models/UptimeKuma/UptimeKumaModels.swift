import Foundation

enum UptimeKumaMonitorState: Int, Codable, Hashable, Sendable {
    case down = 0
    case up = 1
    case pending = 2
    case maintenance = 3
    case unknown = -1

    init(metricValue: Double?) {
        guard let metricValue, metricValue.isFinite else {
            self = .unknown
            return
        }
        self = UptimeKumaMonitorState(rawValue: Int(metricValue.rounded())) ?? .unknown
    }
}

struct UptimeKumaMonitor: Identifiable, Codable, Hashable, Sendable {
    let id: String
    let name: String
    let type: String?
    let url: String?
    let hostname: String?
    var state: UptimeKumaMonitorState
    var responseTimeMs: Double?
    var certDaysRemaining: Int?

    var target: String? {
        [url, hostname]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty }
    }
}

struct UptimeKumaDashboardData: Codable, Hashable, Sendable {
    let monitors: [UptimeKumaMonitor]
    let scrapedAt: Date

    var total: Int { monitors.count }
    var up: Int { monitors.filter { $0.state == .up }.count }
    var down: Int { monitors.filter { $0.state == .down }.count }
    var pending: Int { monitors.filter { $0.state == .pending }.count }
    var maintenance: Int { monitors.filter { $0.state == .maintenance }.count }
    var unknown: Int { monitors.filter { $0.state == .unknown }.count }

    var healthyPercent: Double {
        guard total > 0 else { return 0 }
        return Double(up) / Double(total)
    }

    var averageResponseTimeMs: Double? {
        let values = monitors.compactMap(\.responseTimeMs).filter { $0.isFinite && $0 >= 0 }
        guard !values.isEmpty else { return nil }
        return values.reduce(0, +) / Double(values.count)
    }

    var expiringCertificates: Int {
        monitors.filter { monitor in
            guard let days = monitor.certDaysRemaining else { return false }
            return days >= 0 && days <= 14
        }.count
    }
}

struct UptimeKumaSummary: Codable, Hashable, Sendable {
    let up: Int
    let total: Int
    let down: Int
}
