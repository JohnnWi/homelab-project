import SwiftUI
import Charts

private let wakapiHeatmapColorsDark: [Color] = [
    Color(hex: "#111827"), Color(hex: "#163E75"), Color(hex: "#1D5BBF"), Color(hex: "#2563EB"), Color(hex: "#60A5FA")
]
private let wakapiHeatmapColorsLight: [Color] = [
    Color(hex: "#E8EEF8"), Color(hex: "#C0D7FF"), Color(hex: "#93C5FD"), Color(hex: "#3B82F6"), Color(hex: "#1D4ED8")
]

private struct WakapiActivityPoint: Identifiable {
    let date: Date
    let totalSeconds: Double

    var id: TimeInterval { date.timeIntervalSince1970 }
    var totalHours: Double { totalSeconds / 3600 }
}

private struct WakapiHeatmapCell {
    let level: Int
    let totalSeconds: Double
}

private struct WakapiActivitySnapshot {
    let recentPoints: [WakapiActivityPoint]
    let averageSeconds: Double
    let activeDays: Int
    let bestDay: WakapiActivityPoint?
    let heatmapWeeks: [[WakapiHeatmapCell]]
}

private struct WakapiActivityCaptionFormatter {
    static func recentWindowLabel(days: Int, using t: Translations) -> String {
        String(format: t.jellystatWindowDaysFormat, days)
    }
}

private enum WakapiDateParser {
    static func fractionalFormatter() -> ISO8601DateFormatter {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter
    }

    static func regularFormatter() -> ISO8601DateFormatter {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }

    static func plainDateFormatter() -> DateFormatter {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .iso8601)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }

    static func parse(_ value: String?) -> Date? {
        guard let value, !value.isEmpty else { return nil }
        return fractionalFormatter().date(from: value)
            ?? regularFormatter().date(from: value)
            ?? plainDateFormatter().date(from: value)
    }
}

