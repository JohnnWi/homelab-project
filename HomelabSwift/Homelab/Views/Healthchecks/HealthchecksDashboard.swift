import SwiftUI

struct HealthchecksDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var checks: [HealthchecksCheck] = []
    @State private var channels: [HealthchecksChannel] = []
    @State private var showBadges = false
    @State private var showChannels = false
    @State private var showEditor = false
    @State private var editingCheck: HealthchecksCheck?

    private let healthchecksColor = Color(hex: "#16A34A")
    private let smoothAnimation = Animation.spring(response: 0.45, dampingFraction: 0.86)

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .healthchecks,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(force: true) }
        ) {
            overviewCard
            if isReadOnlyKey {
                readOnlyBanner
            }
            instancePicker
        }
        .animation(smoothAnimation, value: isReadOnlyKey)
        .animation(smoothAnimation, value: checks.count)
        .navigationTitle(localizer.t.serviceHealthchecks)
        .navigationDestination(for: HealthchecksListRoute.self) { route in
            HealthchecksChecksList(instanceId: route.instanceId, filter: route.filter)
        }
        .navigationDestination(for: HealthchecksCheckRoute.self) { route in
            HealthchecksCheckDetail(instanceId: route.instanceId, check: route.check, channels: channels)
        }
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !isReadOnlyKey {
                    Button {
                        editingCheck = nil
                        showEditor = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }

                Menu {
                    Button(localizer.t.healthchecksIntegrations) {
                        showChannels = true
                    }
                    Button(localizer.t.healthchecksBadges) {
                        showBadges = true
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .sheet(isPresented: $showBadges) {
            HealthchecksBadgesView(instanceId: selectedInstanceId)
        }
        .sheet(isPresented: $showChannels) {
            HealthchecksChannelsView(instanceId: selectedInstanceId)
        }
        .sheet(isPresented: $showEditor) {
            HealthchecksCheckEditor(instanceId: selectedInstanceId, existing: editingCheck) {
                await fetchDashboard(force: true)
            }
        }
        .task(id: selectedInstanceId) {
            state = .idle
            await fetchDashboard(force: true)
        }
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .healthchecks)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            withAnimation(smoothAnimation) {
                                selectedInstanceId = instance.id
                                servicesStore.setPreferredInstance(id: instance.id, for: .healthchecks)
                                checks = []
                                channels = []
                                state = .idle
                            }
                        } label: {
                            HStack(spacing: 10) {
                                ServiceIconView(type: .healthchecks, size: 22)
                                    .frame(width: 36, height: 36)
                                    .background(healthchecksColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(instance.displayLabel)
                                        .font(.subheadline.weight(.semibold))
                                    Text(instance.url)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                            }
                            .padding(14)
                            .glassCard(tint: instance.id == selectedInstanceId ? healthchecksColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(PressableCardButtonStyle())
                    }
                }
            }
        }
    }

    private var overviewCard: some View {
        let total = checks.count
        let up = checks.filter { $0.status == "up" }.count
        let grace = checks.filter { $0.status == "grace" }.count
        let down = checks.filter { $0.status == "down" }.count
        let paused = checks.filter { $0.status == "paused" }.count
        let newCount = checks.filter { $0.status == "new" }.count
        return GlassCard(tint: AppTheme.surface.opacity(0.45)) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 14) {
                    ServiceIconView(type: .healthchecks, size: 36)
                        .frame(width: 56, height: 56)
                        .background(healthchecksColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    VStack(alignment: .leading, spacing: 4) {
                        Text(localizer.t.serviceHealthchecks)
                            .font(.headline.bold())
                        Text(localizer.t.serviceHealthchecksDesc)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    Spacer()
                }

                Divider()
                    .overlay(AppTheme.textMuted.opacity(0.2))

                LazyVGrid(columns: twoColumnGrid, spacing: 10) {
                    summaryTile(title: localizer.t.healthchecksChecks, value: "\(total)", icon: "checkmark.seal.fill", filter: .all)
                    summaryTile(title: localizer.t.healthchecksUp, value: "\(up)", icon: "checkmark.circle.fill", filter: .up)
                    summaryTile(title: localizer.t.healthchecksGrace, value: "\(grace)", icon: "clock.fill", filter: .grace)
                    summaryTile(title: localizer.t.healthchecksDown, value: "\(down)", icon: "xmark.circle.fill", filter: .down)
                    summaryTile(title: localizer.t.healthchecksPaused, value: "\(paused)", icon: "pause.circle.fill", filter: .paused)
                    summaryTile(title: localizer.t.healthchecksNew, value: "\(newCount)", icon: "sparkles", filter: .new)
                }
            }
            .padding(14)
        }
    }

    private var readOnlyBanner: some View {
        HealthchecksReadOnlyBanner()
            .transition(.opacity.combined(with: .move(edge: .top)))
    }

    /// A key is considered read-only when NO check has a writable identifier.
    /// This covers both `uuid` (standard key) and `unique_key` (read-only API key response).
    private var isReadOnlyKey: Bool {
        !checks.isEmpty && checks.allSatisfy { $0.uuid == nil && $0.uniqueKey == nil }
    }

    private func fetchDashboard(force: Bool) async {
        if state.isLoading { return }
        if case .loaded = state, !force { return }
        state = .loading
        do {
            guard let client = await servicesStore.healthchecksClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }
            async let checksTask = client.listChecks()
            async let channelsTask = client.listChannels()

            let loadedChecks = try await checksTask
            let loadedChannels: [HealthchecksChannel]
            do {
                loadedChannels = try await channelsTask
            } catch {
                loadedChannels = []
            }

            checks = loadedChecks
            channels = loadedChannels
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}

