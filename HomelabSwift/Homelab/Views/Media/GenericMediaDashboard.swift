import SwiftUI
import UIKit

struct GenericMediaDashboard: View {
    let serviceType: ServiceType
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase

    @State private var state: LoadableState<Void> = .idle
    @State private var instance: ServiceInstance?
    @State private var statusDetails: [GenericStatusDetail] = []
    @State private var snapshot: GenericServiceSnapshot?
    @State private var isLoadingDetails = false
    @State private var pendingRequestActions: Set<Int> = []
    @State private var pendingSessionDeletions: Set<String> = []
    @State private var isRunningServiceAction = false
    @State private var actionMessage: String?
    @State private var showCopiedToast = false
    @State private var isViewVisible = false
    @State private var silentSnapshotSkips = 0
    @State private var silentDetailsSkips = 0
    @State private var isRefreshing = false
    @State private var pendingInteractiveRefresh = false
    @State private var contentSearchQuery: String = ""
    @State private var contentSearchResults: [GenericLookupResult] = []
    @State private var isSearchingContent = false
    @State private var contentSearchError: String?
    @State private var selectedSearchResult: GenericLookupResult?
    @State private var pendingContentRequestIds: Set<String> = []

    private let timer = Timer.publish(every: 30, on: .main, in: .common).autoconnect()

    private var reachability: Bool? {
        servicesStore.reachability(for: instanceId)
    }

    private var isPinging: Bool {
        servicesStore.isPinging(instanceId: instanceId)
    }

