import SwiftUI

struct ProxmoxGuestBackupSheet: View {
    @Environment(Localizer.self) private var localizer

    @Binding var isPresented: Bool
    @Binding var backupStorage: String
    @Binding var backupMode: String
    @Binding var backupCompress: String

    let instanceId: UUID
    let nodeName: String
    let vmid: Int
    let guestType: ProxmoxGuestType
    let availableStorages: [ProxmoxStorage]
    let onBackup: (String, String, String) async -> Void

    private var backupStorageOptions: [ProxmoxStorage] {
        let storagesWithBackupContent = availableStorages.filter {
            $0.isEnabled && ($0.contentTypes.contains("backup") || $0.contentTypes.isEmpty)
        }
        return (storagesWithBackupContent.isEmpty ? availableStorages.filter(\.isEnabled) : storagesWithBackupContent)
            .sorted { $0.storage.localizedCaseInsensitiveCompare($1.storage) == .orderedAscending }
    }

    private var guestKindLabel: String { guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if backupStorageOptions.isEmpty {
                        Text(localizer.t.proxmoxNoBackupStorage)
                            .foregroundStyle(AppTheme.textMuted)
                    } else {
                        Picker(localizer.t.proxmoxStorage, selection: $backupStorage) {
                            ForEach(backupStorageOptions, id: \.storage) { storage in
                                Text(storage.storage).tag(storage.storage)
                            }
                        }
                    }

                    Picker(localizer.t.proxmoxMode, selection: $backupMode) {
                        Text(localizer.t.proxmoxSnapshotMode).tag("snapshot")
                        Text(localizer.t.proxmoxSuspend).tag("suspend")
                        Text(localizer.t.actionStop).tag("stop")
                    }

                    Picker(localizer.t.proxmoxCompression, selection: $backupCompress) {
                        Text(localizer.t.proxmoxZstdLabel).tag("zstd")
                        Text(localizer.t.proxmoxLzoLabel).tag("lzo")
                        Text(localizer.t.proxmoxGzipLabel).tag("gzip")
                    }
                }

                Section {
                    Button(localizer.t.proxmoxStartBackup) {
                        Task {
                            let storage = backupStorage
                            let mode = backupMode
                            let compress = backupCompress
                            isPresented = false
                            await onBackup(storage, mode, compress)
                        }
                    }
                    .disabled(backupStorage.isEmpty)
                }
            }
            .navigationTitle("\(localizer.t.actionBackup) \(guestKindLabel)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { isPresented = false }
                }
            }
        }
        .presentationDetents([.medium])
    }
}
