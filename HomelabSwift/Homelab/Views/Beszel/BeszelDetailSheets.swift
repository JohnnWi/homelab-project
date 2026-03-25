import SwiftUI
import UIKit
import Charts

private let beszelColor = Color(hex: "#0EA5E9")

// MARK: - Extra Metric Detail Sheet

struct ExtraMetricDetailSheet: View {
    let metricType: ExtraMetricType
    let records: [BeszelRecordStats]
    let localizer: Localizer

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch metricType {
                    case .temperature: temperatureContent
                    case .load: loadContent
                    case .network: networkContent
                    case .diskIO: diskIOContent
                    case .battery: batteryContent
                    case .swap: swapContent
                    }
                }
                .padding()
            }
            .background(AppTheme.background)
            .navigationTitle(sheetTitle)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    private var sheetTitle: String {
        switch metricType {
        case .temperature: return localizer.t.beszelTemperature
        case .load: return localizer.t.beszelLoadAverage
        case .network: return localizer.t.beszelNetworkTraffic
        case .diskIO: return localizer.t.beszelDiskIO
        case .battery: return localizer.t.beszelBattery
        case .swap: return localizer.t.beszelSwap
        }
    }

    // MARK: - Temperature

    @ViewBuilder
    private var temperatureContent: some View {
        let data = records.compactMap(\.maxTempCelsius)
        if data.count >= 2 {
            SmoothLineGraph(data: data, graphColor: .orange, height: 150,
                           labelFormatter: { "\(Int($0))°C" })
        }
        if let latest = records.last {
            let sensors = latest.temperatureSensors
            if !sensors.isEmpty {
                VStack(spacing: 0) {
                    ForEach(sensors.sorted(by: { $0.key < $1.key }), id: \.key) { name, temp in
                        HStack {
                            Text(name).font(.subheadline)
                            Spacer()
                            Text("\(Int(temp))°C")
                                .font(.subheadline.bold())
                                .foregroundStyle(temp > 80 ? .red : temp > 60 ? .orange : .green)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                    }
                }
                .glassCard()
            }
        }
    }

    // MARK: - Load Average

    @ViewBuilder
    private var loadContent: some View {
        let la1 = records.compactMap { $0.loadAvgValues.first }
        let la5 = records.compactMap { ($0.loadAvgValues.count > 1) ? $0.loadAvgValues[1] : nil }
        if la1.count >= 2 {
            SmoothLineGraph(data: la1, secondaryData: la5.count == la1.count ? la5 : nil, graphColor: beszelColor, secondaryColor: .purple,
                           height: 150, labelFormatter: { String(format: "1m: %.2f", $0) },
                           secondaryLabelFormatter: { String(format: "5m: %.2f", $0) })
        }
        if let latest = records.last, !latest.loadAvgValues.isEmpty {
            let labels = ["1 min", "5 min", "15 min"]
            VStack(spacing: 0) {
                ForEach(Array(zip(labels, latest.loadAvgValues)), id: \.0) { label, value in
                    HStack {
                        Text(label).font(.subheadline)
                        Spacer()
                        Text(String(format: "%.2f", value)).font(.subheadline.bold())
                    }
                    .padding(.horizontal, 16).padding(.vertical, 10)
                }
            }
            .glassCard()
        }
    }

    // MARK: - Network

    @ViewBuilder
    private var networkContent: some View {
        let up = records.compactMap { $0.bandwidthUpBytesPerSec }
        let down = records.compactMap { $0.bandwidthDownBytesPerSec }
        if up.count >= 2 {
            SmoothLineGraph(
                data: up,
                secondaryData: down.count == up.count ? down : nil,
                graphColor: .green,
                secondaryColor: beszelColor,
                height: 150,
                labelFormatter: { "\(localizer.t.beszelUpload): \(BeszelFormatters.formatBytesRate($0))" },
                secondaryLabelFormatter: { "\(localizer.t.beszelDownload): \(BeszelFormatters.formatBytesRate($0))" }
            )
        }
        if let latest = records.last {
            let interfaces = latest.networkInterfaces
            if !interfaces.isEmpty {
                Text(localizer.t.beszelNetworkInterfaces)
                    .font(.subheadline.bold())
                    .padding(.horizontal, 4)

                VStack(spacing: 0) {
                    ForEach(interfaces.sorted(by: { $0.key < $1.key }), id: \.key) { name, iface in
                        HStack {
                            Text(name).font(.subheadline.weight(.medium))
                            Spacer()
                            VStack(alignment: .trailing, spacing: 2) {
                                Text("\(localizer.t.beszelUpload) \(BeszelFormatters.formatBytesRate(iface.uploadRateBytesPerSec))")
                                    .font(.caption).foregroundStyle(.green)
                                Text("\(localizer.t.beszelDownload) \(BeszelFormatters.formatBytesRate(iface.downloadRateBytesPerSec))")
                                    .font(.caption).foregroundStyle(beszelColor)
                                if let totalDown = iface.downloadTotalBytes {
                                    Text("\(localizer.t.beszelTotalDownload) \(BeszelFormatters.formatBytes(totalDown))")
                                        .font(.caption2).foregroundStyle(.secondary)
                                }
                                if let totalUp = iface.uploadTotalBytes {
                                    Text("\(localizer.t.beszelTotalUpload) \(BeszelFormatters.formatBytes(totalUp))")
                                        .font(.caption2).foregroundStyle(.secondary)
                                }
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 10)
                    }
                }
                .glassCard()
            }
        }
    }

    // MARK: - Disk I/O

    @ViewBuilder
    private var diskIOContent: some View {
        let read = records.compactMap { $0.diskReadBytesPerSec }
        let write = records.compactMap { $0.diskWriteBytesPerSec }
        if read.count >= 2 {
            SmoothLineGraph(
                data: read,
                secondaryData: write.count == read.count ? write : nil,
                graphColor: .green,
                secondaryColor: .orange,
                height: 150,
                labelFormatter: { "\(localizer.t.beszelRead): \(BeszelFormatters.formatBytesRate($0))" },
                secondaryLabelFormatter: { "\(localizer.t.beszelWrite): \(BeszelFormatters.formatBytesRate($0))" }
            )
        }
    }

    // MARK: - Battery

    @ViewBuilder
    private var batteryContent: some View {
        let levels = records.compactMap(\.batteryLevel).map { Double($0) }
        if levels.count >= 2 {
            SmoothLineGraph(data: levels, graphColor: .green, height: 150,
                           labelFormatter: { "\(Int($0))%" })
        }
        if let latest = records.last {
            VStack(spacing: 0) {
                if let level = latest.batteryLevel {
                    detailRow(localizer.t.beszelLevel, "\(level)%")
                }
                if let mins = latest.batteryMinutes {
                    detailRow(localizer.t.beszelRemaining, "\(mins) min")
                }
            }
            .glassCard()
        }
    }

    // MARK: - Swap

    @ViewBuilder
    private var swapContent: some View {
        let used = records.compactMap(\.swapUsedGb)
        if used.count >= 2 {
            SmoothLineGraph(data: used, graphColor: .purple, height: 150,
                           labelFormatter: { BeszelFormatters.formatGB($0) })
        }
        if let latest = records.last {
            VStack(spacing: 0) {
                if let total = latest.swapTotalGb {
                    detailRow(localizer.t.beszelTotal, BeszelFormatters.formatGB(total))
                }
                if let used = latest.swapUsedGb {
                    detailRow(localizer.t.beszelUsed, BeszelFormatters.formatGB(used))
                }
            }
            .glassCard()
        }
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.subheadline)
            Spacer()
            Text(value).font(.subheadline.bold())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

// MARK: - CPU Detail Sheet

struct CpuDetailSheet: View {
    let records: [BeszelRecordStats]
    let localizer: Localizer

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    let data = records.compactMap(\.cpu)
                    if data.count >= 2 {
                        SmoothLineGraph(data: data, graphColor: beszelColor, height: 150,
                                       labelFormatter: { String(format: "%.1f%%", $0) })
                    }

                    if let latest = records.last, !latest.cpuBreakdownValues.isEmpty {
                        let labels = [
                            localizer.t.beszelCpuUser,
                            localizer.t.beszelCpuSystem,
                            localizer.t.beszelCpuNice,
                            localizer.t.beszelCpuWait,
                            localizer.t.beszelCpuIdle
                        ]
                        let colors: [Color] = [beszelColor, .orange, .green, .red, .gray]
                        Text(localizer.t.beszelCpuBreakdown)
                            .font(.subheadline.bold())
                            .padding(.horizontal, 4)
                        VStack(spacing: 0) {
                            ForEach(Array(zip(labels, latest.cpuBreakdownValues).enumerated()), id: \.offset) { _, pair in
                                let (label, value) = pair
                                let color = colors[min(labels.firstIndex(of: label) ?? 0, colors.count - 1)]
                                HStack {
                                    Circle().fill(color).frame(width: 8, height: 8)
                                    Text(label).font(.subheadline)
                                    Spacer()
                                    Text(String(format: "%.1f%%", value)).font(.subheadline.bold())
                                }
                                .padding(.horizontal, 16).padding(.vertical, 8)
                            }
                        }
                        .glassCard()
                    }
                }
                .padding()
            }
            .background(AppTheme.background)
            .navigationTitle(localizer.t.beszelCpu)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }
}

