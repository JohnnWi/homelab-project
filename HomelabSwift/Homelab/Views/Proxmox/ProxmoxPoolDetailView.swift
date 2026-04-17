import SwiftUI

struct ProxmoxPoolDetailView: View {
    let instanceId: UUID
    let poolId: String

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    @State private var poolDetail: ProxmoxPoolDetail?
    @State private var members: [ProxmoxPoolMember] = []
    @State private var state: LoadableState<Void> = .loading
    @State private var showError: String?
    @State private var isEditingComment = false
    @State private var isDeletingPool = false
    @State private var draftComment = ""
    @State private var showDeleteConfirmation = false

    private let proxmoxColor = ServiceType.proxmox.colors.primary
    private var sortedMembers: [ProxmoxPoolMember] {
        members.sorted { ($0.name ?? $0.storage ?? "").localizedCaseInsensitiveCompare($1.name ?? $1.storage ?? "") == .orderedAscending }
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchData
        ) {
            if let detail = poolDetail {
                // Pool info header
                poolInfoHeader(detail)

                // Members section
                membersSection
            }

            // Actions
            actionsSection
        }
        .navigationTitle(poolId)
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: ProxmoxRoute.self) { route in
            switch route {
            case .nodeDetail(let instanceId, let nodeName):
                ProxmoxNodeDetailView(instanceId: instanceId, nodeName: nodeName)
            case .guestDetail(let instanceId, let nodeName, let vmid, let guestType):
                ProxmoxGuestDetailView(instanceId: instanceId, nodeName: nodeName, vmid: vmid, guestType: guestType)
            case .storageContent(let instanceId, let nodeName, let storageName, let storageType):
                ProxmoxStorageContentView(instanceId: instanceId, nodeName: nodeName, storageName: storageName, storageType: storageType)
            case .firewall(let instanceId, let scope):
                ProxmoxFirewallView(instanceId: instanceId, scope: scope.toScope)
            case .network(let instanceId, let nodeName):
                ProxmoxNetworkView(instanceId: instanceId, nodeName: nodeName)
            case .backupJobs(let instanceId):
                ProxmoxBackupJobsView(instanceId: instanceId)
            case .services(let instanceId, let nodeName):
                ProxmoxServicesView(instanceId: instanceId, nodeName: nodeName)
            case .taskLog(let instanceId, let nodeName, let task):
                ProxmoxTaskLogView(instanceId: instanceId, nodeName: nodeName, task: task)
            case .haReplication(let instanceId):
                ProxmoxHAView(instanceId: instanceId)
            case .ceph(let instanceId, let nodeName):
                ProxmoxCephView(instanceId: instanceId, nodeName: nodeName)
            case .createGuest(let instanceId, let preferredNode):
                ProxmoxCreateGuestView(instanceId: instanceId, preferredNode: preferredNode)
            case .clusterResources(let instanceId):
                ProxmoxClusterResourcesView(instanceId: instanceId)
            case .poolDetail(let instanceId, let poolId):
                ProxmoxPoolDetailView(instanceId: instanceId, poolId: poolId)
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
        .alert(localizer.t.proxmoxDeletePool, isPresented: $showDeleteConfirmation) {
            Button(localizer.t.cancel, role: .cancel) {}
            Button(localizer.t.delete, role: .destructive) {
                Task { await deletePool() }
            }
            .disabled(isDeletingPool)
        } message: {
            Text(String(format: localizer.t.proxmoxDeletePoolMessage, poolId))
        }
        .sheet(isPresented: $isEditingComment) {
            NavigationStack {
                Form {
                    Section(localizer.t.proxmoxComment) {
                        TextField(localizer.t.proxmoxComment, text: $draftComment, axis: .vertical)
                            .lineLimit(3...6)
                    }
                }
                .navigationTitle(localizer.t.proxmoxEditComment)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button(localizer.t.cancel) { isEditingComment = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(localizer.t.save) {
                            Task { await saveComment() }
                        }
                    }
                }
            }
            .presentationDetents([.medium])
        }
        .task { await fetchData() }
    }

    // MARK: - Pool Info Header

    private func poolInfoHeader(_ detail: ProxmoxPoolDetail) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(systemName: "square.stack.3d.up.fill")
                    .font(.title2)
                    .foregroundStyle(proxmoxColor)
                    .frame(width: 48, height: 48)
                    .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(poolId)
                        .font(.title3.bold())
                    if let comment = detail.comment, !comment.isEmpty {
                        Text(comment)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                            .lineLimit(2)
                    } else {
                        Text(localizer.t.proxmoxNoComment)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }

                Spacer()
            }

            // Member stats
            HStack(spacing: 12) {
                let guestCount = members.filter { $0.type == "qemu" || $0.type == "lxc" }.count
                let storageCount = members.filter { $0.type == "storage" }.count

                poolStatChip(label: localizer.t.proxmoxGuests, value: "\(guestCount)", icon: "desktopcomputer", color: proxmoxColor)
                poolStatChip(label: localizer.t.proxmoxStorage, value: "\(storageCount)", icon: "externaldrive.fill", color: AppTheme.info)
                poolStatChip(label: localizer.t.proxmoxPoolMembers, value: "\(members.count)", icon: "link", color: AppTheme.paused)
            }
        }
    }

    private func poolStatChip(label: String, value: String, icon: String, color: Color) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(color)
            Text(value)
                .font(.title3.bold())
            Text(label.sentenceCased())
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .glassCard(tint: color.opacity(0.08))
    }

    // MARK: - Members Section

    private var membersSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxPoolMembers.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                Spacer()
                Text("\(members.count)")
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.textMuted)
            }

            if members.isEmpty {
                emptyMembersView
            } else {
                VStack(spacing: 0) {
                    ForEach(sortedMembers) { member in
                        memberRow(member)
                        if member.id != sortedMembers.last?.id {
                            Divider().padding(.leading, 50)
                        }
                    }
                }
                .glassCard()
            }
        }
    }

    private var emptyMembersView: some View {
        VStack(spacing: 12) {
            Image(systemName: "square.stack.3d.up")
                .font(.title2)
                .foregroundStyle(AppTheme.textMuted)
            Text(localizer.t.proxmoxNoPoolMembers)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 30)
        .glassCard()
    }

    private func memberRow(_ member: ProxmoxPoolMember) -> some View {
        Group {
            if member.type == "qemu", let vmid = member.vmid, let node = member.node {
                NavigationLink(value: ProxmoxRoute.guestDetail(instanceId: instanceId, nodeName: node, vmid: vmid, guestType: .qemu)) {
                    enhancedMemberRow(member)
                }
                .buttonStyle(.plain)
            } else if member.type == "lxc", let vmid = member.vmid, let node = member.node {
                NavigationLink(value: ProxmoxRoute.guestDetail(instanceId: instanceId, nodeName: node, vmid: vmid, guestType: .lxc)) {
                    enhancedMemberRow(member)
                }
                .buttonStyle(.plain)
            } else if member.type == "storage", let storage = member.storage, let node = member.node {
                NavigationLink(value: ProxmoxRoute.storageContent(instanceId: instanceId, nodeName: node, storageName: storage, storageType: nil)) {
                    enhancedMemberRow(member)
                }
                .buttonStyle(.plain)
            } else {
                enhancedMemberRow(member)
            }
        }
    }

    private func enhancedMemberRow(_ member: ProxmoxPoolMember) -> some View {
        let isRunning = member.status?.lowercased() == "running" || member.status?.lowercased() == "online"
        let icon = memberIcon(for: member.type)
        let color = memberColor(for: member.type)
        let isNavigable = memberIsNavigable(member)
        let memberName = memberDisplayName(member)

        return HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(isRunning ? color : AppTheme.textMuted)
                .frame(width: 36, height: 36)
                .background((isRunning ? color : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(memberName)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    if let vmid = member.vmid {
                        Text("#\(vmid)")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }

                HStack(spacing: 6) {
                    Circle()
                        .fill(isRunning ? AppTheme.running : AppTheme.stopped)
                        .frame(width: 6, height: 6)
                    Text(memberTypeLabel(for: member.type))
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)

                    if let node = member.node {
                        Text("· \(node)")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
            }

            Spacer()

            if isNavigable {
                Image(systemName: "chevron.right")
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
    }

    // MARK: - Actions

    private var actionsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxActions.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            actionChip(icon: "square.and.pencil", title: localizer.t.proxmoxEditComment, color: proxmoxColor) {
                draftComment = poolDetail?.comment ?? ""
                isEditingComment = true
            }
            .disabled(isDeletingPool)

            actionChip(icon: "trash", title: localizer.t.proxmoxDeletePool, color: AppTheme.danger) {
                showDeleteConfirmation = true
            }
            .disabled(isDeletingPool)
        }
    }

    private func actionChip(icon: String, title: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon)
                    .font(.body.bold())
                Text(title)
                    .font(.subheadline.bold())
                    .lineLimit(1)
                Spacer(minLength: 0)
            }
            .foregroundStyle(color)
            .padding(14)
            .frame(maxWidth: .infinity)
            .glassCard(tint: color.opacity(0.08))
        }
        .buttonStyle(.plain)
    }

    // MARK: - Helpers

    private func memberIcon(for type: String?) -> String {
        switch type {
        case "qemu": return "desktopcomputer"
        case "lxc": return "shippingbox.fill"
        case "storage": return "externaldrive.fill"
        default: return "questionmark.circle"
        }
    }

    private func memberColor(for type: String?) -> Color {
        switch type {
        case "qemu": return proxmoxColor
        case "lxc": return .green
        case "storage": return AppTheme.info
        default: return AppTheme.textMuted
        }
    }

    private func memberTypeLabel(for type: String?) -> String {
        switch type {
        case "qemu": return localizer.t.proxmoxGuestTypeQemu
        case "lxc": return localizer.t.proxmoxGuestTypeLxc
        case "storage": return localizer.t.proxmoxStorage
        default: return localizer.t.proxmoxUnknown
        }
    }

    private func memberDisplayName(_ member: ProxmoxPoolMember) -> String {
        if let name = member.name, !name.isEmpty { return name }
        if let storage = member.storage, !storage.isEmpty { return storage }
        if let vmid = member.vmid { return "\(memberTypeLabel(for: member.type)) \(vmid)" }
        return localizer.t.proxmoxUnknown
    }

    private func memberIsNavigable(_ member: ProxmoxPoolMember) -> Bool {
        if member.type == "storage" {
            return member.storage != nil && member.node != nil
        }
        return member.vmid != nil && member.node != nil
    }

    // MARK: - Data Fetching

    private func fetchData() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            let detail = try await client.getPoolMembers(poolid: poolId)
            poolDetail = detail
            members = detail.members ?? []
            state = .loaded(())
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func saveComment() async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
        do {
            try await client.updatePoolComment(poolid: poolId, comment: draftComment.trimmingCharacters(in: .whitespacesAndNewlines))
            isEditingComment = false
            await fetchData()
        } catch {
            showError = error.localizedDescription
        }
    }

    private func deletePool() async {
        guard !isDeletingPool,
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }

        isDeletingPool = true
        defer { isDeletingPool = false }

        do {
            try await client.deletePool(poolid: poolId)
            dismiss()
        } catch {
            showError = error.localizedDescription
        }
    }
}
