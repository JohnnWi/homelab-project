import Charts
import SwiftUI

struct ProxmoxNodeDetailView: View {
    let instanceId: UUID
    let nodeName: String

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var nodeStatus: ProxmoxNodeStatus?
    @State private var vms: [ProxmoxVM] = []
    @State private var lxcs: [ProxmoxLXC] = []
    @State private var storages: [ProxmoxStorage] = []
    @State private var tasks: [ProxmoxTask] = []
    @State private var performanceHistory: [ProxmoxRRDData] = []
    @State private var performanceTimeframe: ProxmoxRRDTimeframe = .day
    @State private var performanceLoading = false
    @State private var performanceError: String?
    @State private var state: LoadableState<Void> = .idle

    private let proxmoxColor = ServiceType.proxmox.colors.primary
    private var regularVMs: [ProxmoxVM] {
        vms.filter { !$0.isTemplate }
    }
    private var regularLXCs: [ProxmoxLXC] {
        lxcs.filter { !$0.isTemplate }
    }
    private var templateGuests: [ProxmoxNodeTemplateGuest] {
        let vmTemplates = vms
            .filter(\.isTemplate)
            .map {
                ProxmoxNodeTemplateGuest(
                    vmid: $0.vmid,
                    guestType: .qemu,
                    displayName: $0.displayName,
                    tags: $0.tagList,
                    lock: $0.lock
                )
            }
        let lxcTemplates = lxcs
            .filter(\.isTemplate)
            .map {
                ProxmoxNodeTemplateGuest(
                    vmid: $0.vmid,
                    guestType: .lxc,
                    displayName: $0.displayName,
                    tags: $0.tagList,
                    lock: $0.lock
                )
            }

        return (vmTemplates + lxcTemplates).sorted {
            if $0.guestType != $1.guestType {
                return $0.guestType.rawValue < $1.guestType.rawValue
            }
            return $0.vmid < $1.vmid
        }
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchAll
        ) {
            // System info
            if let status = nodeStatus {
                systemInfoSection(status)
            }

            // Resource gauges
            if let status = nodeStatus {
                resourceGaugesSection(status)
            }

            performanceSection

            // VMs
            if !regularVMs.isEmpty {
                vmListSection
            }

            // LXCs
            if !regularLXCs.isEmpty {
                lxcListSection
            }

            if !templateGuests.isEmpty {
                templateListSection
            }

            // Storage
            if !storages.isEmpty {
                storageSection
            }

            // Quick actions
            quickActionsSection

            // Recent tasks
            if !tasks.isEmpty {
                tasksSection
            }
        }
        .navigationTitle(nodeName)
        .navigationDestination(for: ProxmoxRoute.self) { route in
            switch route {
            case .guestDetail(let instanceId, let nodeName, let vmid, let guestType):
                ProxmoxGuestDetailView(instanceId: instanceId, nodeName: nodeName, vmid: vmid, guestType: guestType)
            case .storageContent(let instanceId, let nodeName, let storageName, let storageType):
                ProxmoxStorageContentView(instanceId: instanceId, nodeName: nodeName, storageName: storageName, storageType: storageType)
            case .firewall(let instanceId, let scope):
                ProxmoxFirewallView(instanceId: instanceId, scope: scope.toScope)
            case .network(let instanceId, let nodeName):
                ProxmoxNetworkView(instanceId: instanceId, nodeName: nodeName)
            case .services(let instanceId, let nodeName):
                ProxmoxServicesView(instanceId: instanceId, nodeName: nodeName)
            case .taskLog(let instanceId, let nodeName, let task):
                ProxmoxTaskLogView(instanceId: instanceId, nodeName: nodeName, task: task)
            case .ceph(let instanceId, let nodeName):
                ProxmoxCephView(instanceId: instanceId, nodeName: nodeName)
            case .createGuest(let instanceId, let preferredNode):
                ProxmoxCreateGuestView(instanceId: instanceId, preferredNode: preferredNode)
            default:
                EmptyView()
            }
        }
        .task { await fetchAll() }
        .task(id: performanceTimeframe) {
            guard state.value != nil,
                  let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            await loadPerformanceHistory(using: client)
        }
    }

    // MARK: - System Info

    private func systemInfoSection(_ status: ProxmoxNodeStatus) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxSystemInfo.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 0) {
                infoRow(label: localizer.t.proxmoxNode, value: nodeName)
                Divider().padding(.leading, 16)
                infoRow(label: localizer.t.proxmoxKernel, value: status.kversion ?? "-")
                Divider().padding(.leading, 16)
                infoRow(label: localizer.t.proxmoxPveLabel, value: status.pveversion ?? "-")
                Divider().padding(.leading, 16)
                if let cpuInfo = status.cpuinfo {
                    infoRow(label: localizer.t.proxmoxCpu, value: "\(cpuInfo.model ?? "-") (\(cpuInfo.cores ?? 0)C/\(cpuInfo.sockets ?? 1)S)")
                    Divider().padding(.leading, 16)
                }
                infoRow(label: localizer.t.proxmoxUptime, value: formatUptime(status.uptime))
                if let loadavg = status.loadavg, loadavg.count >= 3 {
                    Divider().padding(.leading, 16)
                    infoRow(label: localizer.t.proxmoxLoadAverage, value: "\(loadavg[0]), \(loadavg[1]), \(loadavg[2])")
                }
            }
            .glassCard()
        }
    }

    private func infoRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
                .lineLimit(2)
                .multilineTextAlignment(.trailing)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    // MARK: - Resource Gauges

    private func resourceGaugesSection(_ status: ProxmoxNodeStatus) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxResources.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                circularGauge(
                    label: localizer.t.proxmoxCpu,
                    percent: (status.cpu ?? 0) * 100,
                    detail: String(format: "%.1f%%", (status.cpu ?? 0) * 100),
                    color: proxmoxColor
                )
                circularGauge(
                    label: localizer.t.proxmoxRam,
                    percent: status.memory?.usedPercent ?? 0,
                    detail: "\(Formatters.formatBytes(Double(status.memory?.used ?? 0))) / \(Formatters.formatBytes(Double(status.memory?.total ?? 0)))",
                    color: AppTheme.info
                )
                circularGauge(
                    label: localizer.t.proxmoxSwap,
                    percent: status.swap?.usedPercent ?? 0,
                    detail: "\(Formatters.formatBytes(Double(status.swap?.used ?? 0))) / \(Formatters.formatBytes(Double(status.swap?.total ?? 0)))",
                    color: .purple
                )
                circularGauge(
                    label: localizer.t.proxmoxDisk,
                    percent: status.rootfs?.usedPercent ?? 0,
                    detail: "\(Formatters.formatBytes(Double(status.rootfs?.used ?? 0))) / \(Formatters.formatBytes(Double(status.rootfs?.total ?? 0)))",
                    color: AppTheme.paused
                )
            }
        }
    }

    private func circularGauge(label: String, percent: Double, detail: String, color: Color) -> some View {
        VStack(spacing: 10) {
            ZStack {
                Circle()
                    .stroke(color.opacity(0.15), lineWidth: 6)
                Circle()
                    .trim(from: 0, to: min(percent / 100, 1))
                    .stroke(color, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                    .rotationEffect(.degrees(-90))
                    .animation(.easeOut(duration: 0.6), value: percent)
                VStack(spacing: 2) {
                    Text(String(format: "%.0f%%", percent))
                        .font(.system(size: 16, weight: .bold, design: .rounded))
                    Text(label)
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
            .frame(width: 80, height: 80)

            Text(detail)
                .font(.caption2)
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(16)
        .glassCard()
    }

    // MARK: - Performance

    private var performanceSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxPerformance.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                Picker(localizer.t.proxmoxTimeframe, selection: $performanceTimeframe) {
                    Text(localizedPerformanceTimeframe(.hour)).tag(ProxmoxRRDTimeframe.hour)
                    Text(localizedPerformanceTimeframe(.day)).tag(ProxmoxRRDTimeframe.day)
                    Text(localizedPerformanceTimeframe(.week)).tag(ProxmoxRRDTimeframe.week)
                }
                .pickerStyle(.segmented)
                .frame(maxWidth: 190)
            }

            if let latestSample {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    historySummaryCard(
                        label: localizer.t.proxmoxCpu,
                        value: String(format: "%.1f%%", latestSample.cpuPercent),
                        icon: "cpu",
                        color: proxmoxColor
                    )
                    historySummaryCard(
                        label: localizer.t.proxmoxRam,
                        value: String(format: "%.0f%%", latestSample.memoryPercent),
                        icon: "memorychip",
                        color: AppTheme.info
                    )
                    historySummaryCard(
                        label: localizer.t.proxmoxTraffic,
                        value: formattedRate(latestSample.networkRate),
                        icon: "network",
                        color: .green
                    )
                    historySummaryCard(
                        label: localizer.t.proxmoxDiskActivity,
                        value: formattedRate(latestSample.diskRate),
                        icon: "internaldrive.fill",
                        color: .orange
                    )
                }
            }

            if performanceLoading && performanceHistory.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)
                    .glassCard()
            } else if !utilizationSeries.isEmpty {
                NodePerformanceChart(
                    title: localizer.t.proxmoxUtilization,
                    timeframe: performanceTimeframe,
                    series: utilizationSeries,
                    valueFormatter: { String(format: "%.0f%%", $0) }
                )
                NodePerformanceChart(
                    title: localizer.t.proxmoxTraffic,
                    timeframe: performanceTimeframe,
                    series: trafficSeries,
                    valueFormatter: { formattedRate($0) }
                )
                NodePerformanceChart(
                    title: localizer.t.proxmoxDiskActivity,
                    timeframe: performanceTimeframe,
                    series: diskSeries,
                    valueFormatter: { formattedRate($0) }
                )
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .font(.title3)
                        .foregroundStyle(AppTheme.textMuted)
                    Text(performanceError ?? localizer.t.proxmoxNoMetricsAvailable)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 20)
                .glassCard()
            }
        }
    }

    private func historySummaryCard(label: String, value: String, icon: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.body.bold())
                .foregroundStyle(color)
                .frame(width: 32, height: 32)
                .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 9, style: .continuous))

            Text(value)
                .font(.headline.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.caption2.bold())
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassCard()
    }

    // MARK: - VM List

    private var vmListSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxVMs.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            ForEach(regularVMs.sorted(by: { $0.vmid < $1.vmid })) { vm in
                NavigationLink(value: ProxmoxRoute.guestDetail(instanceId: instanceId, nodeName: nodeName, vmid: vm.vmid, guestType: .qemu)) {
                    compactGuestRow(name: vm.displayName, vmid: vm.vmid, status: vm.status, icon: "desktopcomputer", cpuPercent: vm.cpuPercent, memPercent: vm.memPercent)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - LXC List

    private var lxcListSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxContainers.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            ForEach(regularLXCs.sorted(by: { $0.vmid < $1.vmid })) { lxc in
                NavigationLink(value: ProxmoxRoute.guestDetail(instanceId: instanceId, nodeName: nodeName, vmid: lxc.vmid, guestType: .lxc)) {
                    compactGuestRow(name: lxc.displayName, vmid: lxc.vmid, status: lxc.status, icon: "shippingbox.fill", cpuPercent: lxc.cpuPercent, memPercent: lxc.memPercent)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Templates

    private var templateListSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxTemplates.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                Text("\(templateGuests.count)")
                    .font(.caption2.bold())
                    .foregroundStyle(.indigo)
            }

            ForEach(templateGuests) { template in
                NavigationLink(value: ProxmoxRoute.guestDetail(instanceId: instanceId, nodeName: nodeName, vmid: template.vmid, guestType: template.guestType)) {
                    compactTemplateRow(template)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Storage

    private var storageSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxStorage.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            ForEach(storages) { storage in
                NavigationLink(value: ProxmoxRoute.storageContent(instanceId: instanceId, nodeName: nodeName, storageName: storage.storage, storageType: storage.type)) {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Image(systemName: "externaldrive.fill")
                                .font(.subheadline)
                                .foregroundStyle(proxmoxColor)
                            Text(storage.storage)
                                .font(.subheadline.bold())
                                .foregroundStyle(.primary)
                            Spacer()
                            Text(storage.type ?? "")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(AppTheme.textMuted.opacity(0.1), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                            Image(systemName: "chevron.right")
                                .font(.caption2.bold())
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        if let total = storage.total, total > 0 {
                            HStack {
                                Text("\(Formatters.formatBytes(Double(storage.used ?? 0))) / \(Formatters.formatBytes(Double(total)))")
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                                Spacer()
                                Text(String(format: "%.0f%%", storage.usedPercent))
                                    .font(.caption.bold())
                                    .foregroundStyle(storage.usedPercent > 90 ? .red : proxmoxColor)
                            }

                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(proxmoxColor.opacity(0.15))
                                    RoundedRectangle(cornerRadius: 3)
                                        .fill(storage.usedPercent > 90 ? Color.red : proxmoxColor)
                                        .frame(width: geo.size.width * min(storage.usedPercent / 100, 1))
                                }
                            }
                            .frame(height: 6)
                        }

                        if !storage.contentTypes.isEmpty {
                            HStack(spacing: 4) {
                                ForEach(storage.contentTypes, id: \.self) { ct in
                                    Text(ct)
                                        .font(.system(size: 9, weight: .medium))
                                        .foregroundStyle(AppTheme.textMuted)
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(AppTheme.textMuted.opacity(0.08), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                                }
                            }
                        }
                    }
                    .padding(12)
                    .glassCard()
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Tasks

    // MARK: - Quick Actions

    private var quickActionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxActions.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                NavigationLink(value: ProxmoxRoute.createGuest(instanceId: instanceId, preferredNode: nodeName)) {
                    quickActionButton(icon: "plus.circle.fill", label: localizer.t.proxmoxCreateGuest)
                }
                .buttonStyle(.plain)

                NavigationLink(value: ProxmoxRoute.network(instanceId: instanceId, nodeName: nodeName)) {
                    quickActionButton(icon: "network", label: localizer.t.proxmoxNetwork)
                }
                .buttonStyle(.plain)

                NavigationLink(value: ProxmoxRoute.services(instanceId: instanceId, nodeName: nodeName)) {
                    quickActionButton(icon: "gearshape.2.fill", label: localizer.t.proxmoxServices)
                }
                .buttonStyle(.plain)

                NavigationLink(value: ProxmoxRoute.firewall(instanceId: instanceId, scope: .node(nodeName))) {
                    quickActionButton(icon: "flame.fill", label: localizer.t.proxmoxFirewall)
                }
                .buttonStyle(.plain)

                NavigationLink(value: ProxmoxRoute.ceph(instanceId: instanceId, nodeName: nodeName)) {
                    quickActionButton(icon: "circle.grid.2x2.fill", label: localizer.t.proxmoxCeph)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func quickActionButton(icon: String, label: String) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(proxmoxColor)
            Text(label)
                .font(.caption2.bold())
                .foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .glassCard()
    }

    // MARK: - Tasks

    private var tasksSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxTasks.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 0) {
                ForEach(tasks.prefix(10)) { task in
                    NavigationLink(value: ProxmoxRoute.taskLog(instanceId: instanceId, nodeName: nodeName, task: task)) {
                        HStack(spacing: 10) {
                            Circle()
                                .fill(task.isRunning ? .blue : (task.isOk ? AppTheme.running : AppTheme.stopped))
                                .frame(width: 8, height: 8)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(task.type ?? localizer.t.proxmoxTaskLog)
                                    .font(.subheadline.weight(.medium))
                                    .foregroundStyle(.primary)
                                HStack(spacing: 6) {
                                    Text(task.user ?? "-")
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                    Text("·")
                                        .foregroundStyle(AppTheme.textMuted)
                                    Text(task.formattedStart)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                }
                            }

                            Spacer()

                            Text(task.isRunning ? localizer.t.proxmoxRunning : (task.exitstatus ?? task.status ?? ""))
                                .font(.caption2.bold())
                                .foregroundStyle(task.isRunning ? .blue : (task.isOk ? AppTheme.running : AppTheme.stopped))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(
                                    (task.isRunning ? Color.blue : (task.isOk ? AppTheme.running : AppTheme.stopped)).opacity(0.1),
                                    in: RoundedRectangle(cornerRadius: 6, style: .continuous)
                                )

                            Image(systemName: "chevron.right")
                                .font(.caption2.bold())
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                    }
                    .buttonStyle(.plain)

                    if task.id != tasks.prefix(10).last?.id {
                        Divider().padding(.leading, 30)
                    }
                }
            }
            .glassCard()
        }
    }

    // MARK: - Helper Views

    private func compactGuestRow(name: String, vmid: Int, status: String?, icon: String, cpuPercent: Double, memPercent: Double) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(status == "running" ? proxmoxColor : AppTheme.textMuted)
                .frame(width: 28, height: 28)
                .background((status == "running" ? proxmoxColor : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 7, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(name)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text("#\(vmid)")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }

                HStack(spacing: 4) {
                    Circle()
                        .fill(statusColor(status))
                        .frame(width: 5, height: 5)
                    Text(localizedStatus(status))
                        .font(.caption2)
                        .foregroundStyle(statusColor(status))
                }
            }

            Spacer()

            if status?.lowercased() == "running" {
                VStack(alignment: .trailing, spacing: 2) {
                    Text(String(format: "%@ %.0f%%", localizer.t.proxmoxCpuLabel, cpuPercent))
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textSecondary)
                    Text(String(format: "%@ %.0f%%", localizer.t.proxmoxRamLabel, memPercent))
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textSecondary)
                }
            }

            Image(systemName: "chevron.right")
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(12)
        .glassCard()
    }

    private func compactTemplateRow(_ template: ProxmoxNodeTemplateGuest) -> some View {
        HStack(spacing: 10) {
            Image(systemName: template.guestType == .qemu ? "square.stack.3d.up.fill" : "shippingbox.circle.fill")
                .font(.caption)
                .foregroundStyle(.indigo)
                .frame(width: 28, height: 28)
                .background(Color.indigo.opacity(0.12), in: RoundedRectangle(cornerRadius: 7, style: .continuous))

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 4) {
                    Text(template.displayName)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text("#\(template.vmid)")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }

                HStack(spacing: 4) {
                    Text(localizer.t.proxmoxTemplate)
                        .font(.caption2.bold())
                        .foregroundStyle(.indigo)
                    Text(template.guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc)
                        .font(.caption2)
                        .foregroundStyle(proxmoxColor)
                    if let lock = template.lock, !lock.isEmpty {
                        Text("· \(localizer.t.proxmoxLocked): \(lock)")
                            .font(.caption2)
                            .foregroundStyle(.orange)
                    }
                }

                if !template.tags.isEmpty {
                    HStack(spacing: 4) {
                        ForEach(template.tags.prefix(3), id: \.self) { tag in
                            Text(tag)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundStyle(proxmoxColor)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        }
                    }
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(12)
        .glassCard(tint: Color.indigo.opacity(0.05))
    }

    // MARK: - Helpers

    private func formatUptime(_ seconds: Int?) -> String {
        guard let seconds, seconds > 0 else { return "-" }
        let days = seconds / 86400
        let hours = (seconds % 86400) / 3600
        let minutes = (seconds % 3600) / 60
        if days > 0 { return "\(days)d \(hours)h \(minutes)m" }
        if hours > 0 { return "\(hours)h \(minutes)m" }
        return "\(minutes)m"
    }

    private func localizedStatus(_ status: String?) -> String {
        switch status?.lowercased() {
        case "running":
            return localizer.t.proxmoxRunning
        case "stopped":
            return localizer.t.proxmoxStopped
        case "paused":
            return localizer.t.proxmoxPaused
        case .some(let value) where !value.isEmpty:
            return value.capitalized
        default:
            return localizer.t.proxmoxUnknown
        }
    }

    private func statusColor(_ status: String?) -> Color {
        switch status?.lowercased() {
        case "running":
            return AppTheme.running
        case "paused":
            return .yellow
        default:
            return AppTheme.stopped
        }
    }

    private var latestSample: ProxmoxRRDData? {
        performanceHistory.last(where: \.hasData)
    }

    private var utilizationSeries: [NodeMetricChartSeries] {
        [
            NodeMetricChartSeries(
                id: "cpu",
                label: localizer.t.proxmoxCpu,
                color: proxmoxColor,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date else { return nil }
                    return NodeMetricChartPoint(date: date, value: sample.cpuPercent)
                }
            ),
            NodeMetricChartSeries(
                id: "memory",
                label: localizer.t.proxmoxRam,
                color: AppTheme.info,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date else { return nil }
                    return NodeMetricChartPoint(date: date, value: sample.memoryPercent)
                }
            )
        ]
        .filter { !$0.points.isEmpty }
    }

    private var trafficSeries: [NodeMetricChartSeries] {
        [
            NodeMetricChartSeries(
                id: "netin",
                label: localizer.t.proxmoxIn,
                color: .green,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date, let value = sample.netin else { return nil }
                    return NodeMetricChartPoint(date: date, value: max(value, 0))
                }
            ),
            NodeMetricChartSeries(
                id: "netout",
                label: localizer.t.proxmoxOut,
                color: proxmoxColor,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date, let value = sample.netout else { return nil }
                    return NodeMetricChartPoint(date: date, value: max(value, 0))
                }
            )
        ]
        .filter { !$0.points.isEmpty }
    }

    private var diskSeries: [NodeMetricChartSeries] {
        [
            NodeMetricChartSeries(
                id: "diskread",
                label: localizer.t.proxmoxRead,
                color: .orange,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date, let value = sample.diskread else { return nil }
                    return NodeMetricChartPoint(date: date, value: max(value, 0))
                }
            ),
            NodeMetricChartSeries(
                id: "diskwrite",
                label: localizer.t.proxmoxWrite,
                color: .purple,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date, let value = sample.diskwrite else { return nil }
                    return NodeMetricChartPoint(date: date, value: max(value, 0))
                }
            )
        ]
        .filter { !$0.points.isEmpty }
    }

    private func formattedRate(_ bytesPerSecond: Double) -> String {
        "\(Formatters.formatBytes(bytesPerSecond))/s"
    }

    private func localizedPerformanceTimeframe(_ timeframe: ProxmoxRRDTimeframe) -> String {
        switch timeframe {
        case .hour:
            return localizer.t.proxmoxLastHour
        case .day:
            return localizer.t.proxmoxLastDay
        case .week:
            return localizer.t.proxmoxLastWeek
        }
    }

    // MARK: - Data Fetching

    private func fetchAll() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let statusTask = client.getNodeStatus(node: nodeName)
            async let vmsTask = client.getVMs(node: nodeName, includeTemplates: true)
            async let lxcsTask = client.getLXCs(node: nodeName, includeTemplates: true)
            async let storageTask = client.getStorage(node: nodeName)
            async let tasksTask = client.getTasks(node: nodeName, limit: 20)

            nodeStatus = try await statusTask
            vms = (try? await vmsTask) ?? []
            lxcs = (try? await lxcsTask) ?? []
            storages = (try? await storageTask) ?? []
            tasks = (try? await tasksTask) ?? []

            await loadPerformanceHistory(using: client)

            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func loadPerformanceHistory(using client: ProxmoxAPIClient) async {
        performanceLoading = true
        defer { performanceLoading = false }

        do {
            performanceHistory = try await client.getNodeRRDData(node: nodeName, timeframe: performanceTimeframe)
            performanceError = nil
        } catch {
            performanceHistory = []
            performanceError = error.localizedDescription
        }
    }
}

