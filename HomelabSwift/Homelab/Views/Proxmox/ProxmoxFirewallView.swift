import SwiftUI

struct ProxmoxFirewallView: View {
    let instanceId: UUID
    let scope: FirewallScope

    enum FirewallScope: Hashable {
        case cluster
        case node(String)
        case guest(node: String, vmid: Int, guestType: ProxmoxGuestType)

        var apiPath: String {
            switch self {
            case .cluster: return "/api2/json/cluster/firewall/rules"
            case .node(let n): return "/api2/json/nodes/\(n)/firewall/rules"
            case .guest(let n, let vmid, let t):
                return "/api2/json/nodes/\(n)/\(t.rawValue)/\(vmid)/firewall/rules"
            }
        }
    }

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var rules: [ProxmoxFirewallRule] = []
    @State private var options: ProxmoxFirewallOptions?
    @State private var state: LoadableState<Void> = .idle
    @State private var showAddRule = false
    @State private var showError: String?
    @State private var showToggleConfirm: Bool?

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchData
        ) {
            // Firewall status (cluster only)
            if case .cluster = scope, let opts = options {
                firewallStatusSection(opts)
            }

            // Rules list
            rulesSection

            // Add rule button
            addRuleButton
        }
        .navigationTitle(navigationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showAddRule) {
            ProxmoxAddFirewallRuleSheet(instanceId: instanceId, scope: scope) {
                Task { await fetchData() }
            }
        }
        .alert(localizer.t.error, isPresented: .init(
            get: { showError != nil },
            set: { if !$0 { showError = nil } }
        )) {
            Button(localizer.t.done) { showError = nil }
        } message: {
            Text(showError ?? "")
        }
        .alert(localizer.t.proxmoxFirewallToggle, isPresented: .init(
            get: { showToggleConfirm != nil },
            set: { if !$0 { showToggleConfirm = nil } }
        )) {
            Button(localizer.t.cancel, role: .cancel) { showToggleConfirm = nil }
            Button(showToggleConfirm == true ? localizer.t.actionStart : localizer.t.actionStop, role: .destructive) {
                if let enable = showToggleConfirm {
                    Task { await toggleFirewall(currentlyEnabled: enable) }
                    showToggleConfirm = nil
                }
            }
        } message: {
            Text(showToggleConfirm == true ? localizer.t.proxmoxFirewallEnableConfirm : localizer.t.proxmoxFirewallDisableConfirm)
        }
        .task { await fetchData() }
    }

    private var navigationTitle: String {
        switch scope {
        case .cluster:
            return "\(localizer.t.proxmoxFirewall) \(localizer.t.proxmoxClusterLabel)"
        case .node(let node):
            return "\(node) \(localizer.t.proxmoxFirewall)"
        case .guest(_, let vmid, let type):
            return "\(type == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc) \(vmid) \(localizer.t.proxmoxFirewall)"
        }
    }

    // MARK: - Firewall Status

    private func firewallStatusSection(_ opts: ProxmoxFirewallOptions) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxFirewallStatus)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            HStack(spacing: 16) {
                VStack(spacing: 6) {
                    Image(systemName: opts.isEnabled ? "flame.fill" : "flame")
                        .font(.title2)
                        .foregroundStyle(opts.isEnabled ? .orange : AppTheme.textMuted)
                        .symbolEffect(.bounce, value: opts.isEnabled)

                    Text(opts.isEnabled ? localizer.t.piholeEnabled : localizer.t.piholeDisabled)
                        .font(.caption.bold())
                        .foregroundStyle(opts.isEnabled ? AppTheme.running : AppTheme.stopped)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .glassCard()
                .onTapGesture {
                    showToggleConfirm = !opts.isEnabled
                }

                VStack(spacing: 6) {
                    HStack(spacing: 4) {
                        Text("\(localizer.t.proxmoxIn):")
                            .font(.caption2.bold())
                            .foregroundStyle(AppTheme.textMuted)
                        Text(opts.policy_in?.uppercased() ?? "DROP")
                            .font(.caption.bold())
                            .foregroundStyle(policyColor(opts.policy_in))
                    }
                    HStack(spacing: 4) {
                        Text("\(localizer.t.proxmoxOut):")
                            .font(.caption2.bold())
                            .foregroundStyle(AppTheme.textMuted)
                        Text(opts.policy_out?.uppercased() ?? "ACCEPT")
                            .font(.caption.bold())
                            .foregroundStyle(policyColor(opts.policy_out))
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .glassCard()
            }
        }
    }

    // MARK: - Rules List

    private var rulesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(localizer.t.proxmoxRules) (\(rules.count))")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
            }

            if rules.isEmpty {
                HStack {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "shield.slash")
                            .font(.title2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(localizer.t.proxmoxNoRules)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .padding(.vertical, 30)
                    Spacer()
                }
                .glassCard()
            } else {
                VStack(spacing: 0) {
                    ForEach(rules) { rule in
                        firewallRuleRow(rule)
                        if rule.id != rules.last?.id {
                            Divider().padding(.leading, 50)
                        }
                    }
                }
                .glassCard()
            }
        }
    }

    private func firewallRuleRow(_ rule: ProxmoxFirewallRule) -> some View {
        HStack(spacing: 10) {
            // Direction badge
            Text(rule.displayDirection)
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(
                    rule.type == "in" ? Color.blue : (rule.type == "out" ? Color.orange : Color.purple),
                    in: RoundedRectangle(cornerRadius: 4, style: .continuous)
                )

            // Action badge
            Text(rule.displayAction)
                .font(.system(size: 9, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(
                    actionColor(rule.action),
                    in: RoundedRectangle(cornerRadius: 4, style: .continuous)
                )

            VStack(alignment: .leading, spacing: 2) {
                if let macro = rule.macro, !macro.isEmpty {
                    Text(macro)
                        .font(.subheadline.bold())
                        .lineLimit(1)
                }

                HStack(spacing: 4) {
                    if let proto = rule.proto, !proto.isEmpty {
                        Text(proto.uppercased())
                            .font(.system(size: 9, weight: .medium, design: .monospaced))
                            .foregroundStyle(proxmoxColor)
                    }
                    if let dport = rule.dport, !dport.isEmpty {
                        Text(":\(dport)")
                            .font(.system(size: 9, weight: .medium, design: .monospaced))
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    if let source = rule.source, !source.isEmpty {
                        Text("← \(source)")
                            .font(.system(size: 9, design: .monospaced))
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(1)
                    }
                }

                if let comment = rule.comment, !comment.isEmpty {
                    Text(comment)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }

            Spacer()

            // Enabled indicator
            Circle()
                .fill(rule.isEnabled ? AppTheme.running : AppTheme.stopped)
                .frame(width: 7, height: 7)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .contextMenu {
            Button(role: .destructive) {
                Task { await deleteRule(rule) }
            } label: {
                Label(localizer.t.delete, systemImage: "trash")
            }
        }
    }

    // MARK: - Add Rule Button

    private var addRuleButton: some View {
        Button {
            showAddRule = true
        } label: {
            HStack {
                Image(systemName: "plus.circle.fill")
                Text(localizer.t.proxmoxAddRule)
                    .font(.subheadline.bold())
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .foregroundStyle(proxmoxColor)
        }
        .glassCard()
    }

    // MARK: - Helpers

    private func actionColor(_ action: String?) -> Color {
        switch action?.uppercased() {
        case "ACCEPT": return AppTheme.running
        case "DROP": return AppTheme.stopped
        case "REJECT": return .orange
        default: return AppTheme.textMuted
        }
    }

    private func policyColor(_ policy: String?) -> Color {
        switch policy?.uppercased() {
        case "ACCEPT": return AppTheme.running
        case "DROP": return AppTheme.stopped
        case "REJECT": return .orange
        default: return AppTheme.textMuted
        }
    }

    // MARK: - Data

    private func fetchData() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }

            switch scope {
            case .cluster:
                async let rulesTask = client.getClusterFirewallRules()
                async let optsTask = client.getClusterFirewallOptions()
                rules = try await rulesTask
                options = try await optsTask
            case .node(let n):
                rules = try await client.getNodeFirewallRules(node: n)
            case .guest(let n, let vmid, let t):
                rules = try await client.getGuestFirewallRules(node: n, vmid: vmid, guestType: t.rawValue)
            }

            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func toggleFirewall(currentlyEnabled: Bool) async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
        do {
            try await client.setClusterFirewallEnable(!currentlyEnabled)
            await fetchData()
        } catch {
            showError = error.localizedDescription
        }
    }

    private func deleteRule(_ rule: ProxmoxFirewallRule) async {
        guard let pos = rule.pos,
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
        do {
            try await client.deleteFirewallRule(path: scope.apiPath, pos: pos)
            await fetchData()
        } catch {
            showError = error.localizedDescription
        }
    }
}

// MARK: - Add Firewall Rule Sheet

struct ProxmoxAddFirewallRuleSheet: View {
    let instanceId: UUID
    let scope: ProxmoxFirewallView.FirewallScope
    var onSave: () -> Void

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    @State private var direction: String = "in"
    @State private var action: String = "ACCEPT"
    @State private var proto: String = ""
    @State private var dport: String = ""
    @State private var source: String = ""
    @State private var dest: String = ""
    @State private var comment: String = ""
    @State private var macro: String = ""
    @State private var isEnabled = true
    @State private var isSaving = false
    @State private var errorMessage: String?

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    private let directions = ["in", "out"]
    private let actions = ["ACCEPT", "DROP", "REJECT"]
    private let macros = [
        "", "SSH", "HTTP", "HTTPS", "DNS", "Ping", "SMTP", "SMTPS", "IMAP", "IMAPS",
        "FTP", "NTP", "MySQL", "PostgreSQL", "RDP", "SNMP", "SMB", "TFTP", "Telnet",
        "VNC", "Ceph", "DHCPfwd", "GRE", "LDAP", "LDAPS", "Rsync", "Git", "MSSQL",
        "Redis", "SIEVE", "SIP", "Squid"
    ]

    var body: some View {
        NavigationStack {
            Form {
                Section("\(localizer.t.proxmoxDirection) & \(localizer.t.actionConfirm)") {
                    Picker(localizer.t.proxmoxDirection, selection: $direction) {
                        ForEach(directions, id: \.self) { d in
                            Text(d.uppercased()).tag(d)
                        }
                    }
                    .pickerStyle(.segmented)

                    Picker(localizer.t.proxmoxActions, selection: $action) {
                        ForEach(actions, id: \.self) { a in
                            Text(a).tag(a)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                Section(localizer.t.proxmoxProtocol) {
                    Picker("Macro", selection: $macro) {
                        ForEach(macros, id: \.self) { m in
                            Text(m.isEmpty ? localizer.t.proxmoxNone : m).tag(m)
                        }
                    }

                    if macro.isEmpty {
                        TextField(localizer.t.proxmoxProtocol, text: $proto)
                            .textInputAutocapitalization(.never)
                        TextField(localizer.t.proxmoxDestinationPort, text: $dport)
                            .textInputAutocapitalization(.never)
                    }
                }

                Section(localizer.t.proxmoxAddresses) {
                    TextField("\(localizer.t.proxmoxSource) (IP/CIDR)", text: $source)
                        .textInputAutocapitalization(.never)
                    TextField("\(localizer.t.proxmoxDestination) (IP/CIDR)", text: $dest)
                        .textInputAutocapitalization(.never)
                }

                Section(localizer.t.proxmoxOptions) {
                    Toggle(localizer.t.proxmoxEnabled, isOn: $isEnabled)
                    TextField(localizer.t.proxmoxComment, text: $comment)
                }

                if let errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                            .font(.caption)
                    }
                }
            }
            .navigationTitle(localizer.t.proxmoxAddRule)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(localizer.t.save) {
                        Task { await saveRule() }
                    }
                    .disabled(isSaving)
                }
            }
        }
    }

    private func saveRule() async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
        isSaving = true
        defer { isSaving = false }

        var params: [String: String] = [
            "type": direction,
            "action": action,
            "enable": isEnabled ? "1" : "0"
        ]
        if !macro.isEmpty { params["macro"] = macro }
        if !proto.isEmpty && macro.isEmpty { params["proto"] = proto }
        if !dport.isEmpty && macro.isEmpty { params["dport"] = dport }
        if !source.isEmpty { params["source"] = source }
        if !dest.isEmpty { params["dest"] = dest }
        if !comment.isEmpty { params["comment"] = comment }

        do {
            try await client.createFirewallRule(path: scope.apiPath, params: params)
            onSave()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
