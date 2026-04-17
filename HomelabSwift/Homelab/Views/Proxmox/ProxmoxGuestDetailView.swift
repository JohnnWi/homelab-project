import Charts
import SwiftUI

struct ProxmoxGuestDetailView: View {
    let instanceId: UUID
    let nodeName: String
    let vmid: Int
    let guestType: ProxmoxGuestType

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var guestName: String = ""
    @State private var status: String = ""
    @State private var guestLock: String?
    @State private var isTemplateGuest = false
    @State private var cpuPercent: Double = 0
    @State private var memUsed: Int64 = 0
    @State private var memTotal: Int64 = 0
    @State private var diskUsed: Int64 = 0
    @State private var diskTotal: Int64 = 0
    @State private var netIn: Int64 = 0
    @State private var netOut: Int64 = 0
    @State private var uptime: Int = 0
    @State private var cpuCores: Int = 0
    @State private var tags: [String] = []
    @State private var config: ProxmoxGuestConfig?
    @State private var snapshots: [ProxmoxSnapshot] = []
    @State private var currentNodeName: String
    @State private var trackedTask: ProxmoxGuestTrackedTask?
    @State private var guestAgentInfo: ProxmoxGuestAgentInfo?
    @State private var guestAgentOSInfo: ProxmoxGuestAgentOSInfo?
    @State private var guestAgentHostname: String?
    @State private var guestAgentTimezone: ProxmoxGuestAgentTimezone?
    @State private var guestAgentInterfaces: [ProxmoxGuestAgentNetworkInterface] = []
    @State private var guestAgentUsers: [ProxmoxGuestAgentUser] = []
    @State private var guestAgentFilesystems: [ProxmoxGuestAgentFilesystem] = []
    @State private var guestAgentLoading = false
    @State private var guestAgentError: String?
    @State private var availableStorages: [ProxmoxStorage] = []
    @State private var availableNodes: [ProxmoxNode] = []
    @State private var availablePools: [ProxmoxPool] = []
    @State private var nextAvailableVmid: Int?
    @State private var cloneStorageOptions: [ProxmoxStorage] = []
    @State private var performanceHistory: [ProxmoxRRDData] = []
    @State private var performanceTimeframe: ProxmoxRRDTimeframe = .day
    @State private var performanceLoading = false
    @State private var performanceError: String?

    @State private var state: LoadableState<Void> = .idle
    @State private var actionInProgress: String?
    @State private var confirmAction: GuestAction?
    @State private var actionError: String?
    @State private var showSnapshotCreate = false
    @State private var showBackupSheet = false
    @State private var showCloneSheet = false
    @State private var showMigrateSheet = false
    @State private var newSnapshotName = ""
    @State private var newSnapshotDesc = ""
    @State private var includeRAM = false
    @State private var backupStorage = ""
    @State private var backupMode = "snapshot"
    @State private var backupCompress = "zstd"
    @State private var cloneName = ""
    @State private var cloneVmid = ""
    @State private var cloneFull = true
    @State private var cloneTargetNode = ""
    @State private var cloneTargetStorage = ""
    @State private var clonePool = ""
    @State private var migrateTargetNode = ""
    @State private var migrateOnline = true
    @State private var showConsole = false
    @State private var showConfigEdit = false
    @State private var configEditLoading = false
    @State private var configEditError: String?

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    init(instanceId: UUID, nodeName: String, vmid: Int, guestType: ProxmoxGuestType) {
        self.instanceId = instanceId
        self.nodeName = nodeName
        self.vmid = vmid
        self.guestType = guestType
        _currentNodeName = State(initialValue: nodeName)
    }

