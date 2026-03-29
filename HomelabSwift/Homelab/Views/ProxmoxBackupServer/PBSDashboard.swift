import SwiftUI

struct PBSDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var datastores: [PBSDatastore] = []
    @State private var usage: [PBSDatastoreUsage] = []
    @State private var tasks: [PBSTask] = []

    private let serviceColor = ServiceType.proxmoxBackupServer.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmoxBackupServer,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await load(force: true) }
        ) {
            heroCard

            if !usage.isEmpty {
                usageCard
            }

            statsGrid

            if !tasks.isEmpty {
                tasksCard
            }
        }
        .navigationTitle("Proxmox Backup Server")
        .task(id: selectedInstanceId) {
            await load(force: true)
        }
    }

    private var heroCard: some View {
        GlassCard(tint: serviceColor.opacity(colorScheme == .light ? 0.14 : 0.10)) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    ServiceIconView(type: .proxmoxBackupServer, size: 34)
                        .frame(width: 56, height: 56)
                        .background(serviceColor.opacity(0.13), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Proxmox Backup Server")
                            .font(.headline.bold())
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                        Text("\(datastores.count) datastore\(datastores.count == 1 ? "" : "s")")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }

                    Spacer()
                }
            }
            .padding(14)
        }
    }

    private var usageCard: some View {
        GlassCard(tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Datastore Usage")
                    .font(.subheadline.weight(.semibold))

                ForEach(usage, id: \.store) { ds in
                    let usagePercent = ds.total > 0 ? Double(ds.used) / Double(ds.total) : 0

                    VStack(spacing: 6) {
                        HStack {
                            Text(ds.store)
                                .font(.caption.weight(.semibold))
                                .lineLimit(1)
                            Spacer()
                            Text("\(Formatters.formatBytes(Double(ds.used))) / \(Formatters.formatBytes(Double(ds.total)))")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule(style: .continuous)
                                    .fill(AppTheme.surface.opacity(0.5))
                                Capsule(style: .continuous)
                                    .fill(usagePercent > 0.9 ? Color(hex: "#EF4444").gradient : serviceColor.gradient)
                                    .frame(width: geo.size.width * CGFloat(min(usagePercent, 1.0)))
                            }
                        }
                        .frame(height: 6)
                    }
                }
            }
            .padding(14)
        }
    }

    private var statsGrid: some View {
        let okTasks = tasks.filter { $0.status == "OK" || $0.status?.lowercased() == "ok" }.count
        return LazyVGrid(columns: twoColumnGrid, spacing: 10) {
            GlassStatCard(
                title: "Datastores",
                value: "\(datastores.count)",
                icon: "externaldrive.fill",
                iconColor: serviceColor
            )
            GlassStatCard(
                title: "Recent Tasks",
                value: "\(okTasks) / \(tasks.count)",
                icon: "checkmark.circle.fill",
                iconColor: AppTheme.running
            )
        }
    }

    private var tasksCard: some View {
        GlassCard(tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Recent Tasks")
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    Text("\(tasks.count)")
                        .font(.caption.bold())
                        .foregroundStyle(serviceColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(serviceColor.opacity(0.14), in: Capsule())
                }

                ForEach(tasks.prefix(10), id: \.upid) { task in
                    HStack(spacing: 10) {
                        let isOk = task.status == "OK" || task.status?.lowercased() == "ok"
                        Image(systemName: isOk ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .font(.caption)
                            .foregroundStyle(isOk ? AppTheme.running : Color(hex: "#EF4444"))

                        VStack(alignment: .leading, spacing: 2) {
                            Text(task.worker_type)
                                .font(.caption.weight(.semibold))
                                .lineLimit(1)
                            Text(task.status ?? "unknown")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
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
            guard let client = await servicesStore.pbsClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let datastoresTask = client.getDatastores()
            async let usageTask = client.getDatastoreUsage()
            async let tasksTask = client.getRecentTasks(limit: 10)

            datastores = try await datastoresTask
            usage = try await usageTask
            tasks = try await tasksTask

            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}
