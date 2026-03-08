import SwiftUI

struct CategoryFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(BookmarksStore.self) private var store
    @Environment(Localizer.self) private var localizer
    
    var categoryToEdit: BookmarkCategory?
    
    @State private var name: String = ""
    @State private var icon: String = ""
    @State private var isShowingDeleteConfirm = false
    
    var isEditing: Bool { categoryToEdit != nil }
    
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(localizer.t.categoryName, text: $name)
                    TextField("SF Symbol Name (optional)", text: $icon)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text(localizer.t.categoryName)
                } footer: {
                    Text("Example: 'folder', 'network', 'play.tv.fill'")
                }
                
                if isEditing {
                    Section {
                        Button(role: .destructive) {
                            isShowingDeleteConfirm = true
                        } label: {
                            HStack {
                                Spacer()
                                Text(localizer.t.categoryDelete)
                                Spacer()
                            }
                        }
                    }
                }
            }
            .navigationTitle(isEditing ? localizer.t.categoryEdit : localizer.t.categoryAdd)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) {
                        save()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .onAppear {
                if let cat = categoryToEdit {
                    name = cat.name
                    icon = cat.icon ?? ""
                }
            }
            .alert(localizer.t.categoryDelete, isPresented: $isShowingDeleteConfirm) {
                Button(localizer.t.delete, role: .destructive) {
                    if let id = categoryToEdit?.id {
                        store.deleteCategory(id)
                    }
                    dismiss()
                }
                Button(localizer.t.cancel, role: .cancel) { }
            } message: {
                Text(localizer.t.categoryDeleteConfirm)
            }
        }
    }
    
    private func save() {
        if let cat = categoryToEdit {
            var updated = cat
            updated.name = name
            updated.icon = icon.isEmpty ? nil : icon
            store.updateCategory(updated)
        } else {
            let newCat = BookmarkCategory(name: name, icon: icon.isEmpty ? nil : icon, sortOrder: store.categories.count)
            store.addCategory(newCat)
        }
        dismiss()
    }
}
