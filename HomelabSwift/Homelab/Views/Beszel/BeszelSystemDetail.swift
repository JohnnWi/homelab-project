import SwiftUI

// Maps to app/beszel/[systemId].tsx — system detail with info, resources, network, containers

struct BeszelSystemDetail: View {
    let systemId: String

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var system: BeszelSystem?
    @State private var records: [BeszelSystemRecord] = []
    @State private var isLoading = true
    @State private var fetchError: String?
    @State private var showFetchError = false

    private let beszelColor = Color(hex: "#0EA5E9")
    private let memoryColor = Color(hex: "#8B5CF6")

    var body: some View {
        ScrollView {
            if isLoading && system == nil {
                ProgressView()
                    .tint(beszelColor)
                    .frame(maxWidth: .infinity, minHeight: 300)
            } else if let system {
                LazyVStack(spacing: AppTheme.gridSpacing) {
                    headerCard(system)

                    if let info = system.info {
                        systemInfoSection(info)
                        resourcesSection(system: system, info: info)
                        containersSection
                        uptimeCard(info)
                    }
                }
                .padding(AppTheme.padding)
            } else {
                ContentUnavailableView(
                    label: { Label(localizer.t.noData, systemImage: "server.rack") },
                    description: { Text(localizer.t.noData).foregroundStyle(AppTheme.textSecondary) }
                )
            }
        }
        .background(AppTheme.background)
        .navigationTitle(system?.name ?? localizer.t.beszelSystemDetail)
        .refreshable { await fetchAll() }
        .task { await fetchAll() }
        .alert("Error", isPresented: $showFetchError) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(fetchError ?? "Unknown error")
        }
    }

    // MARK: - Header Card

    @ViewBuilder
    private func headerCard(_ system: BeszelSystem) -> some View {
        let isUp = system.isOnline

        HStack(spacing: 14) {
            Image(systemName: isUp ? "wifi" : "wifi.slash")
                .font(.title2)
                .foregroundStyle(isUp ? AppTheme.running : AppTheme.stopped)
                .frame(width: 52, height: 52)
                .background(
                    (isUp ? AppTheme.running : AppTheme.stopped).opacity(0.1),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(system.name)
                    .font(.title3.bold())
                Text("\(system.host):\(system.port)")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textMuted)
            }

            Spacer()

            HStack(spacing: 5) {
                Circle()
                    .fill(isUp ? AppTheme.running : AppTheme.stopped)
                    .frame(width: 8, height: 8)
                Text(isUp ? localizer.t.beszelUp : localizer.t.beszelDown)
                    .font(.caption.bold())
                    .foregroundStyle(isUp ? AppTheme.running : AppTheme.stopped)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(
                (isUp ? AppTheme.running : AppTheme.stopped).opacity(0.1),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
        }
        .padding(18)
        .glassCard()
    }

    // MARK: - System Info Section

    @ViewBuilder
    private func systemInfoSection(_ info: BeszelSystemInfo) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(icon: "info.circle", title: localizer.t.beszelSystemInfo)

            VStack(spacing: 0) {
                if let os = info.os, !os.isEmpty {
                    infoRow(label: localizer.t.beszelOs, value: os)
                }
                if let k = info.k, !k.isEmpty {
                    Divider()
                    infoRow(label: localizer.t.beszelKernel, value: k)
                }
                if let h = info.h, !h.isEmpty {
                    Divider()
                    infoRow(label: localizer.t.beszelHostname, value: h)
                }
                if let cm = info.cm, !cm.isEmpty {
                    Divider()
                    infoRow(label: localizer.t.beszelCpuModel, value: cm)
                }
                if let c = info.c, c > 0 {
                    Divider()
                    infoRow(label: localizer.t.beszelCores, value: "\(c)")
                }
                Divider()
                infoRow(label: localizer.t.beszelUptime, value: formatUptimeHours(info.uValue))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
            .glassCard()
        }
    }

    // MARK: - Resources Section

    @ViewBuilder
    private func resourcesSection(system: BeszelSystem, info: BeszelSystemInfo) -> some View {
        let latestStats = records.first?.stats
        let cpuHistory = Array(records.prefix(20).reversed().map { $0.stats.cpuValue })
        let memHistory = Array(records.prefix(20).reversed().map { $0.stats.mpValue })

        let memUsed = latestStats?.mValue ?? info.mValue
        let memTotal = latestStats?.mtValue ?? info.mtValue
        let diskUsed = latestStats?.dValue ?? info.dValue
        let diskTotal = latestStats?.dtValue ?? info.dtValue

        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(icon: "square.stack.3d.up", title: localizer.t.beszelResources)

            // CPU
            ResourceCard(
                icon: "cpu",
                iconColor: beszelColor,
                title: localizer.t.beszelCpu,
                percent: info.cpuValue,
                history: cpuHistory,
                barColor: beszelColor
            )

            // Memory
            ResourceCard(
                icon: "memorychip",
                iconColor: memoryColor,
                title: localizer.t.beszelMemory,
                percent: info.mpValue,
                history: memHistory,
                barColor: memoryColor
            )

            // Disk
            ResourceCard(
                icon: "internaldrive",
                iconColor: AppTheme.warning,
                title: localizer.t.beszelDisk,
                percent: info.dpValue,
                history: nil,
                barColor: AppTheme.warning
            )
        }
    }


    // MARK: - Containers Section

    @ViewBuilder
    private var containersSection: some View {
        let containers: [BeszelContainer] = records.first?.stats.dc ?? []

        if !containers.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                sectionHeader(
                    icon: "shippingbox",
                    title: "\(localizer.t.beszelContainers) (\(containers.count))"
                )

                VStack(spacing: 0) {
                    ForEach(Array(containers.enumerated()), id: \.element.id) { index, container in
                        HStack(spacing: 10) {
                            Image(systemName: "shippingbox")
                                .font(.caption)
                                .foregroundStyle(beszelColor)
                                .frame(width: 28, height: 28)
                                .background(beszelColor.opacity(0.08), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

                            Text(container.name)
                                .font(.subheadline.weight(.medium))
                                .lineLimit(1)

                            Spacer()

                            HStack(spacing: 4) {
                                Image(systemName: "cpu")
                                    .font(.system(size: 10))
                                    .foregroundStyle(AppTheme.textMuted)
                                Text(String(format: "%.1f%%", container.cpuValue))
                                    .font(.caption2.weight(.medium))
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)

                        if index < containers.count - 1 {
                            Divider().padding(.leading, 52)
                        }
                    }
                }
                .glassCard()
            }
        }
    }

    // MARK: - Uptime Card

    @ViewBuilder
    private func uptimeCard(_ info: BeszelSystemInfo) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "clock")
                .font(.title3)
                .foregroundStyle(AppTheme.textMuted)
            VStack(alignment: .leading, spacing: 2) {
                Text(localizer.t.beszelUptime)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
                Text(formatUptimeHours(info.uValue))
                    .font(.title3.bold())
            }
            Spacer()
        }
        .padding(16)
        .glassCard()
    }

    // MARK: - Section Header

    private func sectionHeader(icon: String, title: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(beszelColor)
            Text(title)
                .font(.subheadline.bold())
        }
        .padding(.horizontal, 4)
    }

    // MARK: - Info Row

    private func infoRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.medium))
                .multilineTextAlignment(.trailing)
                .frame(maxWidth: .infinity, alignment: .trailing)
                .lineLimit(2)
        }
        .padding(.vertical, 12)
    }

    // MARK: - Fetch

    private func fetchAll() async {
        do {
            let s = try await servicesStore.beszelClient.getSystem(id: systemId)
            system = s
        } catch {
            if system == nil {
                fetchError = error.localizedDescription
                showFetchError = true
            }
        }

        // Records are non-critical — fetch separately
        if let r = try? await servicesStore.beszelClient.getSystemRecords(systemId: systemId, limit: 30) {
            records = r.items
        }

        isLoading = false
    }
}