    private var arr: ArrStrings {
        localizer.arr
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: serviceType,
            instanceId: instanceId,
            state: state,
            onRefresh: { await refreshStatus(silent: false) }
        ) {
            VStack(spacing: 16) {
                headerCard

                if !statusDetails.isEmpty {
                    statusDetailsCard
                } else if isLoadingDetails {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .padding(14)
                        .glassCard()
                }

                if let actionMessage {
                    actionMessageCard(actionMessage)
                }

                if let snapshot {
                    snapshotCard(snapshot)
                }

                if let instance {
                    detailsCard(instance)
                } else {
                    missingInstanceCard
                }
            }
            .padding(.top, 8)

            if showCopiedToast {
                ToastView(message: localizer.t.settingsCopied)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .animation(.spring(duration: 0.3), value: showCopiedToast)
        .task {
            isViewVisible = true
            await refreshStatus(silent: false)
        }
        .onAppear { isViewVisible = true }
        .onDisappear { isViewVisible = false }
        .onReceive(timer) { _ in
            guard scenePhase == .active, isViewVisible else { return }
            Task { await refreshStatus(silent: true) }
        }
        .sheet(item: $selectedSearchResult) { result in
            MediaSearchResultDetailSheet(
                result: MediaSearchResultPresentation(
                    id: result.id,
                    title: result.title,
                    subtitle: result.subtitle,
                    supporting: result.supporting,
                    status: result.status.map(localizedSearchStatus),
                    posterURL: result.posterURL,
                    artworkHeaders: serviceArtworkHeaders(for: result.posterURL, instance: instance),
                    detailsURL: result.detailsURL,
                    details: result.details,
                    canRequest: result.requestMediaType != nil && result.requestMediaId != nil
                ),
                openDetailsTitle: arr.openDetails,
                requestTitle: arr.requestContent,
                isRequesting: pendingContentRequestIds.contains(result.id),
                onOpenDetails: result.detailsURL.map { url in
                    { open(url) }
                },
                onRequest: (result.requestMediaType != nil && result.requestMediaId != nil) ? {
                    Task { await runContentRequest(for: result) }
                } : nil
            )
            .presentationDetents([.medium, .large])
        }
    }

    private var headerCard: some View {
        VStack(spacing: 18) {
            ServiceIconView(type: serviceType, size: 56)
                .frame(width: 72, height: 72)
                .background(serviceType.colors.bg, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .padding(.top, 8)

            VStack(spacing: 6) {
                Text(serviceType.displayName)
                    .font(.title.bold())

                Text(serviceType.localizedDescription(using: localizer.translations))
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            statusPill
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .glassCard(tint: serviceType.colors.primary.opacity(0.05))
    }

    @ViewBuilder
    private var statusPill: some View {
        if isPinging {
            HStack(spacing: 8) {
                ProgressView()
                    .controlSize(.small)
                Text(localizer.t.statusVerifying)
                    .font(.caption.bold())
            }
            .foregroundStyle(AppTheme.info)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(AppTheme.info.opacity(0.12), in: Capsule())
        } else if reachability == true {
            HStack(spacing: 8) {
                Circle()
                    .fill(AppTheme.running)
                    .frame(width: 8, height: 8)
                Text(localizer.t.statusOnline)
                    .font(.caption.bold())
            }
            .foregroundStyle(AppTheme.running)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(AppTheme.running.opacity(0.12), in: Capsule())
        } else if reachability == false {
            HStack(spacing: 8) {
                Circle()
                    .fill(AppTheme.danger)
                    .frame(width: 8, height: 8)
                Text(localizer.t.statusUnreachable)
                    .font(.caption.bold())
            }
            .foregroundStyle(AppTheme.danger)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(AppTheme.danger.opacity(0.12), in: Capsule())
        } else {
            HStack(spacing: 8) {
                Circle()
                    .fill(AppTheme.textMuted)
                    .frame(width: 8, height: 8)
                Text(localizer.t.statusVerifying)
                    .font(.caption.bold())
            }
            .foregroundStyle(AppTheme.textMuted)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(AppTheme.textMuted.opacity(0.12), in: Capsule())
        }
    }

    private var statusDetailsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(statusDetails) { item in
                HStack(spacing: 8) {
                    Text(localizedDetailLabel(item.label))
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                    Spacer()
                    Text(item.value)
                        .font(.subheadline.weight(.semibold))
                        .multilineTextAlignment(.trailing)
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    @ViewBuilder
    private func snapshotCard(_ snapshot: GenericServiceSnapshot) -> some View {
        switch snapshot {
        case .jellyseerr(let data):
            jellyseerrCard(data)
        case .prowlarr(let data):
            prowlarrCard(data)
        case .bazarr(let data):
            bazarrCard(data)
        case .gluetun(let data):
            gluetunCard(data)
        case .flaresolverr(let data):
            flaresolverrCard(data)
        }
    }

    private func jellyseerrCard(_ data: JellyseerrSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(arr.requests)
                    .font(.headline.bold())
                Spacer()
                if let version = data.version {
                    Text("v\(version)")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.info)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(AppTheme.info.opacity(0.12), in: Capsule())
                }
            }

            HStack(spacing: 10) {
                infoChip(title: arr.total, value: "\(data.totalRequests)", color: AppTheme.primary)
                infoChip(title: arr.pending, value: "\(data.pendingRequests)", color: AppTheme.warning)
            }

            HStack(spacing: 10) {
                infoChip(title: arr.approved, value: "\(data.approvedRequests)", color: AppTheme.info)
                infoChip(title: arr.available, value: "\(data.availableRequests)", color: AppTheme.running)
            }

            if data.pendingRequests > 0 {
                HStack(spacing: 10) {
                    Button {
                        Task { await runJellyseerrBulkAction(.approveOldestPending) }
                    } label: {
                        Label(arr.approveOldestPending, systemImage: "checkmark.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .disabled(isRunningServiceAction)

                    Button(role: .destructive) {
                        Task { await runJellyseerrBulkAction(.declineOldestPending) }
                    } label: {
                        Label(arr.declineOldestPending, systemImage: "xmark.circle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .disabled(isRunningServiceAction)
                }
                .font(.caption.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            }

            HStack(spacing: 10) {
                Button {
                    Task { await runJellyseerrBulkAction(.runRecentScan) }
                } label: {
                    Label(arr.recentMediaScan, systemImage: "sparkles")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningServiceAction)

                Button {
                    Task { await runJellyseerrBulkAction(.runFullScan) }
                } label: {
                    Label(arr.fullMediaScan, systemImage: "arrow.triangle.2.circlepath")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningServiceAction)
            }
            .font(.caption.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.7)

            contentSearchSection(serviceLabel: ServiceType.jellyseerr.displayName)

            if data.recentRequests.isEmpty {
                Text(localizer.t.noData)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
            } else {
                ForEach(data.recentRequests.prefix(8)) { request in
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .top) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(request.title)
                                    .font(.subheadline.weight(.semibold))
                                    .lineLimit(2)
                                HStack(spacing: 6) {
                                    Text(request.status)
                                        .font(.caption2.bold())
                                        .foregroundStyle(statusColor(request.status))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 3)
                                        .background(statusColor(request.status).opacity(0.12), in: Capsule())

                                    if let requestedBy = request.requestedBy {
                                        Text(requestedBy)
                                            .font(.caption2)
                                            .foregroundStyle(AppTheme.textMuted)
                                            .lineLimit(1)
                                    }
                                }
                            }
                            Spacer()
                        }

                        if let requestedAt = request.requestedAt {
                            Text(Formatters.formatDate(requestedAt))
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        if request.isPending {
                            HStack(spacing: 8) {
                                Button {
                                    Task { await handleJellyseerrRequestAction(request.id, approve: true) }
                                } label: {
                                    Label(localizer.t.confirm, systemImage: "checkmark")
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.borderedProminent)
                                .tint(AppTheme.running)

                                Button(role: .destructive) {
                                    Task { await handleJellyseerrRequestAction(request.id, approve: false) }
                                } label: {
                                    Label(localizer.t.delete, systemImage: "xmark")
                                        .frame(maxWidth: .infinity)
                                }
                                .buttonStyle(.bordered)
                            }
                            .font(.caption.bold())
                            .disabled(pendingRequestActions.contains(request.id))
                        }
                    }
                    .padding(12)
                    .background(AppTheme.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    private func prowlarrCard(_ data: ProwlarrSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(arr.indexers)
                    .font(.headline.bold())
                Spacer()
                if let version = data.version {
                    Text("v\(version)")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.info)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(AppTheme.info.opacity(0.12), in: Capsule())
                }
            }

            HStack(spacing: 10) {
                infoChip(title: arr.indexers, value: "\(data.indexers.count)", color: AppTheme.primary)
                infoChip(title: arr.apps, value: "\(data.applications.count)", color: AppTheme.info)
                infoChip(title: arr.issues, value: "\(data.unhealthyCount)", color: data.unhealthyCount > 0 ? AppTheme.warning : AppTheme.running)
            }

            HStack(spacing: 10) {
                Button {
                    Task { await runProwlarrAction(.testIndexers) }
                } label: {
                    Label(arr.testIndexers, systemImage: "checkmark.seal")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningServiceAction)

                Button {
                    Task { await runProwlarrAction(.syncApps) }
                } label: {
                    Label(arr.syncApps, systemImage: "arrow.triangle.2.circlepath")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .disabled(isRunningServiceAction)
            }
            .font(.caption.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.7)

            Button {
                Task { await runProwlarrAction(.healthCheck) }
            } label: {
                Label(arr.healthCheck, systemImage: "heart.text.square")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(isRunningServiceAction)
            .font(.caption.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.7)

            contentSearchSection(serviceLabel: ServiceType.prowlarr.displayName)

            if !data.indexers.isEmpty {
                ForEach(data.indexers.prefix(10)) { indexer in
                    HStack(spacing: 10) {
                        Circle()
                            .fill(indexer.enabled ? AppTheme.running : AppTheme.textMuted)
                            .frame(width: 8, height: 8)
                        Text(indexer.name)
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                        Spacer()
                        Text(indexer.status)
                            .font(.caption2.bold())
                            .foregroundStyle(statusColor(indexer.status))
                    }
                    .padding(.vertical, 2)
                }
            } else {
                Text(localizer.t.noData)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
            }

            if !data.healthIssues.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(arr.health)
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                    ForEach(data.healthIssues.prefix(4), id: \.self) { issue in
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.caption)
                                .foregroundStyle(AppTheme.warning)
                            Text(issue)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                }
            }

            if !data.recentHistory.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(localizer.arr.recentHistory)
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                    ForEach(data.recentHistory.prefix(3), id: \.self) { event in
                        Text(event)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                            .lineLimit(1)
                    }
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    private func bazarrCard(_ data: BazarrSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(arr.subtitles)
                    .font(.headline.bold())
                Spacer()
                if let version = data.version {
                    Text("v\(version)")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.info)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(AppTheme.info.opacity(0.12), in: Capsule())
                }
            }

            if !data.badges.isEmpty {
                LazyVGrid(columns: twoColumnGrid, spacing: 8) {
                    ForEach(data.badges.prefix(6)) { badge in
                        HStack {
                            Text(badge.label)
                                .font(.caption.bold())
                                .foregroundStyle(AppTheme.textSecondary)
                            Spacer()
                            Text(badge.value)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(AppTheme.primary)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 8)
                        .background(AppTheme.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
            }

            if !data.issues.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(arr.health)
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                    ForEach(data.issues.prefix(3), id: \.self) { issue in
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.caption)
                                .foregroundStyle(AppTheme.warning)
                            Text(issue)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                }
            }

            if !data.tasks.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text(arr.service)
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)
                    ForEach(data.tasks.prefix(3), id: \.self) { task in
                        Text(task)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                            .lineLimit(1)
                    }
                }
            }

            if data.badges.isEmpty && data.issues.isEmpty && data.tasks.isEmpty {
                Text(localizer.t.noData)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
            }
        }
        .padding(16)
        .glassCard()
    }

    private func gluetunCard(_ data: GluetunSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(arr.vpn)
                .font(.headline.bold())

            detailRow(title: arr.statusLabel, value: data.connectionStatus ?? localizer.t.notAvailable)
            detailRow(title: arr.publicIPLabel, value: data.publicIP ?? localizer.t.notAvailable)

            if let country = data.country {
                detailRow(title: arr.countryLabel, value: country)
            }

            if let server = data.serverName {
                detailRow(title: arr.serverLabel, value: server)
            }

            if let provider = data.vpnProvider {
                detailRow(title: arr.provider, value: provider)
            }

            if let port = data.forwardedPort {
                detailRow(title: arr.forwardedPort, value: port)
            }

            Button {
                Task { await restartGluetunVPN() }
            } label: {
                Label(arr.restartVpnTunnel, systemImage: "arrow.triangle.2.circlepath")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(isRunningServiceAction)
            .font(.caption.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.75)
        }
        .padding(16)
        .glassCard()
    }

    private func flaresolverrCard(_ data: FlaresolverrSnapshot) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(arr.service)
                    .font(.headline.bold())
                Spacer()
                Button {
                    Task { await createFlaresolverrSession() }
                } label: {
                    Label(arr.newSession, systemImage: "plus")
                }
                .font(.caption.bold())
                .buttonStyle(.bordered)
                .disabled(isRunningServiceAction)
            }

            detailRow(title: arr.versionLabel, value: data.version ?? localizer.t.notAvailable)
            detailRow(title: arr.statusLabel, value: data.status ?? localizer.t.notAvailable)
            detailRow(title: arr.sessions, value: "\(data.sessions.count)")

            if let message = data.message, !message.isEmpty {
                detailRow(title: arr.messageLabel, value: message)
            }

            if !data.sessions.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text(arr.sessionIds)
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textMuted)

                    ForEach(data.sessions.prefix(6), id: \.self) { session in
                        HStack(spacing: 8) {
                            Text(session)
                                .font(.caption.monospaced())
                                .lineLimit(1)
                            Spacer()
                            Button(role: .destructive) {
                                Task { await destroyFlaresolverrSession(session) }
                            } label: {
                                Image(systemName: "trash")
                                    .font(.caption.bold())
                            }
                            .buttonStyle(.bordered)
                            .disabled(pendingSessionDeletions.contains(session))
                        }
                    }
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    private func actionMessageCard(_ text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(AppTheme.running)
            Text(text)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.running)
            Spacer()
        }
        .padding(12)
        .background(AppTheme.running.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func detailsCard(_ instance: ServiceInstance) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            infoRow(title: arr.urlLabel, value: instance.url)

            if let fallback = instance.fallbackUrl, !fallback.isEmpty {
                infoRow(title: arr.fallbackURLLabel, value: fallback)
            }

            if let key = instance.apiKey, !key.isEmpty {
                infoRow(title: arr.apiKeyLabel, value: maskedSecret(key))
            }

            VStack(spacing: 8) {
                HStack(spacing: 10) {
                    Button {
                        open(instance.url)
                    } label: {
                        Label(arr.openService, systemImage: "arrow.up.right.square")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(serviceType.colors.primary)

                    Button {
                        copy(instance.url)
                    } label: {
                        Label(localizer.t.copy.sentenceCased(), systemImage: "doc.on.doc")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                }

                HStack(spacing: 10) {
                    if let fallback = instance.fallbackUrl, !fallback.isEmpty {
                        Button {
                            open(fallback)
                        } label: {
                            Label(arr.openFallback, systemImage: "link")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }

                    Button {
                        Task { await refreshStatus(silent: false) }
                    } label: {
                        Label(localizer.t.refresh, systemImage: "arrow.clockwise")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .disabled(isPinging)
                }
            }
            .font(.subheadline.weight(.semibold))
            .lineLimit(1)
            .minimumScaleFactor(0.75)
        }
        .padding(16)
        .glassCard()
    }

    private var missingInstanceCard: some View {
        VStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill")
                .foregroundStyle(AppTheme.warning)
            Text(localizer.t.settingsNoInstances)
                .font(.headline)
                .foregroundStyle(AppTheme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .glassCard()
    }

    private func infoRow(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title)
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
            Text(value)
                .font(.subheadline.weight(.semibold))
                .textSelection(.enabled)
        }
    }

    private func detailRow(title: String, value: String) -> some View {
        HStack(spacing: 8) {
            Text(title)
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.semibold))
                .multilineTextAlignment(.trailing)
        }
    }

