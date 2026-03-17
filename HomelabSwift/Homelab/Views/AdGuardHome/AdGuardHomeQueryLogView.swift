import SwiftUI

enum AdguardQueryStatusFilter: CaseIterable, Identifiable {
    case all
    case blocked
    case allowed

    var id: Self { self }
}

private let adguardAllClientFilter = "__all__"

struct AdGuardHomeQueryLogView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var entries: [AdGuardQueryLogEntry] = []
    @State private var searchText = ""
    @State private var statusFilter: AdguardQueryStatusFilter
    @State private var clientFilter = adguardAllClientFilter
    @State private var state: LoadableState<Void> = .idle
    @State private var allowError: String?
    @State private var showAllowError = false
    @State private var serverFilter: AdguardQueryStatusFilter?

    private var availableClients: [String] {
        let clients = Set(entries.map(\.client).filter { !$0.isEmpty })
        return [adguardAllClientFilter] + clients.sorted()
    }

    private var filteredEntries: [AdGuardQueryLogEntry] {
        entries.filter { entry in
            let matchesStatus: Bool = {
                switch statusFilter {
                case .all:
                    return true
                case .blocked:
                    if serverFilter == .blocked { return true }
                    return entry.isBlocked
                case .allowed:
                    return !entry.isBlocked
                }
            }()
            let matchesClient = clientFilter == adguardAllClientFilter || entry.client == clientFilter
            let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let matchesSearch = query.isEmpty || entry.domain.lowercased().contains(query) || entry.client.lowercased().contains(query)
            return matchesStatus && matchesClient && matchesSearch
        }
    }

    init(instanceId: UUID, initialStatusFilter: AdguardQueryStatusFilter = .all) {
        self.instanceId = instanceId
        _statusFilter = State(initialValue: initialStatusFilter)
    }

    var body: some View {
        Group {
            switch state {
            case .idle, .loading:
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            case .offline:
                ServiceErrorView(error: .networkError(NSError(domain: "Network", code: -1009))) {
                    await loadLog(showLoading: true)
                }
            case .error(let apiError):
                ServiceErrorView(error: apiError) {
                    await loadLog(showLoading: true)
                }
            case .loaded:
                VStack(spacing: 12) {
                    VStack(spacing: 12) {
                        ViewThatFits(in: .horizontal) {
                            Picker(localizer.t.adguardQueryLog, selection: $statusFilter) {
                                ForEach(AdguardQueryStatusFilter.allCases) { filter in
                                    switch filter {
                                    case .all:
                                        Text(localizer.t.adguardFilterAll).tag(filter)
                                    case .blocked:
                                        Text(localizer.t.adguardFilterBlocked).tag(filter)
                                    case .allowed:
                                        Text(localizer.t.adguardFilterAllowed).tag(filter)
                                    }
                                }
                            }
                            .pickerStyle(.segmented)

                            Picker(localizer.t.adguardQueryLog, selection: $statusFilter) {
                                ForEach(AdguardQueryStatusFilter.allCases) { filter in
                                    switch filter {
                                    case .all:
                                        Text(localizer.t.adguardFilterAll).tag(filter)
                                    case .blocked:
                                        Text(localizer.t.adguardFilterBlocked).tag(filter)
                                    case .allowed:
                                        Text(localizer.t.adguardFilterAllowed).tag(filter)
                                    }
                                }
                            }
                            .pickerStyle(.menu)
                        }

                        HStack {
                            Menu {
                                ForEach(availableClients, id: \.self) { client in
                                    Button(client == adguardAllClientFilter ? localizer.t.adguardFilterAll : client) {
                                        clientFilter = client
                                    }
                                }
                            } label: {
                                Label(String(format: localizer.t.adguardFilterClient, clientFilter == adguardAllClientFilter ? localizer.t.adguardFilterAll : clientFilter), systemImage: "desktopcomputer")
                                    .font(.subheadline.weight(.medium))
                            }
                            .buttonStyle(.bordered)

                            Spacer()

                            Text("\(filteredEntries.count)")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                    .padding(.horizontal)

                    if filteredEntries.isEmpty {
                        ContentUnavailableView(localizer.t.adguardNoQueryResults, systemImage: "tray")
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        List(filteredEntries) { entry in
                            HStack(alignment: .top, spacing: 12) {
                                Circle()
                                    .fill(entry.isBlocked ? AppTheme.stopped : AppTheme.running)
                                    .frame(width: 8, height: 8)
                                    .padding(.top, 6)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(entry.domain)
                                        .font(.body.weight(.medium))
                                        .lineLimit(1)
                                    Text(entry.client)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textSecondary)
                                        .lineLimit(1)
                                }

                                Spacer()

                                VStack(alignment: .trailing, spacing: 2) {
                                    Text(entry.isBlocked ? localizer.t.adguardFilterBlocked : localizer.t.adguardFilterAllowed)
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(entry.isBlocked ? AppTheme.stopped : AppTheme.running)
                                    Text(Formatters.formatDate(entry.time))
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                }
                            }
                            .padding(.vertical, 6)
                            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                            .listRowBackground(Color.clear)
                            .swipeActions(edge: .leading, allowsFullSwipe: false) {
                                if entry.isBlocked {
                                    Button {
                                        Task { await allowDomain(entry.domain) }
                                    } label: {
                                        Label(localizer.t.adguardAllow, systemImage: "checkmark.seal.fill")
                                    }
                                    .tint(AppTheme.running)
                                }
                            }
                        }
                        .listStyle(.plain)
                        .scrollContentBackground(.hidden)
                    }
                }
            }
        }
        .navigationTitle(localizer.t.adguardQueryLog)
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchText, prompt: localizer.t.adguardFilterSearch)
        .task { await loadLog(showLoading: true) }
        .refreshable { await loadLog(showLoading: false) }
        .onChange(of: statusFilter) { _, _ in
            Task { await loadLog(showLoading: false) }
        }
        .alert(localizer.t.error, isPresented: $showAllowError) {
            Button(localizer.t.confirm, role: .cancel) { }
        } message: {
            Text(allowError ?? localizer.t.errorUnknown)
        }
    }

    private func loadLog(showLoading: Bool) async {
        if showLoading { state = .loading }
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let responseStatus: String? = statusFilter == .blocked ? "blocked" : nil
            var page = try await client.getQueryLog(limit: 200, responseStatus: responseStatus)
            if statusFilter == .blocked, page.items.isEmpty {
                page = try await client.getQueryLog(limit: 200, responseStatus: "filtered")
            }
            if statusFilter == .blocked, page.items.isEmpty {
                page = try await client.getQueryLog(limit: 200)
            }
            entries = page.items
            serverFilter = responseStatus == nil ? nil : statusFilter
            state = .loaded(())
        } catch let apiError as APIError {
            if entries.isEmpty { state = .error(apiError) }
        } catch {
            if entries.isEmpty { state = .error(.custom(error.localizedDescription)) }
        }
    }

    private func allowDomain(_ domain: String) async {
        let clean = domain.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let status = try await client.getFilteringStatus()
            let rule = "@@||\(clean)^"
            if !status.userRules.contains(rule) {
                try await client.setUserRules(status.userRules + [rule])
            }
            await loadLog(showLoading: false)
        } catch {
            allowError = error.localizedDescription
            showAllowError = true
        }
    }
}
