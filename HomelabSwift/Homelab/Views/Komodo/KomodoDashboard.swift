import SwiftUI

struct KomodoDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var dashboard: KomodoDashboardData?

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
            await fetchDashboard(showLoading: true)
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
                resourceCard(title: localizer.t.komodoStacks, summary: dashboard?.stacks ?? .empty, icon: "square.stack.3d.up.fill", tint: AppTheme.warning)
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

    private func resourceCard(title: String, summary: KomodoResourceSummary, icon: String, tint: Color) -> some View {
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
        }
        .frame(maxWidth: .infinity, minHeight: 126, alignment: .topLeading)
        .padding(14)
        .glassCard(tint: tint.opacity(colorScheme == .dark ? 0.08 : 0.045))
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
}
