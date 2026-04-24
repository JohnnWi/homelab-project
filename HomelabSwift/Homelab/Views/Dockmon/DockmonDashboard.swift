import SwiftUI
import UIKit

struct DockmonDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var selectedHostId: String?
    @State private var state: LoadableState<Void> = .idle
    @State private var dashboard: DockmonDashboardData?
    @State private var selectedContainer: DockmonContainer?
    @State private var logsState: LoadableState<String> = .idle
    @State private var imageDraft = ""
    @State private var isRunningAction = false
    @State private var actionMessage: String?

    private let dockmonColor = ServiceType.dockmon.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .dockmon,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(showLoading: false) }
        ) {
            instancePicker
            overviewCard
            hostFilter
            containerList
        }
        .navigationTitle(ServiceType.dockmon.displayName)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await fetchDashboard(showLoading: false) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(state.isLoading || isRunningAction)
                .accessibilityLabel(localizer.t.refresh)
            }
        }
        .task(id: selectedInstanceId) {
            await fetchDashboard(showLoading: true)
        }
        .sheet(item: $selectedContainer) { container in
            containerDetailSheet(container)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .alert(
            ServiceType.dockmon.displayName,
            isPresented: Binding(
                get: { actionMessage != nil },
                set: { if !$0 { actionMessage = nil } }
            )
        ) {
            Button(localizer.t.done) { actionMessage = nil }
        } message: {
            Text(actionMessage ?? "")
        }
    }

    private var selectedContainers: [DockmonContainer] {
        guard let dashboard else { return [] }
        if let selectedHostId {
            return dashboard.containersByHost[selectedHostId] ?? []
        }
        return dashboard.allContainers
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .dockmon)
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
                            servicesStore.setPreferredInstance(id: instance.id, for: .dockmon)
                            selectedHostId = nil
                            dashboard = nil
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? dockmonColor : AppTheme.textMuted.opacity(0.4))
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
                            .glassCard(tint: instance.id == selectedInstanceId ? dockmonColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var overviewCard: some View {
        let dashboard = dashboard
        let total = dashboard?.totalContainers ?? 0
        let running = dashboard?.runningContainers ?? 0
        let updates = dashboard?.updateCount ?? 0
        let autoRestart = dashboard?.autoRestartCount ?? 0

        return VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .center, spacing: 12) {
                ServiceIconView(type: .dockmon, size: 52)
                    .padding(6)
                    .background(
                        dockmonColor.opacity(colorScheme == .dark ? 0.18 : 0.12),
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )

                VStack(alignment: .leading, spacing: 4) {
                    Text(localizer.t.dockmonContainers)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                    Text("\(running) / \(total)")
                        .font(.system(size: 32, weight: .bold))
                        .contentTransition(.numericText())
                }

                Spacer()
            }

            HStack(spacing: 10) {
                metricCell(title: localizer.t.containersRunning, value: "\(running)", tint: AppTheme.running)
                metricCell(title: localizer.t.dockmonUpdates, value: "\(updates)", tint: updates > 0 ? AppTheme.warning : dockmonColor)
                metricCell(title: localizer.t.dockmonAutoRestart, value: "\(autoRestart)", tint: AppTheme.info)
            }
        }
        .padding(18)
        .glassCard(tint: dockmonColor.opacity(colorScheme == .dark ? 0.1 : 0.055))
    }

    private var hostFilter: some View {
        let hosts = dashboard?.hosts ?? []
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(localizer.t.dockmonHosts)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                Text("\(hosts.count)")
                    .font(.caption.bold())
                    .foregroundStyle(dockmonColor)
            }

            if hosts.isEmpty {
                Text(localizer.t.dockmonNoHosts)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        hostChip(title: localizer.t.dockmonAllHosts, hostId: nil, isOnline: true)
                        ForEach(hosts) { host in
                            hostChip(title: host.name, hostId: host.id, isOnline: host.isOnline)
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    private var containerList: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.dockmonContainers)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                Text("\(selectedContainers.count)")
                    .font(.caption.bold())
                    .foregroundStyle(dockmonColor)
            }

            if selectedContainers.isEmpty {
                emptyContainers
            } else {
                ForEach(selectedContainers) { container in
                    containerCard(container)
                }
            }
        }
    }

    private func metricCell(title: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(value)
                .font(.title3.bold())
                .foregroundStyle(tint)
                .contentTransition(.numericText())
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(2)
                .minimumScaleFactor(0.82)
        }
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
        .padding(.vertical, 6)
    }

    private func hostChip(title: String, hostId: String?, isOnline: Bool) -> some View {
        let selected = selectedHostId == hostId
        return Button {
            HapticManager.light()
            withAnimation(.spring(response: 0.3, dampingFraction: 0.86)) {
                selectedHostId = hostId
            }
        } label: {
            HStack(spacing: 7) {
                Circle()
                    .fill(isOnline ? AppTheme.running : AppTheme.textMuted)
                    .frame(width: 7, height: 7)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
            }
            .foregroundStyle(selected ? dockmonColor : AppTheme.textSecondary)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                selected ? dockmonColor.opacity(0.15) : AppTheme.surface.opacity(0.82),
                in: Capsule(style: .continuous)
            )
            .overlay(
                Capsule(style: .continuous)
                    .stroke(selected ? dockmonColor.opacity(0.42) : .white.opacity(0.04), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func containerCard(_ container: DockmonContainer) -> some View {
        Button {
            HapticManager.light()
            imageDraft = container.latestImage ?? container.image
            logsState = .idle
            selectedContainer = container
        } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 10) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .fill((container.isRunning ? AppTheme.running : AppTheme.textMuted).opacity(0.12))
                            .frame(width: 42, height: 42)
                        Image(systemName: container.isRunning ? "play.circle.fill" : "pause.circle.fill")
                            .font(.title3)
                            .foregroundStyle(container.isRunning ? AppTheme.running : AppTheme.textMuted)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(container.name)
                            .font(.headline)
                            .foregroundStyle(.primary)
                            .lineLimit(1)
                        Text(container.image)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(1)
                    }

                    Spacer(minLength: 8)

                    if container.updateAvailable {
                        Image(systemName: "arrow.down.circle.fill")
                            .foregroundStyle(AppTheme.warning)
                            .accessibilityLabel(localizer.t.dockmonUpdateAvailable)
                    }
                }

                HStack(spacing: 8) {
                    pill(container.isRunning ? localizer.t.containersRunning : localizer.t.containersStopped, tint: container.isRunning ? AppTheme.running : AppTheme.textMuted)
                    if container.autoRestart {
                        pill(localizer.t.dockmonAutoRestart, tint: AppTheme.info)
                    }
                    if container.updateAvailable {
                        pill(localizer.t.dockmonUpdateAvailable, tint: AppTheme.warning)
                    }
                    Spacer(minLength: 0)
                }
            }
            .padding(16)
            .glassCard(tint: AppTheme.surface.opacity(colorScheme == .dark ? 0.24 : 0.1))
        }
        .buttonStyle(.plain)
    }

    private func pill(_ text: String, tint: Color) -> some View {
        Text(text)
            .font(.caption2.bold())
            .foregroundStyle(tint)
            .lineLimit(1)
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(tint.opacity(0.12), in: Capsule(style: .continuous))
    }

    private var emptyContainers: some View {
        VStack(spacing: 12) {
            Image(systemName: "shippingbox")
                .font(.system(size: 42))
                .foregroundStyle(AppTheme.textMuted)
                .accessibilityHidden(true)
            Text(localizer.t.dockmonNoContainers)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 42)
        .glassCard()
    }

    private func containerDetailSheet(_ container: DockmonContainer) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    containerHeader(container)
                    actionPanel(container)
                    logsPanel(container)
                }
                .padding(18)
            }
            .background(AppTheme.background)
            .navigationTitle(container.name)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.close) {
                        selectedContainer = nil
                    }
                }
            }
            .task(id: container.id) {
                await fetchLogs(for: container)
            }
        }
    }

    private func containerHeader(_ container: DockmonContainer) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(container.name)
                    .font(.title3.bold())
                    .lineLimit(1)
                Spacer()
                pill(container.isRunning ? localizer.t.containersRunning : localizer.t.containersStopped, tint: container.isRunning ? AppTheme.running : AppTheme.textMuted)
            }

            detailRow(localizer.t.dockmonCurrentImage, container.image)
            if let latest = container.latestImage, !latest.isEmpty {
                detailRow(localizer.t.dockmonLatestImage, latest)
            }
            if let hostName = hostName(for: container), !hostName.isEmpty {
                detailRow(localizer.t.dockmonHost, hostName)
            }
            if !container.ports.isEmpty {
                detailRow(localizer.t.dockhandPorts, container.ports.joined(separator: ", "))
            }
        }
        .padding(16)
        .glassCard(tint: dockmonColor.opacity(0.08))
    }

    private func actionPanel(_ container: DockmonContainer) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.actionConfirm)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)

            HStack(spacing: 10) {
                DockmonActionButton(
                    title: localizer.t.dockmonRestartContainer,
                    icon: "arrow.clockwise",
                    tint: dockmonColor,
                    isLoading: isRunningAction
                ) {
                    Task { await runRestart(container) }
                }

                DockmonActionButton(
                    title: localizer.t.dockmonUpdateContainer,
                    icon: "arrow.down.circle.fill",
                    tint: AppTheme.warning,
                    isLoading: isRunningAction
                ) {
                    Task { await runUpdate(container) }
                }
            }

            TextField(localizer.t.dockmonImagePlaceholder, text: $imageDraft)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.subheadline)
                .padding(12)
                .background(AppTheme.surface.opacity(0.76), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .padding(16)
        .glassCard()
    }

    private func logsPanel(_ container: DockmonContainer) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.detailLogs)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                Button {
                    Task { await fetchLogs(for: container) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(logsState.isLoading)
            }

            switch logsState {
            case .idle, .loading:
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 120)
            case .error(let error):
                Text(error.localizedDescription)
                    .font(.caption)
                    .foregroundStyle(AppTheme.danger)
            case .offline:
                Text(String(format: localizer.t.offlineUnreachable, ServiceType.dockmon.displayName))
                    .font(.caption)
                    .foregroundStyle(AppTheme.danger)
            case .loaded(let logs):
                Text(logs.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? localizer.t.detailNoLogs : logs)
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(AppTheme.textSecondary)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .padding(16)
        .glassCard()
    }

    private func detailRow(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
            Text(value.isEmpty ? "-" : value)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
                .textSelection(.enabled)
        }
    }

    private func hostName(for container: DockmonContainer) -> String? {
        guard let hostId = container.hostId else { return nil }
        return dashboard?.hosts.first(where: { $0.id == hostId })?.name ?? hostId
    }

    private func fetchDashboard(showLoading: Bool) async {
        do {
            guard let client = await servicesStore.dockmonClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }
            if showLoading || dashboard == nil {
                state = .loading
            }
            let data = try await client.getDashboard()
            withAnimation(.spring(response: 0.42, dampingFraction: 0.86)) {
                dashboard = data
                if let selectedHostId, !data.hosts.contains(where: { $0.id == selectedHostId }) {
                    self.selectedHostId = nil
                }
            }
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func fetchLogs(for container: DockmonContainer) async {
        do {
            guard let client = await servicesStore.dockmonClient(instanceId: selectedInstanceId) else {
                logsState = .error(.notConfigured)
                return
            }
            logsState = .loading
            let logs = try await client.getContainerLogs(id: container.id)
            logsState = .loaded(logs)
        } catch let apiError as APIError {
            logsState = .error(apiError)
        } catch {
            logsState = .error(.custom(error.localizedDescription))
        }
    }

    private func runRestart(_ container: DockmonContainer) async {
        await runAction {
            guard let client = await servicesStore.dockmonClient(instanceId: selectedInstanceId) else {
                throw APIError.notConfigured
            }
            return try await client.restartContainer(id: container.id)
        }
    }

    private func runUpdate(_ container: DockmonContainer) async {
        await runAction {
            guard let client = await servicesStore.dockmonClient(instanceId: selectedInstanceId) else {
                throw APIError.notConfigured
            }
            return try await client.updateContainer(id: container.id, image: imageDraft)
        }
    }

    private func runAction(_ operation: () async throws -> DockmonActionResponse) async {
        do {
            isRunningAction = true
            let response = try await operation()
            HapticManager.success()
            actionMessage = response.message ?? localizer.t.dockmonActionSuccess
            await fetchDashboard(showLoading: false)
            if let selectedContainer {
                await fetchLogs(for: selectedContainer)
            }
        } catch {
            HapticManager.error()
            actionMessage = (error as? APIError)?.localizedDescription ?? error.localizedDescription
        }
        isRunningAction = false
    }
}

private struct DockmonActionButton: View {
    let title: String
    let icon: String
    let tint: Color
    let isLoading: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                } else {
                    Image(systemName: icon)
                        .font(.subheadline.weight(.semibold))
                }
                Text(title)
                    .font(.caption.weight(.semibold))
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .padding(.horizontal, 10)
            .foregroundStyle(tint)
            .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .disabled(isLoading)
    }
}
