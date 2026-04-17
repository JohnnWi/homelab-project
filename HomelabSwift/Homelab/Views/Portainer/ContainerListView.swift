import SwiftUI

// Maps to app/portainer/containers.tsx

struct ContainerListView: View {
    let instanceId: UUID
    let endpointId: Int

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var containers: [PortainerContainer] = []
    @State private var search = ""
    @State private var filter: FilterType = .all
    @State private var isLoading = true
    @State private var actionInProgress: String?
    @State private var containerStats: [String: ContainerStats] = [:]
    @State private var statsFetchTask: Task<Void, Never>?
    @State private var actionError: String?
    @State private var showActionError = false

    private let portainerColor = ServiceType.portainer.colors.primary

    enum FilterType: String, CaseIterable {
        case all, running, stopped
    }

    private var filteredContainers: [PortainerContainer] {
        var result = containers
        switch filter {
        case .all: break
        case .running: result = result.filter { ($0.State ?? "") == "running" }
        case .stopped: result = result.filter { let s = $0.State ?? ""; return s == "exited" || s == "dead" }
        }
        if !search.trimmingCharacters(in: .whitespaces).isEmpty {
            let q = search.lowercased()
            result = result.filter { $0.displayName.lowercased().contains(q) || ($0.Image ?? "").lowercased().contains(q) }
        }
        return result
    }

    private func filterLabel(_ f: FilterType) -> String {
        switch f {
        case .all: return localizer.t.containersAll
        case .running: return localizer.t.containersRunning
        case .stopped: return localizer.t.containersStopped
        }
    }

    private func filterCount(_ f: FilterType) -> Int {
        switch f {
        case .all: return containers.count
        case .running: return containers.filter { ($0.State ?? "") == "running" }.count
        case .stopped: return containers.filter { let s = $0.State ?? ""; return s == "exited" || s == "dead" }.count
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Search bar
            searchBar

            // Filter chips
            filterChips

            // Content
            if isLoading {
                Spacer()
                ProgressView()
                    .tint(portainerColor)
                Spacer()
            } else if filteredContainers.isEmpty {
                Spacer()
                ContentUnavailableView {
                    Label(localizer.t.containersEmpty, systemImage: "line.3.horizontal.decrease.circle")
                } description: {
                    Text(localizer.t.containersEmpty)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                }
                Spacer()
            } else {
                containerList
            }
        }
        .background(AppTheme.background)
        .navigationTitle(localizer.t.portainerContainers)
        .navigationDestination(for: PortainerRoute.self) { route in
            switch route {
            case .containerDetail(let routeInstanceId, let epId, let cId):
                ContainerDetailView(instanceId: routeInstanceId, endpointId: epId, containerId: cId)
            default: EmptyView()
            }
        }
        .task { await fetchContainers() }
        .onDisappear {
            statsFetchTask?.cancel()
            statsFetchTask = nil
        }
        .alert(localizer.t.error, isPresented: $showActionError) {
            Button(localizer.t.confirm, role: .cancel) { }
        } message: {
            Text(actionError ?? localizer.t.errorUnknown)
        }
    }

