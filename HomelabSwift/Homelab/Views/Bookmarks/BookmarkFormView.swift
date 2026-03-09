import SwiftUI

struct BookmarkFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(BookmarksStore.self) private var store
    @Environment(Localizer.self) private var localizer
    
    var bookmarkToEdit: Bookmark?
    
    @State private var title: String = ""
    @State private var description: String = ""
    @State private var url: String = ""
    @State private var categoryId: UUID?
    
    // Icon selection
    @State private var useFavicon: Bool = true
    @State private var iconValue: String = ""
    
    var isEditing: Bool { bookmarkToEdit != nil }
    
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
                }
                
                Section {
                    if store.categories.isEmpty {
                        Text(localizer.t.categoryEmpty)
                            .foregroundStyle(.secondary)
                    } else {
                        Picker(localizer.t.bookmarkCategory, selection: $categoryId) {
                            ForEach(store.categories) { category in
                                Text(category.name).tag(category.id as UUID?)
                            }
                        }
                    }
                }
                
                Section(header: Text(localizer.t.bookmarkIcon)) {
                    Toggle("Use Website Favicon", isOn: $useFavicon)
                    
                    if !useFavicon {
                        TextField("SF Symbol Name (e.g., 'link', 'star.fill')", text: $iconValue)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                    }
                }
            }
            .navigationTitle(isEditing ? localizer.t.bookmarkEdit : localizer.t.bookmarkAdd)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) {
                        save()
                    }
                    .disabled(title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || categoryId == nil)
                }
            }
            .onAppear {
                if let bm = bookmarkToEdit {
                    title = bm.title
                    url = bm.url
                    description = bm.description ?? ""
                    categoryId = bm.categoryId
                    useFavicon = (bm.iconType == .favicon)
                    if bm.iconType == .systemSymbol {
                        iconValue = bm.iconValue
                    }
                } else if !store.categories.isEmpty {
                    categoryId = store.categories.first?.id
                }
            }
        }
    }
    
    private func save() {
        guard let catId = categoryId else { return }
        
        let type: BookmarkIconType = useFavicon ? .favicon : .systemSymbol
        let val: String = useFavicon ? "" : iconValue
        
        if let bm = bookmarkToEdit {
            var updated = bm
            updated.title = title
            updated.url = url
            updated.description = description.isEmpty ? nil : description
            updated.categoryId = catId
            updated.iconType = type
            updated.iconValue = val
            store.updateBookmark(updated)
        } else {
            let newBm = Bookmark(
                categoryId: catId,
                title: title,
                description: description.isEmpty ? nil : description,
                url: url,
                iconType: type,
                iconValue: val,
                sortOrder: 0 // Handled in store
            )
            store.addBookmark(newBm)
        }
        dismiss()
    }
}
