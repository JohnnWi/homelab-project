import SwiftUI

private enum PangolinClientSource: String, Sendable {
    case machine
    case userDevice
}

private struct PangolinClientEntry: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let subtitle: String?
    let online: Bool
    let blocked: Bool
    let archived: Bool
    let approvalState: String?
    let version: String?
    let updateAvailable: Bool
    let trafficIn: Double?
    let trafficOut: Double?
    let source: PangolinClientSource
    let agent: String?
    let linkedSites: [String]
}

private struct PangolinPublicEditorState: Identifiable, Hashable {
    let resource: PangolinResource
    let targets: [PangolinTarget]

    var id: Int { resource.resourceId }
}

private struct PangolinPublicResourceUpdateInput: Sendable {
    let resourceId: Int
    let name: String
    let enabled: Bool
    let sso: Bool
    let ssl: Bool
    let targetId: Int?
    let targetSiteId: Int?
    let targetIp: String
    let targetPort: String
    let targetEnabled: Bool
}

private struct PangolinPublicResourceCreateInput: Sendable {
    let name: String
    let resourceProtocol: String
    let enabled: Bool
    let domainId: String?
    let subdomain: String
    let proxyPort: String
    let targetSiteId: Int
    let targetIp: String
    let targetPort: String
    let targetEnabled: Bool
    let targetMethod: String?
}

private struct PangolinPrivateResourceUpdateInput: Sendable {
    let siteResourceId: Int
    let name: String
    let siteId: Int
    let mode: String
    let destination: String
    let enabled: Bool
    let alias: String
    let tcpPortRangeString: String
    let udpPortRangeString: String
    let disableIcmp: Bool
    let authDaemonPort: String
    let authDaemonMode: String?
}

private struct PangolinPrivateResourceCreateInput: Sendable {
    let name: String
    let siteId: Int
    let mode: String
    let destination: String
    let enabled: Bool
    let alias: String
    let tcpPortRangeString: String
    let udpPortRangeString: String
    let disableIcmp: Bool
    let authDaemonPort: String
    let authDaemonMode: String?
}

