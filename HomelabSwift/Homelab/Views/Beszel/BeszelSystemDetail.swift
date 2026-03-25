import SwiftUI

// Maps to app/beszel/[systemId].tsx — system detail with info, resources, network, containers

struct BeszelSystemDetail: View {
    let instanceId: UUID
    let systemId: String

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var system: BeszelSystem?
    @State private var records: [BeszelSystemRecord] = []
    @State private var systemDetails: BeszelSystemDetails?
    @State private var smartDevices: [BeszelSmartDevice] = []
    @State private var isLoading = true
    @State private var fetchError: String?
    @State private var showFetchError = false

    // Sheet state
    @State private var expandedMetric: ExtraMetricType?
    @State private var expandedGpuMetric: GpuMetricType?
    @State private var expandedSmartDevice: BeszelSmartDevice?
    @State private var expandedResource: ResourceMetricType?
    @State private var expandedDiskFs: DiskFsUsage?
    @State private var expandedDockerMetric: DockerMetricType?
    @State private var showCpuDetail = false
    @State private var isSystemInfoExpanded = false

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
                    if let info = system.info {
                        combinedHeaderCard(system, info: info)
                        resourcesSection(system: system, info: info)
                    } else {
                        headerCard(system)
                    }

                    // S.M.A.R.T. Devices
                    SmartDevicesSection(
                        devices: smartDevices,
                        localizer: localizer,
                        expandedDevice: $expandedSmartDevice
                    )

                    // Docker metrics
                    if let summary = dockerSummary {
                        DockerMetricsSection(
                            summary: summary,
                            localizer: localizer,
                            hasNetwork: hasDockerNetwork,
                            onCpuClick: { expandedDockerMetric = .cpu },
                            onMemoryClick: { expandedDockerMetric = .memory },
                            onNetworkClick: { expandedDockerMetric = .network }
                        )
                    }

                    // Per-core CPU
                    if let cores = latestStats?.cpuCoreUsageValues, !cores.isEmpty {
                        PerCoreCpuSection(cores: cores, localizer: localizer)
                    }

                    // GPU
                    if let stats = latestStats, !stats.gpuEntries.isEmpty {
                        GpuMetricsSection(
                            stats: stats,
                            localizer: localizer,
                            expandedGpuMetric: $expandedGpuMetric
                        )
                    }

                    // Extra metrics
                    if let stats = latestStats {
                        ExtraMetricsSection(
                            stats: stats,
                            localizer: localizer,
                            expandedMetric: $expandedMetric
                        )
                    }

