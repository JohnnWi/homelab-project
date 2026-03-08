import SwiftUI
import Charts

// Maps to app/pihole/index.tsx

struct PiHoleDashboard: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var stats: PiholeStats?
    @State private var blocking: PiholeBlockingStatus?
    @State private var topBlocked: [PiholeTopItem] = []
    @State private var topDomains: [PiholeTopItem] = []
    @State private var topClients: [PiholeTopClient] = []
    @State private var history: [PiholeHistoryEntry] = []
    @State private var isLoading = true
    @State private var error: Error?
    @State private var isToggling = false
    @State private var toggleError: String?
    @State private var showToggleError = false
    @State private var showDisableOptions = false

    private let piholeColor = ServiceType.pihole.colors.primary
    private var isBlocking: Bool { blocking?.isEnabled ?? false }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .pihole,
            isLoading: isLoading && stats == nil,
            error: error,
            onRefresh: fetchAll
        ) {
            // Blocking toggle card
            blockingCard

            if let stats {
                // Stats overview
                statsOverview(stats)

                // Query activity bars
                queryActivitySection(stats)

                // Query history chart
                if !history.isEmpty {
                    queryHistoryChart
                }

                // Gravity info
                gravitySection(stats)
                
                // Domain Management Link
                NavigationLink(destination: PiholeDomainListView()) {
                    HStack {
                        Image(systemName: "list.bullet.rectangle.portrait.fill")
                            .font(.body)
                            .foregroundStyle(piholeColor)
                            .frame(width: 36, height: 36)
                            .background(piholeColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                        
                        Text(localizer.t.piholeDomainManagement)
                            .font(.headline)
                        
                        Spacer()
                        
                        Image(systemName: "chevron.right")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Color(.tertiaryLabel))
                    }
                    .padding(16)
                    .glassCard()
                }
                .buttonStyle(.plain)
            }

            // Top blocked
            if !topBlocked.isEmpty {
                topListSection(
                    title: localizer.t.piholeTopBlocked,
                    items: topBlocked,
                    rankColor: piholeColor
                )
            }

            // Top domains
            if !topDomains.isEmpty {
                topDomainsSection
            }

            // Top clients
            if !topClients.isEmpty {
                topClientsSection
            }
        }
        .navigationTitle("Pi-hole")
        .task { await fetchAll() }
        .alert("Error", isPresented: $showToggleError) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(toggleError ?? "Unknown error")
        }
        .confirmationDialog(localizer.t.piholeDisableDesc, isPresented: $showDisableOptions, titleVisibility: .visible) {
            Button(localizer.t.piholeDisablePermanently, role: .destructive) { handleToggle(timer: nil) }
            Button(localizer.t.piholeDisable1h) { handleToggle(timer: 3600) }
            Button(localizer.t.piholeDisable5m) { handleToggle(timer: 300) }
            Button(localizer.t.piholeDisable1m) { handleToggle(timer: 60) }
            Button(localizer.t.cancel, role: .cancel) { }
        }
    }

    // MARK: - Blocking Card

    private var blockingCard: some View {
        Button {
            if isBlocking {
                showDisableOptions = true
            } else {
                handleToggle(timer: nil)
            }
        } label: {
            HStack(spacing: 14) {
                Image(systemName: isBlocking ? "shield.fill" : "shield.slash.fill")
                    .font(.title2)
                    .foregroundStyle(isBlocking ? AppTheme.running : AppTheme.stopped)
                    .frame(width: 56, height: 56)
                    .background(
                        (isBlocking ? AppTheme.running : AppTheme.stopped).opacity(0.1),
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(localizer.t.piholeBlocking)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(isBlocking ? localizer.t.piholeEnabled : localizer.t.piholeDisabled)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(isBlocking ? AppTheme.running : AppTheme.stopped)
                    Text(isBlocking ? localizer.t.piholeBlockingDesc : localizer.t.piholeDisableDesc)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(2)
                }

                Spacer()

                Text(isBlocking ? "ON" : "OFF")
                    .font(.caption.bold())
                    .foregroundStyle(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(
                        isBlocking ? AppTheme.running : AppTheme.stopped,
                        in: Capsule()
                    )
            }
            .padding(18)
            .glassCard(tint: (isBlocking ? AppTheme.running : AppTheme.stopped).opacity(0.05))
        }
        .buttonStyle(.plain)
        .disabled(isToggling)
    }

    // MARK: - Stats Overview

    private func statsOverview(_ stats: PiholeStats) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.piholeOverview)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                statCard(icon: "magnifyingglass", iconBg: piholeColor, value: Formatters.formatNumber(stats.queries.total), label: localizer.t.piholeTotalQueries)
                statCard(icon: "hand.raised.fill", iconBg: AppTheme.stopped, value: Formatters.formatNumber(stats.queries.blocked), label: localizer.t.piholeBlockedQueries)
                statCard(icon: "chart.bar.fill", iconBg: AppTheme.warning, value: String(format: "%.1f%%", stats.queries.percent_blocked), label: localizer.t.piholePercentBlocked)
                statCard(icon: "globe", iconBg: AppTheme.info, value: Formatters.formatNumber(stats.queries.unique_domains), label: localizer.t.piholeUniqueDomains)
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
                .font(.caption2.weight(.medium))
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .glassCard()
    }

    // MARK: - Query Activity

    private func queryActivitySection(_ stats: PiholeStats) -> some View {
        let total = stats.queries.total
        let blocked = stats.queries.blocked
        let cached = stats.queries.cached
        let forwarded = stats.queries.forwarded
        let maxQuery = max(total, 1)

        return VStack(alignment: .leading, spacing: 10) {
            Text(localizer.t.piholeQueryActivity)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)

            VStack(spacing: 10) {
                activityBar(label: localizer.t.piholeBlockedQueries, value: blocked, max: maxQuery, color: AppTheme.stopped)
                activityBar(label: localizer.t.piholeCached, value: cached, max: maxQuery, color: AppTheme.running)
                activityBar(label: localizer.t.piholeForwarded, value: forwarded, max: maxQuery, color: AppTheme.info)
            }
            .padding(16)
            .glassCard()
        }
    }

    private func activityBar(label: String, value: Int, max: Int, color: Color) -> some View {
        VStack(spacing: 4) {
            HStack {
                HStack(spacing: 8) {
                    Circle().fill(color).frame(width: 8, height: 8)
                    Text(label)
                        .font(.caption.weight(.medium))
                }
                Spacer()
                Text(Formatters.formatNumber(value))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textSecondary)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color(.tertiarySystemFill))
                        .frame(height: 6)
                    RoundedRectangle(cornerRadius: 3)
                        .fill(color)
                        .frame(width: geo.size.width * CGFloat(value) / CGFloat(max), height: 6)
                }
            }
            .frame(height: 6)
        }
    }

    // MARK: - Query History Chart (Swift Charts)

    private var queryHistoryChart: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.piholeQueriesOverTime)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)

            VStack(spacing: 12) {
                let recent = Array(history.suffix(24))
                Chart {
                    ForEach(Array(recent.enumerated()), id: \.offset) { idx, entry in
                        let allowed = entry.total - entry.blocked
                        BarMark(
                            x: .value("Time", idx),
                            y: .value("Allowed", allowed)
                        )
                        .foregroundStyle(AppTheme.running.opacity(0.7))

                        BarMark(
                            x: .value("Time", idx),
                            y: .value("Blocked", entry.blocked)
                        )
                        .foregroundStyle(piholeColor.opacity(0.8))
                    }
                }
                .chartXAxis(.hidden)
                .chartYAxis(.hidden)
                .frame(height: 100)

                // Legend
                HStack(spacing: 16) {
                    Spacer()
                    HStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 2).fill(AppTheme.running.opacity(0.7)).frame(width: 8, height: 8)
                        Text("Allowed")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    HStack(spacing: 6) {
                        RoundedRectangle(cornerRadius: 2).fill(piholeColor.opacity(0.8)).frame(width: 8, height: 8)
                        Text("Blocked")
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

    // MARK: - Gravity

    private func gravitySection(_ stats: PiholeStats) -> some View {
        HStack(spacing: 14) {
            Image(systemName: "cylinder.fill")
                .font(.body)
                .foregroundStyle(piholeColor)
                .frame(width: 44, height: 44)
                .background(piholeColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(localizer.t.piholeGravity)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                Text(Formatters.formatNumber(stats.gravity.domains_being_blocked))
                    .font(.title3.bold())
            }

            Spacer()

            if stats.gravity.last_update > 0 {
                HStack(spacing: 4) {
                    Image(systemName: "clock")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                    Text(Formatters.formatUnixDate(stats.gravity.last_update))
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    // MARK: - Top Lists

    private func topListSection(title: String, items: [PiholeTopItem], rankColor: Color) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)

            VStack(spacing: 0) {
                ForEach(Array(items.enumerated()), id: \.element.id) { idx, item in
                    HStack(spacing: 12) {
                        Text("\(idx + 1)")
                            .font(.caption2.bold())
                            .foregroundStyle(rankColor)
                            .frame(width: 28, height: 28)
                            .background(rankColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                        Text(item.domain)
                            .font(.caption.weight(.medium))
                            .lineLimit(1)
                        Spacer()
                        Text(Formatters.formatNumber(item.count))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    if idx < items.count - 1 { Divider().padding(.leading, 56) }
                }
            }
            .glassCard()
        }
    }

    private var topDomainsSection: some View {
        let maxCount = max(1, topDomains.map(\.count).max() ?? 1)
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.piholeTopDomains)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                Text("\(Formatters.formatNumber(topDomains.reduce(0) { $0 + $1.count })) totali")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(AppTheme.textSecondary)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color(.tertiarySystemFill), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }

            VStack(spacing: 0) {
                ForEach(Array(topDomains.enumerated()), id: \.element.id) { idx, item in
                    HStack(spacing: 12) {
                        Text("\(idx + 1)")
                            .font(.caption2.bold())
                            .foregroundStyle(AppTheme.running)
                            .frame(width: 28, height: 28)
                            .background(AppTheme.running.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

                        ZStack(alignment: .leading) {
                            GeometryReader { geo in
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(AppTheme.running.opacity(0.1))
                                    .frame(width: geo.size.width * CGFloat(item.count) / CGFloat(maxCount))
                            }
                            Text(item.domain)
                                .font(.caption.weight(.medium))
                                .lineLimit(1)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 2)
                        }

                        Text(Formatters.formatNumber(item.count))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    if idx < topDomains.count - 1 { Divider().padding(.leading, 56) }
                }
            }
            .glassCard()
        }
    }

    private var topClientsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.piholeClients)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)

            VStack(spacing: 0) {
                ForEach(Array(topClients.enumerated()), id: \.element.id) { idx, client in
                    HStack(spacing: 12) {
                        Image(systemName: "person.fill")
                            .font(.caption)
                            .foregroundStyle(AppTheme.info)
                            .frame(width: 28, height: 28)
                            .background(AppTheme.info.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

                        VStack(alignment: .leading, spacing: 1) {
                            Text(client.name.isEmpty ? client.ip : client.name)
                                .font(.caption.weight(.medium))
                                .lineLimit(1)
                            if !client.name.isEmpty {
                                Text(client.ip)
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }
                        Spacer()
                        Text(Formatters.formatNumber(client.count))
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    if idx < topClients.count - 1 { Divider().padding(.leading, 56) }
                }
            }
            .glassCard()
        }
    }

    // MARK: - Toggle Blocking

    private func handleToggle(timer: Int? = nil) {
        HapticManager.medium()
        isToggling = true
        Task {
            do {
                if let timer = timer {
                    try await servicesStore.piholeClient.setBlocking(enabled: false, timer: timer)
                } else {
                    try await servicesStore.piholeClient.setBlocking(enabled: !isBlocking)
                }
                HapticManager.success()
                blocking = try? await servicesStore.piholeClient.getBlockingStatus()
                stats = try? await servicesStore.piholeClient.getStats()
            } catch {
                HapticManager.error()
                toggleError = error.localizedDescription
                showToggleError = true
            }
            isToggling = false
        }
    }

    // MARK: - Data Fetching

    private func fetchAll() async {
        isLoading = true
        error = nil
        defer { isLoading = false }

        do {
            async let s = servicesStore.piholeClient.getStats()
            async let b = servicesStore.piholeClient.getBlockingStatus()
            stats = try await s
            blocking = try await b
        } catch {
            self.error = error
            return
        }

        // Non-critical fetches in parallel, assign results on main actor
        async let tb = { (try? await servicesStore.piholeClient.getTopBlocked(count: 8)) ?? [] }()
        async let td = { (try? await servicesStore.piholeClient.getTopDomains(count: 10)) ?? [] }()
        async let tc = { (try? await servicesStore.piholeClient.getTopClients(count: 10)) ?? [] }()
        async let qh = { try? await servicesStore.piholeClient.getQueryHistory() }()

        topBlocked = await tb
        topDomains = await td
        topClients = await tc
        if let h = await qh { history = h.history }
    }
}
