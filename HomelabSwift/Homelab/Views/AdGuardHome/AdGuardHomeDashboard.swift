import SwiftUI
import Charts

struct AdGuardHomeDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedInstanceId: UUID
    @State private var status: AdGuardStatus?
    @State private var stats: AdGuardStats?
    @State private var state: LoadableState<Void> = .idle

    @State private var isToggling = false
    @State private var toggleError: String?
    @State private var showToggleError = false
    @State private var showDisableOptions = false
    @State private var showCustomDisablePrompt = false
    @State private var customDisableMinutes = ""
    @State private var lastLoadedInstanceId: UUID?
    @State private var lastLoadedAt: Date?

    private let adguardColor = ServiceType.adguardHome.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    private var isProtectionEnabled: Bool { status?.protection_enabled ?? false }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .adguardHome,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchAll(force: true) }
        ) {
            instancePicker
            protectionCard

            if let stats {
                statsOverview(stats)
                queryActivitySection(stats)
                toolsSection
                safetyBreakdownSection(stats)

                if let status {
                    serverInfoSection(status)
                }

                if !stats.topQueried.isEmpty {
                    topListSection(title: localizer.t.adguardTopQueried, items: stats.topQueried, accent: AppTheme.info)
                }
                if !stats.topBlocked.isEmpty {
                    topListSection(title: localizer.t.adguardTopBlocked, items: stats.topBlocked, accent: AppTheme.stopped)
                }
                if !stats.topClients.isEmpty {
                    topListSection(title: localizer.t.adguardTopClients, items: stats.topClients, accent: adguardColor)
                }
            }
        }
        .navigationTitle(localizer.t.serviceAdguard)
        .task(id: selectedInstanceId) { await fetchAll() }
        .alert(localizer.t.error, isPresented: $showToggleError) {
            Button(localizer.t.confirm, role: .cancel) { }
        } message: {
            Text(toggleError ?? localizer.t.errorUnknown)
        }
        .confirmationDialog(localizer.t.adguardDisableDesc, isPresented: $showDisableOptions, titleVisibility: .visible) {
            Button(localizer.t.adguardDisablePermanently, role: .destructive) { handleToggle(timer: nil) }
            Button(localizer.t.adguardDisable1h) { handleToggle(timer: 3600) }
            Button(localizer.t.adguardDisable5m) { handleToggle(timer: 300) }
            Button(localizer.t.adguardDisable1m) { handleToggle(timer: 60) }
            Button(localizer.t.adguardDisableCustom) {
                customDisableMinutes = ""
                showCustomDisablePrompt = true
            }
            Button(localizer.t.cancel, role: .cancel) { }
        }
        .alert(localizer.t.adguardCustomDisableTitle, isPresented: $showCustomDisablePrompt) {
            TextField(localizer.t.adguardCustomDisableMinutes, text: $customDisableMinutes)
                .keyboardType(.numberPad)
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.confirm) {
                if let minutes = Int(customDisableMinutes.trimmingCharacters(in: .whitespaces)), minutes > 0 {
                    handleToggle(timer: minutes * 60)
                }
            }
        } message: {
            Text(localizer.t.adguardCustomDisableDesc)
        }
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .adguardHome)
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
                            servicesStore.setPreferredInstance(id: instance.id, for: .adguardHome)
                            status = nil
                            stats = nil
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? adguardColor : AppTheme.textMuted.opacity(0.4))
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
                            .glassCard(tint: instance.id == selectedInstanceId ? adguardColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var protectionCard: some View {
        Button {
            if isProtectionEnabled {
                showDisableOptions = true
            } else {
                handleToggle(timer: nil)
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: isProtectionEnabled ? "shield.fill" : "shield.slash.fill")
                    .font(.title2)
                    .foregroundStyle(isProtectionEnabled ? AppTheme.running : AppTheme.stopped)
                    .frame(width: 56, height: 56)
                    .background(
                        (isProtectionEnabled ? AppTheme.running : AppTheme.stopped).opacity(0.1),
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(localizer.t.adguardProtection)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(isProtectionEnabled ? localizer.t.adguardEnabled : localizer.t.adguardDisabled)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(isProtectionEnabled ? AppTheme.running : AppTheme.stopped)
                    Text(isProtectionEnabled ? localizer.t.adguardProtectionDesc : localizer.t.adguardDisableDesc)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(2)
                }

                Spacer()

                Text(isProtectionEnabled ? localizer.t.statusOn : localizer.t.statusOff)
                    .font(.caption.bold())
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        isProtectionEnabled ? AppTheme.running : AppTheme.stopped,
                        in: Capsule()
                    )
            }
            .padding(18)
            .glassCard(tint: (isProtectionEnabled ? AppTheme.running : AppTheme.stopped).opacity(0.05))
        }
        .buttonStyle(.plain)
        .disabled(isToggling)
    }

    private func statsOverview(_ stats: AdGuardStats) -> some View {
        let total = max(1, stats.totalQueries)
        let percentBlocked = Double(stats.blockedFiltering) / Double(total) * 100
        let avgText = String(format: "%.2f ms", stats.avgProcessingTime * 1000)
        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.adguardOverview.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                NavigationLink(destination: AdGuardHomeQueryLogView(instanceId: selectedInstanceId, initialStatusFilter: .all)) {
                    statCard(icon: "magnifyingglass", iconBg: adguardColor, value: Formatters.formatNumber(stats.totalQueries), label: localizer.t.adguardTotalQueries)
                }
                .buttonStyle(.plain)

                NavigationLink(destination: AdGuardHomeQueryLogView(instanceId: selectedInstanceId, initialStatusFilter: .blocked)) {
                    statCard(icon: "hand.raised.fill", iconBg: AppTheme.stopped, value: Formatters.formatNumber(stats.blockedFiltering), label: localizer.t.adguardBlockedQueries)
                }
                .buttonStyle(.plain)

                statCard(icon: "chart.bar.fill", iconBg: AppTheme.warning, value: String(format: "%.1f%%", percentBlocked), label: localizer.t.adguardPercentBlocked)
                statCard(icon: "speedometer", iconBg: AppTheme.info, value: avgText, label: localizer.t.adguardAvgProcessing)
            }
        }
    }

    private func statCard(icon: String, iconBg: Color, value: String, label: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(iconBg)
                .frame(width: 36, height: 36)
                .background(iconBg.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            Text(value)
                .font(.title3.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(label)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .glassCard()
    }

    @ViewBuilder
    private func queryActivitySection(_ stats: AdGuardStats) -> some View {
        let count = min(stats.dnsQueries.count, stats.blockedSeries.count)
        if count > 0 {
            let maxValue = max(stats.dnsQueries.prefix(count).max() ?? 0, stats.blockedSeries.prefix(count).max() ?? 0)
            let midValue = maxValue / 2
            let yValues = Array(Set([0, midValue, maxValue])).sorted()
            let xValues = Array(Set([0, count / 2, max(0, count - 1)])).sorted()
            let unit = count >= 60 ? "m" : "h"
            VStack(alignment: .leading, spacing: 12) {
                Text(localizer.t.adguardQueryActivity.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)

                VStack(spacing: 12) {
                    Chart {
                        ForEach(0..<count, id: \.self) { idx in
                            let total = stats.dnsQueries[idx]
                            let blocked = stats.blockedSeries[idx]
                            let allowed = max(0, total - blocked)
                            BarMark(
                                x: .value("Time", idx),
                                y: .value("Allowed", allowed),
                                width: .fixed(6)
                            )
                            .foregroundStyle(AppTheme.running.opacity(0.65))

                            BarMark(
                                x: .value("Time", idx),
                                y: .value("Blocked", blocked),
                                width: .fixed(6)
                            )
                            .foregroundStyle(AppTheme.stopped.opacity(0.8))
                        }
                    }
                    .chartXAxis {
                        AxisMarks(values: xValues) { value in
                            AxisValueLabel {
                                if let idx = value.as(Int.self) {
                                    let offset = max(0, count - 1 - idx)
                                    let label = "\(offset)\(unit)"
                                    Text(label)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                }
                            }
                            AxisGridLine().foregroundStyle(AppTheme.textMuted.opacity(0.15))
                        }
                    }
                    .chartYAxis {
                        AxisMarks(values: yValues) { value in
                            AxisGridLine().foregroundStyle(AppTheme.textMuted.opacity(0.15))
                            AxisValueLabel {
                                if let y = value.as(Int.self) {
                                    Text(Formatters.formatNumber(y))
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                }
                            }
                        }
                    }
                    .frame(height: 140)

                    HStack(spacing: 16) {
                        Spacer()
                        HStack(spacing: 6) {
                            RoundedRectangle(cornerRadius: 2).fill(AppTheme.running.opacity(0.7)).frame(width: 8, height: 8)
                            Text(localizer.t.adguardFilterAllowed)
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        HStack(spacing: 6) {
                            RoundedRectangle(cornerRadius: 2).fill(AppTheme.stopped.opacity(0.8)).frame(width: 8, height: 8)
                            Text(localizer.t.adguardFilterBlocked)
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        Spacer()
                    }
                }
                .padding(16)
                .glassCard()
            }
        }
    }

    @ViewBuilder
    private func safetyBreakdownSection(_ stats: AdGuardStats) -> some View {
        let total = stats.replacedSafebrowsing + stats.replacedSafesearch + stats.replacedParental
        if total > 0 {
            VStack(alignment: .leading, spacing: 10) {
                Text(localizer.t.adguardSafety.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)

                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                    statCard(icon: "shield.checkerboard", iconBg: AppTheme.info, value: Formatters.formatNumber(stats.replacedSafebrowsing), label: localizer.t.adguardSafeBrowsing)
                    statCard(icon: "magnifyingglass.circle.fill", iconBg: AppTheme.warning, value: Formatters.formatNumber(stats.replacedSafesearch), label: localizer.t.adguardSafeSearch)
                    statCard(icon: "figure.and.child.holdinghands", iconBg: AppTheme.accent, value: Formatters.formatNumber(stats.replacedParental), label: localizer.t.adguardParental)
                }
            }
        }
    }

    private func serverInfoSection(_ status: AdGuardStatus) -> some View {
        let addresses = status.dns_addresses?.joined(separator: ", ") ?? "—"
        let dnsPort = status.dns_port.map(String.init) ?? "—"
        let httpPort = status.http_port.map(String.init) ?? "—"
        let version = status.version ?? "—"
        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.adguardServerInfo.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: twoColumnGrid, spacing: 10) {
                infoCard(title: localizer.t.adguardVersion, value: version, icon: "tag.fill", tint: AppTheme.accent)
                infoCard(title: localizer.t.adguardDnsAddress, value: addresses, icon: "server.rack", tint: adguardColor)
                infoCard(title: localizer.t.adguardDnsPort, value: dnsPort, icon: "antenna.radiowaves.left.and.right", tint: AppTheme.info)
                infoCard(title: localizer.t.adguardHttpPort, value: httpPort, icon: "globe", tint: AppTheme.warning)
            }
        }
    }

    private func infoCard(title: String, value: String, icon: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.caption.weight(.semibold))
                .foregroundStyle(tint)
                .frame(width: 28, height: 28)
                .background(tint.opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
                .truncationMode(.middle)
                .minimumScaleFactor(0.8)
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: 96, alignment: .topLeading)
        .padding(12)
        .glassCard()
    }

    private func topListSection(title: String, items: [AdGuardTopItem], accent: Color) -> some View {
        let maxCount = items.map(\.count).max() ?? 1
        return VStack(alignment: .leading, spacing: 10) {
            Text(title.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 12) {
                ForEach(Array(items.prefix(8).enumerated()), id: \.element.id) { index, item in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 10) {
                            Text("#\(index + 1)")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(accent)
                                .frame(width: 24, alignment: .leading)

                            Text(item.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.primary)
                                .lineLimit(1)

                            Spacer()

                            Text(Formatters.formatNumber(item.count))
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        GeometryReader { geo in
                            let fraction = max(0.05, CGFloat(item.count) / CGFloat(maxCount))
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(accent.opacity(0.18))
                                Capsule()
                                    .fill(
                                        LinearGradient(
                                            colors: [accent.opacity(0.95), accent.opacity(0.55)],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .frame(width: geo.size.width * fraction)
                            }
                        }
                        .frame(height: 6)
                    }

                    if index < min(7, items.count - 1) {
                        Divider().opacity(0.4)
                    }
                }
            }
            .padding(14)
            .glassCard()
        }
    }

    private var toolsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.adguardQuickActions.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            NavigationLink(destination: AdGuardHomeRewritesView(instanceId: selectedInstanceId)) {
                toolWideCard(icon: "arrow.right.circle.fill", color: AppTheme.accent, title: localizer.t.adguardRewrites)
            }
            .buttonStyle(.plain)

            LazyVGrid(columns: twoColumnGrid, spacing: 10) {
                NavigationLink(destination: AdGuardHomeQueryLogView(instanceId: selectedInstanceId)) {
                    toolCard(icon: "text.append", color: AppTheme.info, title: localizer.t.adguardQueryLog)
                }
                .buttonStyle(.plain)

                NavigationLink(destination: AdGuardHomeUserRulesView(instanceId: selectedInstanceId)) {
                    toolCard(icon: "list.bullet.rectangle.portrait.fill", color: adguardColor, title: localizer.t.adguardUserRules)
                }
                .buttonStyle(.plain)

                NavigationLink(destination: AdGuardHomeFiltersView(instanceId: selectedInstanceId)) {
                    toolCard(icon: "line.3.horizontal.decrease.circle.fill", color: AppTheme.info, title: localizer.t.adguardFilters)
                }
                .buttonStyle(.plain)

                NavigationLink(destination: AdGuardHomeBlockedServicesView(instanceId: selectedInstanceId)) {
                    toolCard(icon: "nosign", color: AppTheme.warning, title: localizer.t.adguardBlockedServices)
                }
                .buttonStyle(.plain)
            }
        }
    }

    private func toolWideCard(icon: String, color: Color, title: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(color)
                .frame(width: 44, height: 44)
                .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.headline)
                    .foregroundStyle(.primary)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .accessibilityHidden(true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .glassCard()
        .contentShape(Rectangle())
    }

    private func toolCard(icon: String, color: Color, title: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: icon)
                    .font(.body)
                    .foregroundStyle(color)
                    .frame(width: 36, height: 36)
                    .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
            }

            Text(title)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .glassCard()
        .contentShape(Rectangle())
    }

    private func fetchAll(force: Bool = false) async {
        if !force,
           lastLoadedInstanceId == selectedInstanceId,
           let lastLoadedAt,
           Date().timeIntervalSince(lastLoadedAt) < 30,
           case .loaded = state {
            return
        }
        if force || state.isLoading {
            state = .loading
        }
        do {
            guard let client = await servicesStore.adguardClient(instanceId: selectedInstanceId) else {
                throw APIError.notConfigured
            }
            async let statusTask = client.getStatus()
            async let statsTask = client.getStats()
            let (fetchedStatus, fetchedStats) = try await (statusTask, statsTask)
            status = fetchedStatus
            stats = fetchedStats
            state = .loaded(())
            lastLoadedInstanceId = selectedInstanceId
            lastLoadedAt = Date()
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func handleToggle(timer: Int?) {
        guard !isToggling else { return }
        isToggling = true
        toggleError = nil
        Task {
            do {
                guard let client = await servicesStore.adguardClient(instanceId: selectedInstanceId) else {
                    throw APIError.notConfigured
                }
                if isProtectionEnabled {
                    try await client.setProtection(enabled: false, durationSeconds: timer)
                } else {
                    try await client.setProtection(enabled: true, durationSeconds: nil)
                }
                await fetchAll()
            } catch {
                toggleError = error.localizedDescription
                showToggleError = true
            }
            isToggling = false
        }
    }
}
