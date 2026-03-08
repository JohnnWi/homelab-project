import SwiftUI

struct EditCategoryView: View {
    @Environment(\.dismiss) var dismiss
    @Environment(Localizer.self) private var localizer

    let editingCategory: BookmarkCategory?
    let onSave: (String, String?, String?) -> Void

    @State private var name: String = ""
    @State private var icon: String = ""
    @State private var selectedColor: String

    private let predefinedIcons = [
        "folder.fill", "server.rack", "network", "house.fill", "desktopcomputer",
        "laptopcomputer", "display", "tv.fill", "gamecontroller.fill", "gamecontroller",
        "doc.fill", "book.fill", "books.vertical.fill", "archivebox.fill", "tray.fill",
        "chart.bar.fill", "chart.pie.fill", "lock.fill", "key.fill", "camera.fill",
        "video.fill", "music.note", "play.rectangle.fill", "photo.fill", "printer.fill",
        "lightbulb.fill", "powerplug.fill", "terminal.fill", "cpu", "memorychip",
        "wifi.router.fill", "antenna.radiowaves.left.and.right", "externaldrive.fill", "opticaldiscdrive.fill", "xserve",
        "icloud.fill", "cloud.fill", "gearshape.fill", "wrench.and.screwdriver.fill", "hammer.fill",
        "shield.fill", "globe", "map.fill", "bubble.left.and.bubble.right.fill", "envelope.fill",
        "star.fill", "heart.fill", "flag.fill", "bell.fill", "tag.fill",
        "graduationcap.fill", "briefcase.fill", "building.2.fill", "banknote.fill", "creditcard.fill",
        "cart.fill", "bag.fill", "shippingbox.fill", "box.truck.fill", "archivebox.circle.fill",
        "person.2.fill", "person.crop.circle.fill", "person.badge.key.fill", "person.badge.shield.checkmark.fill", "figure.run",
        "fork.knife", "takeoutbag.and.cup.and.straw.fill", "cup.and.saucer.fill", "wineglass.fill", "birthday.cake.fill",
        "cross.case.fill", "bandage.fill", "pills.fill", "stethoscope", "waveform.path.ecg",
        "leaf.fill", "tree.fill", "flame.fill", "drop.fill", "sun.max.fill",
        "moon.stars.fill", "sparkles", "bolt.fill", "atom", "globe.europe.africa.fill",
        "airplane", "car.fill", "bus.fill", "tram.fill", "bicycle",
        "figure.walk", "scooter", "fuelpump.fill", "mappin.and.ellipse", "location.fill",
        "doc.text.image.fill", "folder.badge.gearshape", "tray.full.fill", "server.rack", "rectangle.3.group.bubble.left.fill",
        "person.crop.square.fill.and.at.rectangle", "megaphone.fill", "shield.lefthalf.filled", "clock.arrow.trianglehead.counterclockwise.rotate.90",
        "icloud.and.arrow.down.fill", "photo.stack.fill", "tv.and.mediabox.fill", "chart.line.uptrend.xyaxis", "waveform.badge.magnifyingglass",
        "lock.shield.fill", "person.3.sequence.fill", "checklist.checked", "app.connected.to.app.below.fill", "cube.transparent.fill"
    ]

    init(editingCategory: BookmarkCategory? = nil, onSave: @escaping (String, String?, String?) -> Void) {
        self.editingCategory = editingCategory
        self.onSave = onSave
        _name = State(initialValue: editingCategory?.name ?? "")
        _icon = State(initialValue: editingCategory?.icon ?? "")
        _selectedColor = State(initialValue: editingCategory?.color ?? CategoryColor.defaultColor.rawValue)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(localizer.t.categoryName, text: $name)
                }

                Section(header: Text(localizer.t.bookmarkIcon)) {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 16) {
                        ForEach(predefinedIcons, id: \.self) { iconName in
                            Image(systemName: iconName)
                                .font(.title2)
                                .foregroundStyle(icon == iconName ? AppTheme.background : (selectedColor.isEmpty ? .primary : Color(hex: selectedColor)))
                                .frame(width: 44, height: 44)
                                .background(
                                    Circle()
                                        .fill(icon == iconName ? (selectedColor.isEmpty ? .primary : Color(hex: selectedColor)) : Color.clear)
                                )
                                .onTapGesture {
                                    if icon == iconName {
                                        icon = "" // Deselect
                                    } else {
                                        icon = iconName
                                    }
                                }
                                .animation(.spring(duration: 0.2), value: icon)
                        }
                    }
                    .padding(.vertical, 8)
                }

                Section(header: Text(localizer.t.categoryColor)) {
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 5), spacing: 12) {
                        ForEach(CategoryColor.allCases, id: \.rawValue) { catColor in
                            Circle()
                                .fill(catColor.color)
                                .frame(width: 36, height: 36)
                                .overlay(
                                    Circle()
                                        .strokeBorder(.white, lineWidth: selectedColor == catColor.rawValue ? 3 : 0)
                                )
                                .scaleEffect(selectedColor == catColor.rawValue ? 1.15 : 1.0)
                                .animation(.spring(duration: 0.2), value: selectedColor)
                                .onTapGesture {
                                    selectedColor = catColor.rawValue
                                }
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
            .navigationTitle(editingCategory != nil ? localizer.t.categoryEdit : localizer.t.categoryAdd)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) {
                        onSave(
                            name.trimmingCharacters(in: .whitespaces),
                            icon.isEmpty ? nil : icon.trimmingCharacters(in: .whitespaces),
                            selectedColor
                        )
                        dismiss()
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}
