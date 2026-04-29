import SwiftUI

struct UniFiClientsView: View {
    let instanceId: UUID
    let initialClients: [UniFiClient]
    let sites: [UniFiSite]

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    @State private var clients: [UniFiClient]
    @State private var searchText = ""
    @State private var filter: ClientFilter = .all
    @State private var selectedClient: UniFiClient?
    @State private var authorizingClientId: String?
    @State private var actionMessage: String?
    @State private var actionTint: Color = AppTheme.running
    @State private var actionSucceeded = true
    @State private var selectedSiteId: String?

    private let color = ServiceType.unifiNetwork.colors.primary

    init(instanceId: UUID, initialClients: [UniFiClient], sites: [UniFiSite] = [], selectedSiteId: String? = nil) {
        self.instanceId = instanceId
        self.initialClients = initialClients
        self.sites = sites
        _clients = State(initialValue: initialClients)
        _selectedSiteId = State(initialValue: selectedSiteId)
    }

    // MARK: - Filter

    enum ClientFilter: String, CaseIterable, Identifiable {
        case all, wifi, wired, guest
        var id: String { rawValue }
    }

    private var filteredClients: [UniFiClient] {
        var result = clients
        if let selectedSiteId {
            result = result.filter { $0.siteId == selectedSiteId }
        }
        switch filter {
        case .all:   break
        case .wifi:  result = result.filter { $0.type?.uppercased() == "WIRELESS" }
        case .wired: result = result.filter { $0.type?.uppercased() == "WIRED" }
        case .guest: result = result.filter { $0.isGuestUnauthorized || $0.access?.type?.uppercased() == "GUEST" }
        }
        guard !searchText.isEmpty else { return result }
        return result.filter {
            $0.displayName.localizedCaseInsensitiveContains(searchText) ||
            $0.ipAddress?.localizedCaseInsensitiveContains(searchText) == true ||
            $0.macAddress?.localizedCaseInsensitiveContains(searchText) == true ||
            $0.networkName?.localizedCaseInsensitiveContains(searchText) == true
        }
    }

    // MARK: - Body

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if availableSites.count > 1 {
                    siteFilterBar
                        .padding(.top, 4)
                }
                filterBar

                if let msg = actionMessage {
                    actionBanner(msg)
                }

