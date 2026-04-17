import SwiftUI
import UIKit

struct DockhandDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.scenePhase) private var scenePhase

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var dashboard: DockhandDashboardData?

    @State private var selectedEnvironmentId: String?
    @State private var selectedFilter: DockhandContainerFilter = .all
    @State private var selectedTab: DockhandDashboardTab = .overview

    @State private var selectedContainerId: String?
    @State private var containerDetailState: LoadableState<DockhandContainerDetailData> = .idle
    @State private var selectedStackName: String?
    @State private var selectedStackDetailState: LoadableState<DockhandStackDetailData> = .idle
    @State private var selectedScheduleId: String?
    @State private var selectedScheduleDetailState: LoadableState<DockhandScheduleDetailData> = .idle

    @State private var isRefreshing = false
    @State private var isRunningAction = false
    @State private var actionMessage: String?
    @State private var showSettingsSheet = false
    @State private var showFullContainerLogs = false
    @State private var isEditingCompose = false
    @State private var isSavingCompose = false
    @State private var composeDraft = ""

    @AppStorage("dockhand_auto_refresh_enabled") private var autoRefreshEnabled = true
    @AppStorage("dockhand_auto_refresh_seconds") private var autoRefreshSeconds = 45
    @AppStorage("dockhand_activity_limit") private var activityLimit = 25
    @AppStorage("dockhand_show_raw_activity") private var showAdvancedActivity = false

    private let dockhandColor = ServiceType.dockhand.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .dockhand,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: {
                await fetchDashboard(forceLoading: false)
            }
        ) {
            instancePicker
            environmentPicker
            overviewSection
            tabSelector
            tabContent
        }
        .navigationTitle(ServiceType.dockhand.displayName)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button {
                    Task { await fetchDashboard(forceLoading: false) }
                } label: {
                    if isRefreshing {
                        ProgressView()
                            .controlSize(.small)
                    } else {
                        Image(systemName: "arrow.clockwise")
                    }
                }
                .disabled(isRefreshing || isRunningAction)

                Button {
                    showSettingsSheet = true
                } label: {
                    Image(systemName: "slider.horizontal.3")
                }
            }
        }
        .task(id: selectedInstanceId) {
            await fetchDashboard(forceLoading: true)
        }
        .task(id: autoRefreshTaskKey) {
            await runAutoRefreshLoop()
        }
        .sheet(isPresented: containerSheetPresented) {
            containerDetailSheet
                .presentationDetents([.large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: stackSheetPresented) {
            stackDetailSheet
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: scheduleSheetPresented) {
            scheduleDetailSheet
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .sheet(isPresented: $showSettingsSheet) {
            dockhandSettingsSheet
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
        .alert(
            ServiceType.dockhand.displayName,
            isPresented: Binding(
                get: { actionMessage != nil },
                set: { isPresented in
                    if !isPresented { actionMessage = nil }
                }
            )
        ) {
            Button(localizer.t.done) { actionMessage = nil }
        } message: {
            Text(actionMessage ?? "")
        }
    }

    private var autoRefreshTaskKey: String {
        "\(selectedInstanceId.uuidString)|\(selectedEnvironmentId ?? "all")|\(autoRefreshEnabled)|\(autoRefreshSeconds)|\(scenePhaseToken)"
    }

    private var scenePhaseToken: String {
        switch scenePhase {
        case .active: return "active"
        case .inactive: return "inactive"
        case .background: return "background"
        @unknown default: return "unknown"
        }
    }

    private var filteredContainers: [DockhandContainerInfo] {
        let containers = dashboard?.containers ?? []
        switch selectedFilter {
        case .all:
            return containers
        case .running:
            return containers.filter(\.isRunning)
        case .stopped:
            return containers.filter { !$0.isRunning }
        case .issues:
            return containers.filter(\.isIssue)
        }
    }

    private var limitedActivity: [DockhandActivityInfo] {
        let limit = min(max(activityLimit, 5), 100)
        return Array((dashboard?.activity ?? []).prefix(limit))
    }

    private var limitedSchedules: [DockhandScheduleInfo] {
        Array((dashboard?.schedules ?? []).prefix(20))
    }

    private var containerSheetPresented: Binding<Bool> {
        Binding(
            get: { selectedContainerId != nil },
            set: { isPresented in
                if !isPresented {
                    selectedContainerId = nil
                    containerDetailState = .idle
                    showFullContainerLogs = false
                }
            }
        )
    }

    private var stackSheetPresented: Binding<Bool> {
        Binding(
            get: { selectedStackName != nil },
            set: { isPresented in
                if !isPresented {
                    selectedStackName = nil
                    selectedStackDetailState = .idle
                    isEditingCompose = false
                    isSavingCompose = false
                    composeDraft = ""
                }
            }
        )
    }

    private var scheduleSheetPresented: Binding<Bool> {
        Binding(
            get: { selectedScheduleId != nil },
            set: { isPresented in
                if !isPresented {
                    selectedScheduleId = nil
                    selectedScheduleDetailState = .idle
                }
            }
        )
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .dockhand)
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
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .dockhand)
                            selectedEnvironmentId = nil
                            selectedFilter = .all
                            selectedTab = .overview
                            dashboard = nil
                            selectedStackName = nil
                            selectedContainerId = nil
                            containerDetailState = .idle
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? dockhandColor : AppTheme.textMuted.opacity(0.4))
                                    .frame(width: 10, height: 10)

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
                            .glassCard(tint: instance.id == selectedInstanceId ? dockhandColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var environmentPicker: some View {
        let environments = dashboard?.environments ?? []
        return Group {
            if !environments.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text(localizer.t.dockhandEnvironments)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            environmentChip(title: localizer.t.containersAll, selected: selectedEnvironmentId == nil) {
                                selectedEnvironmentId = nil
                                Task { await fetchDashboard(forceLoading: false) }
                            }

                            ForEach(environments) { env in
                                environmentChip(
                                    title: env.name,
                                    selected: env.id == selectedEnvironmentId,
                                    isDefault: env.isDefault
                                ) {
                                    selectedEnvironmentId = env.id
                                    Task { await fetchDashboard(forceLoading: false) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private func environmentChip(
        title: String,
        selected: Bool,
        isDefault: Bool = false,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if isDefault {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.caption)
                }
                Text(title)
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }
            .foregroundStyle(selected ? dockhandColor : AppTheme.textSecondary)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .fill(selected ? dockhandColor.opacity(0.15) : AppTheme.surface.opacity(0.85))
            )
            .overlay {
                RoundedRectangle(cornerRadius: 999, style: .continuous)
                    .stroke(selected ? dockhandColor.opacity(0.45) : .white.opacity(0.04), lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }

    private var overviewSection: some View {
        let stats = dashboard?.stats ?? DockhandStatsInfo(
            totalContainers: 0,
            runningContainers: 0,
            stoppedContainers: 0,
            issueContainers: 0,
            stacks: 0,
            images: 0,
            volumes: 0,
            networks: 0
        )

        return VStack(alignment: .leading, spacing: 12) {
            sectionHeader(localizer.t.summaryTitle, trailing: nil)

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                statCard(title: localizer.t.containersAll, value: "\(stats.totalContainers)", icon: "shippingbox", tint: dockhandColor, filter: .all)
                statCard(title: localizer.t.containersRunning, value: "\(stats.runningContainers)", icon: "play.fill", tint: AppTheme.running, filter: .running)
                statCard(title: localizer.t.containersStopped, value: "\(stats.stoppedContainers)", icon: "stop.fill", tint: AppTheme.warning, filter: .stopped)
                statCard(title: localizer.t.dockhandIssues, value: "\(stats.issueContainers)", icon: "exclamationmark.triangle.fill", tint: AppTheme.danger, filter: .issues)
            }
        }
    }

    private func statCard(
        title: String,
        value: String,
        icon: String,
        tint: Color,
        filter: DockhandContainerFilter
    ) -> some View {
        let selected = selectedFilter == filter
        return Button {
            HapticManager.light()
            withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                selectedFilter = (selectedFilter == filter && filter != .all) ? .all : filter
                selectedTab = .containers
            }
        } label: {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    ZStack {
                        Circle()
                            .fill(tint.opacity(0.15))
                            .frame(width: 28, height: 28)
                        Image(systemName: icon)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(tint)
                    }
                    Text(title)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(AppTheme.textSecondary)
                        .lineLimit(2)
                        .minimumScaleFactor(0.72)
                    Spacer(minLength: 0)
                }

                Text(value)
                    .font(.title2.weight(.bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }
            .frame(maxWidth: .infinity, minHeight: 84, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .glassCard(cornerRadius: AppTheme.smallRadius, tint: selected ? tint.opacity(0.12) : nil)
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.smallRadius, style: .continuous)
                    .stroke(selected ? tint.opacity(0.5) : .white.opacity(0.04), lineWidth: selected ? 1.4 : 1)
            }
            .contentShape(RoundedRectangle(cornerRadius: AppTheme.smallRadius, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private var tabSelector: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                dockhandTabButton(for: .overview)
                dockhandTabButton(for: .containers)
                dockhandTabButton(for: .stacks)
            }

            HStack(spacing: 8) {
                dockhandTabButton(for: .activity)
                dockhandTabButton(for: .schedules)
            }
        }
    }

    private func dockhandTabButton(for tab: DockhandDashboardTab) -> some View {
        let selected = selectedTab == tab

        return Button {
            withAnimation(.easeInOut(duration: 0.18)) {
                selectedTab = tab
            }
        } label: {
            HStack(spacing: 8) {
                Image(systemName: icon(for: tab))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(selected ? dockhandColor : tint(for: tab))

                Text(title(for: tab))
                    .font(.caption.weight(.bold))
                    .foregroundStyle(selected ? dockhandColor : AppTheme.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.82)

                if let badge = badge(for: tab) {
                    DockhandMiniPill(text: badge, tint: selected ? dockhandColor : AppTheme.textMuted)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(selected ? dockhandColor.opacity(0.15) : AppTheme.surface.opacity(0.82))
            )
            .overlay {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .stroke(selected ? dockhandColor.opacity(0.45) : .white.opacity(0.04), lineWidth: 1)
            }
            .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var tabContent: some View {
        switch selectedTab {
        case .overview:
            overviewTabContent
        case .containers:
            containerSection
        case .stacks:
            stackSection
        case .activity:
            activitySection
        case .schedules:
            schedulesSection
        }
    }

    private var overviewTabContent: some View {
        VStack(alignment: .leading, spacing: 12) {
            resourcesSection
            quickContainersSection
            quickStacksSection
        }
    }

    private var quickContainersSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionHeader(localizer.t.portainerContainers, trailing: "\(filteredContainers.count)")

            let preview = Array(filteredContainers.prefix(3))
            if preview.isEmpty {
                placeholder(localizer.t.dockhandNoContainers)
            } else {
                ForEach(preview) { container in
                    Button {
                        openContainerDetail(container.id)
                    } label: {
                        containerCard(container)
                    }
                    .buttonStyle(.plain)
                }

                if filteredContainers.count > preview.count {
                    openTabButton(title: localizer.t.portainerViewAll, tab: .containers)
                }
            }
        }
    }

    private var quickStacksSection: some View {
        let stacks = dashboard?.stacks ?? []
        return VStack(alignment: .leading, spacing: 10) {
            sectionHeader(localizer.t.dockhandStacks, trailing: "\(stacks.count)")

            let preview = Array(stacks.prefix(3))
            if preview.isEmpty {
                placeholder(localizer.t.dockhandNoStacks)
            } else {
                ForEach(preview) { stack in
                    Button {
                        openStackDetail(stack)
                    } label: {
                        stackCard(stack)
                    }
                    .buttonStyle(.plain)
                }

                if stacks.count > preview.count {
                    openTabButton(title: localizer.t.dockhandStacks, tab: .stacks)
                }
            }
        }
    }

    private func openTabButton(title: String, tab: DockhandDashboardTab) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedTab = tab
            }
        } label: {
            HStack(spacing: 6) {
                Text(title)
                    .font(.caption.weight(.semibold))
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
            }
            .foregroundStyle(dockhandColor)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(dockhandColor.opacity(0.1), in: Capsule())
        }
        .buttonStyle(.plain)
    }

    private var containerSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionHeader(localizer.t.portainerContainers, trailing: "\(filteredContainers.count)")

            if filteredContainers.isEmpty && state.value != nil {
                placeholder(localizer.t.dockhandNoContainers)
            } else {
                ForEach(filteredContainers) { container in
                    Button {
                        openContainerDetail(container.id)
                    } label: {
                        containerCard(container)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var stackSection: some View {
        let stacks = dashboard?.stacks ?? []
        return VStack(alignment: .leading, spacing: 10) {
            sectionHeader(localizer.t.dockhandStacks, trailing: "\(stacks.count)")

            if stacks.isEmpty && state.value != nil {
                placeholder(localizer.t.dockhandNoStacks)
            } else {
                ForEach(stacks) { stack in
                    Button {
                        openStackDetail(stack)
                    } label: {
                        stackCard(stack)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var resourcesSection: some View {
        let stats = dashboard?.stats ?? DockhandStatsInfo(
            totalContainers: 0,
            runningContainers: 0,
            stoppedContainers: 0,
            issueContainers: 0,
            stacks: 0,
            images: 0,
            volumes: 0,
            networks: 0
        )

        return VStack(alignment: .leading, spacing: 10) {
            sectionHeader(localizer.t.dockhandResources, trailing: nil)

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                DockhandResourceStatCard(title: localizer.t.dockhandImages, value: stats.images, tint: dockhandColor)
                DockhandResourceStatCard(title: localizer.t.dockhandVolumes, value: stats.volumes, tint: AppTheme.info)
                DockhandResourceStatCard(title: localizer.t.dockhandNetworks, value: stats.networks, tint: AppTheme.running)
                DockhandResourceStatCard(title: localizer.t.dockhandStacks, value: stats.stacks, tint: AppTheme.warning)
            }
        }
    }

    private var activitySection: some View {
        let activity = limitedActivity
        return VStack(alignment: .leading, spacing: 10) {
            sectionHeader(localizer.t.dockhandActivity, trailing: "\(dashboard?.activity.count ?? 0)")

            if activity.isEmpty {
                placeholder(localizer.t.noData)
            } else {
                ForEach(activity) { item in
                    activityRow(item)
                }
            }
        }
    }

    private var schedulesSection: some View {
        let schedules = limitedSchedules
        return VStack(alignment: .leading, spacing: 10) {
            sectionHeader(localizer.t.dockhandSchedules, trailing: "\(dashboard?.schedules.count ?? 0)")

            if schedules.isEmpty {
                placeholder(localizer.t.noData)
            } else {
                ForEach(schedules) { item in
                    Button {
                        openScheduleDetail(item)
                    } label: {
                        scheduleRow(item)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func containerCard(_ container: DockhandContainerInfo) -> some View {
        DockhandContainerCard(container: container) {
            openContainerDetail(container.id)
        }
    }

    private func stackCard(_ stack: DockhandStackInfo) -> some View {
        DockhandStackCard(stack: stack) {
            openStackDetail(stack)
        }
    }

    private func activityRow(_ item: DockhandActivityInfo) -> some View {
        let tint = activityStatusTint(for: item.status)
        let icon = activityIcon(for: item)
        let title = displayActivityTitle(item)
        let subtitle = displayActivitySubtitle(item)

        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 10) {
                ZStack {
                    Circle()
                        .fill(tint.opacity(0.14))
                        .frame(width: 28, height: 28)
                    Image(systemName: icon)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(tint)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(2)

                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                        .lineLimit(2)
                }

                Spacer(minLength: 0)

                DockhandMiniPill(text: displayActivityText(item.status), tint: tint)
            }

            HStack(spacing: 8) {
                DockhandMiniPill(text: displayActivityCategory(item), tint: dockhandColor)
                if let createdAt = displayDockhandDate(item.createdAt) {
                    Label(createdAt, systemImage: "clock")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
        }
        .padding(12)
        .glassCard(cornerRadius: AppTheme.smallRadius)
    }

    private func scheduleRow(_ item: DockhandScheduleInfo) -> some View {
        let tint = item.enabled ? AppTheme.running : AppTheme.warning
        let nextRun = displayDockhandDate(item.nextRun)

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(item.name)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Spacer()
                DockhandMiniPill(text: item.enabled ? localizer.t.statusOnline : localizer.t.dockhandDisabled, tint: tint)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
            }
            if let schedule = item.schedule, !schedule.isEmpty {
                Text(schedule)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(2)
            }
            if let nextRun {
                Label(nextRun, systemImage: "clock")
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
        .padding(12)
        .glassCard(cornerRadius: AppTheme.smallRadius)
    }

    private func sectionHeader(_ title: String, trailing: String? = nil, uppercased: Bool = true) -> some View {
        DockhandSectionTitle(title: title, trailing: trailing, uppercased: uppercased)
    }

    private func placeholder(_ text: String) -> some View {
        DockhandPlaceholder(text: text)
    }

    private func detailRow(_ label: String, _ value: String) -> some View {
        DockhandDetailRow(label: label, value: value)
    }

    @ViewBuilder
    private var dockhandSettingsSheet: some View {
        NavigationStack {
            Form {
                Section(localizer.t.dockhandSettingsRefresh) {
                    Toggle(localizer.t.dockhandAutoRefresh, isOn: $autoRefreshEnabled)

                    Picker(localizer.t.dockhandRefreshInterval, selection: $autoRefreshSeconds) {
                        Text("15s").tag(15)
                        Text("30s").tag(30)
                        Text("45s").tag(45)
                        Text("60s").tag(60)
                        Text("120s").tag(120)
                    }
                    .disabled(!autoRefreshEnabled)
                }

                Section(localizer.t.dockhandSettingsData) {
                    Picker(localizer.t.dockhandActivityLimit, selection: $activityLimit) {
                        Text("10").tag(10)
                        Text("20").tag(20)
                        Text("50").tag(50)
                        Text("100").tag(100)
                    }

                    Toggle(localizer.t.dockhandShowAdvancedActivity, isOn: $showAdvancedActivity)
                }
            }
            .navigationTitle(localizer.t.dockhandSettingsTitle)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(localizer.t.done) { showSettingsSheet = false }
                }
            }
        }
    }

    @ViewBuilder
    private var containerDetailSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: AppTheme.gridSpacing) {
                switch containerDetailState {
                case .idle, .loading:
                    VStack(spacing: 10) {
                        ProgressView()
                        Text(localizer.t.loading)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 28)

                case .offline:
                    placeholder(localizer.t.error)

                case .error(let error):
                    VStack(spacing: 10) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.title3)
                            .foregroundStyle(AppTheme.warning)
                        Text(error.localizedDescription)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                            .multilineTextAlignment(.center)
                        Button(localizer.t.retry) {
                            Task { await fetchContainerDetail(forceLoading: true) }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)

                case .loaded(let detail):
                    containerDetailContent(detail)
                }
            }
            .padding(AppTheme.padding)
        }
    }

    @ViewBuilder
    private var stackDetailSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch selectedStackDetailState {
                case .idle, .loading:
                    VStack(spacing: 10) {
                        ProgressView()
                        Text(localizer.t.loading)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 28)

                case .offline:
                    placeholder(localizer.t.error)

                case .error(let error):
                    VStack(spacing: 10) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.title3)
                            .foregroundStyle(AppTheme.warning)
                        Text(error.localizedDescription)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                            .multilineTextAlignment(.center)
                        Button(localizer.t.retry) {
                            Task { await fetchStackDetail(forceLoading: true) }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)

                case .loaded(let detail):
                    Text(detail.stack.name)
                        .font(.title3.weight(.semibold))

                    Text(detail.stack.source ?? detail.stack.status)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(2)

                    HStack(spacing: 8) {
                        DockhandStackActionButton(title: localizer.t.actionStart, icon: "play.fill", tint: AppTheme.running, enabled: !isRunningAction && !isSavingCompose) {
                            Task { await runStackAction(.start, stack: detail.stack) }
                        }
                        DockhandStackActionButton(title: localizer.t.actionRestart, icon: "arrow.clockwise", tint: AppTheme.info, enabled: !isRunningAction && !isSavingCompose) {
                            Task { await runStackAction(.restart, stack: detail.stack) }
                        }
                        DockhandStackActionButton(title: localizer.t.actionStop, icon: "stop.fill", tint: AppTheme.warning, enabled: !isRunningAction && !isSavingCompose) {
                            Task { await runStackAction(.stop, stack: detail.stack) }
                        }
                    }

                    if isRunningAction {
                        HStack(spacing: 8) {
                            ProgressView()
                            Text(localizer.t.loading)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }
                    if isSavingCompose {
                        HStack(spacing: 8) {
                            ProgressView()
                            Text(localizer.t.detailComposeLoading)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }

                    let detailRows = curatedStackRows(detail)
                    if !detailRows.isEmpty {
                        sectionHeader(localizer.t.detailContainer, trailing: nil)
                        VStack(spacing: 8) {
                            ForEach(Array(detailRows.enumerated()), id: \.offset) { _, pair in
                                detailRow(pair.0, pair.1)
                            }
                        }
                        .padding(12)
                        .glassCard(cornerRadius: AppTheme.smallRadius)
                    }

                    let relatedContainers = stackRelatedContainers(for: detail.stack)
                    if !relatedContainers.isEmpty {
                        sectionHeader(localizer.t.portainerContainers, trailing: "\(relatedContainers.count)")
                        VStack(spacing: 8) {
                            ForEach(Array(relatedContainers.prefix(6))) { container in
                                Button {
                                    selectedStackName = nil
                                    selectedStackDetailState = .idle
                                    openContainerDetail(container.id)
                                } label: {
                                    containerCard(container)
                                }
                                .buttonStyle(.plain)
                            }

                            if relatedContainers.count > 6 {
                                openTabButton(title: localizer.t.portainerViewAll, tab: .containers)
                            }
                        }
                    }

                    let composeEditable = canEditCompose(detail.compose)
                    sectionHeader(localizer.t.detailComposeFile, trailing: nil, uppercased: false)
                    if isEditingCompose {
                        VStack(alignment: .leading, spacing: 8) {
                            TextEditor(text: $composeDraft)
                                .font(.caption.monospaced())
                                .frame(minHeight: 180)
                                .padding(8)
                                .background(AppTheme.surface.opacity(0.66), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                            HStack(spacing: 8) {
                                DockhandDetailActionButton(
                                    title: localizer.t.cancel,
                                    icon: "xmark",
                                    tint: AppTheme.textMuted,
                                    enabled: !isSavingCompose
                                ) {
                                    isEditingCompose = false
                                    composeDraft = detail.compose
                                }
                                DockhandDetailActionButton(
                                    title: localizer.t.detailComposeSave,
                                    icon: "checkmark",
                                    tint: dockhandColor,
                                    enabled: !isSavingCompose && !composeDraft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                ) {
                                    Task { await saveStackCompose() }
                                }
                            }
                        }
                        .padding(12)
                        .glassCard(cornerRadius: AppTheme.smallRadius)
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Spacer()
                                DockhandDetailActionButton(
                                    title: localizer.t.copy,
                                    icon: "doc.on.doc",
                                    tint: dockhandColor,
                                    enabled: !detail.compose.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                ) {
                                    UIPasteboard.general.string = detail.compose
                                    actionMessage = localizer.t.copy
                                }
                            }

                            ScrollView([.horizontal, .vertical], showsIndicators: false) {
                                Text(detail.compose)
                                    .font(.caption.monospaced())
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .textSelection(.enabled)
                            }
                            .frame(minHeight: 180, alignment: .top)

                            if composeEditable {
                                DockhandDetailActionButton(
                                    title: localizer.t.actionEdit,
                                    icon: "pencil",
                                    tint: dockhandColor,
                                    enabled: !isSavingCompose
                                ) {
                                    composeDraft = detail.compose
                                    isEditingCompose = true
                                }
                            }
                        }
                        .padding(12)
                        .glassCard(cornerRadius: AppTheme.smallRadius)
                    }
                }
            }
            .padding(AppTheme.padding)
        }
    }

    @ViewBuilder
    private var scheduleDetailSheet: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                switch selectedScheduleDetailState {
                case .idle, .loading:
                    VStack(spacing: 10) {
                        ProgressView()
                        Text(localizer.t.loading)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)

                case .offline:
                    placeholder(localizer.t.error)

                case .error(let error):
                    VStack(spacing: 10) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.title3)
                            .foregroundStyle(AppTheme.warning)
                        Text(error.localizedDescription)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                        Button(localizer.t.retry) {
                            Task { await fetchScheduleDetail(forceLoading: true) }
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 24)

                case .loaded(let detail):
                    Text(detail.schedule.name)
                        .font(.title3.weight(.semibold))

                    if let schedule = detail.schedule.schedule, !schedule.isEmpty {
                        Text(schedule)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                    }

                    let overviewRows = curatedScheduleRows(detail)
                    if !overviewRows.isEmpty {
                        VStack(spacing: 8) {
                            ForEach(Array(overviewRows.enumerated()), id: \.offset) { _, pair in
                                detailRow(pair.0, pair.1)
                            }
                        }
                        .padding(12)
                        .glassCard(cornerRadius: AppTheme.smallRadius)
                    } else {
                        placeholder(localizer.t.noData)
                    }

                    let related = scheduleActivityItems(for: detail)
                    if !related.isEmpty {
                        sectionHeader(localizer.t.dockhandActivity, trailing: "\(related.count)")
                        VStack(spacing: 8) {
                            ForEach(Array(related.prefix(6))) { item in
                                activityRow(item)
                            }
                        }
                    }
                }
            }
            .padding(AppTheme.padding)
        }
    }

    private func containerDetailContent(_ detail: DockhandContainerDetailData) -> some View {
        let overviewRows = curatedContainerRows(detail)
        let logLines = detail.logs.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        let visibleLines = showFullContainerLogs ? logLines : Array(logLines.prefix(40))
        let displayLogs = visibleLines.joined(separator: "\n")

        return VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(detail.container.name)
                    .font(.title3.weight(.semibold))
                Text(detail.container.image)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textMuted)
            }

            HStack(spacing: 8) {
                DockhandDetailActionButton(title: localizer.t.refresh, icon: "arrow.clockwise", tint: dockhandColor, enabled: !isRunningAction) {
                    Task { await fetchContainerDetail(forceLoading: false) }
                }
                DockhandDetailActionButton(title: localizer.t.actionStart, icon: "play.fill", tint: AppTheme.running, enabled: !isRunningAction) {
                    Task { await runContainerAction(.start, containerId: detail.container.id) }
                }
                DockhandDetailActionButton(title: localizer.t.actionStop, icon: "stop.fill", tint: AppTheme.warning, enabled: !isRunningAction) {
                    Task { await runContainerAction(.stop, containerId: detail.container.id) }
                }
                DockhandDetailActionButton(title: localizer.t.actionRestart, icon: "arrow.clockwise", tint: AppTheme.info, enabled: !isRunningAction) {
                    Task { await runContainerAction(.restart, containerId: detail.container.id) }
                }
            }

            if isRunningAction {
                HStack(spacing: 8) {
                    ProgressView()
                    Text(localizer.t.loading)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            sectionHeader(localizer.t.detailContainer, trailing: nil)
            if !overviewRows.isEmpty {
                VStack(spacing: 8) {
                    ForEach(Array(overviewRows.enumerated()), id: \.offset) { _, pair in
                        detailRow(pair.0, pair.1)
                    }
                }
                .padding(12)
                .glassCard(cornerRadius: AppTheme.smallRadius)
            }

            sectionHeader(localizer.t.dockhandLogs, trailing: nil)
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    if !detail.logs.isEmpty {
                        DockhandMiniPill(text: "\(logLines.count)", tint: dockhandColor)
                    }
                    Spacer()
                    if !detail.logs.isEmpty {
                        DockhandDetailActionButton(title: localizer.t.copy, icon: "doc.on.doc", tint: dockhandColor, enabled: true) {
                            UIPasteboard.general.string = detail.logs
                            actionMessage = localizer.t.copy
                        }
                    }
                    if logLines.count > 40 {
                        let remainingLines = max(0, logLines.count - 40)
                        Button(showFullContainerLogs ? localizer.t.dockhandShowLess : String(format: localizer.t.dockhandMoreLines, remainingLines)) {
                            withAnimation(.easeInOut(duration: 0.18)) {
                                showFullContainerLogs.toggle()
                            }
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(dockhandColor)
                        .buttonStyle(.plain)
                    }
                }

                ScrollView([.horizontal, .vertical], showsIndicators: false) {
                    Text(detail.logs.isEmpty ? localizer.t.dockhandNoLogs : displayLogs)
                        .font(.caption.monospaced())
                        .foregroundStyle(detail.logs.isEmpty ? AppTheme.textMuted : AppTheme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                }
                .frame(minHeight: 140, alignment: .top)
            }
            .padding(12)
            .glassCard(cornerRadius: AppTheme.smallRadius)
        }
    }

    // MARK: - Tab helpers

    private func title(for tab: DockhandDashboardTab) -> String {
        switch tab {
        case .overview:
            return localizer.t.summaryTitle
        case .containers:
            return localizer.t.portainerContainers
        case .stacks:
            return localizer.t.dockhandStacks
        case .activity:
            return localizer.t.dockhandActivity
        case .schedules:
            return localizer.t.dockhandSchedules
        }
    }

    private func icon(for tab: DockhandDashboardTab) -> String {
        switch tab {
        case .overview:
            return "square.grid.2x2.fill"
        case .containers:
            return "shippingbox.fill"
        case .stacks:
            return "square.3.layers.3d.fill"
        case .activity:
            return "bolt.fill"
        case .schedules:
            return "calendar"
        }
    }

    private func tint(for tab: DockhandDashboardTab) -> Color {
        switch tab {
        case .overview:
            return dockhandColor
        case .containers:
            return AppTheme.running
        case .stacks:
            return AppTheme.info
        case .activity:
            return AppTheme.warning
        case .schedules:
            return AppTheme.textMuted
        }
    }

    private func badge(for tab: DockhandDashboardTab) -> String? {
        switch tab {
        case .overview:
            return nil
        case .containers:
            return "\(filteredContainers.count)"
        case .stacks:
            return "\(dashboard?.stacks.count ?? 0)"
        case .activity:
            return "\(dashboard?.activity.count ?? 0)"
        case .schedules:
            return "\(dashboard?.schedules.count ?? 0)"
        }
    }

    private func displayActivityText(_ raw: String) -> String {
        guard !showAdvancedActivity else { return raw }
        let cleaned = raw
            .replacingOccurrences(of: "_", with: " ")
            .replacingOccurrences(of: ":", with: " ")
            .replacingOccurrences(of: "-", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)

        guard !cleaned.isEmpty else { return raw }
        return cleaned
            .split(separator: " ")
            .map { $0.capitalized }
            .joined(separator: " ")
    }

    private func displayActivityTitle(_ item: DockhandActivityInfo) -> String {
        let target = displayActivityText(item.target)
        if target != "-", !target.isEmpty {
            return target
        }
        return displayActivityText(item.action)
    }

    private func displayActivitySubtitle(_ item: DockhandActivityInfo) -> String {
        let action = displayActivityText(item.action)
        let status = displayActivityText(item.status)
        if action.caseInsensitiveCompare(status) == .orderedSame {
            return action
        }
        return "\(action) • \(status)"
    }

    private func displayActivityCategory(_ item: DockhandActivityInfo) -> String {
        let haystack = "\(item.action) \(item.target)".lowercased()
        if haystack.contains("schedule") || haystack.contains("cron") || haystack.contains("cleanup") {
            return localizer.t.dockhandSchedules
        }
        if haystack.contains("stack") || haystack.contains("compose") {
            return localizer.t.dockhandStacks
        }
        return localizer.t.portainerContainers
    }

    private func displayDockhandDate(_ raw: String?) -> String? {
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("{") || trimmed.hasPrefix("[") {
            return nil
        }
        let formatted = Formatters.formatDate(trimmed)
        if formatted == trimmed && trimmed.contains("T") {
            return nil
        }
        return formatted
    }

    private func dockhandDetailMap(_ pairs: [(String, String)]) -> [String: String] {
        Dictionary(
            pairs.map { ($0.0.lowercased(), $0.1) },
            uniquingKeysWith: { current, _ in current }
        )
    }

    private func curatedContainerRows(_ detail: DockhandContainerDetailData) -> [(String, String)] {
        let map = dockhandDetailMap(detail.details)
        let rows: [(String, String?)] = [
            (localizer.t.dockhandState, detail.container.state),
            (localizer.t.dockhandStatus, detail.container.status),
            (localizer.t.dockhandPorts, detail.container.portsSummary),
            (localizer.t.dockhandHealth, detail.container.health),
            (dockhandLabel(for: "created"), map["created"] ?? map["createdat"]),
            (dockhandLabel(for: "platform"), map["platform"]),
            (dockhandLabel(for: "runtime"), map["runtime"]),
            (dockhandLabel(for: "driver"), map["driver"]),
            (dockhandLabel(for: "restartpolicy"), map["restartpolicy"]),
            (dockhandLabel(for: "networkmode"), map["networkmode"]),
            (dockhandLabel(for: "command"), map["command"]),
            (dockhandLabel(for: "entrypoint"), map["entrypoint"])
        ]

        return rows.compactMap { label, value in
            guard let value, !value.isEmpty, value != "-" else { return nil }
            return (label, compactDockhandValue(value))
        }
    }

    private func curatedStackRows(_ detail: DockhandStackDetailData) -> [(String, String)] {
        let map = dockhandDetailMap(detail.details)
        let rows: [(String, String?)] = [
            (localizer.t.dockhandStatus, detail.stack.status),
            (dockhandLabel(for: "services"), "\(detail.stack.services)"),
            (dockhandLabel(for: "source"), detail.stack.source),
            (dockhandLabel(for: "sourcetype"), map["sourcetype"] ?? map["source"]),
            (dockhandLabel(for: "environment"), detail.stack.environmentId),
            (dockhandLabel(for: "id"), map["id"])
        ]

        return rows.compactMap { label, value in
            guard let value, !value.isEmpty, value != "-" else { return nil }
            return (label, compactDockhandValue(value))
        }
    }

    private func curatedScheduleRows(_ detail: DockhandScheduleDetailData) -> [(String, String)] {
        let map = dockhandDetailMap(detail.details)
        let rows: [(String, String?)] = [
            (localizer.t.dockhandStatus, detail.schedule.enabled ? localizer.t.statusOnline : localizer.t.dockhandDisabled),
            (dockhandLabel(for: "schedule"), detail.schedule.schedule ?? map["cronexpression"] ?? map["schedule"]),
            (dockhandLabel(for: "nextrun"), displayDockhandDate(detail.schedule.nextRun) ?? displayDockhandDate(map["nextrun"])),
            (dockhandLabel(for: "lastrun"), displayDockhandDate(detail.schedule.lastRun) ?? displayDockhandDate(map["lastexecution"])),
            (dockhandLabel(for: "description"), compactDockhandValue(map["description"] ?? "")),
            (dockhandLabel(for: "entityname"), map["entityname"]),
            (dockhandLabel(for: "issystem"), dockhandBooleanLabel(map["issystem"])),
            (dockhandLabel(for: "enabled"), dockhandBooleanLabel(map["enabled"])),
            (dockhandLabel(for: "recentexecutions"), dockhandExecutionSummary(map["recentexecutions"]))
        ]

        return rows.compactMap { label, value in
            guard let value, !value.isEmpty, value != "-" else { return nil }
            return (label, compactDockhandValue(value))
        }
    }

    private func dockhandLabel(for key: String) -> String {
        switch key.lowercased() {
        case "created", "createdat":
            return localizer.t.detailCreated
        case "platform":
            return localizer.t.dockhandPlatform
        case "runtime":
            return localizer.t.dockhandRuntime
        case "driver":
            return localizer.t.dockhandDriver
        case "restartpolicy":
            return localizer.t.detailRestartPolicy
        case "networkmode":
            return localizer.t.detailMode
        case "command":
            return localizer.t.detailCommand
        case "entrypoint":
            return localizer.t.dockhandEntrypoint
        case "services":
            return localizer.t.dockhandServicesLabel
        case "source":
            return localizer.t.dockhandSource
        case "sourcetype":
            return localizer.t.dockhandSourceType
        case "environment":
            return localizer.t.dockhandEnvironments
        case "schedule":
            return localizer.t.healthchecksSchedule
        case "nextrun":
            return localizer.t.dockhandNextRun
        case "lastrun":
            return localizer.t.dockhandLastRun
        case "description":
            return localizer.t.dockhandDescription
        case "entityname":
            return localizer.t.dockhandEntity
        case "issystem":
            return localizer.t.dockhandSystemLabel
        case "enabled":
            return localizer.t.dockhandEnabledLabel
        case "recentexecutions":
            return localizer.t.dockhandRecentRuns
        case "id":
            return localizer.t.detailId
        default:
            return displayActivityText(key)
        }
    }

    private func compactDockhandValue(_ value: String, limit: Int = 180) -> String {
        let normalized = value
            .replacingOccurrences(of: "\n", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard normalized.count > limit else { return normalized }
        return String(normalized.prefix(limit)) + "…"
    }

    private func dockhandBooleanLabel(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let normalized = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch normalized {
        case "1", "true", "yes":
            return localizer.t.statusOnline
        case "0", "false", "no":
            return localizer.t.dockhandDisabled
        default:
            return nil
        }
    }

    private func dockhandExecutionSummary(_ raw: String?) -> String? {
        guard let raw, !raw.isEmpty else { return nil }
        let completedCount = raw.components(separatedBy: "completedAt").count - 1
        if completedCount > 0 {
            return "\(completedCount)"
        }
        return compactDockhandValue(raw, limit: 80)
    }

    private func containerTint(_ container: DockhandContainerInfo) -> Color {
        if container.isIssue { return AppTheme.danger }
        return container.isRunning ? AppTheme.running : AppTheme.warning
    }

    private func healthTint(for health: String) -> Color {
        let value = health.lowercased()
        if value.contains("unhealthy") || value.contains("fail") { return AppTheme.danger }
        if value.contains("starting") { return AppTheme.warning }
        if value.contains("healthy") { return AppTheme.running }
        return AppTheme.info
    }

    private func activityStatusTint(for status: String) -> Color {
        let value = status.lowercased()
        if value.contains("fail") || value.contains("error") || value.contains("die") || value.contains("kill") {
            return AppTheme.danger
        }
        if value.contains("stop") || value.contains("created") || value.contains("pending") {
            return AppTheme.warning
        }
        return AppTheme.running
    }

    private func activityIcon(for item: DockhandActivityInfo) -> String {
        let status = item.status.lowercased()
        let action = item.action.lowercased()
        if status.contains("fail") || status.contains("error") {
            return "exclamationmark.triangle.fill"
        }
        if action.contains("start") {
            return "play.fill"
        }
        if action.contains("stop") || action.contains("kill") || action.contains("die") {
            return "stop.fill"
        }
        if action.contains("restart") {
            return "arrow.clockwise"
        }
        return "info.circle.fill"
    }

    private func scheduleActivityItems(for detail: DockhandScheduleDetailData) -> [DockhandActivityInfo] {
        let nameNeedle = detail.schedule.name.lowercased()
        let idNeedle = detail.schedule.id.lowercased()
        return limitedActivity.filter { item in
            let haystack = "\(item.action) \(item.target)".lowercased()
            if !nameNeedle.isEmpty && haystack.contains(nameNeedle) {
                return true
            }
            if !idNeedle.isEmpty && haystack.contains(idNeedle) {
                return true
            }
            return false
        }
    }

    private func runAutoRefreshLoop() async {
        guard autoRefreshEnabled else { return }
        let seconds = max(15, autoRefreshSeconds)

        while !Task.isCancelled {
            do {
                try await Task.sleep(nanoseconds: UInt64(seconds) * 1_000_000_000)
            } catch {
                return
            }

            if Task.isCancelled { return }
            if scenePhase != .active { continue }
            if isRunningAction || isRefreshing { continue }

            await fetchDashboard(forceLoading: false)
        }
    }

    private func fetchDashboard(forceLoading: Bool) async {
        if isRefreshing && !forceLoading { return }
        if forceLoading || state.value == nil {
            state = .loading
        }
        isRefreshing = true
        defer { isRefreshing = false }

        do {
            guard let client = await servicesStore.dockhandClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            let data = try await client.getDashboard(environmentId: selectedEnvironmentId)
            dashboard = data
            state = .loaded(())

            if let selectedContainerId,
               !data.containers.contains(where: { $0.id == selectedContainerId }) {
                self.selectedContainerId = nil
                containerDetailState = .idle
            }

            if let selectedStackName,
               !data.stacks.contains(where: { $0.name == selectedStackName }) {
                self.selectedStackName = nil
                selectedStackDetailState = .idle
            }

            if let selectedScheduleId,
               !data.schedules.contains(where: { $0.id == selectedScheduleId }) {
                self.selectedScheduleId = nil
                selectedScheduleDetailState = .idle
            }
        } catch let error as APIError {
            state = .error(error)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func fetchContainerDetail(forceLoading: Bool) async {
        guard let containerId = selectedContainerId else { return }
        if forceLoading || containerDetailState.value == nil {
            containerDetailState = .loading
        }

        do {
            guard let client = await servicesStore.dockhandClient(instanceId: selectedInstanceId) else {
                containerDetailState = .error(.notConfigured)
                return
            }
            let detail = try await client.getContainerDetail(
                id: containerId,
                environmentId: selectedEnvironmentForContainer(containerId)
            )
            containerDetailState = .loaded(detail)
        } catch let error as APIError {
            containerDetailState = .error(error)
        } catch {
            containerDetailState = .error(.custom(error.localizedDescription))
        }
    }

    private func openContainerDetail(_ containerId: String) {
        HapticManager.light()
        selectedContainerId = containerId
        showFullContainerLogs = false
        Task { await fetchContainerDetail(forceLoading: true) }
    }

    private func runContainerAction(_ action: DockhandContainerActionKind, containerId: String) async {
        guard !isRunningAction else { return }
        isRunningAction = true
        defer { isRunningAction = false }

        do {
            guard let client = await servicesStore.dockhandClient(instanceId: selectedInstanceId) else {
                throw APIError.notConfigured
            }
            let result = try await client.runContainerAction(
                id: containerId,
                action: action,
                environmentId: selectedEnvironmentForContainer(containerId)
            )
            actionMessage = result.message
            await fetchDashboard(forceLoading: false)
            await fetchContainerDetail(forceLoading: false)
        } catch let error as APIError {
            actionMessage = error.localizedDescription
        } catch {
            actionMessage = error.localizedDescription
        }
    }

    private func openStackDetail(_ stack: DockhandStackInfo) {
        HapticManager.light()
        selectedStackName = stack.name
        selectedStackDetailState = .loading
        composeDraft = ""
        isEditingCompose = false
        isSavingCompose = false
        Task { await fetchStackDetail(forceLoading: true) }
    }

    private func fetchStackDetail(forceLoading: Bool) async {
        guard let stackName = selectedStackName else { return }
        if forceLoading || selectedStackDetailState.value == nil {
            selectedStackDetailState = .loading
        }

        do {
            guard let client = await servicesStore.dockhandClient(instanceId: selectedInstanceId) else {
                selectedStackDetailState = .error(.notConfigured)
                return
            }
            let detail = try await client.getStackDetail(
                name: stackName,
                environmentId: selectedEnvironmentForStack(stackName)
            )
            selectedStackDetailState = .loaded(detail)
            if !isEditingCompose {
                composeDraft = detail.compose
            }
        } catch let error as APIError {
            selectedStackDetailState = .error(error)
        } catch {
            selectedStackDetailState = .error(.custom(error.localizedDescription))
        }
    }

    private func openScheduleDetail(_ schedule: DockhandScheduleInfo) {
        HapticManager.light()
        selectedScheduleId = schedule.id
        selectedScheduleDetailState = .loading
        Task { await fetchScheduleDetail(forceLoading: true) }
    }

    private func fetchScheduleDetail(forceLoading: Bool) async {
        guard let scheduleId = selectedScheduleId else { return }
        if forceLoading || selectedScheduleDetailState.value == nil {
            selectedScheduleDetailState = .loading
        }

        let fallback = dashboard?.schedules.first(where: { $0.id == scheduleId })

        do {
            guard let client = await servicesStore.dockhandClient(instanceId: selectedInstanceId) else {
                selectedScheduleDetailState = .error(.notConfigured)
                return
            }
            let detail = try await client.getScheduleDetail(
                id: scheduleId,
                environmentId: fallback?.environmentId ?? selectedEnvironmentId
            )
            selectedScheduleDetailState = .loaded(detail)
        } catch {
            if let fallback {
                selectedScheduleDetailState = .loaded(
                    DockhandScheduleDetailData(schedule: fallback, details: [])
                )
            } else if let apiError = error as? APIError {
                selectedScheduleDetailState = .error(apiError)
            } else {
                selectedScheduleDetailState = .error(.custom(error.localizedDescription))
            }
        }
    }

    private func runStackAction(_ action: DockhandStackActionKind, stack: DockhandStackInfo) async {
        guard !isRunningAction else { return }
        isRunningAction = true
        defer { isRunningAction = false }

        do {
            guard let client = await servicesStore.dockhandClient(instanceId: selectedInstanceId) else {
                throw APIError.notConfigured
            }
            let result = try await client.runStackAction(
                name: stack.name,
                action: action,
                environmentId: stack.environmentId ?? selectedEnvironmentId
            )
            actionMessage = result.message
            await fetchDashboard(forceLoading: false)
            await fetchStackDetail(forceLoading: false)
        } catch let error as APIError {
            actionMessage = error.localizedDescription
        } catch {
            actionMessage = error.localizedDescription
        }
    }

    private func saveStackCompose() async {
        guard !isSavingCompose else { return }
        guard let stackName = selectedStackName else { return }

        let compose = composeDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !compose.isEmpty else { return }

        isSavingCompose = true
        defer { isSavingCompose = false }

        do {
            guard let client = await servicesStore.dockhandClient(instanceId: selectedInstanceId) else {
                throw APIError.notConfigured
            }
            let outcome = try await client.updateStackCompose(
                name: stackName,
                compose: compose,
                environmentId: selectedEnvironmentForStack(stackName)
            )
            actionMessage = outcome.message
            isEditingCompose = false
            await fetchDashboard(forceLoading: false)
            await fetchStackDetail(forceLoading: false)
        } catch let error as APIError {
            actionMessage = error.localizedDescription
        } catch {
            actionMessage = error.localizedDescription
        }
    }

    private func selectedEnvironmentForContainer(_ containerId: String) -> String? {
        dashboard?.containers.first(where: { $0.id == containerId })?.environmentId ?? selectedEnvironmentId
    }

    private func selectedEnvironmentForStack(_ stackName: String) -> String? {
        dashboard?.stacks.first(where: { $0.name == stackName })?.environmentId ?? selectedEnvironmentId
    }

    private func stackRelatedContainers(for stack: DockhandStackInfo) -> [DockhandContainerInfo] {
        let needle = stack.name.lowercased()
        return (dashboard?.containers ?? [])
            .filter { container in
                let envMatches = stack.environmentId == nil ||
                    container.environmentId == nil ||
                    container.environmentId == stack.environmentId
                let loweredName = container.name.lowercased()
                return envMatches &&
                    (loweredName.hasPrefix("\(needle)_") || loweredName.contains(needle))
            }
            .sorted { lhs, rhs in
                if lhs.isRunning != rhs.isRunning {
                    return lhs.isRunning && !rhs.isRunning
                }
                return lhs.name.localizedCaseInsensitiveCompare(rhs.name) == .orderedAscending
            }
    }

    private func canEditCompose(_ compose: String) -> Bool {
        let normalized = compose.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return false }
        let lowered = normalized.lowercased()
        if lowered == "-" || lowered == "n/a" {
            return false
        }
        if lowered.hasPrefix("compose not available") {
            return false
        }
        return true
    }
}

private enum DockhandContainerFilter {
    case all
    case running
    case stopped
    case issues
}

private enum DockhandDashboardTab: CaseIterable, Identifiable {
    case overview
    case containers
    case stacks
    case activity
    case schedules

    var id: Self { self }
}