// MARK: - GPU Detail Sheet

struct GpuDetailSheet: View {
    let metricType: GpuMetricType
    let records: [BeszelRecordStats]
    let localizer: Localizer

    private var gpuKey: String { metricType.gpuKey }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let gpuName = records.last?.g?[gpuKey]?.n {
                        Text(gpuName)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }

                    let data: [Double]
                    let color: Color
                    let formatter: (Double) -> String

                    switch metricType {
                    case .usage:
                        let _ = (data = records.compactMap { $0.gpuUsagePercent(forKey: gpuKey) },
                                 color = .green,
                                 formatter = { String(format: "%.1f%%", $0) })
                    case .power:
                        let _ = (data = records.compactMap { $0.gpuPowerWatts(forKey: gpuKey) },
                                 color = .orange,
                                 formatter = { String(format: "%.0fW", $0) })
                    case .vram:
                        let _ = (data = records.compactMap { $0.gpuVramPercent(forKey: gpuKey) },
                                 color = .purple,
                                 formatter = { String(format: "%.1f%%", $0) })
                    }

                    if data.count >= 2 {
                        SmoothLineGraph(data: data, graphColor: color, height: 150,
                                       labelFormatter: formatter)
                    }
                }
                .padding()
            }
            .background(AppTheme.background)
            .navigationTitle(sheetTitle)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    private var sheetTitle: String {
        switch metricType {
        case .usage: return localizer.t.beszelGpuUsage
        case .power: return localizer.t.beszelGpuPower
        case .vram: return localizer.t.beszelGpuVram
        }
    }
}

