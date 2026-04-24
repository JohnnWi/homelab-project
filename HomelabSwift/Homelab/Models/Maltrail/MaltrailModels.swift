import Foundation

struct MaltrailCountPoint: Identifiable, Hashable, Sendable {
    let timestamp: Int
    let count: Int

    var id: String { "\(timestamp)-\(count)" }

    var date: Date {
        Date(timeIntervalSince1970: TimeInterval(timestamp))
    }

    var apiDate: String {
        MaltrailDateFormatting.apiDayString(from: date)
    }

    var displayDate: String {
        MaltrailDateFormatting.displayDayString(from: date)
    }
}

struct MaltrailEvent: Identifiable, Hashable, Sendable {
    let id: String
    let timestamp: String?
    let source: String?
    let destination: String?
    let protocolName: String?
    let trail: String?
    let severity: String?
    let sensor: String?
    let info: String?
    let rawFields: [String: String]

    var title: String {
        firstNonEmpty(trail, info, source, destination) ?? "-"
    }

    var route: String {
        let src = source?.nonEmptyValue ?? "-"
        let dst = destination?.nonEmptyValue ?? "-"
        return "\(src) -> \(dst)"
    }

    var normalizedSeverity: String {
        severity?.nonEmptyValue ?? "event"
    }
}

struct MaltrailDashboardData: Sendable {
    let counts: [MaltrailCountPoint]
    let selectedDate: Date
    let events: [MaltrailEvent]

    var latestCount: MaltrailCountPoint? {
        counts.sorted { $0.timestamp > $1.timestamp }.first
    }

    var totalFindings: Int {
        counts.reduce(0) { $0 + $1.count }
    }
}

struct MaltrailSummary: Sendable {
    let latestCount: Int
    let latestDayLabel: String
    let totalFindings: Int
}

enum MaltrailDateFormatting {
    static func apiDayString(from date: Date) -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    static func displayDayString(from date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        return formatter.string(from: date)
    }
}

private func firstNonEmpty(_ values: String?...) -> String? {
    values.compactMap { $0?.nonEmptyValue }.first
}

private extension String {
    var nonEmptyValue: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
