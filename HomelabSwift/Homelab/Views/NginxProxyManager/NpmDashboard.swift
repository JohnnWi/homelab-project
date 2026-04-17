import SwiftUI

// MARK: - Tab Enum

enum NpmTab: String, CaseIterable, Identifiable {
    case overview, proxyHosts, redirections, streams, deadHosts, accessLists, ssl, users, auditLogs

    var id: String { rawValue }

    func title(t: Translations) -> String {
        switch self {
        case .overview:     return t.npmOverview
        case .proxyHosts:   return t.npmProxyHosts
        case .redirections: return t.npmRedirections
        case .streams:      return t.npmStreams
        case .deadHosts:    return t.npm404Hosts
        case .accessLists:  return t.npmAccessList
        case .ssl:          return t.npmSslCertificates
        case .users:        return t.npmUsers
        case .auditLogs:    return t.npmAuditLogs
        }
    }

    var icon: String {
        switch self {
        case .overview:     return "chart.bar.fill"
        case .proxyHosts:   return "server.rack"
        case .redirections: return "arrow.triangle.turn.up.right.diamond.fill"
        case .streams:      return "arrow.left.arrow.right"
        case .deadHosts:    return "exclamationmark.triangle.fill"
        case .accessLists:  return "person.2.fill"
        case .ssl:          return "lock.shield.fill"
        case .users:        return "person.crop.circle.fill"
        case .auditLogs:    return "doc.text.magnifyingglass"
        }
    }
}

// MARK: - Dashboard