    private var isRunning: Bool { status.lowercased() == "running" }
    private var isStopped: Bool { status.lowercased() == "stopped" }
    private var hasActiveTask: Bool { trackedTask?.isRunning == true }
    private var guestKindLabel: String { guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc }
    private var hasGuestLock: Bool { !(guestLock?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true) }
    private var showsGuestAgentSection: Bool {
        guestType == .qemu && isRunning && (
            config?.guestAgentEnabled == true ||
            guestAgentLoading ||
            hasGuestAgentData ||
            guestAgentError != nil
        )
    }
    private var hasGuestAgentData: Bool {
        guestAgentInfo != nil ||
        guestAgentOSInfo != nil ||
        (guestAgentHostname?.isEmpty == false) ||
        guestAgentTimezone != nil ||
        !guestAgentInterfaces.isEmpty ||
        !guestAgentUsers.isEmpty ||
        !guestAgentFilesystems.isEmpty
    }
    private var guestAgentEnabledCommands: [ProxmoxGuestAgentCommand] {
        (guestAgentInfo?.supportedCommands ?? [])
            .filter { $0.enabled != false }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }
    private var guestAgentDisabledCommandCount: Int {
        (guestAgentInfo?.supportedCommands ?? []).filter { $0.enabled == false }.count
    }
    private var memPercent: Double {
        guard memTotal > 0 else { return 0 }
        return Double(memUsed) / Double(memTotal) * 100
    }
    private var backupStorageOptions: [ProxmoxStorage] {
        let storagesWithBackupContent = availableStorages.filter {
            $0.isEnabled && ($0.contentTypes.contains("backup") || $0.contentTypes.isEmpty)
        }
        return (storagesWithBackupContent.isEmpty ? availableStorages.filter(\.isEnabled) : storagesWithBackupContent)
            .sorted { $0.storage.localizedCaseInsensitiveCompare($1.storage) == .orderedAscending }
    }
    private var migrationTargets: [ProxmoxNode] {
        availableNodes
            .filter { $0.isOnline && $0.node != currentNodeName }
            .sorted { $0.node.localizedCaseInsensitiveCompare($1.node) == .orderedAscending }
    }
    private var visibleSnapshots: [ProxmoxSnapshot] {
        snapshots.filter { !$0.isCurrent }
    }
    private var currentSnapshot: ProxmoxSnapshot? {
        snapshots.first(where: \.isCurrent)
    }
    private var guestPlatformLabel: String {
        guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: { await fetchAll() }
        ) {
            // Status header
            statusHeaderSection

            // Resource stats
            if isRunning {
                resourceStatsSection
            }

            if isRunning || !performanceHistory.isEmpty || performanceLoading || performanceError != nil {
                performanceSection
            }

            if let trackedTask {
                currentOperationSection(trackedTask)
            }

            // Actions
            actionsSection

            // Configuration
            if let config {
                configSection(config)
            }

            if showsGuestAgentSection {
                guestAgentSection
            }

            // Snapshots
            snapshotsSection

            // Console
            if isRunning && !isTemplateGuest {
                consoleSection
            }

            // Firewall
            firewallLinkSection
        }
        .navigationTitle("\(guestName.isEmpty ? guestKindLabel : guestName) #\(vmid)")
        .navigationDestination(for: ProxmoxRoute.self) { route in
            switch route {
            case .firewall(let instanceId, let scope):
                ProxmoxFirewallView(instanceId: instanceId, scope: scope.toScope)
            case .taskLog(let instanceId, let nodeName, let task):
                ProxmoxTaskLogView(instanceId: instanceId, nodeName: nodeName, task: task)
            default:
                EmptyView()
            }
        }
        .alert(localizer.t.proxmoxConfirmAction, isPresented: .init(
            get: { confirmAction != nil },
            set: { if !$0 { confirmAction = nil } }
        )) {
            Button(localizer.t.cancel, role: .cancel) { confirmAction = nil }
            Button(confirmAction.map { $0.label(localizer) } ?? "", role: confirmAction?.isDestructive == true ? .destructive : nil) {
                if let action = confirmAction {
                    Task { await performAction(action) }
                }
            }
        } message: {
            Text(localizer.t.proxmoxConfirmMessage)
        }
        .alert(localizer.t.error, isPresented: .init(
            get: { actionError != nil },
            set: { if !$0 { actionError = nil } }
        )) {
            Button(localizer.t.done) { actionError = nil }
        } message: {
            Text(actionError ?? "")
        }
        .sheet(isPresented: $showSnapshotCreate) {
            createSnapshotSheet
        }
        .sheet(isPresented: $showBackupSheet) {
            ProxmoxGuestBackupSheet(
                isPresented: $showBackupSheet,
                backupStorage: $backupStorage,
                backupMode: $backupMode,
                backupCompress: $backupCompress,
                instanceId: instanceId,
                nodeName: currentNodeName,
                vmid: vmid,
                guestType: guestType,
                availableStorages: availableStorages,
                onBackup: { storage, mode, compress in
                    await performBackup(storage: storage, mode: mode, compress: compress)
                }
            )
        }
        .sheet(isPresented: $showCloneSheet) {
            ProxmoxGuestCloneSheet(
                isPresented: $showCloneSheet,
                cloneName: $cloneName,
                cloneVmid: $cloneVmid,
                cloneFull: $cloneFull,
                cloneTargetNode: $cloneTargetNode,
                cloneTargetStorage: $cloneTargetStorage,
                clonePool: $clonePool,
                instanceId: instanceId,
                nodeName: currentNodeName,
                vmid: vmid,
                guestType: guestType,
                guestName: guestName,
                nextAvailableVmid: nextAvailableVmid,
                availableNodes: availableNodes,
                availablePools: availablePools,
                onClone: { name, full, targetNode, storage, pool in
                    await performClone(name: name, full: full, targetNode: targetNode, storage: storage, pool: pool)
                },
                onRefreshVmid: {
                    await refreshSuggestedVmid()
                }
            )
        }
        .sheet(isPresented: $showMigrateSheet) {
            ProxmoxGuestMigrateSheet(
                isPresented: $showMigrateSheet,
                migrateTargetNode: $migrateTargetNode,
                migrateOnline: $migrateOnline,
                nodeName: currentNodeName,
                vmid: vmid,
                guestType: guestType,
                isRunning: isRunning,
                availableNodes: availableNodes,
                onMigrate: { targetNode, online in
                    await performMigration(targetNode: targetNode, online: online)
                }
            )
        }
        .sheet(isPresented: $showConsole) {
            consoleSheet
        }
        .sheet(isPresented: $showConfigEdit) {
            ProxmoxGuestConfigEditSheet(
                instanceId: instanceId,
                nodeName: currentNodeName,
                vmid: vmid,
                guestType: guestType,
                currentConfig: config
            ) { params in
                Task { await applyConfigChanges(params) }
            }
        }
        .task { await fetchAll() }
        .task(id: performanceTimeframe) {
            guard state.value != nil,
                  let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            await loadPerformanceHistory(using: client)
        }
        .task(id: trackedTask?.id) {
            guard trackedTask?.isRunning == true else { return }
            while true {
                try? await Task.sleep(for: .seconds(2.5))
                guard !Task.isCancelled else { break }
                await refreshTrackedTask()
                guard trackedTask?.isRunning == true else { break }
            }
        }
    }

    // MARK: - Status Header

    private var statusHeaderSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(systemName: guestType == .qemu ? "desktopcomputer" : "shippingbox.fill")
                    .font(.title2)
                    .foregroundStyle(isRunning ? proxmoxColor : AppTheme.textMuted)
                    .frame(width: 48, height: 48)
                .background((isRunning ? proxmoxColor : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(guestName.isEmpty ? "\(guestKindLabel) \(vmid)" : guestName)
                        .font(.title3.bold())
                    HStack(spacing: 8) {
                        HStack(spacing: 4) {
                            Circle()
                                .fill(isRunning ? AppTheme.running : (isStopped ? AppTheme.stopped : .yellow))
                                .frame(width: 8, height: 8)
                            Text(statusLabel(status))
                                .font(.subheadline.bold())
                                .foregroundStyle(isRunning ? AppTheme.running : (isStopped ? AppTheme.stopped : .yellow))
                        }

                        Text("\(localizer.t.proxmoxVmidLabel) \(vmid)")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)

                        Text(guestPlatformLabel)
                            .font(.caption2.bold())
                            .foregroundStyle(proxmoxColor)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                    }
                }

                Spacer()

                if isRunning {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(localizer.t.proxmoxUptime)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(formatUptime(uptime))
                            .font(.caption.bold())
                    }
                }
            }

            if !tags.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(tags, id: \.self) { tag in
                            tagChip(tag, color: proxmoxColor)
                        }
                    }
                }
            }

            if isTemplateGuest || hasGuestLock {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        if isTemplateGuest {
                            tagChip(localizer.t.proxmoxTemplate, color: .indigo)
                        }
                        if let guestLock, !guestLock.isEmpty {
                            tagChip("\(localizer.t.proxmoxLocked): \(guestLock)", color: .orange)
                        }
                    }
                }
            }
        }
    }

    private func tagChip(_ label: String, color: Color) -> some View {
        Text(label)
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
    }

    // MARK: - Resource Stats

    private var resourceStatsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxResources.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                resourceCard(icon: "cpu", label: localizer.t.proxmoxCpu, value: String(format: "%.1f%%", cpuPercent), detail: "\(cpuCores) \(localizer.t.proxmoxCpuCores)", color: proxmoxColor)
                resourceCard(icon: "memorychip", label: localizer.t.proxmoxRam, value: String(format: "%.0f%%", memPercent), detail: "\(Formatters.formatBytes(Double(memUsed))) / \(Formatters.formatBytes(Double(memTotal)))", color: AppTheme.info)
                resourceCard(icon: "externaldrive.fill", label: localizer.t.proxmoxDisk, value: Formatters.formatBytes(Double(diskUsed)), detail: "/ \(Formatters.formatBytes(Double(diskTotal)))", color: AppTheme.paused)
                resourceCard(icon: "network", label: localizer.t.proxmoxNetwork, value: "↓\(Formatters.formatBytes(Double(netIn)))", detail: "↑\(Formatters.formatBytes(Double(netOut)))", color: .green)
            }
        }
    }

    private func resourceCard(icon: String, label: String, value: String, detail: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: icon)
                .font(.body.bold())
                .foregroundStyle(color)
                .frame(width: 32, height: 32)
                .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 9, style: .continuous))

            Text(value)
                .font(.headline.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(detail)
                .font(.caption2)
                .foregroundStyle(AppTheme.textSecondary)
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

            if let latestHistorySample {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    historySummaryCard(
                        label: localizer.t.proxmoxCpu,
                        value: String(format: "%.1f%%", latestHistorySample.cpuPercent),
                        icon: "cpu",
                        color: proxmoxColor
                    )
                    historySummaryCard(
                        label: localizer.t.proxmoxRam,
                        value: String(format: "%.0f%%", latestHistorySample.memoryPercent),
                        icon: "memorychip",
                        color: AppTheme.info
                    )
                    historySummaryCard(
                        label: localizer.t.proxmoxTraffic,
                        value: formattedRate(latestHistorySample.networkRate),
                        icon: "network",
                        color: .green
                    )
                    historySummaryCard(
                        label: localizer.t.proxmoxDiskActivity,
                        value: formattedRate(latestHistorySample.diskRate),
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
                GuestPerformanceChart(
                    title: localizer.t.proxmoxUtilization,
                    timeframe: performanceTimeframe,
                    series: utilizationSeries,
                    valueFormatter: { String(format: "%.0f%%", $0) }
                )
                GuestPerformanceChart(
                    title: localizer.t.proxmoxTraffic,
                    timeframe: performanceTimeframe,
                    series: trafficSeries,
                    valueFormatter: { formattedRate($0) }
                )
                GuestPerformanceChart(
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

    // MARK: - Actions

    private func currentOperationSection(_ operation: ProxmoxGuestTrackedTask) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxCurrentOperation.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    Image(systemName: operation.iconName)
                        .font(.title3.bold())
                        .foregroundStyle(operation.tintColor)
                        .frame(width: 42, height: 42)
                        .background(operation.tintColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .symbolEffect(.pulse, isActive: operation.isRunning)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(operation.title)
                            .font(.headline)
                        Text(operation.statusLabel(localizer))
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                    }

                    Spacer()

                    Text(operation.statusLabel(localizer))
                        .font(.caption.bold())
                        .foregroundStyle(operation.tintColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(operation.tintColor.opacity(0.12), in: Capsule())
                }

                VStack(spacing: 0) {
                    operationInfoRow(label: localizer.t.proxmoxTaskIdentifier, value: operation.reference.upid, multiline: true)
                    Divider().padding(.leading, 16)
                    operationInfoRow(label: localizer.t.proxmoxSourceNode, value: operation.sourceNode)
                    if let targetNode = operation.targetNode {
                        Divider().padding(.leading, 16)
                        operationInfoRow(label: localizer.t.proxmoxTargetNode, value: targetNode)
                    }
                    Divider().padding(.leading, 16)
                    operationInfoRow(label: localizer.t.proxmoxNode, value: currentNodeName)
                    Divider().padding(.leading, 16)
                    operationInfoRow(label: localizer.t.proxmoxLastUpdate, value: operation.formattedLastUpdated)
                    if let exitstatus = operation.task.exitstatus, !operation.isRunning {
                        Divider().padding(.leading, 16)
                        operationInfoRow(label: localizer.t.proxmoxExitStatus, value: exitstatus)
                    }
                }
                .glassCard()

                NavigationLink(
                    value: ProxmoxRoute.taskLog(
                        instanceId: instanceId,
                        nodeName: operation.taskNode,
                        task: operation.navigationTask
                    )
                ) {
                    HStack(spacing: 10) {
                        Image(systemName: "doc.text.magnifyingglass")
                            .font(.body.bold())
                            .foregroundStyle(proxmoxColor)
                        Text(localizer.t.proxmoxOpenTaskLog)
                            .font(.subheadline.bold())
                            .foregroundStyle(proxmoxColor)
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .padding(14)
                    .glassCard(tint: proxmoxColor.opacity(0.08))
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var actionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxActions.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            if isTemplateGuest || hasGuestLock {
                VStack(alignment: .leading, spacing: 8) {
                    if isTemplateGuest {
                        guestNoticeRow(icon: "square.on.square", text: localizer.t.proxmoxTemplateGuestHint, color: .indigo)
                    }
                    if hasGuestLock {
                        guestNoticeRow(icon: "lock.fill", text: localizer.t.proxmoxLockedGuestHint, color: .orange)
                    }
                }
                .padding(14)
                .glassCard()
            }

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                if isStopped && !isTemplateGuest {
                    actionButton(action: .start)
                }
                if isRunning && !isTemplateGuest {
                    actionButton(action: .shutdown)
                    actionButton(action: .reboot)
                    actionButton(action: .stop)
                }
                if isRunning && guestType == .qemu && !isTemplateGuest {
                    actionButton(action: .suspend)
                }
                if status.lowercased() == "paused" && guestType == .qemu && !isTemplateGuest {
                    actionButton(action: .resume)
                }
                if isStopped && !isTemplateGuest {
                    actionButton(action: .template)
                }
                if !backupStorageOptions.isEmpty && !isTemplateGuest {
                    actionButton(action: .backup)
                }
                actionButton(action: .clone)
                if !migrationTargets.isEmpty && !isTemplateGuest {
                    actionButton(action: .migrate)
                }
            }
        }
    }

    private func guestNoticeRow(icon: String, text: String, color: Color) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.caption.bold())
                .foregroundStyle(color)
                .frame(width: 20, height: 20)
                .background(color.opacity(0.12), in: Circle())
            Text(text)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
        }
    }

    private func actionButton(action: GuestAction) -> some View {
        Button {
            triggerAction(action)
        } label: {
            VStack(spacing: 6) {
                if actionInProgress == action.id {
                    ProgressView()
                        .controlSize(.small)
                        .frame(width: 24, height: 24)
                } else {
                    Image(systemName: action.icon)
                        .font(.body.bold())
                        .foregroundStyle(action.color)
                        .frame(width: 24, height: 24)
                }
                Text(action.label(localizer))
                    .font(.caption2.bold())
                    .foregroundStyle(action.color)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .glassCard(tint: action.color.opacity(0.08))
        }
        .buttonStyle(.plain)
        .disabled(actionInProgress != nil || hasActiveTask || hasGuestLock)
    }

    // MARK: - Config

    private func configSection(_ config: ProxmoxGuestConfig) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxConfiguration.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                Button {
                    showConfigEdit = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "pencil")
                            .font(.caption)
                        Text(localizer.t.actionEdit)
                            .font(.caption.bold())
                    }
                    .foregroundStyle(proxmoxColor)
                }
            }

            configSubsection(title: localizer.t.proxmoxGuestOverview) {
                VStack(spacing: 0) {
                    if let description = config.description, !description.isEmpty {
                        configRow(label: localizer.t.proxmoxDescription, value: description, multiline: true)
                        Divider().padding(.leading, 16)
                    }
                    if let cores = config.cores {
                        configRow(label: localizer.t.proxmoxCpuCores, value: "\(cores)")
                        Divider().padding(.leading, 16)
                    }
                    if let sockets = config.sockets {
                        configRow(label: localizer.t.proxmoxSockets, value: "\(sockets)")
                        Divider().padding(.leading, 16)
                    }
                    if let memory = config.memory {
                        configRow(label: localizer.t.proxmoxRam, value: "\(memory) MB")
                        Divider().padding(.leading, 16)
                    }
                    if let ostype = config.ostype {
                        configRow(label: localizer.t.proxmoxOs, value: ostype)
                        Divider().padding(.leading, 16)
                    }
                    if let cpu = config.cpu {
                        configRow(label: localizer.t.proxmoxCpuType, value: cpu)
                        Divider().padding(.leading, 16)
                    }
                    if let bios = config.bios {
                        configRow(label: localizer.t.proxmoxBios, value: bios.uppercased())
                        Divider().padding(.leading, 16)
                    }
                    if let machine = config.machine {
                        configRow(label: localizer.t.proxmoxMachine, value: machine)
                        Divider().padding(.leading, 16)
                    }
                    if let scsihw = config.scsihw {
                        configRow(label: localizer.t.proxmoxScsi, value: scsihw)
                        Divider().padding(.leading, 16)
                    }
                    if let boot = config.boot, !boot.isEmpty {
                        configRow(label: localizer.t.proxmoxBootOrder, value: boot)
                        Divider().padding(.leading, 16)
                    }
                    if let startup = config.startup, !startup.isEmpty {
                        configRow(label: localizer.t.proxmoxStartupPolicy, value: startup)
                        Divider().padding(.leading, 16)
                    }
                    if let agentEnabled = config.guestAgentEnabled {
                        configRow(label: localizer.t.proxmoxAgent, value: agentEnabled ? localizer.t.proxmoxEnabled : localizer.t.proxmoxDisabled)
                        Divider().padding(.leading, 16)
                    }
                    if let balloon = config.balloon {
                        configRow(label: localizer.t.proxmoxBallooning, value: balloon > 0 ? "\(balloon) MB" : localizer.t.proxmoxDisabled)
                        Divider().padding(.leading, 16)
                    }
                    if let hotplug = config.hotplug, !hotplug.isEmpty {
                        configRow(label: localizer.t.proxmoxHotplug, value: hotplug)
                        Divider().padding(.leading, 16)
                    }
                    if let numa = config.numa {
                        configRow(label: localizer.t.proxmoxNuma, value: numa == 1 ? localizer.t.proxmoxEnabled : localizer.t.proxmoxDisabled)
                        Divider().padding(.leading, 16)
                    }
                    configRow(label: localizer.t.proxmoxBootOnStart, value: config.onboot == 1 ? localizer.t.yes : localizer.t.no)
                    if let protection = config.protection {
                        Divider().padding(.leading, 16)
                        configRow(label: localizer.t.proxmoxProtection, value: protection == 1 ? localizer.t.yes : localizer.t.no)
                    }
                }
                .glassCard()
            }

            configSubsection(title: localizer.t.proxmoxDisks) {
                if config.diskDevices.isEmpty {
                    emptyConfigCard(localizer.t.proxmoxNoDevices)
                } else {
                    ForEach(config.diskDevices) { device in
                        guestDiskCard(device)
                    }
                }
            }

            configSubsection(title: localizer.t.proxmoxInterfaces) {
                if config.networkInterfaces.isEmpty {
                    emptyConfigCard(localizer.t.proxmoxNoNetworkInterfaces)
                } else {
                    ForEach(config.networkInterfaces) { interface in
                        guestInterfaceCard(interface)
                    }
                }
            }

            if !config.rawConfigEntries.isEmpty {
                configSubsection(title: localizer.t.proxmoxRawConfiguration) {
                    DisclosureGroup(localizer.t.proxmoxRawConfiguration) {
                        VStack(alignment: .leading, spacing: 10) {
                            ForEach(config.rawConfigEntries, id: \.key) { item in
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(item.key)
                                        .font(.caption.bold())
                                        .foregroundStyle(AppTheme.textMuted)
                                    Text(item.value)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textSecondary)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                                if item.key != config.rawConfigEntries.last?.key {
                                    Divider()
                                }
                            }
                        }
                        .padding(.top, 8)
                    }
                    .font(.subheadline.weight(.semibold))
                    .padding(14)
                    .glassCard()
                }
            }
        }
    }

    private func configSubsection<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.subheadline.weight(.semibold))
            content()
        }
    }

    private func emptyConfigCard(_ message: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "tray")
                .foregroundStyle(AppTheme.textMuted)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassCard()
    }

    private func guestDiskCard(_ device: ProxmoxGuestDiskDevice) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "internaldrive.fill")
                    .font(.body.bold())
                    .foregroundStyle(.orange)
                    .frame(width: 34, height: 34)
                    .background(Color.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(device.displayName)
                        .font(.subheadline.bold())
                    if let storage = device.storage {
                        Text(storage)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
                Spacer()
                if let size = device.size {
                    Text(size)
                        .font(.caption.bold())
                        .foregroundStyle(.orange)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.orange.opacity(0.12), in: Capsule())
                }
            }

            if let volume = device.volume {
                Text(volume)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                    .textSelection(.enabled)
            }

            VStack(spacing: 0) {
                if let mountPoint = device.mountPoint {
                    configRow(label: localizer.t.proxmoxMountPoint, value: mountPoint)
                    Divider().padding(.leading, 16)
                }
                if let media = device.media {
                    configRow(label: localizer.t.proxmoxMode, value: media)
                    Divider().padding(.leading, 16)
                }
                if let backupEnabled = device.backupEnabled {
                    configRow(label: localizer.t.actionBackup, value: backupEnabled ? localizer.t.proxmoxEnabled : localizer.t.proxmoxDisabled)
                    Divider().padding(.leading, 16)
                }
                if let replicateEnabled = device.replicateEnabled {
                    configRow(label: localizer.t.proxmoxReplicationJobs, value: replicateEnabled ? localizer.t.proxmoxEnabled : localizer.t.proxmoxDisabled)
                    Divider().padding(.leading, 16)
                }
                if let discardEnabled = device.discardEnabled {
                    configRow(label: localizer.t.proxmoxDiscard, value: discardEnabled ? localizer.t.proxmoxEnabled : localizer.t.proxmoxDisabled)
                    Divider().padding(.leading, 16)
                }
                if let ssdEnabled = device.ssdEnabled {
                    configRow(label: localizer.t.proxmoxSsd, value: ssdEnabled ? localizer.t.proxmoxEnabled : localizer.t.proxmoxDisabled)
                } else {
                    configRow(label: localizer.t.proxmoxRawConfiguration, value: device.rawValue, multiline: true)
                }
            }
            .glassCard()
        }
    }

    private func guestInterfaceCard(_ interface: ProxmoxGuestNetworkInterface) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "network")
                    .font(.body.bold())
                    .foregroundStyle(.green)
                    .frame(width: 34, height: 34)
                    .background(Color.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(interface.displayName)
                        .font(.subheadline.bold())
                    if let model = interface.model {
                        Text(model.uppercased())
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
                Spacer()
                if let bridge = interface.bridge {
                    Text(bridge)
                        .font(.caption.bold())
                        .foregroundStyle(.green)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.green.opacity(0.12), in: Capsule())
                }
            }

            VStack(spacing: 0) {
                if let macAddress = interface.macAddress {
                    configRow(label: localizer.t.proxmoxMacAddress, value: macAddress)
                    Divider().padding(.leading, 16)
                }
                if let address = interface.ipAddress {
                    configRow(label: localizer.t.proxmoxAddress, value: address)
                    Divider().padding(.leading, 16)
                }
                if let ipv6Address = interface.ipv6Address {
                    configRow(label: localizer.t.proxmoxIpv6, value: ipv6Address)
                    Divider().padding(.leading, 16)
                }
                if let gateway = interface.gateway {
                    configRow(label: localizer.t.proxmoxGateway, value: gateway)
                    Divider().padding(.leading, 16)
                }
                if let gateway6 = interface.gateway6 {
                    configRow(label: localizer.t.proxmoxIpv6Gateway, value: gateway6)
                    Divider().padding(.leading, 16)
                }
                if let vlanTag = interface.vlanTag {
                    configRow(label: localizer.t.proxmoxVlanTag, value: vlanTag)
                    Divider().padding(.leading, 16)
                }
                if let rateLimit = interface.rateLimit {
                    configRow(label: localizer.t.proxmoxRateLimit, value: rateLimit)
                    Divider().padding(.leading, 16)
                }
                if let firewallEnabled = interface.firewallEnabled {
                    configRow(label: localizer.t.proxmoxFirewall, value: firewallEnabled ? localizer.t.proxmoxEnabled : localizer.t.proxmoxDisabled)
                    Divider().padding(.leading, 16)
                }
                configRow(label: localizer.t.proxmoxRawConfiguration, value: interface.rawIpConfig ?? interface.rawValue, multiline: true)
            }
            .glassCard()
        }
    }

    private func configRow(label: String, value: String, multiline: Bool = false) -> some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.medium))
                .lineLimit(multiline ? 4 : 1)
                .multilineTextAlignment(.trailing)
                .textSelection(.enabled)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // MARK: - Guest Agent

    private var guestAgentSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxAgent.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    Image(systemName: "wave.3.forward.circle.fill")
                        .font(.title3.bold())
                        .foregroundStyle(guestAgentTintColor)
                        .frame(width: 42, height: 42)
                        .background(guestAgentTintColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .symbolEffect(.pulse, isActive: guestAgentLoading)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(localizer.t.proxmoxAgent)
                            .font(.headline)
                        Text(guestAgentStatusText)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                    }

                    Spacer()

                    if guestAgentLoading {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Text(guestAgentStatusText)
                            .font(.caption.bold())
                            .foregroundStyle(guestAgentTintColor)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(guestAgentTintColor.opacity(0.12), in: Capsule())
                    }
                }

                VStack(spacing: 0) {
                    if let hostname = guestAgentHostname, !hostname.isEmpty {
                        operationInfoRow(label: localizer.t.proxmoxHostname, value: hostname)
                        if guestAgentOSInfo?.displayName != "-" || guestAgentInfo?.version?.isEmpty == false || guestAgentOSInfo?.displayVersion?.isEmpty == false || guestAgentOSInfo?.displayKernel?.isEmpty == false || guestAgentTimezone != nil {
                            Divider().padding(.leading, 16)
                        }
                    }
                    if let osName = guestAgentOSInfo?.displayName, osName != "-" {
                        operationInfoRow(label: localizer.t.proxmoxOs, value: osName)
                        if guestAgentInfo?.version?.isEmpty == false || guestAgentOSInfo?.displayVersion?.isEmpty == false || guestAgentOSInfo?.displayKernel?.isEmpty == false || guestAgentTimezone != nil {
                            Divider().padding(.leading, 16)
                        }
                    }
                    if let agentVersion = guestAgentInfo?.version, !agentVersion.isEmpty {
                        operationInfoRow(label: localizer.t.proxmoxAgentVersion, value: agentVersion)
                        if guestAgentOSInfo?.displayVersion?.isEmpty == false || guestAgentOSInfo?.displayKernel?.isEmpty == false || guestAgentTimezone != nil {
                            Divider().padding(.leading, 16)
                        }
                    }
                    if let version = guestAgentOSInfo?.displayVersion, !version.isEmpty {
                        operationInfoRow(label: localizer.t.settingsVersion, value: version)
                        if guestAgentOSInfo?.displayKernel?.isEmpty == false || guestAgentTimezone != nil {
                            Divider().padding(.leading, 16)
                        }
                    }
                    if let kernel = guestAgentOSInfo?.displayKernel, !kernel.isEmpty {
                        operationInfoRow(label: localizer.t.proxmoxKernel, value: kernel, multiline: true)
                        if guestAgentTimezone != nil {
                            Divider().padding(.leading, 16)
                        }
                    }
                    if let timezone = guestAgentTimezone {
                        operationInfoRow(label: localizer.t.proxmoxGuestTimezone, value: timezone.displayName, multiline: true)
                    }
                }
                .glassCard()

                if let error = guestAgentError, !error.isEmpty {
                    guestNoticeRow(icon: "exclamationmark.triangle.fill", text: error, color: .orange)
                        .padding(14)
                        .glassCard()
                }

                if !guestAgentLoading || hasGuestAgentData || guestAgentError != nil {
                    if guestAgentInfo != nil {
                        configSubsection(title: localizer.t.proxmoxGuestCommands) {
                            if guestAgentEnabledCommands.isEmpty && guestAgentDisabledCommandCount == 0 {
                                emptyConfigCard(localizer.t.proxmoxNoGuestCommands)
                            } else {
                                DisclosureGroup {
                                    VStack(alignment: .leading, spacing: 10) {
                                        ForEach(guestAgentEnabledCommands) { command in
                                            HStack(alignment: .firstTextBaseline, spacing: 10) {
                                                Image(systemName: command.successResponse == false ? "bolt.slash.fill" : "checkmark.circle.fill")
                                                    .font(.caption.bold())
                                                    .foregroundStyle(command.successResponse == false ? .orange : AppTheme.running)
                                                Text(command.name)
                                                    .font(.caption.weight(.semibold))
                                                    .foregroundStyle(AppTheme.textSecondary)
                                                    .textSelection(.enabled)
                                                Spacer(minLength: 8)
                                            }
                                            if command.id != guestAgentEnabledCommands.last?.id {
                                                Divider()
                                            }
                                        }
                                    }
                                    .padding(.top, 8)
                                } label: {
                                    HStack(spacing: 12) {
                                        guestAgentCompactMetric(
                                            label: localizer.t.proxmoxEnabled,
                                            value: "\(guestAgentEnabledCommands.count)",
                                            color: AppTheme.running
                                        )
                                        guestAgentCompactMetric(
                                            label: localizer.t.proxmoxDisabled,
                                            value: "\(guestAgentDisabledCommandCount)",
                                            color: guestAgentDisabledCommandCount == 0 ? AppTheme.textMuted : .orange
                                        )
                                    }
                                }
                                .font(.subheadline.weight(.semibold))
                                .padding(14)
                                .glassCard()
                            }
                        }
                    }

                    configSubsection(title: localizer.t.proxmoxGuestUsers) {
                        if guestAgentUsers.isEmpty {
                            emptyConfigCard(localizer.t.proxmoxNoGuestUsers)
                        } else {
                            ForEach(guestAgentUsers) { user in
                                guestAgentUserCard(user)
                            }
                        }
                    }

                    configSubsection(title: localizer.t.proxmoxGuestFilesystems) {
                        if guestAgentFilesystems.isEmpty {
                            emptyConfigCard(localizer.t.proxmoxNoGuestFilesystems)
                        } else {
                            ForEach(guestAgentFilesystems) { filesystem in
                                guestAgentFilesystemCard(filesystem)
                            }
                        }
                    }

                    if !guestAgentInterfaces.isEmpty {
                        configSubsection(title: localizer.t.proxmoxInterfaces) {
                            ForEach(guestAgentInterfaces) { interface in
                                guestAgentInterfaceCard(interface)
                            }
                        }
                    }
                }
            }
        }
    }

    private var guestAgentStatusText: String {
        if guestAgentLoading {
            return localizer.t.loading
        }
        if guestAgentError != nil && !hasGuestAgentData {
            return localizer.t.notAvailable
        }
        return localizer.t.proxmoxActive
    }

    private var guestAgentTintColor: Color {
        if guestAgentLoading {
            return proxmoxColor
        }
        if guestAgentError != nil && !hasGuestAgentData {
            return .orange
        }
        return AppTheme.running
    }

    private func guestAgentCompactMetric(label: String, value: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundStyle(color)
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
    }

    private func guestAgentUserCard(_ user: ProxmoxGuestAgentUser) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "person.crop.circle.fill")
                    .font(.body.bold())
                    .foregroundStyle(proxmoxColor)
                    .frame(width: 34, height: 34)
                    .background(proxmoxColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(user.user)
                        .font(.subheadline.bold())
                    if let domain = user.domain, !domain.isEmpty {
                        Text(domain)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }

                Spacer()
            }

            VStack(spacing: 0) {
                operationInfoRow(label: localizer.t.proxmoxUser, value: user.displayName, multiline: true)
                if user.loginDate != nil {
                    Divider().padding(.leading, 16)
                    operationInfoRow(label: localizer.t.proxmoxLoginTime, value: formattedGuestAgentDate(user.loginDate), multiline: true)
                }
            }
            .glassCard()
        }
    }

    private func guestAgentFilesystemCard(_ filesystem: ProxmoxGuestAgentFilesystem) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: "externaldrive.fill")
                    .font(.body.bold())
                    .foregroundStyle(.orange)
                    .frame(width: 34, height: 34)
                    .background(Color.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(filesystem.mountpoint)
                        .font(.subheadline.bold())
                    Text("\(filesystem.type) • \(filesystem.name)")
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                }

                Spacer()

                if filesystem.usedBytes != nil, let capacityBytes = filesystem.capacityBytes, capacityBytes > 0 {
                    Text("\(Int((filesystem.usagePercent * 100).rounded()))%")
                        .font(.caption.bold())
                        .foregroundStyle(.orange)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.orange.opacity(0.12), in: Capsule())
                }
            }

            if let usedBytes = filesystem.usedBytes, let capacityBytes = filesystem.capacityBytes, capacityBytes > 0 {
                VStack(alignment: .leading, spacing: 8) {
                    ProgressView(value: filesystem.usagePercent)
                        .tint(.orange)
                    HStack {
                        Text("\(Formatters.formatBytes(Double(usedBytes))) / \(Formatters.formatBytes(Double(capacityBytes)))")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                        Spacer()
                        Text(String(format: "%.0f%%", filesystem.usagePercent * 100))
                            .font(.caption.bold())
                            .foregroundStyle(.orange)
                    }
                }
            }

            VStack(spacing: 0) {
                operationInfoRow(label: localizer.t.proxmoxMountPoint, value: filesystem.mountpoint, multiline: true)
                Divider().padding(.leading, 16)
                operationInfoRow(label: localizer.t.proxmoxType, value: filesystem.type)
                if let diskSummary = filesystem.diskSummary, !diskSummary.isEmpty {
                    Divider().padding(.leading, 16)
                    operationInfoRow(label: localizer.t.proxmoxDisk, value: diskSummary, multiline: true)
                }
            }
            .glassCard()
        }
    }

    private func guestAgentInterfaceCard(_ interface: ProxmoxGuestAgentNetworkInterface) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Image(systemName: "point.3.connected.trianglepath.dotted")
                    .font(.body.bold())
                    .foregroundStyle(.green)
                    .frame(width: 34, height: 34)
                    .background(Color.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(interface.name)
                        .font(.subheadline.bold())
                    if let hardwareAddress = interface.hardwareAddress, !hardwareAddress.isEmpty {
                        Text(hardwareAddress)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }

                Spacer()

                Text("\(interface.visibleAddresses.count)")
                    .font(.caption.bold())
                    .foregroundStyle(.green)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.green.opacity(0.12), in: Capsule())
            }

            VStack(spacing: 0) {
                ForEach(Array(interface.visibleAddresses.enumerated()), id: \.element.id) { index, address in
                    operationInfoRow(label: address.type.uppercased(), value: address.displayLabel)
                    if index < interface.visibleAddresses.count - 1 {
                        Divider().padding(.leading, 16)
                    }
                }
            }
            .glassCard()
        }
    }

    // MARK: - Snapshots

    private var snapshotsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxSnapshots.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                Button {
                    HapticManager.light()
                    newSnapshotName = ""
                    newSnapshotDesc = ""
                    includeRAM = false
                    showSnapshotCreate = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                        Text(localizer.t.proxmoxCreateSnapshot)
                    }
                    .font(.caption.bold())
                    .foregroundStyle(proxmoxColor)
                }
                .disabled(actionInProgress != nil || hasActiveTask || isTemplateGuest || hasGuestLock)
            }

            if let currentSnapshot {
                HStack(spacing: 10) {
                    Image(systemName: "camera.metering.center.weighted")
                        .foregroundStyle(.indigo)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(localizer.t.proxmoxCurrentSnapshot)
                            .font(.caption.bold())
                            .foregroundStyle(.indigo)
                        Text(currentSnapshot.formattedDate)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    Spacer()
                }
                .padding(12)
                .glassCard(tint: Color.indigo.opacity(0.06))
            }

            if visibleSnapshots.isEmpty {
                HStack {
                    Image(systemName: "camera.on.rectangle")
                        .foregroundStyle(AppTheme.textMuted)
                    Text(localizer.t.proxmoxNoSnapshots)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textMuted)
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.vertical, 20)
                .glassCard()
            } else {
                ForEach(visibleSnapshots) { snapshot in
                    HStack(spacing: 12) {
                        Image(systemName: snapshot.hasVMState ? "camera.fill" : "camera")
                            .font(.subheadline)
                            .foregroundStyle(proxmoxColor)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(snapshot.name)
                                .font(.subheadline.bold())
                            HStack(spacing: 6) {
                                Text(snapshot.formattedDate)
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                                if snapshot.hasVMState {
                                    Text(localizer.t.proxmoxRam)
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundStyle(.purple)
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 1)
                                        .background(Color.purple.opacity(0.1), in: RoundedRectangle(cornerRadius: 3, style: .continuous))
                                }
                            }
                            if let desc = snapshot.description, !desc.isEmpty {
                                Text(desc)
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .lineLimit(2)
                            }
                        }

                        Spacer()

                        // Rollback
                        Button {
                            HapticManager.medium()
                            Task { await rollbackSnapshot(snapshot.name) }
                        } label: {
                            Image(systemName: "arrow.uturn.backward")
                                .font(.caption)
                                .foregroundStyle(AppTheme.info)
                                .frame(width: 28, height: 28)
                                .background(AppTheme.info.opacity(0.1), in: Circle())
                        }
                        .buttonStyle(.plain)
                        .disabled(actionInProgress != nil || hasActiveTask || isTemplateGuest || hasGuestLock)

                        // Delete
                        Button {
                            HapticManager.medium()
                            Task { await deleteSnapshot(snapshot.name) }
                        } label: {
                            Image(systemName: "trash")
                                .font(.caption)
                                .foregroundStyle(AppTheme.stopped)
                                .frame(width: 28, height: 28)
                                .background(AppTheme.stopped.opacity(0.1), in: Circle())
                        }
                        .buttonStyle(.plain)
                        .disabled(actionInProgress != nil || hasActiveTask || isTemplateGuest || hasGuestLock)
                    }
                    .padding(12)
                    .glassCard()
                }
            }
        }
    }

    // MARK: - Console

    private var consoleSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxConsole.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            Button {
                HapticManager.medium()
                showConsole = true
            } label: {
                HStack(spacing: 10) {
                    Image(systemName: "terminal.fill")
                        .font(.body.bold())
                        .foregroundStyle(proxmoxColor)
                    Text(localizer.t.proxmoxOpenConsole)
                        .font(.subheadline.bold())
                        .foregroundStyle(proxmoxColor)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                }
                .padding(14)
                .glassCard(tint: proxmoxColor.opacity(0.08))
            }
            .buttonStyle(.plain)
            .disabled(hasActiveTask)
        }
    }

    // MARK: - Firewall Link

    private var firewallLinkSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            NavigationLink(value: ProxmoxRoute.firewall(instanceId: instanceId, scope: .guest(node: currentNodeName, vmid: vmid, guestType: guestType))) {
                HStack(spacing: 10) {
                    Image(systemName: "flame.fill")
                        .font(.body.bold())
                        .foregroundStyle(.orange)
                    Text(localizer.t.proxmoxFirewallRules)
                        .font(.subheadline.bold())
                        .foregroundStyle(.orange)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                }
                .padding(14)
                .glassCard(tint: Color.orange.opacity(0.06))
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Create Snapshot Sheet

    private var createSnapshotSheet: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(localizer.t.proxmoxSnapshotName, text: $newSnapshotName)
                    TextField(localizer.t.proxmoxSnapshotDescription, text: $newSnapshotDesc)
                    if guestType == .qemu {
                        Toggle(localizer.t.proxmoxIncludeRAM, isOn: $includeRAM)
                    }
                }

                Section {
                    Button(localizer.t.proxmoxCreateSnapshot) {
                        Task {
                            showSnapshotCreate = false
                            await createSnapshot()
                        }
                    }
                    .disabled(newSnapshotName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .navigationTitle(localizer.t.proxmoxCreateSnapshot)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { showSnapshotCreate = false }
                }
            }
        }
        .presentationDetents([.medium])
    }

    // MARK: - Console Sheet

    private var consoleSheet: some View {
        NavigationStack {
            ProxmoxConsoleView(instanceId: instanceId, nodeName: currentNodeName, vmid: vmid, guestType: guestType)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(localizer.t.close) { showConsole = false }
                    }
                }
        }
    }

    // MARK: - Actions

    private func triggerAction(_ action: GuestAction) {
        HapticManager.medium()
        switch action {
        case .backup:
            if backupStorage.isEmpty {
                backupStorage = backupStorageOptions.first?.storage ?? ""
            }
            showBackupSheet = true
        case .clone:
            cloneName = guestName.isEmpty ? "" : "\(guestName)-clone"
            cloneVmid = nextAvailableVmid.map(String.init) ?? ""
            cloneFull = true
            cloneTargetNode = currentNodeName
            cloneTargetStorage = ""
            clonePool = ""
            showCloneSheet = true
            Task { await loadCloneStorageOptions(for: cloneTargetNode) }
        case .migrate:
            migrateTargetNode = migrationTargets.first?.node ?? ""
            migrateOnline = isRunning
            showMigrateSheet = true
        default:
            confirmAction = action
        }
    }

    private func performAction(_ action: GuestAction) async {
        actionInProgress = action.id
        defer { actionInProgress = nil }

        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            let reference: ProxmoxTaskReference
            switch (guestType, action) {
            case (.qemu, .start):    reference = try await client.startVM(node: currentNodeName, vmid: vmid)
            case (.qemu, .stop):     reference = try await client.stopVM(node: currentNodeName, vmid: vmid)
            case (.qemu, .shutdown): reference = try await client.shutdownVM(node: currentNodeName, vmid: vmid)
            case (.qemu, .reboot):   reference = try await client.rebootVM(node: currentNodeName, vmid: vmid)
            case (.qemu, .suspend):  reference = try await client.suspendVM(node: currentNodeName, vmid: vmid)
            case (.qemu, .resume):   reference = try await client.resumeVM(node: currentNodeName, vmid: vmid)
            case (.qemu, .template): reference = try await client.convertVMToTemplate(node: currentNodeName, vmid: vmid)
            case (.lxc, .start):     reference = try await client.startLXC(node: currentNodeName, vmid: vmid)
            case (.lxc, .stop):      reference = try await client.stopLXC(node: currentNodeName, vmid: vmid)
            case (.lxc, .shutdown):  reference = try await client.shutdownLXC(node: currentNodeName, vmid: vmid)
            case (.lxc, .reboot):    reference = try await client.rebootLXC(node: currentNodeName, vmid: vmid)
            case (.lxc, .template):  reference = try await client.convertLXCToTemplate(node: currentNodeName, vmid: vmid)
            default: return
            }
            HapticManager.success()
            await beginTrackingTask(
                title: action.label(localizer),
                kind: action == .template ? .template : .lifecycle,
                reference: reference,
                sourceNode: currentNodeName
            )
        } catch {
            presentActionError(error)
        }
    }

    private func performBackup(storage: String, mode: String, compress: String) async {
        guard !storage.isEmpty else { return }
        actionInProgress = GuestAction.backup.id
        defer { actionInProgress = nil }

        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            let reference = try await client.createBackup(
                node: currentNodeName,
                vmid: vmid,
                guestType: guestType.rawValue,
                storage: storage,
                mode: mode,
                compress: compress
            )
            HapticManager.success()
            await beginTrackingTask(
                title: localizer.t.actionBackup,
                kind: .backup,
                reference: reference,
                sourceNode: currentNodeName
            )
        } catch {
            presentActionError(error)
        }
    }

    private func performClone(name: String, full: Bool, targetNode: String, storage: String, pool: String) async {
        guard let newVmid = Int(cloneVmid.trimmingCharacters(in: .whitespacesAndNewlines)) else { return }
        actionInProgress = GuestAction.clone.id
        defer { actionInProgress = nil }

        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            let resolvedName = name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : name.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedTargetNode = targetNode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? currentNodeName : targetNode.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedStorage = storage.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : storage.trimmingCharacters(in: .whitespacesAndNewlines)
            let resolvedPool = pool.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : pool.trimmingCharacters(in: .whitespacesAndNewlines)
            let reference: ProxmoxTaskReference
            if guestType == .qemu {
                reference = try await client.cloneVM(
                    node: currentNodeName,
                    vmid: vmid,
                    newVmid: newVmid,
                    name: resolvedName,
                    full: full,
                    targetNode: resolvedTargetNode,
                    storage: resolvedStorage,
                    pool: resolvedPool
                )
            } else {
                reference = try await client.cloneLXC(
                    node: currentNodeName,
                    vmid: vmid,
                    newVmid: newVmid,
                    name: resolvedName,
                    full: full,
                    targetNode: resolvedTargetNode,
                    storage: resolvedStorage,
                    pool: resolvedPool
                )
            }
            HapticManager.success()
            await beginTrackingTask(
                title: localizer.t.proxmoxClone,
                kind: .clone,
                reference: reference,
                sourceNode: currentNodeName,
                targetNode: resolvedTargetNode == currentNodeName ? nil : resolvedTargetNode
            )
        } catch {
            presentActionError(error)
        }
    }

    private func performMigration(targetNode: String, online: Bool) async {
        guard !targetNode.isEmpty else { return }
        actionInProgress = GuestAction.migrate.id
        defer { actionInProgress = nil }

        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            let reference: ProxmoxTaskReference
            if guestType == .qemu {
                reference = try await client.migrateVM(node: currentNodeName, vmid: vmid, targetNode: targetNode, online: online && isRunning)
            } else {
                reference = try await client.migrateLXC(node: currentNodeName, vmid: vmid, targetNode: targetNode, online: online && isRunning)
            }
            HapticManager.success()
            await beginTrackingTask(
                title: localizer.t.proxmoxMigrate,
                kind: .migrate,
                reference: reference,
                sourceNode: currentNodeName,
                targetNode: targetNode
            )
        } catch {
            presentActionError(error)
        }
    }

    private func createSnapshot() async {
        let name = newSnapshotName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        actionInProgress = "snapshot-create"
        defer { actionInProgress = nil }

        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            let reference: ProxmoxTaskReference
            if guestType == .qemu {
                reference = try await client.createVMSnapshot(node: currentNodeName, vmid: vmid, name: name, description: newSnapshotDesc, includeRAM: includeRAM)
            } else {
                reference = try await client.createLXCSnapshot(node: currentNodeName, vmid: vmid, name: name, description: newSnapshotDesc)
            }
            HapticManager.success()
            await beginTrackingTask(
                title: localizer.t.proxmoxCreateSnapshot,
                kind: .snapshot,
                reference: reference,
                sourceNode: currentNodeName
            )
        } catch {
            presentActionError(error)
        }
    }

    private func rollbackSnapshot(_ snapname: String) async {
        actionInProgress = "snapshot-rollback-\(snapname)"
        defer { actionInProgress = nil }

        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            let reference: ProxmoxTaskReference
            if guestType == .qemu {
                reference = try await client.rollbackVMSnapshot(node: currentNodeName, vmid: vmid, snapname: snapname)
            } else {
                reference = try await client.rollbackLXCSnapshot(node: currentNodeName, vmid: vmid, snapname: snapname)
            }
            HapticManager.success()
            await beginTrackingTask(
                title: localizer.t.proxmoxRollbackSnapshot,
                kind: .snapshot,
                reference: reference,
                sourceNode: currentNodeName
            )
        } catch {
            presentActionError(error)
        }
    }

    private func deleteSnapshot(_ snapname: String) async {
        actionInProgress = "snapshot-delete-\(snapname)"
        defer { actionInProgress = nil }

        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            let reference: ProxmoxTaskReference
            if guestType == .qemu {
                reference = try await client.deleteVMSnapshot(node: currentNodeName, vmid: vmid, snapname: snapname)
            } else {
                reference = try await client.deleteLXCSnapshot(node: currentNodeName, vmid: vmid, snapname: snapname)
            }
            HapticManager.success()
            await beginTrackingTask(
                title: localizer.t.proxmoxDeleteSnapshot,
                kind: .snapshot,
                reference: reference,
                sourceNode: currentNodeName
            )
        } catch {
            presentActionError(error)
        }
    }

    private func presentActionError(_ error: Error) {
        HapticManager.error()
        actionError = error.localizedDescription
    }

    private func operationInfoRow(label: String, value: String, multiline: Bool = false) -> some View {
        HStack(alignment: multiline ? .top : .center, spacing: 12) {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)

            Spacer(minLength: 12)

            Text(value)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
                .multilineTextAlignment(.trailing)
                .lineLimit(multiline ? 3 : 1)
                .textSelection(.enabled)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private func beginTrackingTask(
        title: String,
        kind: ProxmoxGuestTrackedTask.Kind,
        reference: ProxmoxTaskReference,
        sourceNode: String,
        targetNode: String? = nil
    ) async {
        let placeholderTask = ProxmoxTask(
            upid: reference.upid,
            type: title,
            status: "running",
            starttime: Int(Date().timeIntervalSince1970),
            node: reference.node ?? sourceNode
        )

        await MainActor.run {
            trackedTask = ProxmoxGuestTrackedTask(
                title: title,
                kind: kind,
                reference: reference,
                sourceNode: sourceNode,
                targetNode: targetNode,
                task: placeholderTask,
                lastUpdated: Date()
            )
        }

        await refreshTrackedTask()
    }

    private func refreshTrackedTask() async {
        guard let operation = trackedTask,
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }

        do {
            let refreshedTask = try await client.getTaskStatus(node: operation.taskNode, upid: operation.reference.upid)
            let updatedOperation = operation.updating(with: refreshedTask)

            await MainActor.run {
                trackedTask = updatedOperation
                if updatedOperation.didCompleteMigration {
                    currentNodeName = updatedOperation.targetNode ?? currentNodeName
                }
            }

            guard updatedOperation.didFinish else { return }

            if updatedOperation.task.isOk {
                HapticManager.success()
            } else {
                HapticManager.error()
            }
            await fetchAll(showLoading: false)
        } catch {
            await MainActor.run {
                trackedTask = operation.touchingLastUpdated()
            }
        }
    }

    private func statusLabel(_ value: String?) -> String {
        switch value?.lowercased() {
        case "running":
            return localizer.t.proxmoxRunning
        case "stopped":
            return localizer.t.proxmoxStopped
        case "paused":
            return localizer.t.proxmoxPaused
        case .some(let status) where !status.isEmpty:
            return status.capitalized
        default:
            return localizer.t.proxmoxUnknown
        }
    }

    private var latestHistorySample: ProxmoxRRDData? {
        performanceHistory.last(where: \.hasData)
    }

    private var utilizationSeries: [GuestMetricChartSeries] {
        [
            GuestMetricChartSeries(
                id: "cpu",
                label: localizer.t.proxmoxCpu,
                color: proxmoxColor,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date else { return nil }
                    return GuestMetricChartPoint(date: date, value: sample.cpuPercent)
                }
            ),
            GuestMetricChartSeries(
                id: "memory",
                label: localizer.t.proxmoxRamLabel,
                color: AppTheme.info,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date else { return nil }
                    return GuestMetricChartPoint(date: date, value: sample.memoryPercent)
                }
            )
        ]
        .filter { !$0.points.isEmpty }
    }

    private var trafficSeries: [GuestMetricChartSeries] {
        [
            GuestMetricChartSeries(
                id: "netin",
                label: localizer.t.proxmoxIn,
                color: .green,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date, let value = sample.netin else { return nil }
                    return GuestMetricChartPoint(date: date, value: max(value, 0))
                }
            ),
            GuestMetricChartSeries(
                id: "netout",
                label: localizer.t.proxmoxOut,
                color: proxmoxColor,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date, let value = sample.netout else { return nil }
                    return GuestMetricChartPoint(date: date, value: max(value, 0))
                }
            )
        ]
        .filter { !$0.points.isEmpty }
    }

    private var diskSeries: [GuestMetricChartSeries] {
        [
            GuestMetricChartSeries(
                id: "diskread",
                label: localizer.t.proxmoxRead,
                color: .orange,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date, let value = sample.diskread else { return nil }
                    return GuestMetricChartPoint(date: date, value: max(value, 0))
                }
            ),
            GuestMetricChartSeries(
                id: "diskwrite",
                label: localizer.t.proxmoxWrite,
                color: .purple,
                points: performanceHistory.compactMap { sample in
                    guard let date = sample.date, let value = sample.diskwrite else { return nil }
                    return GuestMetricChartPoint(date: date, value: max(value, 0))
                }
            )
        ]
        .filter { !$0.points.isEmpty }
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

    private func formattedGuestAgentDate(_ date: Date?) -> String {
        guard let date else { return localizer.t.notAvailable }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    private func formattedRate(_ bytesPerSecond: Double) -> String {
        "\(Formatters.formatBytes(bytesPerSecond))/s"
    }

    private func loadCloneStorageOptions(for nodeName: String) async {
        guard !nodeName.isEmpty,
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }

        do {
            let storages = try await client.getStorage(node: nodeName)
            let requiredContentType = guestType == .qemu ? "images" : "rootdir"
            let filtered = storages.filter {
                $0.isEnabled && ($0.contentTypes.contains(requiredContentType) || $0.contentTypes.isEmpty)
            }
            let resolvedOptions = (filtered.isEmpty ? storages.filter(\.isEnabled) : filtered)
                .sorted { $0.storage.localizedCaseInsensitiveCompare($1.storage) == .orderedAscending }

            await MainActor.run {
                cloneStorageOptions = resolvedOptions
                if !cloneTargetStorage.isEmpty,
                   !resolvedOptions.contains(where: { $0.storage == cloneTargetStorage }) {
                    cloneTargetStorage = ""
                }
            }
        } catch {
            await MainActor.run {
                cloneStorageOptions = []
                cloneTargetStorage = ""
            }
        }
    }

    private func refreshSuggestedVmid() async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
        do {
            let suggested = try await client.getNextAvailableVmid()
            await MainActor.run {
                nextAvailableVmid = suggested
                cloneVmid = "\(suggested)"
            }
        } catch {
            presentActionError(error)
        }
    }

    private func applyConfigChanges(_ params: [String: String]) async {
        guard !params.isEmpty else {
            showConfigEdit = false
            return
        }
        configEditLoading = true
        configEditError = nil
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                configEditError = localizer.t.proxmoxClientNotConfigured
                configEditLoading = false
                return
            }
            if guestType == .qemu {
                _ = try await client.updateVMConfig(node: currentNodeName, vmid: vmid, params: params)
            } else {
                _ = try await client.updateLXCConfig(node: currentNodeName, vmid: vmid, params: params)
            }
            HapticManager.success()
            showConfigEdit = false
            await fetchAll()
        } catch {
            configEditError = error.localizedDescription
            HapticManager.error()
        }
        configEditLoading = false
    }

    // MARK: - Data

    private func fetchAll(showLoading: Bool = true) async {
        if showLoading {
            state = .loading
        }
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            async let storageTask = client.getStorage(node: currentNodeName)
            async let nodesTask = client.getNodes()
            async let poolsTask = try? client.getPools()
            async let nextVmidTask = try? client.getNextAvailableVmid()

            if guestType == .qemu {
                let vm = try await client.getVMStatus(node: currentNodeName, vmid: vmid)
                guestName = vm.name ?? ""
                status = vm.status ?? "unknown"
                guestLock = vm.lock
                isTemplateGuest = vm.isTemplate
                cpuPercent = vm.cpuPercent
                cpuCores = vm.cpus ?? 0
                memUsed = vm.mem ?? 0
                memTotal = vm.maxmem ?? 0
                diskUsed = vm.disk ?? 0
                diskTotal = vm.maxdisk ?? 0
                netIn = vm.netin ?? 0
                netOut = vm.netout ?? 0
                uptime = vm.uptime ?? 0
                tags = vm.tagList

                async let configTask = client.getVMConfig(node: currentNodeName, vmid: vmid)
                async let snapshotTask = client.getVMSnapshots(node: currentNodeName, vmid: vmid)
                config = try? await configTask
                snapshots = (try? await snapshotTask) ?? []
            } else {
                let lxc = try await client.getLXCStatus(node: currentNodeName, vmid: vmid)
                guestName = lxc.name ?? ""
                status = lxc.status ?? "unknown"
                guestLock = lxc.lock
                isTemplateGuest = lxc.isTemplate
                cpuPercent = lxc.cpuPercent
                cpuCores = lxc.cpus ?? 0
                memUsed = lxc.mem ?? 0
                memTotal = lxc.maxmem ?? 0
                diskUsed = lxc.disk ?? 0
                diskTotal = lxc.maxdisk ?? 0
                netIn = lxc.netin ?? 0
                netOut = lxc.netout ?? 0
                uptime = lxc.uptime ?? 0
                tags = lxc.tagList

                async let configTask = client.getLXCConfig(node: currentNodeName, vmid: vmid)
                async let snapshotTask = client.getLXCSnapshots(node: currentNodeName, vmid: vmid)
                config = try? await configTask
                snapshots = (try? await snapshotTask) ?? []
            }

            availableStorages = ((try? await storageTask) ?? [])
                .sorted { $0.storage.localizedCaseInsensitiveCompare($1.storage) == .orderedAscending }
            availableNodes = (try? await nodesTask) ?? []
            availablePools = ((await poolsTask) ?? [])
                .sorted { $0.poolid.localizedCaseInsensitiveCompare($1.poolid) == .orderedAscending }
            nextAvailableVmid = await nextVmidTask
            await loadPerformanceHistory(using: client)
            await loadGuestAgent(using: client)
            if backupStorage.isEmpty {
                backupStorage = backupStorageOptions.first?.storage ?? ""
            }
            if migrateTargetNode.isEmpty {
                migrateTargetNode = migrationTargets.first?.node ?? ""
            }
            if cloneTargetNode.isEmpty {
                cloneTargetNode = currentNodeName
            }
            if cloneStorageOptions.isEmpty {
                await loadCloneStorageOptions(for: cloneTargetNode)
            }

            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func formatUptime(_ seconds: Int) -> String {
        guard seconds > 0 else { return "-" }
        let days = seconds / 86400
        let hours = (seconds % 86400) / 3600
        let minutes = (seconds % 3600) / 60
        if days > 0 { return "\(days)d \(hours)h" }
        if hours > 0 { return "\(hours)h \(minutes)m" }
        return "\(minutes)m"
    }

    private func loadPerformanceHistory(using client: ProxmoxAPIClient) async {
        performanceLoading = true
        defer { performanceLoading = false }

        do {
            performanceHistory = try await client.getGuestRRDData(
                node: currentNodeName,
                vmid: vmid,
                guestType: guestType.rawValue,
                timeframe: performanceTimeframe
            )
            performanceError = nil
        } catch {
            performanceHistory = []
            performanceError = error.localizedDescription
        }
    }

    private func loadGuestAgent(using client: ProxmoxAPIClient) async {
        guard guestType == .qemu,
              isRunning,
              config?.guestAgentEnabled == true else {
            guestAgentInfo = nil
            guestAgentOSInfo = nil
            guestAgentHostname = nil
            guestAgentTimezone = nil
            guestAgentInterfaces = []
            guestAgentUsers = []
            guestAgentFilesystems = []
            guestAgentError = nil
            return
        }

        guestAgentLoading = true
        defer { guestAgentLoading = false }

        async let infoTask = try? client.getVMGuestAgentInfo(node: currentNodeName, vmid: vmid)
        async let osInfoTask = try? client.getVMGuestAgentOSInfo(node: currentNodeName, vmid: vmid)
        async let hostnameTask = try? client.getVMGuestAgentHostname(node: currentNodeName, vmid: vmid)
        async let timezoneTask = try? client.getVMGuestAgentTimezone(node: currentNodeName, vmid: vmid)
        async let usersTask = try? client.getVMGuestAgentUsers(node: currentNodeName, vmid: vmid)
        async let filesystemsTask = try? client.getVMGuestAgentFilesystems(node: currentNodeName, vmid: vmid)
        async let interfacesTask = try? client.getVMGuestAgentNetworkInterfaces(node: currentNodeName, vmid: vmid)

        guestAgentInfo = await infoTask
        guestAgentOSInfo = await osInfoTask
        guestAgentHostname = (await hostnameTask)?.hostName
        guestAgentTimezone = await timezoneTask
        guestAgentUsers = ((await usersTask) ?? [])
            .sorted {
                $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending
            }
        guestAgentFilesystems = ((await filesystemsTask) ?? [])
            .sorted {
                $0.mountpoint.localizedCaseInsensitiveCompare($1.mountpoint) == .orderedAscending
            }
        guestAgentInterfaces = ((await interfacesTask) ?? [])
            .filter { !$0.visibleAddresses.isEmpty }
            .sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }

        let hasAnyData = guestAgentInfo != nil ||
            guestAgentOSInfo != nil ||
            (guestAgentHostname?.isEmpty == false) ||
            guestAgentTimezone != nil ||
            !guestAgentUsers.isEmpty ||
            !guestAgentFilesystems.isEmpty ||
            !guestAgentInterfaces.isEmpty
        guestAgentError = hasAnyData ? nil : localizer.t.notAvailable
    }
}

