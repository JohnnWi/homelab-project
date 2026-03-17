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

    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    private var visibleTypes: [ServiceType] {
        settingsStore.serviceOrder.filter { !settingsStore.isServiceHidden($0) }
    }

    private var hasServices: Bool {
        visibleTypes.contains { servicesStore.hasInstances(for: $0) }
    }

    private var hasUnreachableService: Bool {
        visibleTypes
            .flatMap { servicesStore.instances(for: $0) }
            .contains { servicesStore.reachability(for: $0.id) == false }
    }

    private var reachabilityHash: String {
        ServiceType.allCases.map { type in
            let r = servicesStore.isReachable(type)
            return "\(type.rawValue):\(r.map { $0 ? "1" : "0" } ?? "?")"
        }.joined(separator: ",")
    }

    private var preferredSelectionHash: String {
        ServiceType.allCases.map { type in
            let instanceId = servicesStore.preferredInstance(for: type)?.id.uuidString ?? "none"
            return "\(type.rawValue):\(instanceId)"
        }.joined(separator: ",")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 0) {
                    headerSection
                    if hasUnreachableService || servicesStore.isTailscaleConnected {
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
            .task(id: summaryRefreshID) { await fetchAllSummaryData() }
            .onChange(of: reachabilityHash) { _, _ in
                summaryRefreshID = UUID()
                for type in ServiceType.allCases {
                    for instance in servicesStore.instances(for: type) {
                        if servicesStore.reachability(for: instance.id) == false {
                            summaryData.removeValue(forKey: instance.id)
                        }
                    }
                }
            }
            .onChange(of: preferredSelectionHash) { _, _ in
                summaryData = [:]
                summaryRefreshID = UUID()
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
                    Text("\(servicesStore.connectedCount)")
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
                        .fill(servicesStore.isTailscaleConnected ? AppTheme.running : Color.black)
                        .frame(width: 44, height: 44)

                    Image(systemName: servicesStore.isTailscaleConnected ? "shield.checkered" : "network.badge.shield.half.filled")
                        .font(.title3)
                        .foregroundStyle(.white)
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
                ForEach(visibleTypes) { type in
                    let instances = servicesStore.instances(for: type)
                    if instances.isEmpty {
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
                    } else {
                        ForEach(instances) { instance in
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
    }

    private var footerSection: some View {
        Text("\(localizer.t.launcherServices) • \(servicesStore.connectedCount) \(localizer.t.launcherConnected.sentenceCased())")
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
        case .beszel:            BeszelDashboard(instanceId: route.instanceId)
        case .gitea:             GiteaDashboard(instanceId: route.instanceId)
        case .nginxProxyManager: NpmDashboard(instanceId: route.instanceId)
        }
    }

    // MARK: - Summary Data Fetching

    private func fetchAllSummaryData() async {
        summaryLoading = true
        defer { summaryLoading = false }

        await withTaskGroup(of: (UUID, ServiceSummaryInfo?).self) { group in
            for type in ServiceType.allCases {
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
            case .beszel:
                guard let client = await servicesStore.beszelClient(instanceId: instanceId) else { return nil }
                let response = try await client.getSystems()
                let online = response.items.filter { $0.isOnline }.count
                return ServiceSummaryInfo(value: "\(online)", subValue: "/ \(response.items.count)", label: localizer.t.summarySystemsOnline)
            case .gitea:
                guard let client = await servicesStore.giteaClient(instanceId: instanceId) else { return nil }
                let repos = try await client.getUserRepos(page: 1, limit: 100)
                return ServiceSummaryInfo(value: "\(repos.count)", label: localizer.t.giteaRepos)
            case .nginxProxyManager:
                guard let client = await servicesStore.npmClient(instanceId: instanceId) else { return nil }
                let report = try await client.getHostReport()
                return ServiceSummaryInfo(value: "\(report.proxy)", subValue: "/ \(report.total)", label: localizer.t.npmProxyHosts)
            }
        } catch {
            return nil
        }
    }
}

private struct HomeServiceRoute: Hashable {
    let type: ServiceType
    let instanceId: UUID
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
                ForEach(settingsStore.serviceOrder) { type in
                    HStack {
                        Text(type.displayName)
                            .font(.body.weight(.semibold))
                        Spacer()
                        HStack(spacing: 12) {
                            Button {
                                settingsStore.moveService(type, offset: -1)
                            } label: {
                                Image(systemName: "chevron.up")
                            }
                            .buttonStyle(.borderless)
                            .disabled(!settingsStore.canMoveService(type, offset: -1))
                            .accessibilityLabel(localizer.t.settingsMoveUp)

                            Button {
                                settingsStore.moveService(type, offset: 1)
                            } label: {
                                Image(systemName: "chevron.down")
                            }
                            .buttonStyle(.borderless)
                            .disabled(!settingsStore.canMoveService(type, offset: 1))
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
    
    private var cardTint: Color? {
        if isConnected && reachable == false {
            return type.colors.primary.opacity(0.06)
        }
        return nil
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
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
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: 110, alignment: .trailing)
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

                if isPreferred {
                    Image(systemName: "star.fill")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(type.colors.primary)
                        .padding(5)
                        .background(type.colors.primary.opacity(0.12), in: Circle())
                        .accessibilityLabel(t.badgeDefault)
                }
            }
        }
        .frame(maxWidth: .infinity, minHeight: 140, alignment: .leading)
        .padding(14)
        .contentShape(Rectangle())
        .glassCard(tint: cardTint)
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
                Circle().fill(type.colors.primary).frame(width: 6, height: 6)
                Text(t.statusUnreachable)
                    .font(.caption2.bold())
                    .foregroundStyle(type.colors.primary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(type.colors.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        } else if reachable == true {
            HStack(spacing: 5) {
                Circle().fill(type.colors.primary).frame(width: 6, height: 6)
                Text(t.statusOnline)
                    .font(.caption2.bold())
                    .foregroundStyle(type.colors.primary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(type.colors.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
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

    @State private var index: Int = 0

    var body: some View {
        ZStack {
            if type.iconCandidates.indices.contains(index) {
                AsyncImage(url: type.iconCandidates[index]) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .renderingMode(.original)
                            .scaledToFit()
                    case .failure:
                        if index < type.iconCandidates.count - 1 {
                            Color.clear
                                .onAppear { index += 1 }
                        } else {
                            fallbackView
                        }
                    default:
                        ProgressView()
                            .scaleEffect(0.7)
                            .tint(type.colors.primary)
                    }
                }
                .id(index)
            } else {
                fallbackView
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private var fallbackView: some View {
        Image(systemName: type.symbolName)
            .font(.system(size: size * 0.6))
            .foregroundStyle(type.colors.primary)
    }
}

