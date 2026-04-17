import SwiftUI

struct ProxmoxClusterResourcesView: View {
    let instanceId: UUID

    enum ResourceFilter: String, CaseIterable, Identifiable {
        case all, nodes, vms, lxcs, storage, running, stopped

        var id: String { rawValue }
    }

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var resources: [ProxmoxClusterResource] = []
    @State private var state: LoadableState<Void> = .idle
    @State private var searchQuery = ""
    @State private var selectedFilter: ResourceFilter = .all

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    // MARK: - Filtered Resources

    private var filteredResources: [ProxmoxClusterResource] {
        resources.filter { resource in
            // Filter by type
            let typeMatch: Bool = {
                switch selectedFilter {
                case .all: return true
                case .nodes: return resource.isNode
                case .vms: return resource.isQemu && !resource.isTemplate
                case .lxcs: return resource.isLXC && !resource.isTemplate
                case .storage: return resource.isStorage
                case .running: return resource.isRunning && !resource.isTemplate
                case .stopped: return !resource.isRunning && !resource.isTemplate
                }
            }()

            // Filter by search
            let searchMatch: Bool = {
                guard !searchQuery.isEmpty else { return true }
                let query = searchQuery.lowercased()
                let nameMatch = (resource.name ?? "").lowercased().contains(query)
                let nodeMatch = (resource.node ?? "").lowercased().contains(query)
                let vmidMatch = resource.vmid.map { "\($0)".contains(query) } ?? false
                let storageMatch = (resource.storage ?? "").lowercased().contains(query)
                return nameMatch || nodeMatch || vmidMatch || storageMatch
            }()

            return typeMatch && searchMatch
        }
        .sorted { a, b in
            // Sort by type priority, then name/vmid
            let typeOrder = { (r: ProxmoxClusterResource) -> Int in
                if r.isNode { return 0 }
                if r.isQemu { return 1 }
                if r.isLXC { return 2 }
                if r.isStorage { return 3 }
                return 4
            }
            let orderA = typeOrder(a)
            let orderB = typeOrder(b)
            if orderA != orderB { return orderA < orderB }
            return (a.name ?? "").localizedCaseInsensitiveCompare(b.name ?? "") == .orderedAscending
        }
    }

    // MARK: - Summary Stats

    private var totalNodes: Int { resources.filter(\.isNode).count }
    private var runningVMs: Int { resources.filter { $0.isQemu && $0.isRunning && !$0.isTemplate }.count }
    private var runningLXCs: Int { resources.filter { $0.isLXC && $0.isRunning && !$0.isTemplate }.count }
    private var totalStorageUsed: Int64 { resources.filter(\.isStorage).reduce(0) { $0 + ($1.disk ?? 0) } }
    private var totalStorageCapacity: Int64 { resources.filter(\.isStorage).reduce(0) { $0 + ($1.maxdisk ?? 0) } }
    private var onlineNodes: Int { resources.filter { $0.isNode && $0.isRunning }.count }

    // MARK: - Grouped Resources

    private var groupedResources: [(sectionTitle: String, resources: [ProxmoxClusterResource])] {
        let groups = filteredResources.reduce(into: [String: [ProxmoxClusterResource]]()) { result, resource in
            let key: String = {
                if resource.isNode { return localizer.t.proxmoxNodes }
                if resource.isQemu { return localizer.t.proxmoxVMs }
                if resource.isLXC { return localizer.t.proxmoxContainers }
                if resource.isStorage { return localizer.t.proxmoxStorage }
                return localizer.t.proxmoxResources
            }()
            result[key, default: []].append(resource)
        }

        let order: [String] = [
            localizer.t.proxmoxNodes,
            localizer.t.proxmoxVMs,
            localizer.t.proxmoxContainers,
            localizer.t.proxmoxStorage,
            localizer.t.proxmoxResources
        ]

        return order.compactMap { key in
            guard let items = groups[key], !items.isEmpty else { return nil }
            return (sectionTitle: key, resources: items)
        }
    }