                    containersLink

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
        .alert(localizer.t.error, isPresented: $showFetchError) {
            Button(localizer.t.confirm, role: .cancel) { }
        } message: {
            Text(fetchError ?? localizer.t.errorUnknown)
        }
        // Sheets
        .sheet(item: $expandedMetric) { metric in
            ExtraMetricDetailSheet(
                metricType: metric,
                records: sortedHistoryStats,
                localizer: localizer
            )
        }
        .sheet(item: $expandedGpuMetric) { metric in
            GpuDetailSheet(
                metricType: metric,
                records: sortedHistoryStats,
                localizer: localizer
            )
        }
        .sheet(item: $expandedSmartDevice) { device in
            SmartDetailSheet(device: device, localizer: localizer)
        }
        .sheet(item: $expandedResource) { metric in
            ResourceMetricDetailSheet(metricType: metric, records: sortedHistoryStats, localizer: localizer)
        }
        .sheet(item: $expandedDiskFs) { filesystem in
            DiskFsDetailSheet(filesystem: filesystem, records: sortedHistoryStats, localizer: localizer)
        }
        .sheet(item: $expandedDockerMetric) { metric in
            DockerDetailSheet(metricType: metric, records: sortedHistoryStats, localizer: localizer)
        }
        .sheet(isPresented: $showCpuDetail) {
            CpuDetailSheet(records: sortedHistoryStats, localizer: localizer)
        }
    }

    // MARK: - Computed

    private var latestStats: BeszelRecordStats? {
        records.sorted { ($0.created ?? "") > ($1.created ?? "") }.first?.stats
    }

    private var sortedHistoryStats: [BeszelRecordStats] {
        records.sorted { ($0.created ?? "") < ($1.created ?? "") }.suffix(30).map(\.stats)
    }

    private var dockerSummary: DockerMetricSummary? {
        guard let containers = latestStats?.dc, !containers.isEmpty else { return nil }
        let totalCpu = containers.reduce(0.0) { $0 + $1.cpuValue }
        let totalMem = containers.reduce(0.0) { $0 + $1.mValue }
        let totalUp = containers.compactMap(\.bandwidthUpBytesPerSec).reduce(0.0, +)
        let totalDown = containers.compactMap(\.bandwidthDownBytesPerSec).reduce(0.0, +)
        let hasNetwork = containers.contains { $0.bandwidthUpBytesPerSec != nil }
        return DockerMetricSummary(
            cpuPercent: totalCpu, memoryUsedMb: totalMem,
            uploadRate: hasNetwork ? totalUp : nil,
            downloadRate: hasNetwork ? totalDown : nil
        )
    }

    private var dockerCpuHistoryPercent: [Double] {
        dockerSeries { $0.cpuValue }
    }

    private var dockerMemoryUsedHistoryMb: [Double] {
        dockerSeries { $0.mValue }
    }

    private var dockerUploadHistoryBytesPerSec: [Double] {
        dockerOptionalSeries { $0.bandwidthUpBytesPerSec }
    }

    private var dockerDownloadHistoryBytesPerSec: [Double] {
        dockerOptionalSeries { $0.bandwidthDownBytesPerSec }
    }

    private var hasDockerNetwork: Bool {
        guard let summary = dockerSummary,
              summary.uploadRate != nil,
              summary.downloadRate != nil else { return false }
        return !dockerUploadHistoryBytesPerSec.isEmpty &&
            dockerDownloadHistoryBytesPerSec.count == dockerUploadHistoryBytesPerSec.count
    }

    private var rootDiskUsage: DiskFsUsage? {
        let used = latestStats?.du ?? system?.info?.du ?? 0
        let total = latestStats?.d ?? system?.info?.d ?? 0
        guard total > 0 else { return nil }
        return DiskFsUsage(key: "root", label: "root", usedGb: used, totalGb: total, isRoot: true)
    }

    private func dockerSeries(_ selector: (BeszelContainer) -> Double) -> [Double] {
        sortedHistoryStats.compactMap { stats in
            guard let containers = stats.dc, !containers.isEmpty else { return nil }
            return containers.reduce(0.0) { $0 + selector($1) }
        }
    }

    private func dockerOptionalSeries(_ selector: (BeszelContainer) -> Double?) -> [Double] {
        sortedHistoryStats.compactMap { stats in
            guard let containers = stats.dc, !containers.isEmpty else { return nil }
            let values = containers.compactMap(selector)
            guard !values.isEmpty else { return nil }
            return values.reduce(0.0, +)
        }
    }

    private func buildDiskItems(info: BeszelSystemInfo) -> [DiskFsUsage] {
        var items: [DiskFsUsage] = []
        if let root = rootDiskUsage {
            items.append(root)
        } else if let d = info.d, let du = info.du, d > 0 {
            items.append(DiskFsUsage(key: "root", label: "root", usedGb: du, totalGb: d, isRoot: true))
        }
        if let efs = latestStats?.efs {
            for (label, entry) in efs.sorted(by: { $0.key < $1.key }) {
                guard let d = entry.d, let du = entry.du, d > 0 else { continue }
                items.append(DiskFsUsage(key: label, label: label, usedGb: du, totalGb: d, isRoot: false))
            }
        }
        return items
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
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 2) {
                Text(system.name)
                    .font(.title3.bold())
                if let port = system.port {
                    Text("\(system.host):\(port)")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textMuted)
                } else {
                    Text(system.host)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textMuted)
                }
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

    // MARK: - Combined Header + System Info

    private func combinedHeaderCard(_ system: BeszelSystem, info: BeszelSystemInfo) -> some View {
        let isUp = system.isOnline
        let osDisplay = firstNonEmpty(systemDetails?.osName, info.os)
        let kernelDisplay = firstNonEmpty(systemDetails?.kernel, info.k)
        let hostnameDisplay = firstNonEmpty(systemDetails?.hostname, info.h)
        let cpuDisplay = firstNonEmpty(systemDetails?.cpu, info.cm)
        let coresDisplay = (systemDetails?.cores ?? 0) > 0 ? systemDetails?.cores : info.c
        let threadsDisplay = systemDetails?.threads
        let archDisplay = firstNonEmpty(systemDetails?.arch, nil)
        let memoryBytes = (systemDetails?.memory ?? 0) > 0 ? systemDetails?.memory : nil
        let memoryGbFallback = memoryBytes == nil ? info.mt : nil
        let uptimeSeconds = info.uValue

        return VStack(spacing: 0) {
            // Header row
            HStack(spacing: 14) {
                Image(systemName: isUp ? "wifi" : "wifi.slash")
                    .font(.title2)
                    .foregroundStyle(isUp ? AppTheme.running : AppTheme.stopped)
                    .frame(width: 44, height: 44)
                    .background(
                        (isUp ? AppTheme.running : AppTheme.stopped).opacity(0.1),
                        in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                    )
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(system.name)
                        .font(.headline)
                    if let port = system.port {
                        Text("\(system.host):\(port)")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    } else {
                        Text(system.host)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
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
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(
                    (isUp ? AppTheme.running : AppTheme.stopped).opacity(0.1),
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                )
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)

            // Expandable system info
            Divider()

            Button {
                withAnimation(.easeInOut(duration: 0.2)) {
                    isSystemInfoExpanded.toggle()
                }
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(beszelColor)

                    Text(localizer.t.beszelSystemInfo)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)

                    if let host = hostnameDisplay, !host.isEmpty {
                        Text("•")
                            .foregroundStyle(AppTheme.textMuted)
                        Text(host)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                            .lineLimit(1)
                    }

                    Spacer()

                    Image(systemName: isSystemInfoExpanded ? "chevron.up" : "chevron.down")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            if isSystemInfoExpanded {
                Divider()

                VStack(spacing: 0) {
                    if let os = osDisplay, !os.isEmpty {
                        infoRow(label: localizer.t.beszelOs, value: os)
                    }
                    if let k = kernelDisplay, !k.isEmpty {
                        Divider()
                        infoRow(label: localizer.t.beszelKernel, value: k)
                    }
                    if let cm = cpuDisplay, !cm.isEmpty {
                        Divider()
                        infoRow(label: localizer.t.beszelCpu, value: cm)
                    }
                    if let c = coresDisplay, c > 0 {
                        Divider()
                        let coreStr = threadsDisplay.map { "\(c) (\($0) threads)" } ?? "\(c)"
                        infoRow(label: localizer.t.beszelCores, value: coreStr)
                    }
                    if uptimeSeconds > 0 {
                        Divider()
                        infoRow(label: localizer.t.beszelUptime, value: formatUptimeHours(uptimeSeconds))
                    }
                    if let mem = memoryBytes, mem > 0 {
                        Divider()
                        infoRow(label: localizer.t.beszelMemory, value: BeszelFormatters.formatBytes(Double(mem)))
                    } else if let memGb = memoryGbFallback, memGb > 0 {
                        Divider()
                        infoRow(label: localizer.t.beszelMemory, value: BeszelFormatters.formatGB(memGb))
                    }
                    if let arch = archDisplay, !arch.isEmpty {
                        Divider()
                        infoRow(label: localizer.t.beszelArch, value: arch)
                    }
                    if systemDetails?.podman == true {
                        Divider()
                        infoRow(label: localizer.t.beszelPodman, value: "true")
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 4)
            }
        }
        .glassCard()
    }

    // MARK: - System Info Section (standalone fallback)

    @ViewBuilder
    private func systemInfoSection(_ info: BeszelSystemInfo) -> some View {
        let osDisplay = firstNonEmpty(systemDetails?.osName, info.os)
        let kernelDisplay = firstNonEmpty(systemDetails?.kernel, info.k)
        let hostnameDisplay = firstNonEmpty(systemDetails?.hostname, info.h)
        let cpuDisplay = firstNonEmpty(systemDetails?.cpu, info.cm)
        let coresDisplay = (systemDetails?.cores ?? 0) > 0 ? systemDetails?.cores : info.c
        let threadsDisplay = systemDetails?.threads
        let archDisplay = firstNonEmpty(systemDetails?.arch, nil)
        let memoryBytes = (systemDetails?.memory ?? 0) > 0 ? systemDetails?.memory : nil
        let memoryGbFallback = memoryBytes == nil ? info.mt : nil
        let uptimeSeconds = info.uValue

        VStack(alignment: .leading, spacing: 12) {
            VStack(spacing: 0) {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        isSystemInfoExpanded.toggle()
                    }
                } label: {
                    HStack(spacing: 10) {
                        Image(systemName: "info.circle")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(beszelColor)
                            .frame(width: 28, height: 28)
                            .background(beszelColor.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))

                        VStack(alignment: .leading, spacing: 2) {
                            Text(localizer.t.beszelSystemInfo)
                                .font(.subheadline.bold())
                                .foregroundStyle(.primary)

                            if (hostnameDisplay?.isEmpty == false) || uptimeSeconds > 0 {
                                HStack(spacing: 6) {
                                    if let host = hostnameDisplay, !host.isEmpty {
                                        Text(host)
                                            .font(.caption)
                                            .foregroundStyle(AppTheme.textSecondary)
                                            .lineLimit(1)
                                    }
                                    if uptimeSeconds > 0 {
                                        Text("• \(localizer.t.beszelUptime): \(formatUptimeHours(uptimeSeconds))")
                                            .font(.caption)
                                            .foregroundStyle(AppTheme.textSecondary)
                                            .lineLimit(1)
                                    }
                                }
                            }
                        }

                        Spacer()

                        Image(systemName: isSystemInfoExpanded ? "chevron.up" : "chevron.down")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                }
                .buttonStyle(.plain)

                if isSystemInfoExpanded {
                    Divider()

                    VStack(spacing: 0) {
                        if let os = osDisplay, !os.isEmpty {
                            infoRow(label: localizer.t.beszelOs, value: os)
                        }
                        if let k = kernelDisplay, !k.isEmpty {
                            Divider()
                            infoRow(label: localizer.t.beszelKernel, value: k)
                        }
                        if let cm = cpuDisplay, !cm.isEmpty {
                            Divider()
                            infoRow(label: localizer.t.beszelCpu, value: cm)
                        }
                        if let c = coresDisplay, c > 0 {
                            Divider()
                            let coreStr = threadsDisplay.map { "\(c) (\($0) threads)" } ?? "\(c)"
                            infoRow(label: localizer.t.beszelCores, value: coreStr)
                        }
                        if uptimeSeconds > 0 {
                            Divider()
                            infoRow(label: localizer.t.beszelUptime, value: formatUptimeHours(uptimeSeconds))
                        }
                        if let mem = memoryBytes, mem > 0 {
                            Divider()
                            infoRow(label: localizer.t.beszelMemory, value: BeszelFormatters.formatBytes(Double(mem)))
                        } else if let memGb = memoryGbFallback, memGb > 0 {
                            Divider()
                            infoRow(label: localizer.t.beszelMemory, value: BeszelFormatters.formatGB(memGb))
                        }
                        if let arch = archDisplay, !arch.isEmpty {
                            Divider()
                            infoRow(label: localizer.t.beszelArch, value: arch)
                        }
                        if systemDetails?.podman == true {
                            Divider()
                            infoRow(label: localizer.t.beszelPodman, value: "true")
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 4)
                }
            }
            .glassCard()
        }
    }

    // MARK: - Resources Section

    @ViewBuilder
    private func resourcesSection(system: BeszelSystem, info: BeszelSystemInfo) -> some View {
        let historyStats = sortedHistoryStats
        let cpuHistory = historyStats.map(\.cpuValue)
        let memHistoryPercent = historyStats.map(\.mpValue)
        let memHistoryUsed = historyStats.compactMap(\.memoryUsedGb)

        VStack(alignment: .leading, spacing: 12) {
            BeszelSectionHeader(icon: "square.stack.3d.up", title: localizer.t.beszelResources)

            // CPU
            ResourceCard(
                icon: "cpu",
                iconColor: beszelColor,
                title: localizer.t.beszelCpu,
                percent: info.cpuValue,
                history: cpuHistory.count > 3 ? cpuHistory : nil,
                barColor: beszelColor
            )
            .onTapGesture { showCpuDetail = true }

            // Memory
            ResourceCard(
                icon: "memorychip",
                iconColor: memoryColor,
                title: localizer.t.beszelRam,
                percent: info.mpValue,
                history: memHistoryUsed.count > 2 ? memHistoryUsed : (memHistoryPercent.count > 3 ? memHistoryPercent : nil),
                barColor: memoryColor,
                detailLeft: latestStats?.memoryUsedGb.flatMap { used in
                    latestStats?.memoryTotalGb.map { total in
                        "\(BeszelFormatters.formatGB(used)) / \(BeszelFormatters.formatGB(total))"
                    }
                },
                detailRight: nil
            )
            .onTapGesture { expandedResource = .memory }

            let diskItems = buildDiskItems(info: info)
            if !diskItems.isEmpty {
                DiskResourceCard(
                    dp: info.dpValue,
                    items: diskItems,
                    localizer: localizer,
                    onDiskFsClick: { fs in
                        expandedDiskFs = fs
                    }
                )
            }
        }
    }


    // MARK: - Containers Link

    private var containersLink: some View {
        NavigationLink(destination: BeszelContainersView(instanceId: instanceId, systemId: systemId, records: records)) {
            HStack(spacing: 12) {
                Image(systemName: "shippingbox")
                    .font(.body)
                    .foregroundStyle(beszelColor)
                    .frame(width: 36, height: 36)
                    .background(beszelColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                Text(localizer.t.beszelContainers)
                    .font(.headline)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
            }
            .padding(16)
            .contentShape(Rectangle())
            .glassCard()
        }
        .buttonStyle(.plain)
    }

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

    private func firstNonEmpty(_ primary: String?, _ fallback: String?) -> String? {
        if let p = primary, !p.isEmpty { return p }
        if let f = fallback, !f.isEmpty { return f }
        return nil
    }

    // MARK: - Fetch
    
    @MainActor
    private func fetchAll() async {
        do {
            guard let client = await servicesStore.beszelClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let s = try await client.getSystem(id: systemId)
            system = s
        } catch {
            if system == nil {
                fetchError = error.localizedDescription
                showFetchError = true
            }
        }

        guard let client = await servicesStore.beszelClient(instanceId: instanceId) else {
            isLoading = false
            return
        }

        // Non-critical fetches in parallel
        async let recordsResult = client.getSystemRecords(systemId: systemId, limit: 60)
        async let detailsResult = client.getSystemDetails(systemId: systemId)
        async let smartResult = client.getSmartDevices(systemId: systemId)

        if let r = try? await recordsResult { self.records = r.items }
        if let d = try? await detailsResult { self.systemDetails = d }
        
        do {
            let s = try await smartResult
            self.smartDevices = s
        } catch {
            print("Beszel: Failed to fetch SMART devices for \(systemId): \(error)")
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
            if let left = detailLeft {
                HStack {
                    Text(left)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                    Spacer()
                    if let right = detailRight {
                        Text(right)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
            }

            // Mini graph
            if let history, history.count > 3 {
                MiniLineGraph(data: history, color: barColor, height: 40)
            }
        }
        .padding(16)
        .glassCard()
    }
}

// MARK: - Disk Resource Card (root + external)

private struct DiskResourceCard: View {
    let dp: Double
    let items: [DiskFsUsage]
    let localizer: Localizer
    let onDiskFsClick: (DiskFsUsage) -> Void

    private func usageColor(_ value: Double) -> Color {
        if value > 90 { return AppTheme.stopped }
        if value > 70 { return AppTheme.warning }
        return AppTheme.running
    }

    var body: some View {
        let totalUsed = max(items.reduce(0.0) { $0 + $1.usedGb }, 0)
        let totalCapacity = max(items.reduce(0.0) { $0 + $1.totalGb }, 0)
        let overallPercent = totalCapacity > 0 ? min(totalUsed / totalCapacity * 100, 100) : min(max(dp, 0), 100)
        let overallColor = usageColor(overallPercent)

        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "internaldrive")
                    .font(.title3)
                    .foregroundStyle(AppTheme.warning)
                    .frame(width: 36, height: 36)
                    .background(AppTheme.warning.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                Text(localizer.t.beszelDisk)
                    .font(.subheadline.bold())
                    .lineLimit(1)

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text(Formatters.formatPercent(overallPercent))
                        .font(.title3.bold())
                        .foregroundStyle(overallColor)
                    if totalCapacity > 0 {
                        Text("\(BeszelFormatters.formatGB(totalUsed)) / \(BeszelFormatters.formatGB(totalCapacity))")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
            }

            ForEach(items) { fs in
                let percent = fs.percent
                Button {
                    onDiskFsClick(fs)
                } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 8) {
                            Text(fs.label)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                                .lineLimit(1)
                            Spacer()
                            Text(Formatters.formatPercent(percent))
                                .font(.caption2.bold())
                                .foregroundStyle(usageColor(percent))
                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 3)
                                    .fill(.white.opacity(0.08))
                                    .frame(height: 6)
                                RoundedRectangle(cornerRadius: 3)
                                    .fill(usageColor(percent).gradient)
                                    .frame(width: geo.size.width * CGFloat(min(percent, 100)) / 100, height: 6)
                            }
                        }
                        .frame(height: 6)

                        Text("\(BeszelFormatters.formatGB(fs.usedGb)) \(localizer.t.beszelUsed) / \(BeszelFormatters.formatGB(fs.totalGb)) \(localizer.t.beszelTotal)")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
                .buttonStyle(.plain)
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

extension BeszelSystemDetail {
    // MARK: - Private formatters

    private func formatBeszelSize(_ val: Double, compact: Bool = false) -> String {
        if val == 0 { return "0" }

        // Fallback per valori già in bytes (es. traffico o vecchie API)
        if val > 10000 {
            let formatted = Formatters.formatBytes(val)
            return compact ? formatted.replacingOccurrences(of: "B", with: "").replacingOccurrences(of: " ", with: "") : formatted
        }

        if val < 0.01 { return compact ? "<0.01" : "< 0.01 \(localizer.t.unitGB)" }
        if val < 1 {
            let mb = String(format: "%.0f", val * 1024)
            return compact ? mb : "\(mb) \(localizer.t.unitMB)"
        }
        let gb = String(format: "%.1f", val)
        return compact ? gb : "\(gb) \(localizer.t.unitGB)"
    }

    private func formatNetRate(_ val: Double) -> String {
        if val == 0 { return "0 B/s" }
        return "\(Formatters.formatBytes(val))/s"
    }

    private func formatUptimeHours(_ seconds: Double) -> String {
        let days = Int(seconds / 86400)
        let hours = Int(seconds.truncatingRemainder(dividingBy: 86400) / 3600)
        if days > 0 { return "\(days)\(localizer.t.unitDays) \(hours)\(localizer.t.unitHours)" }
        let minutes = Int(seconds.truncatingRemainder(dividingBy: 3600) / 60)
        if hours > 0 { return "\(hours)\(localizer.t.unitHours) \(minutes)\(localizer.t.unitMinutes)" }
        return "\(minutes)\(localizer.t.unitMinutes)"
    }
}