struct PangolinDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedInstanceId: UUID
    @State private var selectedOrgId: String?
    @State private var state: LoadableState<PangolinSnapshot> = .idle
    @State private var editingPublicResource: PangolinPublicEditorState?
    @State private var editingPrivateResource: PangolinSiteResource?
    @State private var isPresentingCreatePublicResource = false
    @State private var isPresentingCreatePrivateResource = false
    @State private var togglingResourceIds: Set<String> = []
    @State private var actionErrorMessage: String?
    @State private var isFetchingSnapshot = false
    @State private var queuedSnapshotRefresh = false

    private let accent = ServiceType.pangolin.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .pangolin,
            instanceId: selectedInstanceId,
            state: state,
            showTailscaleQuickAccess: false,
            onRefresh: { await fetchSnapshot(forceLoading: false) }
        ) {
            instancePicker
            orgPicker
            if let snapshot = state.value {
                heroCard(snapshot)
                statsGrid(snapshot)
                sitesSection(snapshot)
                privateResourcesSection(snapshot)
                publicResourcesSection(snapshot)
                clientsSection(snapshot)
                domainsSection(snapshot)
            }
        }
        .navigationTitle(ServiceType.pangolin.displayName)
        .sheet(item: $editingPublicResource) { editor in
            PangolinPublicResourceEditorSheet(
                resource: editor.resource,
                targets: editor.targets,
                sites: state.value?.sites ?? [],
                strings: strings,
                onSave: { input in
                    try await savePublicResource(input)
                }
            )
        }
        .sheet(isPresented: $isPresentingCreatePublicResource) {
            PangolinPublicResourceCreateSheet(
                sites: state.value?.sites ?? [],
                domains: state.value?.domains ?? [],
                strings: strings,
                onSave: { input in
                    try await createPublicResource(input)
                }
            )
        }
        .sheet(item: $editingPrivateResource) { resource in
            PangolinPrivateResourceEditorSheet(
                resource: resource,
                sites: state.value?.sites ?? [],
                strings: strings,
                onSave: { input in
                    try await savePrivateResource(input)
                }
            )
        }
        .sheet(isPresented: $isPresentingCreatePrivateResource) {
            PangolinPrivateResourceCreateSheet(
                sites: state.value?.sites ?? [],
                strings: strings,
                onSave: { input in
                    try await createPrivateResource(input)
                }
            )
        }
        .alert(localizer.t.error, isPresented: Binding(
            get: { actionErrorMessage != nil },
            set: { if !$0 { actionErrorMessage = nil } }
        )) {
            Button(localizer.t.confirm, role: .cancel) { actionErrorMessage = nil }
        } message: {
            Text(actionErrorMessage ?? localizer.t.error)
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await fetchSnapshot(forceLoading: false) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(state.isLoading)
            }
        }
        .task(id: fetchTaskKey) {
            await fetchSnapshot(forceLoading: true)
        }
        .task(id: autoRefreshTaskKey) {
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(60))
                guard !Task.isCancelled else { break }
                // Skip refresh when the app is not in the foreground to avoid
                // unnecessary API load and battery drain.
                guard scenePhase == .active else { continue }
                guard !isFetchingSnapshot else { continue }
                await fetchSnapshot(forceLoading: false)
            }
        }
    }

    @Environment(\.scenePhase) private var scenePhase

    private var fetchTaskKey: String { selectedInstanceId.uuidString }
    private var autoRefreshTaskKey: String { "\(selectedInstanceId.uuidString):\(selectedOrgId ?? "auto")" }

    private var selectedOrg: PangolinOrg? {
        guard let snapshot = state.value else { return nil }
        return snapshot.orgs.first { $0.orgId == snapshot.selectedOrgId }
    }

    private var strings: PangolinStrings {
        PangolinStrings.forLanguage(localizer.language)
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .pangolin)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances.sentenceCased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            selectedOrgId = nil
                            state = .idle
                            servicesStore.setPreferredInstance(id: instance.id, for: .pangolin)
                        } label: {
                            HStack(spacing: 12) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? accent : AppTheme.textMuted.opacity(0.3))
                                    .frame(width: 10, height: 10)

                                VStack(alignment: .leading, spacing: 3) {
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
                            .glassCard(tint: instance.id == selectedInstanceId ? accent.opacity(0.12) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var orgPicker: some View {
        Group {
            if let snapshot = state.value, snapshot.orgs.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    sectionHeader(strings.organizations, detail: "\(snapshot.orgs.count)")

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(snapshot.orgs) { org in
                                let isSelected = org.orgId == snapshot.selectedOrgId
                                Button {
                                    guard selectedOrgId != org.orgId else { return }
                                    HapticManager.light()
                                    selectedOrgId = org.orgId
                                    Task { await fetchSnapshot(forceLoading: false) }
                                } label: {
                                    HStack(spacing: 8) {
                                        Image(systemName: isSelected ? "checkmark.seal.fill" : "point.3.connected.trianglepath.dotted")
                                            .font(.caption.bold())
                                        Text(org.name)
                                            .font(.caption.weight(.semibold))
                                            .lineLimit(1)
                                    }
                                    .foregroundStyle(isSelected ? accent : AppTheme.textSecondary)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 10)
                                    .background(
                                        Capsule()
                                            .fill(isSelected ? accent.opacity(0.16) : AppTheme.surface.opacity(0.9))
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
        }
    }

    private func heroCard(_ snapshot: PangolinSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(alignment: .top, spacing: 14) {
                ServiceIconView(type: .pangolin, size: 38)
                    .frame(width: 70, height: 70)
                    .background(
                        RoundedRectangle(cornerRadius: 22, style: .continuous)
                            .fill(accent.opacity(0.12))
                    )

                VStack(alignment: .leading, spacing: 6) {
                    Text(selectedOrg?.name ?? ServiceType.pangolin.displayName)
                        .font(.title2.weight(.bold))
                        .foregroundStyle(.primary)

                    Text(strings.overviewSubtitle)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)

                    HStack(spacing: 8) {
                        heroBadge(
                            snapshot.sites.filter(\.online).count == snapshot.sites.count && !snapshot.sites.isEmpty
                                ? strings.allSitesOnline
                                : strings.onlineSites(snapshot.sites.filter(\.online).count),
                            tint: snapshot.sites.contains(where: \.online) ? AppTheme.running : AppTheme.warning
                        )
                        if let org = selectedOrg?.subnet, !org.isEmpty {
                            heroBadge(org, tint: accent)
                        }
                    }
                }
            }

            if let org = selectedOrg {
                HStack(spacing: 10) {
                    infoPill(strings.org, org.orgId, tint: accent)
                    if let subnet = org.utilitySubnet, !subnet.isEmpty {
                        infoPill(strings.utility, subnet, tint: AppTheme.info)
                    }
                    if org.isBillingOrg == true {
                        infoPill(strings.billing, strings.enabled, tint: AppTheme.warning)
                    }
                }
            }
        }
        .padding(20)
        .background(
            RoundedRectangle(cornerRadius: 30, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [
                            accent.opacity(0.20),
                            accent.opacity(0.08),
                            Color.clear
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
        )
        .glassCard(cornerRadius: 30, tint: accent.opacity(0.08))
    }

    private func statsGrid(_ snapshot: PangolinSnapshot) -> some View {
        let clientEntries = mergedClients(snapshot)
        return LazyVGrid(columns: twoColumnGrid, spacing: AppTheme.gridSpacing) {
            GlassStatCard(
                title: strings.sites,
                value: "\(snapshot.sites.count)",
                icon: "point.3.connected.trianglepath.dotted",
                iconColor: accent,
                subtitle: strings.onlineCount(snapshot.sites.filter { $0.online }.count)
            )
            GlassStatCard(
                title: strings.privateResources,
                value: "\(snapshot.siteResources.count)",
                icon: "lock.shield.fill",
                iconColor: AppTheme.info,
                subtitle: strings.enabledCount(snapshot.siteResources.filter { $0.enabled }.count)
            )
            GlassStatCard(
                title: strings.publicResources,
                value: "\(snapshot.resources.count)",
                icon: "globe",
                iconColor: AppTheme.running,
                subtitle: strings.enabledCount(snapshot.resources.filter { $0.enabled }.count)
            )
            GlassStatCard(
                title: strings.clients,
                value: "\(clientEntries.count)",
                icon: "person.2.fill",
                iconColor: AppTheme.warning,
                subtitle: strings.onlineCount(clientEntries.filter { $0.online }.count)
            )
            GlassStatCard(
                title: strings.domains,
                value: "\(snapshot.domains.count)",
                icon: "network",
                iconColor: AppTheme.accent,
                subtitle: strings.verifiedCount(snapshot.domains.filter { $0.verified }.count)
            )
            GlassStatCard(
                title: strings.traffic,
                value: trafficValue(snapshot.sites, clientEntries),
                icon: "arrow.left.and.right.circle.fill",
                iconColor: accent,
                subtitle: strings.ingressEgress
            )
        }
    }

    private func sitesSection(_ snapshot: PangolinSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(strings.sites, detail: strings.onlineCount(snapshot.sites.filter { $0.online }.count))

            if snapshot.sites.isEmpty {
                placeholderCard(strings.noSites)
            } else {
                ForEach(snapshot.sites.prefix(8)) { site in
                    itemCard(
                        title: site.name,
                        subtitle: joined(site.address, site.subnet, site.type),
                        details: [
                            site.online ? strings.online : strings.offline,
                            site.newtVersion.map(strings.newtVersion),
                            site.exitNodeName.map(strings.exitNode),
                            trafficLabel(site),
                            site.newtUpdateAvailable == true ? strings.newtUpdate : nil,
                            site.exitNodeEndpoint.map(strings.endpoint)
                        ],
                        tint: site.online ? accent : AppTheme.warning
                    )
                }
            }
        }
    }

    private func privateResourcesSection(_ snapshot: PangolinSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(
                strings.privateResources,
                detail: strings.enabledCount(snapshot.siteResources.filter { $0.enabled }.count),
                actionLabel: snapshot.sites.isEmpty ? nil : strings.createPrivateResource,
                action: {
                    HapticManager.light()
                    isPresentingCreatePrivateResource = true
                }
            )

            if snapshot.siteResources.isEmpty {
                placeholderCard(strings.noPrivateResources)
            } else {
                ForEach(snapshot.siteResources.prefix(8)) { resource in
                    itemCard(
                        title: resource.name,
                        subtitle: joined(resource.siteName, resource.destination),
                        details: [
                            resource.enabled ? strings.enabled : strings.disabled,
                            resource.mode?.capitalized,
                            resource.protocolName?.uppercased(),
                            resource.proxyPort.map(strings.proxyPort),
                            resource.destinationPort.map(strings.destinationPort),
                            resource.alias.map(strings.alias),
                            resource.aliasAddress.map(strings.dns),
                            resource.tcpPortRangeString.map(strings.tcpPorts),
                            resource.udpPortRangeString.map(strings.udpPorts),
                            resource.authDaemonPort.map(strings.authDaemonPort),
                            resource.authDaemonMode?.uppercased(),
                            resource.disableIcmp == true ? strings.icmpOff : nil
                        ],
                        tint: resource.enabled ? AppTheme.info : AppTheme.textMuted,
                        onEdit: {
                            editingPrivateResource = resource
                        }
                    )
                }
            }
        }
    }

    private func publicResourcesSection(_ snapshot: PangolinSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(
                strings.publicResources,
                detail: strings.enabledCount(snapshot.resources.filter { $0.enabled }.count),
                actionLabel: snapshot.sites.isEmpty ? nil : strings.createPublicResource,
                action: {
                    HapticManager.light()
                    isPresentingCreatePublicResource = true
                }
            )

            if snapshot.resources.isEmpty {
                placeholderCard(strings.noPublicResources)
            } else {
                ForEach(snapshot.resources.prefix(8)) { resource in
                    publicResourceCard(
                        resource,
                        targets: snapshot.targetsByResourceId[resource.resourceId] ?? resource.targets
                    )
                }
            }
        }
    }

    private func clientsSection(_ snapshot: PangolinSnapshot) -> some View {
        let clientEntries = mergedClients(snapshot)
        return VStack(alignment: .leading, spacing: 12) {
            sectionHeader(strings.clients, detail: strings.onlineCount(clientEntries.filter { $0.online }.count))

            if clientEntries.isEmpty {
                placeholderCard(strings.noClients)
            } else {
                ForEach(clientEntries.prefix(8)) { client in
                    itemCard(
                        title: client.name,
                        subtitle: client.subtitle,
                        details: [
                            clientSourceLabel(client.source),
                            client.agent.map(agentLabel),
                            client.blocked ? strings.blocked : nil,
                            client.archived ? strings.archived : nil,
                            client.online ? strings.online : strings.offline,
                            client.version.map(strings.olmVersion),
                            client.approvalState.map(strings.approvalState),
                            client.updateAvailable ? strings.agentUpdate : nil,
                            client.linkedSites.isEmpty ? nil : strings.linkedSites(client.linkedSites.count),
                            clientTrafficLabel(client)
                        ],
                        tint: client.blocked ? AppTheme.danger : (client.online ? AppTheme.running : AppTheme.textMuted)
                    )
                }
            }
        }
    }

    private func domainsSection(_ snapshot: PangolinSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(strings.domains, detail: strings.verifiedCount(snapshot.domains.filter { $0.verified }.count))

            if snapshot.domains.isEmpty {
                placeholderCard(strings.noDomains)
            } else {
                ForEach(snapshot.domains.prefix(8)) { domain in
                    itemCard(
                        title: domain.baseDomain,
                        subtitle: joined(domain.type?.capitalized, domain.certResolver),
                        details: [
                            domain.verified ? strings.verified : strings.pending,
                            domain.failed ? strings.failed : nil,
                            domain.errorMessage,
                            domain.certResolver.map(strings.resolver),
                            domain.configManaged.map { $0 ? strings.managed : strings.manual },
                            domain.preferWildcardCert == true ? strings.wildcard : nil,
                            domain.tries.map(strings.tries)
                        ],
                        tint: domain.failed ? AppTheme.danger : (domain.verified ? accent : AppTheme.warning)
                    )
                }
            }
        }
    }

    private func publicResourceCard(_ resource: PangolinResource, targets: [PangolinTarget]) -> some View {
        let tint = resourceTint(resource, targets: targets)
        let detailItems = [
            resource.enabled ? strings.enabled : strings.disabled,
            resource.ssl ? "TLS" : nil,
            resource.sso ? "SSO" : nil,
            resource.whitelist ? strings.whitelist : nil,
            resource.http ? "HTTP" : nil,
            resource.proxyPort.map(strings.proxyPort),
            strings.targetsCount(targets.count),
            targetHealthLabel(targets)
        ]

        return VStack(alignment: .leading, spacing: 12) {
            itemCard(
                title: resource.name,
                subtitle: joined(resource.fullDomain, resource.protocolName?.uppercased()),
                details: detailItems,
                tint: tint,
                onToggle: {
                    Task { await togglePublicResource(resource) }
                },
                toggleLabel: resource.enabled ? strings.disableAction : strings.enableAction,
                toggleTint: resource.enabled ? AppTheme.danger : tint,
                isToggling: togglingResourceIds.contains(toggleKey(for: resource)),
                onEdit: {
                    editingPublicResource = PangolinPublicEditorState(resource: resource, targets: targets)
                }
            )

            if !targets.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(targets.prefix(3)) { target in
                        targetCard(target, tint: targetTint(target))
                    }
                }
            }
        }
    }

    private func itemCard(
        title: String,
        subtitle: String?,
        details: [String?],
        tint: Color,
        onToggle: (() -> Void)? = nil,
        toggleLabel: String? = nil,
        toggleTint: Color? = nil,
        isToggling: Bool = false,
        onEdit: (() -> Void)? = nil
    ) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(tint.opacity(0.12))
                    .frame(width: 44, height: 44)
                    .overlay(
                        Image(systemName: "seal.fill")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(tint)
                    )

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                Spacer(minLength: 0)

                HStack(spacing: 8) {
                    if let onToggle {
                        Button(action: onToggle) {
                            Group {
                                if isToggling {
                                    ProgressView()
                                        .progressViewStyle(.circular)
                                        .tint(toggleTint ?? tint)
                                } else {
                                    Image(systemName: "power")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(toggleTint ?? tint)
                                }
                            }
                            .frame(width: 18, height: 18)
                            .padding(10)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill((toggleTint ?? tint).opacity(0.10))
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(isToggling)
                        .accessibilityLabel(toggleLabel ?? "")
                    }

                    if let onEdit {
                        Button(action: onEdit) {
                            Image(systemName: "square.and.pencil")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(tint)
                                .padding(10)
                                .background(
                                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        .fill(tint.opacity(0.10))
                                )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(localizer.t.actionEdit)
                    }
                }
            }

            let trimmedDetails = details.compactMap { detail -> String? in
                guard let detail else { return nil }
                let value = detail.trimmingCharacters(in: .whitespacesAndNewlines)
                return value.isEmpty ? nil : value
            }
            if !trimmedDetails.isEmpty {
                FlexiblePillRow(items: trimmedDetails, tint: tint)
            }
        }
        .padding(AppTheme.innerPadding)
        .glassCard(tint: tint.opacity(0.05))
    }

    private func placeholderCard(_ title: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "tray")
                .foregroundStyle(AppTheme.textMuted)
            Text(title)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
            Spacer()
        }
        .padding(AppTheme.innerPadding)
        .glassCard()
    }

    private func targetCard(_ target: PangolinTarget, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(target.ip):\(target.port)")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)

            if let subtitle = joined(target.method?.uppercased(), target.path, target.pathMatchType?.uppercased()) {
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }

            FlexiblePillRow(
                items: [
                    target.enabled ? strings.enabled : strings.disabled,
                    target.hcEnabled == true ? strings.healthCheck : nil,
                    target.hcHealth.map(strings.healthStatus),
                    target.hcPath.map(strings.healthPath),
                    target.priority.map(strings.priority),
                    target.rewritePath.map(strings.rewrite)
                ].compactMap { $0 },
                tint: tint
            )
        }
        .padding(AppTheme.innerPadding)
        .glassCard(tint: tint.opacity(0.05))
    }

    private func sectionHeader(
        _ title: String,
        detail: String?,
        actionLabel: String? = nil,
        action: (() -> Void)? = nil
    ) -> some View {
        HStack {
            Text(title)
                .font(.headline.weight(.bold))
            Spacer()
            if let actionLabel, let action {
                Button(action: action) {
                    Image(systemName: "plus.circle.fill")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(accent)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(actionLabel)
            }
            if let detail {
                Text(detail)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
    }

    private func heroBadge(_ title: String, tint: Color) -> some View {
        Text(title)
            .font(.caption.weight(.semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                Capsule()
                    .fill(tint.opacity(0.14))
            )
    }

    private func infoPill(_ title: String, _ value: String, tint: Color) -> some View {
        HStack(spacing: 6) {
            Text(title)
                .font(.caption2.weight(.bold))
                .foregroundStyle(AppTheme.textMuted)
            Text(value)
                .font(.caption.weight(.semibold))
                .foregroundStyle(tint)
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(
            Capsule()
                .fill(tint.opacity(0.12))
        )
    }

    private func mergedClients(_ snapshot: PangolinSnapshot) -> [PangolinClientEntry] {
        let machineClients = snapshot.clients.map { client in
            PangolinClientEntry(
                id: "machine-\(client.clientId)",
                name: client.name,
                subtitle: joined(client.subnet, client.type?.capitalized),
                online: client.online,
                blocked: client.blocked,
                archived: client.archived,
                approvalState: client.approvalState,
                version: client.olmVersion,
                updateAvailable: client.olmUpdateAvailable == true,
                trafficIn: client.megabytesIn,
                trafficOut: client.megabytesOut,
                source: .machine,
                agent: nil,
                linkedSites: client.sites.compactMap { $0.siteName ?? $0.siteNiceId }
            )
        }

        let userDevices = snapshot.userDevices.map { device in
            PangolinClientEntry(
                id: "device-\(device.clientId)",
                name: device.name,
                subtitle: joined(device.deviceModel, device.fingerprintPlatform, device.subnet),
                online: device.online,
                blocked: device.blocked,
                archived: device.archived || device.olmArchived,
                approvalState: device.approvalState,
                version: device.olmVersion,
                updateAvailable: device.olmUpdateAvailable == true,
                trafficIn: device.megabytesIn,
                trafficOut: device.megabytesOut,
                source: .userDevice,
                agent: device.agent ?? device.type,
                linkedSites: []
            )
        }

        return (machineClients + userDevices).sorted { lhs, rhs in
            if lhs.online != rhs.online { return lhs.online && !rhs.online }
            if lhs.blocked != rhs.blocked { return !lhs.blocked && rhs.blocked }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
    }

    private func fetchSnapshot(forceLoading: Bool) async {
        if isFetchingSnapshot {
            queuedSnapshotRefresh = true
            return
        }

        isFetchingSnapshot = true
        let previousSnapshot = state.value
        if forceLoading || state.value == nil {
            state = .loading
        }

        do {
            guard let client = await servicesStore.pangolinClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                isFetchingSnapshot = false
                await flushQueuedSnapshotRefresh()
                return
            }

            let orgs = try await client.listOrgs()
            guard let orgId = selectedOrgId ?? orgs.first?.orgId else {
                state = .error(.custom(strings.noOrganizations))
                isFetchingSnapshot = false
                await flushQueuedSnapshotRefresh()
                return
            }

            if selectedOrgId != orgId { selectedOrgId = orgId }

            let snapshot = try await client.fetchSnapshot(orgId: orgId, orgs: orgs)
            state = .loaded(mergeMissingDisabledResources(from: snapshot, previous: previousSnapshot))
        } catch let error as APIError {
            state = .error(error)
        } catch {
            state = .error(.networkError(error))
        }

        isFetchingSnapshot = false
        await flushQueuedSnapshotRefresh()
    }

    private func flushQueuedSnapshotRefresh() async {
        guard queuedSnapshotRefresh else { return }
        queuedSnapshotRefresh = false
        await fetchSnapshot(forceLoading: false)
    }

    private func mergeMissingDisabledResources(from snapshot: PangolinSnapshot, previous: PangolinSnapshot?) -> PangolinSnapshot {
        guard let previous else { return snapshot }
        let missingDisabled = previous.resources
            .filter { !$0.enabled }
            .filter { previousResource in
                !snapshot.resources.contains(where: { $0.resourceId == previousResource.resourceId })
            }
        guard !missingDisabled.isEmpty else { return snapshot }

        let mergedResources = (snapshot.resources + missingDisabled).sorted { lhs, rhs in
            if lhs.enabled != rhs.enabled { return lhs.enabled && !rhs.enabled }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }

        let currentIds = Set(snapshot.resources.map(\.resourceId))
        var mergedTargets = snapshot.targetsByResourceId
        for resource in missingDisabled where !currentIds.contains(resource.resourceId) {
            if let targets = previous.targetsByResourceId[resource.resourceId] {
                mergedTargets[resource.resourceId] = targets
            }
        }

        return PangolinSnapshot(
            orgs: snapshot.orgs,
            selectedOrgId: snapshot.selectedOrgId,
            sites: snapshot.sites,
            siteResources: snapshot.siteResources,
            resources: mergedResources,
            targetsByResourceId: mergedTargets,
            clients: snapshot.clients,
            userDevices: snapshot.userDevices,
            domains: snapshot.domains
        )
    }

    private func savePublicResource(_ input: PangolinPublicResourceUpdateInput) async throws {
        guard let client = await servicesStore.pangolinClient(instanceId: selectedInstanceId) else {
            throw APIError.notConfigured
        }

        _ = try await client.updateResource(
            resourceId: input.resourceId,
            name: input.name.trimmingCharacters(in: .whitespacesAndNewlines),
            enabled: input.enabled,
            sso: input.sso,
            ssl: input.ssl
        )

        if let targetId = input.targetId,
           let siteId = input.targetSiteId,
           !input.targetIp.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
           let port = Int(input.targetPort.trimmingCharacters(in: .whitespacesAndNewlines)) {
            _ = try await client.updateTarget(
                targetId: targetId,
                siteId: siteId,
                ip: input.targetIp.trimmingCharacters(in: .whitespacesAndNewlines),
                port: port,
                enabled: input.targetEnabled
            )
        }

        await fetchSnapshot(forceLoading: false)
    }

    private func createPublicResource(_ input: PangolinPublicResourceCreateInput) async throws {
        guard let client = await servicesStore.pangolinClient(instanceId: selectedInstanceId) else {
            throw APIError.notConfigured
        }

        let orgId = selectedOrgId ?? state.value?.selectedOrgId ?? ""
        guard !orgId.isEmpty else {
            throw APIError.custom(strings.noOrganizations)
        }

        let resource = try await client.createResource(
            orgId: orgId,
            name: input.name.trimmingCharacters(in: .whitespacesAndNewlines),
            resourceProtocol: input.resourceProtocol,
            enabled: input.enabled,
            domainId: input.domainId?.trimmingCharacters(in: .whitespacesAndNewlines),
            subdomain: input.subdomain.trimmingCharacters(in: .whitespacesAndNewlines),
            proxyPort: Int(input.proxyPort.trimmingCharacters(in: .whitespacesAndNewlines))
        )

        do {
            _ = try await client.createTarget(
                resourceId: resource.resourceId,
                siteId: input.targetSiteId,
                ip: input.targetIp.trimmingCharacters(in: .whitespacesAndNewlines),
                port: Int(input.targetPort.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0,
                enabled: input.targetEnabled,
                method: input.targetMethod
            )
        } catch {
            try? await client.deleteResource(resourceId: resource.resourceId)
            throw error
        }

        await fetchSnapshot(forceLoading: false)
    }

    private func savePrivateResource(_ input: PangolinPrivateResourceUpdateInput) async throws {
        guard let client = await servicesStore.pangolinClient(instanceId: selectedInstanceId) else {
            throw APIError.notConfigured
        }

        let bindings = try await client.getSiteResourceBindings(siteResourceId: input.siteResourceId)
        _ = try await client.updateSiteResource(
            siteResourceId: input.siteResourceId,
            bindings: bindings,
            name: input.name.trimmingCharacters(in: .whitespacesAndNewlines),
            siteId: input.siteId,
            mode: input.mode,
            destination: input.destination.trimmingCharacters(in: .whitespacesAndNewlines),
            enabled: input.enabled,
            alias: input.alias.trimmingCharacters(in: .whitespacesAndNewlines),
            tcpPortRangeString: input.tcpPortRangeString.trimmingCharacters(in: .whitespacesAndNewlines),
            udpPortRangeString: input.udpPortRangeString.trimmingCharacters(in: .whitespacesAndNewlines),
            disableIcmp: input.disableIcmp,
            authDaemonPort: Int(input.authDaemonPort.trimmingCharacters(in: .whitespacesAndNewlines)),
            authDaemonMode: input.authDaemonMode
        )

        await fetchSnapshot(forceLoading: false)
    }

    private func createPrivateResource(_ input: PangolinPrivateResourceCreateInput) async throws {
        guard let client = await servicesStore.pangolinClient(instanceId: selectedInstanceId) else {
            throw APIError.notConfigured
        }

        let orgId = selectedOrgId ?? state.value?.selectedOrgId ?? ""
        guard !orgId.isEmpty else {
            throw APIError.custom(strings.noOrganizations)
        }

        _ = try await client.createSiteResource(
            orgId: orgId,
            name: input.name.trimmingCharacters(in: .whitespacesAndNewlines),
            siteId: input.siteId,
            mode: input.mode,
            destination: input.destination.trimmingCharacters(in: .whitespacesAndNewlines),
            enabled: input.enabled,
            alias: input.alias.trimmingCharacters(in: .whitespacesAndNewlines),
            tcpPortRangeString: input.tcpPortRangeString.trimmingCharacters(in: .whitespacesAndNewlines),
            udpPortRangeString: input.udpPortRangeString.trimmingCharacters(in: .whitespacesAndNewlines),
            disableIcmp: input.disableIcmp,
            authDaemonPort: Int(input.authDaemonPort.trimmingCharacters(in: .whitespacesAndNewlines)),
            authDaemonMode: input.authDaemonMode
        )

        await fetchSnapshot(forceLoading: false)
    }

    @MainActor
    private func togglePublicResource(_ resource: PangolinResource) async {
        let actionKey = toggleKey(for: resource)
        guard !togglingResourceIds.contains(actionKey) else { return }
        togglingResourceIds.insert(actionKey)
        defer { togglingResourceIds.remove(actionKey) }

        do {
            guard let client = await servicesStore.pangolinClient(instanceId: selectedInstanceId) else {
                throw APIError.notConfigured
            }

            _ = try await client.updateResource(
                resourceId: resource.resourceId,
                name: resource.name.trimmingCharacters(in: .whitespacesAndNewlines),
                enabled: !resource.enabled,
                sso: resource.sso,
                ssl: resource.ssl
            )

            HapticManager.success()
            await fetchSnapshot(forceLoading: false)
        } catch {
            actionErrorMessage = error.localizedDescription
            HapticManager.error()
        }
    }

    private func trafficValue(_ sites: [PangolinSite], _ clients: [PangolinClientEntry]) -> String {
        let siteMegabytes = sites.reduce(0.0) { partial, site in
            partial + (site.megabytesIn ?? 0) + (site.megabytesOut ?? 0)
        }
        let clientMegabytes = clients.reduce(0.0) { partial, client in
            partial + (client.trafficIn ?? 0) + (client.trafficOut ?? 0)
        }
        return Formatters.formatBytes((siteMegabytes + clientMegabytes) * 1_048_576)
    }

    private func trafficLabel(_ site: PangolinSite) -> String? {
        let incoming = site.megabytesIn ?? 0
        let outgoing = site.megabytesOut ?? 0
        guard incoming > 0 || outgoing > 0 else { return nil }
        return strings.trafficAmount(Formatters.formatBytes((incoming + outgoing) * 1_048_576))
    }

    private func clientTrafficLabel(_ client: PangolinClientEntry) -> String? {
        let incoming = client.trafficIn ?? 0
        let outgoing = client.trafficOut ?? 0
        guard incoming > 0 || outgoing > 0 else { return nil }
        return strings.trafficAmount(Formatters.formatBytes((incoming + outgoing) * 1_048_576))
    }

    private func targetHealthLabel(_ targets: [PangolinTarget]) -> String? {
        guard !targets.isEmpty else { return nil }
        let unhealthy = targets.filter { (($0.hcHealth ?? $0.healthStatus) ?? "").localizedCaseInsensitiveContains("unhealthy") }.count
        if unhealthy > 0 {
            return strings.unhealthyCount(unhealthy)
        }
        let healthy = targets.filter {
            let status = ($0.hcHealth ?? $0.healthStatus) ?? ""
            return status.localizedCaseInsensitiveContains("healthy")
                && !status.localizedCaseInsensitiveContains("unhealthy")
        }.count
        if healthy > 0 {
            return strings.healthyCount(healthy)
        }
        return nil
    }

    private func resourceTint(_ resource: PangolinResource, targets: [PangolinTarget]) -> Color {
        if targets.contains(where: { (($0.hcHealth ?? $0.healthStatus) ?? "").localizedCaseInsensitiveContains("unhealthy") }) {
            return AppTheme.danger
        }
        return resource.enabled ? accent : AppTheme.textMuted
    }

    private func targetTint(_ target: PangolinTarget) -> Color {
        let status = (target.hcHealth ?? target.healthStatus) ?? ""
        if status.localizedCaseInsensitiveContains("unhealthy") {
            return AppTheme.danger
        }
        if status.localizedCaseInsensitiveContains("healthy") {
            return AppTheme.running
        }
        return target.enabled ? accent : AppTheme.textMuted
    }

    private func joined(_ values: String?...) -> String? {
        let filtered = values.compactMap { value -> String? in
            guard let value else { return nil }
            let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmed.isEmpty ? nil : trimmed
        }
        return filtered.isEmpty ? nil : filtered.joined(separator: " • ")
    }

    private func clientSourceLabel(_ source: PangolinClientSource) -> String {
        PangolinEditorCopy.clientSource(source, language: localizer.language)
    }

    private func agentLabel(_ value: String) -> String {
        PangolinEditorCopy.agent(value, language: localizer.language)
    }

    private func toggleKey(for resource: PangolinResource) -> String {
        "public-\(resource.resourceId)"
    }

    private func toggleKey(for resource: PangolinSiteResource) -> String {
        "private-\(resource.siteResourceId)"
    }
}

private struct PangolinPublicResourceEditorSheet: View {
    let resource: PangolinResource
    let targets: [PangolinTarget]
    let sites: [PangolinSite]
    let strings: PangolinStrings
    let onSave: (PangolinPublicResourceUpdateInput) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(Localizer.self) private var localizer

    @State private var name: String
    @State private var enabled: Bool
    @State private var sso: Bool
    @State private var ssl: Bool
    @State private var selectedTargetId: Int
    @State private var selectedSiteId: Int
    @State private var targetIp: String
    @State private var targetPort: String
    @State private var targetEnabled: Bool
    @State private var isSaving = false
    @State private var errorMessage: String?

    init(
        resource: PangolinResource,
        targets: [PangolinTarget],
        sites: [PangolinSite],
        strings: PangolinStrings,
        onSave: @escaping (PangolinPublicResourceUpdateInput) async throws -> Void
    ) {
        self.resource = resource
        self.targets = targets
        self.sites = sites
        self.strings = strings
        self.onSave = onSave

        let initialTarget = targets.first(where: \.enabled) ?? targets.first
        _name = State(initialValue: resource.name)
        _enabled = State(initialValue: resource.enabled)
        _sso = State(initialValue: resource.sso)
        _ssl = State(initialValue: resource.ssl)
        _selectedTargetId = State(initialValue: initialTarget?.targetId ?? 0)
        _selectedSiteId = State(initialValue: initialTarget?.siteId ?? sites.first?.siteId ?? 0)
        _targetIp = State(initialValue: initialTarget?.ip ?? "")
        _targetPort = State(initialValue: initialTarget.map { String($0.port) } ?? "")
        _targetEnabled = State(initialValue: initialTarget?.enabled ?? true)
    }

    private var canSave: Bool {
        let hasValidTarget = selectedTargetId == 0 || (!targetIp.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && Int(targetPort) != nil && selectedSiteId > 0)
        return !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && hasValidTarget && !isSaving
    }

    var body: some View {
        NavigationStack {
            Form {
                if let errorMessage, !errorMessage.isEmpty {
                    Section {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(AppTheme.danger)
                    }
                }

                Section(PangolinEditorCopy.editPublicResource(localizer.language)) {
                    TextField(PangolinEditorCopy.name(localizer.language), text: $name)
                    Toggle(strings.enabled, isOn: $enabled)
                    Toggle(PangolinEditorCopy.pangolinSso(localizer.language), isOn: $sso)
                    Toggle(PangolinEditorCopy.tls(localizer.language), isOn: $ssl)
                }

                if !targets.isEmpty {
                    Section(PangolinEditorCopy.target(localizer.language)) {
                        if targets.count > 1 {
                            Picker(PangolinEditorCopy.target(localizer.language), selection: $selectedTargetId) {
                                ForEach(targets) { target in
                                    Text("\(target.ip):\(target.port)").tag(target.targetId)
                                }
                            }
                        }

                        Picker(strings.site, selection: $selectedSiteId) {
                            ForEach(sites) { site in
                                Text(site.name).tag(site.siteId)
                            }
                        }

                        TextField(PangolinEditorCopy.targetIp(localizer.language), text: $targetIp)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .keyboardType(.numbersAndPunctuation)

                        TextField(PangolinEditorCopy.targetPort(localizer.language), text: $targetPort)
                            .keyboardType(.numberPad)

                        Toggle(PangolinEditorCopy.targetEnabled(localizer.language), isOn: $targetEnabled)
                    }
                }
            }
            .navigationTitle(PangolinEditorCopy.editPublicResource(localizer.language))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                        .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) { Task { await save() } }
                        .disabled(!canSave)
                        .fontWeight(.semibold)
                }
            }
            .onChange(of: selectedTargetId) { _, newValue in
                guard let target = targets.first(where: { $0.targetId == newValue }) else { return }
                selectedSiteId = target.siteId ?? selectedSiteId
                targetIp = target.ip
                targetPort = String(target.port)
                targetEnabled = target.enabled
            }
        }
    }

    private func save() async {
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            try await onSave(
                PangolinPublicResourceUpdateInput(
                    resourceId: resource.resourceId,
                    name: name,
                    enabled: enabled,
                    sso: sso,
                    ssl: ssl,
                    targetId: selectedTargetId == 0 ? nil : selectedTargetId,
                    targetSiteId: selectedTargetId == 0 ? nil : selectedSiteId,
                    targetIp: targetIp,
                    targetPort: targetPort,
                    targetEnabled: targetEnabled
                )
            )
            HapticManager.success()
            dismiss()
        } catch let error as APIError {
            errorMessage = error.localizedDescription
            HapticManager.error()
        } catch {
            errorMessage = error.localizedDescription
            HapticManager.error()
        }
    }
}

private struct PangolinPrivateResourceEditorSheet: View {
    let resource: PangolinSiteResource
    let sites: [PangolinSite]
    let strings: PangolinStrings
    let onSave: (PangolinPrivateResourceUpdateInput) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(Localizer.self) private var localizer

    @State private var name: String
    @State private var siteId: Int
    @State private var mode: String
    @State private var destination: String
    @State private var enabled: Bool
    @State private var alias: String
    @State private var tcpPorts: String
    @State private var udpPorts: String
    @State private var disableIcmp: Bool
    @State private var authDaemonPort: String
    @State private var authDaemonMode: String
    @State private var isSaving = false
    @State private var errorMessage: String?

    init(
        resource: PangolinSiteResource,
        sites: [PangolinSite],
        strings: PangolinStrings,
        onSave: @escaping (PangolinPrivateResourceUpdateInput) async throws -> Void
    ) {
        self.resource = resource
        self.sites = sites
        self.strings = strings
        self.onSave = onSave

        _name = State(initialValue: resource.name)
        _siteId = State(initialValue: resource.siteId)
        _mode = State(initialValue: resource.mode ?? "host")
        _destination = State(initialValue: resource.destination ?? "")
        _enabled = State(initialValue: resource.enabled)
        _alias = State(initialValue: resource.alias ?? "")
        _tcpPorts = State(initialValue: resource.tcpPortRangeString ?? "")
        _udpPorts = State(initialValue: resource.udpPortRangeString ?? "")
        _disableIcmp = State(initialValue: resource.disableIcmp ?? false)
        _authDaemonPort = State(initialValue: resource.authDaemonPort.map(String.init) ?? "")
        _authDaemonMode = State(initialValue: resource.authDaemonMode ?? "")
    }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !destination.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            siteId > 0 &&
            ["host", "cidr"].contains(mode) &&
            (authDaemonPort.isEmpty || Int(authDaemonPort) != nil) &&
            !isSaving
    }

    var body: some View {
        NavigationStack {
            Form {
                if let errorMessage, !errorMessage.isEmpty {
                    Section {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(AppTheme.danger)
                    }
                }

                Section(PangolinEditorCopy.editPrivateResource(localizer.language)) {
                    TextField(PangolinEditorCopy.name(localizer.language), text: $name)
                    Picker(strings.site, selection: $siteId) {
                        ForEach(sites) { site in
                            Text(site.name).tag(site.siteId)
                        }
                    }
                    Picker(PangolinEditorCopy.mode(localizer.language), selection: $mode) {
                        Text(PangolinEditorCopy.hostMode(localizer.language)).tag("host")
                        Text(PangolinEditorCopy.cidrMode(localizer.language)).tag("cidr")
                    }
                    TextField(PangolinEditorCopy.destination(localizer.language), text: $destination)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(PangolinEditorCopy.alias(localizer.language), text: $alias)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(PangolinEditorCopy.tcpPorts(localizer.language), text: $tcpPorts)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(PangolinEditorCopy.udpPorts(localizer.language), text: $udpPorts)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(PangolinEditorCopy.authDaemonPort(localizer.language), text: $authDaemonPort)
                        .keyboardType(.numberPad)
                    Picker(PangolinEditorCopy.authDaemonMode(localizer.language), selection: $authDaemonMode) {
                        Text(PangolinEditorCopy.none(localizer.language)).tag("")
                        Text(PangolinEditorCopy.siteMode(localizer.language)).tag("site")
                        Text(PangolinEditorCopy.remoteMode(localizer.language)).tag("remote")
                    }
                    Toggle(PangolinEditorCopy.disableIcmp(localizer.language), isOn: $disableIcmp)
                }
            }
            .navigationTitle(PangolinEditorCopy.editPrivateResource(localizer.language))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                        .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) { Task { await save() } }
                        .disabled(!canSave)
                        .fontWeight(.semibold)
                }
            }
        }
    }

    private func save() async {
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            try await onSave(
                PangolinPrivateResourceUpdateInput(
                    siteResourceId: resource.siteResourceId,
                    name: name,
                    siteId: siteId,
                    mode: mode,
                    destination: destination,
                    enabled: enabled,
                    alias: alias,
                    tcpPortRangeString: tcpPorts,
                    udpPortRangeString: udpPorts,
                    disableIcmp: disableIcmp,
                    authDaemonPort: authDaemonPort,
                    authDaemonMode: authDaemonMode.isEmpty ? nil : authDaemonMode
                )
            )
            HapticManager.success()
            dismiss()
        } catch let error as APIError {
            errorMessage = error.localizedDescription
            HapticManager.error()
        } catch {
            errorMessage = error.localizedDescription
            HapticManager.error()
        }
    }
}

