import SwiftUI

struct CategoryFormView: View {
    @Environment(\.dismiss) private var dismiss
    @Bindable private var bookmarkManager = BookmarkManager.shared
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
                    TextField(localizer.t.categorySymbolPlaceholder, text: $icon)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } header: {
                    Text(localizer.t.categoryName)
                } footer: {
                    Text(localizer.t.categorySymbolExample)
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
                    if let cat = categoryToEdit {
                        bookmarkManager.deleteCategory(cat)
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
        let iconValue = icon.isEmpty ? nil : icon
        if let cat = categoryToEdit {
            bookmarkManager.updateCategory(cat, newName: name, newIcon: iconValue, newColor: cat.color)
        } else {
            bookmarkManager.addCategory(name: name, icon: iconValue)
        }
        dismiss()
    }
}
