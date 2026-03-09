import Foundation
import Observation

@Observable
@MainActor
final class BookmarksStore {
    var categories: [BookmarkCategory] = []
    var bookmarks: [Bookmark] = []
    
    private let fileManager = FileManager.default
    private var dataFileURL: URL {
        let documentsDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
        return documentsDirectory.appendingPathComponent("homelab_bookmarks.json")
    }
    
    // MARK: - Initializer
    
    init() {
        loadData()
    }
    
    // MARK: - Persistence
    
    private func loadData() {
        guard fileManager.fileExists(atPath: dataFileURL.path) else {
            // Seed with some default categories if needed or leave empty
            seedDefaults()
            saveData()
            return
        }
        
        do {
            let data = try Data(contentsOf: dataFileURL)
            let wrapper = try JSONDecoder().decode(BookmarksDataWrapper.self, from: data)
            self.categories = wrapper.categories.sorted(by: { $0.sortOrder < $1.sortOrder })
            self.bookmarks = wrapper.bookmarks.sorted(by: { $0.sortOrder < $1.sortOrder })
        } catch {
            print("Failed to load bookmarks data: \(error.localizedDescription)")
            self.categories = []
            self.bookmarks = []
        }
    }
    
    private func saveData() {
        let wrapper = BookmarksDataWrapper(categories: categories, bookmarks: bookmarks)
        do {
            let data = try JSONEncoder().encode(wrapper)
            try data.write(to: dataFileURL, options: .atomic)
        } catch {
            print("Failed to save bookmarks data: \(error.localizedDescription)")
        }
    }
    
    private func seedDefaults() {
        let mediaCat = BookmarkCategory(name: "Media", icon: "play.tv.fill", sortOrder: 0)
        let systemCat = BookmarkCategory(name: "Systems", icon: "server.rack", sortOrder: 1)
        self.categories = [mediaCat, systemCat]
    }
    
    // MARK: - Category Interactions
    
    func addCategory(_ newCategory: BookmarkCategory) {
        var category = newCategory
        if category.sortOrder == 0 {
            category.sortOrder = categories.count
        }
        categories.append(category)
        saveData()
    }
    
    func updateCategory(_ updated: BookmarkCategory) {
        if let index = categories.firstIndex(where: { $0.id == updated.id }) {
            categories[index] = updated
            saveData()
        }
    }
    
    func deleteCategory(_ id: UUID) {
        categories.removeAll { $0.id == id }
        // Also wipe out bookmarks inside that category
        bookmarks.removeAll { $0.categoryId == id }
        saveData()
    }
    
    func moveCategories(from source: IndexSet, to destination: Int) {
        categories.move(fromOffsets: source, toOffset: destination)
        // Recalculate sortOrders
        for (index, cat) in categories.enumerated() {
            categories[index].sortOrder = index
        }
        saveData()
    }
    
    // MARK: - Bookmark Interactions
    
    func getBookmarks(for categoryId: UUID) -> [Bookmark] {
        bookmarks.filter { $0.categoryId == categoryId }.sorted(by: { $0.sortOrder < $1.sortOrder })
    }
    
    func addBookmark(_ newBookmark: Bookmark) {
        var bookmark = newBookmark
        if bookmark.sortOrder == 0 {
            bookmark.sortOrder = getBookmarks(for: bookmark.categoryId).count
        }
        bookmarks.append(bookmark)
        saveData()
    }
    
    func updateBookmark(_ updated: Bookmark) {
        if let index = bookmarks.firstIndex(where: { $0.id == updated.id }) {
            bookmarks[index] = updated
            saveData()
        }
    }
    
    func deleteBookmark(_ id: UUID) {
        bookmarks.removeAll { $0.id == id }
        saveData()
    }
    
    func moveBookmarks(in categoryId: UUID, from source: IndexSet, to destination: Int) {
        var categoryBookmarks = getBookmarks(for: categoryId)
        categoryBookmarks.move(fromOffsets: source, toOffset: destination)
        
        // Overwrite updated sort orders into the main container
        for (index, bm) in categoryBookmarks.enumerated() {
            if let mainIndex = bookmarks.firstIndex(where: { $0.id == bm.id }) {
                bookmarks[mainIndex].sortOrder = index
            }
        }
        saveData()
    }
}

// MARK: - Storage Wrapper

fileprivate struct BookmarksDataWrapper: Codable {
    let categories: [BookmarkCategory]
    let bookmarks: [Bookmark]
}
