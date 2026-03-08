import Foundation
import SwiftUI

@Observable
@MainActor
final class BookmarkManager {
    static let shared = BookmarkManager()
    
    var categories: [BookmarkCategory] = []
    var bookmarks: [Bookmark] = []
    
    private let categoriesKey = "homelab_bookmark_categories"
    private let bookmarksKey = "homelab_bookmarks"
    
    init() {
        loadData()
        
        // Add a default Uncategorized category if categories are completely empty
        if categories.isEmpty {
            let defaultCat = BookmarkCategory(id: UUID(), name: "Uncategorized", sortOrder: 0)
            categories.append(defaultCat)
            saveData()
        }
    }
    
    // MARK: - Load/Save
    private func loadData() {
        if let categoryData = UserDefaults.standard.data(forKey: categoriesKey),
           let decodedCategories = try? JSONDecoder().decode([BookmarkCategory].self, from: categoryData) {
            self.categories = decodedCategories.sorted(by: { $0.sortOrder < $1.sortOrder })
        }
        
        if let bookmarkData = UserDefaults.standard.data(forKey: bookmarksKey),
           let decodedBookmarks = try? JSONDecoder().decode([Bookmark].self, from: bookmarkData) {
            self.bookmarks = decodedBookmarks.sorted(by: { $0.sortOrder < $1.sortOrder })
        }
    }
    
    private func saveData() {
        if let encodedCategories = try? JSONEncoder().encode(categories) {
            UserDefaults.standard.set(encodedCategories, forKey: categoriesKey)
        }
        if let encodedBookmarks = try? JSONEncoder().encode(bookmarks) {
            UserDefaults.standard.set(encodedBookmarks, forKey: bookmarksKey)
        }
    }
    
    // MARK: - Categories
    func addCategory(name: String, icon: String? = nil, color: String? = nil) {
        let maxSort = categories.map { $0.sortOrder }.max() ?? -1
        let newCat = BookmarkCategory(name: name, icon: icon, color: color, sortOrder: maxSort + 1)
        categories.append(newCat)
        saveData()
    }
    
    func updateCategory(_ category: BookmarkCategory, newName: String, newIcon: String?, newColor: String?) {
        if let index = categories.firstIndex(where: { $0.id == category.id }) {
            categories[index].name = newName
            categories[index].icon = newIcon
            categories[index].color = newColor
            saveData()
        }
    }
    
    func deleteCategory(_ category: BookmarkCategory) {
        categories.removeAll { $0.id == category.id }
        bookmarks.removeAll { $0.categoryId == category.id }
        // Ensure at least one category exists
        if categories.isEmpty {
            categories.append(BookmarkCategory(id: UUID(), name: "Uncategorized", sortOrder: 0))
        }
        saveData()
    }
    
    func moveCategory(from source: IndexSet, to destination: Int) {
        categories.move(fromOffsets: source, toOffset: destination)
        for (index, cat) in categories.enumerated() {
            if let i = categories.firstIndex(where: { $0.id == cat.id }) {
                categories[i].sortOrder = index
            }
        }
        saveData()
    }
    
    // MARK: - Bookmarks
    func addBookmark(title: String, description: String? = nil, url: String, categoryId: UUID, iconType: BookmarkIconType, iconValue: String, tags: [String] = []) {
        let filtered = bookmarks.filter { $0.categoryId == categoryId }
        let maxSort = filtered.map { $0.sortOrder }.max() ?? -1
        let newBookmark = Bookmark(
            categoryId: categoryId,
            title: title,
            description: description,
            url: url,
            iconType: iconType,
            iconValue: iconValue,
            tags: tags,
            sortOrder: maxSort + 1
        )
        bookmarks.append(newBookmark)
        saveData()
    }
    