// MARK: - Shared Sheet Header

private struct SheetHeader: View {
    let title: String
    let subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.headline.bold())
            if let subtitle {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 4)
    }
}

// MARK: - Resource Metric Detail Sheet

struct ResourceMetricDetailSheet: View {
    let metricType: ResourceMetricType
    let records: [BeszelRecordStats]
    let localizer: Localizer

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SheetHeader(title: sheetTitle, subtitle: nil)

                    let (data, formatter, color): ([Double], (Double) -> String, Color) = {
                        switch metricType {
                        case .memory:
                            return (
                                records.compactMap(\.memoryUsedGb),
                                { BeszelFormatters.formatGB($0) },
                                .purple
                            )
                        case .cpu:
                            return (
                                records.compactMap(\.cpu),
                                { String(format: "%.1f%%", $0) },
                                beszelColor
                            )
                        }
                    }()

                    if data.count >= 2 {
                        SmoothLineGraph(
                            data: data,
                            graphColor: color,
                            height: 150,
                            labelFormatter: formatter
                        )
                    }

                    if metricType == .memory, let latest = records.last {
                        VStack(spacing: 0) {
                            if let used = latest.memoryUsedGb {
                                infoRow(localizer.t.beszelUsedMemory, BeszelFormatters.formatGB(used))
                            }
                            if let total = latest.memoryTotalGb {
                                infoRow(localizer.t.beszelTotalMemory, BeszelFormatters.formatGB(total))
                            }
                        }
                        .glassCard()
                    }
                }
                .padding()
            }
            .background(AppTheme.background)
            .navigationTitle(sheetTitle)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    private var sheetTitle: String {
        switch metricType {
        case .cpu: return localizer.t.beszelCpu
        case .memory: return localizer.t.beszelMemoryUsage
        }
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.subheadline)
            Spacer()
            Text(value).font(.subheadline.bold())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

// MARK: - Container Detail Sheet

struct BeszelContainerDetailSheet: View {
    let container: BeszelContainerRecord
    let instanceId: UUID
    let systemId: String
    let containerStats: [BeszelContainerStatsRecord]

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedTab: ContainerDetailTab = .info
    @State private var logsState: LoadState = .idle
    @State private var detailsState: LoadState = .idle

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker(localizer.t.beszelContainerInfo, selection: $selectedTab) {
                    ForEach(ContainerDetailTab.allCases, id: \.self) { tab in
                        Text(tab.title(localizer: localizer)).tag(tab)
                    }
                }
                .pickerStyle(.segmented)
                .padding()