private struct ProxmoxNodeTemplateGuest: Identifiable, Hashable {
    let vmid: Int
    let guestType: ProxmoxGuestType
    let displayName: String
    let tags: [String]
    let lock: String?

    var id: String {
        "\(guestType.rawValue)-\(vmid)"
    }
}

private struct NodeMetricChartPoint: Identifiable {
    let date: Date
    let value: Double

    var id: TimeInterval { date.timeIntervalSince1970 }
}

private struct NodeMetricChartSeries: Identifiable {
    let id: String
    let label: String
    let color: Color
    let points: [NodeMetricChartPoint]
}

private struct NodePerformanceChart: View {
    let title: String
    let timeframe: ProxmoxRRDTimeframe
    let series: [NodeMetricChartSeries]
    let valueFormatter: (Double) -> String

    private var maxValue: Double {
        max(series.flatMap(\.points).map(\.value).max() ?? 0, 1)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Spacer()
                HStack(spacing: 10) {
                    ForEach(series) { item in
                        HStack(spacing: 4) {
                            Circle()
                                .fill(item.color)
                                .frame(width: 6, height: 6)
                            Text(item.label)
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                }
            }

            Chart {
                ForEach(series) { item in
                    ForEach(item.points) { point in
                        LineMark(
                            x: .value("Time", point.date),
                            y: .value(item.label, point.value)
                        )
                        .foregroundStyle(item.color)
                        .interpolationMethod(.catmullRom)
                    }
                }
            }
            .chartYScale(domain: 0...maxValue)
            .chartYAxis {
                AxisMarks(values: .automatic(desiredCount: 3)) { value in
                    AxisGridLine()
                    AxisValueLabel {
                        if let raw = value.as(Double.self) {
                            Text(valueFormatter(raw))
                                .font(.system(size: 9))
                        }
                    }
                }
            }
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 4)) { value in
                    AxisGridLine()
                    AxisValueLabel {
                        if let date = value.as(Date.self) {
                            switch timeframe {
                            case .week:
                                Text(date, format: .dateTime.month().day())
                            case .day:
                                Text(date, format: .dateTime.hour())
                            case .hour:
                                Text(date, format: .dateTime.hour().minute())
                            }
                        }
                    }
                }
            }
            .frame(height: 170)
        }
        .padding(16)
        .glassCard()
    }
}
