import SwiftUI

struct KomodoDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var dashboard: KomodoDashboardData?
    @State private var isShowingStacks = false
    @State private var stacksState: LoadableState<[KomodoStackItem]> = .idle
    @State private var stackDetailState: LoadableState<KomodoStackDetail> = .idle
    @State private var isRunningStackAction = false

    private let komodoColor = ServiceType.komodo.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .komodo,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(showLoading: false) }
        ) {
            instancePicker
            overviewCard
            resourceGrid
            containerStateCard
        }
        .navigationTitle(ServiceType.komodo.displayName)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await fetchDashboard(showLoading: false) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(state.isLoading)
                .accessibilityLabel(localizer.t.refresh)
            }
        }
        .task(id: selectedInstanceId) {
            dashboard = nil
            stacksState = .idle
            stackDetailState = .idle
            await fetchDashboard(showLoading: true)
        }
        .sheet(isPresented: $isShowingStacks) {
            KomodoStacksSheet(
                stacksState: stacksState,
                detailState: stackDetailState,
                isRunningAction: isRunningStackAction,
                onRefreshList: { Task { await loadStacks() } },
                onBackToList: { stackDetailState = .idle },
                onSelectStack: { stack in Task { await loadStackDetail(stack) } },
                onAction: { stackId, action in Task { await runStackAction(stackId: stackId, action: action) } }
            )
            .presentationDetents([.medium, .large])
            .presentationDragIndicator(.visible)
        }
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .komodo)
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
                            servicesStore.setPreferredInstance(id: instance.id, for: .komodo)
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? komodoColor : AppTheme.textMuted.opacity(0.4))
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
                            .glassCard(tint: instance.id == selectedInstanceId ? komodoColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var overviewCard: some View {
        let data = dashboard
        let running = data?.containers.running ?? 0
        let total = data?.containers.total ?? 0

        return VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .center, spacing: 12) {
                ServiceIconView(type: .komodo, size: 52)
                    .padding(6)
                    .background(
                        komodoColor.opacity(colorScheme == .dark ? 0.18 : 0.12),
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )

                VStack(alignment: .leading, spacing: 4) {
                    Text(localizer.t.komodoContainers)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                    Text("\(running) / \(total)")
                        .font(.system(size: 32, weight: .bold))
                        .contentTransition(.numericText())
                    if let version = data?.version, !version.isEmpty {
                        Text("\(localizer.t.komodoVersion) \(version)")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(1)
                            .minimumScaleFactor(0.78)
                    }
                }

                Spacer()
            }

            HStack(spacing: 10) {
                metricCell(title: localizer.t.containersRunning, value: "\(running)", tint: AppTheme.running)
                metricCell(title: localizer.t.komodoDeployments, value: "\(data?.deployments.total ?? 0)", tint: komodoColor)
                metricCell(title: localizer.t.komodoServers, value: "\(data?.servers.total ?? 0)", tint: AppTheme.info)
            }
        }
        .padding(18)
        .glassCard(tint: komodoColor.opacity(colorScheme == .dark ? 0.1 : 0.055))
    }

    private var resourceGrid: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(localizer.t.komodoResources, systemImage: "square.grid.2x2.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                resourceCard(title: localizer.t.komodoServers, summary: dashboard?.servers ?? .empty, icon: "server.rack", tint: AppTheme.info)
                resourceCard(title: localizer.t.komodoDeployments, summary: dashboard?.deployments ?? .empty, icon: "shippingbox.fill", tint: komodoColor)
                resourceCard(title: localizer.t.komodoStacks, summary: dashboard?.stacks ?? .empty, icon: "square.stack.3d.up.fill", tint: AppTheme.warning) {
                    isShowingStacks = true
                    Task { await loadStacks() }
                }
                resourceCard(title: localizer.t.komodoContainers, summary: resourceSummary(from: dashboard?.containers ?? .empty), icon: "cube.box.fill", tint: AppTheme.running)
            }
        }
    }

    private var containerStateCard: some View {
        let containers = dashboard?.containers ?? .empty
        let total = max(containers.total, 1)
        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label(localizer.t.komodoContainerStates, systemImage: "chart.bar.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                Text("\(containers.total)")
                    .font(.caption.bold())
                    .foregroundStyle(komodoColor)
            }

            VStack(spacing: 8) {
                stateRow(title: localizer.t.containersRunning, value: containers.running, total: total, tint: AppTheme.running)
                stateRow(title: localizer.t.containersStopped, value: containers.stopped + containers.exited, total: total, tint: AppTheme.textMuted)
                stateRow(title: localizer.t.komodoPaused, value: containers.paused, total: total, tint: AppTheme.warning)
                stateRow(title: localizer.t.komodoUnhealthy, value: containers.unhealthy + containers.restarting, total: total, tint: AppTheme.danger)
                if containers.unknown > 0 {
                    stateRow(title: localizer.t.komodoUnknown, value: containers.unknown, total: total, tint: AppTheme.textSecondary)
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    private func resourceCard(title: String, summary: KomodoResourceSummary, icon: String, tint: Color, action: (() -> Void)? = nil) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: icon)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(tint)
                    .frame(width: 30, height: 30)
                    .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                Spacer()
                Text("\(summary.total)")
                    .font(.title3.bold())
                    .foregroundStyle(tint)
                    .contentTransition(.numericText())
            }

            Text(title)
                .font(.subheadline.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.78)

            HStack(spacing: 8) {
                miniStat(title: localizer.t.komodoHealthy, value: summary.healthy, tint: AppTheme.running)
                miniStat(title: localizer.t.komodoUnhealthy, value: summary.unhealthy, tint: AppTheme.danger)
            }

            if action != nil {
                Label(localizer.t.komodoOpenStacks, systemImage: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(tint)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 126, alignment: .topLeading)
        .padding(14)
        .glassCard(tint: tint.opacity(colorScheme == .dark ? 0.08 : 0.045))
        .onTapGesture {
            action?()
        }
    }

    private func metricCell(title: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(value)
                .font(.title3.bold())
                .foregroundStyle(tint)
                .contentTransition(.numericText())
                .lineLimit(1)
                .minimumScaleFactor(0.78)
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(2)
                .minimumScaleFactor(0.78)
        }
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
        .padding(.vertical, 6)
    }

    private func miniStat(title: String, value: Int, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("\(value)")
                .font(.caption.bold())
                .foregroundStyle(tint)
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)
                .minimumScaleFactor(0.72)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func stateRow(title: String, value: Int, total: Int, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.78)
                Spacer()
                Text("\(value)")
                    .font(.caption.bold())
                    .foregroundStyle(tint)
            }

            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule(style: .continuous)
                        .fill(Color.primary.opacity(colorScheme == .dark ? 0.12 : 0.08))
                    Capsule(style: .continuous)
                        .fill(tint)
                        .frame(width: max(4, proxy.size.width * CGFloat(value) / CGFloat(total)))
                }
            }
            .frame(height: 7)
        }
    }

    private func resourceSummary(from containers: KomodoContainerSummary) -> KomodoResourceSummary {
        KomodoResourceSummary(
            total: containers.total,
            running: containers.running,
            stopped: containers.stopped + containers.exited,
            healthy: containers.running,
            unhealthy: containers.unhealthy,
            unknown: containers.unknown
        )
    }

    private func fetchDashboard(showLoading: Bool) async {
        guard let client = await servicesStore.komodoClient(instanceId: selectedInstanceId) else {
            state = .error(.notConfigured)
            return
        }

        if showLoading { state = .loading }
        do {
            dashboard = try await client.getDashboard()
            servicesStore.markInstanceReachable(selectedInstanceId)
            state = .loaded(())
        } catch {
            state = .error(APIError.custom(error.localizedDescription))
        }
    }

    private func loadStacks() async {
        guard let client = await servicesStore.komodoClient(instanceId: selectedInstanceId) else {
            stacksState = .error(.notConfigured)
            return
        }

        stacksState = .loading
        do {
            stacksState = .loaded(try await client.getStacks())
        } catch {
            stacksState = .error(APIError.custom(error.localizedDescription))
        }
    }

    private func loadStackDetail(_ stack: KomodoStackItem) async {
        await loadStackDetail(stack.id, fallback: stack)
    }

    private func loadStackDetail(_ stackId: String, fallback: KomodoStackItem? = nil) async {
        guard let client = await servicesStore.komodoClient(instanceId: selectedInstanceId) else {
            stackDetailState = .error(.notConfigured)
            return
        }

        stackDetailState = .loading
        do {
            let detail = try await client.getStackDetail(stackId: stackId)
            stackDetailState = .loaded(detail.withFallback(fallback))
        } catch {
            stackDetailState = .error(APIError.custom(error.localizedDescription))
        }
    }

    private func runStackAction(stackId: String, action: KomodoStackAction) async {
        guard let client = await servicesStore.komodoClient(instanceId: selectedInstanceId) else {
            stackDetailState = .error(.notConfigured)
            return
        }

        isRunningStackAction = true
        do {
            try await client.executeStackAction(stackId: stackId, action: action)
            let fallback = stackDetailState.loadedValue?.stack.id == stackId ? stackDetailState.loadedValue?.stack : nil
            await loadStackDetail(stackId, fallback: fallback)
            await loadStacks()
            await fetchDashboard(showLoading: false)
        } catch {
            stackDetailState = .error(APIError.custom(error.localizedDescription))
        }
        isRunningStackAction = false
    }
}