                switch selectedTab {
                case .info:
                    infoTab
                case .logs:
                    logsTab
                case .details:
                    detailsTab
                }
            }
            .navigationTitle(container.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if selectedTab == .logs || selectedTab == .details {
                    ToolbarItemGroup(placement: .topBarTrailing) {
                        Button {
                            if let text = currentLoadedText {
                                UIPasteboard.general.string = text
                            }
                        } label: {
                            Image(systemName: "doc.on.doc")
                        }
                        .disabled(currentLoadedText == nil)

                        Button {
                            Task {
                                if selectedTab == .logs {
                                    await fetchLogs()
                                } else {
                                    await fetchDetails()
                                }
                            }
                        } label: {
                            Image(systemName: "arrow.clockwise")
                        }
                        .disabled(selectedTab == .logs ? logsState == .loading : detailsState == .loading)
                    }
                }
            }
            .onChange(of: selectedTab) { _, newValue in
                if newValue == .logs, logsState == .idle {
                    Task { await fetchLogs() }
                } else if newValue == .details, detailsState == .idle {
                    Task { await fetchDetails() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Info Tab

    private var infoTab: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    if let health = container.health, health != .none {
                        Text(healthLabel(health))
                            .font(.caption2.weight(.medium))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(healthColor(health).opacity(0.15), in: Capsule())
                            .foregroundStyle(healthColor(health))
                    }
                    if let status = container.status, !status.isEmpty {
                        Text(status)
                            .font(.caption2.weight(.medium))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(AppTheme.textSecondary.opacity(0.12), in: Capsule())
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    Spacer()
                }

                if let image = container.image, !image.isEmpty {
                    Label(image, systemImage: "shippingbox")
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                        .lineLimit(2)
                        .truncationMode(.middle)
                }

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    ExtraMetricChip(
                        icon: "cpu",
                        label: localizer.t.beszelCpu,
                        value: String(format: "%.1f%%", container.cpuValue),
                        color: beszelColor
                    )
                    ExtraMetricChip(
                        icon: "memorychip",
                        label: localizer.t.beszelRam,
                        value: BeszelFormatters.formatMB(container.memoryValue),
                        color: .purple
                    )
                    if container.netValue > 0 {
                        ExtraMetricChip(
                            icon: "network",
                            label: localizer.t.beszelNetworkTraffic,
                            value: BeszelFormatters.formatBytesRate(container.netValue),
                            color: .green
                        )
                    }
                }

                if cpuChartData.count >= 2 {
                    ContainerDetailLineChart(
                        title: localizer.t.beszelCpu,
                        data: cpuChartData,
                        color: beszelColor,
                        formatY: { String(format: "%.1f%%", $0) }
                    )
                }

                if memoryChartData.count >= 2 {
                    ContainerDetailLineChart(
                        title: localizer.t.beszelRam,
                        data: memoryChartData,
                        color: .purple,
                        formatY: { BeszelFormatters.formatMB($0) }
                    )
                }
            }
            .padding()
        }
        .background(AppTheme.background)
    }

    // MARK: - Chart Data

    private var cpuChartData: [ContainerDetailChartPoint] {
        extractChartData { $0.cpu }
    }

    private var memoryChartData: [ContainerDetailChartPoint] {
        extractChartData { $0.memory }
    }

    private func extractChartData(value: (BeszelContainerStat) -> Double) -> [ContainerDetailChartPoint] {
        let sorted = containerStats.sorted { ($0.created ?? "") < ($1.created ?? "") }
        return sorted.compactMap { record in
            guard let date = Self.parseDate(record.created),
                  let stat = record.stats.first(where: { $0.name == container.name }) else { return nil }
            return ContainerDetailChartPoint(date: date, value: max(0, value(stat)))
        }
    }

    // MARK: - Logs & Details Tabs

    private var logsTab: some View {
        contentTab(state: logsState, emptyMessage: localizer.t.detailNoLogs) { text in
            LogHighlightView(text: text)
        }
    }

    private var detailsTab: some View {
        contentTab(state: detailsState, emptyMessage: localizer.t.noData) { text in
            JSONHighlightView(text: Self.formatDetails(text))
        }
    }

    private func contentTab(state: LoadState, emptyMessage: String, content: @escaping (String) -> some View) -> some View {
        Group {
            switch state {
            case .idle, .loading:
                centeredTab {
                    ProgressView()
                }
            case .error(let message):
                centeredTab {
                    VStack(spacing: 8) {
                        Text(localizer.t.error)
                            .font(.headline)
                        Text(message)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            case .empty:
                centeredTab {
                    Text(emptyMessage)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            case .loaded(let text):
                GeometryReader { geometry in
                    ScrollView([.vertical, .horizontal], showsIndicators: true) {
                        content(text)
                            .padding(12)
                            .frame(minWidth: geometry.size.width - 32, minHeight: geometry.size.height, alignment: .topLeading)
                    }
                }
                .background(AppTheme.surface)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .padding()
            }
        }
    }

    private func centeredTab<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack {
            Spacer()
            content()
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var currentLoadedText: String? {
        switch selectedTab {
        case .logs:
            if case .loaded(let text) = logsState {
                return text
            }
        case .details:
            if case .loaded(let text) = detailsState {
                return text
            }
        case .info:
            break
        }
        return nil
    }

    // MARK: - Fetching

    private func fetchLogs() async {
        await MainActor.run { logsState = .loading }
        guard let client = await servicesStore.beszelClient(instanceId: instanceId) else {
            await MainActor.run { logsState = .error(localizer.t.error) }
            return
        }
        do {
            let rawLogs = try await client.getContainerLogs(systemId: systemId, containerId: container.id)
            let formattedLogs = Self.formatLogs(rawLogs)
            await MainActor.run {
                logsState = formattedLogs.isEmpty ? .empty : .loaded(formattedLogs)
            }
        } catch {
            await MainActor.run { logsState = .error(error.localizedDescription) }
        }
    }

    private func fetchDetails() async {
        await MainActor.run { detailsState = .loading }
        guard let client = await servicesStore.beszelClient(instanceId: instanceId) else {
            await MainActor.run { detailsState = .error(localizer.t.error) }
            return
        }
        do {
            let details = try await client.getContainerInfo(systemId: systemId, containerId: container.id)
            await MainActor.run {
                detailsState = details.isEmpty ? .empty : .loaded(details)
            }
        } catch {
            await MainActor.run { detailsState = .error(error.localizedDescription) }
        }
    }

    // MARK: - Log Formatting

    private static func formatLogs(_ rawLogs: String) -> String {
        if let data = rawLogs.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let logsContent = json["logs"] as? String {
            return logsContent
                .replacingOccurrences(of: "\\n", with: "\n")
                .replacingOccurrences(of: "\\t", with: "\t")
                .replacingOccurrences(of: "\\\"", with: "\"")
                .replacingOccurrences(of: "\\/", with: "/")
        }

        if let data = rawLogs.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data),
           let prettyData = try? JSONSerialization.data(withJSONObject: json, options: [.prettyPrinted]),
           let prettyString = String(data: prettyData, encoding: .utf8) {
            return prettyString
        }

        return rawLogs
    }

    private static func formatDetails(_ raw: String) -> String {
        if let data = raw.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let info = json["info"] as? String,
           let infoData = info.data(using: .utf8),
           let infoJson = try? JSONSerialization.jsonObject(with: infoData),
           let prettyData = try? JSONSerialization.data(withJSONObject: infoJson, options: [.prettyPrinted, .sortedKeys]),
           let prettyString = String(data: prettyData, encoding: .utf8) {
            return prettyString
        }

        if let data = raw.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data),
           let prettyData = try? JSONSerialization.data(withJSONObject: json, options: [.prettyPrinted, .sortedKeys]),
           let prettyString = String(data: prettyData, encoding: .utf8) {
            return prettyString
        }

        return raw
            .replacingOccurrences(of: "\\n", with: "\n")
            .replacingOccurrences(of: "\\t", with: "\t")
            .replacingOccurrences(of: "\\\"", with: "\"")
            .replacingOccurrences(of: "\\/", with: "/")
    }

    // MARK: - Date Parsing

    private static let pbDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss.SSS'Z'"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    private static let pbDateFormatterNoMillis: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss'Z'"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    private static func parseDate(_ value: String?) -> Date? {
        guard let value else { return nil }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        if let date = formatter.date(from: value) { return date }
        if let date = pbDateFormatter.date(from: value) { return date }
        return pbDateFormatterNoMillis.date(from: value)
    }

    // MARK: - Helpers

    private func healthLabel(_ health: BeszelContainerHealth) -> String {
        switch health {
        case .none: return localizer.t.beszelHealthNone
        case .starting: return localizer.t.beszelHealthStarting
        case .healthy: return localizer.t.beszelHealthHealthy
        case .unhealthy: return localizer.t.beszelHealthUnhealthy
        }
    }

    private func healthColor(_ health: BeszelContainerHealth) -> Color {
        switch health {
        case .none: return AppTheme.textMuted
        case .starting: return AppTheme.warning
        case .healthy: return AppTheme.running
        case .unhealthy: return AppTheme.stopped
        }
    }

    private enum ContainerDetailTab: String, CaseIterable {
        case info
        case logs
        case details

        @MainActor
        func title(localizer: Localizer) -> String {
            switch self {
            case .info: return localizer.t.beszelContainerInfo
            case .logs: return localizer.t.beszelContainerLogs
            case .details: return localizer.t.beszelContainerDetails
            }
        }
    }

    private enum LoadState: Equatable {
        case idle
        case loading
        case empty
        case loaded(String)
        case error(String)
    }
}

// MARK: - Container Detail Chart

private struct ContainerDetailChartPoint: Identifiable {
    let date: Date
    let value: Double
    var id: TimeInterval { date.timeIntervalSince1970 }
}

private struct ContainerDetailLineChart: View {
    let title: String
    let data: [ContainerDetailChartPoint]
    let color: Color
    let formatY: (Double) -> String

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline.weight(.medium))

            let maxVal = max(data.map(\.value).max() ?? 0, 1)
            Chart(data) { point in
                LineMark(
                    x: .value("Time", point.date),
                    y: .value("Value", point.value)
                )
                .foregroundStyle(color)

                AreaMark(
                    x: .value("Time", point.date),
                    y: .value("Value", point.value)
                )
                .foregroundStyle(
                    LinearGradient(colors: [color.opacity(0.3), color.opacity(0.02)], startPoint: .top, endPoint: .bottom)
                )
            }
            .chartYScale(domain: 0...maxVal)
            .chartYAxis {
                AxisMarks(values: .automatic(desiredCount: 3)) { value in
                    AxisGridLine()
                    AxisValueLabel {
                        if let v = value.as(Double.self) {
                            Text(formatY(v)).font(.system(size: 9))
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                    AxisGridLine()
                    AxisValueLabel(format: .dateTime.hour().minute())
                }
            }
            .frame(height: 180)
        }
        .padding(16)
        .glassCard()
    }
}

