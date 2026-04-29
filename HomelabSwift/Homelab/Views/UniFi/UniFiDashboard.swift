import SwiftUI
import Charts

struct UniFiDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scenePhase) private var scenePhase

    @State private var selectedInstanceId: UUID
    @State private var selectedSiteId: String?
    @State private var data: UniFiDashboardData?
    @State private var state: LoadableState<Void> = .idle
    @State private var isPreview = false
    @State private var isViewVisible = false
    @State private var isFetchingDashboard = false
    @State private var selectedWANDate: Date?

    private let color = ServiceType.unifiNetwork.colors.primary

    init(instanceId: UUID, _previewData: UniFiDashboardData? = nil) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
        if let preview = _previewData {
            _data = State(initialValue: preview)
            _state = State(initialValue: .loaded(()))
            _isPreview = State(initialValue: true)
        }
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .unifiNetwork,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(forceLoading: false) }
        ) {
            instancePicker

            if let data {
                let scoped = data.scoped(to: selectedSiteId)
                if data.sites.count > 1 {
                    siteFilterBar(data.sites)
                }
                heroCard(scoped, totalSiteCount: data.sites.count)
                alertsRow(scoped)
                operationsBoard(scoped)
                if !scoped.ispMetrics.isEmpty { wanHealthCard(scoped) }
                overviewBoard(scoped)
                if scoped.mode == .localNetwork, !scoped.devices.isEmpty {
                    fabricBoard(scoped)
                }
                if scoped.mode == .localNetwork, !scoped.clients.isEmpty {
                    clientExperienceBoard(scoped)
                }
                if selectedSiteId == nil, data.sites.count > 1 {
                    siteDistributionCard(data.sites)
                }
                if scoped.mode == .localNetwork, !scoped.devices.isEmpty {
                    topologyPreviewCard(scoped)
                }
                if scoped.mode == .localNetwork,
                   scoped.devices.contains(where: { $0.liveTrafficBytesPerSecond != nil }) || scoped.clients.contains(where: { $0.liveTrafficBytesPerSecond != nil }) {
                    liveTrafficCard(scoped)
                }
                devicesNavCard(scoped, sites: scoped.sites)
                if scoped.mode == .localNetwork { clientsNavCard(scoped, sites: scoped.sites) }
                if selectedSiteId == nil || scoped.sites.count > 1 {
                    sitesSection(scoped.sites)
                }
                if scoped.mode == .localNetwork && !scoped.networks.isEmpty { networksSection(scoped.networks) }
                if !scoped.hosts.isEmpty { hostsSection(scoped.hosts) }
            }
        }
        .navigationTitle(ServiceType.unifiNetwork.displayName)
        .task { await fetchDashboard() }
        .onAppear { isViewVisible = true }
        .onDisappear { isViewVisible = false }
        .onChange(of: selectedInstanceId) { _, _ in
            data = nil
            selectedSiteId = nil
            selectedWANDate = nil
            Task { await fetchDashboard() }
        }
        .task(id: autoRefreshTaskKey) {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(autoRefreshInterval))
                guard !Task.isCancelled else { break }
                guard scenePhase == .active, isViewVisible, !isPreview else { continue }
                await fetchDashboard(forceLoading: false)
            }
        }
    }

    // MARK: - Instance Picker

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .unifiNetwork)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 10) {
                    Text(localizer.t.dashboardInstances.sentenceCased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .unifiNetwork)
                            data = nil
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? color : AppTheme.textMuted.opacity(0.4))
                                    .frame(width: 10, height: 10)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(instance.displayLabel)
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(.primary)
                                    Text(instance.url)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                            }
                            .padding(14)
                            .glassCard(tint: instance.id == selectedInstanceId ? color.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Site Filter

    private func siteFilterBar(_ sites: [UniFiSite]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                siteChip(
                    title: localizer.t.unifiAllSites,
                    subtitle: "\(sites.count) \(localizer.t.unifiSites.lowercased())",
                    selected: selectedSiteId == nil
                ) {
                    selectedWANDate = nil
                    selectedSiteId = nil
                }

                ForEach(sites) { site in
                    siteChip(
                        title: site.displayName,
                        subtitle: "\(site.totalClients) \(localizer.t.unifiClients.lowercased())",
                        selected: selectedSiteId == site.siteId
                    ) {
                        selectedWANDate = nil
                        selectedSiteId = site.siteId
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func siteChip(title: String, subtitle: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button {
            HapticManager.light()
            action()
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.caption.bold())
                    .foregroundStyle(selected ? .white : .primary)
                Text(subtitle)
                    .font(.caption2)
                    .foregroundStyle(selected ? Color.white.opacity(0.8) : AppTheme.textMuted)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(selected ? color : color.opacity(colorScheme == .dark ? 0.14 : 0.09))
            )
        }
        .buttonStyle(.plain)
    }

    // MARK: - Hero Card

    private func heroCard(_ data: UniFiDashboardData, totalSiteCount: Int) -> some View {
        let currentSite = data.primarySite
        let latestWAN = data.latestWAN?.wan

        return VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 14) {
                ZStack {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(color.opacity(colorScheme == .dark ? 0.24 : 0.12))
                    ServiceIconView(type: .unifiNetwork, size: 34)
                }
                .frame(width: 62, height: 62)

                VStack(alignment: .leading, spacing: 5) {
                    Text(heroTitle(data, totalSiteCount: totalSiteCount))
                        .font(.title2.bold())
                        .foregroundStyle(.primary)
                    Text(data.mode == .siteManager ? localizer.t.unifiSiteManager : localizer.t.unifiLocalNetwork)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                    if let isp = data.primarySite?.ispName ?? data.latestWAN?.wan?.ispName {
                        Text(isp)
                            .font(.caption.bold())
                            .foregroundStyle(color)
                            .lineLimit(1)
                    }
                }

                Spacer()

                statusPill(data.offlineDevices == 0 && data.criticalAlerts == 0)
            }

            if let currentSite {
                heroContextRow(site: currentSite, mode: data.mode)
            }

            Divider().opacity(0.35)

            HStack(spacing: 0) {
                miniMetric(localizer.t.unifiDevices, value: "\(data.onlineDevices)/\(max(data.totalDevices, data.onlineDevices))", icon: "antenna.radiowaves.left.and.right")
                Divider().frame(height: 36).opacity(0.35)
                miniMetric(localizer.t.unifiClients, value: "\(data.totalClients)", icon: "person.2.fill")
                Divider().frame(height: 36).opacity(0.35)
                miniMetric(localizer.t.unifiSites, value: "\(data.sites.count)", icon: "square.grid.2x2.fill")
            }

            if let latestWAN {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    heroTelemetryTile(localizer.t.unifiLatency, value: formatMilliseconds(latestWAN.avgLatency), tint: color)
                    heroTelemetryTile(localizer.t.unifiUptime, value: formatPercent(latestWAN.uptime), tint: AppTheme.running)
                    heroTelemetryTile(localizer.t.unifiDownload, value: formatMbps(latestWAN.downloadKbps), tint: AppTheme.running)
                    heroTelemetryTile(localizer.t.unifiUpload, value: formatMbps(latestWAN.uploadKbps), tint: color)
                }
            }
        }
        .padding(18)
        .glassCard(tint: color.opacity(colorScheme == .dark ? 0.13 : 0.07))
    }

    private func heroTitle(_ data: UniFiDashboardData, totalSiteCount: Int) -> String {
        if let selectedSiteId, let site = data.sites.first(where: { $0.siteId == selectedSiteId }) {
            return site.displayName
        }
        if totalSiteCount > 1 {
            return currentInstance?.displayLabel ?? ServiceType.unifiNetwork.displayName
        }
        return data.primarySite?.displayName ?? currentInstance?.displayLabel ?? ServiceType.unifiNetwork.displayName
    }

    // MARK: - Alerts Row

    @ViewBuilder
    private func alertsRow(_ data: UniFiDashboardData) -> some View {
        if data.pendingUpdates > 0 || data.criticalAlerts > 0 || data.unauthorizedGuests > 0 {
            HStack(spacing: 10) {
                if data.pendingUpdates > 0 {
                    alertChip(String(format: localizer.t.unifiPendingUpdatesFormat, data.pendingUpdates),
                              icon: "arrow.up.circle.fill", color: AppTheme.info)
                }
                if data.criticalAlerts > 0 {
                    alertChip(String(format: localizer.t.unifiCriticalAlertsFormat, data.criticalAlerts),
                              icon: "exclamationmark.triangle.fill", color: AppTheme.warning)
                }
                if data.unauthorizedGuests > 0 {
                    alertChip(String(format: localizer.t.unifiUnauthorizedGuestsFormat, data.unauthorizedGuests),
                              icon: "person.badge.clock.fill", color: AppTheme.warning)
                }
                Spacer()
            }
        }
    }

    private func alertChip(_ text: String, icon: String, color: Color) -> some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.caption.bold())
            Text(text)
                .font(.caption.bold())
        }
        .foregroundStyle(color)
        .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(color.opacity(0.12), in: Capsule())
    }

    private func heroContextRow(site: UniFiSite, mode: UniFiAuthMode) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if let gateway = site.gatewayName {
                    heroMetaPill(icon: "router.fill", text: gateway, tint: color)
                }
                if let timezone = site.meta?.timezone {
                    heroMetaPill(icon: "clock.fill", text: timezone, tint: AppTheme.info)
                }
                heroMetaPill(
                    icon: mode == .siteManager ? "building.2.fill" : "network",
                    text: mode == .siteManager ? localizer.t.unifiSiteManager : localizer.t.unifiLocalNetwork,
                    tint: AppTheme.textSecondary
                )
            }
        }
    }

    private func heroMetaPill(icon: String, text: String, tint: Color) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption2.bold())
            Text(text)
                .font(.caption.bold())
                .lineLimit(1)
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(tint.opacity(colorScheme == .dark ? 0.12 : 0.08), in: Capsule())
    }

    private func heroTelemetryTile(_ title: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundStyle(tint)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    // MARK: - Operations Board

    private func operationsBoard(_ data: UniFiDashboardData) -> some View {
        let latestWAN = data.latestWAN?.wan
        let ports = data.devices.flatMap(\.ports)
        let activePorts = ports.filter { $0.up == true }.count
        let poeDraw = ports.compactMap(\.poePowerWatts).reduce(0, +)
        let liveTraffic = data.devices.compactMap(\.liveTrafficBytesPerSecond).reduce(0, +)
            + data.clients.compactMap(\.liveTrafficBytesPerSecond).reduce(0, +)
        let wirelessQuality = averageInt(data.clients.compactMap(\.wifiExperience))
        let attentionCount = data.offlineDevices + data.pendingUpdates + data.criticalAlerts + data.unauthorizedGuests

        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiOperations.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 10) {
                    operationTile(
                        title: localizer.t.unifiWan,
                        value: latestWAN.map { formatMilliseconds($0.avgLatency) } ?? data.primarySite?.statistics?.percentages?.wanUptime.map { formatPercent($0) } ?? localizer.t.notAvailable,
                        subtitle: latestWAN.map { "\(localizer.t.unifiPacketLoss): \(formatPercent($0.packetLoss))" } ?? localizer.t.unifiInternetHealth,
                        icon: "network",
                        tint: (latestWAN?.packetLoss ?? 0) > 0 ? AppTheme.warning : color
                    )

                    operationTile(
                        title: localizer.t.unifiDevices,
                        value: "\(data.onlineDevices)/\(max(data.totalDevices, data.onlineDevices))",
                        subtitle: data.pendingUpdates > 0 ? String(format: localizer.t.unifiPendingUpdatesFormat, data.pendingUpdates) : localizer.t.statusOnline,
                        icon: "server.rack",
                        tint: data.offlineDevices > 0 ? AppTheme.warning : AppTheme.running
                    )
                }

                HStack(alignment: .top, spacing: 10) {
                    operationTile(
                        title: localizer.t.unifiClients,
                        value: "\(data.totalClients)",
                        subtitle: "\(data.wirelessClients) \(localizer.t.unifiWifiClients.lowercased())  •  \(data.wiredClients) \(localizer.t.unifiWiredClients.lowercased())",
                        icon: "person.2.wave.2.fill",
                        tint: wirelessQuality.map(qualityTint(for:)) ?? AppTheme.info
                    )

                    operationTile(
                        title: localizer.t.unifiLan,
                        value: data.mode == .localNetwork ? "\(activePorts)" : "\(data.hosts.count)",
                        subtitle: data.mode == .localNetwork ? "\(data.networks.count) \(localizer.t.unifiNetworks.lowercased())  •  \(poeDraw > 0 ? String(format: "%.1f W PoE", poeDraw) : rateString(liveTraffic))" : "\(data.sites.count) \(localizer.t.unifiSites.lowercased())",
                        icon: "point.3.connected.trianglepath.dotted",
                        tint: AppTheme.info
                    )
                }

                if attentionCount > 0 {
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.warning)
                        Text("\(attentionCount) \(localizer.t.unifiNeedsAttention.lowercased())")
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.warning)
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                    .background(AppTheme.warning.opacity(colorScheme == .dark ? 0.13 : 0.08), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
            }
            .padding(16)
            .glassCard(tint: color.opacity(colorScheme == .dark ? 0.08 : 0.05))
        }
    }

    // MARK: - Overview Board

    private func overviewBoard(_ data: UniFiDashboardData) -> some View {
        let aps = data.devices.filter { UniFiDeviceClassifier.isAP($0) }.count
        let switches = data.devices.filter { UniFiDeviceClassifier.isSwitch($0) }.count
        let gateways = data.devices.filter { UniFiDeviceClassifier.isGateway($0) }.count

        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.summaryTitle.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 16) {
                ViewThatFits(in: .vertical) {
                    HStack(spacing: 12) {
                        overviewPanel(title: localizer.t.unifiClients, icon: "person.3.fill") {
                            overviewSplitRow(
                                label: localizer.t.unifiWifiClients,
                                value: data.wirelessClients,
                                total: max(data.totalClients, 1),
                                tint: color
                            )
                            overviewSplitRow(
                                label: localizer.t.unifiWiredClients,
                                value: data.wiredClients,
                                total: max(data.totalClients, 1),
                                tint: AppTheme.running
                            )
                        }

                        overviewPanel(title: localizer.t.unifiDevices, icon: "switch.2") {
                            overviewStatRow(label: localizer.t.unifiAPs, value: aps, tint: Color(hex: "#2563EB"))
                            overviewStatRow(label: localizer.t.unifiSwitches, value: switches, tint: AppTheme.info)
                            overviewStatRow(label: localizer.t.unifiGateways, value: gateways, tint: color)
                        }
                    }

                    VStack(spacing: 12) {
                        overviewPanel(title: localizer.t.unifiClients, icon: "person.3.fill") {
                            overviewSplitRow(
                                label: localizer.t.unifiWifiClients,
                                value: data.wirelessClients,
                                total: max(data.totalClients, 1),
                                tint: color
                            )
                            overviewSplitRow(
                                label: localizer.t.unifiWiredClients,
                                value: data.wiredClients,
                                total: max(data.totalClients, 1),
                                tint: AppTheme.running
                            )
                        }

                        overviewPanel(title: localizer.t.unifiDevices, icon: "switch.2") {
                            overviewStatRow(label: localizer.t.unifiAPs, value: aps, tint: Color(hex: "#2563EB"))
                            overviewStatRow(label: localizer.t.unifiSwitches, value: switches, tint: AppTheme.info)
                            overviewStatRow(label: localizer.t.unifiGateways, value: gateways, tint: color)
                        }
                    }
                }

                if data.pendingUpdates > 0 || data.criticalAlerts > 0 || data.unauthorizedGuests > 0 {
                    HStack(spacing: 8) {
                        if data.pendingUpdates > 0 {
                            alertChip(
                                String(format: localizer.t.unifiPendingUpdatesFormat, data.pendingUpdates),
                                icon: "arrow.up.circle.fill",
                                color: AppTheme.info
                            )
                        }
                        if data.criticalAlerts > 0 {
                            alertChip(
                                String(format: localizer.t.unifiCriticalAlertsFormat, data.criticalAlerts),
                                icon: "exclamationmark.triangle.fill",
                                color: AppTheme.warning
                            )
                        }
                        if data.unauthorizedGuests > 0 {
                            alertChip(
                                String(format: localizer.t.unifiUnauthorizedGuestsFormat, data.unauthorizedGuests),
                                icon: "person.badge.clock.fill",
                                color: AppTheme.warning
                            )
                        }
                    }
                }

                HStack(spacing: 10) {
                    statCapsule("\(data.sites.count)", label: localizer.t.unifiSites, tint: color)
                    if data.mode == .localNetwork {
                        statCapsule("\(data.networks.count)", label: localizer.t.unifiNetworks, tint: AppTheme.running)
                    }
                    if !data.hosts.isEmpty {
                        statCapsule("\(data.hosts.count)", label: localizer.t.unifiHosts, tint: AppTheme.info)
                    }
                }
            }
            .padding(16)
            .glassCard(tint: color.opacity(colorScheme == .dark ? 0.08 : 0.05))
        }
    }

    // MARK: - Fabric Board

    private func fabricBoard(_ data: UniFiDashboardData) -> some View {
        let radios = data.devices.flatMap(\.radios)
        let radioQualities = radios.compactMap(\.satisfaction)
        let averageQuality = radioQualities.isEmpty ? nil : Int((radioQualities.reduce(0, +) / Double(radioQualities.count)).rounded())
        let apCount = data.devices.filter { UniFiDeviceClassifier.isAP($0) }.count
        let ports = data.devices.flatMap(\.ports)
        let poePorts = ports.filter { ($0.poePowerWatts ?? 0) > 0 }
        let poeCapablePorts = ports.filter { $0.poeEnabled == true || $0.poePowerWatts != nil || $0.poeMode != nil }
        let poeDraw = poePorts.compactMap(\.poePowerWatts).reduce(0, +)
        let uplinks = ports.filter { $0.isUplink == true }
        let activeUplinks = uplinks.filter { $0.up == true }
        let fastestUplink = uplinks.compactMap(\.speedMbps).max()
        let uplinkTraffic = uplinks.compactMap(\.liveTrafficBytesPerSecond).reduce(0, +)

        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiFabric.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            ViewThatFits(in: .vertical) {
                HStack(spacing: 12) {
                    fabricPanel(
                        title: localizer.t.unifiCoverage,
                        value: averageQuality.map { "\($0)%" } ?? localizer.t.notAvailable,
                        subtitle: "\(apCount) \(localizer.t.unifiAPs)  •  \(radios.count) \(localizer.t.unifiWifiRadios.lowercased())",
                        tint: qualityTint(for: averageQuality),
                        icon: "wifi",
                        progress: averageQuality.map { Double($0) / 100.0 }
                    )

                    fabricPanel(
                        title: "PoE",
                        value: poeDraw > 0 ? String(format: "%.1f W", poeDraw) : localizer.t.notAvailable,
                        subtitle: "\(poePorts.count) / \(max(poeCapablePorts.count, poePorts.count)) \(localizer.t.unifiPorts.lowercased())",
                        tint: Color(hex: "#F59E0B"),
                        icon: "bolt.fill",
                        progress: poeCapablePorts.isEmpty ? nil : Double(poePorts.count) / Double(max(poeCapablePorts.count, 1))
                    )

                    fabricPanel(
                        title: localizer.t.unifiUplinks,
                        value: "\(activeUplinks.count)",
                        subtitle: "\(formatLinkSpeed(fastestUplink))  •  \(uplinkTraffic > 0 ? rateString(uplinkTraffic) : localizer.t.unifiThroughput.lowercased())",
                        tint: AppTheme.info,
                        icon: "point.topleft.down.curvedto.point.bottomright.up.fill",
                        progress: nil
                    )
                }

                VStack(spacing: 12) {
                    fabricPanel(
                        title: localizer.t.unifiCoverage,
                        value: averageQuality.map { "\($0)%" } ?? localizer.t.notAvailable,
                        subtitle: "\(apCount) \(localizer.t.unifiAPs)  •  \(radios.count) \(localizer.t.unifiWifiRadios.lowercased())",
                        tint: qualityTint(for: averageQuality),
                        icon: "wifi",
                        progress: averageQuality.map { Double($0) / 100.0 }
                    )

                    fabricPanel(
                        title: "PoE",
                        value: poeDraw > 0 ? String(format: "%.1f W", poeDraw) : localizer.t.notAvailable,
                        subtitle: "\(poePorts.count) / \(max(poeCapablePorts.count, poePorts.count)) \(localizer.t.unifiPorts.lowercased())",
                        tint: Color(hex: "#F59E0B"),
                        icon: "bolt.fill",
                        progress: poeCapablePorts.isEmpty ? nil : Double(poePorts.count) / Double(max(poeCapablePorts.count, 1))
                    )

                    fabricPanel(
                        title: localizer.t.unifiUplinks,
                        value: "\(activeUplinks.count)",
                        subtitle: "\(formatLinkSpeed(fastestUplink))  •  \(uplinkTraffic > 0 ? rateString(uplinkTraffic) : localizer.t.unifiThroughput.lowercased())",
                        tint: AppTheme.info,
                        icon: "point.topleft.down.curvedto.point.bottomright.up.fill",
                        progress: nil
                    )
                }
            }
        }
    }

    // MARK: - Client Experience

    private func clientExperienceBoard(_ data: UniFiDashboardData) -> some View {
        let wirelessClients = data.clients.filter {
            $0.type?.uppercased() == "WIRELESS" || $0.accessPointName != nil || $0.signalStrength != nil || $0.wifiExperience != nil
        }
        let averageExperience = averageInt(wirelessClients.compactMap(\.wifiExperience))
        let averageSignal = averageInt(wirelessClients.compactMap { $0.signalStrength.map(Double.init) })
        let busiestClient = data.clients
            .compactMap { client -> (UniFiClient, Double)? in
                guard let total = client.liveTrafficBytesPerSecond else { return nil }
                return (client, total)
            }
            .sorted { $0.1 > $1.1 }
            .first
        let rankedAPs = data.devices
            .filter { UniFiDeviceClassifier.isAP($0) }
            .sorted { lhs, rhs in
                let lhsLoad = lhs.clientCount ?? lhs.radios.compactMap(\.clientCount).reduce(0, +)
                let rhsLoad = rhs.clientCount ?? rhs.radios.compactMap(\.clientCount).reduce(0, +)
                if lhsLoad != rhsLoad {
                    return lhsLoad > rhsLoad
                }
                let lhsQuality = averageInt(lhs.radios.compactMap(\.satisfaction)) ?? 0
                let rhsQuality = averageInt(rhs.radios.compactMap(\.satisfaction)) ?? 0
                return lhsQuality > rhsQuality
            }
        let totalGuests = data.clients.filter(\.isGuestUnauthorized).count

        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiClientExperience.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 16) {
                ViewThatFits(in: .vertical) {
                    HStack(spacing: 12) {
                        fabricPanel(
                            title: localizer.t.unifiQuality,
                            value: averageExperience.map { "\($0)%" } ?? localizer.t.notAvailable,
                            subtitle: "\(wirelessClients.count) \(localizer.t.unifiWifiClients.lowercased())  •  \(totalGuests) \(localizer.t.unifiGuestUnauthorized.lowercased())",
                            tint: qualityTint(for: averageExperience),
                            icon: "wifi.circle.fill",
                            progress: averageExperience.map { Double($0) / 100.0 }
                        )

                        fabricPanel(
                            title: localizer.t.unifiSignal,
                            value: averageSignal.map { "\($0) dBm" } ?? localizer.t.notAvailable,
                            subtitle: rankedAPs.first.map { "\($0.displayName)  •  \(($0.clientCount ?? $0.radios.compactMap(\.clientCount).reduce(0, +))) \(localizer.t.unifiClients.lowercased())" } ?? localizer.t.unifiNoClients,
                            tint: signalTint(for: averageSignal),
                            icon: "dot.radiowaves.left.and.right",
                            progress: signalProgress(for: averageSignal)
                        )
                    }

                    VStack(spacing: 12) {
                        fabricPanel(
                            title: localizer.t.unifiQuality,
                            value: averageExperience.map { "\($0)%" } ?? localizer.t.notAvailable,
                            subtitle: "\(wirelessClients.count) \(localizer.t.unifiWifiClients.lowercased())  •  \(totalGuests) \(localizer.t.unifiGuestUnauthorized.lowercased())",
                            tint: qualityTint(for: averageExperience),
                            icon: "wifi.circle.fill",
                            progress: averageExperience.map { Double($0) / 100.0 }
                        )

                        fabricPanel(
                            title: localizer.t.unifiSignal,
                            value: averageSignal.map { "\($0) dBm" } ?? localizer.t.notAvailable,
                            subtitle: rankedAPs.first.map { "\($0.displayName)  •  \(($0.clientCount ?? $0.radios.compactMap(\.clientCount).reduce(0, +))) \(localizer.t.unifiClients.lowercased())" } ?? localizer.t.unifiNoClients,
                            tint: signalTint(for: averageSignal),
                            icon: "dot.radiowaves.left.and.right",
                            progress: signalProgress(for: averageSignal)
                        )
                    }
                }

                if let busiestClient {
                    HStack(spacing: 12) {
                        iconBox("bolt.horizontal.circle.fill", tint: color)
                        VStack(alignment: .leading, spacing: 4) {
                            Text(busiestClient.0.displayName)
                                .font(.subheadline.bold())
                                .lineLimit(1)
                            let details = [busiestClient.0.accessPointName, busiestClient.0.networkName]
                                .compactMap { value -> String? in
                                    let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines)
                                    guard let trimmed, !trimmed.isEmpty else { return nil }
                                    return trimmed
                                }
                                .joined(separator: "  •  ")
                            if !details.isEmpty {
                                Text(details)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 4) {
                            Text(rateString(busiestClient.1))
                                .font(.subheadline.bold())
                                .foregroundStyle(color)
                            if let experience = busiestClient.0.wifiExperience {
                                Text("\(Int(experience.rounded()))% \(localizer.t.unifiQuality.lowercased())")
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }
                    }
                    .padding(14)
                    .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                }

                if !rankedAPs.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(localizer.t.unifiWifiRadios)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)

                        ForEach(Array(rankedAPs.prefix(3))) { accessPoint in
                            accessPointQualityRow(accessPoint)
                        }
                    }
                }
            }
            .padding(16)
            .glassCard(tint: color.opacity(colorScheme == .dark ? 0.08 : 0.05))
        }
    }

    // MARK: - Site Distribution

    private func siteDistributionCard(_ sites: [UniFiSite]) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(localizer.t.unifiSites.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Text(localizer.t.unifiSiteDistribution)
                        .font(.headline.bold())
                    Spacer()
                    Text("\(sites.count)")
                        .font(.caption.bold())
                        .foregroundStyle(color)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(color.opacity(0.12), in: Capsule())
                }

                Chart(sites) { site in
                    BarMark(
                        x: .value(localizer.t.unifiSites, site.displayName),
                        y: .value(localizer.t.unifiClients, site.totalClients)
                    )
                    .foregroundStyle(color.gradient)
                    .position(by: .value("Metric", localizer.t.unifiClients))
                    .cornerRadius(6)

                    BarMark(
                        x: .value(localizer.t.unifiSites, site.displayName),
                        y: .value(localizer.t.unifiDevices, max((site.counts.totalDevice ?? 0) - site.offlineDevices, 0))
                    )
                    .foregroundStyle(AppTheme.running.gradient)
                    .position(by: .value("Metric", localizer.t.unifiDevices))
                    .cornerRadius(6)
                }
                .frame(height: 180)
                .chartYAxis { AxisMarks(position: .leading) }

                VStack(spacing: 10) {
                    ForEach(sites) { site in
                        HStack(spacing: 10) {
                            iconBox("building.2.fill", tint: selectedSiteId == site.siteId ? color : AppTheme.info)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(site.displayName)
                                    .font(.subheadline.weight(.semibold))
                                Text("\(site.totalClients) \(localizer.t.unifiClients.lowercased())  •  \(site.counts.totalDevice ?? 0) \(localizer.t.unifiDevices.lowercased())")
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            Spacer()
                            if site.statistics?.percentages?.wanUptime != nil {
                                Text(Formatters.formatPercent(site.statistics?.percentages?.wanUptime ?? 0))
                                    .font(.caption.bold())
                                    .foregroundStyle(AppTheme.running)
                            } else if site.offlineDevices > 0 {
                                Text("\(site.offlineDevices)")
                                    .font(.caption.bold())
                                    .foregroundStyle(AppTheme.warning)
                            }
                        }
                    }
                }
            }
            .padding(16)
            .glassCard(tint: color.opacity(colorScheme == .dark ? 0.08 : 0.05))
        }
    }

    // MARK: - Live Traffic

    private func liveTrafficCard(_ data: UniFiDashboardData) -> some View {
        let rankedDevices = data.devices
            .compactMap { device -> (UniFiDevice, Double)? in
                guard let total = device.liveTrafficBytesPerSecond else { return nil }
                return (device, total)
            }
            .sorted { $0.1 > $1.1 }
        let rankedClients = data.clients
            .compactMap { client -> (UniFiClient, Double)? in
                guard let total = client.liveTrafficBytesPerSecond else { return nil }
                return (client, total)
            }
            .sorted { $0.1 > $1.1 }
        let topDevices = Array(rankedDevices.prefix(4))
        let topClients = Array(rankedClients.prefix(4))
        let aggregateTraffic = topDevices.map(\.1).reduce(0, +) + topClients.map(\.1).reduce(0, +)

        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiThroughput.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 14) {
                cardHeader(localizer.t.unifiTrafficNow, icon: "bolt.horizontal.circle.fill")

                if aggregateTraffic > 0 {
                    HStack(spacing: 8) {
                        statCapsule(rateString(aggregateTraffic), label: localizer.t.unifiThroughput, tint: color)
                        statCapsule("\(topDevices.count)", label: localizer.t.unifiDevices, tint: AppTheme.info)
                        if !topClients.isEmpty {
                            statCapsule("\(topClients.count)", label: localizer.t.unifiClients, tint: AppTheme.running)
                        }
                    }
                }

                if !topDevices.isEmpty {
                    trafficLaneSection(title: localizer.t.unifiDevices, tint: color) {
                        ForEach(topDevices, id: \.0.id) { lane in
                            trafficDeviceRow(device: lane.0, value: lane.1, maximum: topDevices.first?.1 ?? lane.1)
                        }
                    }
                }

                if !topClients.isEmpty {
                    trafficLaneSection(title: localizer.t.unifiClients, tint: AppTheme.running) {
                        ForEach(topClients, id: \.0.id) { lane in
                            trafficClientRow(client: lane.0, value: lane.1, maximum: topClients.first?.1 ?? lane.1)
                        }
                    }
                }
            }
            .padding(16)
            .glassCard(tint: color.opacity(colorScheme == .dark ? 0.08 : 0.05))
        }
    }

    // MARK: - Topology Preview

    private func topologyPreviewCard(_ data: UniFiDashboardData) -> some View {
        let gateway = data.devices.first(where: { UniFiDeviceClassifier.isGateway($0) }) ?? data.devices.first
        let children = topologyChildren(for: gateway, in: data.devices)

        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiTopology.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 14) {
                cardHeader(localizer.t.unifiTopology, icon: "point.3.connected.trianglepath.dotted")

                if let gateway {
                    VStack(spacing: 18) {
                        topologyNode(gateway, emphasis: true)
                            .frame(maxWidth: .infinity)

                        GeometryReader { geometry in
                            let nodeCount = max(children.count, 1)
                            let segment = geometry.size.width / CGFloat(nodeCount)

                            ZStack(alignment: .topLeading) {
                                ForEach(Array(children.enumerated()), id: \.element.id) { index, child in
                                    let x = segment * (CGFloat(index) + 0.5)

                                    Path { path in
                                        path.move(to: CGPoint(x: geometry.size.width / 2, y: 8))
                                        path.addLine(to: CGPoint(x: x, y: 62))
                                    }
                                    .stroke(
                                        child.liveTrafficBytesPerSecond != nil ? color.opacity(0.45) : AppTheme.textMuted.opacity(0.22),
                                        style: StrokeStyle(lineWidth: 1.5, lineCap: .round, dash: child.liveTrafficBytesPerSecond != nil ? [] : [5, 5])
                                    )

                                    topologyNode(child)
                                        .frame(width: min(110, segment - 8))
                                        .position(x: x, y: 88)
                                }
                            }
                        }
                        .frame(height: children.isEmpty ? 0 : 132)

                        HStack(spacing: 10) {
                            statCapsule("\(data.onlineDevices)", label: localizer.t.unifiDevices, tint: AppTheme.running)
                            statCapsule("\(data.totalClients)", label: localizer.t.unifiClients, tint: color)
                            if let aggregateTraffic = data.devices.compactMap(\.liveTrafficBytesPerSecond).reduce(0, +).nilIfZero {
                                statCapsule(rateString(aggregateTraffic), label: localizer.t.unifiThroughput, tint: AppTheme.info)
                            }
                        }

                        if !children.isEmpty {
                            VStack(alignment: .leading, spacing: 10) {
                                ForEach(children) { child in
                                    topologyLinkRow(child)
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
            .glassCard(tint: color.opacity(colorScheme == .dark ? 0.08 : 0.05))
        }
    }

    // MARK: - Devices Nav Card

    private func devicesNavCard(_ data: UniFiDashboardData, sites: [UniFiSite]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiDevices.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            NavigationLink(destination: UniFiDevicesView(instanceId: selectedInstanceId, initialDevices: data.devices, sites: sites, selectedSiteId: selectedSiteId)) {
                HStack(spacing: 14) {
                    ServiceIconView(type: .unifiNetwork, size: 24)
                        .frame(width: 44, height: 44)
                        .background(color.opacity(colorScheme == .dark ? 0.18 : 0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                    VStack(alignment: .leading, spacing: 5) {
                        HStack(spacing: 6) {
                            Text("\(data.onlineDevices) online")
                                .font(.headline.bold())
                                .foregroundStyle(AppTheme.running)
                            Text("/ \(max(data.totalDevices, data.onlineDevices))")
                                .font(.headline)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        deviceTypeSummary(data.devices)
                    }

                    Spacer()

                    HStack(spacing: 8) {
                        if data.pendingUpdates > 0 {
                            Text(String(format: localizer.t.unifiPendingUpdatesFormat, data.pendingUpdates))
                                .font(.caption2.bold())
                                .foregroundStyle(AppTheme.info)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(AppTheme.info.opacity(0.12), in: Capsule())
                        }
                        Image(systemName: "chevron.right")
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
                .padding(16)
                .glassCard()
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    @ViewBuilder
    private func deviceTypeSummary(_ devices: [UniFiDevice]) -> some View {
        let aps = devices.filter { UniFiDeviceClassifier.isAP($0) }.count
        let switches = devices.filter { UniFiDeviceClassifier.isSwitch($0) }.count
        let gateways = devices.filter { UniFiDeviceClassifier.isGateway($0) }.count
        let parts = [
            aps > 0 ? "\(aps) \(localizer.t.unifiAPs)" : nil,
            switches > 0 ? "\(switches) \(localizer.t.unifiSwitches)" : nil,
            gateways > 0 ? "\(gateways) \(localizer.t.unifiGateways)" : nil
        ].compactMap { $0 }
        if !parts.isEmpty {
            Text(parts.joined(separator: "  •  "))
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
        }
    }

    // MARK: - Clients Nav Card

    private func clientsNavCard(_ data: UniFiDashboardData, sites: [UniFiSite]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiClients.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            NavigationLink(destination: UniFiClientsView(instanceId: selectedInstanceId, initialClients: data.clients, sites: sites, selectedSiteId: selectedSiteId)) {
                HStack(spacing: 14) {
                    Image(systemName: "person.2.fill")
                        .font(.body.bold())
                        .foregroundStyle(color)
                        .frame(width: 44, height: 44)
                        .background(color.opacity(colorScheme == .dark ? 0.18 : 0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

                    VStack(alignment: .leading, spacing: 5) {
                        Text("\(data.totalClients) \(localizer.t.unifiClients)")
                            .font(.headline.bold())
                            .foregroundStyle(.primary)
                        Text("\(data.wirelessClients) \(localizer.t.unifiWifiClients)  •  \(data.wiredClients) \(localizer.t.unifiWiredClients)")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }

                    Spacer()

                    HStack(spacing: 8) {
                        if data.unauthorizedGuests > 0 {
                            Text("\(data.unauthorizedGuests) \(localizer.t.unifiGuest)")
                                .font(.caption2.bold())
                                .foregroundStyle(AppTheme.warning)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(AppTheme.warning.opacity(0.12), in: Capsule())
                        }
                        Image(systemName: "chevron.right")
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
                .padding(16)
                .glassCard()
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - WAN Health Card

    private func wanHealthCard(_ data: UniFiDashboardData) -> some View {
        let points = data.ispMetrics.flatMap(\.periods).sorted { $0.date < $1.date }.suffix(96)
        let pointsArray = Array(points)
        let hasThroughput = points.contains { $0.wan?.downloadKbps != nil || $0.wan?.uploadKbps != nil }
        let selectedPoint = wanSelection(in: pointsArray)

        return VStack(alignment: .leading, spacing: 14) {
            Text(localizer.t.unifiInternetHealth.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 16) {
                cardHeader(localizer.t.unifiInternetHealth, icon: "waveform.path.ecg")

                if let latest = (selectedPoint ?? data.latestWAN)?.wan {
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        wanMetricTile(localizer.t.unifiLatency, value: formatMilliseconds(latest.avgLatency), icon: "speedometer", color: color)
                        wanMetricTile(localizer.t.unifiPacketLoss, value: formatPercent(latest.packetLoss), icon: "exclamationmark.triangle", color: AppTheme.warning)
                        wanMetricTile(localizer.t.unifiUptime, value: formatPercent(latest.uptime), icon: "checkmark.circle", color: AppTheme.running)
                    }

                    if hasThroughput {
                        HStack(spacing: 24) {
                            throughputTile("↓ \(localizer.t.unifiDownload)", value: formatMbps(latest.downloadKbps), color: AppTheme.running)
                            throughputTile("↑ \(localizer.t.unifiUpload)", value: formatMbps(latest.uploadKbps), color: color)
                            Spacer()
                        }
                    }
                }

                latencyChart(pointsArray, selectedPoint: selectedPoint)

                if hasThroughput {
                    Divider().opacity(0.35)
                    throughputChart(pointsArray, selectedPoint: selectedPoint)
                }
            }
            .padding(16)
            .glassCard(tint: color.opacity(0.05))
        }
    }

    private func wanMetricTile(_ label: String, value: String, icon: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Image(systemName: icon)
                .font(.caption.bold())
                .foregroundStyle(color)
            Text(value)
                .font(.subheadline.bold())
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(color.opacity(colorScheme == .dark ? 0.1 : 0.06), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func throughputTile(_ label: String, value: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.headline.bold())
                .foregroundStyle(color)
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
    }

    private func latencyChart(_ points: [UniFiISPMetricPoint], selectedPoint: UniFiISPMetricPoint?) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(localizer.t.unifiLatency)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                if let selected = selectedPoint {
                    Text(selected.date.formatted(.dateTime.hour().minute()))
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            Chart(points) { point in
                if let latency = point.wan?.avgLatency {
                    LineMark(x: .value(localizer.t.unifiTime, point.date),
                             y: .value(localizer.t.unifiLatency, latency))
                        .foregroundStyle(color)
                        .interpolationMethod(.catmullRom)
                    AreaMark(x: .value(localizer.t.unifiTime, point.date),
                             y: .value(localizer.t.unifiLatency, latency))
                        .foregroundStyle(color.opacity(0.15).gradient)
                        .interpolationMethod(.catmullRom)
                }
                if selectedPoint?.id == point.id, let latency = point.wan?.avgLatency {
                    RuleMark(x: .value(localizer.t.unifiTime, point.date))
                        .foregroundStyle(Color.white.opacity(0.35))
                    PointMark(x: .value(localizer.t.unifiTime, point.date),
                              y: .value(localizer.t.unifiLatency, latency))
                        .foregroundStyle(color)
                        .symbolSize(54)
                }
            }
            .chartXAxis(.hidden)
            .chartYAxis { AxisMarks(position: .leading) }
            .transaction { $0.animation = nil }
            .chartOverlay { proxy in
                GeometryReader { geometry in
                    Rectangle()
                        .fill(.clear)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 6)
                                .onChanged { value in
                                    updateWANSelection(at: value.location, proxy: proxy, geometry: geometry, points: points)
                                }
                                .onEnded { _ in
                                    selectedWANDate = nil
                                }
                        )
                }
            }
            .frame(height: 100)
        }
    }

    private func throughputChart(_ points: [UniFiISPMetricPoint], selectedPoint: UniFiISPMetricPoint?) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 14) {
                Text(localizer.t.unifiThroughput)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                HStack(spacing: 10) {
                    legendDot(AppTheme.running, label: localizer.t.unifiDownload)
                    legendDot(color, label: localizer.t.unifiUpload)
                }
            }

            Chart(points) { point in
                if let dl = point.wan?.downloadKbps {
                    AreaMark(x: .value(localizer.t.unifiTime, point.date),
                             y: .value(localizer.t.unifiDownload, dl / 1000))
                        .foregroundStyle(AppTheme.running.opacity(0.25).gradient)
                        .interpolationMethod(.catmullRom)
                    LineMark(x: .value(localizer.t.unifiTime, point.date),
                             y: .value(localizer.t.unifiDownload, dl / 1000))
                        .foregroundStyle(AppTheme.running)
                        .interpolationMethod(.catmullRom)
                }
                if let ul = point.wan?.uploadKbps {
                    AreaMark(x: .value(localizer.t.unifiTime, point.date),
                             y: .value(localizer.t.unifiUpload, ul / 1000))
                        .foregroundStyle(color.opacity(0.2).gradient)
                        .interpolationMethod(.catmullRom)
                    LineMark(x: .value(localizer.t.unifiTime, point.date),
                             y: .value(localizer.t.unifiUpload, ul / 1000))
                        .foregroundStyle(color)
                        .interpolationMethod(.catmullRom)
                }
                if selectedPoint?.id == point.id {
                    RuleMark(x: .value(localizer.t.unifiTime, point.date))
                        .foregroundStyle(Color.white.opacity(0.35))
                }
            }
            .chartXAxis(.hidden)
            .chartYAxis { AxisMarks(position: .leading) }
            .transaction { $0.animation = nil }
            .chartOverlay { proxy in
                GeometryReader { geometry in
                    Rectangle()
                        .fill(.clear)
                        .contentShape(Rectangle())
                        .gesture(
                            DragGesture(minimumDistance: 6)
                                .onChanged { value in
                                    updateWANSelection(at: value.location, proxy: proxy, geometry: geometry, points: points)
                                }
                                .onEnded { _ in
                                    selectedWANDate = nil
                                }
                        )
                }
            }
            .frame(height: 100)
        }
    }

    private func legendDot(_ color: Color, label: String) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color).frame(width: 7, height: 7)
            Text(label).font(.caption2).foregroundStyle(AppTheme.textMuted)
        }
    }

    // MARK: - Sites Section

    private func sitesSection(_ sites: [UniFiSite]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiSites.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 0) {
                ForEach(sites) { site in
                    HStack(spacing: 12) {
                        iconBox("building.2.fill", tint: color)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(site.displayName)
                                .font(.subheadline.bold())
                            let meta = [site.gatewayName, site.meta?.timezone].compactMap { $0 }.joined(separator: "  •  ")
                            if !meta.isEmpty {
                                Text(meta)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 3) {
                            Text("\(site.totalClients)")
                                .font(.headline.bold())
                            Text(localizer.t.unifiClients)
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        if site.offlineDevices > 0 {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.caption)
                                .foregroundStyle(AppTheme.warning)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 13)
                    if site.id != sites.last?.id {
                        Divider().padding(.leading, 62).opacity(0.4)
                    }
                }
            }
            .glassCard()
        }
    }

    // MARK: - Networks Section

    private func networksSection(_ networks: [UniFiNetwork]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiNetworks.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 0) {
                ForEach(networks) { network in
                    HStack(spacing: 12) {
                        iconBox(network.isGuestNetwork ? "person.badge.clock.fill" : "network",
                                tint: network.isGuestNetwork ? AppTheme.warning : color)
                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Text(network.displayName)
                                    .font(.subheadline.bold())
                                if network.isGuestNetwork {
                                    Text(localizer.t.unifiGuest)
                                        .font(.caption2.bold())
                                        .foregroundStyle(AppTheme.warning)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(AppTheme.warning.opacity(0.12), in: Capsule())
                                }
                            }
                            let sub = [network.ipSubnet, network.vlanId.map { "\(localizer.t.unifiVlan) \($0)" }]
                                .compactMap { $0 }.joined(separator:  "  •  ")
                            if !sub.isEmpty {
                                Text(sub)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .lineLimit(1)
                            }
                        }
                        Spacer()
                        if network.dhcpEnabled == true {
                            Text(localizer.t.unifiDhcp)
                                .font(.caption2.bold())
                                .foregroundStyle(AppTheme.running)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(AppTheme.running.opacity(0.1), in: Capsule())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 13)
                    if network.id != networks.last?.id {
                        Divider().padding(.leading, 62).opacity(0.4)
                    }
                }
            }
            .glassCard()
        }
    }

    // MARK: - Hosts Section

    private func hostsSection(_ hosts: [UniFiHost]) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.unifiHosts.uppercased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 0) {
                ForEach(hosts) { host in
                    HStack(spacing: 12) {
                        iconBox(host.isConnected ? "checkmark.circle.fill" : "xmark.circle.fill",
                                tint: host.isConnected ? AppTheme.running : AppTheme.danger)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(host.displayName)
                                .font(.subheadline.bold())
                            let detail = [host.ipAddress, host.reportedState?.version].compactMap { $0 }.joined(separator: "  •  ")
                            if !detail.isEmpty {
                                Text(detail)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                            }
                        }
                        Spacer()
                        Text(host.state.capitalized)
                            .font(.caption.bold())
                            .foregroundStyle(host.isConnected ? AppTheme.running : AppTheme.warning)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 13)
                    if host.id != hosts.last?.id {
                        Divider().padding(.leading, 62).opacity(0.4)
                    }
                }
            }
            .glassCard()
        }
    }

    // MARK: - Shared Components

    private var currentInstance: ServiceInstance? {
        servicesStore.instance(id: selectedInstanceId)
    }

    private var currentAuthMode: UniFiAuthMode {
        currentInstance?.unifiAuthMode ?? .siteManager
    }

    private var autoRefreshTaskKey: String {
        "\(selectedInstanceId.uuidString):\(currentAuthMode.rawValue)"
    }

    private var autoRefreshInterval: Double {
        currentAuthMode == .localNetwork ? 30 : 75
    }

    private func cardHeader(_ title: String, icon: String) -> some View {
        HStack(spacing: 10) {
            iconBox(icon, tint: color)
            Text(title)
                .font(.headline.bold())
            Spacer()
        }
    }

    private func iconBox(_ systemName: String, tint: Color) -> some View {
        Image(systemName: systemName)
            .font(.subheadline.bold())
            .foregroundStyle(tint)
            .frame(width: 34, height: 34)
            .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func statusPill(_ ok: Bool) -> some View {
        Text(ok ? localizer.t.statusOnline : localizer.t.unifiNeedsAttention)
            .font(.caption.bold())
            .foregroundStyle(ok ? AppTheme.running : AppTheme.warning)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background((ok ? AppTheme.running : AppTheme.warning).opacity(0.12), in: Capsule())
    }

    private func miniMetric(_ title: String, value: String, icon: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: icon)
                .font(.caption.bold())
                .foregroundStyle(color)
            Text(value)
                .font(.headline.bold())
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
    }

    private func operationTile(title: String, value: String, subtitle: String, icon: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.caption.bold())
                    .foregroundStyle(tint)
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(1)
                Spacer(minLength: 0)
            }

            Text(value)
                .font(.title3.bold())
                .foregroundStyle(tint)
                .lineLimit(1)
                .minimumScaleFactor(0.65)

            Text(subtitle)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(2)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, minHeight: 112, alignment: .topLeading)
        .padding(14)
        .background(tint.opacity(colorScheme == .dark ? 0.11 : 0.07), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func overviewPanel<Content: View>(title: String, icon: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.caption.bold())
                    .foregroundStyle(color)
                Text(title)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
            }

            content()
        }
        .frame(maxWidth: .infinity, minHeight: 148, alignment: .topLeading)
        .padding(14)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func overviewSplitRow(label: String, value: Int, total: Int, tint: Color) -> some View {
        let ratio = total > 0 ? Double(value) / Double(total) : 0
        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(label)
                    .font(.caption.weight(.medium))
                    .foregroundStyle(AppTheme.textSecondary)
                Spacer()
                Text("\(value)")
                    .font(.caption.bold())
                    .foregroundStyle(tint)
            }

            GeometryReader { geometry in
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .fill(AppTheme.textMuted.opacity(0.12))
                    .overlay(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 999, style: .continuous)
                            .fill(tint.gradient)
                            .frame(width: max(10, geometry.size.width * ratio))
                    }
            }
            .frame(height: 8)
        }
    }

    private func overviewStatRow(label: String, value: Int, tint: Color) -> some View {
        HStack(spacing: 8) {
            Circle()
                .fill(tint)
                .frame(width: 8, height: 8)
            Text(label)
                .font(.caption.weight(.medium))
                .foregroundStyle(AppTheme.textSecondary)
            Spacer()
            Text("\(value)")
                .font(.caption.bold())
                .foregroundStyle(.primary)
        }
    }

    private func fabricPanel(title: String, value: String, subtitle: String, tint: Color, icon: String, progress: Double?) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.caption.bold())
                    .foregroundStyle(tint)
                Text(title)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
            }

            Text(value)
                .font(.title3.bold())
                .foregroundStyle(tint)
                .lineLimit(1)
                .minimumScaleFactor(0.7)

            Text(subtitle)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(2)

            if let progress {
                GeometryReader { geometry in
                    RoundedRectangle(cornerRadius: 999, style: .continuous)
                        .fill(AppTheme.textMuted.opacity(0.12))
                        .overlay(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 999, style: .continuous)
                                .fill(tint.gradient)
                                .frame(width: max(10, geometry.size.width * min(max(progress, 0), 1)))
                        }
                }
                .frame(height: 8)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 138, alignment: .topLeading)
        .padding(14)
        .glassCard(tint: tint.opacity(colorScheme == .dark ? 0.09 : 0.05))
    }

    private func accessPointQualityRow(_ device: UniFiDevice) -> some View {
        let quality = averageInt(device.radios.compactMap(\.satisfaction))
        let load = device.clientCount ?? device.radios.compactMap(\.clientCount).reduce(0, +)
        let progress = quality.map { Double($0) / 100.0 } ?? 0

        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                UniFiDeviceGlyph(device: device, size: 18, compact: true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(device.displayName)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Text("\(load) \(localizer.t.unifiClients.lowercased())")
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                }
                Spacer()
                Text(quality.map { "\($0)%" } ?? localizer.t.notAvailable)
                    .font(.caption.bold())
                    .foregroundStyle(qualityTint(for: quality))
            }

            GeometryReader { geometry in
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .fill(AppTheme.textMuted.opacity(0.12))
                    .overlay(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 999, style: .continuous)
                            .fill(qualityTint(for: quality).gradient)
                            .frame(width: max(14, geometry.size.width * progress))
                    }
            }
            .frame(height: 8)
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func trafficLaneSection<Content: View>(title: String, tint: Color, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                Circle()
                    .fill(tint)
                    .frame(width: 8, height: 8)
            }
            content()
        }
    }

    private func trafficDeviceRow(device: UniFiDevice, value: Double, maximum: Double) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                UniFiDeviceGlyph(device: device, size: 18, compact: true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(device.displayName)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Text(device.model ?? device.kindLabel)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                        .lineLimit(1)
                }
                Spacer()
                Text(rateString(value))
                    .font(.caption.bold())
                    .foregroundStyle(color)
            }
            trafficBar(value: value, maximum: maximum, tint: color)
        }
    }

    private func trafficClientRow(client: UniFiClient, value: Double, maximum: Double) -> some View {
        let subtitle = [client.accessPointName, client.networkName]
            .compactMap { value -> String? in
                let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines)
                guard let trimmed, !trimmed.isEmpty else { return nil }
                return trimmed
            }
            .joined(separator: "  •  ")

        return VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 10) {
                iconBox("iphone.gen3", tint: AppTheme.running)
                VStack(alignment: .leading, spacing: 2) {
                    Text(client.displayName)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    if !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                            .lineLimit(1)
                    }
                }
                Spacer()
                Text(rateString(value))
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.running)
            }
            trafficBar(value: value, maximum: maximum, tint: AppTheme.running)
        }
    }

    private func trafficBar(value: Double, maximum: Double, tint: Color) -> some View {
        let share = maximum > 0 ? min(max(value / maximum, 0), 1) : 0
        return GeometryReader { geometry in
            RoundedRectangle(cornerRadius: 8, style: .continuous)
                .fill(tint.opacity(0.12))
                .overlay(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(
                            LinearGradient(
                                colors: [tint, tint.opacity(0.55)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: max(24, geometry.size.width * share))
                }
        }
        .frame(height: 10)
    }

    // MARK: - Formatters

    private func formatMilliseconds(_ value: Double?) -> String {
        guard let value else { return localizer.t.notAvailable }
        return "\(Int(value.rounded())) ms"
    }

    private func formatPercent(_ value: Double?) -> String {
        guard let value else { return localizer.t.notAvailable }
        return String(format: "%.1f%%", value)
    }

    private func formatMbps(_ kbps: Double?) -> String {
        guard let kbps, kbps > 0 else { return localizer.t.notAvailable }
        let mbps = kbps / 1000.0
        if mbps < 1 { return String(format: "%.0f kbps", kbps) }
        return String(format: "%.1f Mbps", mbps)
    }

    private func rateString(_ bytesPerSecond: Double) -> String {
        "\(Formatters.formatBytes(bytesPerSecond))/s"
    }

    private func formatLinkSpeed(_ mbps: Int?) -> String {
        guard let mbps, mbps > 0 else { return localizer.t.notAvailable }
        if mbps >= 1000 {
            return String(format: "%.1f Gbps", Double(mbps) / 1000.0)
        }
        return "\(mbps) Mbps"
    }

    private func wanSelection(in points: [UniFiISPMetricPoint]) -> UniFiISPMetricPoint? {
        guard let selectedWANDate else { return nil }
        return points.min { abs($0.date.timeIntervalSince(selectedWANDate)) < abs($1.date.timeIntervalSince(selectedWANDate)) }
    }

    private func nearestWANDate(to date: Date, in points: [UniFiISPMetricPoint]) -> Date? {
        points.min { abs($0.date.timeIntervalSince(date)) < abs($1.date.timeIntervalSince(date)) }?.date
    }

    private func updateWANSelection(at location: CGPoint, proxy: ChartProxy, geometry: GeometryProxy, points: [UniFiISPMetricPoint]) {
        guard let plotFrame = proxy.plotFrame else { return }
        let plotRect = geometry[plotFrame]
        let clampedX = min(max(location.x, plotRect.minX), plotRect.maxX)
        let relativeX = clampedX - plotRect.minX
        guard let date = proxy.value(atX: relativeX, as: Date.self) else { return }
        let snapped = nearestWANDate(to: date, in: points)
        guard snapped != selectedWANDate else { return }
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction) {
            selectedWANDate = snapped
        }
    }

    private func topologyChildren(for gateway: UniFiDevice?, in devices: [UniFiDevice]) -> [UniFiDevice] {
        let children = devices
            .filter { device in
                guard device.id != gateway?.id else { return false }
                if let gateway {
                    if device.uplinkDeviceName == gateway.displayName || device.uplinkDeviceName == gateway.name || device.uplinkDeviceName == gateway.model {
                        return true
                    }
                    if UniFiDeviceClassifier.isSwitch(device) || UniFiDeviceClassifier.isAP(device) {
                        return true
                    }
                }
                return !UniFiDeviceClassifier.isGateway(device)
            }
            .sorted { lhs, rhs in
                (lhs.liveTrafficBytesPerSecond ?? 0) > (rhs.liveTrafficBytesPerSecond ?? 0)
            }
        return Array(children.prefix(4))
    }

    private func topologyNode(_ device: UniFiDevice, emphasis: Bool = false) -> some View {
        VStack(spacing: 8) {
            UniFiDeviceGlyph(
                device: device,
                size: emphasis ? 24 : 18,
                compact: !emphasis,
                boxSize: emphasis ? 72 : 48
            )
            .overlay {
                if device.liveTrafficBytesPerSecond != nil {
                    RoundedRectangle(cornerRadius: emphasis ? 20 : 14, style: .continuous)
                        .stroke(color.opacity(0.35), lineWidth: 1)
                        .padding(-4)
                }
            }

            VStack(spacing: 2) {
                Text(device.displayName)
                    .font(emphasis ? .subheadline.bold() : .caption.bold())
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Text(device.model ?? device.kindLabel)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(1)
            }
        }
    }

    private func topologyLinkRow(_ device: UniFiDevice) -> some View {
        let uplinkSpeed = device.uplink?.speedMbps ?? device.ports.first(where: { $0.isUplink == true })?.speedMbps
        let portName = device.ports.first(where: { $0.isUplink == true })?.displayName

        return HStack(spacing: 12) {
            UniFiDeviceGlyph(device: device, size: 18, compact: true)
            VStack(alignment: .leading, spacing: 3) {
                Text(device.displayName)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                let detail = [portName, formatLinkSpeed(uplinkSpeed)]
                    .compactMap { value -> String? in
                        let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard let trimmed, !trimmed.isEmpty else { return nil }
                        return trimmed
                    }
                    .joined(separator: "  •  ")
                if !detail.isEmpty {
                    Text(detail)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                        .lineLimit(1)
                }
            }
            Spacer()
            if let traffic = device.liveTrafficBytesPerSecond {
                Text(rateString(traffic))
                    .font(.caption.bold())
                    .foregroundStyle(color)
            }
        }
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func statCapsule(_ value: String, label: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(value)
                .font(.subheadline.bold())
                .foregroundStyle(tint)
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(tint.opacity(colorScheme == .dark ? 0.12 : 0.07), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func averageInt(_ values: [Double]) -> Int? {
        guard !values.isEmpty else { return nil }
        return Int((values.reduce(0, +) / Double(values.count)).rounded())
    }

    private func qualityTint(for value: Int?) -> Color {
        guard let value else { return AppTheme.textSecondary }
        switch value {
        case 90...: return AppTheme.running
        case 75..<90: return color
        case 60..<75: return AppTheme.warning
        default: return AppTheme.danger
        }
    }

    private func signalTint(for dbm: Int?) -> Color {
        guard let dbm else { return AppTheme.textSecondary }
        switch dbm {
        case (-55)...: return AppTheme.running
        case (-67)..<(-55): return color
        case (-75)..<(-67): return AppTheme.warning
        default: return AppTheme.danger
        }
    }

    private func signalProgress(for dbm: Int?) -> Double? {
        guard let dbm else { return nil }
        let clamped = min(max(dbm, -90), -40)
        return Double(clamped + 90) / 50.0
    }

    // MARK: - Fetch

    private func fetchDashboard(forceLoading: Bool = true) async {
        guard !isPreview else { return }
        guard !isFetchingDashboard else { return }
        guard let client = await servicesStore.unifiClient(instanceId: selectedInstanceId) else {
            if forceLoading || data == nil {
                state = .error(.notConfigured)
            }
            return
        }
        isFetchingDashboard = true
        defer { isFetchingDashboard = false }

        if forceLoading || data == nil {
            state = .loading
        }
        do {
            let fresh = try await client.getDashboard()
            if let selectedSiteId, !fresh.sites.contains(where: { $0.siteId == selectedSiteId }) {
                self.selectedSiteId = nil
            }
            data = fresh
            state = .loaded(())
        } catch let apiError as APIError {
            if forceLoading || data == nil {
                state = .error(apiError)
            }
        } catch {
            if forceLoading || data == nil {
                state = .error(.networkError(error))
            }
        }
    }
}

private extension Double {
    var nilIfZero: Double? {
        self > 0 ? self : nil
    }
}

// MARK: - Mock data for preview

extension UniFiDashboardData {
    static func demo(mode: UniFiAuthMode) -> UniFiDashboardData {
        let decoder = JSONDecoder()

        let sitesJSON = """
        [
          {"siteId":"casa","hostId":"host1","meta":{"desc":"Casa","timezone":"Europe/Rome"},"statistics":{"counts":{"wifiClient":29,"wiredClient":12,"guestClient":1,"totalDevice":8,"offlineDevice":1,"pendingUpdateDevice":2,"criticalNotification":0},"gateway":{"shortname":"UDM-Pro"},"ispInfo":{"name":"Fastweb"},"percentages":{"wanUptime":99.97}}},
          {"siteId":"studio","hostId":"host2","meta":{"desc":"Studio","timezone":"Europe/Rome"},"statistics":{"counts":{"wifiClient":11,"wiredClient":6,"guestClient":0,"totalDevice":5,"offlineDevice":0,"pendingUpdateDevice":1,"criticalNotification":1},"gateway":{"shortname":"UXG-Max"},"ispInfo":{"name":"TIM Business"},"percentages":{"wanUptime":100.0}}}
        ]
        """
        let sites = try! decoder.decode([UniFiSite].self, from: Data(sitesJSON.utf8))

        let devicesJSON = """
        [
          {"id":"d1","siteId":"casa","updateAvailable":false,"uidb":{"_id":"d1","name":"UDM-Pro","model":"UDM-Pro","type":"udm","ip":"192.168.1.1","mac":"78:45:58:aa:bb:01","state":"online","firmwareVersion":"3.2.12","site_id":"casa","serial":"UDM0001","num_sta":42,"rx_bytes-r":5242880,"tx_bytes-r":1441792,"cpu":21,"mem":48,"temperature":53,"uplink":{"name":"ISP WAN","speed":2500,"full_duplex":true,"rx_bytes-r":5242880,"tx_bytes-r":1441792},"port_table":[{"port_idx":1,"name":"WAN","up":true,"speed":2500,"is_uplink":true,"rx_bytes-r":5242880,"tx_bytes-r":1441792},{"port_idx":9,"name":"Port 9","up":true,"speed":1000,"rx_bytes-r":262144,"tx_bytes-r":131072},{"port_idx":10,"name":"Port 10","up":true,"speed":1000,"rx_bytes-r":131072,"tx_bytes-r":65536}]}},
          {"id":"d2","siteId":"casa","uidb":{"_id":"d2","name":"USW-24-PoE","model":"USW-24-PoE","type":"usw","ip":"192.168.1.2","mac":"78:45:58:aa:bb:02","state":"online","firmwareVersion":"6.6.61","site_id":"casa","serial":"USW0002","rx_bytes-r":1572864,"tx_bytes-r":786432,"port_table":[{"port_idx":1,"name":"Uplink","up":true,"speed":1000,"is_uplink":true,"rx_bytes-r":1572864,"tx_bytes-r":786432},{"port_idx":5,"name":"PoE Cam","up":true,"speed":1000,"poe_power":6.4,"poe_enable":true,"rx_bytes-r":196608,"tx_bytes-r":98304},{"port_idx":7,"name":"NAS","up":true,"speed":1000,"rx_bytes-r":786432,"tx_bytes-r":655360}]}}
          ,
          {"id":"d3","siteId":"casa","updateAvailable":true,"uidb":{"_id":"d3","name":"UAP Soggiorno","model":"UAP-AC-Pro","type":"uap","ip":"192.168.1.3","mac":"78:45:58:aa:bb:03","state":"online","firmwareVersion":"6.6.55","site_id":"casa","serial":"UAP0003","num_sta":19,"rx_bytes-r":786432,"tx_bytes-r":655360,"temperature":47,"radio_table":[{"radio":"5G","channel":44,"channel_width":80,"num_sta":14,"satisfaction":96},{"radio":"2G","channel":1,"channel_width":20,"num_sta":5,"satisfaction":91}]}},
          {"id":"d4","siteId":"casa","updateAvailable":true,"uidb":{"_id":"d4","name":"UAP Studio","model":"U7-Pro","type":"uap","ip":"192.168.1.4","mac":"78:45:58:aa:bb:04","state":"online","firmwareVersion":"7.0.10","site_id":"casa","serial":"UAP0004","num_sta":12,"rx_bytes-r":917504,"tx_bytes-r":524288}},
          {"id":"d5","siteId":"casa","uidb":{"_id":"d5","name":"USW-Flex Mini","model":"USW-Flex-Mini","type":"usw","ip":"192.168.1.6","mac":"78:45:58:aa:bb:06","state":"online","firmwareVersion":"6.6.61","site_id":"casa","serial":"USW0005","port_table":[{"port_idx":1,"name":"Uplink","up":true,"speed":1000,"is_uplink":true},{"port_idx":2,"name":"TV","up":true,"speed":1000,"rx_bytes-r":32768,"tx_bytes-r":16384}]}},
          {"id":"d6","siteId":"casa","uidb":{"_id":"d6","name":"UAP Cantina","model":"UAP-AC-Lite","type":"uap","ip":"192.168.1.7","mac":"78:45:58:aa:bb:07","state":"offline","firmwareVersion":"6.5.28","site_id":"casa","serial":"UAP0006"}},
          {"id":"d7","siteId":"studio","uidb":{"_id":"d7","name":"UXG-Max","model":"UXG-Max","type":"gateway","ip":"10.1.0.1","mac":"78:45:58:aa:bb:11","state":"online","firmwareVersion":"4.1.3","site_id":"studio","serial":"UXG0007","rx_bytes-r":1048576,"tx_bytes-r":524288,"cpu":17,"mem":39,"temperature":49,"uplink":{"name":"Fiber WAN","speed":1000,"full_duplex":true,"rx_bytes-r":1048576,"tx_bytes-r":524288},"port_table":[{"port_idx":1,"name":"WAN","up":true,"speed":1000,"is_uplink":true,"rx_bytes-r":1048576,"tx_bytes-r":524288},{"port_idx":2,"name":"LAN","up":true,"speed":2500,"rx_bytes-r":524288,"tx_bytes-r":262144}]}},
          {"id":"d8","siteId":"studio","uidb":{"_id":"d8","name":"USW-Lite-16-PoE","model":"USW-Lite-16-PoE","type":"usw","ip":"10.1.0.2","mac":"78:45:58:aa:bb:12","state":"online","firmwareVersion":"6.6.61","site_id":"studio","serial":"USW0008","port_table":[{"port_idx":1,"name":"Uplink","up":true,"speed":1000,"is_uplink":true},{"port_idx":3,"name":"Door Access","up":true,"speed":1000,"poe_power":4.2,"poe_enable":true},{"port_idx":4,"name":"Camera","up":true,"speed":1000,"poe_power":7.8,"poe_enable":true,"rx_bytes-r":131072,"tx_bytes-r":65536}]}},
          {"id":"d9","siteId":"studio","uidb":{"_id":"d9","name":"U7 Wall Studio","model":"U7-Wall","type":"uap","ip":"10.1.0.3","mac":"78:45:58:aa:bb:13","state":"online","firmwareVersion":"7.0.10","site_id":"studio","serial":"UAP0009","num_sta":6,"rx_bytes-r":393216,"tx_bytes-r":327680}}
        ]
        """
        let devices = try! decoder.decode([UniFiDevice].self, from: Data(devicesJSON.utf8))

        let host = UniFiHost(
            id: "host1", hardwareId: "udm-pro-001", type: "udm-pro",
            ipAddress: "192.168.1.1", owner: true, isBlocked: nil,
            reportedState: UniFiReportedState(name: "UDM-Pro Casa", hostname: "udm-pro-casa",
                                              state: "connected", version: "3.2.12", firmwareVersion: nil)
        )
        let studioHost = UniFiHost(
            id: "host2", hardwareId: "uxg-max-002", type: "uxg-max",
            ipAddress: "10.1.0.1", owner: true, isBlocked: nil,
            reportedState: UniFiReportedState(name: "UXG Max Studio", hostname: "uxg-max-studio",
                                              state: "connected", version: "4.1.3", firmwareVersion: nil)
        )

        let isoFmt = ISO8601DateFormatter()
        let now = Date()
        let casaPoints: [UniFiISPMetricPoint] = (0..<96).map { i in
            let date = Calendar.current.date(byAdding: .minute, value: -(96 - i) * 15, to: now)!
            let spike = i % 23 == 0
            return UniFiISPMetricPoint(
                metricTime: isoFmt.string(from: date),
                data: UniFiISPMetricData(wan: UniFiWANMetric(
                    avgLatency: spike ? Double.random(in: 42...68) : Double.random(in: 8...18),
                    maxLatency: spike ? 80 : 24,
                    packetLoss: spike ? 1.2 : 0.0,
                    uptime: 99.97, downtime: 0.03,
                    downloadKbps: Double.random(in: 200000...270000),
                    uploadKbps: Double.random(in: 85000...130000),
                    ispName: "Fastweb"
                ))
            )
        }
        let studioPoints: [UniFiISPMetricPoint] = (0..<96).map { i in
            let date = Calendar.current.date(byAdding: .minute, value: -(96 - i) * 15, to: now)!
            let spike = i % 31 == 0
            return UniFiISPMetricPoint(
                metricTime: isoFmt.string(from: date),
                data: UniFiISPMetricData(wan: UniFiWANMetric(
                    avgLatency: spike ? Double.random(in: 25...40) : Double.random(in: 6...12),
                    maxLatency: spike ? 52 : 18,
                    packetLoss: spike ? 0.4 : 0.0,
                    uptime: 100.0, downtime: 0,
                    downloadKbps: Double.random(in: 85000...140000),
                    uploadKbps: Double.random(in: 40000...65000),
                    ispName: "TIM Business"
                ))
            )
        }
        let ispMetrics = [
            UniFiISPMetricSeries(metricType: "isp", periods: casaPoints, hostId: "host1", siteId: "casa"),
            UniFiISPMetricSeries(metricType: "isp", periods: studioPoints, hostId: "host2", siteId: "studio")
        ]

        if mode == .siteManager {
            return UniFiDashboardData(
                mode: .siteManager,
                hosts: [host, studioHost],
                sites: sites,
                devices: devices,
                clients: [],
                ispMetrics: ispMetrics,
                networks: []
            )
        }

        let clientsJSON = """
        [
          {"id":"c1","name":"MacBook Pro Andrea","siteId":"casa","ipAddress":"192.168.1.101","macAddress":"a4:83:e7:01:02:03","type":"WIRELESS","networkName":"LAN","rxBytes":3254512640,"txBytes":982345678,"ap_name":"UAP Soggiorno","signal":-53,"experience":98,"rx_bytes-r":65536,"tx_bytes-r":131072},
          {"id":"c2","name":"iPhone Andrea","siteId":"casa","ipAddress":"192.168.1.102","macAddress":"a4:83:e7:04:05:06","type":"WIRELESS","networkName":"LAN","rxBytes":445123456,"txBytes":123456789,"ap_name":"UAP Studio","signal":-61,"experience":95,"rx_bytes-r":32768,"tx_bytes-r":49152},
          {"id":"c3","name":"iPad Pro","siteId":"casa","ipAddress":"192.168.1.103","macAddress":"a4:83:e7:07:08:09","type":"WIRELESS","networkName":"LAN","rxBytes":234567890,"txBytes":45678901,"ap_name":"UAP Studio","signal":-58,"experience":94},
          {"id":"c4","name":"TV Samsung","siteId":"casa","ipAddress":"192.168.1.104","macAddress":"dc:a6:32:10:11:12","type":"WIRED","networkName":"LAN","rxBytes":8765432100,"txBytes":123456},
          {"id":"c5","name":"NAS Synology DS923+","siteId":"casa","ipAddress":"192.168.1.105","macAddress":"00:11:32:13:14:15","type":"WIRED","networkName":"LAN","rxBytes":15234567890,"txBytes":9876543210,"rx_bytes-r":524288,"tx_bytes-r":458752},
          {"id":"c6","name":"Door Controller","siteId":"studio","ipAddress":"10.1.0.20","macAddress":"b8:27:eb:16:17:18","type":"WIRED","networkName":"Office","rxBytes":987654321,"txBytes":456789012},
          {"id":"c7","name":"Meeting Room Display","siteId":"studio","ipAddress":"10.1.0.21","macAddress":"cc:dd:ee:19:20:21","type":"WIRELESS","networkName":"Office","rxBytes":12345678,"txBytes":3456789,"ap_name":"U7 Wall Studio","signal":-47,"experience":99,"rx_bytes-r":16384,"tx_bytes-r":8192},
          {"id":"c8","name":"Studio iPhone","siteId":"studio","ipAddress":"10.1.0.22","macAddress":"18:b4:30:22:23:24","type":"WIRELESS","networkName":"Office","rxBytes":5678901,"txBytes":2345678,"ap_name":"U7 Wall Studio","signal":-55,"experience":96},
          {"id":"c9","name":"iPhone Marco","siteId":"casa","ipAddress":"192.168.100.1","macAddress":"ff:ee:dd:25:26:27","type":"WIRELESS","networkName":"Guest","rxBytes":45678901,"txBytes":12345678,"access":{"type":"GUEST","authorized":false},"ap_name":"UAP Soggiorno","signal":-67,"experience":88}
        ]
        """
        let clients = try! decoder.decode([UniFiClient].self, from: Data(clientsJSON.utf8))

        let casaNetworksJSON = """
        [
          {"_id":"n1","name":"LAN","purpose":"corporate","ip_subnet":"192.168.1.0/24","dhcpdEnabled":true},
          {"_id":"n2","name":"IoT","purpose":"corporate","vlan_id":10,"ip_subnet":"10.0.10.0/24","dhcpdEnabled":true},
          {"_id":"n3","name":"Ospiti","purpose":"guest","vlan_id":100,"ip_subnet":"192.168.100.0/24","dhcpdEnabled":true}
        ]
        """
        let studioNetworksJSON = """
        [
          {"_id":"n4","name":"Office","purpose":"corporate","ip_subnet":"10.1.0.0/24","dhcpdEnabled":true},
          {"_id":"n5","name":"Devices","purpose":"corporate","vlan_id":20,"ip_subnet":"10.1.20.0/24","dhcpdEnabled":true}
        ]
        """
        let casaNetworks = try! decoder.decode([UniFiNetwork].self, from: Data(casaNetworksJSON.utf8)).map { $0.withSiteId("casa") }
        let studioNetworks = try! decoder.decode([UniFiNetwork].self, from: Data(studioNetworksJSON.utf8)).map { $0.withSiteId("studio") }
        let networks = casaNetworks + studioNetworks

        return UniFiDashboardData(
            mode: .localNetwork,
            hosts: [],
            sites: sites,
            devices: devices,
            clients: clients,
            ispMetrics: [],
            networks: networks
        )
    }
}

// MARK: - Preview

#Preview("UniFi – Site Manager") {
    NavigationStack {
        UniFiDashboard(instanceId: UUID(), _previewData: .demo(mode: .siteManager))
    }
    .environment(Localizer(language: .en))
    .environment(ServicesStore())
}

#Preview("UniFi – Local Network") {
    NavigationStack {
        UniFiDashboard(instanceId: UUID(), _previewData: .demo(mode: .localNetwork))
    }
    .environment(Localizer(language: .en))
    .environment(ServicesStore())
}
