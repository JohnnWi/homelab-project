import SwiftUI

struct MediaDashboardView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer

    @AppStorage("homelab_media_arr_tutorial_dismissed") private var hasDismissedTutorial = false
    @State private var quickSetupType: ServiceType?
    @State private var showLogin: ServiceType? = nil
    @State private var showingMediaServiceOrder = false
    @State private var cardPreviewsByInstanceId: [UUID: MediaCardPreviewData] = [:]
    @State private var previewLoadingIds: Set<UUID> = []
    @State private var previewErrorIds: Set<UUID> = []
    @State private var previewLastLoadedAt: [UUID: Date] = [:]

    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]
    private let previewMinRefreshInterval: TimeInterval = 90

    private var hiddenServiceKeys: Set<String> {
        settingsStore.hiddenServices
    }

    private var mediaServices: [ServiceType] {
        settingsStore.serviceOrder.filter { ServiceType.mediaServices.contains($0) && !hiddenServiceKeys.contains($0.rawValue) }
    }

    private var configuredMediaServices: [ServiceType] {
        mediaServices.filter { servicesStore.hasInstances(for: $0) }
    }

    private var unconfiguredMediaServices: [ServiceType] {
        mediaServices.filter { !servicesStore.hasInstances(for: $0) }
    }

    private var connectedMediaCount: Int {
        mediaServices.reduce(0) { partial, type in
            partial + servicesStore.instances(for: type).count
        }
    }

    private var gridEntries: [MediaGridEntry] {
        mediaServices.flatMap { type -> [MediaGridEntry] in
            let instances = servicesStore.instances(for: type)
            if instances.isEmpty {
                return [MediaGridEntry.disconnected(type: type)]
            }
            return instances.map { instance in
                MediaGridEntry.connected(type: type, instance: instance)
            }
        }
    }

    private var hasUnreachableMediaService: Bool {
        configuredMediaServices
            .flatMap { servicesStore.instances(for: $0) }
            .contains { servicesStore.reachability(for: $0.id) == false }
    }

    private var arr: ArrStrings {
        localizer.arr
    }

    private func previewState(for instanceId: UUID) -> MediaCardPreviewState {
        MediaCardPreviewState(
            preview: cardPreviewsByInstanceId[instanceId],
            isLoading: previewLoadingIds.contains(instanceId),
            hasError: previewErrorIds.contains(instanceId)
        )
    }

    @MainActor
    private func requestCardPreview(for instance: ServiceInstance, force: Bool = false) async {
        guard servicesStore.reachability(for: instance.id) != false else { return }
        if previewLoadingIds.contains(instance.id) { return }

        if !force,
           let lastLoaded = previewLastLoadedAt[instance.id],
           Date().timeIntervalSince(lastLoaded) < previewMinRefreshInterval,
           cardPreviewsByInstanceId[instance.id] != nil {
            return
        }

        previewLoadingIds.insert(instance.id)
        previewErrorIds.remove(instance.id)
        defer { previewLoadingIds.remove(instance.id) }

        do {
            let preview = try await loadCardPreview(for: instance)
            cardPreviewsByInstanceId[instance.id] = preview
            previewLastLoadedAt[instance.id] = Date()
            previewErrorIds.remove(instance.id)
        } catch {
            if cardPreviewsByInstanceId[instance.id] == nil {
                previewErrorIds.insert(instance.id)
            }
        }
    }

    private func loadCardPreview(for instance: ServiceInstance) async throws -> MediaCardPreviewData {
        switch instance.type {
        case .qbittorrent:
            guard let client = await servicesStore.qbittorrentClient(instanceId: instance.id) else {
                throw APIError.notConfigured
            }
            let transfer = try await client.getTransferInfo()
            return MediaCardPreviewData(
                headline: transfer.connection_status.capitalized,
                metrics: [
                    MediaCardPreviewMetric(label: arr.download, value: "\(Formatters.formatBytes(Double(transfer.dl_info_speed)))/s"),
                    MediaCardPreviewMetric(label: arr.upload, value: "\(Formatters.formatBytes(Double(transfer.up_info_speed)))/s"),
                    MediaCardPreviewMetric(label: arr.dhtLabel, value: "\(transfer.dht_nodes ?? 0)")
                ]
            )
        case .radarr:
            guard let client = await servicesStore.radarrClient(instanceId: instance.id) else {
                throw APIError.notConfigured
            }
            async let statusTask = client.getSystemStatus()
            async let queueTask = client.getQueue()
            async let healthTask = client.getHealthMessages()
            async let upcomingTask = client.getUpcomingTitles(limit: 8)
            let status = try await statusTask
            let queue = try await queueTask
            let health = await healthTask
            let upcoming = await upcomingTask
            return MediaCardPreviewData(
                headline: "v\(status.version) • \(status.displayBranch)",
                metrics: [
                    MediaCardPreviewMetric(label: arr.download, value: "\(queue.totalRecords)"),
                    MediaCardPreviewMetric(label: arr.health, value: "\(health.count)"),
                    MediaCardPreviewMetric(label: arr.upcoming, value: "\(upcoming.count)")
                ]
            )
        case .sonarr:
            guard let client = await servicesStore.sonarrClient(instanceId: instance.id) else {
                throw APIError.notConfigured
            }
            async let statusTask = client.getSystemStatus()
            async let queueTask = client.getQueue()
            async let healthTask = client.getHealthMessages()
            async let upcomingTask = client.getUpcomingTitles(limit: 8)
            let status = try await statusTask
            let queue = try await queueTask
            let health = await healthTask
            let upcoming = await upcomingTask
            return MediaCardPreviewData(
                headline: "v\(status.version) • \(status.displayBranch)",
                metrics: [
                    MediaCardPreviewMetric(label: arr.download, value: "\(queue.totalRecords)"),
                    MediaCardPreviewMetric(label: arr.health, value: "\(health.count)"),
                    MediaCardPreviewMetric(label: arr.upcoming, value: "\(upcoming.count)")
                ]
            )
        case .lidarr:
            guard let client = await servicesStore.lidarrClient(instanceId: instance.id) else {
                throw APIError.notConfigured
            }
            async let statusTask = client.getSystemStatus()
            async let queueTask = client.getQueue()
            async let healthTask = client.getHealthMessages()
            async let upcomingTask = client.getUpcomingTitles(limit: 8)
            let status = try await statusTask
            let queue = try await queueTask
            let health = await healthTask
            let upcoming = await upcomingTask
            return MediaCardPreviewData(
                headline: "v\(status.version) • \(status.displayBranch)",
                metrics: [
                    MediaCardPreviewMetric(label: arr.download, value: "\(queue.totalRecords)"),
                    MediaCardPreviewMetric(label: arr.health, value: "\(health.count)"),
                    MediaCardPreviewMetric(label: arr.upcoming, value: "\(upcoming.count)")
                ]
            )
        case .jellyseerr, .prowlarr, .bazarr, .gluetun, .flaresolverr:
            guard let client = await servicesStore.genericMediaClient(instanceId: instance.id) else {
                throw APIError.notConfigured
            }
            guard let snapshot = await client.serviceSnapshot() else {
                throw APIError.custom("No data")
            }
            return genericPreview(from: snapshot)
        default:
            throw APIError.notConfigured
        }
    }

    private func genericPreview(from snapshot: GenericServiceSnapshot) -> MediaCardPreviewData {
        switch snapshot {
        case .jellyseerr(let data):
            return MediaCardPreviewData(
                headline: data.version.map { "v\($0)" },
                metrics: [
                    MediaCardPreviewMetric(label: arr.requests, value: "\(data.totalRequests)"),
                    MediaCardPreviewMetric(label: arr.pending, value: "\(data.pendingRequests)"),
                    MediaCardPreviewMetric(label: arr.available, value: "\(data.availableRequests)")
                ]
            )
        case .prowlarr(let data):
            return MediaCardPreviewData(
                headline: data.version.map { "v\($0)" },
                metrics: [
                    MediaCardPreviewMetric(label: arr.indexers, value: "\(data.indexers.count)"),
                    MediaCardPreviewMetric(label: arr.apps, value: "\(data.applications.count)"),
                    MediaCardPreviewMetric(label: arr.issues, value: "\(data.unhealthyCount)")
                ]
            )
        case .bazarr(let data):
            let wanted = data.badges.first(where: { $0.label.lowercased() == "wanted" })?.value
            let missing = data.badges.first(where: { $0.label.lowercased() == "missing" })?.value
            let providers = data.badges.first(where: { $0.label.lowercased() == "providers" })?.value
            return MediaCardPreviewData(
                headline: data.version.map { "v\($0)" },
                metrics: [
                    MediaCardPreviewMetric(label: arr.subtitles, value: wanted ?? "0"),
                    MediaCardPreviewMetric(label: arr.issues, value: "\(data.issues.count)"),
                    MediaCardPreviewMetric(label: arr.provider, value: providers ?? missing ?? "0")
                ]
            )
        case .gluetun(let data):
            return MediaCardPreviewData(
                headline: data.vpnProvider ?? data.serverName ?? data.connectionStatus,
                metrics: [
                    MediaCardPreviewMetric(label: arr.statusLabel, value: data.connectionStatus ?? localizer.t.noData),
                    MediaCardPreviewMetric(label: arr.publicIPLabel, value: data.publicIP ?? localizer.t.noData),
                    MediaCardPreviewMetric(label: arr.forwardedPort, value: data.forwardedPort ?? localizer.t.noData)
                ]
            )
        case .flaresolverr(let data):
            return MediaCardPreviewData(
                headline: data.version.map { "v\($0)" } ?? data.message,
                metrics: [
                    MediaCardPreviewMetric(label: arr.sessions, value: "\(data.sessions.count)"),
                    MediaCardPreviewMetric(label: arr.statusLabel, value: data.status ?? localizer.t.noData)
                ]
            )
        }
    }

    @MainActor
    private func prunePreviewCache() {
        let validIds = Set(
            gridEntries.compactMap { entry -> UUID? in
                if case .connected(_, let instance) = entry {
                    return instance.id
                }
                return nil
            }
        )

        cardPreviewsByInstanceId = cardPreviewsByInstanceId.filter { validIds.contains($0.key) }
        previewLastLoadedAt = previewLastLoadedAt.filter { validIds.contains($0.key) }
        previewLoadingIds = previewLoadingIds.filter { validIds.contains($0) }
        previewErrorIds = previewErrorIds.filter { validIds.contains($0) }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 24) {
                    headerSection

                    if !hasDismissedTutorial {
                        tutorialSection
                    }

                    if hasUnreachableMediaService || servicesStore.isTailscaleConnected {
                        tailscaleSection
                    }

                    if !unconfiguredMediaServices.isEmpty {
                        quickSetupSection
                    }

                    if gridEntries.isEmpty {
                        emptyStateSection
                    } else {
                        serviceGrid
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 40)
            }
            .background(AppTheme.background)
            .navigationBarHidden(true)
            .navigationDestination(for: MediaServiceRoute.self) { route in
                mediaServiceDestination(for: route)
            }
            .sheet(item: $quickSetupType) { type in
                ServiceLoginView(serviceType: type)
            }
            .sheet(item: $showLogin) { type in
                ServiceLoginView(serviceType: type)
            }
            .sheet(isPresented: $showingMediaServiceOrder) {
                MediaServiceOrderSheet()
            }
            .onChange(of: gridEntries.map(\.id)) { _, _ in
                prunePreviewCache()
            }
            .task {
                prunePreviewCache()
            }
        }
    }

    @ViewBuilder
    private func mediaServiceDestination(for route: MediaServiceRoute) -> some View {
        switch route.type {
        case .qbittorrent:
            QbittorrentDashboard(instanceId: route.instanceId)
        case .radarr:
            RadarrDashboard(instanceId: route.instanceId)
        case .sonarr:
            SonarrDashboard(instanceId: route.instanceId)
        case .lidarr:
            LidarrDashboard(instanceId: route.instanceId)
        case .jellyseerr, .prowlarr, .bazarr, .gluetun, .flaresolverr:
            GenericMediaDashboard(serviceType: route.type, instanceId: route.instanceId)
        default:
            EmptyView()
        }
    }

    private var headerSection: some View {
        HStack {
            Text(localizer.t.tabMedia)
                .font(.largeTitle)
                .fontWeight(.heavy)
                .foregroundStyle(.primary)

            Spacer()

            HStack(spacing: 8) {
                HStack(spacing: 5) {
                    Image(systemName: "play.fill")
                        .font(.caption2)
                        .accessibilityHidden(true)
                    Text("\(connectedMediaCount)")
                        .font(.subheadline.bold())
                }
                .foregroundStyle(AppTheme.info)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .glassCard(cornerRadius: 20, tint: AppTheme.info.opacity(0.15))

                Button {
                    HapticManager.light()
                    showingMediaServiceOrder = true
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                        .font(.subheadline.bold())
                        .foregroundStyle(AppTheme.info)
                        .frame(width: 36, height: 36)
                        .background(AppTheme.info.opacity(0.12), in: Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(localizer.t.homeReorderServices)
            }
        }
        .padding(.top, 8)
        .padding(.bottom, 8)
    }

    private var emptyStateSection: some View {
        VStack(spacing: 16) {
            Image(systemName: "play.tv.fill")
                .font(.system(size: 48))
                .foregroundStyle(AppTheme.textMuted)
            
            Text(localizer.t.settingsNoInstances)
                .font(.headline)
            
            Text(localizer.t.settingsAddInstance)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity)
        .glassCard()
        .padding(.top, 40)
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
            HStack(spacing: 12) {
                Image(systemName: servicesStore.isTailscaleConnected ? "shield.checkered" : "network.badge.shield.half.filled")
                    .font(.subheadline.bold())
                    .foregroundStyle(servicesStore.isTailscaleConnected ? AppTheme.running : AppTheme.info)

                VStack(alignment: .leading, spacing: 2) {
                    Text(servicesStore.isTailscaleConnected ? localizer.t.tailscaleConnected : localizer.t.tailscaleOpen)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                    Text(servicesStore.isTailscaleConnected ? localizer.t.tailscaleSecure : localizer.t.tailscaleOpenDesc)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(2)
                }

                Spacer()

                HStack(spacing: 4) {
                    Text(localizer.t.tailscaleBadge)
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.textMuted)
                    Image(systemName: "chevron.right")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .padding(12)
            .glassCard(tint: servicesStore.isTailscaleConnected ? AppTheme.running.opacity(0.05) : AppTheme.info.opacity(0.05))
        }
        .buttonStyle(.plain)
    }

    private var tutorialSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "sparkles.rectangle.stack")
                    .foregroundStyle(AppTheme.info)
                Text(arr.tutorialTitle)
                    .font(.headline.bold())
                Spacer()
            }

            Text(arr.tutorialBody)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)

            VStack(alignment: .leading, spacing: 8) {
                tutorialRow(icon: "1.circle.fill", text: arr.tutorialStepConnect)
                tutorialRow(icon: "2.circle.fill", text: arr.tutorialStepOpen)
                tutorialRow(icon: "3.circle.fill", text: arr.tutorialStepAutomations)
            }

            HStack(spacing: 10) {
                if let firstUnconfigured = unconfiguredMediaServices.first {
                    Button {
                        quickSetupType = firstUnconfigured
                    } label: {
                        Label(arr.tutorialActionConfigure, systemImage: "plus")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.info)
                }

                Button {
                    hasDismissedTutorial = true
                } label: {
                    Text(arr.tutorialActionDismiss)
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
            .font(.subheadline.weight(.semibold))
        }
        .padding(16)
        .glassCard(tint: AppTheme.info.opacity(0.06))
    }

    private var quickSetupSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(arr.quickSetupTitle)
                .font(.title3.bold())
            Text(arr.quickSetupSubtitle)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)

            LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 10)], spacing: 10) {
                ForEach(unconfiguredMediaServices, id: \.self) { type in
                    Button {
                        quickSetupType = type
                    } label: {
                        HStack(spacing: 8) {
                            ServiceIconView(type: type, size: 18)
                            Text(arr.addService(type.displayName))
                                .font(.caption.bold())
                                .lineLimit(1)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(type.colors.bg, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(16)
        .glassCard(tint: AppTheme.primary.opacity(0.04))
    }

    private func tutorialRow(icon: String, text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: icon)
                .foregroundStyle(AppTheme.info)
            Text(text)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
        }
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
                            MediaCardContent(
                                type: type,
                                instance: nil,
                                reachable: nil,
                                preview: nil,
                                previewLoading: false,
                                previewFailed: false,
                                onRequestPreview: nil,
                                onRefresh: nil
                            )
                        }
                        .buttonStyle(.plain)
                    case .connected(let type, let instance):
                        let state = previewState(for: instance.id)
                        NavigationLink(value: MediaServiceRoute(type: type, instanceId: instance.id)) {
                            MediaCardContent(
                                type: type,
                                instance: instance,
                                reachable: servicesStore.reachability(for: instance.id),
                                preview: state.preview,
                                previewLoading: state.isLoading,
                                previewFailed: state.hasError,
                                onRequestPreview: {
                                    await requestCardPreview(for: instance)
                                },
                                onRefresh: {
                                    await servicesStore.checkReachability(for: instance.id)
                                }
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

private enum MediaGridEntry: Identifiable {
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

private struct MediaServiceRoute: Hashable {
    let type: ServiceType
    let instanceId: UUID
}

private struct MediaCardPreviewMetric: Identifiable, Equatable {
    let label: String
    let value: String

    var id: String { "\(label)-\(value)" }
}

private struct MediaCardPreviewData: Equatable {
    let headline: String?
    let metrics: [MediaCardPreviewMetric]
}

private struct MediaCardPreviewState {
    let preview: MediaCardPreviewData?
    let isLoading: Bool
    let hasError: Bool
}

private struct MediaCardContent: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    let type: ServiceType
    let instance: ServiceInstance?
    let reachable: Bool?
    let preview: MediaCardPreviewData?
    let previewLoading: Bool
    let previewFailed: Bool
    let onRequestPreview: (() async -> Void)?
    let onRefresh: (() async -> Void)?

    private var isConnected: Bool { instance != nil }
    private var connectionStatusText: String {
        if !isConnected { return localizer.t.loginConnect }
        if reachable == true { return localizer.t.statusOnline }
        if reachable == false { return localizer.t.statusUnreachable }
        return localizer.t.statusVerifying
    }
    private var connectionStatusColor: Color {
        if !isConnected { return AppTheme.textMuted }
        if reachable == true { return type.colors.primary }
        if reachable == false { return type.colors.primary }
        return AppTheme.info
    }
    private var cardGradient: LinearGradient {
        LinearGradient(
            colors: [
                type.colors.primary.opacity(isConnected ? 0.13 : 0.06),
                type.colors.dark.opacity(isConnected ? 0.04 : 0.015),
                Color.clear
            ],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                ServiceIconView(type: type, size: 24)
                    .frame(width: 40, height: 40)
                    .background(type.colors.bg, in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                Spacer()

                if isConnected && previewLoading && preview != nil {
                    ProgressView()
                        .controlSize(.mini)
                        .tint(type.colors.primary)
                } else if isConnected && reachable == false, onRefresh != nil {
                    Button {
                        Task { await onRefresh?() }
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.subheadline.bold())
                            .foregroundStyle(type.colors.primary)
                            .frame(width: 32, height: 32)
                            .background(type.colors.primary.opacity(0.1), in: Circle())
                    }
                    .buttonStyle(.plain)
                } else {
                    Image(systemName: "chevron.right")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(instance?.displayLabel ?? type.displayName)
                    .font(.subheadline.weight(.bold))
                    .lineLimit(2)
                    .minimumScaleFactor(0.82)

                statusChip
            }

            if isConnected {
                if let preview {
                    if let headline = preview.headline, !headline.isEmpty {
                        Text(headline)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textSecondary)
                            .lineLimit(1)
                    }

                    VStack(spacing: 3) {
                        ForEach(preview.metrics.prefix(2)) { metric in
                            metricRow(metric)
                        }
                    }

                    if let extraMetric = preview.metrics.dropFirst(2).first {
                        metricPill(extraMetric)
                    }
                } else if previewLoading {
                    ProgressView()
                        .controlSize(.mini)
                        .tint(type.colors.primary)
                } else if previewFailed, reachable == true {
                    Text(localizer.t.noData)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(height: 196, alignment: .topLeading)
        .padding(14)
        .glassCard(tint: isConnected ? type.colors.primary.opacity(0.06) : nil)
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(cardGradient)
                .blendMode(.plusLighter)
                .opacity(0.7)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(type.colors.primary.opacity(isConnected ? 0.15 : 0.06), lineWidth: 1)
        )
        .task(id: reachable) {
            if let instance, reachable == nil {
                await servicesStore.checkReachability(for: instance.id)
            }
            if isConnected, reachable != false {
                await onRequestPreview?()
            }
        }
    }

    private var statusChip: some View {
        HStack(spacing: 6) {
            if isConnected && reachable == nil {
                ProgressView()
                    .controlSize(.mini)
                    .tint(connectionStatusColor)
            } else {
                Circle()
                    .fill(connectionStatusColor)
                    .frame(width: 6, height: 6)
            }
            Text(connectionStatusText)
                .font(.caption2.weight(.bold))
                .foregroundStyle(connectionStatusColor)
                .lineLimit(1)
        }
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(connectionStatusColor.opacity(isConnected ? 0.14 : 0.1), in: Capsule())
    }

    private func metricRow(_ metric: MediaCardPreviewMetric) -> some View {
        HStack(spacing: 6) {
            Text(metric.label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.82)
            Spacer(minLength: 0)
            Text(metric.value)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.82)
        }
    }

    private func metricPill(_ metric: MediaCardPreviewMetric) -> some View {
        HStack(spacing: 6) {
            Text(metric.label)
                .font(.caption2.weight(.semibold))
            Text(metric.value)
                .font(.caption2.weight(.heavy))
                .lineLimit(1)
        }
        .foregroundStyle(type.colors.primary)
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(type.colors.primary.opacity(0.12), in: Capsule())
    }
}

private struct MediaServiceOrderSheet: View {
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(settingsStore.serviceOrder.filter { ServiceType.mediaServices.contains($0) }) { type in
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
                                settingsStore.moveService(type, offset: -1, within: ServiceType.mediaServices)
                                HapticManager.light()
                            } label: {
                                Image(systemName: "chevron.up")
                            }
                            .buttonStyle(.borderless)
                            .disabled(!settingsStore.canMoveService(type, offset: -1, within: ServiceType.mediaServices))
                            .accessibilityLabel(localizer.t.settingsMoveUp)

                            Button {
                                settingsStore.moveService(type, offset: 1, within: ServiceType.mediaServices)
                                HapticManager.light()
                            } label: {
                                Image(systemName: "chevron.down")
                            }
                            .buttonStyle(.borderless)
                            .disabled(!settingsStore.canMoveService(type, offset: 1, within: ServiceType.mediaServices))
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