// MARK: - Resource Card

private struct ResourceCard: View {
    let icon: String
    let iconColor: Color
    let title: String
    let percent: Double
    let history: [Double]?
    let barColor: Color
    var detailLeft: String? = nil
    var detailRight: String? = nil

    private func usageColor(_ value: Double) -> Color {
        if value > 90 { return AppTheme.stopped }
        if value > 70 { return AppTheme.warning }
        return AppTheme.running
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(iconColor)
                    .frame(width: 36, height: 36)
                    .background(iconColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                Text(title)
                    .font(.subheadline.bold())
                    .lineLimit(1)

                Spacer()

                Text(Formatters.formatPercent(percent))
                    .font(.title3.bold())
                    .foregroundStyle(usageColor(percent))
            }

            // Progress bar
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(.white.opacity(0.1))
                        .frame(height: 8)
                    RoundedRectangle(cornerRadius: 4)
                        .fill(usageColor(percent).gradient)
                        .frame(width: geo.size.width * CGFloat(min(percent, 100)) / 100, height: 8)
                        .animation(.spring(response: 0.6, dampingFraction: 0.8), value: percent)
                }
            }
            .frame(height: 8)

            // Details
            if let left = detailLeft, let right = detailRight {
                HStack {
                    Text(left)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                    Spacer()
                    Text(right)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            // Mini graph
            if let history, history.count > 3 {
                MiniBarGraph(data: history, color: barColor)
            }
        }
        .padding(16)
        .glassCard()
    }
}