    func updateBookmark(_ bookmark: Bookmark, newTitle: String, newDescription: String?, newUrl: String, newCategoryId: UUID, newIconType: BookmarkIconType, newIconValue: String, newTags: [String]) {
        if let index = bookmarks.firstIndex(where: { $0.id == bookmark.id }) {
            bookmarks[index].title = newTitle
            bookmarks[index].description = newDescription
            bookmarks[index].url = newUrl
            bookmarks[index].categoryId = newCategoryId
            bookmarks[index].iconType = newIconType
            bookmarks[index].iconValue = newIconValue
            bookmarks[index].tags = newTags
            saveData()
        }
    }
    
    func deleteBookmark(_ bookmark: Bookmark) {
        bookmarks.removeAll { $0.id == bookmark.id }
        saveData()
    }
    
    func moveBookmark(categoryId: UUID, from source: IndexSet, to destination: Int) {
        var categoryBookmarks = bookmarks.filter { $0.categoryId == categoryId }.sorted(by: { $0.sortOrder < $1.sortOrder })
        categoryBookmarks.move(fromOffsets: source, toOffset: destination)
        
        // Update sort orders for this category
        for (index, bm) in categoryBookmarks.enumerated() {
            if let i = bookmarks.firstIndex(where: { $0.id == bm.id }) {
                bookmarks[i].sortOrder = index
            }
        }
        saveData()
    }

    func moveBookmarkToCategory(_ bookmark: Bookmark, targetCategoryId: UUID) {
        guard bookmark.categoryId != targetCategoryId else { return }
        guard let index = bookmarks.firstIndex(where: { $0.id == bookmark.id }) else { return }

        let oldCategoryId = bookmarks[index].categoryId
        let targetCount = bookmarks.filter { $0.categoryId == targetCategoryId }.count

        bookmarks[index].categoryId = targetCategoryId
        bookmarks[index].sortOrder = targetCount

        var oldCategoryBookmarks = bookmarks
            .filter { $0.categoryId == oldCategoryId }
            .sorted(by: { $0.sortOrder < $1.sortOrder })

        for (position, bm) in oldCategoryBookmarks.enumerated() {
            if let oldIndex = bookmarks.firstIndex(where: { $0.id == bm.id }) {
                bookmarks[oldIndex].sortOrder = position
            }
        }

        saveData()
    }

    func moveBookmark(draggedId: UUID, to targetId: UUID) {
        guard let dragged = bookmarks.first(where: { $0.id == draggedId }),
              let target = bookmarks.first(where: { $0.id == targetId }),
              dragged.id != target.id else { return }

        if dragged.categoryId == target.categoryId {
            var current = getBookmarks(for: dragged.categoryId)
            guard let fromIndex = current.firstIndex(where: { $0.id == dragged.id }),
                  let toIndex = current.firstIndex(where: { $0.id == target.id }) else { return }

            let moved = current.remove(at: fromIndex)
            let adjusted = fromIndex < toIndex ? max(0, toIndex - 1) : toIndex
            current.insert(moved, at: adjusted)
            applySort(current, in: dragged.categoryId)
        } else {
            var source = getBookmarks(for: dragged.categoryId)
            source.removeAll { $0.id == dragged.id }
            applySort(source, in: dragged.categoryId)

            var targetItems = getBookmarks(for: target.categoryId)
            guard let insertIndex = targetItems.firstIndex(where: { $0.id == target.id }) else { return }

            var moved = dragged
            moved.categoryId = target.categoryId
            targetItems.insert(moved, at: insertIndex)
            applySort(targetItems, in: target.categoryId)
        }

        saveData()
    }

    private func applySort(_ ordered: [Bookmark], in categoryId: UUID) {
        for (position, item) in ordered.enumerated() {
            if let idx = bookmarks.firstIndex(where: { $0.id == item.id }) {
                bookmarks[idx].categoryId = categoryId
                bookmarks[idx].sortOrder = position
            }
        }
    }
    
    func getBookmarks(for categoryId: UUID) -> [Bookmark] {
        return bookmarks.filter { $0.categoryId == categoryId }.sorted(by: { $0.sortOrder < $1.sortOrder })
    }
}
