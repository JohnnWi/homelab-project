import SwiftUI

private let beszelColor = Color(hex: "#0EA5E9")

// MARK: - Extra Metrics Section

struct ExtraMetricsSection: View {
    let stats: BeszelRecordStats
    let localizer: Localizer
    @Binding var expandedMetric: ExtraMetricType?

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        let chips = availableChips
        let swap = swapInfo
        if !chips.isEmpty || swap != nil {
            VStack(alignment: .leading, spacing: 12) {
                BeszelSectionHeader(icon: "chart.bar.xaxis", title: localizer.t.beszelExtraMetrics, color: beszelColor)

                if let swap {
                    SwapUsageCard(
                        used: swap.used,
                        total: swap.total,
                        percent: swap.percent,
                        color: swap.color,
                        localizer: localizer
                    )
                    .onTapGesture { expandedMetric = .swap }
                }

                if !chips.isEmpty {
                    LazyVGrid(columns: columns, spacing: 10) {
                        ForEach(chips, id: \.type) { chip in
                            ExtraMetricChip(
                                icon: chip.icon,
                                label: chip.label,
                                value: chip.value,
                                color: chip.color,
                                progress: chip.progress
                            )
                            .onTapGesture { expandedMetric = chip.type }
                        }
                    }
                }
            }
        }
    }

    private struct ChipData {
        let type: ExtraMetricType
        let icon: String
        let label: String
        let value: String
        let color: Color
        let progress: Double?
    }

    private struct SwapUsageInfo {
        let used: Double
        let total: Double
        let percent: Double
        let color: Color
    }

    private var swapInfo: SwapUsageInfo? {
        guard let swapTotal = stats.swapTotalGb, swapTotal > 0 else { return nil }
        let used = stats.swapUsedGb ?? 0
        let pct = max(0, min((used / swapTotal) * 100, 100))
        let color: Color = pct > 80 ? .red : pct > 50 ? .orange : .green
        return SwapUsageInfo(used: used, total: swapTotal, percent: pct, color: color)
    }

    private var availableChips: [ChipData] {
        var result: [ChipData] = []
        if let temp = stats.maxTempCelsius {
            result.append(ChipData(type: .temperature, icon: "thermometer", label: localizer.t.beszelTemperature,
                                   value: "\(Int(temp))°C", color: temp > 80 ? .red : temp > 60 ? .orange : .green, progress: nil))
        }
        if !stats.loadAvgValues.isEmpty {
            let la1 = stats.loadAvgValues[0]
            result.append(ChipData(type: .load, icon: "gauge", label: localizer.t.beszelLoadAverage,
                                   value: String(format: "%.2f", la1), color: la1 > 4 ? .red : la1 > 2 ? .orange : .green, progress: nil))
        }
        if stats.bandwidthUpBytesPerSec != nil || stats.bandwidthDownBytesPerSec != nil {
            let up = stats.bandwidthUpBytesPerSec ?? 0
            let down = stats.bandwidthDownBytesPerSec ?? 0
            result.append(ChipData(type: .network, icon: "network", label: localizer.t.beszelNetworkTraffic,
                                   value: "\(BeszelFormatters.formatBytesRate(up)) / \(BeszelFormatters.formatBytesRate(down))",
                                   color: beszelColor, progress: nil))
        }
        if stats.diskReadBytesPerSec != nil {
            let r = stats.diskReadBytesPerSec ?? 0
            let w = stats.diskWriteBytesPerSec ?? 0
            result.append(ChipData(type: .diskIO, icon: "internaldrive", label: localizer.t.beszelDiskIO,
                                   value: "\(BeszelFormatters.formatBytesRate(r)) / \(BeszelFormatters.formatBytesRate(w))",
                                   color: .orange, progress: nil))
        }
        if let level = stats.batteryLevel {
            result.append(ChipData(type: .battery, icon: "battery.100", label: localizer.t.beszelBattery,
                                   value: "\(level)%", color: level < 20 ? .red : level < 50 ? .orange : .green,
                                   progress: Double(level) / 100.0))
        }
        return result
    }
}

private struct SwapUsageCard: View {
    let used: Double
    let total: Double
    let percent: Double
    let color: Color
    let localizer: Localizer

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.title3)
                    .foregroundStyle(color)
                    .frame(width: 36, height: 36)
                    .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                Text(localizer.t.beszelSwap)
                    .font(.subheadline.bold())
                    .lineLimit(1)

                Spacer()

                Text(Formatters.formatPercent(percent))
                    .font(.title3.bold())
                    .foregroundStyle(color)
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(.white.opacity(0.08))
                        .frame(height: 8)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(color.gradient)
                        .frame(width: geo.size.width * CGFloat(min(percent, 100)) / 100, height: 8)
                        .animation(.spring(response: 0.6, dampingFraction: 0.8), value: percent)
                }
            }
            .frame(height: 8)

            HStack {
                Text("\(BeszelFormatters.formatGB(used)) used")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Text("\(BeszelFormatters.formatGB(total)) total")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(14)
        .glassCard()
    }
}

// MARK: - GPU Metrics Section

