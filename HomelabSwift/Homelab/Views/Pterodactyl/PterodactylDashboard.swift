import SwiftUI

private struct PterodactylServerRow: Identifiable, Hashable {
    let server: PterodactylServer
    let resources: PterodactylResources?

    var id: String { server.identifier }

    var effectiveState: String? { resources?.currentState ?? server.status }
    var isRunning: Bool { effectiveState == "running" }
    var isStarting: Bool { effectiveState == "starting" }
    var isStopping: Bool { effectiveState == "stopping" }
}

private struct PterodactylDashboardData: Equatable {
    let rows: [PterodactylServerRow]

    var runningCount: Int { rows.filter(\.isRunning).count }
}

struct PterodactylDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedInstanceId: UUID
    @State private var dashboard: PterodactylDashboardData?
    @State private var state: LoadableState<Void> = .idle
    @State private var actionServerId: String?
    @State private var actionErrorMessage: String?

    private let pteroColor = ServiceType.pterodactyl.colors.primary
    private let twoColumnGrid = [GridItem(.flexible()), GridItem(.flexible())]
    private let actionGrid = [GridItem(.adaptive(minimum: 132), spacing: 8)]

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .pterodactyl,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: fetchDashboard
        ) {
            instancePicker

            if let dashboard {
                overviewSection(dashboard)

                if dashboard.rows.isEmpty {
                    Text(localizer.t.pterodactylNoServers)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .glassCard()
                } else {
                    ForEach(dashboard.rows) { row in
                        serverCard(row)
                    }
                }
            }
        }
        .navigationTitle(ServiceType.pterodactyl.displayName)
        .task(id: selectedInstanceId) {
            await fetchDashboard()
        }
    }

    // MARK: - Instance Picker

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .pterodactyl)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .pterodactyl)
                            dashboard = nil
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? pteroColor : AppTheme.textMuted.opacity(0.4))
                                    .frame(width: 10, height: 10)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(instance.displayLabel)
                                        .font(.subheadline.weight(.semibold))
                                    Text(instance.url)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                            }
                            .padding(14)
                            .glassCard(tint: instance.id == selectedInstanceId ? pteroColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Overview

    private func overviewSection(_ dashboard: PterodactylDashboardData) -> some View {
        LazyVGrid(columns: twoColumnGrid, spacing: AppTheme.gridSpacing) {
            metricCard(
                title: localizer.t.pterodactylRunningServers,
                value: "\(dashboard.runningCount)/\(dashboard.rows.count)",
                icon: "server.rack",
                tint: pteroColor
            )
            metricCard(
                title: localizer.t.pterodactylTotalServers,
                value: "\(dashboard.rows.count)",
                icon: "square.stack.3d.up.fill",
                tint: pteroColor
            )
        }
    }

    private func metricCard(title: String, value: String, icon: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Image(systemName: icon)
                .foregroundStyle(tint)
            Text(value)
                .font(.system(size: 30, weight: .bold))
            Text(title)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassCard(tint: tint.opacity(0.08))
    }

    // MARK: - Server Card

    private func serverCard(_ row: PterodactylServerRow) -> some View {
        let res = row.resources
        let accent = statusColor(for: row)
        let isTransient = row.isStarting || row.isStopping
        let isActionRunning = actionServerId == row.server.identifier
        let actionsEnabled = !isActionRunning && !isTransient && !row.server.isSuspended

        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(row.server.name)
                        .font(.headline)
                    Text(row.server.node ?? localizer.t.notAvailable)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
                Spacer()
                Text(statusText(for: row))
                    .font(.caption.bold())
                    .foregroundStyle(accent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(accent.opacity(0.12), in: Capsule())

                if isActionRunning {
                    ProgressView()
                        .controlSize(.small)
                        .padding(.top, 4)
                }
            }

            LazyVGrid(columns: twoColumnGrid, spacing: 12) {
                detailPill(
                    title: localizer.t.pterodactylCPU,
                    value: res.map { String(format: "%.1f%%", $0.resources.cpuAbsolute) } ?? localizer.t.notAvailable,
                    icon: "speedometer"
                )
                detailPill(
                    title: localizer.t.pterodactylRAM,
                    value: res.map { formatBytes($0.resources.memoryBytes) } ?? localizer.t.notAvailable,
                    icon: "memorychip"
                )
                detailPill(
                    title: localizer.t.pterodactylDisk,
                    value: res.map { formatBytes($0.resources.diskBytes) } ?? localizer.t.notAvailable,
                    icon: "internaldrive"
                )
                detailPill(
                    title: localizer.t.pterodactylUptime,
                    value: res.map { formatUptime($0.resources.uptime) } ?? localizer.t.notAvailable,
                    icon: "clock"
                )
            }

            if let description = row.server.description, !description.isEmpty {
                Text(description)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(2)
            }

            LazyVGrid(columns: actionGrid, spacing: 8) {
                actionButton(
                    title: localizer.t.actionStart,
                    icon: "play.fill",
                    enabled: actionsEnabled && !row.isRunning,
                    primary: true
                ) {
                    await performPower(.start, identifier: row.server.identifier)
                }
                actionButton(
                    title: localizer.t.actionStop,
                    icon: "stop.fill",
                    enabled: actionsEnabled && row.isRunning
                ) {
                    await performPower(.stop, identifier: row.server.identifier)
                }
                actionButton(
                    title: localizer.t.actionRestart,
                    icon: "arrow.clockwise",
                    enabled: actionsEnabled && row.isRunning
                ) {
                    await performPower(.restart, identifier: row.server.identifier)
                }
                actionButton(
                    title: localizer.t.actionKill,
                    icon: "exclamationmark.octagon.fill",
                    enabled: actionsEnabled && row.isRunning,
                    destructive: true
                ) {
                    await performPower(.kill, identifier: row.server.identifier)
                }
            }
        }
        .padding(16)
        .glassCard(tint: accent.opacity(0.06))
    }

    private func detailPill(title: String, value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .foregroundStyle(pteroColor)
            Text(value)
                .font(.subheadline.weight(.bold))
            Text(title)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(AppTheme.surface.opacity(0.7), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func actionButton(
        title: String,
        icon: String,
        enabled: Bool,
        primary: Bool = false,
        destructive: Bool = false,
        action: @escaping () async -> Void
    ) -> some View {
        Button {
            HapticManager.light()
            Task { await action() }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.caption.weight(.bold))
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .padding(.horizontal, 10)
            .padding(.vertical, 10)
            .background(
                (destructive ? AppTheme.danger : pteroColor).opacity(enabled ? (primary ? 0.18 : 0.14) : 0.06),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
        }
        .foregroundStyle(enabled ? (destructive ? AppTheme.danger : pteroColor) : AppTheme.textMuted)
        .disabled(!enabled)
        .buttonStyle(.plain)
    }

    // MARK: - Status helpers

    private func statusText(for row: PterodactylServerRow) -> String {
        if row.server.isSuspended { return localizer.t.pterodactylStatusSuspended }
        if row.server.isInstalling { return localizer.t.pterodactylStatusInstalling }
        guard let state = row.effectiveState else { return localizer.t.pterodactylStatusOffline }
        switch state {
        case "running":  return localizer.t.pterodactylStatusRunning
        case "starting": return localizer.t.pterodactylStatusStarting
        case "stopping": return localizer.t.pterodactylStatusStopping
        default:         return localizer.t.pterodactylStatusOffline
        }
    }

    private func statusColor(for row: PterodactylServerRow) -> Color {
        if row.server.isSuspended { return AppTheme.danger }
        if row.server.isInstalling { return AppTheme.warning }
        guard let res = row.resources else { return AppTheme.textMuted }
        switch res.currentState {
        case "running":  return AppTheme.running
        case "starting": return AppTheme.warning
        case "stopping": return AppTheme.warning
        default:         return AppTheme.textMuted
        }
    }

    // MARK: - Format helpers

    private func formatBytes(_ bytes: Int) -> String {
        let mb = Double(bytes) / 1_048_576
        if mb >= 1024 {
            return String(format: "%.1f GB", mb / 1024)
        }
        return String(format: "%.0f MB", mb)
    }

    private func formatUptime(_ ms: Int) -> String {
        let seconds = ms / 1000
        let hours = seconds / 3600
        let minutes = (seconds % 3600) / 60
        if hours > 0 {
            return "\(hours)h \(minutes)m"
        }
        return "\(minutes)m"
    }

    // MARK: - Data fetching

    private func fetchDashboard() async {
        do {
            if dashboard == nil {
                state = .loading
            }

            guard let client = await servicesStore.pterodactylClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            let servers = try await client.getServers()
            var rows: [PterodactylServerRow] = []

            for chunk in servers.chunked(into: 4) {
                let chunkRows = await withTaskGroup(of: PterodactylServerRow.self) { group in
                    for server in chunk {
                        group.addTask {
                            let resources = try? await client.getServerResources(identifier: server.identifier)
                            return PterodactylServerRow(server: server, resources: resources)
                        }
                    }
                    var collected: [PterodactylServerRow] = []
                    for await row in group {
                        collected.append(row)
                    }
                    return collected
                }
                rows.append(contentsOf: chunkRows)
            }

            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                dashboard = PterodactylDashboardData(rows: rows.sorted { $0.server.name < $1.server.name })
                state = .loaded(())
            }
        } catch let apiError as APIError {
            if dashboard == nil {
                state = .error(apiError)
            }
        } catch {
            if dashboard == nil {
                state = .error(.custom(error.localizedDescription))
            }
        }
    }

    // MARK: - Power actions

    private func performPower(_ signal: PterodactylPowerSignal, identifier: String) async {
        do {
            actionServerId = identifier
            actionErrorMessage = nil
            guard let client = await servicesStore.pterodactylClient(instanceId: selectedInstanceId) else { return }
            try await client.sendPowerSignal(identifier: identifier, signal: signal)
            HapticManager.success()
            await syncServerAfterAction(identifier: identifier, signal: signal)
        } catch {
            HapticManager.error()
            actionErrorMessage = error.localizedDescription
        }
        actionServerId = nil
    }

    private func syncServerAfterAction(identifier: String, signal: PterodactylPowerSignal) async {
        let attempts = signal == .kill ? 3 : 6
        for attempt in 0..<attempts {
            if attempt > 0 {
                try? await Task.sleep(nanoseconds: 1_500_000_000)
            }
            guard let client = await servicesStore.pterodactylClient(instanceId: selectedInstanceId) else { return }
            guard let resources = try? await client.getServerResources(identifier: identifier) else { continue }
            updateRow(identifier: identifier, resources: resources)
        }
    }

    private func updateRow(identifier: String, resources: PterodactylResources) {
        guard let dashboard else { return }
        let rows = dashboard.rows.map { row in
            if row.server.identifier == identifier {
                return PterodactylServerRow(server: row.server, resources: resources)
            }
            return row
        }
        self.dashboard = PterodactylDashboardData(rows: rows)
    }
}

private extension Array {
    func chunked(into size: Int) -> [[Element]] {
        guard size > 0 else { return [self] }
        return stride(from: 0, to: count, by: size).map { index in
            Array(self[index ..< Swift.min(index + size, count)])
        }
    }
}
