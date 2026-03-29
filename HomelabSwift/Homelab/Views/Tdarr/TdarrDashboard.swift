import SwiftUI

struct TdarrDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var nodes: [TdarrNode] = []
    @State private var stats: TdarrStats?

    private let serviceColor = ServiceType.tdarr.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .tdarr,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await load(force: true) }
        ) {
            heroCard

            if let stats {
                statsGrid(stats)
                scoresCard(stats)
            }

            if !nodes.isEmpty {
                nodesCard
            }
        }
        .navigationTitle("Tdarr")
        .task(id: selectedInstanceId) {
            await load(force: true)
        }
    }

    private var heroCard: some View {
        GlassCard(tint: serviceColor.opacity(colorScheme == .light ? 0.14 : 0.10)) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    ServiceIconView(type: .tdarr, size: 34)
                        .frame(width: 56, height: 56)
                        .background(serviceColor.opacity(0.13), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Tdarr")
                            .font(.headline.bold())
                            .lineLimit(1)
                        Text("\(nodes.count) node\(nodes.count == 1 ? "" : "s")")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }

                    Spacer()

                    if let stats, stats.totalFileCount > 0 {
                        Text("\(Formatters.formatNumber(stats.totalFileCount)) files")
                            .font(.caption.bold())
                            .foregroundStyle(serviceColor)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(serviceColor.opacity(0.14), in: Capsule())
                    }
                }

                if let stats {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text("Size diff: \(String(format: "%.1f", stats.sizeDiffGB)) GB")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }
            }
            .padding(14)
        }
    }

    private func statsGrid(_ stats: TdarrStats) -> some View {
        LazyVGrid(columns: twoColumnGrid, spacing: 10) {
            GlassStatCard(
                title: "Transcodes",
                value: Formatters.formatNumber(stats.totalTranscodeCount),
                icon: "waveform.path",
                iconColor: serviceColor
            )
            GlassStatCard(
                title: "Health Checks",
                value: Formatters.formatNumber(stats.totalHealthCheckCount),
                icon: "heart.text.square.fill",
                iconColor: Color(hex: "#22D3EE")
            )
        }
    }

    private func scoresCard(_ stats: TdarrStats) -> some View {
        GlassCard(tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Scores")
                    .font(.subheadline.weight(.semibold))

                HStack(spacing: 16) {
                    if let score = stats.tdarrScore {
                        VStack(spacing: 4) {
                            Text(score)
                                .font(.title2.bold())
                                .foregroundStyle(serviceColor)
                            Text("Transcode")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        .frame(maxWidth: .infinity)
                    }

                    if let score = stats.healthCheckScore {
                        VStack(spacing: 4) {
                            Text(score)
                                .font(.title2.bold())
                                .foregroundStyle(Color(hex: "#22D3EE"))
                            Text("Health Check")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            .padding(14)
        }
    }

    private var nodesCard: some View {
        GlassCard(tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Nodes")
                    .font(.subheadline.weight(.semibold))

                ForEach(nodes) { node in
                    HStack(spacing: 10) {
                        Circle()
                            .fill(node.online ? AppTheme.running : AppTheme.textMuted)
                            .frame(width: 8, height: 8)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(node.name)
                                .font(.caption.weight(.semibold))
                                .lineLimit(1)
                            Text("\(node.workers) worker\(node.workers == 1 ? "" : "s")")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        Spacer()

                        Text(node.online ? "Online" : "Paused")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(node.online ? AppTheme.running : AppTheme.textMuted)
                    }
                }
            }
            .padding(14)
        }
    }

    private func load(force: Bool) async {
        if state.isLoading { return }
        if case .loaded = state, !force { return }

        state = .loading
        do {
            guard let client = await servicesStore.tdarrClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let nodesTask = client.getNodes()
            async let statsTask = client.getStats()

            nodes = try await nodesTask
            stats = try await statsTask

            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}
