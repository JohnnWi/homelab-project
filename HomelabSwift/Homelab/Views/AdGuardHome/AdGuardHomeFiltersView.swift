import SwiftUI

struct AdGuardHomeFiltersView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var status: AdGuardFilteringStatus?
    @State private var isLoading = true
    @State private var error: Error?
    @State private var updatingIds: Set<Int> = []
    @State private var isPresentingAdd = false
    @State private var isPresentingEdit = false
    @State private var pendingRemoval: AdGuardFilter?
    @State private var pendingRemovalWhitelist = false
    @State private var newFilterName = ""
    @State private var newFilterURL = ""
    @State private var newFilterKind: FilterListKind = .blocklist
    @State private var isSubmitting = false
    @State private var editingFilter: AdGuardFilter?
    @State private var editingWhitelist = false
    @State private var editFilterName = ""
    @State private var editFilterURL = ""
    @State private var editFilterEnabled = true

    private struct FilterPreset: Identifiable, Hashable {
        let id = UUID()
        let name: String
        let url: String
    }

    private enum FilterListKind: String, CaseIterable, Identifiable {
        case blocklist
        case allowlist

        var id: Self { self }
    }

    private let presetFilters: [FilterPreset] = [
        .init(name: "AdGuard DNS Filter", url: "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"),
        .init(name: "AdAway", url: "https://adaway.org/hosts.txt"),
        .init(name: "StevenBlack Hosts", url: "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
        .init(name: "OISD", url: "https://big.oisd.nl/"),
        .init(name: "EasyList", url: "https://easylist.to/easylist/easylist.txt"),
        .init(name: "EasyPrivacy", url: "https://easylist.to/easylist/easyprivacy.txt"),
        .init(name: "Peter Lowe", url: "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"),
        .init(name: "MalwareDomains", url: "https://mirror1.malwaredomains.com/files/justdomains"),
        .init(name: "HaGeZi Multi PRO", url: "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.txt"),
        .init(name: "HaGeZi Light", url: "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/light.txt"),
        .init(name: "1Hosts (Lite)", url: "https://badmojr.github.io/1Hosts/Lite/hosts.txt"),
        .init(name: "Dan Pollock", url: "https://someonewhocares.org/hosts/hosts"),
        .init(name: "NoCoin", url: "https://raw.githubusercontent.com/hoshsadiq/adblock-nocoin-list/master/hosts.txt"),
        .init(name: "Phishing Army", url: "https://phishing.army/download/phishing_army_blocklist.txt")
    ]

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.largeTitle)
                        .foregroundStyle(AppTheme.stopped)
                        .accessibilityHidden(true)
                    Text(error.localizedDescription)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                    Button(localizer.t.retry) {
                        Task { await fetchStatus() }
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
            } else {
                ScrollView {
                    LazyVStack(spacing: AppTheme.gridSpacing) {
                        if let status {
                            summarySection(status)
                            filterSection(title: localizer.t.adguardBlocklists, filters: status.filters, isWhitelist: false)
                            filterSection(title: localizer.t.adguardAllowlists, filters: status.whitelistFilters, isWhitelist: true)
                        }
                    }
                    .padding(AppTheme.padding)
                }
                .refreshable { await fetchStatus() }
            }
        }
        .navigationTitle(localizer.t.adguardFilters)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    newFilterName = ""
                    newFilterURL = ""
                    newFilterKind = .blocklist
                    isPresentingAdd = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel(localizer.t.adguardAddFilterList)
            }
        }
        .sheet(isPresented: $isPresentingAdd) {
            addFilterSheet
        }
        .sheet(isPresented: $isPresentingEdit) {
            editFilterSheet
        }
        .alert(isPresented: Binding(get: { pendingRemoval != nil }, set: { if !$0 { pendingRemoval = nil } })) {
            Alert(
                title: Text(localizer.t.delete),
                message: Text(pendingRemoval?.name ?? ""),
                primaryButton: .destructive(Text(localizer.t.delete)) {
                    Task { await removePendingFilter() }
                },
                secondaryButton: .cancel(Text(localizer.t.cancel))
            )
        }
        .task { await fetchStatus() }
    }

    private func summarySection(_ status: AdGuardFilteringStatus) -> some View {
        let enabledCount = status.filters.filter { $0.enabled }.count
        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.adguardFilters.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: twoColumnGrid, spacing: 10) {
                summaryCard(icon: "line.3.horizontal.decrease.circle", iconBg: AppTheme.info, value: "\(status.filters.count)", label: localizer.t.adguardBlocklists)
                summaryCard(icon: "checkmark.seal.fill", iconBg: AppTheme.running, value: "\(enabledCount)", label: localizer.t.adguardFiltersEnabled)
                summaryCard(icon: "checkmark.circle", iconBg: AppTheme.accent, value: "\(status.whitelistFilters.count)", label: localizer.t.adguardAllowlists)
                summaryCard(icon: "list.bullet", iconBg: AppTheme.warning, value: "\(status.userRules.count)", label: localizer.t.adguardUserRules)
            }
        }
    }

    private func summaryCard(icon: String, iconBg: Color, value: String, label: String) -> some View {
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

    private func filterSection(title: String, filters: [AdGuardFilter], isWhitelist: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            if filters.isEmpty {
                Text(localizer.t.noData)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textMuted)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .glassCard()
            } else {
                VStack(spacing: 10) {
                    ForEach(filters) { filter in
                        filterRow(filter, isWhitelist: isWhitelist)
                    }
                }
            }
        }
    }

    private func filterRow(_ filter: AdGuardFilter, isWhitelist: Bool) -> some View {
        let isUpdating = updatingIds.contains(filter.id)
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(filter.name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                if !filter.url.isEmpty {
                    Text(filter.url)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
            .onTapGesture {
                guard !isUpdating else { return }
                beginEdit(filter, isWhitelist: isWhitelist)
            }

            VStack(alignment: .trailing, spacing: 6) {
                Text(filter.enabled ? localizer.t.adguardEnabled : localizer.t.adguardDisabled)
                    .font(.caption.bold())
                    .foregroundStyle(filter.enabled ? AppTheme.running : AppTheme.stopped)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(
                        (filter.enabled ? AppTheme.running : AppTheme.stopped).opacity(0.1),
                        in: Capsule()
                    )
                HStack(spacing: 8) {
                    if isUpdating {
                        ProgressView()
                            .scaleEffect(0.8)
                    } else {
                        Toggle("", isOn: Binding(
                            get: { filter.enabled },
                            set: { newValue in
                                Task { await updateFilterEnabled(filter, enabled: newValue, isWhitelist: isWhitelist) }
                            }
                        ))
                        .labelsHidden()
                        .toggleStyle(SwitchToggleStyle(tint: AppTheme.running))
                    }
                }
                Text("\(Formatters.formatNumber(filter.rulesCount)) \(localizer.t.adguardRules)")
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
        .padding(12)
        .glassCard()
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                pendingRemoval = filter
                pendingRemovalWhitelist = isWhitelist
            } label: {
                Label(localizer.t.delete, systemImage: "trash")
            }
        }
    }

    private func fetchStatus() async {
        isLoading = true
        error = nil
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            status = try await client.getFilteringStatus()
        } catch {
            self.error = error
        }
        isLoading = false
    }

    private func updateFilterEnabled(_ filter: AdGuardFilter, enabled: Bool, isWhitelist: Bool) async {
        guard let status else { return }
        updatingIds.insert(filter.id)
        let updated = AdGuardFilter(
            id: filter.id,
            name: filter.name,
            url: filter.url,
            enabled: enabled,
            rulesCount: filter.rulesCount,
            lastUpdated: filter.lastUpdated
        )
        if isWhitelist {
            let updatedList = status.whitelistFilters.map { $0.id == filter.id ? updated : $0 }
            self.status = AdGuardFilteringStatus(userRules: status.userRules, filters: status.filters, whitelistFilters: updatedList)
        } else {
            let updatedList = status.filters.map { $0.id == filter.id ? updated : $0 }
            self.status = AdGuardFilteringStatus(userRules: status.userRules, filters: updatedList, whitelistFilters: status.whitelistFilters)
        }
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            try await client.setFilter(updated, enabled: enabled, whitelist: isWhitelist)
        } catch {
            self.error = error
            await fetchStatus()
        }
        updatingIds.remove(filter.id)
    }

    private func removePendingFilter() async {
        guard let filter = pendingRemoval else { return }
        pendingRemoval = nil
        updatingIds.insert(filter.id)
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            try await client.removeFilter(url: filter.url, whitelist: pendingRemovalWhitelist)
            await fetchStatus()
        } catch {
            self.error = error
        }
        updatingIds.remove(filter.id)
    }

    private func addFilter() async {
        let name = newFilterName.trimmingCharacters(in: .whitespacesAndNewlines)
        let url = newFilterURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, !url.isEmpty else { return }
        isSubmitting = true
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            try await client.addFilter(name: name, url: url, whitelist: newFilterKind == .allowlist)
            isPresentingAdd = false
            await fetchStatus()
        } catch {
            self.error = error
        }
        isSubmitting = false
    }

    private func beginEdit(_ filter: AdGuardFilter, isWhitelist: Bool) {
        editingFilter = filter
        editingWhitelist = isWhitelist
        editFilterName = filter.name
        editFilterURL = filter.url
        editFilterEnabled = filter.enabled
        isPresentingEdit = true
    }

    private func saveEdit() async {
        guard let original = editingFilter else { return }
        let newName = editFilterName.trimmingCharacters(in: .whitespacesAndNewlines)
        let newURL = editFilterURL.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !newName.isEmpty, !newURL.isEmpty else { return }
        isSubmitting = true
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            if newURL == original.url {
                let updated = AdGuardFilter(
                    id: original.id,
                    name: newName,
                    url: original.url,
                    enabled: editFilterEnabled,
                    rulesCount: original.rulesCount,
                    lastUpdated: original.lastUpdated
                )
                try await client.setFilter(updated, enabled: editFilterEnabled, whitelist: editingWhitelist)
            } else {
                try await client.removeFilter(url: original.url, whitelist: editingWhitelist)
                try await client.addFilter(name: newName, url: newURL, whitelist: editingWhitelist, enabled: editFilterEnabled)
            }
            isPresentingEdit = false
            await fetchStatus()
        } catch {
            self.error = error
        }
        isSubmitting = false
    }

    private var addFilterSheet: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizer.t.adguardListType)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                        Picker(localizer.t.adguardListType, selection: $newFilterKind) {
                            Text(localizer.t.adguardBlocklists).tag(FilterListKind.blocklist)
                            Text(localizer.t.adguardAllowlists).tag(FilterListKind.allowlist)
                        }
                        .pickerStyle(.segmented)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizer.t.adguardCustomList)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                        VStack(alignment: .leading, spacing: 10) {
                            Text(localizer.t.adguardCustomListHint)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                            TextField(localizer.t.adguardListName, text: $newFilterName)
                                .textInputAutocapitalization(.words)
                                .disableAutocorrection(true)
                                .padding(12)
                                .glassCard()
                            TextField(localizer.t.adguardListUrl, text: $newFilterURL)
                                .keyboardType(.URL)
                                .textInputAutocapitalization(.never)
                                .disableAutocorrection(true)
                                .padding(12)
                                .glassCard()
                        }
                        .padding(12)
                        .glassCard()
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizer.t.adguardPresetLists)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                        if newFilterKind == .blocklist {
                            LazyVGrid(columns: twoColumnGrid, spacing: 10) {
                                ForEach(presetFilters) { preset in
                                    Button {
                                        newFilterName = preset.name
                                        newFilterURL = preset.url
                                        newFilterKind = .blocklist
                                    } label: {
                                        VStack(alignment: .leading, spacing: 6) {
                                            HStack {
                                                Text(preset.name)
                                                    .font(.subheadline.weight(.semibold))
                                                    .foregroundStyle(.primary)
                                                    .lineLimit(2)
                                                Spacer()
                                                Image(systemName: "plus.circle.fill")
                                                    .foregroundStyle(AppTheme.accent)
                                            }
                                            Text(preset.url)
                                                .font(.caption2)
                                                .foregroundStyle(AppTheme.textMuted)
                                                .lineLimit(2)
                                        }
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .frame(minHeight: 96, alignment: .topLeading)
                                        .padding(12)
                                        .glassCard()
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        } else {
                            VStack(alignment: .leading, spacing: 6) {
                                Text(localizer.t.noData)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.primary)
                                Text(localizer.t.adguardAllowlistHint)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .glassCard()
                        }
                    }
                }
                .padding(AppTheme.padding)
            }
            .navigationTitle(localizer.t.adguardAddFilterList)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(localizer.t.cancel) { isPresentingAdd = false }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(localizer.t.save) {
                        Task { await addFilter() }
                    }
                    .disabled(isSubmitting || newFilterName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || newFilterURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }

    private var editFilterSheet: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizer.t.adguardListName)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                        TextField(localizer.t.adguardListName, text: $editFilterName)
                            .textInputAutocapitalization(.words)
                            .disableAutocorrection(true)
                            .padding(12)
                            .glassCard()
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text(localizer.t.adguardListUrl)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                        TextField(localizer.t.adguardListUrl, text: $editFilterURL)
                            .keyboardType(.URL)
                            .textInputAutocapitalization(.never)
                            .disableAutocorrection(true)
                            .padding(12)
                            .glassCard()
                    }

                    Toggle(localizer.t.adguardFiltersEnabled, isOn: $editFilterEnabled)
                        .toggleStyle(SwitchToggleStyle(tint: AppTheme.running))
                        .padding(12)
                        .glassCard()
                }
                .padding(AppTheme.padding)
            }
            .navigationTitle(localizer.t.adguardFilters)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(localizer.t.cancel) { isPresentingEdit = false }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(localizer.t.save) {
                        Task { await saveEdit() }
                    }
                    .disabled(isSubmitting || editFilterName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || editFilterURL.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