private struct PangolinPrivateResourceCreateSheet: View {
    let sites: [PangolinSite]
    let strings: PangolinStrings
    let onSave: (PangolinPrivateResourceCreateInput) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(Localizer.self) private var localizer

    @State private var name = ""
    @State private var siteId: Int
    @State private var mode = "host"
    @State private var destination = ""
    @State private var enabled = true
    @State private var alias = ""
    @State private var tcpPorts = "*"
    @State private var udpPorts = "*"
    @State private var disableIcmp = false
    @State private var authDaemonPort = ""
    @State private var authDaemonMode = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

    init(
        sites: [PangolinSite],
        strings: PangolinStrings,
        onSave: @escaping (PangolinPrivateResourceCreateInput) async throws -> Void
    ) {
        self.sites = sites
        self.strings = strings
        self.onSave = onSave
        _siteId = State(initialValue: sites.first?.siteId ?? 0)
    }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            !destination.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            siteId > 0 &&
            ["host", "cidr"].contains(mode) &&
            (authDaemonPort.isEmpty || Int(authDaemonPort) != nil) &&
            !isSaving
    }

    var body: some View {
        NavigationStack {
            Form {
                if let errorMessage, !errorMessage.isEmpty {
                    Section {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(AppTheme.danger)
                    }
                }

                Section(strings.createPrivateResource) {
                    TextField(PangolinEditorCopy.name(localizer.language), text: $name)
                    Picker(strings.site, selection: $siteId) {
                        ForEach(sites) { site in
                            Text(site.name).tag(site.siteId)
                        }
                    }
                    Picker(PangolinEditorCopy.mode(localizer.language), selection: $mode) {
                        Text(PangolinEditorCopy.hostMode(localizer.language)).tag("host")
                        Text(PangolinEditorCopy.cidrMode(localizer.language)).tag("cidr")
                    }
                    TextField(PangolinEditorCopy.destination(localizer.language), text: $destination)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(PangolinEditorCopy.alias(localizer.language), text: $alias)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(PangolinEditorCopy.tcpPorts(localizer.language), text: $tcpPorts)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(PangolinEditorCopy.udpPorts(localizer.language), text: $udpPorts)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField(PangolinEditorCopy.authDaemonPort(localizer.language), text: $authDaemonPort)
                        .keyboardType(.numberPad)
                    Picker(PangolinEditorCopy.authDaemonMode(localizer.language), selection: $authDaemonMode) {
                        Text(PangolinEditorCopy.none(localizer.language)).tag("")
                        Text(PangolinEditorCopy.siteMode(localizer.language)).tag("site")
                        Text(PangolinEditorCopy.remoteMode(localizer.language)).tag("remote")
                    }
                    Toggle(PangolinEditorCopy.disableIcmp(localizer.language), isOn: $disableIcmp)
                }
            }
            .navigationTitle(strings.createPrivateResource)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                        .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) { Task { await save() } }
                        .disabled(!canSave)
                        .fontWeight(.semibold)
                }
            }
        }
    }

    private func save() async {
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            try await onSave(
                PangolinPrivateResourceCreateInput(
                    name: name,
                    siteId: siteId,
                    mode: mode,
                    destination: destination,
                    enabled: enabled,
                    alias: alias,
                    tcpPortRangeString: tcpPorts,
                    udpPortRangeString: udpPorts,
                    disableIcmp: disableIcmp,
                    authDaemonPort: authDaemonPort,
                    authDaemonMode: authDaemonMode.isEmpty ? nil : authDaemonMode
                )
            )
            HapticManager.success()
            dismiss()
        } catch let error as APIError {
            errorMessage = error.localizedDescription
            HapticManager.error()
        } catch {
            errorMessage = error.localizedDescription
            HapticManager.error()
        }
    }
}