private struct KomodoStacksSheet: View {
    let stacksState: LoadableState<[KomodoStackItem]>
    let detailState: LoadableState<KomodoStackDetail>
    let isRunningAction: Bool
    let onRefreshList: () -> Void
    let onBackToList: () -> Void
    let onSelectStack: (KomodoStackItem) -> Void
    let onAction: (String, KomodoStackAction) -> Void

    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                switch detailState {
                case .loaded(let detail):
                    stackDetail(detail)
                case .loading:
                    loadingView
                case .error(let error):
                    errorView(error)
                case .idle, .offline:
                    stackList
                }
            }
            .padding(18)
            .navigationTitle(localizer.t.komodoStackManagement)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        if case .loaded = detailState {
                            onBackToList()
                        } else {
                            dismiss()
                        }
                    } label: {
                        Image(systemName: "chevron.left")
                    }
                    .accessibilityLabel(localizer.t.back)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: onRefreshList) {
                        Image(systemName: "arrow.clockwise")
                    }
                    .accessibilityLabel(localizer.t.refresh)
                }
            }
        }
    }

    private var stackList: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(localizer.t.komodoStackManagementSubtitle)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(2)
                .minimumScaleFactor(0.82)

            switch stacksState {
            case .loaded(let stacks):
                if stacks.isEmpty {
                    ContentUnavailableView(localizer.t.komodoNoStacks, systemImage: "square.stack.3d.up.slash")
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(stacks) { stack in
                                stackRow(stack)
                            }
                        }
                        .padding(.bottom, 24)
                    }
                }
            case .loading, .idle:
                loadingView
            case .error(let error):
                errorView(error)
            case .offline:
                errorView(.custom(String(format: localizer.t.offlineUnreachable, ServiceType.komodo.displayName)))
            }
        }
    }

    private func stackDetail(_ detail: KomodoStackDetail) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 12) {
                    Image(systemName: "square.stack.3d.up.fill")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(statusColor(detail.stack.status))
                        .frame(width: 40, height: 40)
                        .background(statusColor(detail.stack.status).opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                    VStack(alignment: .leading, spacing: 3) {
                        Text(detail.stack.name)
                            .font(.headline)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                        Text([detail.stack.server, detail.stack.project].compactMap { $0 }.joined(separator: " · ").ifEmpty(detail.stack.id))
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(1)
                    }
                    Spacer()
                    statusPill(detail.stack.status)
                }
                .padding(14)
                .glassCard(tint: statusColor(detail.stack.status).opacity(0.08))

                actionGrid(stackId: detail.stack.id)

                VStack(alignment: .leading, spacing: 10) {
                    Text(localizer.t.komodoStackServices)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    if detail.services.isEmpty {
                        Text(localizer.t.komodoNoStackServices)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                            .frame(maxWidth: .infinity, minHeight: 88)
                            .glassCard()
                    } else {
                        ForEach(detail.services) { service in
                            serviceRow(service)
                        }
                    }
                }
            }
            .padding(.bottom, 28)
        }
    }

    private func actionGrid(stackId: String) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
            actionButton(localizer.t.komodoDeploy, icon: "arrow.up.circle.fill", tint: ServiceType.komodo.colors.primary) {
                onAction(stackId, .deploy)
            }
            actionButton(localizer.t.komodoStart, icon: "play.fill", tint: AppTheme.running) {
                onAction(stackId, .start)
            }
            actionButton(localizer.t.komodoStop, icon: "stop.fill", tint: AppTheme.danger) {
                onAction(stackId, .stop)
            }
            actionButton(localizer.t.komodoRestart, icon: "arrow.clockwise", tint: AppTheme.warning) {
                onAction(stackId, .restart)
            }
        }
    }

    private func actionButton(_ title: String, icon: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 42)
                .lineLimit(1)
                .minimumScaleFactor(0.78)
        }
        .buttonStyle(.bordered)
        .tint(tint)
        .disabled(isRunningAction)
    }

    private func stackRow(_ stack: KomodoStackItem) -> some View {
        Button {
            onSelectStack(stack)
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "square.stack.3d.up.fill")
                    .foregroundStyle(statusColor(stack.status))
                    .frame(width: 34, height: 34)
                    .background(statusColor(stack.status).opacity(0.14), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 3) {
                    Text(stack.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.primary)
                        .lineLimit(1)
                    Text([stack.server, stack.project].compactMap { $0 }.joined(separator: " · ").ifEmpty(stack.id))
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
                Spacer()
                statusPill(stack.status)
            }
            .padding(13)
            .glassCard(tint: statusColor(stack.status).opacity(0.06))
        }
        .buttonStyle(.plain)
    }

    private func serviceRow(_ service: KomodoStackService) -> some View {
        HStack(spacing: 10) {
            Circle()
                .fill(statusColor(service.status))
                .frame(width: 9, height: 9)
            VStack(alignment: .leading, spacing: 2) {
                Text(service.name)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(service.image ?? service.containerName ?? "-")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(1)
                    .minimumScaleFactor(0.76)
            }
            Spacer()
            if service.updateAvailable {
                Text(localizer.t.komodoUpdateAvailable)
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(AppTheme.warning)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(AppTheme.warning.opacity(0.14), in: Capsule())
            } else {
                statusPill(service.status)
            }
        }
        .padding(12)
        .glassCard()
    }

    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(ServiceType.komodo.colors.primary)
            Text(localizer.t.loading)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity, minHeight: 240)
    }

    private func errorView(_ error: APIError) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.title2)
                .foregroundStyle(AppTheme.danger)
            Text(error.localizedDescription)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
            Button(localizer.t.retry, action: onRefreshList)
                .buttonStyle(.borderedProminent)
        }
        .frame(maxWidth: .infinity, minHeight: 240)
    }

    private func statusPill(_ status: String) -> some View {
        Text(status.capitalized)
            .font(.caption2.weight(.bold))
            .lineLimit(1)
            .minimumScaleFactor(0.72)
            .foregroundStyle(statusColor(status))
            .padding(.horizontal, 9)
            .padding(.vertical, 5)
            .background(statusColor(status).opacity(0.14), in: Capsule())
    }

    private func statusColor(_ status: String) -> Color {
        let value = status.lowercased()
        if value.contains("stopped") || value.contains("down") || value.contains("dead") || value.contains("unhealthy") { return AppTheme.danger }
        if value.contains("running") || value.contains("healthy") { return AppTheme.running }
        if value.contains("paused") || value.contains("restarting") || value.contains("deploying") || value.contains("created") { return AppTheme.warning }
        return AppTheme.textSecondary
    }
}

private extension KomodoStackDetail {
    func withFallback(_ fallback: KomodoStackItem?) -> KomodoStackDetail {
        guard let fallback else { return self }
        let mergedStack = KomodoStackItem(
            id: stack.id,
            name: stack.name.isEmpty || stack.name == stack.id ? fallback.name : stack.name,
            status: stack.status.isUnknownStatus ? fallback.status : stack.status,
            server: stack.server ?? fallback.server,
            project: stack.project ?? fallback.project,
            updateAvailable: stack.updateAvailable || fallback.updateAvailable
        )
        return KomodoStackDetail(stack: mergedStack, services: services)
    }
}

private extension LoadableState<KomodoStackDetail> {
    var loadedValue: KomodoStackDetail? {
        if case .loaded(let value) = self { return value }
        return nil
    }
}

private extension String {
    func ifEmpty(_ fallback: String) -> String {
        isEmpty ? fallback : self
    }

    var isUnknownStatus: Bool {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || caseInsensitiveCompare("Unknown") == .orderedSame
    }
}
