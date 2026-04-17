import SwiftUI
import UniformTypeIdentifiers

struct BookmarksView: View {
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme
    @Bindable private var bookmarkManager = BookmarkManager.shared

    @State private var editingCategory: BookmarkCategory?
    @State private var showingAddCategory = false
    @State private var categoryToDelete: BookmarkCategory?

    @State private var editingBookmark: Bookmark?
    @State private var showingAddBookmark = false
    @State private var preselectedCategoryIdForNewBookmark: UUID?

    @State private var searchText = ""
    @State private var showingReorderSheet = false
    @State private var collapsedCategories: Set<UUID> = []

    @AppStorage("bookmarksLayoutGridView") private var isGridView = false

    var body: some View {
        NavigationStack {
            ZStack {
                bookmarkPageBackground
                    .ignoresSafeArea()

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 16) {
                        topBar
                        titleSection
                        searchSection

                        if bookmarkManager.categories.isEmpty {
                            emptyStateView
                                .padding(.top, 16)
                        } else {
                            ForEach(bookmarkManager.categories) { category in
                                let bookmarks = filteredBookmarks(for: category)

                                if !bookmarks.isEmpty || searchText.isEmpty {
                                    VStack(alignment: .leading, spacing: 8) {
                                        categoryHeader(
                                            category,
                                            visibleCount: bookmarks.count,
                                            isCollapsed: collapsedCategories.contains(category.id)
                                        ) {
                                            withAnimation(.spring(response: 0.34, dampingFraction: 0.9, blendDuration: 0.12)) {
                                                if collapsedCategories.contains(category.id) {
                                                    collapsedCategories.remove(category.id)
                                                } else {
                                                    collapsedCategories.insert(category.id)
                                                }
                                            }
                                        }

                                        if !bookmarks.isEmpty {
                                            Group {
                                                if isGridView {
                                                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                                                        ForEach(bookmarks) { bookmark in
                                                            BookmarkRow(bookmark: bookmark, accentColor: category.categoryColor, isGrid: true)
                                                                .contextMenu {
                                                                    Button {
                                                                        editingBookmark = bookmark
                                                                    } label: {
                                                                        Label(localizer.t.bookmarkEdit, systemImage: "pencil")
                                                                    }
                                                                    Button(role: .destructive) {
                                                                        bookmarkManager.deleteBookmark(bookmark)
                                                                    } label: {
                                                                        Label(localizer.t.delete, systemImage: "trash")
                                                                    }
                                                                }
                                                        }
                                                    }
                                                    .padding(.top, 2)
                                                } else {
                                                    VStack(spacing: 0) {
                                                        ForEach(bookmarks) { bookmark in
                                                            BookmarkRow(bookmark: bookmark, accentColor: category.categoryColor, isGrid: false)
                                                                .contextMenu {
                                                                    Button {
                                                                        editingBookmark = bookmark
                                                                    } label: {
                                                                        Label(localizer.t.bookmarkEdit, systemImage: "pencil")
                                                                    }
                                                                    Button(role: .destructive) {
                                                                        bookmarkManager.deleteBookmark(bookmark)
                                                                    } label: {
                                                                        Label(localizer.t.delete, systemImage: "trash")
                                                                    }
                                                                }
                                                        }
                                                    }
                                                    .padding(.top, 2)
                                                }
                                            }
                                            .frame(maxHeight: collapsedCategories.contains(category.id) ? 0 : nil, alignment: .top)
                                            .opacity(collapsedCategories.contains(category.id) ? 0 : 1)
                                            .scaleEffect(
                                                x: 1,
                                                y: collapsedCategories.contains(category.id) ? 0.96 : 1,
                                                anchor: .top
                                            )
                                            .clipped()
                                            .allowsHitTesting(!collapsedCategories.contains(category.id))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.top, 8)
                    .padding(.bottom, 34)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showingReorderSheet) {
                BookmarksReorderView(bookmarkManager: bookmarkManager)
            }
            .sheet(isPresented: $showingAddCategory) {
                EditCategoryView { name, icon, color in
                    bookmarkManager.addCategory(name: name, icon: icon, color: color)
                }
            }
            .sheet(item: $editingCategory) { category in
                EditCategoryView(editingCategory: category) { name, icon, color in
                    bookmarkManager.updateCategory(category, newName: name, newIcon: icon, newColor: color)
                }
            }
            .sheet(isPresented: $showingAddBookmark) {
                EditBookmarkView(
                    categories: bookmarkManager.categories,
                    preselectedCategory: preselectedCategoryIdForNewBookmark
                ) { title, desc, url, catId, type, val_, tags in
                    bookmarkManager.addBookmark(title: title, description: desc, url: url, categoryId: catId, iconType: type, iconValue: val_, tags: tags)
                }
            }
            .sheet(item: $editingBookmark) { bookmark in
                EditBookmarkView(
                    categories: bookmarkManager.categories,
                    editingBookmark: bookmark
                ) { title, desc, url, catId, type, val_, tags in
                    bookmarkManager.updateBookmark(bookmark, newTitle: title, newDescription: desc, newUrl: url, newCategoryId: catId, newIconType: type, newIconValue: val_, newTags: tags)
                }
            }
            .alert(localizer.t.categoryDelete, isPresented: .init(
                get: { categoryToDelete != nil },
                set: { if !$0 { categoryToDelete = nil } }
            )) {
                Button(localizer.t.cancel, role: .cancel) { }
                Button(localizer.t.delete, role: .destructive) {
                    if let category = categoryToDelete {
                        bookmarkManager.deleteCategory(category)
                    }
                    categoryToDelete = nil
                }
            } message: {
                Text(localizer.t.categoryDeleteConfirm)
            }
        }
    }

    private var bookmarkPageBackground: Color {
        colorScheme == .dark ? .black : Color(.systemGroupedBackground)
    }

    private var topBar: some View {
        HStack {
            Button {
                showingReorderSheet = true
            } label: {
                Image(systemName: "arrow.up.arrow.down")
                    .font(.title3.weight(.semibold))
                    .frame(width: 52, height: 52)
            }
            .buttonStyle(.plain)
            .glassCard(cornerRadius: 18, tint: nil)
            .accessibilityLabel(localizer.t.bookmarkReorder)

            Spacer()

            HStack(spacing: 8) {
                Button {
                    withAnimation(.spring(duration: 0.25)) {
                        isGridView.toggle()
                    }
                } label: {
                    Image(systemName: isGridView ? "list.bullet" : "square.grid.2x2")
                        .font(.title3.weight(.semibold))
                        .frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(localizer.t.bookmarkToggleView)

                Menu {
                    Button {
                        preselectedCategoryIdForNewBookmark = bookmarkManager.categories.first?.id
                        showingAddBookmark = true
                    } label: {
                        Label(localizer.t.bookmarkAdd, systemImage: "bookmark.fill")
                    }

                    Button {
                        showingAddCategory = true
                    } label: {
                        Label(localizer.t.categoryAdd, systemImage: "folder.fill.badge.plus")
                    }
                } label: {
                    Image(systemName: "plus")
                        .font(.title3.weight(.semibold))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(localizer.t.bookmarkAdd)
            }
            .glassCard(cornerRadius: 18, tint: nil)
        }
    }

    private var titleSection: some View {
        Text(localizer.t.tabBookmarks)
            .font(.largeTitle)
            .fontWeight(.heavy)
            .lineLimit(1)
            .minimumScaleFactor(0.72)
            .frame(maxWidth: .infinity, alignment: .leading)
            .foregroundStyle(.primary)
    }

    private var searchSection: some View {
        HStack(spacing: 10) {
            Image(systemName: "magnifyingglass")
                .font(.title3)
                .foregroundStyle(AppTheme.textSecondary)
                .accessibilityHidden(true)

            TextField(localizer.t.bookmarkSearchPrompt, text: $searchText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.title3.weight(.medium))
                .accessibilityLabel(localizer.t.bookmarkSearchPrompt)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity)
        .glassCard(cornerRadius: 22, tint: nil)
    }

    private func filteredBookmarks(for category: BookmarkCategory) -> [Bookmark] {
        bookmarkManager.getBookmarks(for: category.id).filter { bookmark in
            if searchText.isEmpty { return true }
            let matchTitle = bookmark.title.localizedCaseInsensitiveContains(searchText)
            let matchTags = bookmark.tags.contains { $0.localizedCaseInsensitiveContains(searchText) }
            let matchUrl = bookmark.url.localizedCaseInsensitiveContains(searchText)
            return matchTitle || matchTags || matchUrl
        }
    }

    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Image(systemName: "bookmark.slash.fill")
                .font(.system(size: 56))
                .foregroundStyle(AppTheme.textMuted)
                .accessibilityLabel(localizer.t.categoryEmpty)

            Text(localizer.t.categoryEmpty)
                .font(.headline)
                .foregroundStyle(AppTheme.textSecondary)
                .multilineTextAlignment(.center)

            Button {
                showingAddCategory = true
            } label: {
                Label(localizer.t.categoryAdd, systemImage: "folder.badge.plus")
                    .font(.headline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 14)
                    .background(AppTheme.primary, in: RoundedRectangle(cornerRadius: 14))
            }
            .padding(.top, 10)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .glassCard(cornerRadius: 20, tint: nil)
    }

    private func categoryHeader(_ category: BookmarkCategory, visibleCount: Int, isCollapsed: Bool, onToggle: @escaping () -> Void) -> some View {
        let visibleCountText = String(visibleCount)

        return HStack(spacing: 10) {
            Button(action: onToggle) {
                HStack(spacing: 10) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(category.categoryColor)
                        .frame(width: 5, height: 22)

                    if let icon = category.icon, !icon.isEmpty {
                        Image(systemName: icon)
                            .font(.subheadline)
                            .foregroundStyle(category.categoryColor)
                            .frame(width: 28, height: 28)
                            .accessibilityHidden(true)
                    }

                    Text(category.name)
                        .font(.headline.weight(.semibold))
                        .foregroundStyle(.primary)
                        .textCase(nil)
                        .lineLimit(1)

                    Text(visibleCountText)
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textSecondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color(.tertiarySystemFill), in: Capsule())

                    Image(systemName: isCollapsed ? "chevron.down" : "chevron.up")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.textSecondary)
                        .accessibilityHidden(true)

                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(category.name)
            .accessibilityValue(visibleCountText)
            .accessibilityHint(isCollapsed ? localizer.t.bookmarkExpandCategory : localizer.t.bookmarkCollapseCategory)

            Menu {
                Button {
                    preselectedCategoryIdForNewBookmark = category.id
                    showingAddBookmark = true
                } label: {
                    Label(localizer.t.bookmarkAdd, systemImage: "plus")
                }

                Button {
                    editingCategory = category
                } label: {
                    Label(localizer.t.categoryEdit, systemImage: "pencil")
                }

                Button(role: .destructive) {
                    categoryToDelete = category
                } label: {
                    Label(localizer.t.delete, systemImage: "trash")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .foregroundStyle(AppTheme.textSecondary)
                    .frame(width: 30, height: 30)
                    .background(Color(.tertiarySystemFill), in: Circle())
            }
            .accessibilityLabel(localizer.t.categoryActions)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(.secondarySystemBackground).opacity(colorScheme == .dark ? 0.78 : 0.92))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(category.categoryColor.opacity(colorScheme == .dark ? 0.18 : 0.22), lineWidth: 0.7)
                )
        )
    }
}

// MARK: - BookmarkRow

struct BookmarkRow: View {
    @Environment(\.colorScheme) private var colorScheme
    let bookmark: Bookmark
    var accentColor: Color = .blue
    var isGrid: Bool

    var body: some View {
        Button {
            openBookmark(bookmark)
        } label: {
            if isGrid {
                gridLayout
            } else {
                listLayout
            }
        }
        .buttonStyle(.plain)
    }

    private var gridLayout: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                iconView
                    .frame(width: 40, height: 40)
                Spacer()
                Image(systemName: "arrow.up.right")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .accessibilityHidden(true)
            }

            VStack(alignment: .leading, spacing: 3) {
                Text(bookmark.title)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                if let domain = bookmark.domain {
                    Text(domain)
                        .font(.caption2)
                        .foregroundStyle(accentColor.opacity(0.8))
                        .lineLimit(1)
                }
            }

            if !bookmark.tags.isEmpty {
                tagsView
            }
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
        .frame(minHeight: 132, alignment: .topLeading)
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .contentShape(Rectangle())
    }