private struct PangolinPublicResourceCreateSheet: View {
    let sites: [PangolinSite]
    let domains: [PangolinDomain]
    let strings: PangolinStrings
    let onSave: (PangolinPublicResourceCreateInput) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(Localizer.self) private var localizer

    @State private var name = ""
    @State private var protocolName = "http"
    @State private var enabled = true
    @State private var domainId: String
    @State private var subdomain = ""
    @State private var proxyPort = ""
    @State private var siteId: Int
    @State private var targetIp = ""
    @State private var targetPort = ""
    @State private var targetEnabled = true
    @State private var targetMethod = "http"
    @State private var isSaving = false
    @State private var errorMessage: String?

    init(
        sites: [PangolinSite],
        domains: [PangolinDomain],
        strings: PangolinStrings,
        onSave: @escaping (PangolinPublicResourceCreateInput) async throws -> Void
    ) {
        self.sites = sites
        self.domains = domains
        self.strings = strings
        self.onSave = onSave
        _domainId = State(initialValue: domains.first?.domainId ?? "")
        _siteId = State(initialValue: sites.first?.siteId ?? 0)
    }

    private var isHttp: Bool { protocolName == "http" }

    private var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            siteId > 0 &&
            !targetIp.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
            Int(targetPort) != nil &&
            (!isHttp || !domainId.isEmpty) &&
            (isHttp || Int(proxyPort) != nil) &&
            !isSaving
    }

    var body: some View {
        NavigationStack {
            Form {
                if let errorMessage, !errorMessage.isEmpty {
                    Section {
                        Text(errorMessage)
                            .font(.footnote)
                            .foregroundStyle(AppTheme.danger)
                    }
                }

                Section(strings.createPublicResource) {
                    TextField(PangolinEditorCopy.name(localizer.language), text: $name)
                    Picker(strings.protocolLabel, selection: $protocolName) {
                        Text(strings.httpResource).tag("http")
                        Text(strings.tcpResource).tag("tcp")
                        Text(strings.udpResource).tag("udp")
                    }
                    if isHttp {
                        Picker(strings.domainLabel, selection: $domainId) {
                            ForEach(domains) { domain in
                                Text(domain.baseDomain).tag(domain.domainId)
                            }
                        }
                        TextField(strings.subdomainLabel, text: $subdomain)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    } else {
                        TextField(strings.proxyPortLabel, text: $proxyPort)
                            .keyboardType(.numberPad)
                    }
                    Toggle(strings.enabled, isOn: $enabled)
                }

                Section(PangolinEditorCopy.target(localizer.language)) {
                    Picker(strings.site, selection: $siteId) {
                        ForEach(sites) { site in
                            Text(site.name).tag(site.siteId)
                        }
                    }
                    TextField(PangolinEditorCopy.targetIp(localizer.language), text: $targetIp)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.numbersAndPunctuation)
                    TextField(PangolinEditorCopy.targetPort(localizer.language), text: $targetPort)
                        .keyboardType(.numberPad)
                    if isHttp {
                        Picker(strings.backendMethodLabel, selection: $targetMethod) {
                            Text(strings.httpMethod).tag("http")
                            Text(strings.httpsMethod).tag("https")
                            Text(strings.h2cMethod).tag("h2c")
                        }
                    }
                    Toggle(PangolinEditorCopy.targetEnabled(localizer.language), isOn: $targetEnabled)
                }
            }
            .navigationTitle(strings.createPublicResource)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                        .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) { Task { await save() } }
                        .disabled(!canSave)
                        .fontWeight(.semibold)
                }
            }
        }
    }

    private func save() async {
        isSaving = true
        errorMessage = nil
        defer { isSaving = false }

        do {
            try await onSave(
                PangolinPublicResourceCreateInput(
                    name: name,
                    resourceProtocol: protocolName,
                    enabled: enabled,
                    domainId: isHttp ? domainId : nil,
                    subdomain: subdomain,
                    proxyPort: proxyPort,
                    targetSiteId: siteId,
                    targetIp: targetIp,
                    targetPort: targetPort,
                    targetEnabled: targetEnabled,
                    targetMethod: isHttp ? targetMethod : nil
                )
            )
            HapticManager.success()
            dismiss()
        } catch let error as APIError {
            errorMessage = error.localizedDescription
            HapticManager.error()
        } catch {
            errorMessage = error.localizedDescription
            HapticManager.error()
        }
    }
}

