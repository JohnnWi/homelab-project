import SwiftUI

// Maps to app/gitea/index.tsx — user profile, heatmap, orgs, repo list

private let giteaColor = Color(hex: "#609926")

private let langColors: [String: Color] = [
    "Go": Color(hex: "#00ADD8"),
    "JavaScript": Color(hex: "#F7DF1E"),
    "TypeScript": Color(hex: "#3178C6"),
    "Python": Color(hex: "#3776AB"),
    "Rust": Color(hex: "#DEA584"),
    "Java": Color(hex: "#B07219"),
    "C#": Color(hex: "#178600"),
    "C++": Color(hex: "#F34B7D"),
    "Ruby": Color(hex: "#CC342D"),
    "PHP": Color(hex: "#777BB4"),
    "Shell": Color(hex: "#89E051"),
    "Dockerfile": Color(hex: "#384D54"),
    "HTML": Color(hex: "#E34C26"),
    "CSS": Color(hex: "#563D7C"),
    "Dart": Color(hex: "#00B4AB"),
    "Swift": Color(hex: "#F05138"),
    "Kotlin": Color(hex: "#A97BFF"),
]

private let heatmapColorsDark: [Color] = [
    Color(hex: "#1C222B"), Color(hex: "#0F4A2D"), Color(hex: "#0D6B39"), Color(hex: "#2A8F4E"), Color(hex: "#3BBF64"),
]
private let heatmapColorsLight: [Color] = [
    Color(hex: "#EBEDF0"), Color(hex: "#9BE9A8"), Color(hex: "#40C463"), Color(hex: "#30A14E"), Color(hex: "#216E39"),
]