// MARK: - Docker Detail Sheet

struct DockerDetailSheet: View {
    let metricType: DockerMetricType
    let records: [BeszelRecordStats]
    let localizer: Localizer

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SheetHeader(title: sheetTitle, subtitle: nil)

                    switch metricType {
                    case .cpu:
                        if cpuSeries.count >= 2 {
                            SmoothLineGraph(
                                data: cpuSeries,
                                graphColor: beszelColor,
                                height: 150,
                                labelFormatter: { String(format: "%.1f%%", $0) }
                            )
                        }
                    case .memory:
                        if memorySeries.count >= 2 {
                            SmoothLineGraph(
                                data: memorySeries,
                                graphColor: .purple,
                                height: 150,
                                labelFormatter: { BeszelFormatters.formatMB($0) }
                            )
                        }
                    case .network:
                        if hasNetworkSeries {
                            SmoothLineGraph(
                                data: downloadSeries,
                                secondaryData: uploadSeries,
                                graphColor: .green,
                                secondaryColor: .purple,
                                height: 150,
                                labelFormatter: { "\(localizer.t.beszelDownload): \(BeszelFormatters.formatBytesRate($0))" },
                                secondaryLabelFormatter: { "\(localizer.t.beszelUpload): \(BeszelFormatters.formatBytesRate($0))" }
                            )
                        } else if !downloadSeries.isEmpty {
                            SmoothLineGraph(
                                data: downloadSeries,
                                graphColor: .green,
                                height: 150,
                                labelFormatter: { "\(localizer.t.beszelDownload): \(BeszelFormatters.formatBytesRate($0))" }
                            )
                        }
                    }
                }
                .padding()
            }
            .background(AppTheme.background)
            .navigationTitle(sheetTitle)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    private var sheetTitle: String {
        switch metricType {
        case .cpu: return localizer.t.beszelDockerCpuUsage
        case .memory: return localizer.t.beszelDockerMemoryUsage
        case .network: return localizer.t.beszelDockerNetworkIO
        }
    }

    private var cpuSeries: [Double] {
        dockerSeries { $0.cpuValue }
    }

    private var memorySeries: [Double] {
        dockerSeries { $0.mValue }
    }

    private var uploadSeries: [Double] {
        dockerOptionalSeries { $0.bandwidthUpBytesPerSec }
    }

    private var downloadSeries: [Double] {
        dockerOptionalSeries { $0.bandwidthDownBytesPerSec }
    }

    private var hasNetworkSeries: Bool {
        !downloadSeries.isEmpty && downloadSeries.count == uploadSeries.count
    }

    private func dockerSeries(_ selector: (BeszelContainer) -> Double) -> [Double] {
        records.compactMap { stats in
            guard let containers = stats.dc, !containers.isEmpty else { return nil }
            return containers.reduce(0.0) { $0 + selector($1) }
        }
    }

    private func dockerOptionalSeries(_ selector: (BeszelContainer) -> Double?) -> [Double] {
        records.compactMap { stats in
            guard let containers = stats.dc, !containers.isEmpty else { return nil }
            let values = containers.compactMap(selector)
            guard !values.isEmpty else { return nil }
            return values.reduce(0.0, +)
        }
    }
}