private struct HealthchecksChecksList: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var state: LoadableState<Void> = .idle
    @State private var checks: [HealthchecksCheck] = []
    @State private var channels: [HealthchecksChannel] = []
    @State private var searchText: String = ""
    @State private var filter: HealthchecksStatusFilter
    @Namespace private var filterNamespace

    @State private var showEditor = false
    @State private var editingCheck: HealthchecksCheck?
    @State private var showBadges = false
    @State private var showChannels = false

    private let healthchecksColor = Color(hex: "#16A34A")
    private let smoothAnimation = Animation.spring(response: 0.45, dampingFraction: 0.86)

    init(instanceId: UUID, filter: HealthchecksStatusFilter) {
        self.instanceId = instanceId
        _filter = State(initialValue: filter)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .healthchecks,
            instanceId: instanceId,
            state: state,
            onRefresh: { await fetchChecks(force: true) }
        ) {
            if isReadOnlyKey {
                HealthchecksReadOnlyBanner()
            }
            searchBar
            filterChips
            checksList
        }
        .animation(smoothAnimation, value: filter)
        .animation(smoothAnimation, value: searchText)
        .animation(smoothAnimation, value: checks.count)
        .navigationTitle(localizer.t.healthchecksChecks)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !isReadOnlyKey {
                    Button {
                        editingCheck = nil
                        showEditor = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }

                Menu {
                    Button(localizer.t.healthchecksIntegrations) {
                        showChannels = true
                    }
                    Button(localizer.t.healthchecksBadges) {
                        showBadges = true
                    }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .sheet(isPresented: $showBadges) {
            HealthchecksBadgesView(instanceId: instanceId)
        }
        .sheet(isPresented: $showChannels) {
            HealthchecksChannelsView(instanceId: instanceId)
        }
        .sheet(isPresented: $showEditor) {
            HealthchecksCheckEditor(instanceId: instanceId, existing: editingCheck) {
                await fetchChecks(force: true)
            }
        }
        .task { await fetchChecks(force: false) }
    }

    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
                .accessibilityHidden(true)
            TextField(localizer.t.healthchecksSearch, text: $searchText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !searchText.isEmpty {
                Button { searchText = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textMuted)
                }
                .accessibilityLabel(localizer.t.actionClear)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .glassCard(cornerRadius: 12)
    }

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(HealthchecksStatusFilter.allCases, id: \.self) { item in
                    Button {
                        HapticManager.light()
                        withAnimation(smoothAnimation) {
                            filter = item
                        }
                    } label: {
                        ZStack {
                            if filter == item {
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .fill(healthchecksColor)
                                    .matchedGeometryEffect(id: "filter-pill", in: filterNamespace)
                            }
                            Text(item.title(localizer))
                                .font(.caption.bold())
                                .foregroundStyle(filter == item ? .white : AppTheme.textSecondary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(6)
            .glassCard(cornerRadius: 16, tint: AppTheme.surface.opacity(0.35))
        }
    }

    private var checksList: some View {
        let filtered = filteredChecks
        return Group {
            if filtered.isEmpty && !state.isLoading {
                emptyState
            } else {
                ForEach(filtered) { check in
                    if let apiId = check.apiIdentifier {
                        NavigationLink(value: HealthchecksCheckRoute(instanceId: instanceId, checkId: apiId, check: check)) {
                            HealthchecksCheckCard(check: check)
                        }
                        .buttonStyle(PressableCardButtonStyle())
                        .hoverEffect(.highlight)
                        .contextMenu {
                            if let pingUrl = check.pingUrl {
                                Button(localizer.t.healthchecksCopyPingUrl) {
                                    UIPasteboard.general.string = pingUrl
                                }
                            }
                        }
                    } else {
                        // Read-only check: show card with a subtle visual hint.
                        HealthchecksCheckCard(check: check)
                            .overlay(alignment: .bottomTrailing) {
                                Image(systemName: "lock.fill")
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                                    .padding(8)
                                    .background(.ultraThinMaterial, in: Capsule())
                            }
                    }
                }
            }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "checkmark.seal")
                .font(.system(size: 48))
                .foregroundStyle(AppTheme.textMuted)
                .accessibilityHidden(true)
            Text(localizer.t.healthchecksNoChecks)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 40)
    }

    private var filteredChecks: [HealthchecksCheck] {
        var items = checks
        if filter != .all {
            items = items.filter { $0.status == filter.rawValue }
        }
        if !searchText.isEmpty {
            let needle = searchText.lowercased()
            items = items.filter { check in
                check.name.lowercased().contains(needle)
                || (check.desc?.lowercased().contains(needle) ?? false)
                || check.tagsList.contains(where: { $0.lowercased().contains(needle) })
            }
        }
        return items.sorted { lhs, rhs in
            let order = HealthchecksStatusFilter.sortOrder
            let lIndex = order.firstIndex(of: lhs.status) ?? order.count
            let rIndex = order.firstIndex(of: rhs.status) ?? order.count
            if lIndex != rIndex { return lIndex < rIndex }
            return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
        }
    }

    private var isReadOnlyKey: Bool {
        !checks.isEmpty && checks.allSatisfy { $0.uuid == nil }
    }

    private func fetchChecks(force: Bool) async {
        if state.isLoading { return }
        if case .loaded = state, !force { return }
        state = .loading
        do {
            guard let client = await servicesStore.healthchecksClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            async let checksTask = client.listChecks()
            async let channelsTask = client.listChannels()

            let loadedChecks = try await checksTask
            let loadedChannels: [HealthchecksChannel]
            do {
                loadedChannels = try await channelsTask
            } catch {
                loadedChannels = []
            }

            checks = loadedChecks
            channels = loadedChannels
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}

private struct HealthchecksListRoute: Hashable {
    let instanceId: UUID
    let filter: HealthchecksStatusFilter
}

private enum HealthchecksStatusFilter: String, CaseIterable {
    case all
    case up
    case grace
    case down
    case paused
    case new

    static let sortOrder: [String] = [
        "down",
        "grace",
        "new",
        "paused",
        "up"
    ]

    @MainActor func title(_ localizer: Localizer) -> String {
        switch self {
        case .all: return localizer.t.healthchecksAll
        case .up: return localizer.t.healthchecksUp
        case .grace: return localizer.t.healthchecksGrace
        case .down: return localizer.t.healthchecksDown
        case .paused: return localizer.t.healthchecksPaused
        case .new: return localizer.t.healthchecksNew
        }
    }
}

private struct HealthchecksCheckRoute: Hashable {
    let instanceId: UUID
    let checkId: String
    let check: HealthchecksCheck
}

private extension HealthchecksDashboard {
    func summaryTile(title: String, value: String, icon: String, filter: HealthchecksStatusFilter) -> some View {
        NavigationLink(value: HealthchecksListRoute(instanceId: selectedInstanceId, filter: filter)) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.statusColor(for: filter.rawValue))
                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                        .lineLimit(1)
                    Text(value)
                        .font(.headline.bold())
                        .foregroundStyle(.primary)
                        .contentTransition(.numericText())
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
            .padding(10)
            .glassCard(
                cornerRadius: 12,
                tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)
            )
        }
        .buttonStyle(PressableCardButtonStyle())
    }
}