// MARK: - Network Card

private struct NetworkCard: View {
    let icon: String
    let iconColor: Color
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(iconColor)
                .frame(width: 40, height: 40)
                .background(iconColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            Text(value)
                .font(.body.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.7)

            Text(label)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .glassCard()
    }
}

// MARK: - Mini Bar Graph

private struct MiniBarGraph: View {
    let data: [Double]
    let color: Color

    var body: some View {
        let maxVal = max(1, data.max() ?? 1)

        VStack(spacing: 0) {
            Divider()
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(Array(data.enumerated()), id: \.offset) { _, value in
                    RoundedRectangle(cornerRadius: 1)
                        .fill(color.opacity(0.35))
                        .frame(height: max(2, CGFloat(value / maxVal) * 40))
                }
            }
            .frame(height: 40)
            .frame(maxWidth: .infinity)
            .padding(.top, 4)
        }
    }
}

// MARK: - Private formatters

private func formatBeszelSize(_ val: Double, compact: Bool = false) -> String {
    if val == 0 { return "0" }
    
    // Fallback per valori già in bytes (es. traffico o vecchie API)
    if val > 10000 {
        let formatted = Formatters.formatBytes(val)
        return compact ? formatted.replacingOccurrences(of: "B", with: "").replacingOccurrences(of: " ", with: "") : formatted
    }
    
    if val < 0.01 { return compact ? "<0.01" : "< 0.01 GB" }
    if val < 1 {
        let mb = String(format: "%.0f", val * 1024)
        return compact ? mb : "\(mb) MB"
    }
    let gb = String(format: "%.1f", val)
    return compact ? gb : "\(gb) GB"
}

private func formatNetRate(_ val: Double) -> String {
    if val == 0 { return "0 B/s" }
    return "\(Formatters.formatBytes(val))/s"
}

private func formatUptimeHours(_ seconds: Double) -> String {
    let days = Int(seconds / 86400)
    let hours = Int(seconds.truncatingRemainder(dividingBy: 86400) / 3600)
    if days > 0 { return "\(days)d \(hours)h" }
    let minutes = Int(seconds.truncatingRemainder(dividingBy: 3600) / 60)
    if hours > 0 { return "\(hours)h \(minutes)m" }
    return "\(minutes)m"
}
