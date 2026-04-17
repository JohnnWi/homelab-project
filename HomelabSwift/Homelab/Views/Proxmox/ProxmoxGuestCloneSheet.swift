import SwiftUI

struct ProxmoxGuestCloneSheet: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @Binding var isPresented: Bool
    @Binding var cloneName: String
    @Binding var cloneVmid: String
    @Binding var cloneFull: Bool
    @Binding var cloneTargetNode: String
    @Binding var cloneTargetStorage: String
    @Binding var clonePool: String

    let instanceId: UUID
    let nodeName: String
    let vmid: Int
    let guestType: ProxmoxGuestType
    let guestName: String
    let nextAvailableVmid: Int?
    let availableNodes: [ProxmoxNode]
    let availablePools: [ProxmoxPool]
    let onClone: (String, Bool, String, String, String) async -> Void
    let onRefreshVmid: () async -> Void

    @State private var cloneStorageOptions: [ProxmoxStorage] = []

    private var cloneTargetNodes: [String] {
        availableNodes
            .filter(\.isOnline)
            .map(\.node)
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    private var guestKindLabel: String { guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker(localizer.t.proxmoxTargetNode, selection: $cloneTargetNode) {
                        ForEach(cloneTargetNodes, id: \.self) { node in
                            Text(node).tag(node)
                        }
                    }

                    TextField(localizer.t.proxmoxNewVmid, text: $cloneVmid)
                        .keyboardType(.numberPad)
                        .submitLabel(.done)
                        .onChange(of: cloneVmid) { _, newValue in
                            let cleaned = newValue.filter { $0.isNumber }
                            if cleaned != newValue {
                                cloneVmid = cleaned
                            }
                        }
                    Button(localizer.t.proxmoxRefreshVmid) {
                        Task { await onRefreshVmid() }
                    }

                    TextField(localizer.t.proxmoxOptionalName, text: $cloneName)

                    Picker(localizer.t.proxmoxStorage, selection: $cloneTargetStorage) {
                        Text(localizer.t.proxmoxUseSourceDefault).tag("")
                        ForEach(cloneStorageOptions, id: \.storage) { storage in
                            Text(storage.storage).tag(storage.storage)
                        }
                    }

                    Picker(localizer.t.proxmoxPool, selection: $clonePool) {
                        Text(localizer.t.proxmoxNoneValue).tag("")
                        ForEach(availablePools, id: \.poolid) { pool in
                            Text(pool.poolid).tag(pool.poolid)
                        }
                    }

                    Toggle(localizer.t.proxmoxFullClone, isOn: $cloneFull)
                }

                Section {
                    Button(localizer.t.proxmoxCreateClone) {
                        Task {
                            let name = cloneName
                            let full = cloneFull
                            let targetNode = cloneTargetNode
                            let storage = cloneTargetStorage
                            let pool = clonePool
                            isPresented = false
                            await onClone(name, full, targetNode, storage, pool)
                        }
                    }
                    .disabled(Int(cloneVmid.trimmingCharacters(in: .whitespacesAndNewlines)) == nil)
                }
            }
            .navigationTitle("\(localizer.t.proxmoxClone) \(guestKindLabel)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { isPresented = false }
                }
            }
        }
        .presentationDetents([.medium])
        .task {
            await loadCloneStorageOptions(for: cloneTargetNode)
        }
        .onChange(of: cloneTargetNode) { _, newValue in
            guard !newValue.isEmpty else { return }
            Task { await loadCloneStorageOptions(for: newValue) }
        }
    }

    private func loadCloneStorageOptions(for nodeName: String) async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }

        do {
            let storages = try await client.getStorage(node: nodeName)
            let requiredContentType = guestType == .qemu ? "images" : "rootdir"
            let filtered = storages.filter {
                $0.isEnabled && ($0.contentTypes.contains(requiredContentType) || $0.contentTypes.isEmpty)
            }
            let resolvedOptions = (filtered.isEmpty ? storages.filter(\.isEnabled) : filtered)
                .sorted { $0.storage.localizedCaseInsensitiveCompare($1.storage) == .orderedAscending }

            await MainActor.run {
                cloneStorageOptions = resolvedOptions
                if !cloneTargetStorage.isEmpty,
                   !resolvedOptions.contains(where: { $0.storage == cloneTargetStorage }) {
                    cloneTargetStorage = ""
                }
            }
        } catch {
            await MainActor.run {
                cloneStorageOptions = []
                cloneTargetStorage = ""
            }
        }
    }
}
