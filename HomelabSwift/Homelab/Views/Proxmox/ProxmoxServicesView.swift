import SwiftUI

struct ProxmoxServicesView: View {
    let instanceId: UUID
    let nodeName: String

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var services: [ProxmoxService] = []
    @State private var aptPackages: [ProxmoxAptPackage] = []
    @State private var state: LoadableState<Void> = .idle
    @State private var showError: String?
    @State private var actionInProgress: String?

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchData
        ) {
            // APT updates
            if !aptPackages.isEmpty {
                aptSection
            }

            // Services
            servicesSection
        }
        .navigationTitle("\(localizer.t.proxmoxServices) - \(nodeName)")
        .navigationBarTitleDisplayMode(.inline)
        .alert(localizer.t.error, isPresented: .init(
            get: { showError != nil },
            set: { if !$0 { showError = nil } }
        )) {
            Button(localizer.t.done) { showError = nil }
        } message: {
            Text(showError ?? "")
        }
        .task { await fetchData() }
    }

    // MARK: - APT Updates

    private var aptSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(localizer.t.proxmoxAvailableUpdates) (\(aptPackages.count))")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)

                Spacer()

                Image(systemName: "arrow.down.circle.fill")
                    .font(.caption)
                    .foregroundStyle(.orange)
            }

            VStack(spacing: 0) {
                ForEach(aptPackages.prefix(15)) { pkg in
                    HStack(spacing: 8) {
                        Image(systemName: "shippingbox.fill")
                            .font(.caption2)
                            .foregroundStyle(.orange)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(pkg.package ?? localizer.t.proxmoxUnknownPackage)
                                .font(.subheadline.bold())
                                .lineLimit(1)

                            HStack(spacing: 4) {
                                if let old = pkg.old_version {
                                    Text(old)
                                        .font(.system(size: 9, design: .monospaced))
                                        .foregroundStyle(AppTheme.stopped)
                                        .lineLimit(1)
                                }
                                Image(systemName: "arrow.right")
                                    .font(.system(size: 7, weight: .bold))
                                    .foregroundStyle(AppTheme.textMuted)
                                if let ver = pkg.version {
                                    Text(ver)
                                        .font(.system(size: 9, design: .monospaced))
                                        .foregroundStyle(AppTheme.running)
                                        .lineLimit(1)
                                }
                            }
                        }

                        Spacer()

                        if let section = pkg.section {
                            Text(section)
                                .font(.system(size: 9, weight: .medium))
                                .foregroundStyle(AppTheme.textMuted)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(AppTheme.textMuted.opacity(0.08), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)

                    if pkg.id != aptPackages.prefix(15).last?.id {
                        Divider().padding(.leading, 30)
                    }
                }

                if aptPackages.count > 15 {
                    HStack {
                        Spacer()
                        Text("+ \(aptPackages.count - 15)")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                        Spacer()
                    }
                    .padding(.vertical, 8)
                }
            }
            .glassCard()
        }
    }

    // MARK: - Services

    private var servicesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(localizer.t.proxmoxSystemServices) (\(services.count))")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            if services.isEmpty {
                HStack {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "gearshape.2.fill")
                            .font(.title2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(localizer.t.proxmoxNoServices)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .padding(.vertical, 30)
                    Spacer()
                }
                .glassCard()
            } else {
                VStack(spacing: 0) {
                    ForEach(sortedServices) { svc in
                        serviceRow(svc)
                        if svc.id != sortedServices.last?.id {
                            Divider().padding(.leading, 50)
                        }
                    }
                }
                .glassCard()
            }
        }
    }

    private var sortedServices: [ProxmoxService] {
        services.sorted { a, b in
            if a.isRunning != b.isRunning { return a.isRunning && !b.isRunning }
            return a.service < b.service
        }
    }

    private func serviceRow(_ svc: ProxmoxService) -> some View {
        HStack(spacing: 10) {
            Circle()
                .fill(svc.isRunning ? AppTheme.running : AppTheme.stopped)
                .frame(width: 8, height: 8)

            VStack(alignment: .leading, spacing: 2) {
                Text(svc.name ?? svc.service)
                    .font(.subheadline.bold())
                    .lineLimit(1)
                if let desc = svc.desc, !desc.isEmpty {
                    Text(desc)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }

            Spacer()

            if actionInProgress == svc.service {
                ProgressView()
                    .controlSize(.small)
            } else {
                Text(localizedStatus(svc))
                    .font(.caption2.bold())
                    .foregroundStyle(svc.isRunning ? AppTheme.running : AppTheme.stopped)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(
                        (svc.isRunning ? AppTheme.running : AppTheme.stopped).opacity(0.1),
                        in: RoundedRectangle(cornerRadius: 6, style: .continuous)
                    )
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .contextMenu {
            if svc.isRunning {
                Button {
                    Task { await controlService(svc.service, action: "restart") }
                } label: {
                    Label(localizer.t.proxmoxRestart, systemImage: "arrow.counterclockwise")
                }
                .disabled(actionInProgress == svc.service)
                Button {
                    Task { await controlService(svc.service, action: "stop") }
                } label: {
                    Label(localizer.t.actionStop, systemImage: "stop.fill")
                }
                .disabled(actionInProgress == svc.service)
            } else {
                Button {
                    Task { await controlService(svc.service, action: "start") }
                } label: {
                    Label(localizer.t.actionStart, systemImage: "play.fill")
                }
                .disabled(actionInProgress == svc.service)
            }
            Button {
                Task { await controlService(svc.service, action: "reload") }
            } label: {
                Label(localizer.t.proxmoxReload, systemImage: "arrow.clockwise")
            }
            .disabled(actionInProgress == svc.service)
        }
    }

    // MARK: - Data

    private func fetchData() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            services = try await client.getNodeServices(node: nodeName)
            aptPackages = (try? await client.getNodeAptUpdates(node: nodeName)) ?? []
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func controlService(_ name: String, action: String) async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
        actionInProgress = name
        defer { actionInProgress = nil }
        do {
            try await client.controlService(node: nodeName, service: name, action: action)
            try? await Task.sleep(for: .seconds(1))
            await fetchData()
        } catch {
            showError = error.localizedDescription
        }
    }

    private func localizedStatus(_ service: ProxmoxService) -> String {
        if service.isRunning {
            return localizer.t.proxmoxRunning
        }
        if let state = service.state, !state.isEmpty {
            return state.capitalized
        }
        return localizer.t.proxmoxUnknown
    }
}
