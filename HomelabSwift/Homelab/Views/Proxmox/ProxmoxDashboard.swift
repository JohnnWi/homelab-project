import SwiftUI

struct ProxmoxDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedInstanceId: UUID
    @State private var version: ProxmoxVersion?
    @State private var nodes: [ProxmoxNode] = []
    @State private var allVMs: [String: [ProxmoxVM]] = [:]        // node -> VMs
    @State private var allLXCs: [String: [ProxmoxLXC]] = [:]      // node -> LXCs
    @State private var allStorage: [String: [ProxmoxStorage]] = [:] // node -> Storage
    @State private var pools: [ProxmoxPool] = []
    @State private var poolMembersById: [String: [ProxmoxPoolMember]] = [:]
    @State private var state: LoadableState<Void> = .idle
    @State private var searchQuery = ""
    @State private var showTemplates = false
    @State private var filterStatus: ProxmoxGuestFilterStatus = .all

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    private var totalVMs: Int {
        allVMs.values.reduce(0) { $0 + $1.filter { !$0.isTemplate }.count }
    }
    private var runningVMs: Int {
        allVMs.values.reduce(0) { $0 + $1.filter { !$0.isTemplate && $0.isRunning }.count }
    }
    private var totalLXCs: Int {
        allLXCs.values.reduce(0) { $0 + $1.filter { !$0.isTemplate }.count }
    }
    private var runningLXCs: Int {
        allLXCs.values.reduce(0) { $0 + $1.filter { !$0.isTemplate && $0.isRunning }.count }
    }
    private var totalCores: Int {
        nodes.reduce(0) { $0 + ($1.maxcpu ?? 0) }
    }
    private var totalRAM: Int64 {
        nodes.reduce(0) { $0 + ($1.maxmem ?? 0) }
    }
    private var totalDisk: Int64 {
        allStorage.values.flatMap { $0 }.reduce(0) { $0 + ($1.total ?? 0) }
    }
    private var totalPoolMembers: Int {
        poolMembersById.values.reduce(0) { $0 + $1.count }
    }
    private var onlineNodes: Int {
        nodes.filter { $0.isOnline }.count
    }
    private var templateEntries: [ProxmoxDashboardTemplateGuest] {
        let vmTemplates = allVMs.flatMap { nodeName, guests in
            guests
                .filter(\.isTemplate)
                .map {
                    ProxmoxDashboardTemplateGuest(
                        nodeName: nodeName,
                        vmid: $0.vmid,
                        guestType: .qemu,
                        displayName: $0.displayName,
                        tags: $0.tagList,
                        lock: $0.lock
                    )
                }
        }
        let lxcTemplates = allLXCs.flatMap { nodeName, guests in
            guests
                .filter(\.isTemplate)
                .map {
                    ProxmoxDashboardTemplateGuest(
                        nodeName: nodeName,
                        vmid: $0.vmid,
                        guestType: .lxc,
                        displayName: $0.displayName,
                        tags: $0.tagList,
                        lock: $0.lock
                    )
                }
        }

        return (vmTemplates + lxcTemplates).sorted {
            if $0.nodeName != $1.nodeName {
                return $0.nodeName.localizedCaseInsensitiveCompare($1.nodeName) == .orderedAscending
            }
            if $0.guestType != $1.guestType {
                return $0.guestType.rawValue < $1.guestType.rawValue
            }
            return $0.vmid < $1.vmid
        }
    }
    private var totalTemplates: Int {
        templateEntries.count
    }

    // MARK: - Filtered Guests

    private func matchesSearch(_ name: String, vmid: Int, tags: [String]) -> Bool {
        guard !searchQuery.isEmpty else { return true }
        let query = searchQuery.lowercased()
        return name.lowercased().contains(query) ||
               "\(vmid)".contains(query) ||
               tags.contains { $0.lowercased().contains(query) }
    }

    private func matchesStatusFilter(_ status: String?, isRunning: Bool) -> Bool {
        switch filterStatus {
        case .all: return true
        case .running: return isRunning
        case .stopped: return !isRunning
        }
    }

    private var filteredVMs: [(node: String, vm: ProxmoxVM)] {
        allVMs
            .flatMap { (node: String, vms: [ProxmoxVM]) in
                vms.filter { !$0.isTemplate == !showTemplates }.map { (node, $0) }
            }
            .filter { (_, vm) in
                matchesSearch(vm.displayName, vmid: vm.vmid, tags: vm.tagList) &&
                matchesStatusFilter(vm.status, isRunning: vm.isRunning)
            }
            .sorted { $0.vm.vmid < $1.vm.vmid }
    }

    private var filteredLXCs: [(node: String, lxc: ProxmoxLXC)] {
        allLXCs
            .flatMap { (node: String, lxcs: [ProxmoxLXC]) in
                lxcs.filter { !$0.isTemplate == !showTemplates }.map { (node, $0) }
            }
            .filter { (_, lxc) in
                matchesSearch(lxc.displayName, vmid: lxc.vmid, tags: lxc.tagList) &&
                matchesStatusFilter(lxc.status, isRunning: lxc.isRunning)
            }
            .sorted { $0.lxc.vmid < $1.lxc.vmid }
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: fetchAll
        ) {
            instancePicker

            // Search & Filter Bar
            searchAndFilterBar

            // Cluster overview
            clusterOverviewSection

            // Quick stats
            quickStatsSection

            // Nodes
            nodesSection

            // VM summary
            if totalVMs > 0 {
                vmSummarySection
            }

            // LXC summary
            if totalLXCs > 0 {
                lxcSummarySection
            }

            if totalTemplates > 0 {
                templatesSection
            }

            if !pools.isEmpty {
                poolsSection
            }

            // Cluster-level quick actions
            clusterActionsSection
        }
        .navigationTitle(localizer.t.proxmoxDashboard)
        .navigationDestination(for: ProxmoxRoute.self) { route in
            switch route {
            case .nodeDetail(let instanceId, let nodeName):
                ProxmoxNodeDetailView(instanceId: instanceId, nodeName: nodeName)
            case .guestDetail(let instanceId, let nodeName, let vmid, let guestType):
                ProxmoxGuestDetailView(instanceId: instanceId, nodeName: nodeName, vmid: vmid, guestType: guestType)
            case .storageContent(let instanceId, let nodeName, let storageName, let storageType):
                ProxmoxStorageContentView(instanceId: instanceId, nodeName: nodeName, storageName: storageName, storageType: storageType)
            case .firewall(let instanceId, let scope):
                ProxmoxFirewallView(instanceId: instanceId, scope: scope.toScope)
            case .network(let instanceId, let nodeName):
                ProxmoxNetworkView(instanceId: instanceId, nodeName: nodeName)
            case .backupJobs(let instanceId):
                ProxmoxBackupJobsView(instanceId: instanceId)
            case .services(let instanceId, let nodeName):
                ProxmoxServicesView(instanceId: instanceId, nodeName: nodeName)
            case .taskLog(let instanceId, let nodeName, let task):
                ProxmoxTaskLogView(instanceId: instanceId, nodeName: nodeName, task: task)
            case .haReplication(let instanceId):
                ProxmoxHAView(instanceId: instanceId)
            case .ceph(let instanceId, let nodeName):
                ProxmoxCephView(instanceId: instanceId, nodeName: nodeName)
            case .createGuest(let instanceId, let preferredNode):
                ProxmoxCreateGuestView(instanceId: instanceId, preferredNode: preferredNode)
            case .clusterResources(let instanceId):
                ProxmoxClusterResourcesView(instanceId: instanceId)
            case .poolDetail(let instanceId, let poolId):
                ProxmoxPoolDetailView(instanceId: instanceId, poolId: poolId)
            }
        }
        .task(id: selectedInstanceId) { await fetchAll() }
    }

    // MARK: - Instance Picker

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .proxmox)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances.sentenceCased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .proxmox)
                            resetData()
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? proxmoxColor : AppTheme.textMuted.opacity(0.4))
                                    .frame(width: 10, height: 10)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(instance.displayLabel)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(.primary)
                                    Text(instance.url)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                            }
                            .padding(14)
                            .glassCard(tint: instance.id == selectedInstanceId ? proxmoxColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Cluster Overview

    private var clusterOverviewSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(systemName: "cpu")
                    .font(.body)
                    .foregroundStyle(proxmoxColor)
                    .frame(width: 40, height: 40)
                    .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(localizer.t.proxmoxDashboard)
                        .font(.body.weight(.bold))
                    if let version {
                        Text("v\(version.version ?? "") (\(version.release ?? ""))")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }

                Spacer()

                HStack(spacing: 5) {
                    Circle()
                        .fill(onlineNodes == nodes.count && nodes.count > 0 ? AppTheme.running : AppTheme.stopped)
                        .frame(width: 8, height: 8)
                    Text("\(onlineNodes)/\(nodes.count) \(localizer.t.proxmoxNodes)")
                        .font(.caption.bold())
                        .foregroundStyle(onlineNodes == nodes.count && nodes.count > 0 ? AppTheme.running : AppTheme.stopped)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(
                    (onlineNodes == nodes.count && nodes.count > 0 ? AppTheme.running : AppTheme.stopped).opacity(0.12),
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                )
            }
        }
    }

    // MARK: - Quick Stats

    private var quickStatsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxOverview.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                miniStatChip(label: localizer.t.proxmoxVMs, value: "\(runningVMs)/\(totalVMs)", color: AppTheme.info)
                miniStatChip(label: localizer.t.proxmoxContainers, value: "\(runningLXCs)/\(totalLXCs)", color: proxmoxColor)
                miniStatChip(label: localizer.t.proxmoxTemplates, value: "\(totalTemplates)", color: .indigo)
                miniStatChip(label: localizer.t.proxmoxCpuCores, value: "\(totalCores)", color: AppTheme.created)
                miniStatChip(label: localizer.t.proxmoxRam, value: Formatters.formatBytes(Double(totalRAM)), color: AppTheme.paused)
            }
            .padding(12)
            .glassCard()
        }
    }

    // MARK: - Nodes Section

    private var nodesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxNodes.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            ForEach(nodes) { node in
                NavigationLink(value: ProxmoxRoute.nodeDetail(instanceId: selectedInstanceId, nodeName: node.node)) {
                    VStack(spacing: 12) {
                        HStack(spacing: 10) {
                            Image(systemName: "server.rack")
                                .font(.body)
                                .foregroundStyle(node.isOnline ? proxmoxColor : AppTheme.textMuted)
                                .frame(width: 36, height: 36)
                                .background((node.isOnline ? proxmoxColor : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                            VStack(alignment: .leading, spacing: 2) {
                                Text(node.node)
                                    .font(.subheadline.weight(.bold))
                                    .foregroundStyle(.primary)
                                HStack(spacing: 4) {
                                    Circle()
                                        .fill(node.isOnline ? AppTheme.running : AppTheme.stopped)
                                        .frame(width: 6, height: 6)
                                    Text(node.isOnline ? localizer.t.proxmoxNodeOnline : localizer.t.proxmoxNodeOffline)
                                        .font(.caption2)
                                        .foregroundStyle(node.isOnline ? AppTheme.running : AppTheme.stopped)
                                }
                            }

                            Spacer()

                            if node.isOnline {
                                Text(node.formattedUptime)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textMuted)
                            }

                            Image(systemName: "chevron.right")
                                .font(.caption.bold())
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        if node.isOnline {
                            HStack(spacing: 8) {
                                resourceBar(label: localizer.t.proxmoxCpu, percent: node.cpuPercent, color: proxmoxColor)
                                resourceBar(label: localizer.t.proxmoxRam, percent: node.memPercent, color: AppTheme.info)
                                resourceBar(label: localizer.t.proxmoxDisk, percent: node.diskPercent, color: AppTheme.paused)
                            }
                        }
                    }
                    .padding(14)
                    .glassCard(tint: node.isOnline ? nil : Color.red.opacity(0.05))
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - VM Summary

    private var vmSummarySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxVMs.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                if !searchQuery.isEmpty || filterStatus != .all {
                    Text("\(filteredVMs.count)")
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            if filteredVMs.isEmpty && !searchQuery.isEmpty {
                emptySearchView(type: localizer.t.proxmoxVMs)
            } else {
                ForEach(filteredVMs, id: \.vm.vmid) { nodeName, vm in
                    NavigationLink(value: ProxmoxRoute.guestDetail(instanceId: selectedInstanceId, nodeName: nodeName, vmid: vm.vmid, guestType: .qemu)) {
                        guestRow(name: vm.displayName, vmid: vm.vmid, status: vm.status, cpuPercent: vm.cpuPercent, memPercent: vm.memPercent, uptime: vm.formattedUptime, tags: vm.tagList, icon: "desktopcomputer")
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - LXC Summary

    private var lxcSummarySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxContainers.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                if !searchQuery.isEmpty || filterStatus != .all {
                    Text("\(filteredLXCs.count)")
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            if filteredLXCs.isEmpty && !searchQuery.isEmpty {
                emptySearchView(type: localizer.t.proxmoxContainers)
            } else {
                ForEach(filteredLXCs, id: \.lxc.vmid) { nodeName, lxc in
                    NavigationLink(value: ProxmoxRoute.guestDetail(instanceId: selectedInstanceId, nodeName: nodeName, vmid: lxc.vmid, guestType: .lxc)) {
                        guestRow(name: lxc.displayName, vmid: lxc.vmid, status: lxc.status, cpuPercent: lxc.cpuPercent, memPercent: lxc.memPercent, uptime: lxc.formattedUptime, tags: lxc.tagList, icon: "shippingbox.fill")
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func emptySearchView(type: String) -> some View {
        VStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.title2)
                .foregroundStyle(AppTheme.textMuted)
            Text(String(format: localizer.t.proxmoxNoSearchResults, type))
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .glassCard()
    }

    // MARK: - Pools

    private var templatesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxTemplates.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                Text("\(totalTemplates)")
                    .font(.caption2.bold())
                    .foregroundStyle(.indigo)
            }

            ForEach(templateEntries) { template in
                NavigationLink(value: ProxmoxRoute.guestDetail(instanceId: selectedInstanceId, nodeName: template.nodeName, vmid: template.vmid, guestType: template.guestType)) {
                    templateRow(template)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private var poolsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxPools)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                Text("\(pools.count) / \(totalPoolMembers) \(localizer.t.proxmoxPoolMembers.lowercased())")
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }

            ForEach(pools.sorted { $0.poolid.localizedCaseInsensitiveCompare($1.poolid) == .orderedAscending }) { pool in
                let members = poolMembersById[pool.poolid] ?? []
                let guestCount = members.filter { $0.type == "qemu" || $0.type == "lxc" }.count
                let storageCount = members.filter { $0.type == "storage" }.count

                NavigationLink(value: ProxmoxRoute.poolDetail(instanceId: selectedInstanceId, poolId: pool.poolid)) {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 10) {
                            Image(systemName: "square.stack.3d.up.fill")
                                .font(.subheadline)
                                .foregroundStyle(proxmoxColor)
                                .frame(width: 30, height: 30)
                                .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

                            VStack(alignment: .leading, spacing: 2) {
                                Text(pool.poolid)
                                    .font(.subheadline.bold())
                                if let comment = pool.comment, !comment.isEmpty {
                                    Text(comment)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(2)
                                }
                            }

                            Spacer()

                            HStack(spacing: 4) {
                                Text("\(members.count)")
                                    .font(.caption.bold())
                                    .foregroundStyle(proxmoxColor)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(proxmoxColor.opacity(0.1), in: Capsule())
                                Image(systemName: "chevron.right")
                                    .font(.caption.bold())
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }

                        HStack(spacing: 16) {
                            poolStat(label: localizer.t.proxmoxGuests, value: "\(guestCount)")
                            poolStat(label: localizer.t.proxmoxStorage, value: "\(storageCount)")
                            poolStat(label: localizer.t.proxmoxPoolMembers, value: "\(members.count)")
                        }
                    }
                    .padding(12)
                    .glassCard()
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Helper Views

    private func miniStatChip(label: String, value: String, color: Color) -> some View {
        VStack(alignment: .center, spacing: 6) {
            Text(label.sentenceCased())
                .font(.caption2.weight(.semibold))
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(2)
                .minimumScaleFactor(0.6)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(value)
                .font(.title3.bold())
                .foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .center)
        .padding(.vertical, 12)
        .padding(.horizontal, 8)
        .glassCard(cornerRadius: 14, tint: color.opacity(0.08))
    }

    private func resourceBar(label: String, percent: Double, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(label)
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                Text(String(format: "%.0f%%", percent))
                    .font(.caption2.bold())
                    .foregroundStyle(color)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(color.opacity(0.15))
                    RoundedRectangle(cornerRadius: 3)
                        .fill(color)
                        .frame(width: geo.size.width * min(percent / 100, 1))
                }
            }
            .frame(height: 6)
        }
    }

    private func guestRow(name: String, vmid: Int, status: String?, cpuPercent: Double, memPercent: Double, uptime: String, tags: [String], icon: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(status == "running" ? proxmoxColor : AppTheme.textMuted)
                .frame(width: 32, height: 32)
                .background((status == "running" ? proxmoxColor : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(name)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text("#\(vmid)")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }

                HStack(spacing: 8) {
                    statusBadge(status: status ?? "unknown")

                    if status == "running" {
                        Text(String(format: "%@ %.0f%%", localizer.t.proxmoxCpuLabel, cpuPercent))
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textSecondary)
                        Text(String(format: "%@ %.0f%%", localizer.t.proxmoxRamLabel, memPercent))
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }

                if !tags.isEmpty {
                    HStack(spacing: 4) {
                        ForEach(tags.prefix(3), id: \.self) { tag in
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

            if status == "running" {
                Text(uptime)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }

            Image(systemName: "chevron.right")
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(12)
        .glassCard()
    }

    private func templateRow(_ template: ProxmoxDashboardTemplateGuest) -> some View {
        HStack(spacing: 12) {
            Image(systemName: template.iconName)
                .font(.subheadline)
                .foregroundStyle(.indigo)
                .frame(width: 32, height: 32)
                .background(Color.indigo.opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(template.displayName)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    Text("#\(template.vmid)")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }

                HStack(spacing: 6) {
                    Text(localizer.t.proxmoxTemplate)
                        .font(.caption2.bold())
                        .foregroundStyle(.indigo)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.indigo.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                    Text(template.guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc)
                        .font(.caption2.bold())
                        .foregroundStyle(proxmoxColor)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                    Text(template.nodeName)
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.textMuted)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(AppTheme.textMuted.opacity(0.08), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                }

                if !template.tags.isEmpty || (template.lock?.isEmpty == false) {
                    HStack(spacing: 4) {
                        ForEach(template.tags.prefix(2), id: \.self) { tag in
                            Text(tag)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundStyle(proxmoxColor)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        }
                        if let lock = template.lock, !lock.isEmpty {
                            Text("\(localizer.t.proxmoxLocked): \(lock)")
                                .font(.system(size: 10, weight: .medium))
                                .foregroundStyle(.orange)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
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

    private func poolStat(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.caption.bold())
                .foregroundStyle(.primary)
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
    }

    private func statusBadge(status: String) -> some View {
        let color: Color = {
            switch status.lowercased() {
            case "running": return AppTheme.running
            case "stopped": return AppTheme.stopped
            case "paused": return .yellow
            default: return AppTheme.textMuted
            }
        }()

        return HStack(spacing: 4) {
            Circle().fill(color).frame(width: 5, height: 5)
            Text(localizedStatus(status))
                .font(.caption2.bold())
                .foregroundStyle(color)
        }
    }

    // MARK: - Cluster Quick Actions

    // MARK: - Search & Filter Bar

    private var searchAndFilterBar: some View {
        VStack(spacing: 10) {
            // Search field
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.body)
                    .foregroundStyle(AppTheme.textMuted)
                TextField(localizer.t.proxmoxSearchPrompt, text: $searchQuery)
                    .font(.subheadline)
                    .autocorrectionDisabled()
                if !searchQuery.isEmpty {
                    Button {
                        searchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.body)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
            }
            .padding(12)
            .glassCard()

            // Filter chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    // Status filter
                    ForEach(ProxmoxGuestFilterStatus.allCases) { status in
                        filterChip(
                            label: statusLabel(for: status),
                            icon: statusIcon(for: status),
                            isSelected: filterStatus == status,
                            color: proxmoxColor
                        ) {
                            withAnimation(.spring(duration: 0.25)) {
                                filterStatus = status
                            }
                        }
                    }

                    Divider().frame(height: 20)

                    // Templates toggle
                    filterChip(
                        label: localizer.t.proxmoxTemplates,
                        icon: "square.stack.3d.up.fill",
                        isSelected: showTemplates,
                        color: .indigo
                    ) {
                        withAnimation(.spring(duration: 0.25)) {
                            showTemplates.toggle()
                        }
                    }
                }
            }
        }
    }

    private func statusLabel(for status: ProxmoxGuestFilterStatus) -> String {
        switch status {
        case .all: return localizer.t.proxmoxAllItems
        case .running: return localizer.t.proxmoxRunning
        case .stopped: return localizer.t.proxmoxStopped
        }
    }

    private func statusIcon(for status: ProxmoxGuestFilterStatus) -> String {
        switch status {
        case .all: return "list.bullet"
        case .running: return "play.circle.fill"
        case .stopped: return "stop.circle.fill"
        }
    }

    private func filterChip(label: String, icon: String, isSelected: Bool, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.caption)
                Text(label)
                    .font(.caption.bold())
            }
            .foregroundStyle(isSelected ? .white : color)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                isSelected ? color : color.opacity(0.1),
                in: Capsule()
            )
        }
        .buttonStyle(.plain)
    }

    private var clusterActionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxActions.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                NavigationLink(value: ProxmoxRoute.createGuest(instanceId: selectedInstanceId, preferredNode: nil)) {
                    clusterActionChip(icon: "plus.circle.fill", label: localizer.t.proxmoxCreateGuest, color: proxmoxColor)
                }
                .buttonStyle(.plain)

                NavigationLink(value: ProxmoxRoute.firewall(instanceId: selectedInstanceId, scope: .cluster)) {
                    clusterActionChip(icon: "flame.fill", label: localizer.t.proxmoxFirewall, color: .orange)
                }
                .buttonStyle(.plain)

                NavigationLink(value: ProxmoxRoute.backupJobs(instanceId: selectedInstanceId)) {
                    clusterActionChip(icon: "externaldrive.badge.timemachine", label: localizer.t.actionBackup, color: .green)
                }
                .buttonStyle(.plain)

                NavigationLink(value: ProxmoxRoute.haReplication(instanceId: selectedInstanceId)) {
                    clusterActionChip(icon: "shield.lefthalf.filled", label: localizer.t.proxmoxHaLabel, color: .blue)
                }
                .buttonStyle(.plain)

                NavigationLink(value: ProxmoxRoute.clusterResources(instanceId: selectedInstanceId)) {
                    clusterActionChip(icon: "chart.bar.fill", label: localizer.t.proxmoxClusterResources, color: .purple)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func clusterActionChip(icon: String, label: String, color: Color) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(color)
            Text(label)
                .font(.caption2.bold())
                .foregroundStyle(.primary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .glassCard()
    }

    // MARK: - Data Fetching

    @MainActor
    private func resetData() {
        nodes = []
        allVMs = [:]
        allLXCs = [:]
        allStorage = [:]
        pools = []
        poolMembersById = [:]
        version = nil
    }

    @MainActor
    private func fetchAll() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let versionTask = client.getVersion()
            async let nodesTask = client.getNodes()
            async let poolsTask = client.getPools()

            version = try await versionTask
            nodes = try await nodesTask
            pools = (try? await poolsTask) ?? []
            poolMembersById = [:]

            // Fetch VMs and LXCs for each online node
            await withTaskGroup(of: (String, [ProxmoxVM], [ProxmoxLXC], [ProxmoxStorage]).self) { group in
                for node in nodes where node.isOnline {
                    group.addTask {
                        let vms = (try? await client.getVMs(node: node.node, includeTemplates: true)) ?? []
                        let lxcs = (try? await client.getLXCs(node: node.node, includeTemplates: true)) ?? []
                        let storage = (try? await client.getStorage(node: node.node)) ?? []
                        return (node.node, vms, lxcs, storage)
                    }
                }
                for await (nodeName, vms, lxcs, storage) in group {
                    allVMs[nodeName] = vms
                    allLXCs[nodeName] = lxcs
                    allStorage[nodeName] = storage
                }
            }

            if !pools.isEmpty {
                await withTaskGroup(of: (String, [ProxmoxPoolMember]).self) { group in
                    for pool in pools {
                        group.addTask {
                            let members = (try? await client.getPoolMembers(poolid: pool.poolid).members) ?? []
                            return (pool.poolid, members)
                        }
                    }
                    for await (poolId, members) in group {
                        poolMembersById[poolId] = members
                    }
                }
            }

            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func localizedStatus(_ status: String) -> String {
        switch status.lowercased() {
        case "running":
            return localizer.t.proxmoxRunning
        case "stopped":
            return localizer.t.proxmoxStopped
        case "paused":
            return localizer.t.proxmoxPaused
        default:
            return status.capitalized
        }
    }
}

// MARK: - Routes

enum ProxmoxGuestType: String, Hashable {
    case qemu
    case lxc
}

enum ProxmoxRoute: Hashable {
    case nodeDetail(instanceId: UUID, nodeName: String)
    case guestDetail(instanceId: UUID, nodeName: String, vmid: Int, guestType: ProxmoxGuestType)
    case createGuest(instanceId: UUID, preferredNode: String?)
    case storageContent(instanceId: UUID, nodeName: String, storageName: String, storageType: String?)
    case firewall(instanceId: UUID, scope: FirewallScopeValue)
    case network(instanceId: UUID, nodeName: String)
    case backupJobs(instanceId: UUID)
    case services(instanceId: UUID, nodeName: String)
    case taskLog(instanceId: UUID, nodeName: String, task: ProxmoxTask)
    case haReplication(instanceId: UUID)
    case ceph(instanceId: UUID, nodeName: String)
    case clusterResources(instanceId: UUID)
    case poolDetail(instanceId: UUID, poolId: String)
}

// MARK: - Filter Status Enum

enum ProxmoxGuestFilterStatus: CaseIterable, Identifiable {
    case all, running, stopped

    var id: String { rawValue }
    var rawValue: String {
        switch self {
        case .all: return "all"
        case .running: return "running"
        case .stopped: return "stopped"
        }
    }
}

private struct ProxmoxDashboardTemplateGuest: Identifiable, Hashable {
    let nodeName: String
    let vmid: Int
    let guestType: ProxmoxGuestType
    let displayName: String
    let tags: [String]
    let lock: String?

    var id: String {
        "\(nodeName)-\(guestType.rawValue)-\(vmid)"
    }

    var iconName: String {
        guestType == .qemu ? "square.stack.3d.up.fill" : "shippingbox.circle.fill"
    }
}

/// Hashable wrapper for firewall scope to use in navigation
enum FirewallScopeValue: Hashable {
    case cluster
    case node(String)
    case guest(node: String, vmid: Int, guestType: ProxmoxGuestType)

    var toScope: ProxmoxFirewallView.FirewallScope {
        switch self {
        case .cluster: return .cluster
        case .node(let n): return .node(n)
        case .guest(let n, let vmid, let t): return .guest(node: n, vmid: vmid, guestType: t)
        }
    }
}

struct ProxmoxCreateGuestView: View {
    let instanceId: UUID
    let preferredNode: String?

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedMode: ProxmoxProvisioningMode = .vm
    @State private var state: LoadableState<Void> = .idle
    @State private var actionError: String?
    @State private var actionInProgress = false
    @State private var onlineNodes: [ProxmoxNode] = []
    @State private var pools: [ProxmoxPool] = []
    @State private var storagesByNode: [String: [ProxmoxStorage]] = [:]
    @State private var networksByNode: [String: [ProxmoxNetwork]] = [:]
    @State private var storageContentsByKey: [String: [ProxmoxStorageContent]] = [:]
    @State private var templateOptions: [ProxmoxProvisionableTemplate] = []
    @State private var suggestedVmid: Int?
    @State private var trackedTask: ProxmoxProvisionTrackedTask?

    @State private var vmDraft = ProxmoxVMProvisionDraft()
    @State private var vmInstallSource: ProxmoxVMInstallSource = .iso
    @State private var lxcDraft = ProxmoxLXCProvisionDraft()
    @State private var cloneDraft = ProxmoxTemplateCloneDraft()

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: { await fetchContext() }
        ) {
            heroSection
            modeSection
            currentFormSection

            if let trackedTask {
                taskSection(trackedTask)
            }

            submitSection
        }
        .navigationTitle(localizer.t.proxmoxCreateGuest)
        .alert(localizer.t.error, isPresented: .init(
            get: { actionError != nil },
            set: { if !$0 { actionError = nil } }
        )) {
            Button(localizer.t.done) { actionError = nil }
        } message: {
            Text(actionError ?? "")
        }
        .task { await fetchContext() }
        .task(id: trackedTask?.id) {
            guard trackedTask?.isRunning == true else { return }
            while true {
                try? await Task.sleep(for: .seconds(2.5))
                guard !Task.isCancelled else { break }
                await refreshTrackedTask()
                guard trackedTask?.isRunning == true else { break }
            }
        }
        .onChange(of: vmDraft.nodeName) { _, newValue in
            applyVMNodeDefaults(for: newValue)
        }
        .onChange(of: lxcDraft.nodeName) { _, newValue in
            applyLXCNodeDefaults(for: newValue)
        }
        .onChange(of: cloneDraft.targetNodeName) { _, newValue in
            applyCloneNodeDefaults(for: newValue)
        }
        .onChange(of: cloneDraft.sourceTemplateId) { _, _ in
            applyCloneSourceDefaults()
        }
        .onChange(of: vmInstallSource) { _, newValue in
            guard newValue == .iso, vmDraft.isoVolumeId.isEmpty else { return }
            vmDraft.isoVolumeId = isoOptions(for: vmDraft.nodeName).first?.volid ?? ""
        }
    }

    private var heroSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(systemName: "plus.circle.fill")
                    .font(.title2)
                    .foregroundStyle(proxmoxColor)
                    .frame(width: 48, height: 48)
                    .background(proxmoxColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(localizer.t.proxmoxProvisioning)
                        .font(.title3.bold())
                    Text(currentModeDescription)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer()

                if let suggestedVmid {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(localizer.t.proxmoxSuggestedVmid)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                        Text("#\(suggestedVmid)")
                            .font(.headline.bold())
                            .foregroundStyle(proxmoxColor)
                    }
                }
            }
        }
    }

    private var modeSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxCreateGuest.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 12) {
                Picker("", selection: $selectedMode) {
                    Text(localizer.t.proxmoxCreateVM).tag(ProxmoxProvisioningMode.vm)
                    Text(localizer.t.proxmoxCreateContainer).tag(ProxmoxProvisioningMode.lxc)
                    Text(localizer.t.proxmoxDeployFromTemplate).tag(ProxmoxProvisioningMode.templateClone)
                }
                .pickerStyle(.segmented)

                HStack(spacing: 10) {
                    modeBadge(title: localizer.t.proxmoxSelectNode, value: currentNodeLabel)
                    modeBadge(title: localizer.t.proxmoxVmidLabel, value: currentVmidLabel)
                    modeBadge(title: localizer.t.proxmoxType, value: currentTypeBadgeValue)
                }
            }
            .padding(14)
            .glassCard()
        }
    }

    @ViewBuilder
    private var currentFormSection: some View {
        switch selectedMode {
        case .vm:
            vmIdentitySection
            vmResourcesSection
            vmInstallSection
            vmNetworkSection
            vmOptionsSection
        case .lxc:
            lxcIdentitySection
            lxcResourcesSection
            lxcTemplateSection
            lxcNetworkSection
            lxcOptionsSection
        case .templateClone:
            cloneSourceSection
            cloneTargetSection
            cloneOptionsSection
        }
    }

    private var vmIdentitySection: some View {
        provisionSection(title: localizer.t.proxmoxGuestOverview) {
            fieldCard(title: localizer.t.proxmoxSelectNode) {
                Picker(localizer.t.proxmoxSelectNode, selection: $vmDraft.nodeName) {
                    ForEach(onlineNodes, id: \.node) { node in
                        Text(node.node).tag(node.node)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            fieldCard(title: localizer.t.proxmoxVmidLabel) {
                HStack(spacing: 10) {
                    TextField("100", text: $vmDraft.vmidText)
                        .keyboardType(.numberPad)
                        .textInputAutocapitalization(.never)
                    Button(localizer.t.proxmoxRefreshVmid) {
                        HapticManager.light()
                        Task { await refreshSuggestedVmid(forceReplaceCurrent: true) }
                    }
                    .font(.caption.bold())
                    .foregroundStyle(proxmoxColor)
                }
            }

            fieldCard(title: localizer.t.proxmoxGuestName) {
                TextField(localizer.t.proxmoxOptionalName, text: $vmDraft.name)
                    .textInputAutocapitalization(.never)
            }

            fieldCard(title: localizer.t.proxmoxPool) {
                Picker(localizer.t.proxmoxPool, selection: $vmDraft.poolName) {
                    Text(localizer.t.proxmoxNoneValue).tag("")
                    ForEach(pools, id: \.poolid) { pool in
                        Text(pool.poolid).tag(pool.poolid)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            fieldCard(title: localizer.t.proxmoxTags) {
                TextField("linux,lab", text: $vmDraft.tags)
                    .textInputAutocapitalization(.never)
            }

            fieldCard(title: localizer.t.proxmoxDescription) {
                TextField(localizer.t.proxmoxDescription, text: $vmDraft.description, axis: .vertical)
                    .lineLimit(2...4)
            }
        }
    }

    private var vmResourcesSection: some View {
        provisionSection(title: localizer.t.proxmoxResources) {
            fieldCard(title: localizer.t.proxmoxStorage) {
                Picker(localizer.t.proxmoxStorage, selection: $vmDraft.diskStorage) {
                    ForEach(vmStorageOptions(for: vmDraft.nodeName), id: \.storage) { storage in
                        Text(storage.storage).tag(storage.storage)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            StepperCard(title: localizer.t.proxmoxRam, value: "\(vmDraft.memoryMB) MB") {
                Stepper("", value: $vmDraft.memoryMB, in: 512...65536, step: 256)
                    .labelsHidden()
            }

            StepperCard(title: localizer.t.proxmoxCpuCores, value: "\(vmDraft.cores)") {
                Stepper("", value: $vmDraft.cores, in: 1...64)
                    .labelsHidden()
            }

            StepperCard(title: localizer.t.proxmoxSockets, value: "\(vmDraft.sockets)") {
                Stepper("", value: $vmDraft.sockets, in: 1...8)
                    .labelsHidden()
            }

            StepperCard(title: localizer.t.proxmoxDiskSize, value: "\(vmDraft.diskSizeGiB) GB") {
                Stepper("", value: $vmDraft.diskSizeGiB, in: 4...4096, step: 4)
                    .labelsHidden()
            }
        }
    }

    private var vmInstallSection: some View {
        provisionSection(title: localizer.t.proxmoxInstallSource) {
            fieldCard(title: localizer.t.proxmoxInstallSource) {
                Picker(localizer.t.proxmoxInstallSource, selection: $vmInstallSource) {
                    Text(localizer.t.proxmoxIsoImage).tag(ProxmoxVMInstallSource.iso)
                    Text(localizer.t.proxmoxBlankDisk).tag(ProxmoxVMInstallSource.blank)
                }
                .pickerStyle(.segmented)
            }

            if vmInstallSource == .iso {
                fieldCard(title: localizer.t.proxmoxIsoImage) {
                    if isoOptions(for: vmDraft.nodeName).isEmpty {
                        Text(localizer.t.proxmoxProvisionNoIso)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    } else {
                        Picker(localizer.t.proxmoxIsoImage, selection: $vmDraft.isoVolumeId) {
                            ForEach(isoOptions(for: vmDraft.nodeName), id: \.volid) { iso in
                                Text(iso.provisioningLabel).tag(iso.volid)
                            }
                        }
                        .pickerStyle(.menu)
                        .labelsHidden()
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }
        }
    }

    private var vmNetworkSection: some View {
        provisionSection(title: localizer.t.proxmoxNetwork) {
            fieldCard(title: localizer.t.proxmoxBridge) {
                if bridgeOptions(for: vmDraft.nodeName).isEmpty {
                    Text(localizer.t.proxmoxProvisionNoBridges)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                } else {
                    Picker(localizer.t.proxmoxBridge, selection: $vmDraft.bridge) {
                        ForEach(bridgeOptions(for: vmDraft.nodeName), id: \.self) { bridge in
                            Text(bridge).tag(bridge)
                        }
                    }
                    .pickerStyle(.menu)
                    .labelsHidden()
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private var vmOptionsSection: some View {
        provisionSection(title: localizer.t.proxmoxOptions) {
            fieldCard(title: localizer.t.proxmoxType) {
                Picker(localizer.t.proxmoxType, selection: $vmDraft.osType) {
                    Text("Linux").tag("l26")
                    Text("Windows").tag("win11")
                    Text("Windows Server").tag("win10")
                    Text(localizer.t.proxmoxOtherValue).tag("other")
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            fieldCard(title: localizer.t.proxmoxBiosLabel) {
                Picker(localizer.t.proxmoxBiosLabel, selection: $vmDraft.bios) {
                    Text("OVMF").tag("ovmf")
                    Text("SeaBIOS").tag("seabios")
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            fieldCard(title: localizer.t.proxmoxMachine) {
                Picker(localizer.t.proxmoxMachine, selection: $vmDraft.machine) {
                    Text("Q35").tag("q35")
                    Text("i440fx").tag("pc")
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            toggleCard(title: localizer.t.proxmoxAgent, isOn: $vmDraft.enableGuestAgent)
            toggleCard(title: localizer.t.proxmoxBootOnStart, isOn: $vmDraft.startAtBoot)
            toggleCard(title: localizer.t.proxmoxConvertToTemplateAfterCreate, isOn: $vmDraft.createAsTemplate)
        }
    }

    private var lxcIdentitySection: some View {
        provisionSection(title: localizer.t.proxmoxGuestOverview) {
            fieldCard(title: localizer.t.proxmoxSelectNode) {
                Picker(localizer.t.proxmoxSelectNode, selection: $lxcDraft.nodeName) {
                    ForEach(onlineNodes, id: \.node) { node in
                        Text(node.node).tag(node.node)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            fieldCard(title: localizer.t.proxmoxVmidLabel) {
                HStack(spacing: 10) {
                    TextField("101", text: $lxcDraft.vmidText)
                        .keyboardType(.numberPad)
                        .textInputAutocapitalization(.never)
                    Button(localizer.t.proxmoxRefreshVmid) {
                        HapticManager.light()
                        Task { await refreshSuggestedVmid(forceReplaceCurrent: true) }
                    }
                    .font(.caption.bold())
                    .foregroundStyle(proxmoxColor)
                }
            }

            fieldCard(title: localizer.t.proxmoxHostname) {
                TextField("ubuntu-lxc", text: $lxcDraft.hostname)
                    .textInputAutocapitalization(.never)
            }

            fieldCard(title: localizer.t.proxmoxPool) {
                Picker(localizer.t.proxmoxPool, selection: $lxcDraft.poolName) {
                    Text(localizer.t.proxmoxNoneValue).tag("")
                    ForEach(pools, id: \.poolid) { pool in
                        Text(pool.poolid).tag(pool.poolid)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            fieldCard(title: localizer.t.proxmoxTags) {
                TextField("container,linux", text: $lxcDraft.tags)
                    .textInputAutocapitalization(.never)
            }

            fieldCard(title: localizer.t.proxmoxDescription) {
                TextField(localizer.t.proxmoxDescription, text: $lxcDraft.description, axis: .vertical)
                    .lineLimit(2...4)
            }
        }
    }

    private var lxcResourcesSection: some View {
        provisionSection(title: localizer.t.proxmoxResources) {
            fieldCard(title: localizer.t.proxmoxStorage) {
                Picker(localizer.t.proxmoxStorage, selection: $lxcDraft.rootfsStorage) {
                    ForEach(lxcStorageOptions(for: lxcDraft.nodeName), id: \.storage) { storage in
                        Text(storage.storage).tag(storage.storage)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            StepperCard(title: localizer.t.proxmoxRam, value: "\(lxcDraft.memoryMB) MB") {
                Stepper("", value: $lxcDraft.memoryMB, in: 128...65536, step: 128)
                    .labelsHidden()
            }

            StepperCard(title: localizer.t.proxmoxSwap, value: "\(lxcDraft.swapMB) MB") {
                Stepper("", value: $lxcDraft.swapMB, in: 0...65536, step: 128)
                    .labelsHidden()
            }

            StepperCard(title: localizer.t.proxmoxCpuCores, value: "\(lxcDraft.cores)") {
                Stepper("", value: $lxcDraft.cores, in: 1...64)
                    .labelsHidden()
            }

            StepperCard(title: localizer.t.proxmoxRootDisk, value: "\(lxcDraft.rootfsSizeGiB) GB") {
                Stepper("", value: $lxcDraft.rootfsSizeGiB, in: 2...2048, step: 2)
                    .labelsHidden()
            }
        }
    }

    private var lxcTemplateSection: some View {
        provisionSection(title: localizer.t.proxmoxContainerTemplate) {
            fieldCard(title: localizer.t.proxmoxContainerTemplate) {
                if lxcTemplateOptions(for: lxcDraft.nodeName).isEmpty {
                    Text(localizer.t.proxmoxProvisionNoContainerTemplates)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                } else {
                    Picker(localizer.t.proxmoxContainerTemplate, selection: $lxcDraft.templateVolumeId) {
                        ForEach(lxcTemplateOptions(for: lxcDraft.nodeName), id: \.volid) { template in
                            Text(template.provisioningLabel).tag(template.volid)
                        }
                    }
                    .pickerStyle(.menu)
                    .labelsHidden()
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            fieldCard(title: localizer.t.proxmoxPassword) {
                SecureField(localizer.t.proxmoxPasswordOptional, text: $lxcDraft.password)
            }
        }
    }

    private var lxcNetworkSection: some View {
        provisionSection(title: localizer.t.proxmoxNetwork) {
            fieldCard(title: localizer.t.proxmoxBridge) {
                if bridgeOptions(for: lxcDraft.nodeName).isEmpty {
                    Text(localizer.t.proxmoxProvisionNoBridges)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                } else {
                    Picker(localizer.t.proxmoxBridge, selection: $lxcDraft.bridge) {
                        ForEach(bridgeOptions(for: lxcDraft.nodeName), id: \.self) { bridge in
                            Text(bridge).tag(bridge)
                        }
                    }
                    .pickerStyle(.menu)
                    .labelsHidden()
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            fieldCard(title: localizer.t.proxmoxAddressing) {
                Picker(localizer.t.proxmoxAddressing, selection: $lxcDraft.addressMode) {
                    Text(localizer.t.proxmoxDhcp).tag(ProxmoxLXCAddressMode.dhcp)
                    Text(localizer.t.proxmoxStaticAddress).tag(ProxmoxLXCAddressMode.staticAddress)
                    Text(localizer.t.proxmoxManual).tag(ProxmoxLXCAddressMode.manual)
                }
                .pickerStyle(.segmented)
            }

            if lxcDraft.addressMode == .staticAddress {
                fieldCard(title: localizer.t.proxmoxIPv4Address) {
                    TextField("10.0.10.50/24", text: $lxcDraft.ipv4Address)
                        .textInputAutocapitalization(.never)
                }

                fieldCard(title: localizer.t.proxmoxGateway) {
                    TextField("10.0.10.1", text: $lxcDraft.gateway)
                        .textInputAutocapitalization(.never)
                }
            }
        }
    }

    private var lxcOptionsSection: some View {
        provisionSection(title: localizer.t.proxmoxOptions) {
            toggleCard(title: localizer.t.proxmoxUnprivileged, isOn: $lxcDraft.unprivileged)
            toggleCard(title: localizer.t.proxmoxBootOnStart, isOn: $lxcDraft.startAtBoot)
        }
    }

    private var cloneSourceSection: some View {
        provisionSection(title: localizer.t.proxmoxSourceTemplate) {
            fieldCard(title: localizer.t.proxmoxSourceTemplate) {
                if templateOptions.isEmpty {
                    Text(localizer.t.proxmoxNoTemplatesAvailable)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                } else {
                    Picker(localizer.t.proxmoxSourceTemplate, selection: $cloneDraft.sourceTemplateId) {
                        ForEach(templateOptions) { template in
                            Text(template.pickerLabel).tag(template.id)
                        }
                    }
                    .pickerStyle(.menu)
                    .labelsHidden()
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            if let selectedTemplate {
                HStack(spacing: 10) {
                    modeBadge(title: localizer.t.proxmoxType, value: selectedTemplate.guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc)
                    modeBadge(title: localizer.t.proxmoxSourceNode, value: selectedTemplate.nodeName)
                    modeBadge(title: localizer.t.proxmoxVmidLabel, value: "#\(selectedTemplate.vmid)")
                }
                .padding(.top, 2)
            }
        }
    }

    private var cloneTargetSection: some View {
        provisionSection(title: localizer.t.proxmoxCreateGuest) {
            fieldCard(title: localizer.t.proxmoxSelectNode) {
                Picker(localizer.t.proxmoxSelectNode, selection: $cloneDraft.targetNodeName) {
                    ForEach(onlineNodes, id: \.node) { node in
                        Text(node.node).tag(node.node)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            fieldCard(title: localizer.t.proxmoxVmidLabel) {
                HStack(spacing: 10) {
                    TextField("102", text: $cloneDraft.vmidText)
                        .keyboardType(.numberPad)
                        .textInputAutocapitalization(.never)
                    Button(localizer.t.proxmoxRefreshVmid) {
                        HapticManager.light()
                        Task { await refreshSuggestedVmid(forceReplaceCurrent: true) }
                    }
                    .font(.caption.bold())
                    .foregroundStyle(proxmoxColor)
                }
            }

            fieldCard(title: localizer.t.proxmoxGuestName) {
                TextField(localizer.t.proxmoxOptionalName, text: $cloneDraft.name)
                    .textInputAutocapitalization(.never)
            }

            fieldCard(title: localizer.t.proxmoxStorage) {
                Picker(localizer.t.proxmoxStorage, selection: $cloneDraft.targetStorage) {
                    Text(localizer.t.proxmoxUseSourceDefault).tag("")
                    ForEach(cloneStorageOptions, id: \.storage) { storage in
                        Text(storage.storage).tag(storage.storage)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var cloneOptionsSection: some View {
        provisionSection(title: localizer.t.proxmoxOptions) {
            fieldCard(title: localizer.t.proxmoxPool) {
                Picker(localizer.t.proxmoxPool, selection: $cloneDraft.poolName) {
                    Text(localizer.t.proxmoxNoneValue).tag("")
                    ForEach(pools, id: \.poolid) { pool in
                        Text(pool.poolid).tag(pool.poolid)
                    }
                }
                .pickerStyle(.menu)
                .labelsHidden()
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            toggleCard(title: localizer.t.proxmoxFullClone, isOn: $cloneDraft.fullClone)
        }
    }

    private var submitSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxActions.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            Button {
                Task { await submitSelectedMode() }
            } label: {
                HStack(spacing: 10) {
                    if actionInProgress {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "sparkles")
                            .font(.body.bold())
                    }
                    Text(submitButtonTitle)
                        .font(.subheadline.bold())
                    Spacer()
                    Text(currentNodeLabel)
                        .font(.caption.bold())
                        .foregroundStyle(.white.opacity(0.85))
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
                .background(
                    LinearGradient(colors: [proxmoxColor, proxmoxColor.opacity(0.72)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 18, style: .continuous)
                )
            }
            .buttonStyle(.plain)
            .disabled(isSubmitDisabled || actionInProgress || trackedTask?.isRunning == true)
            .opacity((isSubmitDisabled || actionInProgress || trackedTask?.isRunning == true) ? 0.6 : 1)

            if isSubmitDisabled {
                Text(localizer.t.proxmoxCompleteRequiredFields)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
    }

    private func taskSection(_ trackedTask: ProxmoxProvisionTrackedTask) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxCurrentOperation.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    Image(systemName: trackedTask.iconName)
                        .font(.title3)
                        .foregroundStyle(trackedTask.tintColor)
                        .frame(width: 42, height: 42)
                        .background(trackedTask.tintColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                    VStack(alignment: .leading, spacing: 3) {
                        Text(trackedTask.title)
                            .font(.subheadline.bold())
                        Text(trackedTask.statusLabel(localizer))
                            .font(.caption.bold())
                            .foregroundStyle(trackedTask.tintColor)
                    }

                    Spacer()

                    Text("#\(trackedTask.guestVmid)")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                }

                taskInfoRow(label: localizer.t.proxmoxTaskIdentifier, value: trackedTask.reference.upid)
                taskInfoRow(label: localizer.t.proxmoxTargetNode, value: trackedTask.guestNodeName)
                taskInfoRow(label: localizer.t.proxmoxLastUpdate, value: trackedTask.formattedLastUpdated)

                if let exitStatus = trackedTask.task.exitstatus, !trackedTask.isRunning {
                    taskInfoRow(label: localizer.t.proxmoxExitStatus, value: exitStatus)
                }

                HStack(spacing: 10) {
                    NavigationLink(
                        value: ProxmoxRoute.taskLog(
                            instanceId: instanceId,
                            nodeName: trackedTask.taskNode,
                            task: trackedTask.navigationTask
                        )
                    ) {
                        taskActionPill(icon: "doc.text.magnifyingglass", title: localizer.t.proxmoxOpenTaskLog, color: proxmoxColor)
                    }
                    .buttonStyle(.plain)

                    if trackedTask.task.isOk && !trackedTask.isRunning {
                        NavigationLink(
                            value: ProxmoxRoute.guestDetail(
                                instanceId: instanceId,
                                nodeName: trackedTask.guestNodeName,
                                vmid: trackedTask.guestVmid,
                                guestType: trackedTask.guestType
                            )
                        ) {
                            taskActionPill(icon: "arrow.right.circle.fill", title: localizer.t.proxmoxOpenCreatedGuest, color: trackedTask.tintColor)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(16)
            .glassCard()
        }
    }

    private func provisionSection<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
            content()
        }
    }

    private func fieldCard<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
            content()
                .font(.subheadline)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassCard()
    }

    private func StepperCard<Content: View>(title: String, value: String, @ViewBuilder content: () -> Content) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Text(value)
                    .font(.subheadline.bold())
            }
            Spacer()
            content()
        }
        .padding(14)
        .glassCard()
    }

    private func toggleCard(title: String, isOn: Binding<Bool>) -> some View {
        HStack {
            Text(title)
                .font(.subheadline)
            Spacer()
            Toggle("", isOn: isOn)
                .labelsHidden()
        }
        .padding(14)
        .glassCard()
    }

    private func modeBadge(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
            Text(value)
                .font(.caption.bold())
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(AppTheme.textMuted.opacity(0.08), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func taskInfoRow(label: String, value: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(label)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
            Spacer()
            Text(value)
                .font(.caption.weight(.medium))
                .multilineTextAlignment(.trailing)
                .textSelection(.enabled)
        }
    }

    private func taskActionPill(icon: String, title: String, color: Color) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption.bold())
            Text(title)
                .font(.caption.bold())
        }
        .foregroundStyle(color)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(color.opacity(0.1), in: Capsule())
    }

    private var currentModeDescription: String {
        switch selectedMode {
        case .vm:
            return localizer.t.proxmoxNewVMDescription
        case .lxc:
            return localizer.t.proxmoxNewContainerDescription
        case .templateClone:
            return localizer.t.proxmoxTemplateCloneDescription
        }
    }

    private var currentNodeLabel: String {
        switch selectedMode {
        case .vm:
            return vmDraft.nodeName.isEmpty ? "—" : vmDraft.nodeName
        case .lxc:
            return lxcDraft.nodeName.isEmpty ? "—" : lxcDraft.nodeName
        case .templateClone:
            return cloneDraft.targetNodeName.isEmpty ? "—" : cloneDraft.targetNodeName
        }
    }

    private var currentVmidLabel: String {
        switch selectedMode {
        case .vm:
            return vmDraft.vmidText.isEmpty ? "—" : vmDraft.vmidText
        case .lxc:
            return lxcDraft.vmidText.isEmpty ? "—" : lxcDraft.vmidText
        case .templateClone:
            return cloneDraft.vmidText.isEmpty ? "—" : cloneDraft.vmidText
        }
    }

    private var currentTypeBadgeValue: String {
        switch selectedMode {
        case .vm:
            return localizer.t.proxmoxGuestTypeQemu
        case .lxc:
            return localizer.t.proxmoxGuestTypeLxc
        case .templateClone:
            return localizer.t.proxmoxTemplate
        }
    }

    private var selectedTemplate: ProxmoxProvisionableTemplate? {
        templateOptions.first { $0.id == cloneDraft.sourceTemplateId }
    }

    private var cloneStorageOptions: [ProxmoxStorage] {
        guard let template = selectedTemplate else { return [] }
        switch template.guestType {
        case .qemu:
            return vmStorageOptions(for: cloneDraft.targetNodeName)
        case .lxc:
            return lxcStorageOptions(for: cloneDraft.targetNodeName)
        }
    }

    private var submitButtonTitle: String {
        switch selectedMode {
        case .vm:
            return localizer.t.proxmoxCreateVM
        case .lxc:
            return localizer.t.proxmoxCreateContainer
        case .templateClone:
            return localizer.t.proxmoxDeployTemplate
        }
    }

    private var isSubmitDisabled: Bool {
        switch selectedMode {
        case .vm:
            return Int(vmDraft.vmidText) == nil ||
                vmDraft.nodeName.isEmpty ||
                vmDraft.diskStorage.isEmpty ||
                vmDraft.bridge.isEmpty ||
                (vmInstallSource == .iso && vmDraft.isoVolumeId.isEmpty)
        case .lxc:
            let needsStaticAddress = lxcDraft.addressMode == .staticAddress && lxcDraft.ipv4Address.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            return Int(lxcDraft.vmidText) == nil ||
                lxcDraft.nodeName.isEmpty ||
                lxcDraft.hostname.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                lxcDraft.rootfsStorage.isEmpty ||
                lxcDraft.templateVolumeId.isEmpty ||
                lxcDraft.bridge.isEmpty ||
                needsStaticAddress
        case .templateClone:
            return Int(cloneDraft.vmidText) == nil ||
                cloneDraft.sourceTemplateId.isEmpty ||
                cloneDraft.targetNodeName.isEmpty
        }
    }

    private func bridgeOptions(for nodeName: String) -> [String] {
        let networks = networksByNode[nodeName] ?? []
        let bridges = networks
            .filter { ($0.type == "bridge" || $0.type == "OVSBridge") && $0.iface.isEmpty == false }
            .map(\.iface)
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }

        if !bridges.isEmpty { return bridges }

        return networks
            .map(\.iface)
            .filter { !$0.isEmpty }
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    private func vmStorageOptions(for nodeName: String) -> [ProxmoxStorage] {
        (storagesByNode[nodeName] ?? [])
            .filter { $0.isEnabled && ($0.contentTypes.contains("images") || $0.contentTypes.isEmpty) }
            .sorted { $0.storage.localizedCaseInsensitiveCompare($1.storage) == .orderedAscending }
    }

    private func lxcStorageOptions(for nodeName: String) -> [ProxmoxStorage] {
        (storagesByNode[nodeName] ?? [])
            .filter { $0.isEnabled && ($0.contentTypes.contains("rootdir") || $0.contentTypes.contains("images") || $0.contentTypes.isEmpty) }
            .sorted { $0.storage.localizedCaseInsensitiveCompare($1.storage) == .orderedAscending }
    }

    private func isoOptions(for nodeName: String) -> [ProxmoxStorageContent] {
        provisionContents(for: nodeName, contentType: "iso")
    }

    private func lxcTemplateOptions(for nodeName: String) -> [ProxmoxStorageContent] {
        provisionContents(for: nodeName, contentType: "vztmpl")
    }

    private func provisionContents(for nodeName: String, contentType: String) -> [ProxmoxStorageContent] {
        let storages = storagesByNode[nodeName] ?? []
        var items: [ProxmoxStorageContent] = []

        for storage in storages {
            let key = storageContentKey(node: nodeName, storage: storage.storage)
            let contents = storageContentsByKey[key] ?? []
            items.append(contentsOf: contents.filter { $0.content == contentType })
        }

        return Array(Set(items)).sorted { $0.provisioningLabel.localizedCaseInsensitiveCompare($1.provisioningLabel) == .orderedAscending }
    }

    private func storageContentKey(node: String, storage: String) -> String {
        "\(node)::\(storage)"
    }

    private func fetchContext(showLoading: Bool = true) async {
        if showLoading {
            state = .loading
        }

        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let nextVmidTask = client.getNextAvailableVmid()
            async let nodesTask = client.getNodes()
            async let poolsTask = client.getPools()

            let fetchedNodes = try await nodesTask
                .filter(\.isOnline)
                .sorted { $0.node.localizedCaseInsensitiveCompare($1.node) == .orderedAscending }
            let fetchedPools = ((try? await poolsTask) ?? [])
                .sorted { $0.poolid.localizedCaseInsensitiveCompare($1.poolid) == .orderedAscending }

            onlineNodes = fetchedNodes
            pools = fetchedPools

            var newStoragesByNode: [String: [ProxmoxStorage]] = [:]
            var newNetworksByNode: [String: [ProxmoxNetwork]] = [:]
            var newTemplateOptions: [ProxmoxProvisionableTemplate] = []

            await withTaskGroup(of: ProxmoxNodeProvisionContext.self) { group in
                for node in fetchedNodes {
                    group.addTask {
                        let storages = (try? await client.getStorage(node: node.node)) ?? []
                        let networks = (try? await client.getNetworks(node: node.node)) ?? []
                        let vms = (try? await client.getVMs(node: node.node, includeTemplates: true)) ?? []
                        let lxcs = (try? await client.getLXCs(node: node.node, includeTemplates: true)) ?? []
                        return ProxmoxNodeProvisionContext(
                            nodeName: node.node,
                            storages: storages,
                            networks: networks,
                            vmTemplates: vms.filter(\.isTemplate),
                            lxcTemplates: lxcs.filter(\.isTemplate)
                        )
                    }
                }

                for await context in group {
                    newStoragesByNode[context.nodeName] = context.storages
                    newNetworksByNode[context.nodeName] = context.networks

                    newTemplateOptions.append(contentsOf: context.vmTemplates.map {
                        ProxmoxProvisionableTemplate(
                            nodeName: context.nodeName,
                            vmid: $0.vmid,
                            guestType: .qemu,
                            displayName: $0.displayName,
                            tags: $0.tagList,
                            lock: $0.lock
                        )
                    })
                    newTemplateOptions.append(contentsOf: context.lxcTemplates.map {
                        ProxmoxProvisionableTemplate(
                            nodeName: context.nodeName,
                            vmid: $0.vmid,
                            guestType: .lxc,
                            displayName: $0.displayName,
                            tags: $0.tagList,
                            lock: $0.lock
                        )
                    })
                }
            }

            storagesByNode = newStoragesByNode
            networksByNode = newNetworksByNode
            templateOptions = newTemplateOptions.sorted {
                if $0.displayName != $1.displayName {
                    return $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending
                }
                if $0.nodeName != $1.nodeName {
                    return $0.nodeName.localizedCaseInsensitiveCompare($1.nodeName) == .orderedAscending
                }
                return $0.vmid < $1.vmid
            }

            var newStorageContents: [String: [ProxmoxStorageContent]] = [:]
            await withTaskGroup(of: (String, [ProxmoxStorageContent]).self) { group in
                for node in fetchedNodes {
                    let storages = newStoragesByNode[node.node] ?? []
                    for storage in storages where storage.isEnabled && storageSupportsProvisioningAssets(storage) {
                        group.addTask {
                            let contents = (try? await client.getStorageContent(node: node.node, storage: storage.storage)) ?? []
                            return ("\(node.node)::\(storage.storage)", contents)
                        }
                    }
                }

                for await (key, contents) in group {
                    newStorageContents[key] = contents
                }
            }
            storageContentsByKey = newStorageContents

            suggestedVmid = try? await nextVmidTask
            applyDefaults()
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func storageSupportsProvisioningAssets(_ storage: ProxmoxStorage) -> Bool {
        storage.contentTypes.isEmpty || storage.contentTypes.contains("iso") || storage.contentTypes.contains("vztmpl")
    }

    private func applyDefaults() {
        let fallbackNode = onlineNodes.first?.node ?? ""
        let preferredAvailableNode = {
            guard let preferredNode else { return "" }
            return onlineNodes.contains(where: { $0.node == preferredNode }) ? preferredNode : ""
        }()
        let defaultNode = preferredAvailableNode.isEmpty ? fallbackNode : preferredAvailableNode

        if vmDraft.nodeName.isEmpty { vmDraft.nodeName = defaultNode }
        if lxcDraft.nodeName.isEmpty { lxcDraft.nodeName = defaultNode }
        if cloneDraft.targetNodeName.isEmpty { cloneDraft.targetNodeName = defaultNode }

        applyVMNodeDefaults(for: vmDraft.nodeName)
        applyLXCNodeDefaults(for: lxcDraft.nodeName)
        applyCloneSourceDefaults()
        applyCloneNodeDefaults(for: cloneDraft.targetNodeName)

        if let suggestedVmid {
            if vmDraft.vmidText.isEmpty { vmDraft.vmidText = "\(suggestedVmid)" }
            if lxcDraft.vmidText.isEmpty { lxcDraft.vmidText = "\(suggestedVmid)" }
            if cloneDraft.vmidText.isEmpty { cloneDraft.vmidText = "\(suggestedVmid)" }
        }
    }

    private func applyVMNodeDefaults(for nodeName: String) {
        if vmDraft.diskStorage.isEmpty || !vmStorageOptions(for: nodeName).contains(where: { $0.storage == vmDraft.diskStorage }) {
            vmDraft.diskStorage = vmStorageOptions(for: nodeName).first?.storage ?? ""
        }
        if vmDraft.bridge.isEmpty || !bridgeOptions(for: nodeName).contains(vmDraft.bridge) {
            vmDraft.bridge = bridgeOptions(for: nodeName).first ?? ""
        }
        if vmInstallSource == .iso,
           (vmDraft.isoVolumeId.isEmpty || !isoOptions(for: nodeName).contains(where: { $0.volid == vmDraft.isoVolumeId })) {
            vmDraft.isoVolumeId = isoOptions(for: nodeName).first?.volid ?? ""
        }
    }

    private func applyLXCNodeDefaults(for nodeName: String) {
        if lxcDraft.rootfsStorage.isEmpty || !lxcStorageOptions(for: nodeName).contains(where: { $0.storage == lxcDraft.rootfsStorage }) {
            lxcDraft.rootfsStorage = lxcStorageOptions(for: nodeName).first?.storage ?? ""
        }
        if lxcDraft.bridge.isEmpty || !bridgeOptions(for: nodeName).contains(lxcDraft.bridge) {
            lxcDraft.bridge = bridgeOptions(for: nodeName).first ?? ""
        }
        if lxcDraft.templateVolumeId.isEmpty || !lxcTemplateOptions(for: nodeName).contains(where: { $0.volid == lxcDraft.templateVolumeId }) {
            lxcDraft.templateVolumeId = lxcTemplateOptions(for: nodeName).first?.volid ?? ""
        }
    }

    private func applyCloneSourceDefaults() {
        if cloneDraft.sourceTemplateId.isEmpty {
            cloneDraft.sourceTemplateId = templateOptions.first?.id ?? ""
        }

        if cloneDraft.targetNodeName.isEmpty {
            cloneDraft.targetNodeName = selectedTemplate?.nodeName ?? cloneDraft.targetNodeName
        }
    }

    private func applyCloneNodeDefaults(for nodeName: String) {
        if cloneDraft.targetStorage.isEmpty || !cloneStorageOptions.contains(where: { $0.storage == cloneDraft.targetStorage }) {
            cloneDraft.targetStorage = ""
        }
        if nodeName.isEmpty, let selectedTemplate {
            cloneDraft.targetNodeName = selectedTemplate.nodeName
        }
    }

    private func refreshSuggestedVmid(forceReplaceCurrent: Bool = false) async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
        do {
            let nextVmid = try await client.getNextAvailableVmid()
            suggestedVmid = nextVmid
            if forceReplaceCurrent {
                switch selectedMode {
                case .vm:
                    vmDraft.vmidText = "\(nextVmid)"
                case .lxc:
                    lxcDraft.vmidText = "\(nextVmid)"
                case .templateClone:
                    cloneDraft.vmidText = "\(nextVmid)"
                }
            }
        } catch {
            actionError = error.localizedDescription
        }
    }

    private func submitSelectedMode() async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
            actionError = localizer.t.proxmoxClientNotConfigured
            return
        }

        actionInProgress = true
        defer { actionInProgress = false }

        do {
            let reference: ProxmoxTaskReference
            let guestType: ProxmoxGuestType
            let guestNode: String
            let guestVmid: Int
            let title: String

            switch selectedMode {
            case .vm:
                guard let vmid = Int(vmDraft.vmidText) else {
                    actionError = localizer.t.proxmoxCompleteRequiredFields
                    return
                }
                let request = ProxmoxVMCreationRequest(
                    vmid: vmid,
                    name: vmDraft.name,
                    node: vmDraft.nodeName,
                    diskStorage: vmDraft.diskStorage,
                    diskSizeGiB: vmDraft.diskSizeGiB,
                    memoryMB: vmDraft.memoryMB,
                    cores: vmDraft.cores,
                    sockets: vmDraft.sockets,
                    bridge: vmDraft.bridge,
                    isoVolumeId: vmInstallSource == .iso ? vmDraft.isoVolumeId : nil,
                    osType: vmDraft.osType,
                    bios: vmDraft.bios,
                    machine: vmDraft.machine,
                    pool: vmDraft.poolName.isEmpty ? nil : vmDraft.poolName,
                    tags: vmDraft.tags.isEmpty ? nil : vmDraft.tags,
                    description: vmDraft.description.isEmpty ? nil : vmDraft.description,
                    enableGuestAgent: vmDraft.enableGuestAgent,
                    startAtBoot: vmDraft.startAtBoot,
                    createAsTemplate: vmDraft.createAsTemplate
                )
                reference = try await client.createVM(node: vmDraft.nodeName, request: request)
                guestType = .qemu
                guestNode = vmDraft.nodeName
                guestVmid = vmid
                title = vmDraft.createAsTemplate ? localizer.t.proxmoxCreateTemplate : localizer.t.proxmoxCreateVM

            case .lxc:
                guard let vmid = Int(lxcDraft.vmidText) else {
                    actionError = localizer.t.proxmoxCompleteRequiredFields
                    return
                }
                let request = ProxmoxLXCCreationRequest(
                    vmid: vmid,
                    hostname: lxcDraft.hostname,
                    node: lxcDraft.nodeName,
                    ostemplate: lxcDraft.templateVolumeId,
                    rootfsStorage: lxcDraft.rootfsStorage,
                    rootfsSizeGiB: lxcDraft.rootfsSizeGiB,
                    memoryMB: lxcDraft.memoryMB,
                    swapMB: lxcDraft.swapMB,
                    cores: lxcDraft.cores,
                    bridge: lxcDraft.bridge,
                    addressMode: lxcDraft.addressMode,
                    ipv4Address: lxcDraft.ipv4Address.isEmpty ? nil : lxcDraft.ipv4Address,
                    gateway: lxcDraft.gateway.isEmpty ? nil : lxcDraft.gateway,
                    password: lxcDraft.password.isEmpty ? nil : lxcDraft.password,
                    pool: lxcDraft.poolName.isEmpty ? nil : lxcDraft.poolName,
                    tags: lxcDraft.tags.isEmpty ? nil : lxcDraft.tags,
                    description: lxcDraft.description.isEmpty ? nil : lxcDraft.description,
                    unprivileged: lxcDraft.unprivileged,
                    startAtBoot: lxcDraft.startAtBoot
                )
                reference = try await client.createLXC(node: lxcDraft.nodeName, request: request)
                guestType = .lxc
                guestNode = lxcDraft.nodeName
                guestVmid = vmid
                title = localizer.t.proxmoxCreateContainer

            case .templateClone:
                guard let template = selectedTemplate,
                      let vmid = Int(cloneDraft.vmidText) else {
                    actionError = localizer.t.proxmoxCompleteRequiredFields
                    return
                }
                let resolvedName = cloneDraft.name.trimmingCharacters(in: .whitespacesAndNewlines)
                switch template.guestType {
                case .qemu:
                    reference = try await client.cloneVM(
                        node: template.nodeName,
                        vmid: template.vmid,
                        newVmid: vmid,
                        name: resolvedName.isEmpty ? nil : resolvedName,
                        full: cloneDraft.fullClone,
                        targetNode: cloneDraft.targetNodeName,
                        storage: cloneDraft.targetStorage.isEmpty ? nil : cloneDraft.targetStorage,
                        pool: cloneDraft.poolName.isEmpty ? nil : cloneDraft.poolName
                    )
                case .lxc:
                    reference = try await client.cloneLXC(
                        node: template.nodeName,
                        vmid: template.vmid,
                        newVmid: vmid,
                        name: resolvedName.isEmpty ? nil : resolvedName,
                        full: cloneDraft.fullClone,
                        targetNode: cloneDraft.targetNodeName,
                        storage: cloneDraft.targetStorage.isEmpty ? nil : cloneDraft.targetStorage,
                        pool: cloneDraft.poolName.isEmpty ? nil : cloneDraft.poolName
                    )
                }
                guestType = template.guestType
                guestNode = cloneDraft.targetNodeName.isEmpty ? template.nodeName : cloneDraft.targetNodeName
                guestVmid = vmid
                title = localizer.t.proxmoxDeployTemplate
            }

            HapticManager.success()
            await beginTrackingTask(
                title: title,
                guestType: guestType,
                guestNode: guestNode,
                guestVmid: guestVmid,
                reference: reference
            )
        } catch {
            HapticManager.error()
            actionError = error.localizedDescription
        }
    }

    private func beginTrackingTask(
        title: String,
        guestType: ProxmoxGuestType,
        guestNode: String,
        guestVmid: Int,
        reference: ProxmoxTaskReference
    ) async {
        let placeholderTask = ProxmoxTask(
            upid: reference.upid,
            type: title,
            status: "running",
            starttime: Int(Date().timeIntervalSince1970),
            node: reference.node ?? guestNode
        )

        trackedTask = ProxmoxProvisionTrackedTask(
            title: title,
            guestType: guestType,
            guestNodeName: guestNode,
            guestVmid: guestVmid,
            reference: reference,
            task: placeholderTask,
            lastUpdated: Date()
        )

        await refreshTrackedTask()
    }

    private func refreshTrackedTask() async {
        guard let trackedTask,
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }

        do {
            let refreshedTask = try await client.getTaskStatus(node: trackedTask.taskNode, upid: trackedTask.reference.upid)
            let updatedTask = trackedTask.updating(with: refreshedTask)
            self.trackedTask = updatedTask

            guard !updatedTask.isRunning else { return }

            if updatedTask.task.isOk {
                HapticManager.success()
                await fetchContext(showLoading: false)
                await refreshSuggestedVmid()
            } else {
                HapticManager.error()
            }
        } catch {
            self.trackedTask = trackedTask.touchingLastUpdated()
        }
    }
}

private enum ProxmoxProvisioningMode: String, CaseIterable, Identifiable {
    case vm
    case lxc
    case templateClone

    var id: String { rawValue }
}

private enum ProxmoxVMInstallSource: String, CaseIterable, Identifiable {
    case iso
    case blank

    var id: String { rawValue }
}

private struct ProxmoxVMProvisionDraft {
    var nodeName: String = ""
    var vmidText: String = ""
    var name: String = ""
    var diskStorage: String = ""
    var diskSizeGiB: Int = 32
    var memoryMB: Int = 4096
    var cores: Int = 2
    var sockets: Int = 1
    var bridge: String = ""
    var isoVolumeId: String = ""
    var osType: String = "l26"
    var bios: String = "ovmf"
    var machine: String = "q35"
    var poolName: String = ""
    var tags: String = ""
    var description: String = ""
    var enableGuestAgent: Bool = true
    var startAtBoot: Bool = true
    var createAsTemplate: Bool = false
}

private struct ProxmoxLXCProvisionDraft {
    var nodeName: String = ""
    var vmidText: String = ""
    var hostname: String = ""
    var templateVolumeId: String = ""
    var rootfsStorage: String = ""
    var rootfsSizeGiB: Int = 8
    var memoryMB: Int = 2048
    var swapMB: Int = 512
    var cores: Int = 2
    var bridge: String = ""
    var addressMode: ProxmoxLXCAddressMode = .dhcp
    var ipv4Address: String = ""
    var gateway: String = ""
    var password: String = ""
    var poolName: String = ""
    var tags: String = ""
    var description: String = ""
    var unprivileged: Bool = true
    var startAtBoot: Bool = true
}

private struct ProxmoxTemplateCloneDraft {
    var sourceTemplateId: String = ""
    var targetNodeName: String = ""
    var vmidText: String = ""
    var name: String = ""
    var targetStorage: String = ""
    var poolName: String = ""
    var fullClone: Bool = true
}

private struct ProxmoxNodeProvisionContext {
    let nodeName: String
    let storages: [ProxmoxStorage]
    let networks: [ProxmoxNetwork]
    let vmTemplates: [ProxmoxVM]
    let lxcTemplates: [ProxmoxLXC]
}

private struct ProxmoxProvisionableTemplate: Identifiable, Hashable {
    let nodeName: String
    let vmid: Int
    let guestType: ProxmoxGuestType
    let displayName: String
    let tags: [String]
    let lock: String?

    var id: String { "\(nodeName)-\(guestType.rawValue)-\(vmid)" }

    var pickerLabel: String {
        "\(displayName) • \(nodeName) • #\(vmid)"
    }
}

private struct ProxmoxProvisionTrackedTask: Identifiable {
    let title: String
    let guestType: ProxmoxGuestType
    let guestNodeName: String
    let guestVmid: Int
    let reference: ProxmoxTaskReference
    let task: ProxmoxTask
    let lastUpdated: Date

    var id: String { reference.upid }

    var taskNode: String {
        task.node ?? reference.node ?? guestNodeName
    }

    var isRunning: Bool {
        task.isRunning
    }

    var iconName: String {
        if isRunning {
            return "arrow.triangle.2.circlepath"
        }
        return task.isOk ? "checkmark.circle.fill" : "exclamationmark.triangle.fill"
    }

    var tintColor: Color {
        if isRunning {
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
        if isRunning {
            return localizer.t.proxmoxRunning
        }
        return task.isOk ? localizer.t.proxmoxOk : localizer.t.error
    }

    func updating(with task: ProxmoxTask) -> ProxmoxProvisionTrackedTask {
        ProxmoxProvisionTrackedTask(
            title: title,
            guestType: guestType,
            guestNodeName: guestNodeName,
            guestVmid: guestVmid,
            reference: reference,
            task: task,
            lastUpdated: Date()
        )
    }

    func touchingLastUpdated() -> ProxmoxProvisionTrackedTask {
        ProxmoxProvisionTrackedTask(
            title: title,
            guestType: guestType,
            guestNodeName: guestNodeName,
            guestVmid: guestVmid,
            reference: reference,
            task: task,
            lastUpdated: Date()
        )
    }
}

private extension ProxmoxStorageContent {
    var provisioningLabel: String {
        if let volumeName = volid.split(separator: "/").last {
            return String(volumeName)
        }
        return volid
    }
}