struct GpuMetricsSection: View {
    let stats: BeszelRecordStats
    let localizer: Localizer
    @Binding var expandedGpuMetric: GpuMetricType?

    var body: some View {
        let entries = stats.gpuEntries
        if entries.isEmpty { return AnyView(EmptyView()) }
        return AnyView(
            VStack(alignment: .leading, spacing: 12) {
                BeszelSectionHeader(icon: "gpu", title: localizer.t.beszelGpu, color: .green)

                ForEach(entries, id: \.key) { entry in
                    gpuCard(gpuKey: entry.key, gpu: entry.gpu)
                }
            }
        )
    }

    @ViewBuilder
    private func gpuCard(gpuKey: String, gpu: BeszelGpuEntry) -> some View {
        VStack(spacing: 0) {
            Text(gpu.n)
                .font(.caption.bold())
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)
                .padding(.top, 12)

            if let usage = gpu.u {
                metricRow(icon: "gauge.medium", label: localizer.t.beszelGpuUsage, value: String(format: "%.1f%%", usage),
                          progress: usage / 100.0, color: usageColor(usage))
                .onTapGesture { expandedGpuMetric = .usage(gpuKey: gpuKey) }
            }
            if let power = gpu.p {
                Divider().padding(.leading, 52)
                metricRow(icon: "bolt", label: localizer.t.beszelGpuPower, value: String(format: "%.0fW", power),
                          progress: nil, color: .orange)
                .onTapGesture { expandedGpuMetric = .power(gpuKey: gpuKey) }
            }
            if let vramPct = gpu.memUsagePercent {
                Divider().padding(.leading, 52)
                metricRow(icon: "memorychip", label: localizer.t.beszelGpuVram,
                          value: String(format: "%.0f / %.0f MB (%.0f%%)", gpu.memUsedMb, gpu.memTotalMb, vramPct),
                          progress: vramPct / 100.0, color: usageColor(vramPct))
                .onTapGesture { expandedGpuMetric = .vram(gpuKey: gpuKey) }
            }
        }
        .padding(.bottom, 12)
        .glassCard()
    }

    private func metricRow(icon: String, label: String, value: String, progress: Double?, color: Color) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(color)
                .frame(width: 28, height: 28)
                .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))

            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(.caption).foregroundStyle(.secondary)
                Text(value).font(.subheadline.weight(.medium))
            }

            Spacer()

            if let p = progress {
                CircularProgress(progress: p, color: color, size: 28)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }

    private func usageColor(_ v: Double) -> Color {
        v > 90 ? .red : v > 70 ? .orange : .green
    }
}

// MARK: - S.M.A.R.T. Devices Section

struct SmartDevicesSection: View {
    let devices: [BeszelSmartDevice]
    let localizer: Localizer
    @Binding var expandedDevice: BeszelSmartDevice?

    var body: some View {
        if !devices.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                BeszelSectionHeader(icon: "internaldrive", title: localizer.t.beszelSmartDevices, color: .orange)

                VStack(spacing: 0) {
                    ForEach(Array(devices.enumerated()), id: \.element.id) { index, device in
                        Button { expandedDevice = device } label: {
                            HStack(spacing: 10) {
                                Image(systemName: "internaldrive")
                                    .font(.caption)
                                    .foregroundStyle(.orange)
                                    .frame(width: 28, height: 28)
                                    .background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(device.device).font(.subheadline.weight(.medium))
                                    if let model = device.model {
                                        Text(model).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                    }
                                }

                                Spacer()

                                let statusRaw = device.status.uppercased()
                                let isPassed = statusRaw == "PASSED"
                                let isFailing = statusRaw == "FAILED" || statusRaw == "FAILING"
                                let statusLabel = isPassed ? localizer.t.beszelPassed : (isFailing ? localizer.t.beszelFailing : device.status)

                                // Status badge
                                Text(statusLabel)
                                    .font(.caption2.bold())
                                    .foregroundStyle(isPassed ? .green : (isFailing ? .red : .secondary))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(
                                        (isPassed ? Color.green : (isFailing ? .red : .gray)).opacity(0.1),
                                        in: RoundedRectangle(cornerRadius: 6)
                                    )

                                Image(systemName: "chevron.right")
                                    .font(.caption2)
                                    .foregroundStyle(.tertiary)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)

                        if index < devices.count - 1 {
                            Divider().padding(.leading, 52)
                        }
                    }
                }
                .glassCard()
            }
        }
    }
}

// MARK: - Docker Metrics Section

struct DockerMetricsSection: View {
    let summary: DockerMetricSummary
    let localizer: Localizer
    let hasNetwork: Bool
    let onCpuClick: () -> Void
    let onMemoryClick: () -> Void
    let onNetworkClick: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            BeszelSectionHeader(icon: "shippingbox", title: localizer.t.beszelDocker, color: beszelColor)

