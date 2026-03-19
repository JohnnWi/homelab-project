import SwiftUI

struct HealthchecksCheckDetail: View {
    let instanceId: UUID
    let check: HealthchecksCheck
    let channels: [HealthchecksChannel]

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    @State private var state: LoadableState<Void> = .idle
    @State private var detail: HealthchecksCheck
    @State private var pings: [HealthchecksPing] = []
    @State private var flips: [HealthchecksFlip] = []
    @State private var showBody = false
    @State private var bodyText: String = ""
    @State private var showEditor = false
    @State private var showDeleteConfirm = false
    @State private var showActionError = false
    @State private var actionError: String?
    private let smoothAnimation = Animation.spring(response: 0.45, dampingFraction: 0.86)
    @State private var showIntegrationsEditor = false

    init(instanceId: UUID, check: HealthchecksCheck, channels: [HealthchecksChannel]) {
        self.instanceId = instanceId
        self.check = check
        self.channels = channels
        _detail = State(initialValue: check)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .healthchecks,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchDetail
        ) {
            headerCard
            if !detail.hasUUID {
                readOnlyBanner
            }
            infoGrid
            channelsSection
            pingsSection
            flipsSection
        }
        .animation(smoothAnimation, value: detail.status)
        .animation(smoothAnimation, value: pings.count)
        .animation(smoothAnimation, value: flips.count)
        .navigationTitle(detail.name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if detail.hasUUID {
                    Menu {
                        Button(localizer.t.healthchecksEditCheck) {
                            showEditor = true
                        }
                        Button(detail.isPaused ? localizer.t.actionResume : localizer.t.actionPause) {
                            Task { await togglePause() }
                        }
                        Button(localizer.t.healthchecksDeleteCheck, role: .destructive) {
                            showDeleteConfirm = true
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
            }
        }
        .sheet(isPresented: $showEditor) {
            HealthchecksCheckEditor(instanceId: instanceId, existing: detail) {
                await fetchDetail()
            }
        }
        .sheet(isPresented: $showIntegrationsEditor) {
            HealthchecksIntegrationsEditor(instanceId: instanceId, check: detail) {
                await fetchDetail()
            }
        }
        .sheet(isPresented: $showBody) {
            NavigationStack {
                ScrollView {
                    Text(bodyText)
                        .font(.caption.monospaced())
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                }
                .navigationTitle(localizer.t.healthchecksPingBody)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            UIPasteboard.general.string = bodyText
                        } label: {
                            Image(systemName: "doc.on.doc")
                        }
                    }
                }
            }
            .presentationDetents([.medium, .large])
        }
        .alert(localizer.t.healthchecksDeleteConfirmTitle, isPresented: $showDeleteConfirm) {
            Button(localizer.t.healthchecksDeleteCheck, role: .destructive) {
                Task { await deleteCheck() }
            }
            Button(localizer.t.cancel, role: .cancel) { }
        } message: {
            Text(localizer.t.healthchecksDeleteConfirmMessage)
        }
        .alert(localizer.t.error, isPresented: $showActionError) {
            Button(localizer.t.confirm, role: .cancel) { }
        } message: {
            Text(actionError ?? localizer.t.errorUnknown)
        }
        .task {
            await fetchDetail()
        }
    }

    private var headerCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                StatusBadge(status: detail.status)
                VStack(alignment: .leading, spacing: 4) {
                    Text(detail.name)
                        .font(.title3.bold())
                    if !statusSubtitle.isEmpty {
                        Text(statusSubtitle)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(2)
                            .minimumScaleFactor(0.85)
                    }
                }
                Spacer()
            }

            if let desc = detail.desc, !desc.isEmpty {
                Text(desc)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
            }

            if !detail.tagsList.isEmpty {
                FlexibleTagRow(tags: detail.tagsList)
            }

            if let pingUrl = detail.pingUrl {
                HStack(spacing: 8) {
                    Image(systemName: "link")
                        .foregroundStyle(AppTheme.textMuted)
                    Text(pingUrl)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                    Spacer()
                    Button {
                        UIPasteboard.general.string = pingUrl
                    } label: {
                        Image(systemName: "doc.on.doc")
                            .font(.caption)
                    }
                }
                .padding(12)
                .glassCard(cornerRadius: 12)
            }
        }
        .padding(16)
        .glassCard(tint: AppTheme.surface.opacity(0.45))
    }

    private var readOnlyBanner: some View {
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
        .glassCard(tint: AppTheme.surface.opacity(0.45))
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private var infoGrid: some View {
        let hasSchedule = detail.schedule?.isEmpty == false
        let items: [InfoItem] = [
            InfoItem(title: localizer.t.healthchecksLastPing, value: formattedDate(detail.lastPing), icon: "clock.fill", color: AppTheme.info),
            InfoItem(title: localizer.t.healthchecksNextPing, value: formattedDate(detail.nextPing), icon: "calendar.badge.clock", color: AppTheme.created),
            InfoItem(title: localizer.t.healthchecksGracePeriod, value: secondsValue(detail.grace), icon: "hourglass", color: AppTheme.warning),
            InfoItem(title: hasSchedule ? localizer.t.healthchecksSchedule : localizer.t.healthchecksTimeout, value: scheduleValue, icon: hasSchedule ? "calendar" : "timer", color: hasSchedule ? AppTheme.created : AppTheme.warning),
            InfoItem(title: localizer.t.healthchecksTimezone, value: detail.tz ?? localizer.t.notAvailable, icon: "globe.europe.africa.fill", color: AppTheme.info),
            InfoItem(title: localizer.t.healthchecksMethods, value: methodsLabel, icon: "arrow.up.right.square", color: AppTheme.info),
            InfoItem(title: localizer.t.healthchecksManualResume, value: detail.manualResume == true ? localizer.t.yes : localizer.t.no, icon: "hand.raised.fill", color: AppTheme.paused),
            InfoItem(title: localizer.t.healthchecksPings, value: detail.nPings.map(String.init) ?? localizer.t.notAvailable, icon: "waveform.path.ecg", color: AppTheme.running)
        ]
        let rows = stride(from: 0, to: items.count, by: 2).map { index in
            Array(items[index..<min(index + 2, items.count)])
        }

        return GlassCard(tint: AppTheme.surface.opacity(0.45)) {
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.offset) { rowIndex, rowItems in
                    HStack(spacing: 0) {
                        infoCell(rowItems[0])
                        Divider()
                            .background(AppTheme.textMuted.opacity(0.25))
                        if rowItems.count > 1 {
                            infoCell(rowItems[1])
                        } else {
                            Spacer()
                        }
                    }
                    .frame(maxWidth: .infinity)
                    if rowIndex < rows.count - 1 {
                        Divider()
                            .background(AppTheme.textMuted.opacity(0.25))
                    }
                }
            }
        }
    }

    private var channelsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.healthchecksIntegrations)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                if detail.hasUUID {
                    Button(localizer.t.actionEdit) {
                        showIntegrationsEditor = true
                    }
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textSecondary)
                }
            }

            if detail.channelsList.isEmpty {
                Text(localizer.t.noData)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                    .padding(12)
                    .glassCard(tint: AppTheme.surface.opacity(0.45))
            } else {
                VStack(spacing: 10) {
                    ForEach(resolvedIntegrationRows, id: \.id) { row in
                        integrationRow(row)
                    }
                }
            }
        }
    }

    private var pingsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(localizer.t.healthchecksPings, count: pings.count)

            if pings.isEmpty {
                Text(localizer.t.noData)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                    .padding(12)
                    .glassCard(tint: AppTheme.surface.opacity(0.45))
            } else {
                VStack(spacing: 10) {
                    ForEach(pings.prefix(15)) { ping in
                        Button {
                            if ping.bodyUrl != nil {
                                Task { await fetchPingBody(ping) }
                            }
                        } label: {
                            HealthchecksPingRow(ping: ping)
                        }
                        .buttonStyle(PressableCardButtonStyle())
                        .hoverEffect(.highlight)
                        .disabled(ping.bodyUrl == nil)
                    }
                }
            }
        }
    }

    private var flipsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(localizer.t.healthchecksFlips, count: flips.count)

            if flips.isEmpty {
                Text(localizer.t.noData)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                    .padding(12)
                    .glassCard(tint: AppTheme.surface.opacity(0.45))
            } else {
                VStack(spacing: 10) {
                    ForEach(flips.prefix(15)) { flip in
                        HStack(spacing: 10) {
                            StatusBadge(status: flip.isUp ? "up" : "down", compact: true)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(Formatters.formatDate(flip.timestamp))
                                    .font(.subheadline.weight(.semibold))
                                Text(flip.isUp ? localizer.t.healthchecksUp : localizer.t.healthchecksDown)
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            Spacer()
                        }
                        .padding(12)
                        .glassCard(tint: AppTheme.surface.opacity(0.45))
                    }
                }
            }
        }
    }

    private var scheduleValue: String {
        if let schedule = detail.schedule, !schedule.isEmpty {
            return schedule
        }
        return secondsValue(detail.timeout)
    }

    private var statusSubtitle: String {
        var parts: [String] = []
        if let schedule = detail.schedule, !schedule.isEmpty {
            parts.append("\(localizer.t.healthchecksSchedule): \(schedule)")
        } else if let timeout = detail.timeout {
            parts.append("\(localizer.t.healthchecksTimeout): \(timeout)s")
        }
        if let grace = detail.grace {
            parts.append("\(localizer.t.healthchecksGracePeriod): \(grace)s")
        }
        return parts.joined(separator: " • ")
    }

    private var methodsLabel: String {
        if detail.methods == "POST" {
            return localizer.t.healthchecksMethodsPostOnly
        }
        return localizer.t.healthchecksMethodsAll
    }

    private func sectionHeader(_ title: String, count: Int) -> some View {
        HStack {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)
            Spacer()
            if count > 0 {
                Text("\(min(count, 15)) / \(count)")
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
    }

    private var resolvedIntegrationRows: [IntegrationRowData] {
        detail.channelsList.map { id in
            if let channel = channels.first(where: { $0.id == id || $0.name == id }) {
                return IntegrationRowData(
                    id: channel.id,
                    name: channel.name,
                    detail: "\(channel.kind) • \(channel.id)",
                    icon: channel.iconName,
                    color: channel.accentColor
                )
            }
            return IntegrationRowData(
                id: id,
                name: id,
                detail: id,
                icon: "bell.fill",
                color: AppTheme.info
            )
        }
    }

    private func integrationRow(_ row: IntegrationRowData) -> some View {
        HStack(spacing: 12) {
            Image(systemName: row.icon)
                .foregroundStyle(row.color)
                .frame(width: 28, height: 28)
                .background(AppTheme.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(row.name)
                    .font(.subheadline.weight(.semibold))
                Text(row.detail)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(1)
            }
            Spacer()
            Button {
                UIPasteboard.general.string = row.id
            } label: {
                Image(systemName: "doc.on.doc")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
        .padding(12)
        .glassCard(tint: AppTheme.surface.opacity(0.45))
    }

    private struct IntegrationRowData: Identifiable, Hashable {
        let id: String
        let name: String
        let detail: String
        let icon: String
        let color: Color
    }

    private func infoCell(_ item: InfoItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                Image(systemName: item.icon)
                    .font(.caption.bold())
                    .foregroundStyle(item.color)
                Text(item.title)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)
            }
            Text(item.value)
                .font(.title3.bold())
                .foregroundStyle(.primary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
    }

    private struct InfoItem: Hashable, Identifiable {
        let id = UUID()
        let title: String
        let value: String
        let icon: String
        let color: Color
    }

    private func formattedDate(_ value: String?) -> String {
        guard let value, !value.isEmpty else { return localizer.t.notAvailable }
        return Formatters.formatDate(value)
    }

    private func secondsValue(_ value: Int?) -> String {
        guard let value else { return localizer.t.notAvailable }
        return "\(value)s"
    }

    private func fetchDetail() async {
        state = .loading
        do {
            guard let identifier = detail.apiIdentifier,
                  let client = await servicesStore.healthchecksClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }

            let loadedDetail = try await client.getCheck(idOrKey: identifier)
            detail = loadedDetail

            let flipsTask = Task { () -> [HealthchecksFlip] in
                do {
                    return try await client.listFlips(checkIdOrKey: identifier)
                } catch {
                    return []
                }
            }

            let pingsTask = Task { () -> [HealthchecksPing] in
                guard let uuid = loadedDetail.uuid else { return [] }
                do {
                    return try await client.listPings(checkId: uuid)
                } catch {
                    return []
                }
            }

            flips = await flipsTask.value
            pings = await pingsTask.value
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func fetchPingBody(_ ping: HealthchecksPing) async {
        guard let uuid = detail.uuid else { return }
        do {
            guard let client = await servicesStore.healthchecksClient(instanceId: instanceId) else { return }
            let body = try await client.getPingBody(checkId: uuid, n: ping.n)
            bodyText = body
            showBody = true
        } catch {
            actionError = error.localizedDescription
            showActionError = true
        }
    }

    private func togglePause() async {
        guard let uuid = detail.uuid,
              let client = await servicesStore.healthchecksClient(instanceId: instanceId) else { return }
        do {
            if detail.isPaused {
                try await client.resumeCheck(id: uuid)
            } else {
                try await client.pauseCheck(id: uuid)
            }
            await fetchDetail()
        } catch {
            actionError = error.localizedDescription
            showActionError = true
        }
    }

    private func deleteCheck() async {
        guard let uuid = detail.uuid,
              let client = await servicesStore.healthchecksClient(instanceId: instanceId) else { return }
        do {
            try await client.deleteCheck(id: uuid)
            dismiss()
        } catch {
            actionError = error.localizedDescription
            showActionError = true
        }
    }
}

private struct HealthchecksPingRow: View {
    let ping: HealthchecksPing

    var body: some View {
        HStack(spacing: 12) {
            StatusBadge(status: pingStatus, compact: true)
            VStack(alignment: .leading, spacing: 2) {
                Text(Formatters.formatDate(ping.date))
                    .font(.subheadline.weight(.semibold))
                if !pingMeta.isEmpty {
                    Text(pingMeta)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }
            Spacer()
            if let duration = ping.duration {
                Text(String(format: "%.2fs", duration))
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textSecondary)
            }
            if ping.bodyUrl != nil {
                Image(systemName: "doc.text.magnifyingglass")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
        .padding(12)
        .glassCard(tint: AppTheme.surface.opacity(0.45))
    }

    private var pingStatus: String {
        switch ping.type.lowercased() {
        case "success": return "up"
        case "fail": return "down"
        default: return ping.type
        }
    }

    private var pingTint: Color {
        AppTheme.textSecondary
    }

    private var pingMeta: String {
        var parts: [String] = []
        if let method = ping.method { parts.append(method) }
        if let remote = ping.remoteAddr { parts.append(remote) }
        if parts.isEmpty, let agent = ping.userAgent { parts.append(agent) }
        return parts.joined(separator: " • ")
    }
}

private struct HealthchecksIntegrationsEditor: View {
    let instanceId: UUID
    let check: HealthchecksCheck
    let onComplete: () async -> Void

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    @State private var availableChannels: [HealthchecksChannel] = []
    @State private var selectedChannelIds: Set<String> = []
    @State private var customChannels: String = ""
    @State private var isLoading = false
    @State private var isSaving = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.caption)
                            .foregroundStyle(AppTheme.danger)
                            .padding(10)
                            .background(AppTheme.danger.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }

                    Text(localizer.t.healthchecksIntegrations)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    if isLoading {
                        ProgressView()
                            .frame(maxWidth: .infinity, alignment: .leading)
                    } else if availableChannels.isEmpty {
                        Text(localizer.t.noData)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                            .padding(12)
                            .glassCard(tint: AppTheme.surface.opacity(0.4))
                    } else {
                        VStack(spacing: 10) {
                            ForEach(availableChannels) { channel in
                                Toggle(isOn: Binding(
                                    get: { selectedChannelIds.contains(channel.id) },
                                    set: { newValue in
                                        if newValue {
                                            selectedChannelIds.insert(channel.id)
                                        } else {
                                            selectedChannelIds.remove(channel.id)
                                        }
                                    }
                                )) {
                                    HStack(spacing: 10) {
                                        Image(systemName: channel.iconName)
                                            .foregroundStyle(channel.accentColor)
                                            .frame(width: 24)
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(channel.name)
                                                .font(.subheadline.weight(.semibold))
                                            Text("\(channel.kind) • \(channel.id)")
                                                .font(.caption2)
                                                .foregroundStyle(AppTheme.textMuted)
                                                .lineLimit(1)
                                        }
                                    }
                                }
                                .toggleStyle(.switch)
                                .padding(10)
                                .background(AppTheme.surface.opacity(0.45), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizer.t.healthchecksFieldChannels)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                        TextField(localizer.t.healthchecksChannelsHint, text: $customChannels)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .padding(10)
                            .background(AppTheme.surface.opacity(0.45), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .padding(12)
                    .glassCard(tint: AppTheme.surface.opacity(0.4))
                }
                .padding(AppTheme.padding)
            }
            .background(AppTheme.background)
            .navigationTitle(localizer.t.healthchecksIntegrations)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) { Task { await save() } }
                        .disabled(isSaving)
                        .fontWeight(.semibold)
                }
            }
            .task { await loadChannels() }
        }
    }

    private func loadChannels() async {
        isLoading = true
        defer { isLoading = false }
        do {
            guard let client = await servicesStore.healthchecksClient(instanceId: instanceId) else {
                errorMessage = localizer.t.errorNotConfigured
                return
            }
            let loaded = try await client.listChannels()
            availableChannels = loaded.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            applyInitialSelection()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func applyInitialSelection() {
        let tokens = parseChannelTokens(check.channels ?? "")
        let availableIds = Set(availableChannels.map(\.id))
        selectedChannelIds = Set(tokens.filter { availableIds.contains($0) })
        let custom = tokens.filter { !availableIds.contains($0) }
        customChannels = custom.joined(separator: ", ")
    }

    private func save() async {
        guard let uuid = check.uuid else {
            errorMessage = localizer.t.healthchecksReadOnly
            return
        }
        isSaving = true
        defer { isSaving = false }

        let customTokens = parseChannelTokens(customChannels)
        let combined = Array(selectedChannelIds) + customTokens.filter { !selectedChannelIds.contains($0) }
        let channelsValue: String = combined.isEmpty ? "" : combined.joined(separator: ",")

        let payload = HealthchecksCheckPayload(
            name: nil,
            slug: nil,
            tags: nil,
            desc: nil,
            timeout: nil,
            grace: nil,
            schedule: nil,
            tz: nil,
            manualResume: nil,
            methods: nil,
            channels: channelsValue
        )

        do {
            guard let client = await servicesStore.healthchecksClient(instanceId: instanceId) else {
                errorMessage = localizer.t.errorNotConfigured
                return
            }
            try await client.updateCheck(id: uuid, payload: payload)
            await onComplete()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func parseChannelTokens(_ raw: String) -> [String] {
        raw.split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }
}

struct HealthchecksBadgesView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var state: LoadableState<Void> = .idle
    @State private var badges: [String: HealthchecksBadgeFormats] = [:]

    var body: some View {
        NavigationStack {
            ServiceDashboardLayout(
                serviceType: .healthchecks,
                instanceId: instanceId,
                state: state,
                onRefresh: fetchBadges
            ) {
                badgesList
            }
            .navigationTitle(localizer.t.healthchecksBadges)
            .navigationBarTitleDisplayMode(.inline)
            .task { await fetchBadges() }
        }
    }

    private var badgesList: some View {
        let keys = badges.keys.sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        return VStack(alignment: .leading, spacing: 16) {
            ForEach(keys, id: \.self) { key in
                let formats = badges[key]
                BadgeCard(tag: key, formats: formats)
            }
        }
    }

    private func fetchBadges() async {
        state = .loading
        do {
            guard let client = await servicesStore.healthchecksClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            let response = try await client.listBadges()
            badges = response.badges
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private struct BadgeCard: View {
        let tag: String
        let formats: HealthchecksBadgeFormats?

        @Environment(Localizer.self) private var localizer

        private var entries: [(String, String)] {
            guard let formats else { return [] }
            return [
                ("svg", formats.svg),
                ("svg3", formats.svg3),
                ("json", formats.json),
                ("json3", formats.json3),
                ("shields", formats.shields),
                ("shields3", formats.shields3)
            ].compactMap { key, value in
                guard let value else { return nil }
                return (key, value)
            }
        }

        var body: some View {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(tag == "*" ? localizer.t.healthchecksBadgeAll : tag)
                        .font(.subheadline.bold())
                    Spacer()
                }

                ForEach(entries, id: \.0) { entry in
                    HStack(spacing: 8) {
                        Text(entry.0)
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.textSecondary)
                            .frame(width: 60, alignment: .leading)
                        Text(entry.1)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(1)
                        Spacer()
                        Button {
                            UIPasteboard.general.string = entry.1
                        } label: {
                            Image(systemName: "doc.on.doc")
                                .font(.caption)
                        }
                    }
                    .padding(8)
                    .glassCard(cornerRadius: 8)
                }
            }
            .padding(14)
            .glassCard()
        }
    }
}

struct HealthchecksChannelsView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var state: LoadableState<Void> = .idle
    @State private var channels: [HealthchecksChannel] = []

    var body: some View {
        NavigationStack {
            ServiceDashboardLayout(
                serviceType: .healthchecks,
                instanceId: instanceId,
                state: state,
                onRefresh: fetchChannels
            ) {
                channelsList
            }
            .navigationTitle(localizer.t.healthchecksIntegrations)
            .navigationBarTitleDisplayMode(.inline)
            .task { await fetchChannels() }
        }
    }

    private var channelsList: some View {
        Group {
            if channels.isEmpty && !state.isLoading {
                Text(localizer.t.noData)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            } else {
                ForEach(channels) { channel in
                    HStack(spacing: 12) {
                        Image(systemName: channel.iconName)
                            .foregroundStyle(channel.accentColor)
                            .frame(width: 28, height: 28)
                            .background(AppTheme.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(channel.name)
                                .font(.subheadline.weight(.semibold))
                            Text("\(channel.kind) • \(channel.id)")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                                .lineLimit(1)
                        }
                        Spacer()
                        Button {
                            UIPasteboard.general.string = channel.id
                        } label: {
                            Image(systemName: "doc.on.doc")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }
                    .padding(12)
                    .glassCard(tint: AppTheme.surface.opacity(0.4))
                }
            }
        }
    }

    private func fetchChannels() async {
        state = .loading
        do {
            guard let client = await servicesStore.healthchecksClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            channels = try await client.listChannels()
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}

private extension HealthchecksChannel {
    var iconName: String {
        switch kind.lowercased() {
        case "email": return "envelope.fill"
        case "sms": return "message.fill"
        case "slack": return "bubble.left.and.bubble.right.fill"
        case "webhook": return "paperplane.fill"
        case "discord": return "bubble.left.fill"
        default: return "bell.fill"
        }
    }

    var accentColor: Color {
        switch kind.lowercased() {
        case "email": return AppTheme.info
        case "sms": return AppTheme.warning
        case "slack": return AppTheme.created
        case "webhook": return AppTheme.running
        case "discord": return AppTheme.created
        default: return AppTheme.info
        }
    }
}