struct NpmDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var selectedInstanceId: UUID
    @State private var hostReport: NpmHostReport?
    @State private var proxyHosts: [NpmProxyHost] = []
    @State private var redirectionHosts: [NpmRedirectionHost] = []
    @State private var streams: [NpmStream] = []
    @State private var deadHosts: [NpmDeadHost] = []
    @State private var certificates: [NpmCertificate] = []
    @State private var accessLists: [NpmAccessList] = []
    @State private var users: [NpmUser] = []
    @State private var auditLogs: [NpmAuditLog] = []
    @State private var state: LoadableState<Void> = .idle
    @State private var hasRestoredCache = false

    // CRUD sheet state
    @State private var showingProxyForm = false
    @State private var editingProxyHost: NpmProxyHost?
    @State private var showingRedirectionForm = false
    @State private var editingRedirectionHost: NpmRedirectionHost?
    @State private var showingStreamForm = false
    @State private var editingStream: NpmStream?
    @State private var showingDeadHostForm = false
    @State private var editingDeadHost: NpmDeadHost?
    @State private var showingCertificateForm = false
    @State private var showingAccessListForm = false
    @State private var editingAccessList: NpmAccessList?
    @State private var showingUserForm = false
    @State private var editingUser: NpmUser?

    // Delete confirmation
    @State private var showingDeleteConfirm = false
    @State private var pendingDeleteAction: (() async -> Void)?

    // Toast
    @State private var actionMessage: String?

    private let npmColor = Color(hex: "#F15B2A")
    private static let cacheTtl: TimeInterval = 120

    private struct CacheEntry {
        let hostReport: NpmHostReport?
        let proxyHosts: [NpmProxyHost]
        let redirectionHosts: [NpmRedirectionHost]
        let streams: [NpmStream]
        let deadHosts: [NpmDeadHost]
        let certificates: [NpmCertificate]
        let accessLists: [NpmAccessList]
        let users: [NpmUser]
        let auditLogs: [NpmAuditLog]
        let lastFetch: Date
    }

    private static var cache: [UUID: CacheEntry] = [:]

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .nginxProxyManager,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(force: true) }
        ) {
            instancePicker
            menuHero
            menuContent
        }
        .navigationTitle(localizer.t.serviceNpm)
        .navigationDestination(for: NpmTab.self) { tab in
            sectionScreen(for: tab)
        }
        .task(id: selectedInstanceId) { await loadAndRefreshIfNeeded() }
        .sheet(isPresented: $showingProxyForm, onDismiss: { editingProxyHost = nil }) {
            NpmProxyHostForm(
                editing: editingProxyHost,
                certificates: certificates,
                accessLists: accessLists
            ) { request in
                if let existing = editingProxyHost {
                    try await updateProxyHost(id: existing.id, request)
                } else {
                    try await createProxyHost(request)
                }
            }
            .id(editingProxyHost?.id)
        }
        .sheet(isPresented: $showingRedirectionForm, onDismiss: { editingRedirectionHost = nil }) {
            NpmRedirectionHostForm(
                editing: editingRedirectionHost,
                certificates: certificates,
                accessLists: accessLists
            ) { request in
                if let existing = editingRedirectionHost {
                    try await updateRedirectionHost(id: existing.id, request)
                } else {
                    try await createRedirectionHost(request)
                }
            }
            .id(editingRedirectionHost?.id)
        }
        .sheet(isPresented: $showingStreamForm, onDismiss: { editingStream = nil }) {
            NpmStreamForm(editing: editingStream) { request in
                if let existing = editingStream {
                    try await updateStream(id: existing.id, request)
                } else {
                    try await createStream(request)
                }
            }
            .id(editingStream?.id)
        }
        .sheet(isPresented: $showingDeadHostForm, onDismiss: { editingDeadHost = nil }) {
            NpmDeadHostForm(
                editing: editingDeadHost,
                certificates: certificates
            ) { request in
                if let existing = editingDeadHost {
                    try await updateDeadHost(id: existing.id, request)
                } else {
                    try await createDeadHost(request)
                }
            }
            .id(editingDeadHost?.id)
        }
        .sheet(isPresented: $showingCertificateForm) {
            NpmCertificateForm { request in
                try await createCertificate(request)
            }
        }
        .sheet(isPresented: $showingAccessListForm, onDismiss: { editingAccessList = nil }) {
            NpmAccessListForm(
                editing: editingAccessList,
                onSave: { request in
                    if let existing = editingAccessList {
                        try await updateAccessList(id: existing.id, request)
                    } else {
                        try await createAccessList(request)
                    }
                },
                onDelete: {
                    guard let existing = editingAccessList else { return }
                    await deleteAccessList(id: existing.id)
                }
            )
            .id(editingAccessList?.id)
        }
        .sheet(isPresented: $showingUserForm, onDismiss: { editingUser = nil }) {
            NpmUserForm(
                editing: editingUser,
                onSave: { request in
                    if let existing = editingUser {
                        try await updateUser(id: existing.id, request)
                    } else {
                        try await createUser(request)
                    }
                },
                onDelete: {
                    guard let existing = editingUser else { return }
                    await deleteUser(id: existing.id)
                }
            )
            .id(editingUser?.id)
        }
        .alert(localizer.t.npmDeleteConfirmTitle, isPresented: $showingDeleteConfirm) {
            Button(localizer.t.npmDelete, role: .destructive) {
                Task {
                    await pendingDeleteAction?()
                    pendingDeleteAction = nil
                }
            }
            Button(localizer.t.cancel, role: .cancel) {
                pendingDeleteAction = nil
            }
        } message: {
            Text(localizer.t.npmDeleteConfirm)
        }
        .overlay(alignment: .bottom) { toastOverlay }
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private func toolbarAddButton(for tab: NpmTab) -> some ToolbarContent {
        ToolbarItem(placement: .primaryAction) {
            switch tab {
            case .proxyHosts:
                addButton { editingProxyHost = nil; showingProxyForm = true }
            case .redirections:
                addButton { editingRedirectionHost = nil; showingRedirectionForm = true }
            case .streams:
                addButton { editingStream = nil; showingStreamForm = true }
            case .deadHosts:
                addButton { editingDeadHost = nil; showingDeadHostForm = true }
            case .accessLists:
                addButton { editingAccessList = nil; showingAccessListForm = true }
            case .ssl:
                addButton { showingCertificateForm = true }
            case .users:
                addButton { editingUser = nil; showingUserForm = true }
            case .auditLogs, .overview:
                EmptyView()
            }
        }
    }

    private func addButton(action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: "plus")
                .fontWeight(.semibold)
        }
    }

    // MARK: - Instance Picker

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .nginxProxyManager)
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
                            servicesStore.setPreferredInstance(id: instance.id, for: .nginxProxyManager)
                            clearData()
                            hasRestoredCache = false
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? npmColor : AppTheme.textMuted.opacity(0.4))
                                    .frame(width: 10, height: 10)

                                Text(instance.displayLabel)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.primary)

                                Spacer()

                                if instance.id == selectedInstanceId {
                                    Image(systemName: "checkmark")
                                        .font(.caption.bold())
                                        .foregroundStyle(npmColor)
                                }
                            }
                            .padding(12)
                            .background(
                                instance.id == selectedInstanceId
                                    ? npmColor.opacity(0.08)
                                    : Color.clear,
                                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Dashboard Menu

    private struct MenuItem: Identifiable {
        let id = UUID()
        let icon: String
        let value: String
        let label: String
        let color: Color
        let tab: NpmTab
    }

    private var menuItems: [MenuItem] {
        let report = hostReport ?? NpmHostReport()
        return [
            MenuItem(icon: "server.rack", value: "\(report.proxy)", label: localizer.t.npmProxyHosts, color: npmColor, tab: .proxyHosts),
            MenuItem(icon: "arrow.triangle.turn.up.right.diamond.fill", value: "\(report.redirection)", label: localizer.t.npmRedirections, color: Color(hex: "#5B8DEF"), tab: .redirections),
            MenuItem(icon: "arrow.left.arrow.right", value: "\(report.stream)", label: localizer.t.npmStreams, color: Color(hex: "#34C759"), tab: .streams),
            MenuItem(icon: "exclamationmark.triangle.fill", value: "\(report.dead)", label: localizer.t.npm404Hosts, color: Color(hex: "#FF9500"), tab: .deadHosts),
            MenuItem(icon: "person.2.fill", value: "\(accessLists.count)", label: localizer.t.npmAccessList, color: Color(hex: "#8E8E93"), tab: .accessLists),
            MenuItem(icon: "lock.shield.fill", value: "\(certificates.count)", label: localizer.t.npmSslCertificates, color: npmColor, tab: .ssl),
            MenuItem(icon: "person.crop.circle.fill", value: "\(users.count)", label: localizer.t.npmUsers, color: Color(hex: "#5C6BC0"), tab: .users),
            MenuItem(icon: "doc.text.magnifyingglass", value: "\(auditLogs.count)", label: localizer.t.npmAuditLogs, color: Color(hex: "#4DB6AC"), tab: .auditLogs)
        ]
    }

    private var primaryItems: [MenuItem] {
        menuItems.filter { [.redirections, .streams, .deadHosts].contains($0.tab) }
    }

    private var secondaryItems: [MenuItem] {
        menuItems.filter { [.accessLists, .ssl, .users, .auditLogs].contains($0.tab) }
    }

    private var menuHero: some View {
        let report = hostReport ?? NpmHostReport()
        return VStack(alignment: .leading, spacing: 14) {
            NavigationLink(value: NpmTab.proxyHosts) {
                HStack(spacing: 16) {
                    Image(systemName: "globe")
                        .font(.title2)
                        .foregroundStyle(npmColor)
                        .frame(width: 56, height: 56)
                        .background(npmColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 18, style: .continuous))

                    VStack(alignment: .leading, spacing: 4) {
                        Text(localizer.t.npmHostReport.sentenceCased())
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                        Text("\(report.total)")
                            .font(.title.bold())
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                        .accessibilityHidden(true)
                }
            }
            .buttonStyle(.plain)

            HStack(spacing: 10) {
                ForEach(primaryItems) { item in
                    NavigationLink(value: item.tab) {
                        primaryStatCard(item)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(18)
        .glassCard(tint: npmColor.opacity(0.08))
    }

    private var menuContent: some View {
        VStack(spacing: 0) {
            ForEach(secondaryItems) { item in
                NavigationLink(value: item.tab) {
                    secondaryRow(item)
                }
                .buttonStyle(.plain)
                if item.id != secondaryItems.last?.id {
                    Divider().padding(.leading, 64)
                }
            }
        }
        .padding(.vertical, 4)
        .glassCard()
    }

    private func sectionScreen(for tab: NpmTab) -> some View {
        ServiceDashboardLayout(
            serviceType: .nginxProxyManager,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(force: true) }
        ) {
            switch tab {
            case .overview:
                menuHero
                menuContent
            case .proxyHosts:
                proxyHostsContent
            case .redirections:
                redirectionsContent
            case .streams:
                streamsContent
            case .deadHosts:
                deadHostsContent
            case .accessLists:
                accessListsContent
            case .ssl:
                sslContent
            case .users:
                usersContent
            case .auditLogs:
                auditLogsContent
            }
        }
        .navigationTitle(tab.title(t: localizer.translations))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbarAddButton(for: tab) }
    }

    private func primaryStatCard(_ item: MenuItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: item.icon)
                    .font(.subheadline)
                    .foregroundStyle(item.color)
                    .frame(width: 32, height: 32)
                    .background(item.color.opacity(0.16), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                Spacer()
                Text(item.value)
                    .font(.title3.bold())
                    .foregroundStyle(.primary)
            }
            Text(item.label.sentenceCased())
                .font(.caption2.weight(.medium))
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .padding(12)
        .frame(maxWidth: .infinity, minHeight: 70, maxHeight: 70, alignment: .leading)
        .glassCard(cornerRadius: 14, tint: item.color.opacity(0.12))
        .contentShape(Rectangle())
    }

    private func secondaryRow(_ item: MenuItem) -> some View {
        HStack(spacing: 14) {
            Image(systemName: item.icon)
                .font(.subheadline)
                .foregroundStyle(item.color)
                .frame(width: 38, height: 38)
                .background(item.color.opacity(0.14), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(item.label.sentenceCased())
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text(item.value)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .contentShape(Rectangle())
    }

    private func cardMenu(
        editLabel: String,
        deleteLabel: String,
        onEdit: @escaping () -> Void,
        onDelete: @escaping () -> Void
    ) -> some View {
        Menu {
            Button(editLabel) { onEdit() }
            Button(deleteLabel, role: .destructive) { onDelete() }
        } label: {
            Image(systemName: "ellipsis")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .padding(8)
                .background(Color.black.opacity(0.08), in: Circle())
        }
        .padding(10)
    }

    // MARK: - Proxy Hosts Tab

    @ViewBuilder
    private var proxyHostsContent: some View {
        if proxyHosts.isEmpty && !state.isLoading {
            emptyState(icon: "server.rack", message: localizer.t.npmNoProxyHosts)
        } else {
            ForEach(proxyHosts) { host in
                NpmProxyHostCard(proxyHost: host, npmColor: npmColor, t: localizer.translations)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        editingProxyHost = host
                        showingProxyForm = true
                    }
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        confirmDelete { await deleteProxyHost(id: host.id) }
                    } label: {
                        Label(localizer.t.npmDelete, systemImage: "trash")
                    }
                    Button {
                        Task { await setProxyHostEnabled(id: host.id, enabled: !host.isEnabled) }
                    } label: {
                        Label(
                            host.isEnabled ? localizer.t.npmDisabled : localizer.t.npmEnabled,
                            systemImage: host.isEnabled ? "eye.slash" : "eye"
                        )
                    }
                    .tint(Color.gray)
                    Button {
                        editingProxyHost = host
                        showingProxyForm = true
                    } label: {
                        Label(localizer.t.actionEdit, systemImage: "pencil")
                    }
                    .tint(npmColor)
                }
            }
        }
    }

    // MARK: - Redirections Tab

    @ViewBuilder
    private var redirectionsContent: some View {
        if redirectionHosts.isEmpty && !state.isLoading {
            emptyState(icon: "arrow.triangle.turn.up.right.diamond", message: localizer.t.npmNoRedirections)
        } else {
            ForEach(redirectionHosts) { host in
                NpmRedirectionHostCard(host: host, npmColor: npmColor, t: localizer.translations)
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            confirmDelete { await deleteRedirectionHost(id: host.id) }
                        } label: {
                            Label(localizer.t.npmDelete, systemImage: "trash")
                        }
                        Button {
                            editingRedirectionHost = host
                            showingRedirectionForm = true
                        } label: {
                            Label(localizer.t.actionEdit, systemImage: "pencil")
                        }
                        .tint(npmColor)
                    }
            }
        }
    }

    // MARK: - Streams Tab

    @ViewBuilder
    private var streamsContent: some View {
        if streams.isEmpty && !state.isLoading {
            emptyState(icon: "arrow.left.arrow.right", message: localizer.t.npmNoStreams)
        } else {
            ForEach(streams) { stream in
                NpmStreamCard(stream: stream, npmColor: npmColor, t: localizer.translations)
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            confirmDelete { await deleteStream(id: stream.id) }
                        } label: {
                            Label(localizer.t.npmDelete, systemImage: "trash")
                        }
                        Button {
                            editingStream = stream
                            showingStreamForm = true
                        } label: {
                            Label(localizer.t.actionEdit, systemImage: "pencil")
                        }
                        .tint(npmColor)
                    }
            }
        }
    }

    // MARK: - Dead Hosts Tab

    @ViewBuilder
    private var deadHostsContent: some View {
        if deadHosts.isEmpty && !state.isLoading {
            emptyState(icon: "exclamationmark.triangle", message: localizer.t.npmNoDeadHosts)
        } else {
            ForEach(deadHosts) { host in
                NpmDeadHostCard(host: host, npmColor: npmColor, t: localizer.translations)
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            confirmDelete { await deleteDeadHost(id: host.id) }
                        } label: {
                            Label(localizer.t.npmDelete, systemImage: "trash")
                        }
                        Button {
                            editingDeadHost = host
                            showingDeadHostForm = true
                        } label: {
                            Label(localizer.t.actionEdit, systemImage: "pencil")
                        }
                        .tint(npmColor)
                    }
            }
        }
    }

    // MARK: - SSL Tab

    @ViewBuilder
    private var sslContent: some View {
        if certificates.isEmpty && !state.isLoading {
            emptyState(icon: "lock.shield", message: localizer.t.npmNoCertificates)
        } else {
            ForEach(certificates) { cert in
                let inUse = proxyHosts.contains(where: { $0.certificateId == cert.id }) ||
                    redirectionHosts.contains(where: { $0.certificateId == cert.id }) ||
                    deadHosts.contains(where: { $0.certificateId == cert.id })

                NpmCertificateCard(
                    certificate: cert,
                    npmColor: npmColor,
                    t: localizer.translations,
                    inUse: inUse,
                    onRenew: { Task { await renewCertificate(id: cert.id) } },
                    onDelete: { confirmDelete { await deleteCertificate(id: cert.id) } }
                )
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        confirmDelete { await deleteCertificate(id: cert.id) }
                    } label: {
                        Label(localizer.t.npmDelete, systemImage: "trash")
                    }
                }
            }
        }
    }

    // MARK: - Access Lists Tab

    @ViewBuilder
    private var accessListsContent: some View {
        if accessLists.isEmpty && !state.isLoading {
            emptyState(icon: "person.2", message: localizer.t.npmAccessListNone)
        } else {
            ForEach(accessLists) { list in
                ZStack(alignment: .topTrailing) {
                    NpmAccessListCard(
                        accessList: list,
                        t: localizer.translations,
                        onEdit: {
                            editingAccessList = list
                            showingAccessListForm = true
                        }
                    )

                    cardMenu(
                        editLabel: localizer.t.actionEdit,
                        deleteLabel: localizer.t.npmDelete,
                        onEdit: {
                            editingAccessList = list
                            showingAccessListForm = true
                        },
                        onDelete: { confirmDelete { await deleteAccessList(id: list.id) } }
                    )
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        confirmDelete { await deleteAccessList(id: list.id) }
                    } label: {
                        Label(localizer.t.npmDelete, systemImage: "trash")
                    }
                    Button {
                        editingAccessList = list
                        showingAccessListForm = true
                    } label: {
                        Label(localizer.t.actionEdit, systemImage: "pencil")
                    }
                    .tint(npmColor)
                }
            }
        }
    }

    // MARK: - Users Tab

    @ViewBuilder
    private var usersContent: some View {
        if users.isEmpty && !state.isLoading {
            emptyState(icon: "person.crop.circle", message: localizer.t.npmNoUsers)
        } else {
            ForEach(users) { user in
                userCard(user)
                    .contentShape(Rectangle())
                    .onTapGesture {
                        editingUser = user
                        showingUserForm = true
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) {
                            confirmDelete { await deleteUser(id: user.id) }
                        } label: {
                            Label(localizer.t.npmDelete, systemImage: "trash")
                        }
                        Button {
                            editingUser = user
                            showingUserForm = true
                        } label: {
                            Label(localizer.t.actionEdit, systemImage: "pencil")
                        }
                        .tint(npmColor)
                    }
            }
        }
    }

    private func userCard(_ user: NpmUser) -> some View {
        let displayName = user.name?.isEmpty == false
            ? user.name!
            : (user.nickname?.isEmpty == false ? user.nickname! : (user.email ?? "User #\(user.id)"))

        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(displayName)
                    .font(.body.bold())
                    .lineLimit(1)

                Spacer()

                if user.isDisabled == true {
                    Text(localizer.t.npmDisabled)
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.textMuted)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(AppTheme.textMuted.opacity(0.12), in: Capsule())
                }
            }

            if let email = user.email, !email.isEmpty, email != displayName {
                Text(email)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }

            if let roles = user.roles, !roles.isEmpty {
                Text(roles.joined(separator: ", "))
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
        .padding(14)
        .glassCard()
    }

    // MARK: - Audit Logs Tab

    @ViewBuilder
    private var auditLogsContent: some View {
        if auditLogs.isEmpty && !state.isLoading {
            emptyState(icon: "doc.text.magnifyingglass", message: localizer.t.npmNoAuditLogs)
        } else {
            ForEach(auditLogs) { log in
                auditLogCard(log)
            }
        }
    }

    private func auditLogCard(_ log: NpmAuditLog) -> some View {
        let actionText = auditActionLabel(log.action)
        let actionColor = auditActionColor(log.action)
        let objectLabel = auditObjectLabel(log)
        let userLabel = auditUserLabel(log)
        let timestamp = formatTimestamp(log.createdOn)

        return VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text(actionText)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(actionColor)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(actionColor.opacity(0.16), in: Capsule())

                Spacer()

                if let timestamp {
                    Text(timestamp)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            if let objectLabel {
                Text(objectLabel)
                    .font(.headline.weight(.semibold))
                    .lineLimit(2)
            }

            if let userLabel {
                Label(userLabel, systemImage: "person.fill")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                    .labelStyle(.titleAndIcon)
            }
        }
        .padding(14)
        .glassCard()
    }

    private func auditActionLabel(_ action: String?) -> String {
        let normalized = action?.replacingOccurrences(of: "_", with: " ").lowercased() ?? ""
        if normalized.contains("create") { return localizer.t.npmAuditActionCreated }
        if normalized.contains("update") { return localizer.t.npmAuditActionUpdated }
        if normalized.contains("delete") { return localizer.t.npmAuditActionDeleted }
        return action?.replacingOccurrences(of: "_", with: " ").capitalized ?? localizer.t.npmAuditLogs
    }

    private func auditActionColor(_ action: String?) -> Color {
        let normalized = action?.lowercased() ?? ""
        if normalized.contains("create") { return Color(hex: "#34C759") }
        if normalized.contains("update") { return Color(hex: "#FF9500") }
        if normalized.contains("delete") { return Color(hex: "#FF453A") }
        return npmColor
    }

    private func auditObjectLabel(_ log: NpmAuditLog) -> String? {
        let typeLabel = log.objectType?
            .replacingOccurrences(of: "-", with: " ")
            .capitalized
        let idLabel = log.objectId.map { "#\($0)" }
        let value = [typeLabel, idLabel].compactMap { $0 }.joined(separator: " • ")
        return value.isEmpty ? nil : value
    }

    private func auditUserLabel(_ log: NpmAuditLog) -> String? {
        let userValue = log.user?.name?.isEmpty == false
            ? log.user?.name
            : (log.user?.email?.isEmpty == false ? log.user?.email : nil)
        if let userValue { return userValue }
        if let userId = log.userId { return "ID \(userId)" }
        return nil
    }

    // MARK: - Empty State

    private func emptyState(icon: String, message: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.largeTitle)
                .foregroundStyle(AppTheme.textMuted)
            Text(message)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }

    private func placeholderContent(icon: String, message: String) -> some View {
        emptyState(icon: icon, message: message)
    }

    private func formatTimestamp(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        let iso = ISO8601DateFormatter()
        iso.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        var date = iso.date(from: value)
        if date == nil {
            iso.formatOptions = [.withInternetDateTime]
            date = iso.date(from: value)
        }
        guard let parsed = date else { return value }
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: parsed)
    }

    // MARK: - Toast Overlay

    @ViewBuilder
    private var toastOverlay: some View {
        if let message = actionMessage {
            Text(message)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .background(npmColor, in: Capsule())
                .shadow(color: .black.opacity(0.15), radius: 8, y: 4)
                .padding(.bottom, 20)
                .transition(.move(edge: .bottom).combined(with: .opacity))
        }
    }

    // MARK: - Delete Confirmation

    private func confirmDelete(_ action: @escaping () async -> Void) {
        pendingDeleteAction = action
        showingDeleteConfirm = true
    }

    // MARK: - Data Fetching

    private func loadAndRefreshIfNeeded() async {
        restoreCacheIfNeeded()

        let shouldRefresh: Bool = {
            guard let cached = Self.cache[selectedInstanceId] else { return true }
            return Date().timeIntervalSince(cached.lastFetch) > Self.cacheTtl
        }()

        if shouldRefresh {
            await fetchDashboard(force: false)
        }
    }

    private func restoreCacheIfNeeded() {
        guard !hasRestoredCache, let cached = Self.cache[selectedInstanceId] else { return }
        hostReport = cached.hostReport
        proxyHosts = cached.proxyHosts
        redirectionHosts = cached.redirectionHosts
        streams = cached.streams
        deadHosts = cached.deadHosts
        certificates = cached.certificates
        accessLists = cached.accessLists
        users = cached.users
        auditLogs = cached.auditLogs
        state = .loaded(())
        hasRestoredCache = true
    }

    private func updateCache() {
        Self.cache[selectedInstanceId] = CacheEntry(
            hostReport: hostReport,
            proxyHosts: proxyHosts,
            redirectionHosts: redirectionHosts,
            streams: streams,
            deadHosts: deadHosts,
            certificates: certificates,
            accessLists: accessLists,
            users: users,
            auditLogs: auditLogs,
            lastFetch: Date()
        )
    }

    private func fetchDashboard(force: Bool) async {
        if !force, let cached = Self.cache[selectedInstanceId], Date().timeIntervalSince(cached.lastFetch) <= Self.cacheTtl {
            return
        }
        let hasContent = hostReport != nil || !proxyHosts.isEmpty || !redirectionHosts.isEmpty || !streams.isEmpty ||
            !deadHosts.isEmpty || !certificates.isEmpty || !accessLists.isEmpty || !users.isEmpty || !auditLogs.isEmpty
        if !hasContent {
            state = .loading
        }
        do {
            guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let reportTask = client.getHostReport()
            async let proxyTask = client.getProxyHosts()
            async let redirectionTask = client.getRedirectionHosts()
            async let streamsTask = client.getStreams()
            async let deadTask = client.getDeadHosts()
            async let certsTask = client.getCertificates()
            async let accessTask = client.getAccessLists()
            async let usersTask = client.getUsers()
            async let auditTask = client.getAuditLogs()

            let (report, proxies, redirections, fetchedStreams, dead, certs, access) =
                try await (reportTask, proxyTask, redirectionTask, streamsTask, deadTask, certsTask, accessTask)

            let resolvedUsers: [NpmUser]
            do { resolvedUsers = try await usersTask } catch { resolvedUsers = [] }
            let resolvedAuditLogs: [NpmAuditLog]
            do { resolvedAuditLogs = try await auditTask } catch { resolvedAuditLogs = [] }

            hostReport = report
            proxyHosts = proxies
            redirectionHosts = redirections
            streams = fetchedStreams
            deadHosts = dead
            certificates = certs
            accessLists = access
            users = resolvedUsers
            auditLogs = resolvedAuditLogs
            state = .loaded(())
            updateCache()
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func clearData() {
        proxyHosts = []
        redirectionHosts = []
        streams = []
        deadHosts = []
        certificates = []
        accessLists = []
        hostReport = nil
        users = []
        auditLogs = []
    }

    private func showToast(_ message: String) {
        withAnimation(.spring(response: 0.35)) { actionMessage = message }
        Task {
            try? await Task.sleep(for: .seconds(2))
            withAnimation(.spring(response: 0.35)) { actionMessage = nil }
        }
    }

    // MARK: - CRUD: Proxy Hosts

    private func createProxyHost(_ request: NpmProxyHostRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.createProxyHost(request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func updateProxyHost(id: Int, _ request: NpmProxyHostRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.updateProxyHost(id: id, request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func deleteProxyHost(id: Int) async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            try await client.deleteProxyHost(id: id)
            await refreshAfterMutation()
            HapticManager.success()
            showToast(localizer.t.npmDeleteSuccess)
        } catch {
            HapticManager.error()
        }
    }

    private func setProxyHostEnabled(id: Int, enabled: Bool) async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            if enabled {
                try await client.enableProxyHost(id: id)
            } else {
                try await client.disableProxyHost(id: id)
            }
            await refreshAfterMutation()
            HapticManager.success()
            showToast(localizer.t.npmSaveSuccess)
        } catch {
            HapticManager.error()
        }
    }

    // MARK: - CRUD: Redirection Hosts

    private func createRedirectionHost(_ request: NpmRedirectionHostRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.createRedirectionHost(request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func updateRedirectionHost(id: Int, _ request: NpmRedirectionHostRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.updateRedirectionHost(id: id, request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func deleteRedirectionHost(id: Int) async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            try await client.deleteRedirectionHost(id: id)
            await refreshAfterMutation()
            HapticManager.success()
            showToast(localizer.t.npmDeleteSuccess)
        } catch {
            HapticManager.error()
        }
    }

    // MARK: - CRUD: Streams

    private func createStream(_ request: NpmStreamRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.createStream(request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func updateStream(id: Int, _ request: NpmStreamRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.updateStream(id: id, request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func deleteStream(id: Int) async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            try await client.deleteStream(id: id)
            await refreshAfterMutation()
            HapticManager.success()
            showToast(localizer.t.npmDeleteSuccess)
        } catch {
            HapticManager.error()
        }
    }

    // MARK: - CRUD: Dead Hosts

    private func createDeadHost(_ request: NpmDeadHostRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.createDeadHost(request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func updateDeadHost(id: Int, _ request: NpmDeadHostRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.updateDeadHost(id: id, request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func deleteDeadHost(id: Int) async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            try await client.deleteDeadHost(id: id)
            await refreshAfterMutation()
            HapticManager.success()
            showToast(localizer.t.npmDeleteSuccess)
        } catch {
            HapticManager.error()
        }
    }

    // MARK: - CRUD: Certificates

    private func createCertificate(_ request: NpmCertificateRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.createCertificate(request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    // MARK: - CRUD: Access Lists

    private func createAccessList(_ request: NpmAccessListRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.createAccessList(request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func updateAccessList(id: Int, _ request: NpmAccessListRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.updateAccessList(id: id, request)
        await refreshAfterMutation()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func deleteAccessList(id: Int) async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            try await client.deleteAccessList(id: id)
            await refreshAfterMutation()
            HapticManager.success()
            showToast(localizer.t.npmDeleteSuccess)
        } catch {
            HapticManager.error()
        }
    }

    // MARK: - CRUD: Users

    private func createUser(_ request: NpmUserRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.createUser(request)
        await refreshUsers()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func updateUser(id: Int, _ request: NpmUserRequest) async throws {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        _ = try await client.updateUser(id: id, request)
        await refreshUsers()
        showToast(localizer.t.npmSaveSuccess)
    }

    private func deleteUser(id: Int) async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            try await client.deleteUser(id: id)
            await refreshUsers()
            HapticManager.success()
            showToast(localizer.t.npmDeleteSuccess)
        } catch {
            HapticManager.error()
        }
    }

    private func renewCertificate(id: Int) async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            _ = try await client.renewCertificate(id: id)
            await refreshAfterMutation()
            HapticManager.success()
            showToast(localizer.t.npmRenewSuccess)
        } catch {
            HapticManager.error()
        }
    }

    private func deleteCertificate(id: Int) async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            try await client.deleteCertificate(id: id)
            await refreshAfterMutation()
            HapticManager.success()
            showToast(localizer.t.npmDeleteSuccess)
        } catch {
            HapticManager.error()
        }
    }

    // MARK: - Refresh After Mutation

    private func refreshAfterMutation() async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            async let reportTask = client.getHostReport()
            async let proxyTask = client.getProxyHosts()
            async let redirectionTask = client.getRedirectionHosts()
            async let streamsTask = client.getStreams()
            async let deadTask = client.getDeadHosts()
            async let certsTask = client.getCertificates()
            async let accessTask = client.getAccessLists()

            let (report, proxies, redirections, fetchedStreams, dead, certs, access) =
                try await (reportTask, proxyTask, redirectionTask, streamsTask, deadTask, certsTask, accessTask)

            hostReport = report
            proxyHosts = proxies
            redirectionHosts = redirections
            streams = fetchedStreams
            deadHosts = dead
            certificates = certs
            accessLists = access
            updateCache()
        } catch {
            // Silently fail on refresh - data will be stale but user already got their action feedback
        }
    }

    private func refreshUsers() async {
        guard let client = await servicesStore.npmClient(instanceId: selectedInstanceId) else { return }
        do {
            async let usersTask = client.getUsers()
            async let auditTask = client.getAuditLogs()

            let resolvedUsers: [NpmUser]
            do { resolvedUsers = try await usersTask } catch { resolvedUsers = [] }
            let resolvedAuditLogs: [NpmAuditLog]
            do { resolvedAuditLogs = try await auditTask } catch { resolvedAuditLogs = [] }

            users = resolvedUsers
            auditLogs = resolvedAuditLogs
            updateCache()
        } catch {
            // Ignore refresh errors
        }
    }
}
