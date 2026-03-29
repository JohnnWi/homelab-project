import SwiftUI

struct SABnzbdDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var queue: SABnzbdQueueInfo?
    @State private var history: [SABnzbdHistoryEntry] = []

    private let serviceColor = ServiceType.sabnzbd.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .sabnzbd,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await load(force: true) }
        ) {
            if let q = queue {
                heroCard(q)
                statsGrid(q)
            }

            if !history.isEmpty {
                historyCard
            }
        }
        .navigationTitle("SABnzbd")
        .task(id: selectedInstanceId) {
            await load(force: true)
        }
    }

    private func heroCard(_ q: SABnzbdQueueInfo) -> some View {
        GlassCard(tint: serviceColor.opacity(colorScheme == .light ? 0.14 : 0.10)) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    ServiceIconView(type: .sabnzbd, size: 34)
                        .frame(width: 56, height: 56)
                        .background(serviceColor.opacity(0.13), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    VStack(alignment: .leading, spacing: 4) {
                        Text("SABnzbd")
                            .font(.headline.bold())
                            .lineLimit(1)
                        Text(q.status)
                            .font(.caption)
                            .foregroundStyle(q.status.lowercased() == "downloading" ? serviceColor : AppTheme.textMuted)
                    }

                    Spacer()

                    Text("\(Formatters.formatBytes(q.speedBytes))/s")
                        .font(.caption.bold())
                        .foregroundStyle(serviceColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(serviceColor.opacity(0.14), in: Capsule())
                }

                HStack(spacing: 16) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.down.doc.fill")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text("\(q.totalSlots) in queue")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                    }

                    HStack(spacing: 6) {
                        Image(systemName: "clock")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(q.timeLeft)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }
            }
            .padding(14)
        }
    }

    private func statsGrid(_ q: SABnzbdQueueInfo) -> some View {
        LazyVGrid(columns: twoColumnGrid, spacing: 10) {
            GlassStatCard(
                title: "Remaining",
                value: q.sizeLeft,
                icon: "arrow.down.circle.fill",
                iconColor: serviceColor
            )
            GlassStatCard(
                title: "Disk Free",
                value: q.diskSpaceFree ?? "N/A",
                icon: "internaldrive.fill",
                iconColor: Color(hex: "#22D3EE")
            )
        }
    }

    private var historyCard: some View {
        GlassCard(tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Recent History")
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    Text("\(history.count)")
                        .font(.caption.bold())
                        .foregroundStyle(serviceColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(serviceColor.opacity(0.14), in: Capsule())
                }

                ForEach(history.prefix(10), id: \.name) { entry in
                    HStack(spacing: 10) {
                        Image(systemName: entry.status.lowercased() == "completed" ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .font(.caption)
                            .foregroundStyle(entry.status.lowercased() == "completed" ? AppTheme.running : Color(hex: "#EF4444"))

                        VStack(alignment: .leading, spacing: 2) {
                            Text(entry.name)
                                .font(.caption.weight(.semibold))
                                .lineLimit(1)
                            HStack(spacing: 6) {
                                Text(entry.size)
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                                if let cat = entry.category, !cat.isEmpty, cat != "*" {
                                    Text(cat)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                }
                            }
                        }
                        Spacer()
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
            guard let client = await servicesStore.sabnzbdClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let queueTask = client.getQueue()
            async let historyTask = client.getHistory(limit: 10)

            queue = try await queueTask
            history = try await historyTask

            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}