private enum PangolinEditorCopy {
    static func editPublicResource(_ language: Language) -> String {
        switch language {
        case .it: return "Modifica risorsa pubblica"
        case .fr: return "Modifier la ressource publique"
        case .es: return "Editar recurso publico"
        case .de: return "Offentliche Ressource bearbeiten"
        case .en: return "Edit Public Resource"
        }
    }

    static func editPrivateResource(_ language: Language) -> String {
        switch language {
        case .it: return "Modifica risorsa privata"
        case .fr: return "Modifier la ressource privee"
        case .es: return "Editar recurso privado"
        case .de: return "Private Ressource bearbeiten"
        case .en: return "Edit Private Resource"
        }
    }

    static func name(_ language: Language) -> String {
        switch language {
        case .it: return "Nome"
        case .fr: return "Nom"
        case .es: return "Nombre"
        case .de: return "Name"
        case .en: return "Name"
        }
    }

    static func target(_ language: Language) -> String {
        switch language {
        case .it: return "Target"
        case .fr: return "Cible"
        case .es: return "Destino"
        case .de: return "Ziel"
        case .en: return "Target"
        }
    }

    static func targetIp(_ language: Language) -> String {
        switch language {
        case .it: return "IP target"
        case .fr: return "IP cible"
        case .es: return "IP del destino"
        case .de: return "Ziel-IP"
        case .en: return "Target IP"
        }
    }