struct WakapiDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var summary: WakapiSummary?
    @State private var recentActivity: WakapiDailySummariesResponse?
    @State private var state: LoadableState<Void> = .idle
    @State private var selectedInterval: WakapiInterval = .today
    @State private var activeFilter: WakapiSummaryFilter?

    private let wakapiColor = ServiceType.wakapi.colors.primary

    enum WakapiInterval: String, CaseIterable, Identifiable {
        case today = "today"
        case yesterday = "yesterday"
        case last7Days = "last_7_days"
        case last30Days = "last_30_days"
        case last6Months = "last_6_months"
        case lastYear = "last_year"
        case allTime = "all_time"

        var id: String { rawValue }

        func label(using t: Translations) -> String {
            switch self {
            case .today: return t.wakapiIntervalToday
            case .yesterday: return t.wakapiIntervalYesterday
            case .last7Days: return t.wakapiIntervalLast7Days
            case .last30Days: return t.wakapiIntervalLast30Days
            case .last6Months: return t.wakapiIntervalLast6Months
            case .lastYear: return t.wakapiIntervalLastYear
            case .allTime: return t.wakapiIntervalAllTime
            }
        }
    }

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .wakapi,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: refreshAll
        ) {
            instancePicker
            intervalPicker
            activeFilterBanner

            if let summary {
                overviewCard(summary)

                if let snapshot = activitySnapshot {
                    activityTrendCard(snapshot)
                    activityHeatmapCard(snapshot)
                }

                if let langs = summary.languages, !langs.isEmpty {
                    statListCard(
                        title: localizer.t.wakapiSectionLanguages,
                        icon: "curlybraces",
                        items: langs,
                        filterDimension: .language
                    )
                }

                if let projects = summary.projects, !projects.isEmpty {
                    statListCard(
                        title: localizer.t.wakapiSectionProjects,
                        icon: "folder.fill",
                        items: projects,
                        filterDimension: .project
                    )
                }

                if let editors = summary.editors, !editors.isEmpty {
                    statListCard(
                        title: localizer.t.wakapiSectionEditors,
                        icon: "keyboard.fill",
                        items: editors,
                        filterDimension: .editor
                    )
                }

                if let machines = summary.machines, !machines.isEmpty {
                    statListCard(
                        title: localizer.t.wakapiSectionMachines,
                        icon: "desktopcomputer",
                        items: machines,
                        filterDimension: .machine
                    )
                }

                if let oses = summary.operatingSystems, !oses.isEmpty {
                    statListCard(
                        title: localizer.t.wakapiSectionOperatingSystems,
                        icon: "laptopcomputer",
                        items: oses,
                        filterDimension: .operatingSystem
                    )
                }

                if let labels = summary.labels, !labels.isEmpty {
                    statListCard(
                        title: localizer.t.wakapiSectionLabels,
                        icon: "tag.fill",
                        items: labels,
                        filterDimension: .label
                    )
                }

                if let categories = summary.categories, !categories.isEmpty {
                    statListCard(
                        title: localizer.t.wakapiSectionCategories,
                        icon: "square.grid.2x2.fill",
                        items: categories
                    )
                }

                if let branches = summary.branches, !branches.isEmpty {
                    statListCard(
                        title: localizer.t.wakapiSectionBranches,
                        icon: "point.topleft.down.curvedto.point.bottomright.up",
                        items: branches
                    )
                }
            }
        }
        .navigationTitle(localizer.t.serviceWakapi)
        .task(id: fetchTaskKey) {
            await fetchSummary()
        }
        .task(id: activityTaskKey) {
            await fetchRecentActivity()
        }
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .wakapi)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .wakapi)
                            summary = nil
                            recentActivity = nil
                            activeFilter = nil
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? wakapiColor : AppTheme.textMuted.opacity(0.4))
                                    .frame(width: 10, height: 10)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(instance.displayLabel)
                                        .font(.subheadline.weight(.semibold))
                                    Text(instance.url)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                            }
                            .padding(14)
                            .glassCard(tint: instance.id == selectedInstanceId ? wakapiColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var intervalPicker: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(WakapiInterval.allCases) { interval in
                    Button {
                        HapticManager.light()
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                            selectedInterval = interval
                        }
                    } label: {
                        Text(interval.label(using: localizer.t))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(selectedInterval == interval ? .white : AppTheme.textSecondary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background {
                                if selectedInterval == interval {
                                    Capsule(style: .continuous)
                                        .fill(wakapiColor)
                                } else {
                                    Capsule(style: .continuous)
                                        .fill(AppTheme.surface.opacity(0.75))
                                }
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 4)
        }
    }

    @ViewBuilder
    private var activeFilterBanner: some View {
        if let activeFilter {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(localizer.t.wakapiActiveFilter)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)
                    Text(activeFilter.value)
                        .font(.subheadline.weight(.semibold))
                }

                Spacer()

                Button(localizer.t.wakapiClearFilter) {
                    HapticManager.light()
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                        self.activeFilter = nil
                        self.recentActivity = nil
                    }
                }
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(AppTheme.surface.opacity(0.8), in: Capsule())
                .buttonStyle(.plain)
            }
            .padding(16)
            .glassCard(tint: wakapiColor.opacity(0.08))
        }
    }

    private func overviewCard(_ summary: WakapiSummary) -> some View {
        let total = summary.effectiveGrandTotal

        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    Text(localizer.t.wakapiTotalTimeCoded)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    HStack(alignment: .firstTextBaseline, spacing: 4) {
                        Text("\(total.hours ?? 0)")
                            .font(.system(size: 38, weight: .bold))
                            .foregroundStyle(wakapiColor)
                        Text(localizer.t.unitHours)
                            .font(.headline)
                            .foregroundStyle(AppTheme.textSecondary)
                        Text("\(total.minutes ?? 0)")
                            .font(.system(size: 38, weight: .bold))
                            .foregroundStyle(wakapiColor)
                        Text(localizer.t.unitMinutes)
                            .font(.headline)
                            .foregroundStyle(AppTheme.textSecondary)
                    }

                    if let text = total.text ?? total.digital {
                        Text(text)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }

                Spacer()

                Image(systemName: "timer")
                    .font(.system(size: 40))
                    .foregroundStyle(wakapiColor.opacity(0.2))
            }

            Divider()

            HStack(spacing: 10) {
                summaryPill(
                    title: localizer.t.wakapiIntervalLabel,
                    value: selectedInterval.label(using: localizer.t)
                )

                if let activeFilter {
                    summaryPill(
                        title: localizer.t.wakapiActiveFilter,
                        value: activeFilter.value
                    )
                }
            }
        }
        .padding(20)
        .glassCard(tint: colorScheme == .dark ? wakapiColor.opacity(0.11) : wakapiColor.opacity(0.055))
    }

    private func activityTrendCard(_ snapshot: WakapiActivitySnapshot) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(localizer.t.wakapiRecentActivity)
                        .font(.headline)
                    Text(localizer.t.wakapiLast30DaysWindow)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }

                Spacer()

                Text(recentActivity?.dailyAverage?.textIncludingOtherLanguage ?? recentActivity?.dailyAverage?.text ?? GrandTotal.format(totalSeconds: snapshot.averageSeconds))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(wakapiColor)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(wakapiColor.opacity(0.12), in: Capsule())
            }

            Chart {
                ForEach(snapshot.recentPoints) { point in
                    BarMark(
                        x: .value("Day", point.date, unit: .day),
                        y: .value("Hours", point.totalHours)
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
                    .foregroundStyle(point.date == snapshot.bestDay?.date ? wakapiColor : wakapiColor.opacity(0.42))
                }

                if snapshot.averageSeconds > 0 {
                    RuleMark(y: .value("Average", snapshot.averageSeconds / 3600))
                        .foregroundStyle(AppTheme.textMuted.opacity(0.5))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 5]))
                }
            }
            .frame(height: 190)
            .chartYAxis {
                AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { _ in
                    AxisGridLine(stroke: StrokeStyle(lineWidth: 0.75))
                        .foregroundStyle(AppTheme.textMuted.opacity(0.12))
                    AxisValueLabel()
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
            .chartXAxis {
                AxisMarks(values: .stride(by: .day, count: 7)) { value in
                    AxisValueLabel {
                        if let date = value.as(Date.self) {
                            Text(activityAxisLabel(for: date))
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }
                }
            }

            HStack(spacing: 10) {
                activityMetricCard(
                    title: localizer.t.wakapiAveragePerDay,
                    value: GrandTotal.format(totalSeconds: snapshot.averageSeconds)
                )
                activityMetricCard(
                    title: localizer.t.jellystatActiveDays,
                    value: "\(snapshot.activeDays)",
                    caption: WakapiActivityCaptionFormatter.recentWindowLabel(days: snapshot.recentPoints.count, using: localizer.t)
                )
                activityMetricCard(
                    title: localizer.t.wakapiBestDay,
                    value: snapshot.bestDay.map { GrandTotal.format(totalSeconds: $0.totalSeconds) } ?? localizer.t.wakapiNoRecentActivity,
                    caption: snapshot.bestDay.map(activityDateLabel(for:))
                )
            }
        }
        .padding(18)
        .glassCard(tint: colorScheme == .dark ? wakapiColor.opacity(0.1) : wakapiColor.opacity(0.05))
    }

    private func activityHeatmapCard(_ snapshot: WakapiActivitySnapshot) -> some View {
        let colors = colorScheme == .dark ? wakapiHeatmapColorsDark : wakapiHeatmapColorsLight

        return VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(localizer.t.wakapiActivityHeatmapTitle)
                    .font(.headline)
                Text(localizer.t.wakapiLast20WeeksWindow)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }

            let cellSize: CGFloat = 12
            let cellSpacing: CGFloat = 3
            let gridWidth = CGFloat(snapshot.heatmapWeeks.count) * cellSize + CGFloat(max(snapshot.heatmapWeeks.count - 1, 0)) * cellSpacing
            let gridHeight = CGFloat(7) * cellSize + CGFloat(6) * cellSpacing
            let legendWidth: CGFloat = 34

            GeometryReader { proxy in
                let availableWidth = max(proxy.size.width - legendWidth - 12, 0)
                HStack(alignment: .top, spacing: 12) {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(alignment: .top, spacing: cellSpacing) {
                            ForEach(Array(snapshot.heatmapWeeks.enumerated()), id: \.offset) { _, week in
                                VStack(spacing: cellSpacing) {
                                    ForEach(Array(week.enumerated()), id: \.offset) { _, day in
                                        RoundedRectangle(cornerRadius: 3, style: .continuous)
                                            .fill(colors[day.level])
                                            .frame(width: cellSize, height: cellSize)
                                    }
                                }
                            }
                        }
                        .frame(width: max(gridWidth, availableWidth), alignment: .center)
                    }
                    .frame(height: gridHeight)
                    .frame(maxWidth: availableWidth, alignment: .leading)

                    VStack(spacing: 4) {
                        Text(localizer.t.giteaLessActive)
                        ForEach(Array(colors.enumerated()), id: \.offset) { _, color in
                            RoundedRectangle(cornerRadius: 2)
                                .fill(color)
                                .frame(width: 12, height: 12)
                        }
                        Text(localizer.t.giteaMoreActive)
                    }
                    .font(.system(size: 10))
                    .foregroundStyle(AppTheme.textMuted)
                    .frame(width: legendWidth)
                }
            }
            .frame(height: gridHeight)
        }
        .padding(18)
        .glassCard()
    }

    private func summaryPill(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)
            Text(value)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.surface.opacity(0.9), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func activityMetricCard(title: String, value: String, caption: String? = nil) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)
            Text(value)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(.primary)
            if let caption {
                Text(caption)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(AppTheme.surface.opacity(0.72), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func statListCard(
        title: String,
        icon: String,
        items: [StatItem],
        filterDimension: WakapiSummaryFilter.Dimension? = nil
    ) -> some View {
        let sectionTotalSeconds = items.reduce(0) { $0 + $1.effectiveTotalSeconds }

        return VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .foregroundColor(wakapiColor)
                Text(title)
                    .font(.headline)
                Spacer()
            }

            VStack(spacing: 12) {
                ForEach(items.prefix(5)) { item in
                    statRow(
                        item: item,
                        filterDimension: filterDimension,
                        sectionTotalSeconds: sectionTotalSeconds
                    )
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    @ViewBuilder
    private func statRow(
        item: StatItem,
        filterDimension: WakapiSummaryFilter.Dimension?,
        sectionTotalSeconds: Double
    ) -> some View {
        let row = statRowContent(item: item, sectionTotalSeconds: sectionTotalSeconds)

        if let filterDimension, let name = item.displayName, !name.isEmpty {
            Button {
                HapticManager.light()
                withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                    activeFilter = WakapiSummaryFilter(dimension: filterDimension, value: name)
                    recentActivity = nil
                }
            } label: {
                row
            }
            .buttonStyle(.plain)
        } else {
            row
        }
    }

    private func statRowContent(item: StatItem, sectionTotalSeconds: Double) -> some View {
        let percent = item.resolvedPercent(sectionTotalSeconds: sectionTotalSeconds)

        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(item.displayName ?? localizer.t.unknown)
                    .font(.subheadline)
                    .lineLimit(1)
                Spacer()
                Text(item.displayText ?? formatTime(hours: item.effectiveHours, minutes: item.effectiveMinutes))
                    .font(.caption.monospacedDigit())
                    .foregroundStyle(AppTheme.textSecondary)
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(AppTheme.surface)
                    Capsule()
                        .fill(wakapiColor.opacity(0.8))
                        .frame(width: geo.size.width * CGFloat(percent / 100))
                }
            }
            .frame(height: 6)
        }
    }

    private func formatTime(hours: Int?, minutes: Int?) -> String {
        let h = hours ?? 0
        let m = minutes ?? 0
        if h > 0 {
            return "\(h)\(localizer.t.unitHours) \(m)\(localizer.t.unitMinutes)"
        } else {
            return "\(m)\(localizer.t.unitMinutes)"
        }
    }

    private func activityAxisLabel(for date: Date) -> String {
        let formatter = DateFormatter()
        formatter.setLocalizedDateFormatFromTemplate("MMM d")
        return formatter.string(from: date)
    }

    private func activityDateLabel(for point: WakapiActivityPoint) -> String {
        activityAxisLabel(for: point.date)
    }

    private var fetchTaskKey: String {
        "\(selectedInstanceId.uuidString)-\(selectedInterval.rawValue)-\(activeFilter?.cacheKey ?? "none")"
    }

    private var activityTaskKey: String {
        "\(selectedInstanceId.uuidString)-activity-\(activeFilter?.cacheKey ?? "none")"
    }

    private var activitySnapshot: WakapiActivitySnapshot? {
        guard let recentActivity else { return nil }
        return buildActivitySnapshot(from: recentActivity)
    }

    private func fetchSummary(forceRefresh: Bool = false) async {
        do {
            if summary == nil {
                state = .loading
            }

            guard let client = await servicesStore.wakapiClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            let response = try await client.getSummary(
                interval: selectedInterval.rawValue,
                filter: activeFilter,
                forceRefresh: forceRefresh
            )

            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                self.summary = response
                self.state = .loaded(())
            }
        } catch let apiError as APIError {
            if summary == nil {
                state = .error(apiError)
            } else {
                state = .loaded(())
            }
        } catch {
            if summary == nil {
                state = .error(.custom(error.localizedDescription))
            } else {
                state = .loaded(())
            }
        }
    }

    private func fetchRecentActivity(forceRefresh: Bool = false) async {
        guard let client = await servicesStore.wakapiClient(instanceId: selectedInstanceId) else {
            recentActivity = nil
            return
        }

        do {
            let response = try await client.getDailySummaries(
                filter: activeFilter,
                forceRefresh: forceRefresh
            )
            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                recentActivity = response
            }
        } catch {
            recentActivity = nil
        }
    }

    private func refreshAll() async {
        async let summaryTask: Void = fetchSummary(forceRefresh: true)
        async let activityTask: Void = fetchRecentActivity(forceRefresh: true)
        _ = await (summaryTask, activityTask)
    }

    private func buildActivitySnapshot(from response: WakapiDailySummariesResponse) -> WakapiActivitySnapshot? {
        let calendar = Calendar.current
        let points = response.data.compactMap { entry -> WakapiActivityPoint? in
            guard
                let parsedDate = WakapiDateParser.parse(entry.range?.start)
                    ?? WakapiDateParser.parse(entry.range?.date)
                    ?? WakapiDateParser.parse(entry.range?.end)
            else {
                return nil
            }

            return WakapiActivityPoint(
                date: calendar.startOfDay(for: parsedDate),
                totalSeconds: max(entry.totalSeconds, 0.0)
            )
        }
        .sorted { $0.date < $1.date }

        guard !points.isEmpty else { return nil }

        let recentPoints = Array(points.suffix(30))
        let averageSeconds = recentPoints.isEmpty ? 0 : recentPoints.reduce(0) { $0 + $1.totalSeconds } / Double(recentPoints.count)
        let activeDays = recentPoints.filter { $0.totalSeconds > 0 }.count
        let bestDay = recentPoints.max { $0.totalSeconds < $1.totalSeconds }

        return WakapiActivitySnapshot(
            recentPoints: recentPoints,
            averageSeconds: averageSeconds,
            activeDays: activeDays,
            bestDay: bestDay,
            heatmapWeeks: buildHeatmapWeeks(from: points)
        )
    }

    private func buildHeatmapWeeks(from points: [WakapiActivityPoint]) -> [[WakapiHeatmapCell]] {
        let calendar = Calendar.current
        let totalsByDay = Dictionary(grouping: points, by: { calendar.startOfDay(for: $0.date) })
            .mapValues { $0.reduce(0) { $0 + $1.totalSeconds } }
        let maxTotal = max(totalsByDay.values.max() ?? 0.0, 1.0)
        let today = calendar.startOfDay(for: Date())
        let dayOfWeek = calendar.component(.weekday, from: today) - 1
        let weeksToShow = 20
        let totalDays = weeksToShow * 7 + dayOfWeek + 1
        let startDate = calendar.date(byAdding: .day, value: -(totalDays - 1), to: today) ?? today

        var weeks: [[WakapiHeatmapCell]] = []
        var currentWeek: [WakapiHeatmapCell] = []

        for offset in 0..<totalDays {
            guard let date = calendar.date(byAdding: .day, value: offset, to: startDate) else { continue }
            let day = calendar.startOfDay(for: date)
            let total = totalsByDay[day] ?? 0
            let level: Int
            if total <= 0 {
                level = 0
            } else {
                let ratio = total / maxTotal
                if ratio <= 0.25 {
                    level = 1
                } else if ratio <= 0.5 {
                    level = 2
                } else if ratio <= 0.75 {
                    level = 3
                } else {
                    level = 4
                }
            }

            currentWeek.append(WakapiHeatmapCell(level: level, totalSeconds: total))
            if currentWeek.count == 7 {
                weeks.append(currentWeek)
                currentWeek.removeAll(keepingCapacity: true)
            }
        }

        if !currentWeek.isEmpty {
            weeks.append(currentWeek)
        }

        return weeks
    }
}
