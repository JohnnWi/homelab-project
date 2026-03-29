import SwiftUI

struct JellyfinDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var systemInfo: JellyfinSystemInfo?
    @State private var sessions: [JellyfinSession] = []
    @State private var libraries: [JellyfinLibrary] = []
    @State private var itemCounts: JellyfinItemCounts?

    private let serviceColor = ServiceType.jellyfin.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .jellyfin,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await load(force: true) }
        ) {
            if let info = systemInfo {
                heroCard(info)
            }

            if !sessions.isEmpty {
                sessionsCard
            }

            if let counts = itemCounts {
                libraryCard(counts)
            }

            if !libraries.isEmpty {
                libraryListCard
            }
        }
        .navigationTitle("Jellyfin")
        .task(id: selectedInstanceId) {
            await load(force: true)
        }
    }

    private func heroCard(_ info: JellyfinSystemInfo) -> some View {
        GlassCard(tint: serviceColor.opacity(colorScheme == .light ? 0.14 : 0.10)) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    ServiceIconView(type: .jellyfin, size: 34)
                        .frame(width: 56, height: 56)
                        .background(serviceColor.opacity(0.13), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    VStack(alignment: .leading, spacing: 4) {
                        Text(info.serverName)
                            .font(.headline.bold())
                            .lineLimit(1)
                        Text("v\(info.version)")
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }

                    Spacer()

                    if info.hasUpdateAvailable {
                        Text("Update")
                            .font(.caption2.bold())
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(AppTheme.warning, in: Capsule())
                    }
                }

                if let os = info.operatingSystem {
                    HStack(spacing: 6) {
                        Image(systemName: "desktopcomputer")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(os)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }
            }
            .padding(14)
        }
    }

    private var sessionsCard: some View {
        GlassCard(tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Active Sessions")
                        .font(.subheadline.weight(.semibold))
                    Spacer()
                    Text("\(sessions.count)")
                        .font(.caption.bold())
                        .foregroundStyle(serviceColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(serviceColor.opacity(0.14), in: Capsule())
                }

                let activeSessions = sessions.filter { $0.nowPlayingItem != nil }
                let idleSessions = sessions.filter { $0.nowPlayingItem == nil }

                if !activeSessions.isEmpty {
                    ForEach(activeSessions, id: \.id) { session in
                        sessionRow(session, isPlaying: true)
                    }
                }

                if !idleSessions.isEmpty {
                    ForEach(idleSessions.prefix(5), id: \.id) { session in
                        sessionRow(session, isPlaying: false)
                    }
                }
            }
            .padding(14)
        }
    }

    private func sessionRow(_ session: JellyfinSession, isPlaying: Bool) -> some View {
        HStack(spacing: 10) {
            Image(systemName: isPlaying ? "play.circle.fill" : "person.circle")
                .font(.subheadline)
                .foregroundStyle(isPlaying ? serviceColor : AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 2) {
                Text(session.userName ?? "Unknown")
                    .font(.caption.weight(.semibold))
                    .lineLimit(1)
                if let item = session.nowPlayingItem {
                    Text(item)
                        .font(.caption2)
                        .foregroundStyle(serviceColor)
                        .lineLimit(1)
                } else {
                    Text([session.client, session.deviceName].compactMap { $0 }.joined(separator: " - "))
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }
            Spacer()
        }
    }

    private func libraryCard(_ counts: JellyfinItemCounts) -> some View {
        LazyVGrid(columns: twoColumnGrid, spacing: 10) {
            GlassStatCard(
                title: "Movies",
                value: Formatters.formatNumber(counts.movieCount),
                icon: "film.fill",
                iconColor: serviceColor
            )
            GlassStatCard(
                title: "Series",
                value: Formatters.formatNumber(counts.seriesCount),
                icon: "tv.fill",
                iconColor: Color(hex: "#F59E0B")
            )
        }
    }

    private var libraryListCard: some View {
        GlassCard(tint: AppTheme.surface.opacity(colorScheme == .light ? 0.65 : 0.45)) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Libraries")
                    .font(.subheadline.weight(.semibold))

                ForEach(libraries, id: \.id) { library in
                    HStack(spacing: 10) {
                        Image(systemName: iconForCollectionType(library.collectionType))
                            .font(.caption)
                            .foregroundStyle(serviceColor)
                            .frame(width: 24, height: 24)
                            .background(serviceColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 6, style: .continuous))

                        Text(library.name)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)

                        Spacer()

                        if let type = library.collectionType {
                            Text(type)
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }
                }
            }
            .padding(14)
        }
    }

    private func iconForCollectionType(_ type: String?) -> String {
        switch type?.lowercased() {
        case "movies": return "film.fill"
        case "tvshows": return "tv.fill"
        case "music": return "music.note.list"
        case "books": return "book.fill"
        case "photos": return "photo.fill"
        default: return "folder.fill"
        }
    }

    private func load(force: Bool) async {
        if state.isLoading { return }
        if case .loaded = state, !force { return }

        state = .loading
        do {
            guard let client = await servicesStore.jellyfinClient(instanceId: selectedInstanceId) else {
                state = .error(.notConfigured)
                return
            }

            async let infoTask = client.getSystemInfo()
            async let sessionsTask = client.getSessions()
            async let librariesTask = client.getLibraries()
            async let countsTask = client.getItemCounts()

            systemInfo = try await infoTask
            sessions = try await sessionsTask
            libraries = try await librariesTask
            itemCounts = try await countsTask

            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}