    static func targetPort(_ language: Language) -> String {
        switch language {
        case .it: return "Porta target"
        case .fr: return "Port cible"
        case .es: return "Puerto del destino"
        case .de: return "Ziel-Port"
        case .en: return "Target Port"
        }
    }

    static func targetEnabled(_ language: Language) -> String {
        switch language {
        case .it: return "Target attivo"
        case .fr: return "Cible active"
        case .es: return "Destino activo"
        case .de: return "Ziel aktiv"
        case .en: return "Target Enabled"
        }
    }

    static func tls(_ language: Language) -> String {
        "TLS"
    }

    static func pangolinSso(_ language: Language) -> String {
        switch language {
        case .it: return "Pangolin SSO"
        case .fr: return "SSO Pangolin"
        case .es: return "SSO de Pangolin"
        case .de: return "Pangolin SSO"
        case .en: return "Pangolin SSO"
        }
    }

    static func mode(_ language: Language) -> String {
        switch language {
        case .it: return "Modalita"
        case .fr: return "Mode"
        case .es: return "Modo"
        case .de: return "Modus"
        case .en: return "Mode"
        }
    }

    static func destination(_ language: Language) -> String {
        switch language {
        case .it: return "Destinazione"
        case .fr: return "Destination"
        case .es: return "Destino"
        case .de: return "Ziel"
        case .en: return "Destination"
        }
    }

    static func alias(_ language: Language) -> String {
        "Alias"
    }

    static func tcpPorts(_ language: Language) -> String {
        switch language {
        case .it: return "Porte TCP"
        case .fr: return "Ports TCP"
        case .es: return "Puertos TCP"
        case .de: return "TCP-Ports"
        case .en: return "TCP Ports"
        }
    }

    static func udpPorts(_ language: Language) -> String {
        switch language {
        case .it: return "Porte UDP"
        case .fr: return "Ports UDP"
        case .es: return "Puertos UDP"
        case .de: return "UDP-Ports"
        case .en: return "UDP Ports"
        }
    }

    static func authDaemonPort(_ language: Language) -> String {
        switch language {
        case .it: return "Porta auth daemon"
        case .fr: return "Port auth daemon"
        case .es: return "Puerto auth daemon"
        case .de: return "Auth-Daemon-Port"
        case .en: return "Auth Daemon Port"
        }
    }

    static func authDaemonMode(_ language: Language) -> String {
        switch language {
        case .it: return "Modalita auth daemon"
        case .fr: return "Mode auth daemon"
        case .es: return "Modo auth daemon"
        case .de: return "Auth-Daemon-Modus"
        case .en: return "Auth Daemon Mode"
        }
    }

    static func disableIcmp(_ language: Language) -> String {
        switch language {
        case .it: return "Disabilita ICMP"
        case .fr: return "Desactiver ICMP"
        case .es: return "Desactivar ICMP"
        case .de: return "ICMP deaktivieren"
        case .en: return "Disable ICMP"
        }
    }

    static func hostMode(_ language: Language) -> String {
        "Host"
    }

    static func cidrMode(_ language: Language) -> String {
        "CIDR"
    }

    static func siteMode(_ language: Language) -> String {
        switch language {
        case .it: return "Sito"
        case .fr: return "Site"
        case .es: return "Sitio"
        case .de: return "Site"
        case .en: return "Site"
        }
    }

    static func remoteMode(_ language: Language) -> String {
        switch language {
        case .it: return "Remoto"
        case .fr: return "Distant"
        case .es: return "Remoto"
        case .de: return "Remote"
        case .en: return "Remote"
        }
    }

    static func none(_ language: Language) -> String {
        switch language {
        case .it: return "Nessuno"
        case .fr: return "Aucun"
        case .es: return "Ninguno"
        case .de: return "Keine"
        case .en: return "None"
        }
    }

    static func clientSource(_ source: PangolinClientSource, language: Language) -> String {
        switch (source, language) {
        case (.machine, .it): return "Client macchina"
        case (.machine, .fr): return "Client machine"
        case (.machine, .es): return "Cliente de maquina"
        case (.machine, .de): return "Maschinen-Client"
        case (.machine, .en): return "Machine Client"
        case (.userDevice, .it): return "Dispositivo utente"
        case (.userDevice, .fr): return "Appareil utilisateur"
        case (.userDevice, .es): return "Dispositivo de usuario"
        case (.userDevice, .de): return "Benutzergerat"
        case (.userDevice, .en): return "User Device"
        }
    }

    static func agent(_ value: String, language: Language) -> String {
        switch language {
        case .it: return "Agent \(value)"
        case .fr: return "Agent \(value)"
        case .es: return "Agente \(value)"
        case .de: return "Agent \(value)"
        case .en: return "Agent \(value)"
        }
    }
}

struct PangolinStrings {
    let serviceDescription: String
    let loginHint: String
    let orgIdPlaceholder: String
    let sitesClientsLabel: String
    let overviewSubtitle: String
    let organizations: String
    let sites: String
    let privateResources: String
    let createPrivateResource: String
    let publicResources: String
    let createPublicResource: String
    let clients: String
    let domains: String
    let traffic: String
    let ingressEgress: String
    let org: String
    let utility: String
    let billing: String
    let enabled: String
    let disabled: String
    let enableAction: String
    let disableAction: String
    let online: String
    let offline: String
    let blocked: String
    let archived: String
    let pending: String
    let verified: String
    let failed: String
    let managed: String
    let manual: String
    let wildcard: String
    let whitelist: String
    let healthCheck: String
    let agentUpdate: String
    let newtUpdate: String
    let icmpOff: String
    let noOrganizations: String
    let noSites: String
    let noPrivateResources: String
    let noPublicResources: String
    let noClients: String
    let noDomains: String
    let site: String
    let protocolLabel: String
    let domainLabel: String
    let subdomainLabel: String
    let backendMethodLabel: String
    let proxyPortLabel: String
    let httpResource: String
    let tcpResource: String
    let udpResource: String
    let httpMethod: String
    let httpsMethod: String
    let h2cMethod: String
    let healthy: String
    let unhealthy: String
    let allSitesOnline: String
    let onlineSitesFormat: String
    let onlineCountFormat: String
    let enabledCountFormat: String
    let verifiedCountFormat: String
    let targetsCountFormat: String
    let linkedSitesFormat: String
    let triesFormat: String
    let healthyCountFormat: String
    let unhealthyCountFormat: String
    let newtVersionFormat: String
    let exitNodeFormat: String
    let endpointFormat: String
    let proxyPortFormat: String
    let destinationPortFormat: String
    let aliasFormat: String
    let dnsFormat: String
    let tcpPortsFormat: String
    let udpPortsFormat: String
    let authDaemonPortFormat: String
    let olmVersionFormat: String
    let resolverFormat: String
    let rewriteFormat: String
    let healthPathFormat: String
    let priorityFormat: String
    let trafficAmountFormat: String

