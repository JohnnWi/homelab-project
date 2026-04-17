import SwiftUI

private struct CraftyServerRow: Identifiable, Hashable {
    let server: CraftyServer
    let stats: CraftyServerStats?

    var id: Int { server.id }
}

private struct CraftyDashboardData: Equatable {
    let rows: [CraftyServerRow]

    var runningServers: Int { rows.filter { $0.stats?.running == true }.count }
    var totalPlayers: Int { rows.reduce(0) { $0 + ($1.stats?.online ?? 0) } }
}

private enum CraftySheetRoute: Identifiable {
    case logs(CraftyServer)
    case command(CraftyServer)

    var id: String {
        switch self {
        case .logs(let server):
            return "logs-\(server.id)"
        case .command(let server):
            return "command-\(server.id)"
        }
    }
}

struct CraftyDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedInstanceId: UUID
    @State private var dashboard: CraftyDashboardData?
    @State private var state: LoadableState<Void> = .idle
    @State private var actionServerId: Int?
    @State private var activeSheet: CraftySheetRoute?

    private let craftyColor = ServiceType.craftyController.colors.primary
    private let actionGrid = [GridItem(.adaptive(minimum: 132), spacing: 8)]

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .craftyController,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: fetchDashboard
        ) {
            instancePicker

            if let dashboard {
                overviewSection(dashboard)

                if dashboard.rows.isEmpty {
                    Text(localizer.t.craftyNoServers)
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
        .navigationTitle(ServiceType.craftyController.displayName)
        .task(id: selectedInstanceId) {
            await fetchDashboard()
        }
        .sheet(item: $activeSheet) { route in
            switch route {
            case .logs(let server):
                CraftyLogsSheet(instanceId: selectedInstanceId, server: server)
            case .command(let server):
                CraftyCommandSheet(instanceId: selectedInstanceId, server: server)
            }
        }
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .craftyController)
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
                            servicesStore.setPreferredInstance(id: instance.id, for: .craftyController)
                            dashboard = nil
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? craftyColor : AppTheme.textMuted.opacity(0.4))
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
                            .glassCard(tint: instance.id == selectedInstanceId ? craftyColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func overviewSection(_ dashboard: CraftyDashboardData) -> some View {
        LazyVGrid(columns: twoColumnGrid, spacing: AppTheme.gridSpacing) {
            metricCard(
                title: localizer.t.craftyRunningServers,
                value: "\(dashboard.runningServers)/\(dashboard.rows.count)",
                icon: "server.rack",
                tint: craftyColor
            )
            metricCard(
                title: localizer.t.craftyTotalPlayers,
                value: "\(dashboard.totalPlayers)",
                icon: "person.3.fill",
                tint: craftyColor
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

    private func serverCard(_ row: CraftyServerRow) -> some View {
        let stats = row.stats
        let accent = statusColor(for: stats)

        return VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(row.server.serverName)
                        .font(.headline)
                    Text(row.server.type ?? localizer.t.craftyType)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
                Spacer()
                Text(statusText(for: stats))
                    .font(.caption.bold())
                    .foregroundStyle(accent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(accent.opacity(0.12), in: Capsule())
            }

            LazyVGrid(columns: twoColumnGrid, spacing: 12) {
                detailPill(title: localizer.t.craftyPlayers, value: "\(stats?.online ?? 0)/\(stats?.max ?? 0)", icon: "person.2.fill")
                detailPill(title: localizer.t.craftyCPU, value: stats?.cpu.map { String(format: "%.1f%%", $0) } ?? "N/A", icon: "speedometer")
                detailPill(title: localizer.t.craftyMemory, value: stats?.mem ?? "N/A", icon: "memorychip")
                detailPill(title: localizer.t.craftyVersion, value: stats?.version ?? "N/A", icon: "square.and.arrow.down.on.square")
            }

            if let world = stats?.worldName, !world.isEmpty {
                Text("\(localizer.t.craftyWorld): \(world)")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }

            LazyVGrid(columns: actionGrid, spacing: 8) {
                actionButton(title: localizer.t.actionStart, icon: "play.fill", enabled: actionServerId == nil && stats?.running != true, primary: true) {
                    await performAction(.start, serverId: row.server.serverID)
                }
                actionButton(title: localizer.t.actionStop, icon: "stop.fill", enabled: actionServerId == nil && stats?.running == true) {
                    await performAction(.stop, serverId: row.server.serverID)
                }
                actionButton(title: localizer.t.actionRestart, icon: "arrow.clockwise", enabled: actionServerId == nil && stats?.running == true) {
                    await performAction(.restart, serverId: row.server.serverID)
                }
                actionButton(title: localizer.t.actionBackup, icon: "externaldrive.badge.timemachine", enabled: actionServerId == nil) {
                    await performAction(.backup, serverId: row.server.serverID)
                }
                actionButton(title: localizer.t.craftyUpdateExecutable, icon: "arrow.down.circle", enabled: actionServerId == nil) {
                    await performAction(.updateExecutable, serverId: row.server.serverID)
                }
                actionButton(title: localizer.t.detailLogs, icon: "doc.text.magnifyingglass", enabled: true) {
                    activeSheet = .logs(row.server)
                }
                actionButton(title: localizer.t.detailCommand, icon: "terminal", enabled: true) {
                    activeSheet = .command(row.server)
                }
                actionButton(title: localizer.t.actionKill, icon: "exclamationmark.octagon.fill", enabled: actionServerId == nil && stats?.running == true, destructive: true) {
                    await performAction(.kill, serverId: row.server.serverID)
                }
            }
        }
        .padding(16)
        .glassCard(tint: accent.opacity(0.06))
    }

    private func detailPill(title: String, value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .foregroundStyle(craftyColor)
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
                (destructive ? AppTheme.danger : craftyColor).opacity(enabled ? (primary ? 0.18 : 0.14) : 0.06),
                in: RoundedRectangle(cornerRadius: 14, style: .continuous)
            )
        }
        .foregroundStyle(enabled ? (destructive ? AppTheme.danger : craftyColor) : AppTheme.textMuted)
        .disabled(!enabled)
        .buttonStyle(.plain)
    }

    private func statusText(for stats: CraftyServerStats?) -> String {
        guard let stats else { return localizer.t.craftyStatusOffline }
        if stats.crashed { return localizer.t.craftyStatusCrashed }
        if stats.updating || stats.downloading { return localizer.t.craftyStatusUpdating }
        if stats.waitingStart { return localizer.t.craftyStatusStarting }
        if stats.running { return localizer.t.craftyStatusRunning }
        return localizer.t.craftyStatusStopped
    }

    private func statusColor(for stats: CraftyServerStats?) -> Color {
        guard let stats else { return AppTheme.textMuted }
        if stats.crashed { return AppTheme.danger }
        if stats.updating || stats.downloading || stats.waitingStart { return AppTheme.warning }
        if stats.running { return AppTheme.running }
        return AppTheme.textMuted
    }

    private func fetchDashboard() async {
        do {
            if dashboard == nil {
                state = .loading
            }

            guard let client = await servicesStore.craftyClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            let servers = try await client.getServers()
            var rows: [CraftyServerRow] = []
            for chunk in servers.chunked(into: 4) {
                let chunkRows = try await withThrowingTaskGroup(of: CraftyServerRow.self) { group in
                    for server in chunk {
                        group.addTask {
                            let stats = try? await client.getServerStats(serverId: server.serverID)
                            return CraftyServerRow(server: server, stats: stats)
                        }
                    }
                    var collected: [CraftyServerRow] = []
                    while let next = try await group.next() {
                        collected.append(next)
                    }
                    return collected
                }
                rows.append(contentsOf: chunkRows)
            }

            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                dashboard = CraftyDashboardData(rows: rows.sorted { $0.server.serverName < $1.server.serverName })
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

    private func performAction(_ action: CraftyAction, serverId: Int) async {
        do {
            actionServerId = serverId
            guard let client = await servicesStore.craftyClient(instanceId: selectedInstanceId) else { return }
            try await client.sendAction(serverId: serverId, action: action)
            await fetchDashboard()
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
        actionServerId = nil
    }
}

private struct CraftyLogsSheet: View {
    let instanceId: UUID
    let server: CraftyServer

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    @State private var logs: [String] = []
    @State private var state: LoadableState<Void> = .idle

    var body: some View {
        NavigationStack {
            Group {
                switch state {
                case .idle, .loading:
                    VStack(spacing: 16) {
                        ProgressView()
                        Text(localizer.t.loading)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                case .error(let apiError):
                    ServiceErrorView(error: apiError, retryAction: loadLogs)
                case .offline:
                    ServiceErrorView(error: .networkError(NSError(domain: "Network", code: -1009)), retryAction: loadLogs)
                case .loaded:
                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            if logs.isEmpty {
                                Text(localizer.t.detailNoLogs)
                                    .font(.subheadline)
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            } else {
                                ForEach(Array(logs.enumerated()), id: \.offset) { _, line in
                                    Text(line)
                                        .font(.system(.footnote, design: .monospaced))
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .textSelection(.enabled)
                                }
                            }
                        }
                        .padding(AppTheme.padding)
                    }
                    .refreshable {
                        await loadLogs()
                    }
                }
            }
            .navigationTitle(server.serverName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(localizer.t.close) {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        Task { await loadLogs() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
        }
        .task {
            await loadLogs()
        }
    }

    private func loadLogs() async {
        do {
            if logs.isEmpty {
                state = .loading
            }
            guard let client = await servicesStore.craftyClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            let response = try await client.getServerLogs(serverId: server.serverID)
            logs = response
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}

private struct CraftyCommandSheet: View {
    let instanceId: UUID
    let server: CraftyServer

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    @State private var commandText = ""
    @State private var isSending = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text(localizer.t.craftyCommandHint)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)

                TextEditor(text: $commandText)
                    .font(.system(.body, design: .monospaced))
                    .frame(minHeight: 140)
                    .padding(12)
                    .background(AppTheme.surface.opacity(0.75), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(alignment: .topLeading) {
                        if commandText.isEmpty {
                            Text(localizer.t.craftyCommandPlaceholder)
                                .font(.body)
                                .foregroundStyle(AppTheme.textMuted)
                                .padding(.horizontal, 18)
                                .padding(.vertical, 20)
                                .allowsHitTesting(false)
                        }
                    }

                if let errorMessage, !errorMessage.isEmpty {
                    Text(errorMessage)
                        .font(.footnote)
                        .foregroundStyle(AppTheme.danger)
                }

                Button {
                    Task { await sendCommand() }
                } label: {
                    HStack {
                        Spacer()
                        if isSending {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Image(systemName: "paperplane.fill")
                            Text(localizer.t.detailCommand)
                                .fontWeight(.semibold)
                        }
                        Spacer()
                    }
                    .padding(.vertical, 12)
                    .background(craftyCommandButtonColor, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .foregroundStyle(.white)
                }
                .disabled(isSending || commandText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .buttonStyle(.plain)

                Spacer()
            }
            .padding(AppTheme.padding)
            .navigationTitle(server.serverName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(localizer.t.close) {
                        dismiss()
                    }
                }
            }
        }
    }

    private var craftyCommandButtonColor: Color {
        commandText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? AppTheme.textMuted : AppTheme.primary
    }

    private func sendCommand() async {
        let trimmed = commandText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        do {
            isSending = true
            errorMessage = nil
            guard let client = await servicesStore.craftyClient(instanceId: instanceId) else {
                errorMessage = localizer.t.errorNotConfigured
                isSending = false
                return
            }
            try await client.sendCommand(serverId: server.serverID, command: trimmed)
            HapticManager.success()
            // Show brief success feedback before dismissing.
            commandText = ""
            isSending = false
            try await Task.sleep(nanoseconds: 500_000_000) // 0.5s delay so user sees the spinner complete
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
            isSending = false
        }
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