struct GiteaDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var user: GiteaUser?
    @State private var repos: [GiteaRepo] = []
    @State private var orgs: [GiteaOrg] = []
    @State private var heatmap: [GiteaHeatmapItem] = []
    @State private var state: LoadableState<Void> = .idle
    @State private var sortOrder: SortOrder = .recent
    @State private var totalBranches: Int = 0
    @State private var hasRestoredCache = false

    private struct CacheEntry {
        let user: GiteaUser?
        let repos: [GiteaRepo]
        let orgs: [GiteaOrg]
        let heatmap: [GiteaHeatmapItem]
        let totalBranches: Int
        let sortOrder: SortOrder
        let lastFetch: Date
    }

    private static var cache: [UUID: CacheEntry] = [:]
    private static let cacheTtl: TimeInterval = 120

    private enum SortOrder {
        case recent, alpha
    }

    private var sortedRepos: [GiteaRepo] {
        switch sortOrder {
        case .recent:
            return repos.sorted { $0.updated_at > $1.updated_at }
        case .alpha:
            return repos.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
    }

    private var repoStats: (total: Int, stars: Int) {
        (repos.count, repos.reduce(0) { $0 + $1.stars_count })
    }

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .gitea,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchAll(force: true) }
        ) {
            instancePicker

            if let user {
                userCard(user)
            }

            if !heatmap.isEmpty {
                heatmapSection
            }

            if !orgs.isEmpty {
                orgsSection
            }

            reposSection
        }
        .navigationTitle(localizer.t.serviceGitea)
        .navigationDestination(for: GiteaRepoRoute.self) { route in
            GiteaRepoDetail(instanceId: route.instanceId, owner: route.owner, repoName: route.repoName, initialPath: route.path, isFile: route.isFile, initialBranch: route.branch)
        }
        .task(id: selectedInstanceId) { await loadAndRefreshIfNeeded() }
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .gitea)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances.sentenceCased())
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .gitea)
                            user = nil
                            repos = []
                            orgs = []
                            heatmap = []
                            totalBranches = 0
                            hasRestoredCache = false
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? giteaColor : AppTheme.textMuted.opacity(0.4))
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
                            .glassCard(tint: instance.id == selectedInstanceId ? giteaColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - User Card

    private func userCard(_ user: GiteaUser) -> some View {
        VStack(spacing: 14) {
            HStack(spacing: 14) {
                Image(systemName: "person.fill")
                    .font(.title2)
                    .foregroundStyle(giteaColor)
                    .frame(width: 52, height: 52)
                    .background(giteaColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(user.full_name.isEmpty ? user.login : user.full_name)
                        .font(.title3.bold())
                    Text("@\(user.login)")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                }

                Spacer()
            }

            Divider()

            HStack(spacing: 8) {
                MiniStatInline(icon: "folder", iconColor: AppTheme.running, value: "\(repoStats.total)", label: localizer.t.giteaRepos)
                MiniStatInline(icon: "star", iconColor: AppTheme.warning, value: "\(repoStats.stars)", label: localizer.t.giteaStars)
                MiniStatInline(icon: "arrow.triangle.branch", iconColor: AppTheme.info, value: "\(totalBranches)", label: localizer.t.giteaBranches)
            }
        }
        .padding(18)
        .glassCard()
    }

    // MARK: - Heatmap

    private var heatmapSection: some View {
        let grid = buildHeatmapGrid()
        let colors = colorScheme == .dark ? heatmapColorsDark : heatmapColorsLight

        return VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.giteaContributions.sentenceCased())
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 12) {
                let cellSize: CGFloat = 12
                let cellSpacing: CGFloat = 2
                let gridWidth = CGFloat(grid.count) * cellSize + CGFloat(max(grid.count - 1, 0)) * cellSpacing
                let gridHeight = CGFloat(7) * cellSize + CGFloat(6) * cellSpacing
                let legendWidth: CGFloat = 34

                GeometryReader { proxy in
                    let availableWidth = max(proxy.size.width - legendWidth - 12, 0)
                    HStack(alignment: .top, spacing: 12) {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(alignment: .top, spacing: cellSpacing) {
                                ForEach(Array(grid.enumerated()), id: \.offset) { _, week in
                                    VStack(spacing: cellSpacing) {
                                        ForEach(Array(week.enumerated()), id: \.offset) { _, day in
                                            RoundedRectangle(cornerRadius: 2)
                                                .fill(colors[day.level])
                                                .frame(width: cellSize, height: cellSize)
                                        }
                                    }
                                }
                            }
                            .frame(width: max(gridWidth, availableWidth), alignment: .center)
                        }
                        .frame(height: gridHeight)
                        .frame(maxWidth: availableWidth, alignment: .leading)

                        VStack(spacing: 4) {
                            Text(localizer.t.giteaLessActive)
                            ForEach(Array(colors.enumerated()), id: \.offset) { _, color in
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(color)
                                    .frame(width: 12, height: 12)
                            }
                            Text(localizer.t.giteaMoreActive)
                        }
                        .font(.system(size: 10))
                        .foregroundStyle(AppTheme.textMuted)
                        .frame(width: legendWidth)
                    }
                }
                .frame(height: gridHeight)
                .padding(.top, -4)
            }
            .padding(16)
            .glassCard()
        }
    }

    // MARK: - Orgs

    private var orgsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.giteaOrgs.sentenceCased())
                .font(.caption.bold())
                .foregroundStyle(AppTheme.textMuted)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(orgs) { org in
                        HStack(spacing: 6) {
                            Image(systemName: "building.2")
                                .font(.caption)
                                .foregroundStyle(giteaColor)
                                .accessibilityHidden(true)
                            Text(org.username)
                                .font(.subheadline.bold())
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .glassCard()
                    }
                }
            }
        }
    }

    // MARK: - Repos Section

    private var reposSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(localizer.t.giteaRepos) (\(repos.count))".sentenceCased())
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textMuted)

                Spacer()

                Button {
                    HapticManager.light()
                    sortOrder = sortOrder == .recent ? .alpha : .recent
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: sortOrder == .recent ? "clock" : "textformat.abc")
                            .font(.caption2)
                            .accessibilityHidden(true)
                        Text(sortOrder == .recent ? localizer.t.giteaSortRecent : localizer.t.giteaSortAlpha)
                            .font(.caption2.bold())
                    }
                    .foregroundStyle(AppTheme.textMuted)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
            }

            if sortedRepos.isEmpty && !state.isLoading {
                VStack(spacing: 12) {
                    Image(systemName: "arrow.triangle.branch")
                        .font(.system(size: 40))
                        .foregroundStyle(AppTheme.textMuted)
                        .accessibilityHidden(true)
                    Text(localizer.t.giteaNoRepos)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 40)
            } else {
                ForEach(sortedRepos) { repo in
                    NavigationLink(value: GiteaRepoRoute(instanceId: selectedInstanceId, owner: repo.owner.login, repoName: repo.name)) {
                        RepoCard(repo: repo, t: localizer.translations)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Heatmap Builder

    private func buildHeatmapGrid() -> [[(level: Int, count: Int)]] {
        let contributionMap = Dictionary(grouping: heatmap) { item -> String in
            let date = item.date
            let cal = Calendar.current
            return "\(cal.component(.year, from: date))-\(cal.component(.month, from: date))-\(cal.component(.day, from: date))"
        }.mapValues { $0.reduce(0) { $0 + $1.contributions } }

        let maxContrib = max(1, contributionMap.values.max() ?? 1)
        let today = Date()
        let cal = Calendar.current
        let dayOfWeek = cal.component(.weekday, from: today) - 1 // 0=Sunday
        let weeksToShow = 20
        let totalDays = weeksToShow * 7 + dayOfWeek + 1
        let startDate = cal.date(byAdding: .day, value: -(totalDays - 1), to: today)!

        var weeks: [[(level: Int, count: Int)]] = []
        var currentWeek: [(level: Int, count: Int)] = []

        for i in 0..<totalDays {
            let d = cal.date(byAdding: .day, value: i, to: startDate)!
            let key = "\(cal.component(.year, from: d))-\(cal.component(.month, from: d))-\(cal.component(.day, from: d))"
            let count = contributionMap[key] ?? 0

            var level = 0
            if count > 0 {
                let ratio = Double(count) / Double(maxContrib)
                if ratio <= 0.25 { level = 1 }
                else if ratio <= 0.5 { level = 2 }
                else if ratio <= 0.75 { level = 3 }
                else { level = 4 }
            }

            currentWeek.append((level: level, count: count))
            if currentWeek.count == 7 {
                weeks.append(currentWeek)
                currentWeek = []
            }
        }
        if !currentWeek.isEmpty {
            weeks.append(currentWeek)
        }

        return weeks
    }

    // MARK: - Fetch

    private func loadAndRefreshIfNeeded() async {
        restoreCacheIfNeeded()

        let shouldRefresh: Bool = {
            guard let cached = Self.cache[selectedInstanceId] else { return true }
            return Date().timeIntervalSince(cached.lastFetch) > Self.cacheTtl
        }()

        if shouldRefresh {
            await fetchAll(force: false)
        }
    }

    private func restoreCacheIfNeeded() {
        guard !hasRestoredCache, let cached = Self.cache[selectedInstanceId] else { return }
        user = cached.user
        repos = cached.repos
        orgs = cached.orgs
        heatmap = cached.heatmap
        totalBranches = cached.totalBranches
        sortOrder = cached.sortOrder
        state = .loaded(())
        hasRestoredCache = true
    }

    private func updateCache() {
        Self.cache[selectedInstanceId] = CacheEntry(
            user: user,
            repos: repos,
            orgs: orgs,
            heatmap: heatmap,
            totalBranches: totalBranches,
            sortOrder: sortOrder,
            lastFetch: Date()
        )
    }

    private func fetchAll(force: Bool) async {
        if !force, let cached = Self.cache[selectedInstanceId], Date().timeIntervalSince(cached.lastFetch) <= Self.cacheTtl {
            return
        }
        let hasContent = user != nil || !repos.isEmpty || !orgs.isEmpty || !heatmap.isEmpty
        if !hasContent {
            state = .loading
        }

        guard let client = await servicesStore.giteaClient(instanceId: selectedInstanceId) else {
            state = .error(.notConfigured)
            return
        }
        async let u = { try? await client.getCurrentUser() }()
        async let r = { try? await client.getUserRepos(page: 1, limit: 30) }()
        async let o = { try? await client.getOrgs() }()

        let fetchedUser = await u
        let fetchedRepos = await r
        let fetchedOrgs = await o

        if let fetchedUser { user = fetchedUser }
        if let fetchedOrgs { orgs = fetchedOrgs }
        if let fetchedRepos {
            repos = fetchedRepos
            Task {
                var counts = 0
                await withTaskGroup(of: Int.self) { group in
                    for repo in fetchedRepos {
                        group.addTask {
                            do {
                                let branches = try await client.getRepoBranches(owner: repo.owner.login, repo: repo.name)
                                return branches.count
                            } catch {
                                return 0
                            }
                        }
                    }
                    for await c in group { counts += c }
                }
                totalBranches = counts
                updateCache()
            }
        }

        // Fetch heatmap after we have user
        if let login = (fetchedUser ?? user)?.login {
            if let h = try? await client.getUserHeatmap(username: login) {
                heatmap = h
            }
        }

        if fetchedUser == nil && user == nil {
            state = .error(.custom(localizer.t.error))
        } else {
            state = .loaded(())
        }
        updateCache()
    }
}

// MARK: - Route

struct GiteaRepoRoute: Hashable {
    let instanceId: UUID
    let owner: String
    let repoName: String
    var path: String = ""
    var isFile: Bool = false
    var branch: String? = nil
}

// MARK: - Mini Stat Inline

private struct MiniStatInline: View {
    let icon: String
    let iconColor: Color
    let value: String
    let label: String

    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(iconColor)
                .accessibilityHidden(true)
            Text(value)
                .font(.title3.bold())
            Text(label.sentenceCased())
                .font(.caption2.weight(.medium))
                .foregroundStyle(AppTheme.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .padding(.horizontal, 6)
        .glassCard(cornerRadius: 14, tint: iconColor.opacity(0.12))
    }
}

// MARK: - Repo Card

private struct RepoCard: View {
    let repo: GiteaRepo
    let t: Translations

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Header
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: repo.isPrivate ? "lock" : "lock.open")
                        .font(.caption)
                        .foregroundStyle(repo.isPrivate ? AppTheme.warning : AppTheme.textMuted)
                        .accessibilityHidden(true)
                    Text(repo.name)
                        .font(.subheadline.bold())
                        .foregroundStyle(giteaColor)
                        .lineLimit(1)
                }

                Spacer()

                HStack(spacing: 6) {
                    if repo.fork {
                        HStack(spacing: 3) {
                            Image(systemName: "tuningfork")
                                .font(.system(size: 10))
                                .foregroundStyle(AppTheme.textMuted)
                                .accessibilityHidden(true)
                            Text(t.giteaFork)
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundStyle(AppTheme.textMuted)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                    }
                    Image(systemName: "chevron.right")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .accessibilityHidden(true)
                }
            }

            // Description
            if !repo.description.isEmpty {
                Text(repo.description)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(2)
                    .padding(.bottom, 4)
            }

            // Footer
            HStack(spacing: 12) {
                if !repo.language.isEmpty {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(langColors[repo.language] ?? AppTheme.textMuted)
                            .frame(width: 8, height: 8)
                        Text(repo.language)
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }
                HStack(spacing: 3) {
                    Image(systemName: "star")
                        .font(.system(size: 12))
                        .foregroundStyle(AppTheme.warning)
                        .accessibilityHidden(true)
                    Text("\(repo.stars_count)")
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
                HStack(spacing: 3) {
                    Image(systemName: "tuningfork")
                        .font(.system(size: 12))
                        .foregroundStyle(AppTheme.textMuted)
                        .accessibilityHidden(true)
                    Text("\(repo.forks_count)")
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
                Spacer()
                Text(formatRelativeDate(repo.updated_at, t: t))
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
        .padding(16)
        .glassCard()
    }
}

// MARK: - Date Helper

private func formatRelativeDate(_ dateString: String, t: Translations) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    var date = formatter.date(from: dateString)
    if date == nil {
        formatter.formatOptions = [.withInternetDateTime]
        date = formatter.date(from: dateString)
    }
    guard let d = date else { return dateString }

    let diff = Date().timeIntervalSince(d)
    let days = Int(diff / 86400)
    if days == 0 { return t.timeToday }
    if days == 1 { return t.timeDayAgo }
    if days < 30 { return String(format: t.timeDaysAgo, days) }
    let months = days / 30
    return String(format: t.timeMonthsAgo, months)
}
