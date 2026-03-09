import SwiftUI

struct EditBookmarkView: View {
    @Environment(\.dismiss) var dismiss
    @Environment(Localizer.self) private var localizer

    let categories: [BookmarkCategory]
    let editingBookmark: Bookmark?
    let preselectedCategory: UUID?
    let onSave: (String, String?, String, UUID, BookmarkIconType, String, [String]) -> Void

    @State private var title: String = ""
    @State private var description: String = ""
    @State private var url: String = ""
    @State private var tagsText: String = ""
    @State private var categoryId: UUID?

    @State private var iconType: BookmarkIconType = .favicon
    @State private var selfhstService: String = ""
    @State private var customImageUrl: String = ""

    init(categories: [BookmarkCategory], editingBookmark: Bookmark? = nil, preselectedCategory: UUID? = nil, onSave: @escaping (String, String?, String, UUID, BookmarkIconType, String, [String]) -> Void) {
        self.categories = categories
        self.editingBookmark = editingBookmark
        self.preselectedCategory = preselectedCategory
        self.onSave = onSave

        let initialIconType = editingBookmark?.iconType ?? .favicon
        let initialIconValue = editingBookmark?.iconValue ?? ""

        _title = State(initialValue: editingBookmark?.title ?? "")
        _description = State(initialValue: editingBookmark?.description ?? "")
        _url = State(initialValue: editingBookmark?.url ?? "https://")
        _tagsText = State(initialValue: editingBookmark?.tags.joined(separator: ", ") ?? "")
        _categoryId = State(initialValue: editingBookmark?.categoryId ?? preselectedCategory ?? categories.first?.id)
        _iconType = State(initialValue: initialIconType)
        _selfhstService = State(initialValue: initialIconType == .selfhst ? initialIconValue : "")
        _customImageUrl = State(initialValue: initialIconType == .systemSymbol ? initialIconValue : "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(localizer.t.bookmarkTitle, text: $title)
                    TextField(localizer.t.bookmarkUrl, text: $url)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    TextField(localizer.t.bookmarkDesc, text: $description)
                    TextField(localizer.t.bookmarkTags, text: $tagsText)
                }

                Section(header: Text(localizer.t.bookmarkCategory)) {
                    Picker(localizer.t.bookmarkCategory, selection: $categoryId) {
                        ForEach(categories) { category in
                            Text(category.name).tag(category.id as UUID?)
                        }
                    }
                    .pickerStyle(.menu)
                }

                Section(header: Text(localizer.t.bookmarkIcon)) {
                    HStack(spacing: 8) {
                        iconTypeButton(type: .favicon, title: localizer.t.bookmarkFavicon)
                        iconTypeButton(type: .selfhst, title: localizer.t.bookmarkSelfhst)
                        iconTypeButton(type: .systemSymbol, title: localizer.t.bookmarkSymbol)
                    }

                    if iconType == .systemSymbol {
                        VStack(alignment: .leading, spacing: 10) {
                            TextField(localizer.t.bookmarkEnterUrl, text: $customImageUrl)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)

                            if let remoteImageUrl = Bookmark.normalizeRemoteImageUrl(customImageUrl) {
                                HStack(spacing: 10) {
                                    AsyncImage(url: remoteImageUrl) { phase in
                                        switch phase {
                                        case .success(let image):
                                            image
                                                .resizable()
                                                .scaledToFit()
                                        case .failure:
                                            Image(systemName: "photo")
                                                .foregroundStyle(AppTheme.textMuted)
                                        default:
                                            ProgressView()
                                        }
                                    }
                                    .frame(width: 36, height: 36)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))

                                    Text("Image URL preview")
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textSecondary)
                                }
                            }
                        }
                    } else if iconType == .selfhst {
                        VStack(alignment: .leading, spacing: 12) {
                            TextField(localizer.t.bookmarkEnterSelfhst, text: $selfhstService)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                                .onChange(of: selfhstService) { _, value in
                                    if value.contains("selfh.st"),
                                       let extracted = extractSelfhstService(from: value),
                                       extracted != value {
                                        selfhstService = extracted
                                    }
                                }

                            if !suggestedSelfhstServices.isEmpty {
                                ScrollView {
                                    LazyVStack(alignment: .leading, spacing: 6) {
                                        ForEach(suggestedSelfhstServices, id: \.self) { service in
                                            Button {
                                                selfhstService = service
                                            } label: {
                                                HStack(spacing: 10) {
                                                    AsyncImage(url: URL(string: "https://raw.githubusercontent.com/selfhst/icons/main/png/\(service).png")) { phase in
                                                        switch phase {
                                                        case .success(let image):
                                                            image
                                                                .resizable()
                                                                .scaledToFit()
                                                        default:
                                                            Image(systemName: "sparkles")
                                                                .foregroundStyle(AppTheme.textMuted)
                                                        }
                                                    }
                                                    .frame(width: 24, height: 24)
                                                    .clipShape(RoundedRectangle(cornerRadius: 6))

                                                    Text(service)
                                                        .font(.subheadline)
                                                        .foregroundStyle(.primary)
                                                    Spacer()
                                                }
                                                .padding(.horizontal, 10)
                                                .padding(.vertical, 8)
                                                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10))
                                            }
                                            .buttonStyle(.plain)
                                        }
                                    }
                                }
                                .frame(maxHeight: 180)
                            }

                            if let selfhstUrl = selfhstPreviewUrl {
                                HStack(spacing: 10) {
                                    AsyncImage(url: selfhstUrl) { phase in
                                        switch phase {
                                        case .success(let image):
                                            image
                                                .resizable()
                                                .scaledToFit()
                                        case .failure:
                                            Image(systemName: "exclamationmark.triangle")
                                                .foregroundStyle(.orange)
                                        default:
                                            ProgressView()
                                        }
                                    }
                                    .frame(width: 36, height: 36)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))

                                    Text(localizer.t.bookmarkPreviewSelfhst)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textSecondary)
                                }
                            } else {
                                Text("selfh.st/icons")
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }
                    } else {
                        HStack(spacing: 10) {
                            if faviconPreviewUrl != nil {
                                MultiSourceRemoteIconView(urls: faviconPreviewCandidates, fallbackSystemImage: "globe", tint: AppTheme.textMuted, size: 36)
                                    .id(faviconPreviewCandidates.map(\.absoluteString).joined(separator: "|"))
                                Text(localizer.t.bookmarkAutoFavicon)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                                    .lineLimit(2)
                            } else {
                                Image(systemName: "globe")
                                    .font(.title3)
                                    .foregroundStyle(AppTheme.textMuted)
                                    .frame(width: 36, height: 36)

                                Text(localizer.t.bookmarkEnterUrl)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }
                    }
                }
            }
            .navigationTitle(editingBookmark != nil ? localizer.t.bookmarkEdit : localizer.t.bookmarkAdd)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) {
                        if let catId = categoryId {
                            let fixedUrl = normalizedBookmarkUrl

                            let parsedTags = tagsText.split(separator: ",")
                                .map { $0.trimmingCharacters(in: .whitespaces) }
                                .filter { !$0.isEmpty }

                            onSave(
                                title.trimmingCharacters(in: .whitespaces),
                                description.trimmingCharacters(in: .whitespaces),
                                fixedUrl,
                                catId,
                                iconType,
                                iconValueForSelection,
                                parsedTags
                            )
                            dismiss()
                        }
                    }
                    .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty || url.trimmingCharacters(in: .whitespaces).isEmpty || categoryId == nil)
                }
            }
            .onChange(of: iconType) { oldType, newType in
                if oldType == .selfhst, newType != .selfhst {
                    selfhstService = extractSelfhstService(from: selfhstService) ?? selfhstService
                }
            }
        }
    }

    private var normalizedBookmarkUrl: String {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.lowercased().hasPrefix("http") ? trimmed : "https://\(trimmed)"
    }

    private var iconValueForSelection: String {
        switch iconType {
        case .favicon:
            return ""
        case .selfhst:
            return extractSelfhstService(from: selfhstService) ?? selfhstService.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        case .systemSymbol:
            return customImageUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }

    private var faviconPreviewCandidates: [URL] {
        let previewBookmark = Bookmark(
            categoryId: categoryId ?? UUID(),
            title: title,
            description: description,
            url: normalizedBookmarkUrl,
            iconType: .favicon,
            iconValue: "",
            tags: [],
            sortOrder: 0
        )
        return previewBookmark.faviconCandidates
    }

    private var faviconPreviewUrl: URL? {
        faviconPreviewCandidates.first
    }

    private var selfhstPreviewUrl: URL? {
        let service = extractSelfhstService(from: selfhstService) ?? selfhstService.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !service.isEmpty else { return nil }
        return URL(string: "https://raw.githubusercontent.com/selfhst/icons/main/png/\(service).png")
    }

    private var suggestedSelfhstServices: [String] {
        let query = extractSelfhstService(from: selfhstService) ?? selfhstService.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let source = Self.selfhstCatalog
        if query.isEmpty {
            return Array(source.prefix(12))
        }
        return Array(source.filter { $0.contains(query) }.prefix(8))
    }

    private func iconTypeButton(type: BookmarkIconType, title: String) -> some View {
        let selected = iconType == type
        return Button {
            withAnimation(.spring(duration: 0.2)) {
                iconType = type
            }
        } label: {
            Text(title)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
                .foregroundStyle(selected ? .white : AppTheme.textSecondary)
                .background(selected ? AppTheme.primary : Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    private func extractSelfhstService(from rawValue: String) -> String? {
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !trimmed.isEmpty else { return nil }

        if let parsedUrl = URL(string: trimmed), let host = parsedUrl.host, host.contains("selfh.st") {
            let candidate = parsedUrl.pathComponents.last ?? ""
            return sanitizeSelfhstService(candidate)
        }

        return sanitizeSelfhstService(trimmed)
    }

    private func sanitizeSelfhstService(_ rawValue: String) -> String? {
        let base = rawValue
            .split(separator: "/")
            .last
            .map(String.init) ?? rawValue

        let sanitized = base
            .replacingOccurrences(of: ".png", with: "")
            .replacingOccurrences(of: ".svg", with: "")
            .replacingOccurrences(of: ".webp", with: "")
            .replacingOccurrences(of: ".ico", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()

        return sanitized.isEmpty ? nil : sanitized
    }

    private static let selfhstCatalog: [String] = [
        "adguard", "adguard-home", "airsonic", "audiobookshelf", "authentik", "beszel",
        "calibre-web", "changedetection", "code-server", "dashy", "ddns-updater",
        "deluge", "dozzle", "duplicati", "filebrowser", "freshrss", "glances",
        "gitea", "grafana", "guacamole", "heimdall", "homeassistant", "immich",
        "jellyfin", "jellyseerr", "kavita", "lidarr", "linkding", "mealie",
        "navidrome", "nextcloud", "ntfy", "paperless-ngx", "pihole", "portainer",
        "prowlarr", "qbittorrent", "radarr", "readarr", "scrutiny", "searxng",
        "sonarr", "speedtest-tracker", "syncthing", "tautulli", "traefik",
        "uptime-kuma", "vaultwarden", "vikunja", "watchtower", "wireguard",
        "wordpress"
    ]
}

private struct MultiSourceRemoteIconView: View {
    let urls: [URL]
    let fallbackSystemImage: String
    let tint: Color
    let size: CGFloat

    @State private var index: Int = 0

    var body: some View {
        ZStack {
            if urls.indices.contains(index) {
                AsyncImage(url: urls[index]) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                    case .failure:
                        if index < urls.count - 1 {
                            Color.clear
                                .onAppear {
                                    index += 1
                                }
                        } else {
                            fallbackView
                        }
                    default:
                        ProgressView()
                    }
                }
                .id(index)
            } else {
                fallbackView
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private var fallbackView: some View {
        Image(systemName: fallbackSystemImage)
            .font(.title3)
            .foregroundStyle(tint)
    }
}