    private func infoChip(title: String, value: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption2.bold())
                .foregroundStyle(AppTheme.textMuted)
            Text(value)
                .font(.subheadline.weight(.heavy))
                .foregroundStyle(color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    @ViewBuilder
    private func contentSearchSection(serviceLabel: String) -> some View {
        let accent = serviceType.colors.primary
        let trimmedQuery = contentSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        let canRunSearch = !isSearchingContent && trimmedQuery.count >= 2
        let canClearSearch = !isSearchingContent && (!trimmedQuery.isEmpty || !contentSearchResults.isEmpty || contentSearchError != nil)

        VStack(alignment: .leading, spacing: 14) {
            Text(arr.contentSearchTitle)
                .font(.headline.bold())

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(accent)

                TextField(arr.contentSearchPlaceholder(serviceLabel), text: $contentSearchQuery)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .submitLabel(.search)
                    .onSubmit {
                        Task { await runContentSearch() }
                    }

                if !contentSearchQuery.isEmpty {
                    Button {
                        contentSearchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(arr.clearSearch)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .glassCard(cornerRadius: 14, tint: accent.opacity(0.08))

            HStack(spacing: 10) {
                Button {
                    Task { await runContentSearch() }
                } label: {
                    Label(arr.searchNow, systemImage: isSearchingContent ? "hourglass" : "sparkle.magnifyingglass")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glassProminent)
                .disabled(!canRunSearch)

                Button {
                    contentSearchQuery = ""
                    contentSearchResults = []
                    contentSearchError = nil
                } label: {
                    Label(arr.clearSearch, systemImage: "xmark")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.glass)
                .disabled(!canClearSearch)
            }
            .font(.subheadline.weight(.semibold))
            .lineLimit(1)
            .minimumScaleFactor(0.75)

            if isSearchingContent {
                ProgressView()
                    .controlSize(.small)
                    .tint(accent)
            } else if let contentSearchError {
                Text(contentSearchError)
                    .font(.caption)
                    .foregroundStyle(AppTheme.danger)
                    .lineLimit(2)
            } else if !trimmedQuery.isEmpty && contentSearchResults.isEmpty {
                Text(arr.searchNoResults)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
            }

            if !contentSearchResults.isEmpty {
                VStack(spacing: 8) {
                    ForEach(contentSearchResults.prefix(8)) { result in
                        Button {
                            selectedSearchResult = result
                        } label: {
                            MediaSearchResultRow(
                                result: MediaSearchResultPresentation(
                                    id: result.id,
                                    title: result.title,
                                    subtitle: result.subtitle,
                                    supporting: result.supporting,
                                    status: result.status.map(localizedSearchStatus),
                                    posterURL: result.posterURL,
                                    artworkHeaders: serviceArtworkHeaders(for: result.posterURL, instance: instance),
                                    detailsURL: result.detailsURL,
                                    details: result.details,
                                    canRequest: result.requestMediaType != nil && result.requestMediaId != nil
                                )
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func localizedDetailLabel(_ raw: String) -> String {
        switch raw.lowercased() {
        case "status":
            return arr.statusLabel
        case "version":
            return arr.versionLabel
        case "message":
            return arr.messageLabel
        case "ip":
            return arr.publicIPLabel
        case "server":
            return arr.serverLabel
        default:
            return raw
        }
    }

    private func statusColor(_ status: String) -> Color {
        let normalized = status.lowercased()
        if normalized.contains("approve") || normalized.contains("available") || normalized.contains("healthy") || normalized.contains("ok") {
            return AppTheme.running
        }
        if normalized.contains("pending") || normalized.contains("processing") || normalized.contains("warning") {
            return AppTheme.warning
        }
        if normalized.contains("decline") || normalized.contains("error") || normalized.contains("down") || normalized.contains("failed") {
            return AppTheme.danger
        }
        return AppTheme.info
    }

    private func open(_ rawURL: String) {
        guard let url = URL(string: rawURL) else { return }
        HapticManager.medium()
        openURL(url)
    }

    private func copy(_ text: String) {
        UIPasteboard.general.string = text
        HapticManager.light()
        showCopiedToast = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) {
            withAnimation {
                showCopiedToast = false
            }
        }
    }

    private func maskedSecret(_ value: String) -> String {
        guard value.count > 8 else {
            return String(repeating: "•", count: max(4, value.count))
        }

        let prefix = value.prefix(4)
        let suffix = value.suffix(2)
        let hidden = String(repeating: "•", count: max(4, value.count - 6))
        return "\(prefix)\(hidden)\(suffix)"
    }

    @MainActor
    private func runProwlarrAction(_ action: ProwlarrAction) async {
        guard serviceType == .prowlarr else { return }
        guard let current = servicesStore.instance(id: instanceId) else { return }
        if isRunningServiceAction { return }
        isRunningServiceAction = true
        defer { isRunningServiceAction = false }

        let client = GenericAPIClient(serviceType: .prowlarr, instanceId: instanceId)
        await client.configure(
            url: current.url,
            fallbackUrl: current.fallbackUrl,
            apiKey: current.apiKey,
            allowSelfSigned: current.allowSelfSigned
        )

        do {
            switch action {
            case .testIndexers:
                try await client.triggerProwlarrIndexerTest()
                actionMessage = arr.indexerTestStarted
            case .syncApps:
                try await client.triggerProwlarrAppSync()
                actionMessage = arr.applicationSyncStarted
            case .healthCheck:
                try await client.triggerProwlarrHealthCheck()
                actionMessage = arr.healthCheckQueued
            }
            HapticManager.success()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                actionMessage = nil
            }
            await refreshStatus(silent: false)
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }

    @MainActor
    private func runJellyseerrBulkAction(_ action: JellyseerrBulkAction) async {
        guard serviceType == .jellyseerr else { return }
        guard let current = servicesStore.instance(id: instanceId) else { return }
        if isRunningServiceAction { return }
        isRunningServiceAction = true
        defer { isRunningServiceAction = false }

        let client = GenericAPIClient(serviceType: .jellyseerr, instanceId: instanceId)
        await client.configure(
            url: current.url,
            fallbackUrl: current.fallbackUrl,
            apiKey: current.apiKey,
            allowSelfSigned: current.allowSelfSigned
        )

        do {
            switch action {
            case .approveOldestPending:
                let title = try await client.approveOldestPendingJellyseerrRequest()
                actionMessage = messageWithOptionalDetail(arr.oldestPendingApproved, detail: title)
            case .declineOldestPending:
                let title = try await client.declineOldestPendingJellyseerrRequest()
                actionMessage = messageWithOptionalDetail(arr.oldestPendingDeclined, detail: title)
            case .runRecentScan:
                let job = try await client.triggerJellyseerrRecentScanJob()
                actionMessage = messageWithOptionalDetail(arr.recentScanStarted, detail: job)
            case .runFullScan:
                let job = try await client.triggerJellyseerrFullScanJob()
                actionMessage = messageWithOptionalDetail(arr.fullScanStarted, detail: job)
            }
            HapticManager.success()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                actionMessage = nil
            }
            await refreshStatus(silent: false)
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }

    private func messageWithOptionalDetail(_ base: String, detail: String?) -> String {
        guard let detail, !detail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return base
        }
        return "\(base): \(detail)"
    }

    @MainActor
    private func runContentSearch() async {
        guard serviceType == .jellyseerr || serviceType == .prowlarr else { return }
        guard let current = servicesStore.instance(id: instanceId) else { return }
        let term = contentSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard term.count >= 2 else {
            contentSearchResults = []
            contentSearchError = nil
            return
        }
        if isSearchingContent { return }

        isSearchingContent = true
        defer { isSearchingContent = false }
        contentSearchError = nil

        let client = GenericAPIClient(serviceType: serviceType, instanceId: instanceId)
        await client.configure(
            url: current.url,
            fallbackUrl: current.fallbackUrl,
            apiKey: current.apiKey,
            allowSelfSigned: current.allowSelfSigned
        )

        do {
            contentSearchResults = try await client.searchContent(query: term, limit: 20)
        } catch {
            contentSearchResults = []
            contentSearchError = error.localizedDescription
        }
    }

    private func localizedSearchStatus(_ status: String) -> String {
        switch status.lowercased() {
        case "in library":
            return arr.searchStatusInLibrary
        case "monitored":
            return arr.searchStatusMonitored
        case "unmonitored":
            return arr.searchStatusUnmonitored
        case "ended":
            return arr.searchStatusEnded
        case "pending":
            return arr.searchStatusPending
        case "approved":
            return arr.searchStatusApproved
        case "available":
            return arr.searchStatusAvailable
        case "processing":
            return arr.searchStatusProcessing
        default:
            return status
        }
    }

    @MainActor
    private func runContentRequest(for result: GenericLookupResult) async {
        guard serviceType == .jellyseerr else { return }
        guard let mediaType = result.requestMediaType, let mediaId = result.requestMediaId else { return }
        guard let current = servicesStore.instance(id: instanceId) else { return }
        if pendingContentRequestIds.contains(result.id) { return }

        pendingContentRequestIds.insert(result.id)
        defer { pendingContentRequestIds.remove(result.id) }

        let client = GenericAPIClient(serviceType: .jellyseerr, instanceId: instanceId)
        await client.configure(
            url: current.url,
            fallbackUrl: current.fallbackUrl,
            apiKey: current.apiKey,
            allowSelfSigned: current.allowSelfSigned
        )

        do {
            try await client.requestJellyseerrContent(mediaType: mediaType, mediaId: mediaId)
            actionMessage = messageWithOptionalDetail(arr.requestQueued, detail: result.title)
            HapticManager.success()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                actionMessage = nil
            }
            selectedSearchResult = nil
            await refreshStatus(silent: false)
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }

    @MainActor
    private func restartGluetunVPN() async {
        guard serviceType == .gluetun else { return }
        guard let current = servicesStore.instance(id: instanceId) else { return }
        if isRunningServiceAction { return }
        isRunningServiceAction = true
        defer { isRunningServiceAction = false }

        let client = GenericAPIClient(serviceType: .gluetun, instanceId: instanceId)
        await client.configure(
            url: current.url,
            fallbackUrl: current.fallbackUrl,
            apiKey: current.apiKey,
            allowSelfSigned: current.allowSelfSigned
        )

        do {
            try await client.triggerGluetunRestart()
            actionMessage = arr.vpnRestartQueued
            HapticManager.success()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                actionMessage = nil
            }
            await refreshStatus(silent: false)
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }

    @MainActor
    private func createFlaresolverrSession() async {
        guard serviceType == .flaresolverr else { return }
        guard let current = servicesStore.instance(id: instanceId) else { return }
        if isRunningServiceAction { return }
        isRunningServiceAction = true
        defer { isRunningServiceAction = false }

        let client = GenericAPIClient(serviceType: .flaresolverr, instanceId: instanceId)
        await client.configure(
            url: current.url,
            fallbackUrl: current.fallbackUrl,
            apiKey: current.apiKey,
            allowSelfSigned: current.allowSelfSigned
        )

        do {
            let sessionId = try await client.createFlaresolverrSession()
            actionMessage = "\(arr.sessionCreatedPrefix) \(sessionId)"
            HapticManager.success()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                actionMessage = nil
            }
            await refreshStatus(silent: false)
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }

    @MainActor
    private func destroyFlaresolverrSession(_ session: String) async {
        guard serviceType == .flaresolverr else { return }
        guard let current = servicesStore.instance(id: instanceId) else { return }
        if pendingSessionDeletions.contains(session) { return }
        pendingSessionDeletions.insert(session)
        defer { pendingSessionDeletions.remove(session) }

        let client = GenericAPIClient(serviceType: .flaresolverr, instanceId: instanceId)
        await client.configure(
            url: current.url,
            fallbackUrl: current.fallbackUrl,
            apiKey: current.apiKey,
            allowSelfSigned: current.allowSelfSigned
        )

        do {
            try await client.destroyFlaresolverrSession(session)
            actionMessage = arr.sessionDeleted
            HapticManager.success()
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                actionMessage = nil
            }
            await refreshStatus(silent: false)
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }

    @MainActor
    private func refreshStatus(silent: Bool) async {
        guard let current = servicesStore.instance(id: instanceId) else {
            instance = nil
            state = .error(.notConfigured)
            return
        }
        if isRefreshing {
            if !silent {
                pendingInteractiveRefresh = true
            }
            return
        }
        isRefreshing = true
        defer {
            isRefreshing = false
            if pendingInteractiveRefresh {
                pendingInteractiveRefresh = false
                Task { await refreshStatus(silent: false) }
            }
        }

        if !silent {
            state = .loading
        }

        instance = current
        if !silent || reachability == nil {
            await servicesStore.checkReachability(for: instanceId)
        }

        if silent && servicesStore.reachability(for: instanceId) == false {
            return
        }

        isLoadingDetails = true
        defer { isLoadingDetails = false }

        let client = GenericAPIClient(serviceType: serviceType, instanceId: instanceId)
        await client.configure(
            url: current.url,
            fallbackUrl: current.fallbackUrl,
            apiKey: current.apiKey,
            allowSelfSigned: current.allowSelfSigned
        )

        let shouldRefreshDetails = !silent || silentDetailsSkips >= 1
        if shouldRefreshDetails {
            self.statusDetails = await client.statusDetails()
            silentDetailsSkips = 0
        } else if silent {
            silentDetailsSkips += 1
        }

        let shouldRefreshSnapshot = !silent || silentSnapshotSkips >= 1
        if shouldRefreshSnapshot {
            self.snapshot = await client.serviceSnapshot()
            silentSnapshotSkips = 0
        } else if silent {
            silentSnapshotSkips += 1
        }
        state = .loaded(())
    }

    @MainActor
    private func handleJellyseerrRequestAction(_ requestId: Int, approve: Bool) async {
        guard let current = servicesStore.instance(id: instanceId) else { return }
        if pendingRequestActions.contains(requestId) { return }
        pendingRequestActions.insert(requestId)
        defer { pendingRequestActions.remove(requestId) }

        let client = GenericAPIClient(serviceType: .jellyseerr, instanceId: instanceId)
        await client.configure(
            url: current.url,
            fallbackUrl: current.fallbackUrl,
            apiKey: current.apiKey,
            allowSelfSigned: current.allowSelfSigned
        )

        do {
            if approve {
                try await client.approveJellyseerrRequest(requestId)
            } else {
                try await client.declineJellyseerrRequest(requestId)
            }
            actionMessage = approve ? arr.requestApproved : arr.requestDeclined
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                actionMessage = nil
            }
            HapticManager.success()
            await refreshStatus(silent: false)
        } catch {
            state = .error(.custom(error.localizedDescription))
            HapticManager.error()
        }
    }
}

private enum ProwlarrAction {
    case testIndexers
    case syncApps
    case healthCheck
}

private enum JellyseerrBulkAction {
    case approveOldestPending
    case declineOldestPending
    case runRecentScan
    case runFullScan
}

struct MediaSearchResultPresentation: Identifiable, Equatable {
    let id: String
    let title: String
    let subtitle: String?
    let supporting: String?
    let status: String?
    let posterURL: String?
    let artworkHeaders: [String: String]
    let detailsURL: String?
    let details: [String: String]
    let canRequest: Bool

    init(
        id: String,
        title: String,
        subtitle: String?,
        supporting: String?,
        status: String?,
        posterURL: String?,
        artworkHeaders: [String: String] = [:],
        detailsURL: String?,
        details: [String: String],
        canRequest: Bool
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.supporting = supporting
        self.status = status
        self.posterURL = posterURL
        self.artworkHeaders = artworkHeaders
        self.detailsURL = detailsURL
        self.details = details
        self.canRequest = canRequest
    }
}

func mediaDetailDictionary(_ pairs: [(String, String?)]) -> [String: String] {
    Dictionary(uniqueKeysWithValues: pairs.compactMap { key, value in
        guard let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return (key, value)
    })
}

struct MediaSearchResultRow: View {
    let result: MediaSearchResultPresentation

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            mediaArtwork

            VStack(alignment: .leading, spacing: 4) {
                Text(result.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2)

                if let subtitle = result.subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                        .lineLimit(1)
                }

                if let supporting = result.supporting {
                    Text(supporting)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 6) {
                if let status = result.status {
                    Text(status)
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.info)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(AppTheme.info.opacity(0.12), in: Capsule())
                        .lineLimit(1)
                }

                if result.detailsURL != nil || result.canRequest {
                    Image(systemName: "chevron.right.circle.fill")
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
        }
        .padding(10)
        .background(AppTheme.surface.opacity(0.6), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    @ViewBuilder
    private var mediaArtwork: some View {
        ServiceArtworkView(
            url: result.posterURL.flatMap(URL.init(string:)),
            headers: result.artworkHeaders,
            width: 44,
            height: 64
        ) {
            posterPlaceholder
        }
        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private var posterPlaceholder: some View {
        RoundedRectangle(cornerRadius: 8, style: .continuous)
            .fill(AppTheme.surface.opacity(0.7))
            .frame(width: 44, height: 64)
            .overlay {
                Image(systemName: "photo")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }
    }
}

struct MediaSearchResultDetailSheet: View {
    let result: MediaSearchResultPresentation
    let openDetailsTitle: String
    let requestTitle: String
    let isRequesting: Bool
    let onOpenDetails: (() -> Void)?
    let onRequest: (() -> Void)?

    @Environment(\.dismiss) private var dismiss
    @Environment(Localizer.self) private var localizer

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(alignment: .top, spacing: 14) {
                        artwork

                        VStack(alignment: .leading, spacing: 6) {
                            Text(result.title)
                                .font(.title3.bold())
                                .lineLimit(3)

                            if let subtitle = result.subtitle {
                                Text(subtitle)
                                    .font(.subheadline)
                                    .foregroundStyle(AppTheme.textSecondary)
                            }

                            if let status = result.status {
                                Text(status)
                                    .font(.caption2.bold())
                                    .foregroundStyle(AppTheme.info)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(AppTheme.info.opacity(0.12), in: Capsule())
                            }
                        }

                        Spacer(minLength: 0)
                    }

                    if let supporting = result.supporting {
                        Text(supporting)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                    }

                    if !result.details.isEmpty {
                        let detailRows = result.details.sorted { $0.key < $1.key }
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(Array(detailRows.prefix(7).enumerated()), id: \.offset) { _, detail in
                                HStack(alignment: .top, spacing: 8) {
                                    Text(detail.key)
                                        .font(.caption.weight(.semibold))
                                        .foregroundStyle(AppTheme.textMuted)
                                        .frame(width: 92, alignment: .leading)
                                    Text(detail.value)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textSecondary)
                                        .multilineTextAlignment(.leading)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(10)
                        .background(AppTheme.surface.opacity(0.4), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }

                    if let onOpenDetails {
                        Button(action: onOpenDetails) {
                            Label(openDetailsTitle, systemImage: "arrow.up.right.square")
                                .frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }

                    if let onRequest {
                        Button {
                            onRequest()
                        } label: {
                            if isRequesting {
                                ProgressView()
                                    .frame(maxWidth: .infinity)
                            } else {
                                Label(requestTitle, systemImage: "plus.circle.fill")
                                    .frame(maxWidth: .infinity)
                            }
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(isRequesting)
                    }
                }
                .padding(16)
            }
            .background(AppTheme.background)
            .navigationTitle(result.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.done) { dismiss() }
                }
            }
        }
    }

    @ViewBuilder
    private var artwork: some View {
        ServiceArtworkView(
            url: result.posterURL.flatMap(URL.init(string:)),
            headers: result.artworkHeaders,
            width: 88,
            height: 128
        ) {
            placeholderArtwork
        }
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private var placeholderArtwork: some View {
        RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(AppTheme.surface.opacity(0.7))
            .frame(width: 88, height: 128)
            .overlay {
                Image(systemName: "photo")
                    .font(.title3)
                    .foregroundStyle(AppTheme.textMuted)
            }
    }
}

struct ArrRequestConfigurationSheet: View {
    let configuration: ArrRequestConfiguration
    let titleText: String
    let messageText: String
    let qualityProfileText: String
    let rootFolderText: String
    let languageProfileText: String
    let metadataProfileText: String
    let confirmTitle: String
    let onConfirm: (ArrRequestSelection) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(Localizer.self) private var localizer

    @State private var selectedQualityProfile: ArrRequestOption?
    @State private var selectedRootFolder: ArrRequestOption?
    @State private var selectedLanguageProfile: ArrRequestOption?
    @State private var selectedMetadataProfile: ArrRequestOption?

    init(
        configuration: ArrRequestConfiguration,
        titleText: String,
        messageText: String,
        qualityProfileText: String,
        rootFolderText: String,
        languageProfileText: String,
        metadataProfileText: String,
        confirmTitle: String,
        onConfirm: @escaping (ArrRequestSelection) -> Void
    ) {
        self.configuration = configuration
        self.titleText = titleText
        self.messageText = messageText
        self.qualityProfileText = qualityProfileText
        self.rootFolderText = rootFolderText
        self.languageProfileText = languageProfileText
        self.metadataProfileText = metadataProfileText
        self.confirmTitle = confirmTitle
        self.onConfirm = onConfirm
        _selectedQualityProfile = State(initialValue: configuration.qualityProfiles.count == 1 ? configuration.qualityProfiles.first : nil)
        _selectedRootFolder = State(initialValue: configuration.rootFolders.count == 1 ? configuration.rootFolders.first : nil)
        _selectedLanguageProfile = State(initialValue: configuration.languageProfiles.count == 1 ? configuration.languageProfiles.first : nil)
        _selectedMetadataProfile = State(initialValue: configuration.metadataProfiles.count == 1 ? configuration.metadataProfiles.first : nil)
    }

    private var canConfirm: Bool {
        (configuration.qualityProfiles.isEmpty || selectedQualityProfile != nil) &&
        (configuration.rootFolders.isEmpty || selectedRootFolder != nil) &&
        (configuration.languageProfiles.isEmpty || selectedLanguageProfile != nil) &&
        (configuration.metadataProfiles.isEmpty || selectedMetadataProfile != nil)
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(messageText)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                }

                if !configuration.qualityProfiles.isEmpty {
                    Section(qualityProfileText) {
                        optionRows(configuration.qualityProfiles, selection: $selectedQualityProfile)
                    }
                }

                if !configuration.rootFolders.isEmpty {
                    Section(rootFolderText) {
                        optionRows(configuration.rootFolders, selection: $selectedRootFolder)
                    }
                }

                if !configuration.languageProfiles.isEmpty {
                    Section(languageProfileText) {
                        optionRows(configuration.languageProfiles, selection: $selectedLanguageProfile)
                    }
                }

                if !configuration.metadataProfiles.isEmpty {
                    Section(metadataProfileText) {
                        optionRows(configuration.metadataProfiles, selection: $selectedMetadataProfile)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(AppTheme.background)
            .navigationTitle(titleText)
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 0) {
                    Divider()
                    Button {
                        onConfirm(
                            ArrRequestSelection(
                                qualityProfile: selectedQualityProfile,
                                rootFolder: selectedRootFolder,
                                languageProfile: selectedLanguageProfile,
                                metadataProfile: selectedMetadataProfile
                            )
                        )
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.headline)
                            Text(confirmTitle)
                                .font(.headline.weight(.semibold))
                                .lineLimit(1)
                                .minimumScaleFactor(0.9)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.accent)
                    .disabled(!canConfirm)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)
                    .padding(.bottom, 12)
                    .background(.ultraThinMaterial)
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                }
            }
        }
    }

    @ViewBuilder
    private func optionRows(
        _ options: [ArrRequestOption],
        selection: Binding<ArrRequestOption?>
    ) -> some View {
        ForEach(options) { option in
            Button {
                selection.wrappedValue = option
            } label: {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(option.label)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.primary)
                            .multilineTextAlignment(.leading)
                        if let path = option.pathValue, path != option.label {
                            Text(path)
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                                .lineLimit(1)
                        }
                    }
                    Spacer()
                    if selection.wrappedValue?.key == option.key {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(AppTheme.info)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .contentShape(Rectangle())
                .padding(.vertical, 6)
            }
            .buttonStyle(.plain)
        }
    }
}

private struct ServiceArtworkView<Placeholder: View>: View {
    let url: URL?
    let headers: [String: String]
    let width: CGFloat
    let height: CGFloat
    @ViewBuilder let placeholder: () -> Placeholder

    @StateObject private var loader = ServiceArtworkLoader()

    var body: some View {
        Group {
            if let image = loader.image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                placeholder()
            }
        }
        .frame(width: width, height: height)
        .task(id: cacheIdentifier) {
            await loader.load(url: url, headers: headers)
        }
    }

    private var cacheIdentifier: String {
        let headerKey = headers.keys.sorted().map { "\($0)=\(headers[$0] ?? "")" }.joined(separator: "&")
        return "\(url?.absoluteString ?? "nil")|\(headerKey)"
    }
}

@MainActor
private final class ServiceArtworkLoader: ObservableObject {
    @Published private(set) var image: UIImage?

    private static let cache = NSCache<NSString, UIImage>()

    func load(url: URL?, headers: [String: String]) async {
        guard let url else {
            image = nil
            return
        }

        let headerKey = headers.keys.sorted().map { "\($0)=\(headers[$0] ?? "")" }.joined(separator: "&")
        let cacheKey = NSString(string: "\(url.absoluteString)|\(headerKey)")
        if let cached = Self.cache.object(forKey: cacheKey) {
            image = cached
            return
        }

        do {
            let data = try await BaseNetworkEngine.imageData(from: url, headers: headers)
            guard let decoded = UIImage(data: data) else {
                image = nil
                return
            }
            Self.cache.setObject(decoded, forKey: cacheKey)
            image = decoded
        } catch {
            image = nil
        }
    }
}
