import SwiftUI
import Darwin

struct HomeView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer

    @State private var showLogin: ServiceType? = nil
    @State private var showingServiceOrder = false

    // Summary data (integrated into cards), keyed by instance UUID
    @State private var summaryData: [UUID: ServiceSummaryInfo] = [:]
    @State private var summaryLoading = false
    @State private var summaryRefreshID = UUID()
    @State private var isViewVisible = false

    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]
    private let tailscaleIconURL = URL(string: "https://cdn.jsdelivr.net/gh/selfhst/icons/png/tailscale.png")

    private var hiddenServiceKeys: Set<String> {
        settingsStore.hiddenServices
    }

    private var visibleTypes: [ServiceType] {
        settingsStore.serviceOrder.filter { ServiceType.homeServices.contains($0) && !hiddenServiceKeys.contains($0.rawValue) }
    }

    private var hasServices: Bool {
        visibleTypes.contains { servicesStore.hasInstances(for: $0) }
    }

    private var connectedHomeCount: Int {
        visibleTypes.reduce(into: 0) { total, type in
            total += servicesStore.instances(for: type).count
        }
    }

    private var gridEntries: [ServiceGridEntry] {
        visibleTypes.flatMap { type -> [ServiceGridEntry] in
            let instances = servicesStore.instances(for: type)
            if instances.isEmpty {
                return [ServiceGridEntry.disconnected(type: type)]
            }
            return instances.map { instance in
                ServiceGridEntry.connected(type: type, instance: instance)
            }
        }
    }

    private var hasUnreachableService: Bool {
        visibleTypes
            .flatMap { servicesStore.instances(for: $0) }
            .contains { servicesStore.reachability(for: $0.id) == false }
    }

    private var reachabilityHash: String {
        ServiceType.homeServices.map { type in
            let r = servicesStore.isReachable(type)
            return "\(type.rawValue):\(r.map { $0 ? "1" : "0" } ?? "?")"
        }.joined(separator: ",")
    }

    private var preferredSelectionHash: String {
        ServiceType.homeServices.map { type in
            let instanceId = servicesStore.preferredInstance(for: type)?.id.uuidString ?? "none"
            return "\(type.rawValue):\(instanceId)"
        }.joined(separator: ",")
    }

    private var suppressTailscaleSection: Bool {
        !servicesStore.instances(for: .pangolin).isEmpty && servicesStore.isTailscaleConnected
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 0) {
                    headerSection
                    if (hasUnreachableService || servicesStore.isTailscaleConnected) && !suppressTailscaleSection {
                        tailscaleSection
                    }
                    serviceGrid
                    footerSection
                }
                .padding(.horizontal, 16)
            }
            .background(AppTheme.background)
            .navigationBarHidden(true)
            .sheet(item: $showLogin) { type in
                ServiceLoginView(serviceType: type)
            }
            .sheet(isPresented: $showingServiceOrder) {
                ServiceOrderSheet()
            }
            .navigationDestination(for: HomeServiceRoute.self) { route in
                serviceDestination(for: route)
            }
            .onAppear {
                isViewVisible = true
                summaryRefreshID = UUID()
            }
            .onDisappear { isViewVisible = false }
            .task(id: summaryRefreshID) {
                guard isViewVisible else { return }
                await fetchAllSummaryData()
            }
            .onChange(of: reachabilityHash) { _, _ in
                if isViewVisible {
                    summaryRefreshID = UUID()
                }
                for type in ServiceType.homeServices {
                    for instance in servicesStore.instances(for: type) {
                        if servicesStore.reachability(for: instance.id) == false {
                            summaryData.removeValue(forKey: instance.id)
                        }
                    }
                }
            }
            .onChange(of: preferredSelectionHash) { _, _ in
                summaryData = [:]
                if isViewVisible {
                    summaryRefreshID = UUID()
                }
            }
        }
    }

    private var headerSection: some View {
        HStack {
            Text(localizer.t.launcherTitle)
                .font(.largeTitle)
                .fontWeight(.heavy)
                .foregroundStyle(.primary)

            Spacer()

            HStack(spacing: 8) {
                HStack(spacing: 5) {
                    Image(systemName: "bolt.fill")
                        .font(.caption2)
                        .accessibilityHidden(true)
                    Text("\(connectedHomeCount)")
                        .font(.subheadline.bold())
                }
                .foregroundStyle(AppTheme.accent)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .glassCard(cornerRadius: 20, tint: AppTheme.accent.opacity(0.15))

                Button {
                    HapticManager.light()
                    showingServiceOrder = true
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                        .font(.subheadline.bold())
                        .foregroundStyle(AppTheme.accent)
                        .frame(width: 36, height: 36)
                        .background(AppTheme.accent.opacity(0.12), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(localizer.t.homeReorderServices)
            }
        }
        .padding(.top, 8)
        .padding(.bottom, 16)
    }

    private var tailscaleSection: some View {
        Button {
            HapticManager.medium()
            if let url = URL(string: "tailscale://app") {
                UIApplication.shared.open(url, options: [:]) { success in
                    if !success, let appStoreUrl = URL(string: "https://apps.apple.com/app/tailscale/id1475387142") {
                        UIApplication.shared.open(appStoreUrl)
                    }
                }
            }
        } label: {
            HStack(spacing: 16) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(AppTheme.surface.opacity(0.95))
                        .frame(width: 44, height: 44)

                    AsyncImage(url: tailscaleIconURL) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFit()
                                .frame(width: 26, height: 26)
                        case .failure:
                            Image(systemName: servicesStore.isTailscaleConnected ? "shield.checkered" : "network.badge.shield.half.filled")
                                .font(.title3)
                                .foregroundStyle(servicesStore.isTailscaleConnected ? AppTheme.running : AppTheme.textMuted)
                        case .empty:
                            ProgressView()
                                .scaleEffect(0.7)
                        @unknown default:
                            EmptyView()
                        }
                    }
                    .accessibilityHidden(true)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(servicesStore.isTailscaleConnected ? localizer.t.tailscaleConnected : localizer.t.tailscaleOpen)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)

                    Text(servicesStore.isTailscaleConnected ? localizer.t.tailscaleSecure : localizer.t.tailscaleOpenDesc)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }

                Spacer(minLength: 0)

                if servicesStore.isTailscaleConnected {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(AppTheme.running)
                        .font(.title3)
                        .accessibilityHidden(true)
                } else {
                    HStack(spacing: 4) {
                        Text(localizer.t.tailscaleBadge)
                            .font(.caption2.bold())
                            .foregroundStyle(AppTheme.textMuted)
                        Image(systemName: "chevron.right")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                            .accessibilityHidden(true)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity)
            .glassCard(tint: servicesStore.isTailscaleConnected ? AppTheme.running.opacity(0.05) : nil)
        }
        .buttonStyle(.plain)
        .padding(.bottom, 16)
    }

    private var serviceGrid: some View {
        GlassGroup(spacing: 16) {
            LazyVGrid(columns: columns, spacing: 14) {
                ForEach(gridEntries) { entry in
                    switch entry {
                    case .disconnected(let type):
                        Button {
                            HapticManager.medium()
                            showLogin = type
                        } label: {
                            ServiceCardContent(
                                type: type,
                                label: type.displayName,
                                isConnected: false,
                                isPreferred: false,
                                reachable: nil,
                                isPinging: false,
                                summary: nil,
                                isSummaryLoading: false,
                                t: localizer.t
                            )
                        }
                        .buttonStyle(.plain)
                    case .connected(let type, let instance):
                        let isPreferred = servicesStore.preferredInstance(for: type)?.id == instance.id
                        NavigationLink(value: HomeServiceRoute(type: type, instanceId: instance.id)) {
                            ServiceCardContent(
                                type: type,
                                label: instance.displayLabel,
                                isConnected: true,
                                isPreferred: isPreferred,
                                reachable: servicesStore.reachability(for: instance.id),
                                isPinging: servicesStore.isPinging(instanceId: instance.id),
                                summary: summaryData[instance.id],
                                isSummaryLoading: summaryData[instance.id] == nil && summaryLoading,
                                t: localizer.t
                            ) {
                                Task { await servicesStore.checkReachability(for: instance.id) }
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var footerSection: some View {
        Text("\(localizer.t.launcherServices) • \(connectedHomeCount) \(localizer.t.launcherConnected.sentenceCased())")
            .font(.caption)
            .foregroundStyle(AppTheme.textMuted)
            .frame(maxWidth: .infinity)
            .padding(.top, 28)
            .padding(.bottom, 40)
    }

    @ViewBuilder
    private func serviceDestination(for route: HomeServiceRoute) -> some View {
        switch route.type {
        case .portainer:         PortainerDashboard(instanceId: route.instanceId)
        case .pihole:            PiHoleDashboard(instanceId: route.instanceId)
        case .adguardHome:       AdGuardHomeDashboard(instanceId: route.instanceId)
        case .technitium:        TechnitiumDashboard(instanceId: route.instanceId)
        case .beszel:            BeszelDashboard(instanceId: route.instanceId)
        case .healthchecks:      HealthchecksDashboard(instanceId: route.instanceId)
        case .linuxUpdate:            LinuxUpdateDashboard(instanceId: route.instanceId)
        case .dockhand:               DockhandDashboard(instanceId: route.instanceId)
        case .gitea:             GiteaDashboard(instanceId: route.instanceId)
        case .nginxProxyManager: NpmDashboard(instanceId: route.instanceId)
        case .pangolin:          PangolinDashboard(instanceId: route.instanceId)
        case .patchmon:          PatchmonDashboard(instanceId: route.instanceId)
        case .jellystat:         JellystatDashboard(instanceId: route.instanceId)
        case .plex:              PlexDashboard(instanceId: route.instanceId)
        case .qbittorrent:       QbittorrentDashboard(instanceId: route.instanceId)
        case .radarr:            RadarrDashboard(instanceId: route.instanceId)
        case .sonarr:            SonarrDashboard(instanceId: route.instanceId)
        case .lidarr:            LidarrDashboard(instanceId: route.instanceId)
        case .jellyseerr, .prowlarr, .bazarr, .gluetun, .flaresolverr:
                                 GenericMediaDashboard(serviceType: route.type, instanceId: route.instanceId)
        }
    }

    // MARK: - Summary Data Fetching

    private func fetchAllSummaryData() async {
        summaryLoading = true
        defer { summaryLoading = false }

        await withTaskGroup(of: (UUID, ServiceSummaryInfo?).self) { group in
            for type in ServiceType.homeServices {
                for instance in servicesStore.instances(for: type) {
                    guard servicesStore.reachability(for: instance.id) != false else { continue }
                    group.addTask { await (instance.id, self.fetchSummary(instanceId: instance.id, type: type)) }
                }
            }
            for await (id, info) in group {
                if let info { summaryData[id] = info }
            }
        }
    }

    @MainActor
    private func fetchSummary(instanceId: UUID, type: ServiceType) async -> ServiceSummaryInfo? {
        do {
            switch type {
            case .portainer:
                guard let client = await servicesStore.portainerClient(instanceId: instanceId) else { return nil }
                let endpoints = try await client.getEndpoints()
                guard let first = endpoints.first else { return nil }
                let containers = try await client.getContainers(endpointId: first.Id)
                let running = containers.filter { $0.State == "running" }.count
                return ServiceSummaryInfo(value: "\(running)", subValue: "/ \(containers.count)", label: localizer.t.portainerContainers)
            case .pihole:
                guard let client = await servicesStore.piholeClient(instanceId: instanceId) else { return nil }
                let stats = try await client.getStats()
                return ServiceSummaryInfo(value: Formatters.formatNumber(stats.queries.total), label: localizer.t.summaryQueryTotal)
            case .adguardHome:
                guard let client = await servicesStore.adguardClient(instanceId: instanceId) else { return nil }
                let stats = try await client.getStats()
                return ServiceSummaryInfo(value: Formatters.formatNumber(stats.totalQueries), label: localizer.t.summaryQueryTotal)
            case .technitium:
                guard let client = await servicesStore.technitiumClient(instanceId: instanceId) else { return nil }
                let overview = try await client.getOverview()
                return ServiceSummaryInfo(
                    value: Formatters.formatNumber(overview.totalBlocked),
                    subValue: "/ \(Formatters.formatNumber(overview.totalQueries))",
                    label: "Blocked queries"
                )
            case .beszel:
                guard let client = await servicesStore.beszelClient(instanceId: instanceId) else { return nil }
                let response = try await client.getSystems()
                let online = response.items.filter { $0.isOnline }.count
                return ServiceSummaryInfo(value: "\(online)", subValue: "/ \(response.items.count)", label: localizer.t.summarySystemsOnline)
            case .healthchecks:
                guard let client = await servicesStore.healthchecksClient(instanceId: instanceId) else { return nil }
                let checks = try await client.listChecks()
                let healthy = checks.filter { $0.status == "up" || $0.status == "grace" }.count
                return ServiceSummaryInfo(value: "\(healthy)", subValue: "/ \(checks.count)", label: localizer.t.healthchecksChecks)
            case .linuxUpdate:
                guard let client = await servicesStore.linuxUpdateClient(instanceId: instanceId) else { return nil }
                let stats = try await client.getDashboardStats()
                return ServiceSummaryInfo(
                    value: Formatters.formatNumber(stats.totalUpdates),
                    subValue: "/ \(stats.total)",
                    label: localizer.t.patchmonUpdates
                )
            case .dockhand:
                guard let client = await servicesStore.dockhandClient(instanceId: instanceId) else { return nil }
                let overview = try await client.getQuickOverview(environmentId: nil)
                return ServiceSummaryInfo(
                    value: "\(overview.runningContainers)",
                    subValue: "/ \(overview.totalContainers)",
                    label: "Running containers"
                )
            case .gitea:
                guard let client = await servicesStore.giteaClient(instanceId: instanceId) else { return nil }
                let repos = try await client.getUserRepos(page: 1, limit: 100)
                return ServiceSummaryInfo(value: "\(repos.count)", label: localizer.t.giteaRepos)
            case .nginxProxyManager:
                guard let client = await servicesStore.npmClient(instanceId: instanceId) else { return nil }
                let report = try await client.getHostReport()
                return ServiceSummaryInfo(value: "\(report.proxy)", subValue: "/ \(report.total)", label: localizer.t.npmProxyHosts)
            case .pangolin:
                guard let client = await servicesStore.pangolinClient(instanceId: instanceId) else { return nil }
                let summary = try await client.aggregateSummary()
                return ServiceSummaryInfo(
                    value: "\(summary.sites)",
                    subValue: "/ \(summary.clients)",
                    label: PangolinStrings.forLanguage(localizer.language).sitesClientsLabel
                )
            case .patchmon:
                guard let client = await servicesStore.patchmonClient(instanceId: instanceId) else { return nil }
                let response = try await client.getHosts()
                let securityUpdates = response.hosts.reduce(0) { $0 + $1.securityUpdatesCount }
                let totalHosts = response.total ?? response.hosts.count
                return ServiceSummaryInfo(value: "\(securityUpdates)", subValue: "/ \(totalHosts)", label: localizer.t.patchmonSecurity)
            case .jellystat:
                guard let client = await servicesStore.jellystatClient(instanceId: instanceId) else { return nil }
                let summary = try await client.getWatchSummary(days: 7)
                return ServiceSummaryInfo(value: formatWatchTime(summary.totalHours), label: localizer.t.jellystatWatchTimeHome)
            case .plex:
                guard let client = await servicesStore.plexClient(instanceId: instanceId) else { return nil }
                let libs = try await client.getLibraries()
                let totalItems = libs.reduce(0) { $0 + $1.itemCount + $1.episodeCount }
                return ServiceSummaryInfo(value: Formatters.formatNumber(totalItems), label: localizer.t.plexTotalItems)
            default:
                return nil
            }
        } catch {
            return nil
        }
    }

    private func formatWatchTime(_ hours: Double) -> String {
        if hours > 0, hours < 1 {
            let minutes = Int((hours * 60).rounded(.down))
            if minutes <= 0 {
                return "<1m"
            }
            return "\(minutes)m"
        }
        if hours >= 100 {
            return String(format: "%.0fh", hours)
        }
        return String(format: "%.1fh", hours)
    }
}

private struct HomeServiceRoute: Hashable {
    let type: ServiceType
    let instanceId: UUID
}

private enum ServiceGridEntry: Identifiable {
    case disconnected(type: ServiceType)
    case connected(type: ServiceType, instance: ServiceInstance)

    var id: String {
        switch self {
        case .disconnected(let type):
            return "disconnected-\(type.rawValue)"
        case .connected(_, let instance):
            return "connected-\(instance.id.uuidString)"
        }
    }
}

struct ServiceSummaryInfo {
    let value: String
    let subValue: String?
    let label: String

    init(value: String, subValue: String? = nil, label: String) {
        self.value = value
        self.subValue = subValue
        self.label = label
    }
}

private struct ServiceOrderSheet: View {
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(settingsStore.serviceOrder.filter { ServiceType.homeServices.contains($0) }) { type in
                    let isHidden = settingsStore.isServiceHidden(type)
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(type.displayName)
                                .font(.body.weight(.semibold))
                            if isHidden {
                                Text(localizer.t.settingsHiddenBadge)
                                    .font(.caption2.bold())
                                    .foregroundStyle(.secondary)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(Color.secondary.opacity(0.12), in: Capsule())
                            }
                        }
                        Spacer()
                        HStack(spacing: 12) {
                            Button {
                                settingsStore.toggleServiceVisibility(type)
                                HapticManager.light()
                            } label: {
                                Image(systemName: isHidden ? "eye.slash" : "eye")
                            }
                            .buttonStyle(.borderless)
                            .accessibilityLabel(isHidden ? localizer.t.settingsShowServiceGeneric : localizer.t.settingsHideServiceGeneric)

                            Button {
                                settingsStore.moveService(type, offset: -1, within: ServiceType.homeServices)
                                HapticManager.light()
                            } label: {
                                Image(systemName: "chevron.up")
                            }
                            .buttonStyle(.borderless)
                            .disabled(!settingsStore.canMoveService(type, offset: -1, within: ServiceType.homeServices))
                            .accessibilityLabel(localizer.t.settingsMoveUp)

                            Button {
                                settingsStore.moveService(type, offset: 1, within: ServiceType.homeServices)
                                HapticManager.light()
                            } label: {
                                Image(systemName: "chevron.down")
                            }
                            .buttonStyle(.borderless)
                            .disabled(!settingsStore.canMoveService(type, offset: 1, within: ServiceType.homeServices))
                            .accessibilityLabel(localizer.t.settingsMoveDown)
                        }
                    }
                }
            }
            .navigationTitle(localizer.t.homeReorderServices)
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(AppTheme.background)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.done) { dismiss() }
                }
            }
        }
    }
}

