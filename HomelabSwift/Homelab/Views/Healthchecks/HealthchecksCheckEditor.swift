import SwiftUI

struct HealthchecksCheckEditor: View {
    let instanceId: UUID
    let existing: HealthchecksCheck?
    let onComplete: () async -> Void

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var slug: String = ""
    @State private var tags: String = ""
    @State private var desc: String = ""
    @State private var timeout: String = ""
    @State private var schedule: String = ""
    @State private var timezone: String = ""
    @State private var grace: String = ""
    @State private var manualResume: Bool = false
    @State private var postOnly: Bool = false
    @State private var checkType: HealthchecksCheckType = .simple
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var availableChannels: [HealthchecksChannel] = []
    @State private var selectedChannelIds: Set<String> = []
    @State private var customChannels: String = ""
    @State private var isLoadingChannels = false
    @State private var initialChannelsRaw: String = ""
    private let smoothAnimation = Animation.spring(response: 0.45, dampingFraction: 0.86)

    init(instanceId: UUID, existing: HealthchecksCheck? = nil, onComplete: @escaping () async -> Void) {
        self.instanceId = instanceId
        self.existing = existing
        self.onComplete = onComplete
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.caption)
                            .foregroundStyle(AppTheme.danger)
                            .padding(10)
                            .background(AppTheme.danger.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }

                    sectionCard(title: localizer.t.healthchecksBasics, icon: "list.bullet.rectangle", color: AppTheme.info) {
                        fieldRow(title: localizer.t.healthchecksFieldName, text: $name, placeholder: localizer.t.healthchecksFieldName, icon: "textformat")
                        Divider().opacity(0.4)
                        fieldRow(title: localizer.t.healthchecksFieldSlug, text: $slug, placeholder: localizer.t.healthchecksSlugHint, icon: "link")
                        Divider().opacity(0.4)
                        fieldRow(title: localizer.t.healthchecksFieldTags, text: $tags, placeholder: localizer.t.healthchecksTagsHint, icon: "tag.fill")
                        Divider().opacity(0.4)
                        textEditorRow(title: localizer.t.healthchecksFieldDesc, text: $desc, placeholder: localizer.t.healthchecksFieldDesc, icon: "text.alignleft")
                    }

                    sectionCard(title: localizer.t.healthchecksSchedule, icon: "calendar.badge.clock", color: AppTheme.warning) {
                        sectionLabel(title: localizer.t.healthchecksFieldType, icon: "switch.2", tint: AppTheme.created)
                        Picker(localizer.t.healthchecksFieldType, selection: $checkType) {
                            Text(localizer.t.healthchecksTypeSimple).tag(HealthchecksCheckType.simple)
                            Text(localizer.t.healthchecksTypeCron).tag(HealthchecksCheckType.cron)
                        }
                        .pickerStyle(.segmented)
                        .padding(6)
                        .background(inputBackground(cornerRadius: 14))

                        if checkType == .simple {
                            fieldRow(title: localizer.t.healthchecksFieldTimeout, text: $timeout, placeholder: localizer.t.healthchecksTimeoutHint, icon: "timer", tint: AppTheme.warning, keyboardType: .numberPad)
                        } else {
                            fieldRow(title: localizer.t.healthchecksFieldSchedule, text: $schedule, placeholder: localizer.t.healthchecksScheduleHint, icon: "calendar", tint: AppTheme.created)
                            Divider().opacity(0.4)
                            fieldRow(title: localizer.t.healthchecksFieldTimezone, text: $timezone, placeholder: localizer.t.healthchecksTimezoneHint, icon: "globe.europe.africa.fill", tint: AppTheme.info)
                        }

                        Divider().opacity(0.4)
                        fieldRow(title: localizer.t.healthchecksFieldGrace, text: $grace, placeholder: localizer.t.healthchecksGraceHint, icon: "hourglass", tint: AppTheme.warning, keyboardType: .numberPad)
                    }

