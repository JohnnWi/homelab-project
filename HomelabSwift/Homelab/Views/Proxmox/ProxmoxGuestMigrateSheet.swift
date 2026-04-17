import SwiftUI

struct ProxmoxGuestMigrateSheet: View {
    @Environment(Localizer.self) private var localizer

    @Binding var isPresented: Bool
    @Binding var migrateTargetNode: String
    @Binding var migrateOnline: Bool

    let nodeName: String
    let vmid: Int
    let guestType: ProxmoxGuestType
    let isRunning: Bool
    let availableNodes: [ProxmoxNode]
    let onMigrate: (String, Bool) async -> Void

    private var guestKindLabel: String { guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc }

    private var migrationTargets: [ProxmoxNode] {
        availableNodes
            .filter { $0.isOnline && $0.node != nodeName }
            .sorted { $0.node.localizedCaseInsensitiveCompare($1.node) == .orderedAscending }
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if migrationTargets.isEmpty {
                        Text(localizer.t.proxmoxNoTargetNodes)
                            .foregroundStyle(AppTheme.textMuted)
                    } else {
                        Picker(localizer.t.proxmoxTargetNode, selection: $migrateTargetNode) {
                            ForEach(migrationTargets, id: \.node) { node in
                                Text(node.node).tag(node.node)
                            }
                        }
                    }

                    Toggle(localizer.t.proxmoxOnlineMigration, isOn: $migrateOnline)
                        .disabled(!isRunning)
                }

                Section {
                    Button(localizer.t.proxmoxStartMigration) {
                        Task {
                            let targetNode = migrateTargetNode
                            let online = migrateOnline
                            isPresented = false
                            await onMigrate(targetNode, online)
                        }
                    }
                    .disabled(migrateTargetNode.isEmpty)
                }
            }
            .navigationTitle("\(localizer.t.proxmoxMigrate) \(guestKindLabel)")
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