// MARK: - Container Log / JSON Highlighting

struct MonospaceLinesView: View {
    let text: String
    let showLineNumbers: Bool

    var body: some View {
        let normalized = text.replacingOccurrences(of: "\r\n", with: "\n")
        let lines = normalized.split(separator: "\n", omittingEmptySubsequences: false)
        LazyVStack(alignment: .leading, spacing: 2) {
            ForEach(Array(lines.enumerated()), id: \.offset) { index, line in
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    if showLineNumbers {
                        Text(String(format: "%4d", index + 1))
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundStyle(.secondary)
                            .frame(minWidth: 36, alignment: .trailing)
                    }
                    Text(String(line))
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(.primary)
                        .fixedSize(horizontal: true, vertical: false)
                }
            }
        }
    }
}

struct JSONHighlightView: UIViewRepresentable {
    let text: String

    private static let maxDisplayLength = 100_000

    private static let keyColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.5, green: 0.9, blue: 0.5, alpha: 1.0)
            : UIColor(red: 0.1, green: 0.5, blue: 0.1, alpha: 1.0)
    }
    private static let stringColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.5, green: 0.7, blue: 1.0, alpha: 1.0)
            : UIColor(red: 0.1, green: 0.4, blue: 0.7, alpha: 1.0)
    }
    private static let numberColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.4, green: 0.6, blue: 0.9, alpha: 1.0)
            : UIColor(red: 0.05, green: 0.3, blue: 0.6, alpha: 1.0)
    }

    private static let patterns: [(regex: NSRegularExpression, color: UIColor)] = {
        let defs: [(String, UIColor)] = [
            (#":\s*-?\d+\.?\d*"#, numberColor),
            (#"\b(true|false|null)\b"#, numberColor),
            (#""[^"]*""#, stringColor),
            (#""[^"]+"\s*:"#, keyColor),
        ]
        return defs.compactMap { pattern, color in
            guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else { return nil }
            return (regex, color)
        }
    }()

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UITextView {
        let textView = UITextView()
        textView.isEditable = false
        textView.isSelectable = true
        textView.isScrollEnabled = false
        textView.backgroundColor = .clear
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 0
        textView.textContainer.widthTracksTextView = true
        textView.textContainer.lineBreakMode = .byWordWrapping
        textView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return textView
    }

    func updateUIView(_ textView: UITextView, context: Context) {
        let attributed = context.coordinator.attributedString(for: text, highlight: Self.highlight)
        textView.attributedText = attributed
    }

    class Coordinator {
        private var cachedText: String?
        private var cachedAttributedString: NSAttributedString?

        func attributedString(for text: String, highlight: (String) -> NSAttributedString) -> NSAttributedString {
            if let cached = cachedAttributedString, cachedText == text {
                return cached
            }
            let result = highlight(text)
            cachedText = text
            cachedAttributedString = result
            return result
        }
    }

    private static func highlight(_ text: String) -> NSAttributedString {
        let font = UIFont.monospacedSystemFont(ofSize: 11, weight: .regular)

        let displayText: String
        if text.count > maxDisplayLength {
            displayText = String(text.prefix(maxDisplayLength)) + "\n\n... [content truncated]"
        } else {
            displayText = text
        }

        let result = NSMutableAttributedString(string: displayText, attributes: [
            .font: font,
            .foregroundColor: UIColor.label
        ])

        let range = NSRange(displayText.startIndex..., in: displayText)
        for (regex, color) in patterns {
            for match in regex.matches(in: displayText, options: [], range: range) {
                result.addAttribute(.foregroundColor, value: color, range: match.range)
            }
        }

        return result
    }
}

struct LogHighlightView: UIViewRepresentable {
    let text: String

    static let maxDisplayLength = 20_000

    private static let lineNumberColor = UIColor { trait in
        trait.userInterfaceStyle == .dark ? UIColor(white: 0.6, alpha: 1.0) : UIColor(white: 0.45, alpha: 1.0)
    }

    private static let timestampColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.5, green: 0.8, blue: 1.0, alpha: 1.0)
            : UIColor(red: 0.1, green: 0.4, blue: 0.7, alpha: 1.0)
    }
    private static let errorColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 1.0, green: 0.4, blue: 0.4, alpha: 1.0)
            : UIColor(red: 0.8, green: 0.1, blue: 0.1, alpha: 1.0)
    }
    private static let warnColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 1.0, green: 0.75, blue: 0.3, alpha: 1.0)
            : UIColor(red: 0.8, green: 0.5, blue: 0.0, alpha: 1.0)
    }
    private static let infoColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.5, green: 1.0, blue: 0.5, alpha: 1.0)
            : UIColor(red: 0.15, green: 0.55, blue: 0.15, alpha: 1.0)
    }
    private static let debugColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.8, green: 0.6, blue: 1.0, alpha: 1.0)
            : UIColor(red: 0.5, green: 0.3, blue: 0.7, alpha: 1.0)
    }
    private static let stringColor = UIColor { trait in
        trait.userInterfaceStyle == .dark
            ? UIColor(red: 0.5, green: 0.7, blue: 1.0, alpha: 1.0)
            : UIColor(red: 0.1, green: 0.4, blue: 0.7, alpha: 1.0)
    }

    private static let patterns: [(regex: NSRegularExpression, color: UIColor)] = {
        let defs: [(String, UIColor)] = [
            (#"\d{4}[-/]\d{2}[-/]\d{2}[T ]\d{2}:\d{2}:\d{2}([.,]\d+)?(Z|[+-]\d{2}:?\d{2})?"#, timestampColor),
            (#"\b(ERROR|FATAL|CRITICAL|ERR)\b"#, errorColor),
            (#"\b(WARN|WARNING|WRN)\b"#, warnColor),
            (#"\b(INFO|INF)\b"#, infoColor),
            (#"\b(DEBUG|DBG|TRACE|TRC)\b"#, debugColor),
            (#"(?i)(?:HTTP[/ ]|status[: ]+)[45]\d{2}\b"#, errorColor),
            (#"(?i)(?:HTTP[/ ]|status[: ]+)[23]\d{2}\b"#, infoColor),
            (#"https?://[^\s\]\)]+"#, .link),
            (#""[^"]*""#, stringColor),
        ]
        return defs.compactMap { pattern, color in
            guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else { return nil }
            return (regex, color)
        }
    }()

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UITextView {
        let textView = UITextView()
        textView.isEditable = false
        textView.isSelectable = true
        textView.isScrollEnabled = false
        textView.backgroundColor = .clear
        textView.textContainerInset = .zero
        textView.textContainer.lineFragmentPadding = 0
        textView.textContainer.widthTracksTextView = true
        textView.textContainer.lineBreakMode = .byWordWrapping
        textView.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textView.dataDetectorTypes = [.link]
        return textView
    }

    func updateUIView(_ textView: UITextView, context: Context) {
        let attributed = context.coordinator.attributedString(for: text, highlight: Self.highlight)
        textView.attributedText = attributed
    }

    class Coordinator {
        private var cachedText: String?
        private var cachedAttributedString: NSAttributedString?

        func attributedString(for text: String, highlight: (String) -> NSAttributedString) -> NSAttributedString {
            if let cached = cachedAttributedString, cachedText == text {
                return cached
            }
            let result = highlight(text)
            cachedText = text
            cachedAttributedString = result
            return result
        }
    }

    private static func highlight(_ text: String) -> NSAttributedString {
        let font = UIFont.monospacedSystemFont(ofSize: 11, weight: .regular)

        let displayText: String
        if text.count > maxDisplayLength {
            displayText = String(text.prefix(maxDisplayLength)) + "\n\n... [content truncated]"
        } else {
            displayText = text
        }
        let lines = displayText.split(separator: "\n", omittingEmptySubsequences: false)
        let result = NSMutableAttributedString()

        for (index, lineSub) in lines.enumerated() {
            let lineNumber = String(format: "%4d  ", index + 1)
            let numberAttr = NSAttributedString(string: lineNumber, attributes: [
                .font: font,
                .foregroundColor: lineNumberColor
            ])
            result.append(numberAttr)

            let line = String(lineSub)
            let lineAttr = NSMutableAttributedString(string: line, attributes: [
                .font: font,
                .foregroundColor: UIColor.label
            ])

            let range = NSRange(line.startIndex..., in: line)
            for (regex, color) in patterns {
                for match in regex.matches(in: line, options: [], range: range) {
                    lineAttr.addAttribute(.foregroundColor, value: color, range: match.range)
                }
            }
            result.append(lineAttr)

            if index < lines.count - 1 {
                result.append(NSAttributedString(string: "\n", attributes: [.font: font]))
            }
        }

        return result
    }
}

// MARK: - Disk Filesystem Detail Sheet

struct DiskFsDetailSheet: View {
    let filesystem: DiskFsUsage
    let records: [BeszelRecordStats]
    let localizer: Localizer

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    SheetHeader(
                        title: "\(localizer.t.beszelDisk) • \(filesystem.label)",
                        subtitle: String(format: "%.0f%%", filesystem.percent)
                    )

                    if usageHistory.count >= 2 {
                        SmoothLineGraph(
                            data: usageHistory,
                            graphColor: .orange,
                            height: 150,
                            labelFormatter: { String(format: "%.0f%%", $0) }
                        )
                    }

                    VStack(spacing: 0) {
                        infoRow(localizer.t.beszelUsed, BeszelFormatters.formatGB(filesystem.usedGb))
                        infoRow(localizer.t.beszelTotal, BeszelFormatters.formatGB(filesystem.totalGb))
                    }
                    .glassCard()

                    if readHistory.count >= 2 {
                        SmoothLineGraph(
                            data: readHistory,
                            secondaryData: writeHistory,
                            graphColor: .green,
                            secondaryColor: .orange,
                            height: 150,
                            labelFormatter: { "\(localizer.t.beszelRead): \(BeszelFormatters.formatBytesRate($0))" },
                            secondaryLabelFormatter: { "\(localizer.t.beszelWrite): \(BeszelFormatters.formatBytesRate($0))" }
                        )
                    } else if let latestRead = latestReadBytes, let latestWrite = latestWriteBytes {
                        VStack(spacing: 0) {
                            infoRow(localizer.t.beszelRead, BeszelFormatters.formatBytesRate(latestRead))
                            infoRow(localizer.t.beszelWrite, BeszelFormatters.formatBytesRate(latestWrite))
                        }
                        .glassCard()
                    }
                }
                .padding()
            }
            .background(AppTheme.background)
            .navigationTitle("\(localizer.t.beszelDisk) • \(filesystem.label)")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    private var usageHistory: [Double] {
        if filesystem.isRoot {
            return records.map(\.dpValue)
        }
        return records.compactMap { stats in
            guard let entry = stats.efs?[filesystem.key],
                  let total = entry.d, total > 0,
                  let used = entry.du else { return nil }
            return min(used / total * 100, 100)
        }
    }

    private var readHistory: [Double] {
        if filesystem.isRoot {
            return records.compactMap { $0.diskReadBytesPerSec }
        }
        return records.compactMap { $0.efs?[filesystem.key]?.readBytesPerSec }
    }

    private var writeHistory: [Double] {
        if filesystem.isRoot {
            return records.compactMap { $0.diskWriteBytesPerSec }
        }
        return records.compactMap { $0.efs?[filesystem.key]?.writeBytesPerSec }
    }

    private var latestReadBytes: Double? {
        if filesystem.isRoot {
            return records.last?.diskReadBytesPerSec
        }
        return records.last?.efs?[filesystem.key]?.readBytesPerSec
    }

    private var latestWriteBytes: Double? {
        if filesystem.isRoot {
            return records.last?.diskWriteBytesPerSec
        }
        return records.last?.efs?[filesystem.key]?.writeBytesPerSec
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.subheadline)
            Spacer()
            Text(value).font(.subheadline.bold())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }
}

// MARK: - S.M.A.R.T. Detail Sheet

struct SmartDetailSheet: View {
    let device: BeszelSmartDevice
    let localizer: Localizer

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    // Device info
                    VStack(spacing: 0) {
                        if let model = device.model { infoRow(localizer.t.beszelModel, model) }
                        if let cap = device.capacity {
                            infoRow(localizer.t.beszelCapacity, BeszelFormatters.formatBytes(Double(cap)))
                        }
                        if let type = device.type { infoRow(localizer.t.beszelType, type) }
                        if let temp = device.temperatureCelsius {
                            infoRow(localizer.t.beszelTemperature, "\(Int(temp))°C")
                        }
                        if let hours = device.hours { infoRow(localizer.t.beszelPowerOnHours, "\(hours)") }
                        if let cycles = device.cycles { infoRow(localizer.t.beszelPowerCycles, "\(cycles)") }
                    }
                    .glassCard()

                    // Attributes
                    if let attrs = device.attributes, !attrs.isEmpty {
                        Text(localizer.t.beszelSmartAttributes)
                            .font(.subheadline.bold())
                            .padding(.horizontal, 4)

                        VStack(spacing: 0) {
                            ForEach(attrs, id: \.stableId) { attr in
                                let summaryText = [
                                    attr.value.map { "Value \($0)" },
                                    attr.worst.map { "Worst \($0)" },
                                    attr.threshold.map { "Th \($0)" }
                                ]
                                .compactMap { $0 }
                                .joined(separator: " • ")

                                let rawText = attr.rawString ?? attr.rawValue.map { "\($0)" } ?? ""

                                VStack(spacing: 4) {
                                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                                        Text("\(attr.id ?? 0) \(attr.name)")
                                            .font(.caption.weight(.medium))
                                            .foregroundStyle(.primary)
                                            .lineLimit(1)
                                            .truncationMode(.tail)
                                            .frame(maxWidth: .infinity, alignment: .leading)

                                        if !summaryText.isEmpty {
                                            Text(summaryText)
                                                .font(.caption.monospacedDigit())
                                                .foregroundStyle(.secondary)
                                                .lineLimit(1)
                                                .truncationMode(.tail)
                                        }
                                    }

                                    if !rawText.isEmpty {
                                        HStack {
                                            if summaryText.isEmpty {
                                                Spacer(minLength: 0)
                                            }
                                            Text(rawText)
                                                .font(.caption2.monospacedDigit())
                                                .foregroundStyle(.secondary)
                                                .lineLimit(1)
                                                .truncationMode(.middle)
                                        }
                                    }
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                            }
                        }
                        .glassCard()
                    }
                }
                .padding()
            }
            .background(AppTheme.background)
            .navigationTitle(device.device)
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium, .large])
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label).font(.subheadline).foregroundStyle(.secondary)
            Spacer()
            Text(value).font(.subheadline.weight(.medium))
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }
}