                    sectionCard(title: localizer.t.healthchecksIntegrations, icon: "bolt.horizontal.circle.fill", color: AppTheme.running) {
                        if isLoadingChannels {
                            ProgressView()
                                .frame(maxWidth: .infinity, alignment: .leading)
                        } else if availableChannels.isEmpty {
                            Text(localizer.t.noData)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                        } else {
                            VStack(spacing: 10) {
                                ForEach(availableChannels) { channel in
                                    integrationsToggleRow(channel: channel)
                                }
                            }
                        }

                        Divider().opacity(0.4)
                        fieldRow(title: localizer.t.healthchecksFieldChannels, text: $customChannels, placeholder: localizer.t.healthchecksChannelsHint, icon: "dot.radiowaves.left.and.right")
                    }

                    sectionCard(title: localizer.t.healthchecksAdvanced, icon: "gearshape.fill", color: AppTheme.paused) {
                        toggleRow(title: localizer.t.healthchecksManualResume, icon: "hand.raised.fill", tint: AppTheme.paused, isOn: $manualResume)
                        Divider().opacity(0.4)
                        toggleRow(title: localizer.t.healthchecksMethodsPostOnly, icon: "paperplane.fill", tint: AppTheme.info, isOn: $postOnly)
                    }
                }
                .padding(AppTheme.padding)
            }
            .animation(smoothAnimation, value: checkType)
            .animation(smoothAnimation, value: selectedChannelIds)
            .background(AppTheme.background)
            .navigationTitle(existing == nil ? localizer.t.healthchecksCreateCheck : localizer.t.healthchecksEditCheck)
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
            .onAppear {
                prefill()
                Task { await loadChannels() }
            }
        }
    }

    private func prefill() {
        guard let existing else { return }
        name = existing.name
        slug = existing.slug ?? ""
        tags = existing.tags ?? ""
        desc = existing.desc ?? ""
        timeout = existing.timeout.map(String.init) ?? ""
        schedule = existing.schedule ?? ""
        timezone = existing.tz ?? ""
        grace = existing.grace.map(String.init) ?? ""
        initialChannelsRaw = existing.channels ?? ""
        customChannels = initialChannelsRaw
        manualResume = existing.manualResume ?? false
        postOnly = existing.methods == "POST"
        checkType = existing.schedule == nil || existing.schedule?.isEmpty == true ? .simple : .cron
    }

    private func save() async {
        errorMessage = nil
        let trimmedName = trimmedOrNil(name)
        if trimmedName == nil {
            errorMessage = localizer.t.healthchecksNameRequired
            return
        }
        if checkType == .cron, schedule.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            errorMessage = localizer.t.healthchecksScheduleRequired
            return
        }
        if checkType == .simple, Int(timeout) == nil {
            errorMessage = localizer.t.healthchecksTimeoutRequired
            return
        }
        if existing != nil, existing?.hasUUID != true {
            errorMessage = localizer.t.healthchecksReadOnly
            return
        }

        isSaving = true
        defer { isSaving = false }

        let methodsValue: String? = postOnly ? "POST" : nil
        let scheduleValue: String? = checkType == .cron ? trimmedOrNil(schedule) : nil
        let timezoneValue: String? = checkType == .cron ? trimmedOrNil(timezone) : nil
        let channelsValue: String? = {
            let customTokens = parseChannelTokens(customChannels)
            let combined = Array(selectedChannelIds) + customTokens.filter { !selectedChannelIds.contains($0) }
            if combined.isEmpty { return nil }
            return combined.joined(separator: ",")
        }()

        let payload = HealthchecksCheckPayload(
            name: trimmedName,
            slug: trimmedOrNil(slug),
            tags: trimmedOrNil(tags),
            desc: trimmedOrNil(desc),
            timeout: checkType == .simple ? Int(timeout) : nil,
            grace: Int(grace),
            schedule: scheduleValue,
            tz: timezoneValue,
            manualResume: manualResume,
            methods: methodsValue,
            channels: channelsValue
        )

        do {
            guard let client = await servicesStore.healthchecksClient(instanceId: instanceId) else {
                errorMessage = localizer.t.errorNotConfigured
                return
            }
            if let existing, let uuid = existing.uuid {
                try await client.updateCheck(id: uuid, payload: payload)
            } else {
                try await client.createCheck(payload)
            }
            await onComplete()
            dismiss()
        } catch let apiError as APIError {
            errorMessage = apiError.localizedDescription
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func loadChannels() async {
        guard availableChannels.isEmpty else {
            applyChannelSelection(from: initialChannelsRaw)
            return
        }
        isLoadingChannels = true
        defer { isLoadingChannels = false }
        do {
            guard let client = await servicesStore.healthchecksClient(instanceId: instanceId) else { return }
            let loaded = try await client.listChannels()
            availableChannels = loaded.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
            applyChannelSelection(from: initialChannelsRaw)
        } catch {
            availableChannels = []
        }
    }

    private func applyChannelSelection(from raw: String) {
        let tokens = parseChannelTokens(raw)
        let availableIds = Set(availableChannels.map(\.id))
        selectedChannelIds = Set(tokens.filter { availableIds.contains($0) })
        let custom = tokens.filter { !availableIds.contains($0) }
        customChannels = custom.joined(separator: ", ")
    }

    private func parseChannelTokens(_ raw: String) -> [String] {
        raw.split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private func sectionCard(title: String, icon: String, color: Color, @ViewBuilder content: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                Image(systemName: icon)
                    .font(.caption.bold())
                    .foregroundStyle(color)
                    .frame(width: 26, height: 26)
                    .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                Text(title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
            }
            content()
        }
        .padding(16)
        .glassCard(tint: AppTheme.surface.opacity(0.45))
    }

    private func fieldRow(
        title: String,
        text: Binding<String>,
        placeholder: String,
        icon: String,
        tint: Color = AppTheme.textSecondary,
        keyboardType: UIKeyboardType = .default
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.caption)
                    .foregroundStyle(tint)
                Text(title)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }
            TextField(placeholder, text: text)
                .keyboardType(keyboardType)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .padding(10)
                .background(inputBackground())
        }
    }

    private func textEditorRow(title: String, text: Binding<String>, placeholder: String, icon: String, tint: Color = AppTheme.textSecondary) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.caption)
                    .foregroundStyle(tint)
                Text(title)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }
            ZStack(alignment: .topLeading) {
                if text.wrappedValue.isEmpty {
                    Text(placeholder)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .padding(.top, 12)
                        .padding(.leading, 14)
                }
                TextEditor(text: text)
                    .frame(minHeight: 96)
                    .scrollContentBackground(.hidden)
                    .padding(8)
            }
            .background(inputBackground())
        }
    }

    private func toggleRow(title: String, icon: String, tint: Color = AppTheme.textSecondary, isOn: Binding<Bool>) -> some View {
        Toggle(isOn: isOn) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.caption)
                    .foregroundStyle(tint)
                Text(title)
                    .font(.subheadline)
            }
        }
            .padding(10)
            .background(inputBackground())
    }

    private func integrationsToggleRow(channel: HealthchecksChannel) -> some View {
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
                    .frame(width: 24, height: 24)
                    .background(channel.accentColor.opacity(0.14), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
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
        .background(inputBackground())
    }

    private func sectionLabel(title: String, icon: String, tint: Color) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(tint)
            Text(title)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
        }
    }

    private func inputBackground(cornerRadius: CGFloat = 12) -> some View {
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            .fill(AppTheme.surface.opacity(0.55))
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(Color.white.opacity(0.06), lineWidth: 1)
            )
    }

    private func trimmedOrNil(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

}

private enum HealthchecksCheckType: String, CaseIterable {
    case simple
    case cron
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
