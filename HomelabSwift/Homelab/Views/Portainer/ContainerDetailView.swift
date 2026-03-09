import SwiftUI

// Maps to app/portainer/[containerId].tsx

struct ContainerDetailView: View {
    let endpointId: Int
    let containerId: String

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var detail: ContainerDetail?
    @State private var stats: ContainerStats?
    @State private var logs: String?
    @State private var stacks: [PortainerStack] = []
    @State private var stackFile: String?
    @State private var composeContent = ""
    @State private var composeEdited = false
    @State private var activeTab: TabType = .info
    @State private var isLoading = true
    @State private var isEditing = false
    @State private var editName = ""
    @State private var isSavingCompose = false
    @State private var actionError: String?
    @State private var showActionError = false

    private let portainerColor = ServiceType.portainer.colors.primary

    enum TabType: String, CaseIterable {
        case info, stats, logs, env, compose
    }

    private var containerName: String {
        detail?.Name.replacingOccurrences(of: "^/", with: "", options: .regularExpression) ?? localizer.t.loading
    }

    private var isRunning: Bool { detail?.State.Running ?? false }
    private var isPaused: Bool { detail?.State.Paused ?? false }

    private var matchedStack: PortainerStack? {
        guard let detail else { return nil }
        let projectName = detail.Config.Labels["com.docker.compose.project"]
        guard let projectName, !projectName.isEmpty else { return nil }
        return stacks.first { $0.Name.lowercased() == projectName.lowercased() }
    }

