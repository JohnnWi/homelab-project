import SwiftUI

struct ProxmoxDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var nodes: [ProxmoxNodeInfo] = []
    @State private var vms: [ProxmoxVM] = []
    @State private var storages: [ProxmoxStorage] = []

    private let serviceColor = ServiceType.proxmox.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await load(force: true) }
        ) {
            heroCard

            if !nodes.isEmpty {
                nodesCard
            }

            statsGrid

            if !vms.isEmpty {
                vmsCard
            }

            if !storages.isEmpty {
                storageCard
            }
        }
        .navigationTitle("Proxmox VE")
        .task(id: selectedInstanceId) {
            await load(force: true)
        }
    }

    private var heroCard: some View {
        GlassCard(tint: serviceColor.opacity(colorScheme == .light ? 0.14 : 0.10)) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    ServiceIconView(type: .proxmox, size: 34)
                        .frame(width: 56, height: 56)
                        .background(serviceColor.opacity(0.13), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    VStack(alignment: .leading, spacing: 4) {
                        Text("Proxmox VE")
                            .font(.headline.bold())
                            .lineLimit(1)
                        Text("\(nodes.count) node\(nodes.count == 1 ? "" : "s")")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }

                    Spacer()
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

                ForEach(nodes, id: \.node) { node in
                    HStack(spacing: 10) {
                        Circle()
                            .fill(node.status == "online" ? AppTheme.running : Color(hex: "#EF4444"))
                            .frame(width: 8, height: 8)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(node.node)
                                .font(.caption.weight(.semibold))
                                .lineLimit(1)
                            Text("CPU: \(String(format: "%.1f%%", node.cpuUsage * 100)) | RAM: \(Formatters.formatBytes(Double(node.memoryUsed))) / \(Formatters.formatBytes(Double(node.memoryTotal)))")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                                .lineLimit(1)
                        }

                        Spacer()

                        Text(formatUptime(node.uptime))
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }
            }
            .padding(14)
        }
    }

    private var statsGrid: some View {
        let running = vms.filter { $0.status == "running" }.count
        let qemuCount = vms.filter { $0.type == "qemu" }.count
        let lxcCount = vms.filter { $0.type == "lxc" }.count

        return LazyVGrid(columns: twoColumnGrid, spacing: 10) {
            GlassStatCard(
                title: "Running",
                value: "\(running) / \(vms.count)",
                icon: "play.circle.fill",
                iconColor: AppTheme.running
            )
            GlassStatCard(
                title: "VMs / LXCs",
                value: "\(qemuCount) / \(lxcCount)",
                icon: "cpu.fill",
                iconColor: serviceColor
            )
        }
    }

    private var vmsCard: some View {
        GlassCard(tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Guests")
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    Text("\(vms.count)")
                        .font(.caption.bold())
                        .foregroundStyle(serviceColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(serviceColor.opacity(0.14), in: Capsule())
                }

                ForEach(vms.sorted(by: { $0.vmid < $1.vmid }).prefix(20), id: \.vmid) { vm in
                    HStack(spacing: 10) {
                        Circle()
                            .fill(vm.status == "running" ? AppTheme.running : AppTheme.textMuted)
                            .frame(width: 6, height: 6)

                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Text(vm.name)
                                    .font(.caption.weight(.semibold))
                                    .lineLimit(1)
                                Text("(\(vm.vmid))")
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            Text("\(vm.type.uppercased()) on \(vm.node)")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        Spacer()

                        Text(vm.status)
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(vm.status == "running" ? AppTheme.running : AppTheme.textMuted)
                    }
                }
            }
            .padding(14)
        }
    }

    private var storageCard: some View {
        GlassCard(tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)) {
            VStack(alignment: .leading, spacing: 12) {
                Text("Storage")
                    .font(.subheadline.weight(.semibold))

                ForEach(storages, id: \.storage) { storage in
                    let usagePercent = storage.total > 0 ? Double(storage.used) / Double(storage.total) : 0

                    VStack(spacing: 6) {
                        HStack {
                            Text(storage.storage)
                                .font(.caption.weight(.semibold))
                                .lineLimit(1)
                            Spacer()
                            Text("\(Formatters.formatBytes(Double(storage.used))) / \(Formatters.formatBytes(Double(storage.total)))")
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

    private func formatUptime(_ seconds: Int) -> String {
        let days = seconds / 86400
        let hours = (seconds % 86400) / 3600
        if days > 0 {
            return "\(days)d \(hours)h"
        }
        let minutes = (seconds % 3600) / 60
        return "\(hours)h \(minutes)m"
    }

    private func load(force: Bool) async {
        if state.isLoading { return }
        if case .loaded = state, !force { return }

        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let nodesTask = client.getNodes()
            async let vmsTask = client.getVMs()
            async let storagesTask = client.getStorages()

            nodes = try await nodesTask
            vms = try await vmsTask
            storages = try await storagesTask

            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}