                if filteredClients.isEmpty {
                    emptyState
                } else {
                    ForEach(filteredClients) { client in
                        clientRow(client)
                    }
                }
            }
            .padding(AppTheme.padding)
        }
        .navigationTitle(localizer.t.unifiClients)
        .navigationBarTitleDisplayMode(.large)
        .searchable(text: $searchText, prompt: localizer.t.unifiSearchClients)
        .sheet(item: $selectedClient) { client in
            UniFiClientDetailSheet(
                client: client,
                instanceId: instanceId,
                onAuthorize: { authorize(client) }
            )
        }
    }

    // MARK: - Filter Bar

    private var availableSites: [UniFiSite] {
        sites.filter { site in
            clients.contains { $0.siteId == site.siteId }
        }
    }

    private var siteFilterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chipButton(localizer.t.unifiAllSites, active: selectedSiteId == nil) { selectedSiteId = nil }
                ForEach(availableSites) { site in
                    chipButton(site.displayName, active: selectedSiteId == site.siteId) {
                        selectedSiteId = site.siteId
                    }
                }
            }
            .padding(.horizontal, 2)
        }
    }

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                chipButton(localizer.t.unifiAll, active: filter == .all) { filter = .all }
                chipButton(localizer.t.unifiWifiClients, active: filter == .wifi) { filter = .wifi }
                chipButton(localizer.t.unifiWiredClients, active: filter == .wired) { filter = .wired }
                chipButton(localizer.t.unifiGuest, active: filter == .guest) { filter = .guest }
            }
            .padding(.horizontal, 2)
        }
    }

    private func chipButton(_ title: String, active: Bool, action: @escaping () -> Void) -> some View {
        Button {
            HapticManager.light()
            action()
        } label: {
            Text(title)
                .font(.caption.bold())
                .foregroundStyle(active ? .white : color)
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(active ? color : color.opacity(0.12), in: Capsule())
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: filter)
    }

    // MARK: - Client Row

    private func clientRow(_ client: UniFiClient) -> some View {
        HStack(spacing: 14) {
            clientTypeIcon(client)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(client.displayName)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    if client.isGuestUnauthorized {
                        Text(localizer.t.unifiGuestUnauthorized)
                            .font(.caption2.bold())
                            .foregroundStyle(AppTheme.warning)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(AppTheme.warning.opacity(0.12), in: Capsule())
                    }
                }
                Text([client.ipAddress, client.networkName].compactMap(\.self).joined(separator: " • "))
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(1)
                if let live = client.liveTrafficBytesPerSecond {
                    Text(liveRate(client, total: live))
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }

            Spacer()

            HStack(spacing: 10) {
                if let usage = formattedUsage(client) {
                    Text(usage)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(AppTheme.textMuted)
                }

                if client.isGuestUnauthorized, let siteId = client.siteId {
                    authorizeButton(client: client, siteId: siteId)
                } else {
                    Button {
                        HapticManager.light()
                        selectedClient = client
                    } label: {
                        Image(systemName: "info.circle")
                            .font(.subheadline)
                            .foregroundStyle(color.opacity(0.7))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
        .glassCard(tint: client.isGuestUnauthorized ? AppTheme.warning.opacity(0.06) : nil)
        .contentShape(Rectangle())
        .onTapGesture {
            HapticManager.light()
            selectedClient = client
        }
    }

    private func clientTypeIcon(_ client: UniFiClient) -> some View {
        let isWifi = client.type?.uppercased() == "WIRELESS"
        let tint: Color = client.isGuestUnauthorized ? AppTheme.warning : color
        return Image(systemName: isWifi ? "wifi" : "cable.connector")
            .font(.body.bold())
            .foregroundStyle(tint)
            .frame(width: 44, height: 44)
            .background(
                tint.opacity(colorScheme == .dark ? 0.18 : 0.1),
                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
            )
    }

    private func authorizeButton(client: UniFiClient, siteId: String) -> some View {
        Button {
            authorize(client)
        } label: {
            if authorizingClientId == client.id {
                ProgressView()
                    .controlSize(.small)
            } else {
                Image(systemName: "checkmark.shield.fill")
                    .font(.subheadline.bold())
                    .foregroundStyle(color)
            }
        }
        .buttonStyle(.bordered)
        .tint(color)
        .disabled(authorizingClientId != nil)
    }

    // MARK: - Action Banner

    private func actionBanner(_ message: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: actionSucceeded ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                .foregroundStyle(actionTint)
            Text(message)
                .font(.subheadline)
            Spacer()
        }
        .padding(14)
        .glassCard(tint: actionTint.opacity(0.08))
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 14) {
            Image(systemName: "person.2.slash")
                .font(.system(size: 44))
                .foregroundStyle(AppTheme.textMuted)
            Text(localizer.t.unifiNoClients)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }

    // MARK: - Helpers

    private func formattedUsage(_ client: UniFiClient) -> String? {
        guard let rx = client.rxBytes, let tx = client.txBytes, rx + tx > 0 else { return nil }
        let total = rx + tx
        return "↕ \(Formatters.formatBytes(Double(total)))"
    }

    private func liveRate(_ client: UniFiClient, total: Double) -> String {
        let rx = client.rxRateBytesPerSecond.map { "↓ \(Formatters.formatBytes($0))/s" }
        let tx = client.txRateBytesPerSecond.map { "↑ \(Formatters.formatBytes($0))/s" }
        let parts = [rx, tx].compactMap { $0 }
        return parts.isEmpty ? "\(Formatters.formatBytes(total))/s" : parts.joined(separator: "  ")
    }

    // MARK: - Authorization

    private func authorize(_ client: UniFiClient) {
        guard let siteId = client.siteId else { return }
        authorizingClientId = client.id
        Task {
            defer { authorizingClientId = nil }
            do {
                guard let api = await servicesStore.unifiClient(instanceId: instanceId) else { return }
                try await api.authorizeGuest(siteId: siteId, clientId: client.id)
                actionSucceeded = true
                actionTint = AppTheme.running
                actionMessage = String(format: localizer.t.unifiGuestAuthorizedFormat, client.displayName)
                clients = clients.map { c in
                    c.id == client.id ? c.withAuthorized() : c
                }
                try? await Task.sleep(for: .seconds(3))
                actionMessage = nil
            } catch {
                actionSucceeded = false
                actionTint = AppTheme.warning
                actionMessage = localizer.t.unifiGuestAuthorizationFailed
            }
        }
    }
}

// MARK: - Client Detail Sheet

struct UniFiClientDetailSheet: View {
    let client: UniFiClient
    let instanceId: UUID
    let onAuthorize: () -> Void

    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    private let color = ServiceType.unifiNetwork.colors.primary

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    headerCard
                    infoCard
                    if client.isGuestUnauthorized {
                        guestAuthCard
                    }
                }
                .padding(AppTheme.padding)
            }
            .navigationTitle(localizer.t.unifiClientDetail)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    UniFiSheetCloseButton { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
    }

    // MARK: Header

    private var headerCard: some View {
        let isWifi = client.type?.uppercased() == "WIRELESS"
        let tint: Color = client.isGuestUnauthorized ? AppTheme.warning : color
        return HStack(spacing: 16) {
            Image(systemName: isWifi ? "wifi" : "cable.connector")
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(tint)
                .frame(width: 64, height: 64)
                .background(tint.opacity(colorScheme == .dark ? 0.2 : 0.1), in: RoundedRectangle(cornerRadius: 20, style: .continuous))

            VStack(alignment: .leading, spacing: 5) {
                Text(client.displayName)
                    .font(.title3.bold())
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Text(isWifi ? localizer.t.unifiWifiClients : localizer.t.unifiWiredClients)
                    .font(.caption.bold())
                    .foregroundStyle(tint)
            }

            Spacer()

            if client.isGuestUnauthorized {
                Text(localizer.t.unifiGuestUnauthorized)
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.warning)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(AppTheme.warning.opacity(0.12), in: Capsule())
            }
        }
        .padding(18)
        .glassCard(tint: tint.opacity(colorScheme == .dark ? 0.12 : 0.06))
    }

    // MARK: Info Card

    private var infoCard: some View {
        let usage: String? = {
            guard let rx = client.rxBytes, let tx = client.txBytes, rx + tx > 0 else { return nil }
            return "↓ \(Formatters.formatBytes(Double(rx)))  ↑ \(Formatters.formatBytes(Double(tx)))"
        }()

        let rows: [(String, String)] = [
            ("IP", client.ipAddress ?? "—"),
            ("MAC", client.macAddress ?? "—"),
            (localizer.t.unifiNetworks, client.networkName ?? "—"),
            (localizer.t.unifiUsage, usage ?? "—")
        ].filter { $0.1 != "—" }

        return VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { idx, row in
                HStack {
                    Text(row.0)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                    Spacer()
                    Text(row.1)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 13)
                if idx < rows.count - 1 {
                    Divider().padding(.leading, 16)
                }
            }
        }
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    // MARK: Guest Auth Card

    private var guestAuthCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 10) {
                Image(systemName: "person.badge.clock.fill")
                    .font(.body.bold())
                    .foregroundStyle(AppTheme.warning)
                    .frame(width: 36, height: 36)
                    .background(AppTheme.warning.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(localizer.t.unifiGuest)
                        .font(.subheadline.bold())
                    Text(localizer.t.unifiGuestUnauthorized)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                }
            }

            Button {
                HapticManager.medium()
                onAuthorize()
                dismiss()
            } label: {
                Label(localizer.t.unifiGuest, systemImage: "checkmark.shield.fill")
                    .font(.subheadline.bold())
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 13)
                    .background(color, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .glassCard(tint: AppTheme.warning.opacity(0.06))
    }
}