    // MARK: - Body

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchResources
        ) {
            // Header
            clusterHeader

            // Summary cards
            summaryCardsSection

            // Search & Filter
            searchAndFilterSection

            // Resource list
            if filteredResources.isEmpty && !searchQuery.isEmpty {
                emptySearchView
            } else if filteredResources.isEmpty {
                emptyStateView
            } else {
                resourceListSection
            }
        }
        .navigationTitle(localizer.t.proxmoxClusterResources)
        .navigationBarTitleDisplayMode(.inline)
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
        .task { await fetchResources() }
    }

    // MARK: - Cluster Header

    private var clusterHeader: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(systemName: "chart.bar.fill")
                    .font(.body)
                    .foregroundStyle(proxmoxColor)
                    .frame(width: 40, height: 40)
                    .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    Text(localizer.t.proxmoxClusterResources)
                        .font(.body.weight(.bold))
                    Text(String(format: "%@: %d", localizer.t.proxmoxClusterHealth, resources.isEmpty ? 0 : resources.count))
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }

                Spacer()

                HStack(spacing: 5) {
                    Circle()
                        .fill(onlineNodes == totalNodes && totalNodes > 0 ? AppTheme.running : AppTheme.stopped)
                        .frame(width: 8, height: 8)
                    Text("\(onlineNodes)/\(totalNodes)")
                        .font(.caption.bold())
                        .foregroundStyle(onlineNodes == totalNodes && totalNodes > 0 ? AppTheme.running : AppTheme.stopped)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(
                    (onlineNodes == totalNodes && totalNodes > 0 ? AppTheme.running : AppTheme.stopped).opacity(0.12),
                    in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                )
            }
        }
    }

    // MARK: - Summary Cards

    private var summaryCardsSection: some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            summaryCard(
                icon: "server.rack",
                label: localizer.t.proxmoxNodes,
                value: "\(totalNodes)",
                subtitle: String(format: "%@: %@", localizer.t.proxmoxRunning, "\(onlineNodes)"),
                color: proxmoxColor
            )
            summaryCard(
                icon: "desktopcomputer",
                label: localizer.t.proxmoxVMs,
                value: "\(runningVMs)",
                subtitle: localizer.t.proxmoxRunning,
                color: AppTheme.info
            )
            summaryCard(
                icon: "shippingbox.fill",
                label: localizer.t.proxmoxContainers,
                value: "\(runningLXCs)",
                subtitle: localizer.t.proxmoxRunning,
                color: .green
            )
            summaryCard(
                icon: "externaldrive.fill",
                label: localizer.t.proxmoxStorage,
                value: Formatters.formatBytes(Double(totalStorageUsed)),
                subtitle: totalStorageCapacity > 0 ? String(format: "/ %@", Formatters.formatBytes(Double(totalStorageCapacity))) : "",
                color: AppTheme.paused
            )
        }
    }

    private func summaryCard(icon: String, label: String, value: String, subtitle: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(color)
                    .frame(width: 32, height: 32)
                    .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

                Text(label.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(value)
                    .font(.title3.bold())
                    .foregroundStyle(.primary)
                if !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(color)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .glassCard(tint: color.opacity(0.08))
    }

    // MARK: - Search & Filter

    private var searchAndFilterSection: some View {
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
                        withAnimation(.spring(duration: 0.25)) {
                            searchQuery = ""
                        }
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
                    ForEach(ResourceFilter.allCases) { filter in
                        filterChip(
                            label: filterLabel(for: filter),
                            icon: filterIcon(for: filter),
                            isSelected: selectedFilter == filter,
                            color: proxmoxColor
                        ) {
                            withAnimation(.spring(duration: 0.25)) {
                                selectedFilter = filter
                                HapticManager.light()
                            }
                        }
                    }
                }
            }
        }
    }

    private func filterLabel(for filter: ResourceFilter) -> String {
        switch filter {
        case .all: return localizer.t.proxmoxFilterAll
        case .nodes: return localizer.t.proxmoxFilterNodes
        case .vms: return localizer.t.proxmoxFilterVMs
        case .lxcs: return localizer.t.proxmoxFilterLXCs
        case .storage: return localizer.t.proxmoxFilterStorage
        case .running: return localizer.t.proxmoxFilterRunning
        case .stopped: return localizer.t.proxmoxFilterStopped
        }
    }

    private func filterIcon(for filter: ResourceFilter) -> String {
        switch filter {
        case .all: return "list.bullet"
        case .nodes: return "server.rack"
        case .vms: return "desktopcomputer"
        case .lxcs: return "shippingbox.fill"
        case .storage: return "externaldrive.fill"
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

    // MARK: - Resource List

    private var resourceListSection: some View {
        ForEach(groupedResources, id: \.sectionTitle) { group in
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(group.sectionTitle.sentenceCased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                    Spacer()
                    Text("\(group.resources.count)")
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.textMuted)
                }

                VStack(spacing: 0) {
                    ForEach(Array(group.resources.enumerated()), id: \.element.id) { index, resource in
                        resourceRow(resource)
                        if index < group.resources.count - 1 {
                            Divider().padding(.leading, 50)
                        }
                    }
                }
                .glassCard()
            }
        }
    }

    private func resourceRow(_ resource: ProxmoxClusterResource) -> some View {
        Group {
            if resource.isNode {
                nodeRow(resource)
            } else if resource.isQemu || resource.isLXC {
                guestRow(resource)
            } else if resource.isStorage {
                storageRow(resource)
            } else {
                unknownRow(resource)
            }
        }
    }

    // MARK: - Node Row

    private func nodeRow(_ resource: ProxmoxClusterResource) -> some View {
        Group {
            if let nodeName = resource.node, !nodeName.isEmpty {
                NavigationLink(value: ProxmoxRoute.nodeDetail(instanceId: instanceId, nodeName: nodeName)) {
                    nodeRowContent(resource, nodeName: nodeName)
                }
                .buttonStyle(.plain)
            } else {
                nodeRowContent(resource, nodeName: localizer.t.proxmoxUnknown)
            }
        }
    }

    private func nodeRowContent(_ resource: ProxmoxClusterResource, nodeName: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "server.rack")
                .font(.subheadline)
                .foregroundStyle(resource.isRunning ? proxmoxColor : AppTheme.textMuted)
                .frame(width: 32, height: 32)
                .background((resource.isRunning ? proxmoxColor : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(nodeName)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
                HStack(spacing: 4) {
                    Circle()
                        .fill(resource.isRunning ? AppTheme.running : AppTheme.stopped)
                        .frame(width: 6, height: 6)
                    Text(resource.isRunning ? localizer.t.proxmoxNodeOnline : localizer.t.proxmoxNodeOffline)
                        .font(.caption2)
                        .foregroundStyle(resource.isRunning ? AppTheme.running : AppTheme.stopped)
                }
            }

            Spacer()

            if resource.isRunning {
                VStack(alignment: .trailing, spacing: 2) {
                    resourceBar(label: localizer.t.proxmoxCpuLabel, percent: resource.cpuPercent, color: proxmoxColor, compact: true)
                    resourceBar(label: localizer.t.proxmoxRamLabel, percent: resource.memPercent, color: AppTheme.info, compact: true)
                }
            }

            Image(systemName: "chevron.right")
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    // MARK: - Guest Row (VM/LXC)

    private func guestRow(_ resource: ProxmoxClusterResource) -> some View {
        Group {
            if let nodeName = resource.node, !nodeName.isEmpty, let vmid = resource.vmid {
                let guestType: ProxmoxGuestType = resource.isQemu ? .qemu : .lxc
                NavigationLink(value: ProxmoxRoute.guestDetail(instanceId: instanceId, nodeName: nodeName, vmid: vmid, guestType: guestType)) {
                    guestRowContent(resource, guestType: guestType)
                }
                .buttonStyle(.plain)
            } else {
                let guestType: ProxmoxGuestType = resource.isQemu ? .qemu : .lxc
                guestRowContent(resource, guestType: guestType)
            }
        }
    }

    private func guestRowContent(_ resource: ProxmoxClusterResource, guestType: ProxmoxGuestType) -> some View {
        let iconName = resource.isQemu ? "desktopcomputer" : "shippingbox.fill"
        let guestColor = resource.isQemu ? proxmoxColor : .green

        return HStack(spacing: 12) {
            Image(systemName: iconName)
                .font(.subheadline)
                .foregroundStyle(resource.isRunning ? guestColor : AppTheme.textMuted)
                .frame(width: 32, height: 32)
                .background((resource.isRunning ? guestColor : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(resource.displayName)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    if let vmid = resource.vmid {
                        Text("#\(vmid)")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }

                HStack(spacing: 6) {
                    Circle()
                        .fill(resource.isRunning ? AppTheme.running : AppTheme.stopped)
                        .frame(width: 5, height: 5)
                    Text(resource.isRunning ? localizer.t.proxmoxRunning : localizer.t.proxmoxStopped)
                        .font(.caption2)
                        .foregroundStyle(resource.isRunning ? AppTheme.running : AppTheme.stopped)

                    if let node = resource.node {
                        Text("· \(node)")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
            }

            Spacer()

            if resource.isRunning {
                VStack(alignment: .trailing, spacing: 2) {
                    Text("\(localizer.t.proxmoxCpuLabel) \(String(format: "%.0f%%", resource.cpuPercent))")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textSecondary)
                    Text("\(localizer.t.proxmoxRamLabel) \(String(format: "%.0f%%", resource.memPercent))")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textSecondary)
                }
            }

            Image(systemName: "chevron.right")
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    // MARK: - Storage Row

    private func storageRow(_ resource: ProxmoxClusterResource) -> some View {
        Group {
            if let storageName = resource.storage, !storageName.isEmpty {
                NavigationLink(value: ProxmoxRoute.storageContent(instanceId: instanceId, nodeName: resource.node ?? "", storageName: storageName, storageType: resource.plugintype)) {
                    storageRowContent(resource, storageName: storageName)
                }
                .buttonStyle(.plain)
            } else {
                storageRowContent(resource, storageName: localizer.t.proxmoxUnknown)
            }
        }
    }

    private func storageRowContent(_ resource: ProxmoxClusterResource, storageName: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "externaldrive.fill")
                .font(.subheadline)
                .foregroundStyle(resource.isRunning ? AppTheme.info : AppTheme.textMuted)
                .frame(width: 32, height: 32)
                .background((resource.isRunning ? AppTheme.info : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(storageName)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
                HStack(spacing: 4) {
                    Circle()
                        .fill(resource.isRunning ? AppTheme.running : AppTheme.stopped)
                        .frame(width: 5, height: 5)
                    Text(resource.isRunning ? localizer.t.proxmoxRunning : localizer.t.proxmoxStopped)
                        .font(.caption2)
                        .foregroundStyle(resource.isRunning ? AppTheme.running : AppTheme.stopped)
                    if let node = resource.node {
                        Text("· \(node)")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 2) {
                Text(resource.storageUsageText)
                    .font(.caption2.bold())
                    .foregroundStyle(resource.isRunning ? AppTheme.info : AppTheme.textMuted)
                let storagePercent: Double = {
                    guard let used = resource.disk, let total = resource.maxdisk, total > 0 else { return 0 }
                    return Double(used) / Double(total) * 100
                }()
                resourceBar(label: "", percent: storagePercent, color: AppTheme.info, compact: true)
            }

            Image(systemName: "chevron.right")
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    // MARK: - Unknown Row

    private func unknownRow(_ resource: ProxmoxClusterResource) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "questionmark.circle")
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
                .frame(width: 32, height: 32)
                .background(AppTheme.textMuted.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(resource.displayName)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
                Text(resource.type ?? localizer.t.proxmoxUnknown)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    // MARK: - Resource Bar

    private func resourceBar(label: String, percent: Double, color: Color, compact: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            if !compact {
                HStack {
                    Text(label)
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.textMuted)
                    Spacer()
                    Text(String(format: "%.0f%%", percent))
                        .font(.caption2.bold())
                        .foregroundStyle(color)
                }
            } else {
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
            .frame(height: compact ? 4 : 6)
        }
    }

    // MARK: - Empty States

    private var emptySearchView: some View {
        VStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.title2)
                .foregroundStyle(AppTheme.textMuted)
            Text(String(format: localizer.t.proxmoxNoSearchResults, localizer.t.proxmoxResourceType))
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
        .glassCard()
    }

    private var emptyStateView: some View {
        VStack(spacing: 8) {
            Image(systemName: "chart.bar")
                .font(.title2)
                .foregroundStyle(AppTheme.textMuted)
            Text(localizer.t.proxmoxNoResources)
                .font(.subheadline.bold())
                .foregroundStyle(AppTheme.textMuted)
            Text(localizer.t.proxmoxNoResourcesDescription)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 30)
        .glassCard()
    }

    // MARK: - Data Fetching

    private func fetchResources() async {
        state = .loading
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
            state = .error(.notConfigured)
            return
        }

        do {
            resources = try await client.getClusterResources()
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}

// MARK: - Helpers Extension

extension ProxmoxClusterResource {
    var displayName: String {
        name ?? {
            if let vmid { return "VM \(vmid)" }
            if let storage { return storage }
            if let node { return node }
            return resourceId
        }()
    }

    var cpuPercent: Double {
        guard let cpu else { return 0 }
        return cpu * 100
    }

    var memPercent: Double {
        guard let mem, let maxmem, maxmem > 0 else { return 0 }
        return Double(mem) / Double(maxmem) * 100
    }

    var storageUsageText: String {
        guard let disk, let maxdisk else { return "-" }
        return "\(Formatters.formatBytes(Double(disk))) / \(Formatters.formatBytes(Double(maxdisk)))"
    }
}
