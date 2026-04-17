import SwiftUI

struct PatchmonHostDetailView: View {
    let instanceId: UUID
    let host: PatchmonHost
    var onHostDeleted: ((String) -> Void)? = nil

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedTab: PatchmonDetailTab = .overview
    @State private var tabStates: [PatchmonDetailTab: LoadableState<Void>] = [:]

    @State private var hostInfo: PatchmonHostInfo?
    @State private var hostStats: PatchmonHostStats?
    @State private var hostSystem: PatchmonHostSystem?
    @State private var hostNetwork: PatchmonHostNetwork?
    @State private var hostPackages: PatchmonPackagesResponse?
    @State private var hostReports: PatchmonReportsResponse?
    @State private var hostAgentQueue: PatchmonAgentQueueResponse?
    @State private var hostNotes: PatchmonNotesResponse?
    @State private var hostIntegrations: PatchmonIntegrationsResponse?

    @State private var updatesOnlyPackages = true
    @State private var packageVisibleCount = 150
    @State private var showDeleteConfirmation = false
    @State private var deleteErrorMessage: String?
    @State private var isDeletingHost = false
    @State private var transitionForward = true
    @Namespace private var tabChipNamespace

    private let patchmonColor = ServiceType.patchmon.colors.primary
    private var headerTint: Color {
        colorScheme == .dark ? patchmonColor.opacity(0.085) : patchmonColor.opacity(0.04)
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: AppTheme.gridSpacing) {
                headerCard
                tabSelector
                contentForSelectedTab
                deleteHostCard
            }
            .padding(AppTheme.padding)
        }
        .refreshable {
            await load(tab: selectedTab, force: true)
        }
        .navigationTitle(currentHostTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(role: .destructive) {
                    HapticManager.light()
                    showDeleteConfirmation = true
                } label: {
                    if isDeletingHost {
                        ProgressView()
                            .tint(AppTheme.danger)
                    } else {
                        Image(systemName: "trash")
                    }
                }
                .disabled(isDeletingHost)
                .accessibilityLabel(localizer.t.delete)
            }
        }
        .confirmationDialog(localizer.t.delete, isPresented: $showDeleteConfirmation, titleVisibility: .visible) {
            Button(localizer.t.delete, role: .destructive) {
                Task { await deleteHost() }
            }
            Button(localizer.t.cancel, role: .cancel) { }
        } message: {
            Text(currentHostTitle)
        }
        .alert(localizer.t.error, isPresented: .init(
            get: { deleteErrorMessage != nil },
            set: { if !$0 { deleteErrorMessage = nil } }
        )) {
            Button(localizer.t.done) { deleteErrorMessage = nil }
        } message: {
            Text(deleteErrorMessage ?? "")
        }
        .task(id: host.id) {
            await preloadIntegrationsAvailability()
        }
        .task(id: selectedTab) {
            await load(tab: selectedTab)
        }
    }

    private var currentHostTitle: String {
        hostInfo?.friendlyName.isEmpty == false ? (hostInfo?.friendlyName ?? host.displayName) : host.displayName
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(currentHostTitle)
                        .font(.title3.bold())
                    Text(hostInfo?.hostname.isEmpty == false ? (hostInfo?.hostname ?? host.hostname) : host.hostname)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                    Text(hostInfo?.ip.isEmpty == false ? (hostInfo?.ip ?? host.ip) : host.ip)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
                Spacer()
                statusBadge(status: host.status)
            }

            HStack(spacing: 8) {
                statPill(value: "\(host.securityUpdatesCount)", label: localizer.t.patchmonSecurity, color: AppTheme.danger)
                statPill(value: "\(host.updatesCount)", label: localizer.t.patchmonUpdates, color: AppTheme.warning)
                statPill(value: "\(host.totalPackages)", label: localizer.t.patchmonPackages, color: patchmonColor)
            }

            if host.needsReboot {
                Text(localizer.t.patchmonReboot)
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.danger)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(AppTheme.danger.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }

            if let groups = resolvedGroups, !groups.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(groups, id: \.self) { group in
                            Text(group)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(patchmonColor)
                                .padding(.horizontal, 9)
                                .padding(.vertical, 6)
                                .background(patchmonColor.opacity(0.12), in: Capsule())
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .padding(18)
        .glassCard(tint: headerTint)
    }

    private var tabSelector: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(availableTabs) { tab in
                    let selected = selectedTab == tab
                    Button {
                        guard selectedTab != tab else { return }
                        HapticManager.light()
                        let currentIndex = availableTabs.firstIndex(of: selectedTab) ?? 0
                        let nextIndex = availableTabs.firstIndex(of: tab) ?? currentIndex
                        transitionForward = nextIndex >= currentIndex
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.84)) {
                            selectedTab = tab
                        }
                    } label: {
                        Text(tab.title(localizer.translations))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(selected ? .white : AppTheme.textSecondary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background {
                                if selected {
                                    Capsule()
                                        .fill(patchmonColor)
                                        .matchedGeometryEffect(id: "patchmon-tab-chip", in: tabChipNamespace)
                                } else {
                                    Capsule()
                                        .fill(AppTheme.surface.opacity(0.7))
                                }
                            }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 2)
        }
        .padding(14)
        .glassCard()
    }

    @ViewBuilder
    private var contentForSelectedTab: some View {
        Group {
            switch state(for: selectedTab) {
            case .error(let apiError):
                errorCard(apiError: apiError, tab: selectedTab)
            default:
                switch selectedTab {
                case .overview:
                    overviewTab
                case .system:
                    systemTab
                case .network:
                    networkTab
                case .packages:
                    packagesTab
                case .reports:
                    reportsTab
                case .agent:
                    agentTab
                case .notes:
                    notesTab
                case .integrations:
                    integrationsTab
                }
            }
        }
        .id(selectedTab)
        .transition(
            .asymmetric(
                insertion: .move(edge: transitionForward ? .trailing : .leading).combined(with: .opacity),
                removal: .move(edge: transitionForward ? .leading : .trailing).combined(with: .opacity)
            )
        )
        .animation(.spring(response: 0.36, dampingFraction: 0.86), value: selectedTab)
    }

    private var overviewTab: some View {
        VStack(spacing: 10) {
            if let info = hostInfo {
                detailCard {
                    detailRow(title: localizer.t.detailHostname, value: info.hostname)
                    detailRow(title: localizer.t.detailId, value: info.id)
                    detailRow(title: localizer.t.patchmonArchitecture, value: [info.osType, info.osVersion].filter { !$0.isEmpty }.joined(separator: " "))
                    detailRow(title: localizer.t.detailNetwork, value: info.ip)
                    if let machineId = info.machineId, !machineId.isEmpty {
                        detailRow(title: localizer.t.patchmonMachineId, value: machineId)
                    }
                    if let agentVersion = info.agentVersion, !agentVersion.isEmpty {
                        detailRow(title: localizer.t.patchmonAgentVersion, value: agentVersion)
                    }
                }
            }

            if let stats = hostStats {
                detailCard {
                    HStack(spacing: 8) {
                        statPill(value: "\(stats.totalInstalledPackages)", label: localizer.t.patchmonPackages, color: patchmonColor)
                        statPill(value: "\(stats.outdatedPackages)", label: localizer.t.patchmonUpdates, color: AppTheme.warning)
                    }
                    HStack(spacing: 8) {
                        statPill(value: "\(stats.securityUpdates)", label: localizer.t.patchmonSecurity, color: AppTheme.danger)
                        statPill(value: "\(stats.totalRepos)", label: localizer.t.patchmonRepositories, color: AppTheme.info)
                    }
                }
            }

            if hostInfo == nil && hostStats == nil {
                loadingCard
            }
        }
    }

    private var systemTab: some View {
        VStack(spacing: 10) {
            if let system = hostSystem {
                detailCard {
                    detailRow(title: localizer.t.patchmonArchitecture, value: system.architecture)
                    detailRow(title: localizer.t.patchmonKernel, value: system.kernelVersion)
                    detailRow(title: localizer.t.patchmonInstalledKernel, value: system.installedKernelVersion)
                    detailRow(title: localizer.t.patchmonUptime, value: system.systemUptime)
                    detailRow(title: localizer.t.portainerCpus, value: system.cpuModel)
                    detailRow(title: localizer.t.patchmonCores, value: system.cpuCores.map(String.init))
                    detailRow(title: localizer.t.detailMemory, value: system.ramInstalled)
                    detailRow(title: localizer.t.patchmonSwap, value: system.swapSize)
                    if let load = system.loadAverage {
                        let value = String(format: "%.2f / %.2f / %.2f", load.oneMinute, load.fiveMinutes, load.fifteenMinutes)
                        detailRow(title: localizer.t.patchmonLoadAverage, value: value)
                    }
                    if let reason = system.rebootReason, !reason.isEmpty {
                        detailRow(title: localizer.t.patchmonReboot, value: reason)
                    }
                }

                if !system.diskDetails.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        ForEach(system.diskDetails) { disk in
                            VStack(alignment: .leading, spacing: 6) {
                                Text(disk.filesystem)
                                    .font(.subheadline.weight(.semibold))
                                Text("\(disk.used) / \(disk.size) • \(disk.usePercent)")
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                                Text(disk.mountedOn)
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(AppTheme.surface.opacity(0.75), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                    }
                    .padding(16)
                    .glassCard()
                }
            } else {
                loadingCard
            }
        }
    }

    private var networkTab: some View {
        VStack(spacing: 10) {
            if let network = hostNetwork {
                detailCard {
                    detailRow(title: localizer.t.patchmonGateway, value: network.gatewayIP)
                    detailRow(title: localizer.t.patchmonDnsServers, value: network.dnsServers.joined(separator: ", "))
                    detailRow(title: localizer.t.detailNetwork, value: network.ip)
                }

                if !network.networkInterfaces.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizer.t.patchmonInterfaces)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                            .textCase(.uppercase)
                        ForEach(network.networkInterfaces) { iface in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(iface.name)
                                    .font(.subheadline.weight(.semibold))
                                Text(iface.ip)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                                Text(iface.mac)
                                    .font(.caption2.monospaced())
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(AppTheme.surface.opacity(0.75), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                    }
                    .padding(16)
                    .glassCard()
                }
            } else {
                loadingCard
            }
        }
    }

    private var packagesTab: some View {
        VStack(spacing: 10) {
            HStack {
                Button {
                    updatesOnlyPackages = true
                    packageVisibleCount = 150
                    tabStates[.packages] = .idle
                    Task { await load(tab: .packages, force: true) }
                } label: {
                    Text(localizer.t.patchmonUpdatesOnly)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(updatesOnlyPackages ? .white : AppTheme.textSecondary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(updatesOnlyPackages ? patchmonColor : AppTheme.surface.opacity(0.7), in: Capsule())
                }
                .buttonStyle(.plain)

                Button {
                    updatesOnlyPackages = false
                    packageVisibleCount = 150
                    tabStates[.packages] = .idle
                    Task { await load(tab: .packages, force: true) }
                } label: {
                    Text(localizer.t.patchmonShowAllPackages)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(!updatesOnlyPackages ? .white : AppTheme.textSecondary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(!updatesOnlyPackages ? patchmonColor : AppTheme.surface.opacity(0.7), in: Capsule())
                }
                .buttonStyle(.plain)

                Spacer()
            }
            .padding(14)
            .glassCard()

            if let packages = hostPackages?.packages {
                if packages.isEmpty {
                    emptyData(updatesOnlyPackages ? localizer.t.patchmonNoUpdatesAvailable : localizer.t.patchmonNoPackages)
                } else {
                    let visibleCount = min(packageVisibleCount, packages.count)
                    VStack(spacing: 8) {
                        ForEach(packages.prefix(visibleCount), id: \.id) { pkg in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(spacing: 8) {
                                    Text(pkg.name)
                                        .font(.subheadline.weight(.semibold))
                                    if pkg.isSecurityUpdate {
                                        Text(localizer.t.patchmonSecurity)
                                            .font(.caption2.bold())
                                            .foregroundStyle(.white)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 4)
                                            .background(AppTheme.danger, in: Capsule())
                                    }
                                    Spacer()
                                }

                                Text("\(pkg.currentVersion ?? localizer.t.notAvailable) → \(pkg.availableVersion ?? localizer.t.notAvailable)")
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)

                                if let description = pkg.description, !description.isEmpty {
                                    Text(description)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(2)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(AppTheme.surface.opacity(0.75), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }

                        if packages.count > visibleCount {
                            Button {
                                HapticManager.light()
                                packageVisibleCount += 150
                            } label: {
                                Text("+\(packages.count - visibleCount)")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(patchmonColor)
                                    .frame(maxWidth: .infinity, alignment: .center)
                                    .padding(.top, 6)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(16)
                    .glassCard()
                }
            } else {
                loadingCard
            }
        }
    }

    private var reportsTab: some View {
        VStack(spacing: 10) {
            if let reports = hostReports?.reports {
                if reports.isEmpty {
                    emptyData(localizer.t.patchmonNoReports)
                } else {
                    VStack(spacing: 8) {
                        ForEach(reports, id: \.id) { report in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text(report.status.capitalized)
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(report.status.lowercased() == "success" ? AppTheme.running : AppTheme.danger)
                                    Spacer()
                                    if let date = report.date {
                                        Text(Formatters.formatDate(date))
                                            .font(.caption2)
                                            .foregroundStyle(AppTheme.textMuted)
                                    }
                                }

                                HStack(spacing: 10) {
                                    Text("\(localizer.t.patchmonPackages): \(report.totalPackages)")
                                    Text("\(localizer.t.patchmonUpdates): \(report.outdatedPackages)")
                                    Text("\(localizer.t.patchmonSecurity): \(report.securityUpdates)")
                                }
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textSecondary)

                                if let exec = report.executionTimeSeconds {
                                    Text("\(localizer.t.patchmonExecutionTime): \(String(format: "%.1fs", exec))")
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                }
                                if let errorMessage = report.errorMessage, !errorMessage.isEmpty {
                                    Text(errorMessage)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.danger)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(AppTheme.surface.opacity(0.75), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                    }
                    .padding(16)
                    .glassCard()
                }
            } else {
                loadingCard
            }
        }
    }

    private var agentTab: some View {
        VStack(spacing: 10) {
            if let queue = hostAgentQueue {
                detailCard {
                    HStack(spacing: 8) {
                        statPill(value: "\(queue.queueStatus.waiting)", label: localizer.t.patchmonQueueWaiting, color: AppTheme.info)
                        statPill(value: "\(queue.queueStatus.active)", label: localizer.t.patchmonQueueActive, color: AppTheme.running)
                    }
                    HStack(spacing: 8) {
                        statPill(value: "\(queue.queueStatus.delayed)", label: localizer.t.patchmonQueueDelayed, color: AppTheme.warning)
                        statPill(value: "\(queue.queueStatus.failed)", label: localizer.t.patchmonQueueFailed, color: AppTheme.danger)
                    }
                }

                if queue.jobHistory.isEmpty {
                    emptyData(localizer.t.patchmonNoJobs)
                } else {
                    VStack(spacing: 8) {
                        ForEach(queue.jobHistory, id: \.id) { job in
                            VStack(alignment: .leading, spacing: 5) {
                                HStack {
                                    Text(job.jobName)
                                        .font(.subheadline.weight(.semibold))
                                    Spacer()
                                    Text(job.status.capitalized)
                                        .font(.caption2.weight(.semibold))
                                        .foregroundStyle(colorForJobStatus(job.status))
                                }
                                if let created = job.createdAt {
                                    Text("\(localizer.t.detailCreated): \(Formatters.formatDate(created))")
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                }
                                if let completed = job.completedAt {
                                    Text("\(localizer.t.done): \(Formatters.formatDate(completed))")
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                }
                                if let errorMessage = job.errorMessage, !errorMessage.isEmpty {
                                    Text(errorMessage)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.danger)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(12)
                            .background(AppTheme.surface.opacity(0.75), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                    }
                    .padding(16)
                    .glassCard()
                }
            } else {
                loadingCard
            }
        }
    }

    private var notesTab: some View {
        VStack(spacing: 10) {
            if let notes = hostNotes?.notes?.trimmingCharacters(in: .whitespacesAndNewlines), !notes.isEmpty {
                Text(notes)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(16)
                    .glassCard()
            } else if hostNotes != nil {
                emptyData(localizer.t.patchmonNoNotes)
            } else {
                loadingCard
            }
        }
    }

    private var integrationsTab: some View {
        VStack(spacing: 10) {
            if let integrations = hostIntegrations?.integrations {
                if integrations.isEmpty {
                    emptyData(localizer.t.patchmonNoIntegrations)
                } else {
                    VStack(spacing: 8) {
                        ForEach(integrations.keys.sorted(), id: \.self) { key in
                            if let integration = integrations[key] {
                                VStack(alignment: .leading, spacing: 6) {
                                    HStack {
                                        Text(key.lowercased() == "docker" ? localizer.t.patchmonDocker : key.capitalized)
                                            .font(.subheadline.weight(.semibold))
                                        Spacer()
                                        Text(integration.enabled ? localizer.t.statusOn : localizer.t.statusOff)
                                            .font(.caption2.weight(.semibold))
                                            .foregroundStyle(integration.enabled ? AppTheme.running : AppTheme.textMuted)
                                    }
                                    if let description = integration.description, !description.isEmpty {
                                        Text(description)
                                            .font(.caption)
                                            .foregroundStyle(AppTheme.textSecondary)
                                    }
                                    if integration.enabled {
                                        HStack(spacing: 10) {
                                            if let containers = integration.containersCount {
                                                Text("\(localizer.t.portainerContainers): \(containers)")
                                            }
                                            if let volumes = integration.volumesCount {
                                                Text("\(localizer.t.portainerVolumes): \(volumes)")
                                            }
                                            if let networks = integration.networksCount {
                                                Text("\(localizer.t.detailNetwork): \(networks)")
                                            }
                                        }
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(12)
                                .background(AppTheme.surface.opacity(0.75), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                        }
                    }
                    .padding(16)
                    .glassCard()
                }
            } else {
                loadingCard
            }
        }
    }

    private var resolvedGroups: [String]? {
        if let groups = hostInfo?.hostGroups.map(\.name).filter({ !$0.isEmpty }), !groups.isEmpty {
            return groups
        }
        let groups = host.hostGroups.map(\.name).filter { !$0.isEmpty }
        return groups.isEmpty ? nil : groups
    }

    private var hasDockerIntegration: Bool {
        guard let integrations = hostIntegrations?.integrations else {
            return true
        }
        return integrations["docker"]?.enabled == true
    }

    private var availableTabs: [PatchmonDetailTab] {
        var tabs: [PatchmonDetailTab] = [
            .overview,
            .system,
            .network,
            .packages,
            .reports,
            .agent,
            .notes
        ]
        if hasDockerIntegration {
            tabs.append(.integrations)
        }
        return tabs
    }

    private func state(for tab: PatchmonDetailTab) -> LoadableState<Void> {
        tabStates[tab] ?? .idle
    }

    private func preloadIntegrationsAvailability() async {
        guard hostIntegrations == nil else { return }
        guard let client = await servicesStore.patchmonClient(instanceId: instanceId) else { return }
        if let integrations = try? await client.getHostIntegrations(hostId: host.id) {
            hostIntegrations = integrations
            tabStates[.integrations] = .loaded(())
            if !hasDockerIntegration && selectedTab == .integrations {
                selectedTab = .overview
            }
        }
    }

    private func load(tab: PatchmonDetailTab, force: Bool = false) async {
        if tab == .integrations && !hasDockerIntegration {
            selectedTab = .overview
            return
        }

        if !force, case .loaded = state(for: tab) {
            return
        }

        tabStates[tab] = .loading

        do {
            guard let client = await servicesStore.patchmonClient(instanceId: instanceId) else {
                tabStates[tab] = .error(.notConfigured)
                return
            }

            switch tab {
            case .overview:
                async let info = client.getHostInfo(hostId: host.id)
                async let stats = client.getHostStats(hostId: host.id)
                hostInfo = try await info
                hostStats = try await stats

            case .system:
                hostSystem = try await client.getHostSystem(hostId: host.id)

            case .network:
                hostNetwork = try await client.getHostNetwork(hostId: host.id)

            case .packages:
                hostPackages = try await client.getHostPackages(hostId: host.id, updatesOnly: updatesOnlyPackages)
                packageVisibleCount = 150

            case .reports:
                hostReports = try await client.getHostReports(hostId: host.id)

            case .agent:
                hostAgentQueue = try await client.getHostAgentQueue(hostId: host.id)

            case .notes:
                hostNotes = try await client.getHostNotes(hostId: host.id)

            case .integrations:
                hostIntegrations = try await client.getHostIntegrations(hostId: host.id)
                if !hasDockerIntegration && selectedTab == .integrations {
                    selectedTab = .overview
                }
            }

            tabStates[tab] = .loaded(())
        } catch let apiError as APIError {
            tabStates[tab] = .error(apiError)
        } catch {
            tabStates[tab] = .error(.custom(error.localizedDescription))
        }
    }

    private func detailCard<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            content()
        }
        .padding(16)
        .glassCard()
    }

    private func detailRow(title: String, value: String?) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(title)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
            Spacer()
            Text((value?.isEmpty == false ? value : localizer.t.notAvailable) ?? localizer.t.notAvailable)
                .font(.caption)
                .fixedSize(horizontal: false, vertical: true)
                .multilineTextAlignment(.trailing)
                .foregroundStyle(AppTheme.textSecondary)
        }
    }

    private func statusBadge(status: String) -> some View {
        Text(status.capitalized)
            .font(.caption2.bold())
            .foregroundStyle(host.isActive ? AppTheme.running : AppTheme.warning)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background((host.isActive ? AppTheme.running : AppTheme.warning).opacity(0.15), in: Capsule())
    }

    private func statPill(value: String, label: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundStyle(color)
                .contentTransition(.numericText())
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, minHeight: 76, alignment: .topLeading)
        .padding(10)
        .background(
            AppTheme.surface.opacity(colorScheme == .dark ? 0.75 : 0.88),
            in: RoundedRectangle(cornerRadius: 10, style: .continuous)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(color.opacity(0.2), lineWidth: 1)
        )
    }

    private var loadingCard: some View {
        HStack(spacing: 10) {
            ProgressView()
                .tint(patchmonColor)
            Text(localizer.t.loading)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
            Spacer()
        }
        .padding(16)
        .glassCard()
    }

    private func emptyData(_ text: String) -> some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(AppTheme.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
            .glassCard()
    }

    private func errorCard(apiError: APIError, tab: PatchmonDetailTab) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(apiError.localizedDescription)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
            Button(localizer.t.retry) {
                Task { await load(tab: tab, force: true) }
            }
            .buttonStyle(.bordered)
            .tint(patchmonColor)
        }
        .padding(16)
        .glassCard(tint: AppTheme.danger.opacity(0.08))
    }

    private var deleteHostCard: some View {
        Button(role: .destructive) {
            HapticManager.light()
            showDeleteConfirmation = true
        } label: {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(AppTheme.danger.opacity(colorScheme == .dark ? 0.20 : 0.14))
                    if isDeletingHost {
                        ProgressView()
                            .controlSize(.small)
                            .tint(AppTheme.danger)
                    } else {
                        Image(systemName: "trash.fill")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(AppTheme.danger)
                    }
                }
                .frame(width: 34, height: 34)

                Text(localizer.t.delete)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.danger)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(AppTheme.danger.opacity(0.72))
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: AppTheme.cardRadius, style: .continuous)
                    .fill(AppTheme.danger.opacity(colorScheme == .dark ? 0.12 : 0.06))
            )
            .overlay(
                RoundedRectangle(cornerRadius: AppTheme.cardRadius, style: .continuous)
                    .stroke(AppTheme.danger.opacity(colorScheme == .dark ? 0.42 : 0.30), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(isDeletingHost)
        .glassCard()
    }

    private func deleteHost() async {
        guard !isDeletingHost else { return }

        isDeletingHost = true
        defer { isDeletingHost = false }

        do {
            guard let client = await servicesStore.patchmonClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            _ = try await client.deleteHost(hostId: host.id)
            HapticManager.success()
            onHostDeleted?(host.id)
            dismiss()
        } catch let apiError as APIError {
            HapticManager.error()
            deleteErrorMessage = apiError.localizedDescription
        } catch {
            HapticManager.error()
            deleteErrorMessage = error.localizedDescription
        }
    }

    private func colorForJobStatus(_ status: String) -> Color {
        switch status.lowercased() {
        case "completed", "success":
            return AppTheme.running
        case "failed", "error":
            return AppTheme.danger
        case "active", "running":
            return AppTheme.info
        default:
            return AppTheme.warning
        }
    }
}

enum PatchmonDetailTab: String, CaseIterable, Identifiable {
    case overview
    case system
    case network
    case packages
    case reports
    case agent
    case notes
    case integrations

    var id: String { rawValue }

    func title(_ t: Translations) -> String {
        switch self {
        case .overview: return t.patchmonOverview
        case .system: return t.patchmonSystem
        case .network: return t.detailNetwork
        case .packages: return t.patchmonPackages
        case .reports: return t.patchmonReports
        case .agent: return t.patchmonAgentQueue
        case .notes: return t.patchmonNotes
        case .integrations: return t.patchmonDocker
        }
    }
}
