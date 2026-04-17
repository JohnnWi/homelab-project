import SwiftUI

struct BookmarkFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(Localizer.self) private var localizer
    @Bindable private var bookmarkManager = BookmarkManager.shared

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
                    if bookmarkManager.categories.isEmpty {
                        Text(localizer.t.categoryEmpty)
                            .foregroundStyle(.secondary)
                    } else {
                        Picker(localizer.t.bookmarkCategory, selection: $categoryId) {
                            ForEach(bookmarkManager.categories) { category in
                                Text(category.name).tag(category.id as UUID?)
                            }
                        }
                    }
                }
                
                Section(header: Text(localizer.t.bookmarkIcon)) {
                    Toggle(localizer.t.bookmarkUseFavicon, isOn: $useFavicon)
                    
                    if !useFavicon {
                        TextField(localizer.t.bookmarkSfSymbolPrompt, text: $iconValue)
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
                } else if !bookmarkManager.categories.isEmpty {
                    categoryId = bookmarkManager.categories.first?.id
                }
            }
        }
    }
    
    private func save() {
        guard let catId = categoryId else { return }
        
        let type: BookmarkIconType = useFavicon ? .favicon : .systemSymbol
        let val: String = useFavicon ? "" : iconValue
        let tags: [String] = []

        if let bm = bookmarkToEdit {
            bookmarkManager.updateBookmark(bm, newTitle: title, newDescription: description.isEmpty ? nil : description, newUrl: url, newCategoryId: catId, newIconType: type, newIconValue: val, newTags: tags)
        } else {
            bookmarkManager.addBookmark(title: title, description: description.isEmpty ? nil : description, url: url, categoryId: catId, iconType: type, iconValue: val, tags: tags)
        }
        dismiss()
    }
}