private struct HealthchecksCheckCard: View {
    let check: HealthchecksCheck

    @Environment(Localizer.self) private var localizer

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                StatusBadge(status: check.status, compact: true)
                Text(check.name)
                    .font(.headline)
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }

            if let desc = check.desc, !desc.isEmpty {
                Text(desc)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
            }

            if !check.tagsList.isEmpty {
                FlexibleTagRow(tags: check.tagsList)
            }

            HStack(spacing: 12) {
                HealthchecksMetricPill(
                    icon: "clock.fill",
                    title: localizer.t.healthchecksLastPing,
                    value: formattedDate(check.lastPing),
                    tint: AppTheme.info
                )
                HealthchecksMetricPill(
                    icon: "calendar.badge.clock",
                    title: localizer.t.healthchecksNextPing,
                    value: formattedDate(check.nextPing),
                    tint: AppTheme.created
                )
            }

            HStack(spacing: 12) {
                HealthchecksMetricPill(
                    icon: check.schedule != nil ? "calendar" : "timer",
                    title: scheduleLabel,
                    value: scheduleValue,
                    tint: check.schedule != nil ? AppTheme.created : AppTheme.warning
                )
                HealthchecksMetricPill(
                    icon: "hourglass",
                    title: localizer.t.healthchecksGracePeriod,
                    value: graceValue,
                    tint: AppTheme.warning
                )
            }
        }
        .padding(16)
        .glassCard(tint: AppTheme.surface.opacity(0.45))
    }

    private var scheduleLabel: String {
        if check.schedule != nil { return localizer.t.healthchecksSchedule }
        return localizer.t.healthchecksTimeout
    }

    private var scheduleValue: String {
        if let schedule = check.schedule, !schedule.isEmpty {
            return schedule
        }
        if let timeout = check.timeout { return "\(timeout)s" }
        return localizer.t.notAvailable
    }

    private var graceValue: String {
        guard let grace = check.grace else { return localizer.t.notAvailable }
        return "\(grace)s"
    }

    private func formattedDate(_ value: String?) -> String {
        guard let value, !value.isEmpty else { return localizer.t.notAvailable }
        return Formatters.formatDate(value)
    }
}

private struct HealthchecksMetricPill: View {
    let icon: String
    let title: String
    let value: String
    let tint: Color

    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(tint)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                Text(value)
                    .font(.caption.bold())
                    .foregroundStyle(.primary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .glassCard(cornerRadius: 12, tint: AppTheme.surface.opacity(0.45))
    }
}

private struct HealthchecksReadOnlyBanner: View {
    @Environment(Localizer.self) private var localizer

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "lock.fill")
                .font(.title3)
                .foregroundStyle(AppTheme.warning)
                .frame(width: 40, height: 40)
                .background(AppTheme.warning.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(localizer.t.healthchecksReadOnlyTitle)
                    .font(.subheadline.bold())
                Text(localizer.t.healthchecksReadOnlyMessage)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }
            Spacer()
        }
        .padding(14)
        .glassCard()
    }
}