    private var listLayout: some View {
        HStack(spacing: 12) {
            iconView
                .frame(width: 40, height: 40)

            VStack(alignment: .leading, spacing: 2) {
                Text(bookmark.title)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                if let desc = bookmark.description, !desc.isEmpty {
                    Text(desc)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                        .lineLimit(1)
                }

                if let domain = bookmark.domain {
                    Text(domain)
                        .font(.caption2)
                        .foregroundStyle(accentColor.opacity(0.7))
                        .lineLimit(1)
                }

                if !bookmark.tags.isEmpty {
                    tagsView
                        .padding(.top, 2)
                }
            }

            Spacer()

            Image(systemName: "arrow.up.right")
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(AppTheme.textMuted)
                .accessibilityHidden(true)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }

    private var tagsView: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(bookmark.tags, id: \.self) { tag in
                    Text(tag)
                        .font(.system(size: 9, weight: .semibold))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color(.tertiarySystemFill).opacity(colorScheme == .dark ? 0.32 : 0.55), in: Capsule())
                        .foregroundStyle(AppTheme.textSecondary)
                }
            }
        }
    }

    @ViewBuilder
    private var iconView: some View {
        switch bookmark.iconType {
        case .systemSymbol:
            if let customImage = bookmark.customImageUrl {
                AsyncImage(url: customImage) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                    case .failure:
                        legacySymbolFallback
                    default:
                        ProgressView()
                            .scaleEffect(0.6)
                    }
                }
                .frame(width: 24, height: 24)
                .clipShape(RoundedRectangle(cornerRadius: 4))
            } else {
                legacySymbolFallback
            }

        case .selfhst:
            if let selfhstUrl = bookmark.selfhstIconUrl {
                AsyncImage(url: selfhstUrl) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                    case .failure:
                        Image(systemName: "bookmark.fill")
                            .font(.body)
                            .foregroundStyle(accentColor)
                    default:
                        ProgressView()
                            .scaleEffect(0.6)
                    }
                }
                .frame(width: 24, height: 24)
                .clipShape(RoundedRectangle(cornerRadius: 4))
            } else {
                Image(systemName: "bookmark.fill")
                    .font(.body)
                    .foregroundStyle(accentColor)
            }

        case .favicon:
            if !bookmark.faviconCandidates.isEmpty {
                MultiSourceBookmarkIconView(urls: bookmark.faviconCandidates, fallbackSystemImage: "globe", tint: accentColor)
                    .id(bookmark.faviconCandidates.map(\.absoluteString).joined(separator: "|"))
            } else {
                Image(systemName: "globe")
                    .font(.body)
                    .foregroundStyle(accentColor)
            }
        }
    }

    private var legacySymbolFallback: some View {
        let legacyValue = bookmark.iconValue.trimmingCharacters(in: .whitespacesAndNewlines)
        let systemName: String
        if legacyValue.isEmpty {
            systemName = "photo"
        } else if Bookmark.normalizeRemoteImageUrl(legacyValue) != nil {
            // URL-based custom icon failed to load: keep a deterministic fallback symbol.
            systemName = "photo"
        } else {
            // Backward compatibility for old bookmarks that used SF Symbol names.
            systemName = legacyValue
        }

        return Image(systemName: systemName)
            .font(.body)
            .foregroundStyle(accentColor)
    }

    private func openBookmark(_ bookmark: Bookmark) {
        let raw = bookmark.url.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalized = raw.lowercased().hasPrefix("http") ? raw : "https://\(raw)"
        if let url = URL(string: normalized) {
            UIApplication.shared.open(url)
        }
    }
}