private struct ServiceCardContent: View {
    @Environment(\.colorScheme) private var colorScheme

    let type: ServiceType
    let label: String
    let isConnected: Bool
    let isPreferred: Bool
    let reachable: Bool?
    let isPinging: Bool
    let summary: ServiceSummaryInfo?
    let isSummaryLoading: Bool
    let t: Translations
    var onRefresh: (() -> Void)? = nil
    
    private var cardShape: RoundedRectangle {
        RoundedRectangle(cornerRadius: AppTheme.cardRadius, style: .continuous)
    }

    private var defaultCardTint: Color? {
        if isConnected && reachable == false {
            return type.colors.primary.opacity(0.06)
        }
        return nil
    }

    private var cardGradient: LinearGradient {
        let dark = colorScheme == .dark
        let topAlpha: Double = dark ? (reachable == false ? 0.11 : 0.07) : (reachable == false ? 0.08 : 0.05)
        let bottomAlpha: Double = dark ? (reachable == false ? 0.04 : 0.015) : (reachable == false ? 0.025 : 0.01)
        return LinearGradient(
            colors: [
                type.colors.primary.opacity(topAlpha),
                type.colors.dark.opacity(bottomAlpha),
                Color.clear
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var cardBorderColor: Color {
        let dark = colorScheme == .dark
        if reachable == false {
            return type.colors.primary.opacity(dark ? 0.9 : 0.76)
        }
        if !isConnected {
            return type.colors.primary.opacity(dark ? 0.62 : 0.5)
        }
        return type.colors.primary.opacity(dark ? 0.74 : 0.6)
    }

    private var cardGlassTint: Color? {
        let dark = colorScheme == .dark
        if reachable == false {
            return type.colors.primary.opacity(dark ? 0.08 : 0.06)
        }
        return type.colors.primary.opacity(dark ? 0.05 : 0.03)
    }

    var body: some View {
        let cardCore = VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                ServiceIconView(type: type, size: 34)
                .frame(width: 56, height: 56)
                .background(type.colors.bg, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .accessibilityHidden(true)

                Spacer(minLength: 6)

                if isConnected && reachable == true, let summary {
                    VStack(alignment: .trailing, spacing: 2) {
                        HStack(alignment: .firstTextBaseline, spacing: 3) {
                            Text(summary.value)
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                .foregroundStyle(type.colors.primary)
                            if let sub = summary.subValue {
                                Text(sub)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                            }
                        }
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)

                        Text(summary.label.sentenceCased())
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(2)
                            .minimumScaleFactor(0.72)
                            .multilineTextAlignment(.trailing)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                    .frame(maxWidth: 122, alignment: .trailing)
                } else if isConnected && reachable == true && isSummaryLoading {
                    SkeletonLoader(height: 16, cornerRadius: 4)
                        .frame(width: 60)
                } else if isConnected && reachable == false, let onRefresh {
                    Button(action: onRefresh) {
                        Image(systemName: "arrow.clockwise")
                            .font(.subheadline.bold())
                            .foregroundStyle(type.colors.primary)
                            .frame(width: 36, height: 36)
                            .background(type.colors.primary.opacity(0.1), in: Circle())
                            .rotationEffect(.degrees(isPinging ? 360 : 0))
                            .animation(isPinging ? .linear(duration: 1).repeatForever(autoreverses: false) : .default, value: isPinging)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(t.refresh)
                } else if isConnected && reachable == nil {
                    ProgressView()
                        .controlSize(.small)
                        .tint(type.colors.primary)
                }
            }
            .padding(.bottom, 10)

            Text(label)
                .font(.body.bold())
                .foregroundStyle(.primary)
                .lineLimit(1)

            Spacer(minLength: 6)

            HStack(spacing: 6) {
                statusBadge



            }
        }
        .frame(maxWidth: .infinity, minHeight: 140, alignment: .leading)
        .padding(14)
        .contentShape(Rectangle())

        cardCore
            .glassCard(tint: defaultCardTint)
        .task {
            if isConnected, reachable == nil, !isPinging {
                onRefresh?()
            }
        }
    }

    @ViewBuilder
    private var statusBadge: some View {
        if !isConnected {
            HStack(spacing: 5) {
                Text(t.launcherTapToConnect)
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.textMuted)
                Image(systemName: "chevron.right")
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        } else if reachable == false {
            HStack(spacing: 5) {
                Circle().fill(Color.red).frame(width: 6, height: 6)
                Text(t.statusUnreachable)
                    .font(.caption2.bold())
                    .foregroundStyle(Color.red)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color.red.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        } else if reachable == true {
            HStack(spacing: 5) {
                Circle().fill(Color.green).frame(width: 6, height: 6)
                Text(t.statusOnline)
                    .font(.caption2.bold())
                    .foregroundStyle(Color.green)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        } else {
            HStack(spacing: 5) {
                Circle().fill(AppTheme.info).frame(width: 6, height: 6)
                Text(t.statusVerifying)
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.info)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(AppTheme.info.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }
}

// MARK: - Reusable Components

struct ServiceIconView: View {
    let type: ServiceType
    let size: CGFloat

    private var candidates: [URL] { type.iconCandidates }
    private var localAssetName: String { type.localIconAssetName }

    var body: some View {
        ZStack {
            // Always render a local fallback symbol so icons never appear blank.
            fallbackView

            if let local = UIImage(named: localAssetName) {
                Image(uiImage: local)
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
            } else if let primary = candidates.first {
                primaryIconView(primary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    @ViewBuilder
    private func primaryIconView(_ url: URL) -> some View {
        AsyncImage(url: url) { phase in
            switch phase {
            case .success(let image):
                image
                    .resizable()
                    .renderingMode(.original)
                    .scaledToFit()
            case .failure:
                secondaryIconView
            case .empty:
                EmptyView()
            @unknown default:
                EmptyView()
            }
        }
        .id(url.absoluteString)
    }

    @ViewBuilder
    private var secondaryIconView: some View {
        if candidates.count > 1 {
            AsyncImage(url: candidates[1]) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .renderingMode(.original)
                        .scaledToFit()
                case .failure:
                    EmptyView()
                case .empty:
                    EmptyView()
                @unknown default:
                    EmptyView()
                }
            }
            .id(candidates[1].absoluteString)
        }
    }

    private var fallbackView: some View {
        Image(systemName: type.symbolName)
            .font(.system(size: size * 0.6))
            .foregroundStyle(type.colors.primary)
    }
}