    var body: some View {
        ScrollView {
            if isLoading && detail == nil {
                VStack { Spacer(minLength: 200); ProgressView().tint(portainerColor); Spacer() }
            } else if let detail {
                LazyVStack(spacing: 12) {
                    headerCard(detail)
                    actionsRow
                    tabBar
                    tabContent
                }
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 30)
            } else {
                ContentUnavailableView(localizer.t.detailNotFound, systemImage: "exclamationmark.triangle")
            }
        }
        .background(AppTheme.background)
        .navigationTitle(containerName)
        .refreshable { await refreshAll() }
        .task { await fetchDetail() }
        .onChange(of: activeTab) { _, newTab in
            Task { await fetchTabData(newTab) }
        }
        .alert(localizer.t.error, isPresented: $showActionError) {
            Button(localizer.t.confirm, role: .cancel) {}
        } message: {
            Text(actionError ?? localizer.t.errorUnknown)
        }
    }

    // MARK: - Header Card

    private func headerCard(_ detail: ContainerDetail) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                if isEditing {
                    HStack(spacing: 6) {
                        TextField(containerName, text: $editName)
                            .font(.body.weight(.semibold))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .padding(8)
                            .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 8, style: .continuous).stroke(portainerColor, lineWidth: 1))
                        Button {
                            if !editName.trimmingCharacters(in: .whitespaces).isEmpty, editName.trimmingCharacters(in: .whitespaces) != containerName {
                                Task { await renameContainer(editName.trimmingCharacters(in: .whitespaces)) }
                            }
                            isEditing = false
                        } label: {
                            Image(systemName: "checkmark").foregroundStyle(AppTheme.running)
                        }
                        Button { isEditing = false } label: {
                            Image(systemName: "xmark").foregroundStyle(AppTheme.stopped)
                        }
                    }
                } else {
                    HStack(spacing: 8) {
                        Text(containerName)
                            .font(.title3.bold())
                            .lineLimit(1)
                        Button {
                            HapticManager.light()
                            editName = containerName
                            isEditing = true
                        } label: {
                            Image(systemName: "pencil")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }
                }
                Spacer()
                StatusBadge(status: detail.State.Status)
            }

            Text(detail.Config.Image)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)

            if isRunning, !detail.State.StartedAt.isEmpty {
                Text("\(localizer.t.detailUptime): \(Formatters.formatUptime(from: detail.State.StartedAt))")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
            }
        }
        .padding(18)
        .glassCard()
    }

    // MARK: - Actions Row

    private var actionsRow: some View {
        HStack(spacing: 8) {
            if isRunning {
                containerActionButton(.stop, color: AppTheme.stopped)
                containerActionButton(.restart, color: AppTheme.warning)
                if isPaused {
                    containerActionButton(.unpause, color: AppTheme.running)
                } else {
                    containerActionButton(.pause, color: AppTheme.info)
                }
            } else {
                containerActionButton(.start, color: AppTheme.running)
                removeButton
            }
        }
    }

    private func containerActionButton(_ action: ContainerAction, color: Color) -> some View {
        Button {
            HapticManager.medium()
            Task {
                do {
                    try await servicesStore.portainerClient.containerAction(endpointId: endpointId, containerId: containerId, action: action)
                    HapticManager.success()
                    await fetchDetail()
                } catch {
                    HapticManager.error()
                    actionError = error.localizedDescription
                    showActionError = true
                }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: action.symbolName)
                    .font(.caption)
                Text(action.displayName)
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(color)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(color.opacity(0.2), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    private var removeButton: some View {
        Button {
            HapticManager.medium()
            Task {
                do {
                    try await servicesStore.portainerClient.removeContainer(endpointId: endpointId, containerId: containerId, force: true)
                    HapticManager.success()
                } catch {
                    HapticManager.error()
                    actionError = error.localizedDescription
                    showActionError = true
                }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: "trash.fill")
                    .font(.caption)
                Text(localizer.t.actionRemove)
                    .font(.subheadline.weight(.semibold))
            }
            .foregroundStyle(AppTheme.danger)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(AppTheme.danger.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(AppTheme.danger.opacity(0.2), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Tab Bar

    private var tabBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 2) {
                ForEach(TabType.allCases, id: \.self) { tab in
                    Button {
                        HapticManager.light()
                        activeTab = tab
                    } label: {
                        HStack(spacing: 4) {
                            if tab == .compose {
                                Image(systemName: "doc.text")
                                    .font(.caption)
                            }
                            Text(tabLabel(tab))
                                .font(.subheadline.weight(activeTab == tab ? .semibold : .medium))
                        }
                        .foregroundStyle(activeTab == tab ? portainerColor : AppTheme.textMuted)
                        .padding(.vertical, 10)
                        .padding(.horizontal, 16)
                        .background(
                            activeTab == tab ? portainerColor.opacity(0.1) : .clear,
                            in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(4)
            .glassCard(cornerRadius: 12)
        }
    }

    private func tabLabel(_ tab: TabType) -> String {
        switch tab {
        case .info:    return localizer.t.detailInfo
        case .stats:   return localizer.t.detailStats
        case .logs:    return localizer.t.detailLogs
        case .env:     return localizer.t.detailEnv
        case .compose: return localizer.t.detailCompose
        }
    }

    // MARK: - Tab Content

    @ViewBuilder
    private var tabContent: some View {
        switch activeTab {
        case .info:    infoTab
        case .stats:   statsTab
        case .logs:    logsTab
        case .env:     envTab
        case .compose: composeTab
        }
    }

    // MARK: - Info Tab

    @ViewBuilder
    private var infoTab: some View {
        if let detail {
            // Container info
            VStack(alignment: .leading, spacing: 0) {
                Text(localizer.t.detailContainer)
                    .font(.subheadline.weight(.semibold))
                    .padding(.bottom, 4)
                infoRow(label: "ID", value: String(detail.Id.prefix(12)))
                infoRow(label: localizer.t.detailCreated, value: Formatters.formatDate(detail.Created))
                infoRow(label: localizer.t.detailHostname, value: detail.Config.Hostname)
                if let workDir = detail.Config.WorkingDir, !workDir.isEmpty {
                    infoRow(label: localizer.t.detailWorkDir, value: workDir)
                }
                if let cmd = detail.Config.Cmd, !cmd.isEmpty {
                    infoRow(label: localizer.t.detailCommand, value: cmd.joined(separator: " "))
                }
            }
            .padding(16)
            .glassCard()

            // Network info
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Image(systemName: "network")
                        .font(.subheadline)
                        .foregroundStyle(portainerColor)
                    Text(localizer.t.detailNetwork)
                        .font(.subheadline.weight(.semibold))
                }
                .padding(.bottom, 4)
                infoRow(label: localizer.t.detailMode, value: detail.HostConfig.NetworkMode)
                ForEach(Array(detail.NetworkSettings.Networks.keys.sorted()), id: \.self) { name in
                    if let net = detail.NetworkSettings.Networks[name] {
                        infoRow(label: name, value: net.IPAddress.isEmpty ? localizer.t.notAvailable : net.IPAddress)
                    }
                }
            }
            .padding(16)
            .glassCard()

            // Mounts
            if !detail.Mounts.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    HStack(spacing: 8) {
                        Image(systemName: "externaldrive.fill")
                            .font(.subheadline)
                            .foregroundStyle(portainerColor)
                        Text(localizer.t.detailMounts)
                            .font(.subheadline.weight(.semibold))
                    }
                    ForEach(Array(detail.Mounts.enumerated()), id: \.offset) { _, mount in
                        HStack(spacing: 6) {
                            Text(mount.mountType)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(AppTheme.info)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(AppTheme.info.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                            Text(mount.Source)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                                .lineLimit(1)
                            Text("→")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                            Text(mount.Destination)
                                .font(.caption)
                                .lineLimit(1)
                        }
                        .padding(.vertical, 4)
                        if mount.Destination != detail.Mounts.last?.Destination {
                            Divider()
                        }
                    }
                }
                .padding(16)
                .glassCard()
            }

            // Restart Policy
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Image(systemName: "tag.fill")
                        .font(.subheadline)
                        .foregroundStyle(portainerColor)
                    Text(localizer.t.detailRestartPolicy)
                        .font(.subheadline.weight(.semibold))
                }
                .padding(.bottom, 4)
                infoRow(label: localizer.t.detailPolicy, value: detail.HostConfig.RestartPolicy.Name.isEmpty ? localizer.t.none : detail.HostConfig.RestartPolicy.Name)
                infoRow(label: localizer.t.detailMaxRetries, value: "\(detail.HostConfig.RestartPolicy.MaximumRetryCount)")
            }
            .padding(16)
            .glassCard()
        }
    }

    private func infoRow(label: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
                .frame(minWidth: 80, alignment: .leading)
            Spacer()
            Text(value)
                .font(.caption.weight(.medium))
                .multilineTextAlignment(.trailing)
                .lineLimit(2)
        }
        .padding(.vertical, 8)
    }

    // MARK: - Stats Tab

    @ViewBuilder
    private var statsTab: some View {
        if !isRunning {
            Text(localizer.t.detailNotRunning)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
                .frame(maxWidth: .infinity)
                .padding(.top, 30)
        } else if let stats {
            let cpuDelta = Double(stats.cpu_stats.cpu_usage.total_usage - stats.precpu_stats.cpu_usage.total_usage)
            let systemDelta = Double((stats.cpu_stats.system_cpu_usage ?? 0) - (stats.precpu_stats.system_cpu_usage ?? 0))
            let cpuPercent = Formatters.calculateCpuPercent(cpuDelta: cpuDelta, systemDelta: systemDelta, cpuCount: stats.cpu_stats.online_cpus ?? 1)
            let memPercent = stats.memory_stats.limit > 0 ? (Double(stats.memory_stats.usage) / Double(stats.memory_stats.limit)) * 100 : 0

            // CPU
            GlassProgressCard(
                title: localizer.t.detailCpu,
                value: cpuPercent,
                icon: "cpu",
                color: AppTheme.info,
                subtitle: String(format: "%.2f%%", cpuPercent)
            )

            // Memory
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Image(systemName: "memorychip")
                        .font(.caption.bold())
                        .foregroundStyle(portainerColor)
                    Text(localizer.t.detailMemory)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                    Spacer()
                    Text("\(Formatters.formatBytes(Double(stats.memory_stats.usage))) / \(Formatters.formatBytes(Double(stats.memory_stats.limit)))")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textSecondary)
                }
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(.white.opacity(0.1))
                            .frame(height: 6)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(portainerColor.gradient)
                            .frame(width: geo.size.width * CGFloat(min(memPercent, 100)) / 100, height: 6)
                    }
                }
                .frame(height: 6)
                Text("\(String(format: "%.1f", memPercent))% \(localizer.t.detailUsed)")
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
            .padding(AppTheme.innerPadding)
            .glassCard()

            // Network I/O
            if let networks = stats.networks {
                let rx = networks.values.reduce(0) { $0 + $1.rx_bytes }
                let tx = networks.values.reduce(0) { $0 + $1.tx_bytes }
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        Image(systemName: "network")
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.paused)
                        Text(localizer.t.detailNetworkIO)
                            .font(.subheadline.weight(.semibold))
                    }
                    HStack(spacing: 16) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.down")
                                .font(.caption)
                                .foregroundStyle(AppTheme.running)
                            Text("RX")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                            Text(Formatters.formatBytes(Double(rx)))
                                .font(.subheadline.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(12)
                        .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                        HStack(spacing: 6) {
                            Image(systemName: "arrow.up")
                                .font(.caption)
                                .foregroundStyle(AppTheme.info)
                            Text("TX")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                            Text(Formatters.formatBytes(Double(tx)))
                                .font(.subheadline.weight(.semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(12)
                        .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
                .padding(AppTheme.innerPadding)
                .glassCard()
            }
        } else {
            ProgressView().tint(portainerColor).padding(.top, 30)
        }
    }

    // MARK: - Logs Tab

    @ViewBuilder
    private var logsTab: some View {
        if let logs {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Image(systemName: "terminal.fill")
                        .font(.subheadline)
                        .foregroundStyle(portainerColor)
                    Text(localizer.t.detailContainerLogs)
                        .font(.subheadline.weight(.semibold))
                }
                ScrollView(.horizontal) {
                    Text(logs.isEmpty ? localizer.t.detailNoLogs : logs)
                        .font(.system(.caption, design: .monospaced))
                        .foregroundStyle(AppTheme.textSecondary)
                        .textSelection(.enabled)
                }
                .frame(maxHeight: 400)
            }
            .padding(16)
            .glassCard()
        } else {
            ProgressView().tint(portainerColor).padding(.top, 30)
        }
    }

    // MARK: - Env Tab

    @ViewBuilder
    private var envTab: some View {
        if let detail {
            VStack(alignment: .leading, spacing: 0) {
                Text(localizer.t.detailEnvVars)
                    .font(.subheadline.weight(.semibold))
                    .padding(.bottom, 8)
                ForEach(Array(detail.Config.Env.enumerated()), id: \.offset) { _, env in
                    let parts = env.split(separator: "=", maxSplits: 1)
                    let key = String(parts.first ?? "")
                    let val = parts.count > 1 ? String(parts[1]) : ""
                    VStack(alignment: .leading, spacing: 3) {
                        Text(key)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(portainerColor)
                        Text(val)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                            .lineLimit(2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 10)
                    Divider()
                }
            }
            .padding(16)
            .glassCard()
        }
    }

    // MARK: - Compose Tab

    @ViewBuilder
    private var composeTab: some View {
        if let stack = matchedStack {
            VStack(spacing: 0) {
                HStack {
                    HStack(spacing: 8) {
                        Image(systemName: "doc.text")
                            .font(.subheadline)
                            .foregroundStyle(portainerColor)
                        Text(localizer.t.detailComposeFile)
                            .font(.subheadline.weight(.semibold))
                    }
                    Spacer()
                    Text(stack.Name)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                }
                .padding(16)

                Divider()

                TextEditor(text: $composeContent)
                    .font(.system(.caption, design: .monospaced))
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    .frame(minHeight: 300)
                    .padding(8)
                    .onChange(of: composeContent) { _, _ in
                        composeEdited = true
                    }

                if composeEdited {
                    Button {
                        Task { await saveCompose(stack) }
                    } label: {
                        HStack(spacing: 8) {
                            if isSavingCompose {
                                ProgressView().tint(.white)
                            } else {
                                Image(systemName: "square.and.arrow.down")
                                Text(localizer.t.detailComposeSave)
                                    .fontWeight(.semibold)
                            }
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(portainerColor)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .padding(16)
                    .disabled(isSavingCompose)
                }
            }
            .glassCard()
        } else if stacks.isEmpty && activeTab == .compose {
            VStack(spacing: 12) {
                Image(systemName: "doc.text")
                    .font(.largeTitle)
                    .foregroundStyle(AppTheme.textMuted)
                Text(localizer.t.detailComposeNotAvailable)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textMuted)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, 40)
        }
    }

    // MARK: - Data Fetching

    private func fetchDetail() async {
        isLoading = true
        defer { isLoading = false }
        do {
            detail = try await servicesStore.portainerClient.getContainerDetail(endpointId: endpointId, containerId: containerId)
        } catch {
            // silent
        }
    }

    private func fetchTabData(_ tab: TabType) async {
        switch tab {
        case .stats:
            do { stats = try await servicesStore.portainerClient.getContainerStats(endpointId: endpointId, containerId: containerId) } catch {}
        case .logs:
            do { logs = try await servicesStore.portainerClient.getContainerLogs(endpointId: endpointId, containerId: containerId, tail: 200) } catch {}
        case .compose:
            do {
                stacks = try await servicesStore.portainerClient.getStacks(endpointId: endpointId)
                if let stack = matchedStack {
                    let file = try await servicesStore.portainerClient.getStackFile(stackId: stack.Id)
                    stackFile = file
                    if !composeEdited { composeContent = file }
                }
            } catch {}
        default: break
        }
    }

    private func refreshAll() async {
        await fetchDetail()
        await fetchTabData(activeTab)
    }

    private func renameContainer(_ newName: String) async {
        do {
            try await servicesStore.portainerClient.renameContainer(endpointId: endpointId, containerId: containerId, newName: newName)
            HapticManager.success()
            await fetchDetail()
        } catch {
            HapticManager.error()
            actionError = error.localizedDescription
            showActionError = true
        }
    }

    private func saveCompose(_ stack: PortainerStack) async {
        isSavingCompose = true
        defer { isSavingCompose = false }
        do {
            try await servicesStore.portainerClient.updateStackFile(stackId: stack.Id, endpointId: endpointId, stackFileContent: composeContent)
            HapticManager.success()
            composeEdited = false
        } catch {
            HapticManager.error()
            actionError = error.localizedDescription
            showActionError = true
        }
    }
}