private struct MultiSourceBookmarkIconView: View {
    let urls: [URL]
    let fallbackSystemImage: String
    let tint: Color

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
                            .scaleEffect(0.6)
                    }
                }
                .id(index)
            } else {
                fallbackView
            }
        }
        .frame(width: 24, height: 24)
        .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    private var fallbackView: some View {
        Image(systemName: fallbackSystemImage)
            .font(.body)
            .foregroundStyle(tint)
    }
}

private struct BookmarksReorderView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(Localizer.self) private var localizer
    @Bindable var bookmarkManager: BookmarkManager

    @State private var editMode: EditMode = .active
    @State private var draggedBookmarkId: UUID?

    var body: some View {
        NavigationStack {
            List {
                ForEach(bookmarkManager.categories) { category in
                    Section {
                        ForEach(bookmarkManager.getBookmarks(for: category.id)) { bookmark in
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(category.categoryColor.opacity(0.2))
                                    .frame(width: 28, height: 28)
                                    .overlay(
                                        Image(systemName: "line.3.horizontal")
                                            .font(.caption2)
                                            .foregroundStyle(category.categoryColor)
                                    )
                                    .accessibilityHidden(true)

                                Text(bookmark.title)
                                    .lineLimit(1)
                                    .font(.subheadline)

                                Text(localizer.t.bookmarkReorderBookmarkLabel)
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(.secondary)
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 3)
                                    .background(Color(.tertiarySystemFill), in: Capsule())

                                Spacer()

                                if bookmarkManager.categories.count > 1 {
                                    Menu {
                                        ForEach(bookmarkManager.categories.filter { $0.id != category.id }) { targetCategory in
                                            Button {
                                                bookmarkManager.moveBookmarkToCategory(bookmark, targetCategoryId: targetCategory.id)
                                            } label: {
                                                Label(targetCategory.name, systemImage: "folder")
                                            }
                                        }
                                    } label: {
                                        Image(systemName: "folder.badge.plus")
                                            .font(.caption)
                                            .foregroundStyle(AppTheme.textSecondary)
                                    }
                                    .accessibilityLabel(localizer.t.bookmarkMoveToCategory)
                                }
                            }
                            .padding(.leading, 14)
                            .onDrag {
                                draggedBookmarkId = bookmark.id
                                return NSItemProvider(object: bookmark.id.uuidString as NSString)
                            }
                            .onDrop(of: [.text], isTargeted: nil) { _ in
                                guard let draggedId = draggedBookmarkId else { return false }
                                bookmarkManager.moveBookmark(draggedId: draggedId, to: bookmark.id)
                                draggedBookmarkId = nil
                                return true
                            }
                        }
                        .onMove { source, destination in
                            bookmarkManager.moveBookmark(categoryId: category.id, from: source, to: destination)
                        }
                    } header: {
                        HStack(spacing: 8) {
                            if let icon = category.icon, !icon.isEmpty {
                                Image(systemName: icon)
                                    .foregroundStyle(category.categoryColor)
                            }
                            Text(category.name)
                            Text(localizer.t.bookmarkReorderCategoryLabel)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(Color(.tertiarySystemFill), in: Capsule())

                            Spacer()

                            if let categoryIndex = bookmarkManager.categories.firstIndex(where: { $0.id == category.id }) {
                                Button {
                                    moveCategory(categoryIndex, by: -1)
                                } label: {
                                    Image(systemName: "chevron.up")
                                        .font(.caption.bold())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(localizer.t.settingsMoveUp)
                                .disabled(categoryIndex == 0)
                                .opacity(categoryIndex == 0 ? 0.3 : 0.85)

                                Button {
                                    moveCategory(categoryIndex, by: 1)
                                } label: {
                                    Image(systemName: "chevron.down")
                                        .font(.caption.bold())
                                }
                                .buttonStyle(.plain)
                                .accessibilityLabel(localizer.t.settingsMoveDown)
                                .disabled(categoryIndex == bookmarkManager.categories.count - 1)
                                .opacity(categoryIndex == bookmarkManager.categories.count - 1 ? 0.3 : 0.85)
                            }
                        }
                        .onDrop(of: [.text], isTargeted: nil) { _ in
                            guard let draggedId = draggedBookmarkId,
                                  let dragged = bookmarkManager.bookmarks.first(where: { $0.id == draggedId }) else { return false }
                            bookmarkManager.moveBookmarkToCategory(dragged, targetCategoryId: category.id)
                            draggedBookmarkId = nil
                            return true
                        }
                    }
                }
                .onMove { source, destination in
                    bookmarkManager.moveCategory(from: source, to: destination)
                }
            }
            .environment(\.editMode, $editMode)
            .navigationTitle(localizer.t.bookmarkReorder)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(localizer.t.close) {
                        dismiss()
                    }
                }
            }
        }
    }

    private func moveCategory(_ currentIndex: Int, by direction: Int) {
        let newIndex = currentIndex + direction
        guard newIndex >= 0, newIndex < bookmarkManager.categories.count else { return }

        let source = IndexSet(integer: currentIndex)
        let destination = direction > 0 ? newIndex + 1 : newIndex
        bookmarkManager.moveCategory(from: source, to: destination)
    }

}