private struct ProxmoxGuestTrackedTask: Identifiable {
    let title: String
    let kind: Kind
    let reference: ProxmoxTaskReference
    let sourceNode: String
    let targetNode: String?
    let task: ProxmoxTask
    let lastUpdated: Date

    enum Kind {
        case lifecycle
        case backup
        case clone
        case migrate
        case snapshot
        case template
    }

    var id: String { reference.upid }

    var taskNode: String {
        task.node ?? reference.node ?? sourceNode
    }

    var isRunning: Bool {
        task.isRunning
    }

    var didFinish: Bool {
        !task.isRunning
    }

    var didCompleteMigration: Bool {
        kind == .migrate && didFinish && task.isOk && targetNode != nil
    }

    var iconName: String {
        if task.isRunning {
            return "arrow.triangle.2.circlepath"
        }
        return task.isOk ? "checkmark.circle.fill" : "exclamationmark.triangle.fill"
    }

    var tintColor: Color {
        if task.isRunning {
            return .blue
        }
        return task.isOk ? AppTheme.running : AppTheme.stopped
    }

    var formattedLastUpdated: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .medium
        return formatter.string(from: lastUpdated)
    }

    var navigationTask: ProxmoxTask {
        task
    }

    @MainActor
    func statusLabel(_ localizer: Localizer) -> String {
        if task.isRunning {
            return localizer.t.proxmoxRunning
        }
        return task.isOk ? localizer.t.proxmoxOk : localizer.t.error
    }

    func updating(with task: ProxmoxTask) -> ProxmoxGuestTrackedTask {
        ProxmoxGuestTrackedTask(
            title: title,
            kind: kind,
            reference: reference,
            sourceNode: sourceNode,
            targetNode: targetNode,
            task: task,
            lastUpdated: Date()
        )
    }

    func touchingLastUpdated() -> ProxmoxGuestTrackedTask {
        ProxmoxGuestTrackedTask(
            title: title,
            kind: kind,
            reference: reference,
            sourceNode: sourceNode,
            targetNode: targetNode,
            task: task,
            lastUpdated: Date()
        )
    }
}