    func onlineSites(_ count: Int) -> String { String(format: onlineSitesFormat, count) }
    func onlineCount(_ count: Int) -> String { String(format: onlineCountFormat, count) }
    func enabledCount(_ count: Int) -> String { String(format: enabledCountFormat, count) }
    func verifiedCount(_ count: Int) -> String { String(format: verifiedCountFormat, count) }
    func targetsCount(_ count: Int) -> String { String(format: targetsCountFormat, count) }
    func linkedSites(_ count: Int) -> String { String(format: linkedSitesFormat, count) }
    func tries(_ count: Int) -> String { String(format: triesFormat, count) }
    func healthyCount(_ count: Int) -> String { String(format: healthyCountFormat, count) }
    func unhealthyCount(_ count: Int) -> String { String(format: unhealthyCountFormat, count) }
    func newtVersion(_ value: String) -> String { String(format: newtVersionFormat, value) }
    func exitNode(_ value: String) -> String { String(format: exitNodeFormat, value) }
    func endpoint(_ value: String) -> String { String(format: endpointFormat, value) }
    func proxyPort(_ value: Int) -> String { String(format: proxyPortFormat, value) }
    func destinationPort(_ value: Int) -> String { String(format: destinationPortFormat, value) }
    func alias(_ value: String) -> String { String(format: aliasFormat, value) }
    func dns(_ value: String) -> String { String(format: dnsFormat, value) }
    func tcpPorts(_ value: String) -> String { String(format: tcpPortsFormat, value) }
    func udpPorts(_ value: String) -> String { String(format: udpPortsFormat, value) }
    func authDaemonPort(_ value: Int) -> String { String(format: authDaemonPortFormat, value) }
    func olmVersion(_ value: String) -> String { String(format: olmVersionFormat, value) }
    func resolver(_ value: String) -> String { String(format: resolverFormat, value) }
    func rewrite(_ value: String) -> String { String(format: rewriteFormat, value) }
    func healthPath(_ value: String) -> String { String(format: healthPathFormat, value) }
    func priority(_ value: Int) -> String { String(format: priorityFormat, value) }
    func trafficAmount(_ value: String) -> String { String(format: trafficAmountFormat, value) }
    func approvalState(_ value: String) -> String {
        switch value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "approved": return verified
        case "pending": return pending
        case "blocked": return blocked
        case "archived": return archived
        default: return value.capitalized
        }
    }
    func healthStatus(_ value: String) -> String {
        let lowered = value.lowercased()
        if lowered.contains("unhealthy") { return unhealthy }
        if lowered.contains("healthy") { return healthy }
        if lowered.contains("pending") { return pending }
        return value.capitalized
    }
}