            HStack(spacing: 10) {
                miniStat(
                    icon: "cpu",
                    label: localizer.t.beszelDockerCpuUsage,
                    value: String(format: "%.1f%%", summary.cpuPercent),
                    color: beszelColor,
                    onTap: onCpuClick
                )
                miniStat(
                    icon: "memorychip",
                    label: localizer.t.beszelDockerMemoryUsage,
                    value: BeszelFormatters.formatMB(summary.memoryUsedMb),
                    color: .purple,
                    onTap: onMemoryClick
                )
                if hasNetwork, let up = summary.uploadRate, let down = summary.downloadRate {
                    miniStat(
                        icon: "arrow.up.arrow.down",
                        label: localizer.t.beszelDockerNetworkIO,
                        value: "\(BeszelFormatters.formatBytesRate(up))/\(BeszelFormatters.formatBytesRate(down))",
                        color: .green,
                        onTap: onNetworkClick
                    )
                }
            }
        }
    }

    private func miniStat(icon: String, label: String, value: String, color: Color, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.caption)
                    .foregroundStyle(color)
                Text(value)
                    .font(.caption.bold())
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(12)
            .glassCard()
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Per-Core CPU Section

struct PerCoreCpuSection: View {
    let cores: [Double]
    let localizer: Localizer

    var body: some View {
        if !cores.isEmpty {
            let average = cores.reduce(0, +) / Double(cores.count)
            let peak = cores.max() ?? 0
            VStack(alignment: .leading, spacing: 12) {
                BeszelSectionHeader(icon: "cpu", title: localizer.t.beszelPerCoreCpu, color: beszelColor)

                Text(String(format: localizer.t.beszelPerCoreSummary, cores.count, average, peak))
                    .font(.caption)
                    .foregroundStyle(.secondary)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Array(cores.enumerated()), id: \.offset) { index, usage in
                            VStack(spacing: 6) {
                                Text(String(format: localizer.t.beszelCpuCoreLabel, index + 1))
                                    .font(.caption2.bold())
                                    .foregroundStyle(.secondary)

                                ZStack(alignment: .bottom) {
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(.white.opacity(0.05))
                                        .frame(width: 20, height: 50)

                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(coreColor(usage).gradient)
                                        .frame(width: 20, height: max(2, CGFloat(min(usage, 100)) / 100.0 * 50))
                                }

                                Text(String(format: "%.0f%%", usage))
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(coreColor(usage))
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .glassCard()
            }
        }
    }

    private func coreColor(_ v: Double) -> Color {
        v > 90 ? .red : v > 70 ? .orange : .green
    }
}

// MARK: - Shared Components

struct ExtraMetricChip: View {
    let icon: String
    let label: String
    let value: String
    let color: Color
    var progress: Double? = nil

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(color)
                .frame(width: 24, height: 24)
                .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 6))

            VStack(alignment: .leading, spacing: 1) {
                Text(label).font(.caption2).foregroundStyle(.secondary)
                Text(value).font(.caption.bold()).lineLimit(1).minimumScaleFactor(0.7)
            }
            Spacer(minLength: 0)
        }
        .padding(10)
        .glassCard()
        .contentShape(Rectangle())
    }
}

struct CircularProgress: View {
    let progress: Double
    let color: Color
    var size: CGFloat = 28

    var body: some View {
        ZStack {
            Circle()
                .stroke(.white.opacity(0.1), lineWidth: 3)
            Circle()
                .trim(from: 0, to: min(progress, 1))
                .stroke(color, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: size, height: size)
    }
}

struct BeszelSectionHeader: View {
    let icon: String
    let title: String
    var color: Color = Color(hex: "#0EA5E9")

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(color)
            Text(title)
                .font(.subheadline.bold())
        }
        .padding(.horizontal, 4)
    }
}

// MARK: - Formatters

enum BeszelFormatters {
    static func formatMB(_ val: Double) -> String {
        if val >= 1024 { return String(format: "%.1f GB", val / 1024) }
        return String(format: "%.0f MB", val)
    }

    static func formatGB(_ val: Double) -> String {
        if val >= 1024 { return String(format: "%.1f TB", val / 1024) }
        if val < 1 { return String(format: "%.0f MB", val * 1024) }
        return String(format: "%.1f GB", val)
    }

    static func formatBytes(_ bytes: Double) -> String {
        if bytes < 1024 { return String(format: "%.0f B", bytes) }
        if bytes < 1024 * 1024 { return String(format: "%.1f KB", bytes / 1024) }
        if bytes < 1024 * 1024 * 1024 { return String(format: "%.1f MB", bytes / (1024 * 1024)) }
        return String(format: "%.2f GB", bytes / (1024 * 1024 * 1024))
    }

    static func formatBytesRate(_ bytesPerSec: Double) -> String {
        "\(formatBytes(bytesPerSec))/s"
    }

    static func formatUptime(_ seconds: Double) -> String {
        let days = Int(seconds / 86400)
        let hours = Int(seconds.truncatingRemainder(dividingBy: 86400) / 3600)
        let minutes = Int(seconds.truncatingRemainder(dividingBy: 3600) / 60)
        if days > 0 { return "\(days)d \(hours)h" }
        if hours > 0 { return "\(hours)h \(minutes)m" }
        return "\(minutes)m"
    }
}