private struct GuestMetricChartPoint: Identifiable {
    let date: Date
    let value: Double

    var id: TimeInterval { date.timeIntervalSince1970 }
}

private struct GuestMetricChartSeries: Identifiable {
    let id: String
    let label: String
    let color: Color
    let points: [GuestMetricChartPoint]
}

private struct GuestPerformanceChart: View {
    let title: String
    let timeframe: ProxmoxRRDTimeframe
    let series: [GuestMetricChartSeries]
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

// MARK: - Guest Action

private enum GuestAction: Identifiable {
    case start, stop, shutdown, reboot, suspend, resume, template, backup, clone, migrate

    var id: String {
        switch self {
        case .start: return "start"
        case .stop: return "stop"
        case .shutdown: return "shutdown"
        case .reboot: return "reboot"
        case .suspend: return "suspend"
        case .resume: return "resume"
        case .template: return "template"
        case .backup: return "backup"
        case .clone: return "clone"
        case .migrate: return "migrate"
        }
    }

    @MainActor
    func label(_ localizer: Localizer) -> String {
        switch self {
        case .start: return localizer.t.actionStart
        case .stop: return localizer.t.actionStop
        case .shutdown: return localizer.t.proxmoxShutdown
        case .reboot: return localizer.t.proxmoxRestart
        case .suspend: return localizer.t.proxmoxSuspend
        case .resume: return localizer.t.proxmoxResume
        case .template: return localizer.t.proxmoxConvertToTemplate
        case .backup: return localizer.t.actionBackup
        case .clone: return localizer.t.proxmoxClone
        case .migrate: return localizer.t.proxmoxMigrate
        }
    }

    var icon: String {
        switch self {
        case .start: return "play.fill"
        case .stop: return "stop.fill"
        case .shutdown: return "power"
        case .reboot: return "arrow.clockwise"
        case .suspend: return "pause.fill"
        case .resume: return "play.fill"
        case .template: return "square.stack.3d.up.fill"
        case .backup: return "externaldrive.badge.timemachine"
        case .clone: return "doc.on.doc"
        case .migrate: return "arrow.left.arrow.right"
        }
    }

    var color: Color {
        switch self {
        case .start, .resume: return AppTheme.running
        case .stop: return AppTheme.stopped
        case .shutdown: return .orange
        case .reboot: return AppTheme.info
        case .suspend: return .yellow
        case .template: return .indigo
        case .backup: return .green
        case .clone: return .purple
        case .migrate: return .blue
        }
    }

    var isDestructive: Bool {
        switch self {
        case .stop: return true
        default: return false
        }
    }
}