extension PangolinStrings {
    static func forLanguage(_ language: Language) -> PangolinStrings {
        switch language {
        case .it:
            return PangolinStrings(
                serviceDescription: "Reverse proxy, tunneling e networking zero-trust",
                loginHint: "Usa una Integration API key di Pangolin. Per una chiave con accesso a una sola organizzazione (Org-Scoped Key), inserisci anche l'ID Organizzazione.",
                orgIdPlaceholder: "ID Organizzazione (opzionale)",
                sitesClientsLabel: "Siti / client",
                overviewSubtitle: "Panoramica di reverse proxy, tunneling e rete zero-trust",
                organizations: "Organizzazioni",
                sites: "Siti",
                privateResources: "Risorse private",
                createPrivateResource: "Crea risorsa privata",
                publicResources: "Risorse pubbliche",
                createPublicResource: "Crea risorsa pubblica",
                clients: "Client",
                domains: "Domini",
                traffic: "Traffico",
                ingressEgress: "Ingresso + uscita",
                org: "Org",
                utility: "Utility",
                billing: "Billing",
                enabled: "Attivo",
                disabled: "Disattivato",
                enableAction: "Attiva risorsa",
                disableAction: "Disattiva risorsa",
                online: "Online",
                offline: "Offline",
                blocked: "Bloccato",
                archived: "Archiviato",
                pending: "In attesa",
                verified: "Verificato",
                failed: "Errore",
                managed: "Gestito",
                manual: "Manuale",
                wildcard: "Wildcard",
                whitelist: "Whitelist",
                healthCheck: "Health check",
                agentUpdate: "Aggiornamento agent",
                newtUpdate: "Aggiornamento Newt",
                icmpOff: "ICMP disattivato",
                noOrganizations: "Nessuna organizzazione Pangolin disponibile per questa API key",
                noSites: "Nessun sito Pangolin trovato",
                noPrivateResources: "Nessuna risorsa privata configurata",
                noPublicResources: "Nessuna risorsa pubblica configurata",
                noClients: "Nessun client registrato",
                noDomains: "Nessun dominio gestito",
                site: "Sito",
                protocolLabel: "Protocollo",
                domainLabel: "Dominio",
                subdomainLabel: "Sottodominio",
                backendMethodLabel: "Metodo backend",
                proxyPortLabel: "Porta proxy",
                httpResource: "HTTP",
                tcpResource: "TCP",
                udpResource: "UDP",
                httpMethod: "HTTP",
                httpsMethod: "HTTPS",
                h2cMethod: "H2C",
                healthy: "Sano",
                unhealthy: "Non sano",
                allSitesOnline: "Tutti i siti online",
                onlineSitesFormat: "%d siti online",
                onlineCountFormat: "%d online",
                enabledCountFormat: "%d attivi",
                verifiedCountFormat: "%d verificati",
                targetsCountFormat: "%d target",
                linkedSitesFormat: "%d siti collegati",
                triesFormat: "%d tentativi",
                healthyCountFormat: "%d sani",
                unhealthyCountFormat: "%d non sani",
                newtVersionFormat: "Newt %@",
                exitNodeFormat: "Exit %@",
                endpointFormat: "Endpoint %@",
                proxyPortFormat: "Proxy %d",
                destinationPortFormat: "Dest %d",
                aliasFormat: "Alias %@",
                dnsFormat: "DNS %@",
                tcpPortsFormat: "TCP %@",
                udpPortsFormat: "UDP %@",
                authDaemonPortFormat: "Authd %d",
                olmVersionFormat: "OLM %@",
                resolverFormat: "Resolver %@",
                rewriteFormat: "Rewrite %@",
                healthPathFormat: "HC %@",
                priorityFormat: "Priorità %d",
                trafficAmountFormat: "Traffico %@"
            )
        case .fr:
            return PangolinStrings(
                serviceDescription: "Reverse proxy, tunneling et réseau zero-trust",
                loginHint: "Utilisez une clé Integration API Pangolin. Pour une clé limitée à une organisation (Org-Scoped Key), indiquez aussi l'ID d'organisation.",
                orgIdPlaceholder: "ID d'organisation (optionnel)",
                sitesClientsLabel: "Sites / clients",
                overviewSubtitle: "Vue d’ensemble du reverse proxy, du tunneling et du réseau zero-trust",
                organizations: "Organisations",
                sites: "Sites",
                privateResources: "Ressources privées",
                createPrivateResource: "Créer une ressource privée",
                publicResources: "Ressources publiques",
                createPublicResource: "Créer une ressource publique",
                clients: "Clients",
                domains: "Domaines",
                traffic: "Trafic",
                ingressEgress: "Entrant + sortant",
                org: "Org",
                utility: "Utility",
                billing: "Facturation",
                enabled: "Activé",
                disabled: "Désactivé",
                enableAction: "Activer la ressource",
                disableAction: "Désactiver la ressource",
                online: "En ligne",
                offline: "Hors ligne",
                blocked: "Bloqué",
                archived: "Archivé",
                pending: "En attente",
                verified: "Vérifié",
                failed: "Échec",
                managed: "Géré",
                manual: "Manuel",
                wildcard: "Wildcard",
                whitelist: "Liste blanche",
                healthCheck: "Contrôle de santé",
                agentUpdate: "Mise à jour agent",
                newtUpdate: "Mise à jour Newt",
                icmpOff: "ICMP désactivé",
                noOrganizations: "Aucune organisation Pangolin disponible pour cette clé API",
                noSites: "Aucun site Pangolin trouvé",
                noPrivateResources: "Aucune ressource privée configurée",
                noPublicResources: "Aucune ressource publique configurée",
                noClients: "Aucun client inscrit",
                noDomains: "Aucun domaine géré",
                site: "Site",
                protocolLabel: "Protocole",
                domainLabel: "Domaine",
                subdomainLabel: "Sous-domaine",
                backendMethodLabel: "Méthode backend",
                proxyPortLabel: "Port proxy",
                httpResource: "HTTP",
                tcpResource: "TCP",
                udpResource: "UDP",
                httpMethod: "HTTP",
                httpsMethod: "HTTPS",
                h2cMethod: "H2C",
                healthy: "Sain",
                unhealthy: "Dégradé",
                allSitesOnline: "Tous les sites sont en ligne",
                onlineSitesFormat: "%d sites en ligne",
                onlineCountFormat: "%d en ligne",
                enabledCountFormat: "%d activés",
                verifiedCountFormat: "%d vérifiés",
                targetsCountFormat: "%d cibles",
                linkedSitesFormat: "%d sites liés",
                triesFormat: "%d tentatives",
                healthyCountFormat: "%d sains",
                unhealthyCountFormat: "%d dégradés",
                newtVersionFormat: "Newt %@",
                exitNodeFormat: "Sortie %@",
                endpointFormat: "Endpoint %@",
                proxyPortFormat: "Proxy %d",
                destinationPortFormat: "Destination %d",
                aliasFormat: "Alias %@",
                dnsFormat: "DNS %@",
                tcpPortsFormat: "TCP %@",
                udpPortsFormat: "UDP %@",
                authDaemonPortFormat: "Authd %d",
                olmVersionFormat: "OLM %@",
                resolverFormat: "Résolveur %@",
                rewriteFormat: "Réécriture %@",
                healthPathFormat: "HC %@",
                priorityFormat: "Priorité %d",
                trafficAmountFormat: "Trafic %@"
            )
        case .es:
            return PangolinStrings(
                serviceDescription: "Reverse proxy, tunneling y red zero-trust",
                loginHint: "Usa una clave Integration API de Pangolin. Para una clave de ámbito de organización (Org-Scoped Key), introduce también el ID de organización.",
                orgIdPlaceholder: "ID de organización (opcional)",
                sitesClientsLabel: "Sitios / clientes",
                overviewSubtitle: "Resumen de reverse proxy, tunneling y red zero-trust",
                organizations: "Organizaciones",
                sites: "Sitios",
                privateResources: "Recursos privados",
                createPrivateResource: "Crear recurso privado",
                publicResources: "Recursos públicos",
                createPublicResource: "Crear recurso público",
                clients: "Clientes",
                domains: "Dominios",
                traffic: "Tráfico",
                ingressEgress: "Entrada + salida",
                org: "Org",
                utility: "Utility",
                billing: "Facturación",
                enabled: "Activo",
                disabled: "Desactivado",
                enableAction: "Activar recurso",
                disableAction: "Desactivar recurso",
                online: "En línea",
                offline: "Sin conexión",
                blocked: "Bloqueado",
                archived: "Archivado",
                pending: "Pendiente",
                verified: "Verificado",
                failed: "Error",
                managed: "Gestionado",
                manual: "Manual",
                wildcard: "Wildcard",
                whitelist: "Lista blanca",
                healthCheck: "Health check",
                agentUpdate: "Actualización del agente",
                newtUpdate: "Actualización de Newt",
                icmpOff: "ICMP desactivado",
                noOrganizations: "No hay organizaciones Pangolin disponibles para esta API key",
                noSites: "No se encontraron sitios Pangolin",
                noPrivateResources: "No hay recursos privados configurados",
                noPublicResources: "No hay recursos públicos configurados",
                noClients: "No hay clientes registrados",
                noDomains: "No hay dominios gestionados",
                site: "Sitio",
                protocolLabel: "Protocolo",
                domainLabel: "Dominio",
                subdomainLabel: "Subdominio",
                backendMethodLabel: "Método backend",
                proxyPortLabel: "Puerto proxy",
                httpResource: "HTTP",
                tcpResource: "TCP",
                udpResource: "UDP",
                httpMethod: "HTTP",
                httpsMethod: "HTTPS",
                h2cMethod: "H2C",
                healthy: "Saludable",
                unhealthy: "No saludable",
                allSitesOnline: "Todos los sitios en línea",
                onlineSitesFormat: "%d sitios en línea",
                onlineCountFormat: "%d en línea",
                enabledCountFormat: "%d activos",
                verifiedCountFormat: "%d verificados",
                targetsCountFormat: "%d destinos",
                linkedSitesFormat: "%d sitios vinculados",
                triesFormat: "%d intentos",
                healthyCountFormat: "%d saludables",
                unhealthyCountFormat: "%d no saludables",
                newtVersionFormat: "Newt %@",
                exitNodeFormat: "Salida %@",
                endpointFormat: "Endpoint %@",
                proxyPortFormat: "Proxy %d",
                destinationPortFormat: "Destino %d",
                aliasFormat: "Alias %@",
                dnsFormat: "DNS %@",
                tcpPortsFormat: "TCP %@",
                udpPortsFormat: "UDP %@",
                authDaemonPortFormat: "Authd %d",
                olmVersionFormat: "OLM %@",
                resolverFormat: "Resolver %@",
                rewriteFormat: "Reescritura %@",
                healthPathFormat: "HC %@",
                priorityFormat: "Prioridad %d",
                trafficAmountFormat: "Tráfico %@"
            )
        case .de:
            return PangolinStrings(
                serviceDescription: "Reverse Proxy, Tunneling und Zero-Trust-Networking",
                loginHint: "Verwende einen Pangolin-Integration-API-Schlüssel. Bei einem auf eine Organisation begrenzten Schlüssel (Org-Scoped Key) auch die Organisations-ID eintragen.",
                orgIdPlaceholder: "Organisations-ID (optional)",
                sitesClientsLabel: "Sites / Clients",
                overviewSubtitle: "Übersicht über Reverse Proxy, Tunneling und Zero-Trust-Netzwerk",
                organizations: "Organisationen",
                sites: "Sites",
                privateResources: "Private Ressourcen",
                createPrivateResource: "Private Ressource erstellen",
                publicResources: "Öffentliche Ressourcen",
                createPublicResource: "Öffentliche Ressource erstellen",
                clients: "Clients",
                domains: "Domains",
                traffic: "Traffic",
                ingressEgress: "Ingress + Egress",
                org: "Org",
                utility: "Utility",
                billing: "Abrechnung",
                enabled: "Aktiviert",
                disabled: "Deaktiviert",
                enableAction: "Ressource aktivieren",
                disableAction: "Ressource deaktivieren",
                online: "Online",
                offline: "Offline",
                blocked: "Blockiert",
                archived: "Archiviert",
                pending: "Ausstehend",
                verified: "Verifiziert",
                failed: "Fehler",
                managed: "Verwaltet",
                manual: "Manuell",
                wildcard: "Wildcard",
                whitelist: "Whitelist",
                healthCheck: "Health Check",
                agentUpdate: "Agent-Update",
                newtUpdate: "Newt-Update",
                icmpOff: "ICMP aus",
                noOrganizations: "Keine Pangolin-Organisationen für diesen API-Schlüssel verfügbar",
                noSites: "Keine Pangolin-Sites gefunden",
                noPrivateResources: "Keine privaten Ressourcen konfiguriert",
                noPublicResources: "Keine öffentlichen Ressourcen konfiguriert",
                noClients: "Keine Clients registriert",
                noDomains: "Keine verwalteten Domains",
                site: "Site",
                protocolLabel: "Protokoll",
                domainLabel: "Domain",
                subdomainLabel: "Subdomain",
                backendMethodLabel: "Backend-Methode",
                proxyPortLabel: "Proxy-Port",
                httpResource: "HTTP",
                tcpResource: "TCP",
                udpResource: "UDP",
                httpMethod: "HTTP",
                httpsMethod: "HTTPS",
                h2cMethod: "H2C",
                healthy: "Gesund",
                unhealthy: "Ungesund",
                allSitesOnline: "Alle Sites online",
                onlineSitesFormat: "%d Sites online",
                onlineCountFormat: "%d online",
                enabledCountFormat: "%d aktiviert",
                verifiedCountFormat: "%d verifiziert",
                targetsCountFormat: "%d Ziele",
                linkedSitesFormat: "%d verknüpfte Sites",
                triesFormat: "%d Versuche",
                healthyCountFormat: "%d gesund",
                unhealthyCountFormat: "%d ungesund",
                newtVersionFormat: "Newt %@",
                exitNodeFormat: "Exit %@",
                endpointFormat: "Endpoint %@",
                proxyPortFormat: "Proxy %d",
                destinationPortFormat: "Ziel %d",
                aliasFormat: "Alias %@",
                dnsFormat: "DNS %@",
                tcpPortsFormat: "TCP %@",
                udpPortsFormat: "UDP %@",
                authDaemonPortFormat: "Authd %d",
                olmVersionFormat: "OLM %@",
                resolverFormat: "Resolver %@",
                rewriteFormat: "Rewrite %@",
                healthPathFormat: "HC %@",
                priorityFormat: "Priorität %d",
                trafficAmountFormat: "Traffic %@"
            )
        case .en:
            return PangolinStrings(
                serviceDescription: "Reverse proxy, tunneling and zero-trust networking",
                loginHint: "Use a Pangolin integration API key. If using an org-scoped key (no root access), also enter your Organization ID.",
                orgIdPlaceholder: "Organization ID (optional)",
                sitesClientsLabel: "Sites / clients",
                overviewSubtitle: "Reverse proxy, tunneling and zero-trust network overview",
                organizations: "Organizations",
                sites: "Sites",
                privateResources: "Private Resources",
                createPrivateResource: "Create private resource",
                publicResources: "Public Resources",
                createPublicResource: "Create public resource",
                clients: "Clients",
                domains: "Domains",
                traffic: "Traffic",
                ingressEgress: "Ingress + egress",
                org: "Org",
                utility: "Utility",
                billing: "Billing",
                enabled: "Enabled",
                disabled: "Disabled",
                enableAction: "Enable resource",
                disableAction: "Disable resource",
                online: "Online",
                offline: "Offline",
                blocked: "Blocked",
                archived: "Archived",
                pending: "Pending",
                verified: "Verified",
                failed: "Failed",
                managed: "Managed",
                manual: "Manual",
                wildcard: "Wildcard",
                whitelist: "Whitelist",
                healthCheck: "Health check",
                agentUpdate: "Agent update",
                newtUpdate: "Newt update",
                icmpOff: "ICMP off",
                noOrganizations: "No Pangolin organizations available for this API key",
                noSites: "No Pangolin sites found",
                noPrivateResources: "No private resources configured",
                noPublicResources: "No public resources configured",
                noClients: "No clients enrolled",
                noDomains: "No managed domains",
                site: "Site",
                protocolLabel: "Protocol",
                domainLabel: "Domain",
                subdomainLabel: "Subdomain",
                backendMethodLabel: "Backend method",
                proxyPortLabel: "Proxy port",
                httpResource: "HTTP",
                tcpResource: "TCP",
                udpResource: "UDP",
                httpMethod: "HTTP",
                httpsMethod: "HTTPS",
                h2cMethod: "H2C",
                healthy: "Healthy",
                unhealthy: "Unhealthy",
                allSitesOnline: "All sites online",
                onlineSitesFormat: "%d sites online",
                onlineCountFormat: "%d online",
                enabledCountFormat: "%d enabled",
                verifiedCountFormat: "%d verified",
                targetsCountFormat: "%d targets",
                linkedSitesFormat: "%d linked sites",
                triesFormat: "%d tries",
                healthyCountFormat: "%d healthy",
                unhealthyCountFormat: "%d unhealthy",
                newtVersionFormat: "Newt %@",
                exitNodeFormat: "Exit %@",
                endpointFormat: "Endpoint %@",
                proxyPortFormat: "Proxy %d",
                destinationPortFormat: "Dest %d",
                aliasFormat: "Alias %@",
                dnsFormat: "DNS %@",
                tcpPortsFormat: "TCP %@",
                udpPortsFormat: "UDP %@",
                authDaemonPortFormat: "Authd %d",
                olmVersionFormat: "OLM %@",
                resolverFormat: "Resolver %@",
                rewriteFormat: "Rewrite %@",
                healthPathFormat: "HC %@",
                priorityFormat: "Priority %d",
                trafficAmountFormat: "Traffic %@"
            )
        }
    }
}
