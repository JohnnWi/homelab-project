import SwiftUI

struct ProxmoxGuestConfigEditSheet: View {
    @Environment(Localizer.self) private var localizer

    let instanceId: UUID
    let nodeName: String
    let vmid: Int
    let guestType: ProxmoxGuestType
    let currentConfig: ProxmoxGuestConfig?
    let onSave: ([String: String]) -> Void

    @State private var name: String
    @State private var description: String
    @State private var cores: Int
    @State private var sockets: Int
    @State private var memory: Int
    @State private var balloon: Int
    @State private var onboot: Bool
    @State private var protection: Bool

    init(instanceId: UUID, nodeName: String, vmid: Int, guestType: ProxmoxGuestType, currentConfig: ProxmoxGuestConfig?, onSave: @escaping ([String: String]) -> Void) {
        self.instanceId = instanceId
        self.nodeName = nodeName
        self.vmid = vmid
        self.guestType = guestType
        self.currentConfig = currentConfig
        self.onSave = onSave
        _name = State(initialValue: currentConfig?.displayName ?? "")
        _description = State(initialValue: currentConfig?.description ?? "")
        _cores = State(initialValue: currentConfig?.cores ?? 1)
        _sockets = State(initialValue: currentConfig?.sockets ?? 1)
        _memory = State(initialValue: currentConfig?.memory ?? 1024)
        _balloon = State(initialValue: currentConfig?.balloon ?? 0)
        _onboot = State(initialValue: currentConfig?.onboot == 1)
        _protection = State(initialValue: currentConfig?.protection == 1)
    }

    @State private var validationError: String?

    private var guestKindLabel: String { guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc }
    private var isQemu: Bool { guestType == .qemu }
    private var minimumMemory: Int { isQemu ? 256 : 128 }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(localizer.t.proxmoxConfigEditName, text: $name)
                    TextField(localizer.t.proxmoxConfigEditDesc, text: $description)
                }

                Section(header: Text(localizer.t.proxmoxCpuLabel)) {
                    Stepper("\(localizer.t.proxmoxConfigEditCores): \(cores)", value: $cores, in: 1...128)
                    if isQemu {
                        Stepper("\(localizer.t.proxmoxConfigEditSockets): \(sockets)", value: $sockets, in: 1...64)
                    }
                }

                Section(header: Text(localizer.t.proxmoxRamLabel)) {
                    Stepper("\(localizer.t.proxmoxConfigEditMemory): \(memory)", value: $memory, in: minimumMemory...1_048_576, step: 256)
                    if isQemu {
                        Stepper("\(localizer.t.proxmoxConfigEditBalloon): \(balloon)", value: $balloon, in: 0...1_048_576, step: 256)
                    }
                }

                Section {
                    Toggle(localizer.t.proxmoxConfigEditOnBoot, isOn: $onboot)
                    Toggle(localizer.t.proxmoxConfigEditProtection, isOn: $protection)
                }

                if let error = validationError {
                    Section {
                        Text(error)
                            .foregroundStyle(.red)
                            .font(.caption)
                    }
                }

                Section {
                    Button(localizer.t.save) {
                        save()
                    }
                }
            }
            .navigationTitle("\(localizer.t.proxmoxEditConfig) - \(guestKindLabel) \(vmid)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { onSave([:]) }
                }
            }
        }
        .presentationDetents([.large])
    }

    private func save() {
        guard cores >= 1 else {
            validationError = localizer.t.proxmoxConfigSaveError
            return
        }
        guard memory >= minimumMemory else {
            validationError = localizer.t.proxmoxConfigSaveError
            return
        }

        var params: [String: String] = [
            isQemu ? "name" : "hostname": name,
            "cores": "\(cores)",
            "memory": "\(memory)",
            "onboot": onboot ? "1" : "0",
            "protection": protection ? "1" : "0"
        ]

        if isQemu {
            params["sockets"] = "\(sockets)"
            params["balloon"] = "\(balloon)"
        }

        if !description.isEmpty {
            params["description"] = description
        }

        validationError = nil
        onSave(params)
    }
}