    // MARK: - Search Bar

    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
                .accessibilityHidden(true)
            TextField(localizer.t.containersSearch, text: $search)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !search.isEmpty {
                Button { search = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textMuted)
                }
                .accessibilityLabel(localizer.t.actionClear)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color(.separator), lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.top, 12)
    }

    // MARK: - Filter Chips

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(FilterType.allCases, id: \.self) { f in
                    Button {
                        HapticManager.light()
                        filter = f
                    } label: {
                        Text("\(filterLabel(f)) (\(filterCount(f)))")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(filter == f ? portainerColor : AppTheme.textSecondary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                filter == f
                                    ? portainerColor.opacity(0.1)
                                    : Color(.tertiarySystemFill)
                            )
                            .clipShape(Capsule())
                            .overlay(
                                Capsule()
                                    .stroke(filter == f ? portainerColor.opacity(0.3) : .clear, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }

    // MARK: - Container List

    private var containerList: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                ForEach(filteredContainers) { container in
                    NavigationLink(value: PortainerRoute.containerDetail(instanceId: instanceId, endpointId: endpointId, containerId: container.Id)) {
                        ContainerRow(
                            container: container,
                            stats: containerStats[container.Id],
                            actionInProgress: actionInProgress == container.Id,
                            t: localizer.translations,
                            onAction: { action in
                                handleAction(containerId: container.Id, action: action)
                            }
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 20)
        }
        .refreshable { await fetchContainers() }
    }

    // MARK: - Actions

    private func handleAction(containerId: String, action: ContainerAction) {
        HapticManager.medium()
        actionInProgress = containerId
        Task {
            do {
                guard let client = await servicesStore.portainerClient(instanceId: instanceId) else {
                    throw APIError.notConfigured
                }
                try await client.containerAction(endpointId: endpointId, containerId: containerId, action: action)
                HapticManager.success()
                await fetchContainers()
            } catch {
                HapticManager.error()
                actionError = error.localizedDescription
                showActionError = true
            }
            actionInProgress = nil
        }
    }

    // MARK: - Fetch

    private func fetchContainers() async {
        isLoading = containers.isEmpty
        defer { isLoading = false }
        do {
            guard let client = await servicesStore.portainerClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let list = try await client.getContainers(endpointId: endpointId)
            containers = list
            startStatsFetch(for: list)
        } catch {
            if containers.isEmpty {
                actionError = error.localizedDescription
                showActionError = true
            }
        }
    }

    private func startStatsFetch(for list: [PortainerContainer]) {
        statsFetchTask?.cancel()

        let runningContainers = list.filter { ($0.State ?? "") == "running" }
        let runningIDs = Set(runningContainers.map(\.Id))
        containerStats = containerStats.filter { runningIDs.contains($0.key) }

        let containersNeedingStats = runningContainers.filter { containerStats[$0.Id] == nil }
        guard !containersNeedingStats.isEmpty else { return }

        statsFetchTask = Task {
            let batchSize = 4
            var index = 0

            while !Task.isCancelled && index < containersNeedingStats.count {
                let upperBound = min(index + batchSize, containersNeedingStats.count)
                let batch = Array(containersNeedingStats[index..<upperBound])

                await withTaskGroup(of: (String, ContainerStats?).self) { group in
                    for container in batch {
                        group.addTask {
                            do {
                                guard let client = await servicesStore.portainerClient(instanceId: instanceId) else {
                                    return (container.Id, nil)
                                }
                                let stats = try await client.getContainerStats(endpointId: endpointId, containerId: container.Id)
                                return (container.Id, stats)
                            } catch {
                                return (container.Id, nil)
                            }
                        }
                    }

                    for await (containerId, stats) in group {
                        guard !Task.isCancelled, let stats else { continue }
                        await MainActor.run {
                            containerStats[containerId] = stats
                        }
                    }
                }

                index += batchSize
            }
        }
    }
}

// MARK: - Container Row

struct ContainerRow: View {
    let container: PortainerContainer
    let stats: ContainerStats?
    let actionInProgress: Bool
    let t: Translations
    let onAction: (ContainerAction) -> Void

    private let portainerColor = ServiceType.portainer.colors.primary

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header: status dot + name + actions
            HStack(alignment: .center, spacing: 8) {
                Circle()
                    .fill(AppTheme.statusColor(for: container.State ?? ""))
                    .frame(width: 8, height: 8)
                    .shadow(color: AppTheme.statusColor(for: container.State ?? "").opacity(0.5), radius: 2)
                
                Text(container.displayName)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                
                Spacer(minLength: 4)
                
                // Action Toolbar
                HStack(spacing: 12) {
                    if actionInProgress {
                        ProgressView()
                            .tint(portainerColor)
                            .scaleEffect(0.8)
                            .frame(width: 32, height: 32)
                    } else {
                        let state = container.State ?? ""
                        if state == "running" {
                            actionIconButton(action: .stop, color: AppTheme.stopped)
                            actionIconButton(action: .pause, color: AppTheme.info)
                            actionIconButton(action: .restart, color: AppTheme.warning)
                        } else if state == "paused" {
                            actionIconButton(action: .stop, color: AppTheme.stopped)
                            actionIconButton(action: .unpause, color: AppTheme.running)
                            actionIconButton(action: .restart, color: AppTheme.warning)
                        } else {
                            actionIconButton(action: .start, color: AppTheme.running)
                        }
                    }
                }
            }

            VStack(alignment: .leading, spacing: 4) {
                // Image
                Text(container.Image ?? "Unknown")
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(1)

                // Status + created
                HStack {
                    Label(container.Status ?? "", systemImage: "clock")
                        .font(.system(size: 9))
                        .foregroundStyle(AppTheme.textSecondary)
                    Spacer()
                    Text(Formatters.formatUnixDate(container.Created ?? 0))
                        .font(.system(size: 9))
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            // Ports
            if let ports = container.Ports, !ports.isEmpty {
                let visiblePorts = ports.filter { $0.PublicPort != nil }.prefix(3)
                if !visiblePorts.isEmpty {
                    HStack(spacing: 6) {
                        ForEach(Array(visiblePorts.enumerated()), id: \.offset) { _, port in
                            Text("\(port.PublicPort ?? 0):\(port.PrivatePort)/\(port.portType)")
                                .font(.system(size: 9, weight: .medium))
                                .foregroundStyle(AppTheme.info)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(AppTheme.info.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        }
                    }
                }
            }

            // Stats section
            let state = container.State ?? ""
            if state == "running" || state == "paused" {
                Divider().opacity(0.5)
                statsGrid
            }
        }
        .padding(14)
        .glassCard()
    }

    private var statsGrid: some View {
        HStack(alignment: .top, spacing: 8) {
            horizontalStatItem(
                icon: "cpu",
                label: "CPU",
                value: cpuValue,
                percent: cpuPercent,
                color: AppTheme.info
            )
            horizontalStatItem(
                icon: "memorychip",
                label: "Mem",
                value: memoryValue,
                percent: memoryPercent,
                color: AppTheme.warning
            )
            horizontalStatItem(
                icon: "network",
                label: "Net",
                value: networkValue.components(separatedBy: " / ").first ?? "—",
                color: AppTheme.running
            )
            horizontalStatItem(
                icon: "list.number",
                label: "PIDs",
                value: pidsValue,
                color: .primary.opacity(0.6)
            )
        }
        .padding(.top, 4)
    }

    private func horizontalStatItem(icon: String, label: String, value: String, percent: Double? = nil, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(color)
                Text(label)
                    .font(.system(size: 10))
                    .foregroundStyle(AppTheme.textSecondary)
            }
            
            Text(value)
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(.primary)
                .lineLimit(1)
            
            if let percent = percent {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 1)
                            .fill(color.opacity(0.1))
                        RoundedRectangle(cornerRadius: 1)
                            .fill(color.gradient)
                            .frame(width: geo.size.width * CGFloat(min(max(percent / 100, 0), 1)))
                    }
                }
                .frame(height: 2)
            } else {
                Spacer(minLength: 2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(8)
        .background(Color.white.opacity(0.04), in: RoundedRectangle(cornerRadius: 8))
    }

    private var cpuPercent: Double {
        guard let stats = stats, let cpu = stats.cpu_stats, let pre = stats.precpu_stats else { return 0 }
        let cpuDelta = Double((cpu.cpu_usage.total_usage) - (pre.cpu_usage.total_usage))
        let systemDelta = Double((cpu.system_cpu_usage ?? 0) - (pre.system_cpu_usage ?? 0))
        return Formatters.calculateCpuPercent(cpuDelta: cpuDelta, systemDelta: systemDelta, cpuCount: cpu.online_cpus ?? 1)
    }

    private var cpuValue: String {
        guard stats != nil else { return "—" }
        return String(format: "%.1f%%", cpuPercent)
    }

    private var memoryPercent: Double {
        guard let stats = stats, let mem = stats.memory_stats, (mem.limit ?? 0) > 0 else { return 0 }
        let usage = mem.usage ?? 0
        let cache = mem.stats?.cache ?? 0
        let finalUsage = Double(max(0, usage - cache))
        return (finalUsage / Double(mem.limit ?? 1)) * 100
    }

    private var memoryValue: String {
        guard let stats = stats, let mem = stats.memory_stats else { return "—" }
        let usage = mem.usage ?? 0
        let cache = mem.stats?.cache ?? 0
        let finalUsage = Double(max(0, usage - cache))
        return Formatters.formatBytes(finalUsage, decimals: 1)
    }

    private var networkValue: String {
        guard let stats = stats, let networks = stats.networks else { return "—" }
        let rx = Double(networks.values.reduce(0) { $0 + $1.rx_bytes })
        let tx = Double(networks.values.reduce(0) { $0 + $1.tx_bytes })
        return "\(Formatters.formatBytes(rx)) / \(Formatters.formatBytes(tx))"
    }

    private var pidsValue: String {
        guard let stats = stats, let pids = stats.pids_stats?.current else { return "—" }
        return "\(pids)"
    }

    private func actionIconButton(action: ContainerAction, color: Color) -> some View {
        Button {
            onAction(action)
        } label: {
            Image(systemName: action.symbolName)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(color)
                .frame(width: 32, height: 32)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}
