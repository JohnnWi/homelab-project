import SwiftUI

struct ProxmoxStorageContentView: View {
    let instanceId: UUID
    let nodeName: String
    let storageName: String
    let storageType: String?

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var contents: [ProxmoxStorageContent] = []
    @State private var state: LoadableState<Void> = .idle
    @State private var filter: ContentFilter = .all
    @State private var showDeleteConfirm: ProxmoxStorageContent?
    @State private var showRestoreSheet: ProxmoxStorageContent?
    @State private var showError: String?
    @State private var restoreNodeOptions: [String] = []
    @State private var restorePoolOptions: [String] = []
    @State private var restoreStorageOptions: [ProxmoxStorage] = []
    @State private var restoreTargetNode = ""
    @State private var restoreStorage = ""
    @State private var restoreVmid = ""
    @State private var restorePool = ""
    @State private var restoreUnique = true
    @State private var restoreForce = false
    @State private var restoreLoadingContext = false
    @State private var trackedRestore: ProxmoxStorageRestoreTrackedTask?

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    enum ContentFilter: CaseIterable {
        case all
        case images
        case backups
        case iso
        case templates
        case snippets
    }

    private var filteredContents: [ProxmoxStorageContent] {
        guard filter != .all else { return contents }
        return contents.filter { item in
            switch filter {
            case .all: return true
            case .images: return item.content == "images" || item.content == "rootdir"
            case .backups: return item.content == "backup"
            case .iso: return item.content == "iso"
            case .templates: return item.content == "vztmpl"
            case .snippets: return item.content == "snippets"
            }
        }
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: { await fetchContent() }
        ) {
            storageInfoBanner

            if let trackedRestore {
                restoreTaskSection(trackedRestore)
            }

            filterBar

            contentList
        }
        .navigationTitle(storageName)
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: ProxmoxRoute.self) { route in
            switch route {
            case .guestDetail(let instanceId, let nodeName, let vmid, let guestType):
                ProxmoxGuestDetailView(instanceId: instanceId, nodeName: nodeName, vmid: vmid, guestType: guestType)
            case .taskLog(let instanceId, let nodeName, let task):
                ProxmoxTaskLogView(instanceId: instanceId, nodeName: nodeName, task: task)
            default:
                EmptyView()
            }
        }
        .alert(localizer.t.proxmoxDeleteVolume, isPresented: .init(
            get: { showDeleteConfirm != nil },
            set: { if !$0 { showDeleteConfirm = nil } }
        )) {
            Button(localizer.t.cancel, role: .cancel) { showDeleteConfirm = nil }
            Button(localizer.t.delete, role: .destructive) {
                if let item = showDeleteConfirm {
                    Task { await deleteContent(item) }
                }
            }
        } message: {
            Text(String(format: localizer.t.proxmoxDeleteVolumeMessage, showDeleteConfirm?.volid ?? storageName))
        }
        .alert(localizer.t.error, isPresented: .init(
            get: { showError != nil },
            set: { if !$0 { showError = nil } }
        )) {
            Button(localizer.t.done) { showError = nil }
        } message: {
            Text(showError ?? "")
        }
        .sheet(item: $showRestoreSheet) { item in
            restoreSheet(for: item)
        }
        .task { await fetchContent() }
        .task(id: trackedRestore?.id) {
            guard trackedRestore?.isRunning == true else { return }
            while true {
                try? await Task.sleep(for: .seconds(2.5))
                guard !Task.isCancelled else { break }
                await refreshRestoreTask()
                guard trackedRestore?.isRunning == true else { break }
            }
        }
        .task(id: restoreTargetNode) {
            guard let metadata = showRestoreSheet?.backupMetadata,
                  !restoreTargetNode.isEmpty else { return }
            await loadRestoreStorages(for: restoreTargetNode, guestType: metadata.guestType)
        }
    }

    // MARK: - Storage Info

    private var storageInfoBanner: some View {
        HStack(spacing: 14) {
            Image(systemName: "externaldrive.fill")
                .font(.title2)
                .foregroundStyle(proxmoxColor)

            VStack(alignment: .leading, spacing: 3) {
                Text(storageName)
                    .font(.headline)
                HStack(spacing: 8) {
                    if let storageType {
                        Text(storageType)
                            .font(.caption2.bold())
                            .foregroundStyle(proxmoxColor)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                    }
                    Text("\(contents.count) \(localizer.t.proxmoxItems)")
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 3) {
                let totalSize = contents.compactMap(\.size).reduce(0, +)
                Text(Formatters.formatBytes(Double(totalSize)))
                    .font(.subheadline.bold())
                Text(localizer.t.proxmoxTotal)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
        .padding(14)
        .glassCard()
    }

    // MARK: - Restore Task

    private func restoreTaskSection(_ trackedTask: ProxmoxStorageRestoreTrackedTask) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxCurrentOperation.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 12) {
                    Image(systemName: trackedTask.iconName)
                        .font(.title3.bold())
                        .foregroundStyle(trackedTask.tintColor)
                        .frame(width: 42, height: 42)
                        .background(trackedTask.tintColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .symbolEffect(.pulse, isActive: trackedTask.isRunning)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(localizer.t.proxmoxRestoreBackup)
                            .font(.headline)
                        Text(trackedTask.statusLabel(localizer))
                            .font(.caption)
                            .foregroundStyle(AppTheme.textSecondary)
                    }

                    Spacer()

                    Text(trackedTask.statusLabel(localizer))
                        .font(.caption.bold())
                        .foregroundStyle(trackedTask.tintColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(trackedTask.tintColor.opacity(0.12), in: Capsule())
                }

                VStack(spacing: 0) {
                    restoreInfoRow(label: localizer.t.proxmoxTaskIdentifier, value: trackedTask.reference.upid, multiline: true)
                    Divider().padding(.leading, 16)
                    restoreInfoRow(label: localizer.t.proxmoxTargetNode, value: trackedTask.guestNodeName)
                    Divider().padding(.leading, 16)
                    restoreInfoRow(
                        label: localizer.t.proxmoxRestoreGuestType,
                        value: trackedTask.guestType == .qemu ? localizer.t.proxmoxGuestTypeQemu : localizer.t.proxmoxGuestTypeLxc
                    )
                    Divider().padding(.leading, 16)
                    restoreInfoRow(label: localizer.t.proxmoxVmidLabel, value: "\(trackedTask.vmid)")
                    Divider().padding(.leading, 16)
                    restoreInfoRow(label: localizer.t.proxmoxLastUpdate, value: trackedTask.formattedLastUpdated)
                    if let exitstatus = trackedTask.task.exitstatus, !trackedTask.isRunning {
                        Divider().padding(.leading, 16)
                        restoreInfoRow(label: localizer.t.proxmoxExitStatus, value: exitstatus)
                    }
                }
                .glassCard()

                HStack(spacing: 10) {
                    NavigationLink(
                        value: ProxmoxRoute.taskLog(
                            instanceId: instanceId,
                            nodeName: trackedTask.taskNode,
                            task: trackedTask.navigationTask
                        )
                    ) {
                        actionPill(icon: "doc.text.magnifyingglass", title: localizer.t.proxmoxOpenTaskLog, color: proxmoxColor)
                    }
                    .buttonStyle(.plain)

                    if trackedTask.task.isOk && !trackedTask.isRunning {
                        NavigationLink(
                            value: ProxmoxRoute.guestDetail(
                                instanceId: instanceId,
                                nodeName: trackedTask.guestNodeName,
                                vmid: trackedTask.vmid,
                                guestType: trackedTask.guestType
                            )
                        ) {
                            actionPill(icon: "arrow.right.circle.fill", title: localizer.t.proxmoxOpenCreatedGuest, color: trackedTask.tintColor)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func actionPill(icon: String, title: String, color: Color) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.body.bold())
            Text(title)
                .font(.subheadline.bold())
                .lineLimit(1)
                .minimumScaleFactor(0.75)
            Spacer(minLength: 0)
        }
        .foregroundStyle(color)
        .padding(14)
        .frame(maxWidth: .infinity)
        .glassCard(tint: color.opacity(0.08))
    }

    private func restoreInfoRow(label: String, value: String, multiline: Bool = false) -> some View {
        HStack(alignment: multiline ? .top : .center, spacing: 12) {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)

            Spacer(minLength: 12)

            Text(value)
                .font(.subheadline.weight(.medium))
                .multilineTextAlignment(.trailing)
                .lineLimit(multiline ? 3 : 1)
                .textSelection(.enabled)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // MARK: - Filter Bar

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(ContentFilter.allCases, id: \.self) { f in
                    let count = countForFilter(f)
                    Button {
                        withAnimation(.spring(duration: 0.25)) { filter = f }
                    } label: {
                        HStack(spacing: 4) {
                            Text(labelForFilter(f))
                                .font(.caption.bold())
                            if count > 0 {
                                Text("\(count)")
                                    .font(.system(size: 9, weight: .bold, design: .rounded))
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 1)
                                    .background(
                                        (filter == f ? Color.white : proxmoxColor).opacity(0.3),
                                        in: Capsule()
                                    )
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .foregroundStyle(filter == f ? .white : .primary)
                        .background(filter == f ? proxmoxColor : Color.clear, in: Capsule())
                        .overlay(
                            Capsule()
                                .strokeBorder(filter == f ? Color.clear : AppTheme.textMuted.opacity(0.3), lineWidth: 1)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    // MARK: - Content List

    private var contentList: some View {
        VStack(alignment: .leading, spacing: 8) {
            if filteredContents.isEmpty {
                HStack {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "tray")
                            .font(.title2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(localizer.t.proxmoxNoContent)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .padding(.vertical, 30)
                    Spacer()
                }
                .glassCard()
            } else {
                ForEach(filteredContents) { item in
                    contentRow(item)
                }
            }
        }
    }

    private func contentRow(_ item: ProxmoxStorageContent) -> some View {
        let backupMetadata = item.backupMetadata

        return VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: contentIcon(item.content))
                    .font(.subheadline)
                    .foregroundStyle(contentColor(item.content))
                    .frame(width: 32, height: 32)
                    .background(contentColor(item.content).opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(item.volid.components(separatedBy: "/").last ?? item.volid)
                        .font(.subheadline.bold())
                        .lineLimit(2)

                    HStack(spacing: 6) {
                        Text(item.contentType)
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(contentColor(item.content))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(contentColor(item.content).opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))

                        if let format = item.format {
                            Text(format)
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        if let vmid = item.vmid {
                            Text("VM \(vmid)")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 3) {
                    Text(item.formattedSize)
                        .font(.caption.bold())
                        .foregroundStyle(.primary)
                    Text(item.formattedDate)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }

                if item.isProtected {
                    Image(systemName: "lock.fill")
                        .font(.caption2)
                        .foregroundStyle(.orange)
                }
            }

            if let backupMetadata {
                HStack(spacing: 8) {
                    metadataChip(backupMetadata.guestTypeLabel, color: proxmoxColor)
                    metadataChip("#\(backupMetadata.vmid)", color: .green)
                    Spacer()
                    Button {
                        Task { await prepareRestore(for: item) }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.clockwise.circle.fill")
                            Text(localizer.t.proxmoxRestore)
                        }
                        .font(.caption.bold())
                        .foregroundStyle(proxmoxColor)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 7)
                        .background(proxmoxColor.opacity(0.1), in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(12)
        .glassCard()
        .contextMenu {
            if let backupMetadata {
                Button {
                    Task { await prepareRestore(for: item) }
                } label: {
                    Label(localizer.t.proxmoxRestoreBackup, systemImage: backupMetadata.guestType == .qemu ? "desktopcomputer" : "shippingbox.fill")
                }
            }

            if !item.isProtected {
                Button(role: .destructive) {
                    showDeleteConfirm = item
                } label: {
                    Label(localizer.t.delete, systemImage: "trash")
                }
            }

            Button {
                UIPasteboard.general.string = item.volid
            } label: {
                Label(localizer.t.copy, systemImage: "doc.on.doc")
            }
        }
    }

    private func metadataChip(_ label: String, color: Color) -> some View {
        Text(label)
            .font(.system(size: 10, weight: .bold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.1), in: Capsule())
    }

    // MARK: - Restore Sheet

    private func restoreSheet(for item: ProxmoxStorageContent) -> some View {
        NavigationStack {
            Form {
                if let metadata = item.backupMetadata {
                    Section {
                        restoreInfoRow(label: localizer.t.proxmoxBackupArchive, value: metadata.archiveName, multiline: true)
                        restoreInfoRow(label: localizer.t.proxmoxRestoreGuestType, value: metadata.guestTypeLabel)
                        restoreInfoRow(label: localizer.t.proxmoxRestoreSourceVmid, value: "\(metadata.vmid)")
                    }

                    Section {
                        if restoreLoadingContext {
                            ProgressView()
                                .frame(maxWidth: .infinity, alignment: .center)
                        }

                        Picker(localizer.t.proxmoxTargetNode, selection: $restoreTargetNode) {
                            ForEach(restoreNodeOptions, id: \.self) { node in
                                Text(node).tag(node)
                            }
                        }

                        Picker(localizer.t.proxmoxStorage, selection: $restoreStorage) {
                            ForEach(restoreStorageOptions, id: \.storage) { storage in
                                Text(storage.storage).tag(storage.storage)
                            }
                        }

                        TextField(localizer.t.proxmoxNewVmid, text: $restoreVmid)
                            .keyboardType(.numberPad)

                        Picker(localizer.t.proxmoxPool, selection: $restorePool) {
                            Text(localizer.t.proxmoxNoneValue).tag("")
                            ForEach(restorePoolOptions, id: \.self) { pool in
                                Text(pool).tag(pool)
                            }
                        }

                        Toggle(localizer.t.proxmoxRestoreAsUnique, isOn: $restoreUnique)
                        Toggle(localizer.t.proxmoxForceOverwrite, isOn: $restoreForce)
                    }

                    Section {
                        Button(localizer.t.proxmoxRestoreBackup) {
                            Task {
                                showRestoreSheet = nil
                                await performRestore(item)
                            }
                        }
                        .disabled(
                            restoreLoadingContext ||
                                restoreTargetNode.isEmpty ||
                                restoreStorage.isEmpty ||
                                Int(restoreVmid.trimmingCharacters(in: .whitespacesAndNewlines)) == nil
                        )
                    }
                }
            }
            .navigationTitle(localizer.t.proxmoxRestoreBackup)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.cancel) { showRestoreSheet = nil }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Helpers

    private func contentIcon(_ content: String?) -> String {
        switch content {
        case "images", "rootdir": return "desktopcomputer"
        case "backup": return "externaldrive.badge.timemachine"
        case "iso": return "opticaldisc.fill"
        case "vztmpl": return "square.stack.3d.down.right.fill"
        case "snippets": return "doc.text.fill"
        default: return "doc.fill"
        }
    }

    private func contentColor(_ content: String?) -> Color {
        switch content {
        case "images", "rootdir": return proxmoxColor
        case "backup": return .green
        case "iso": return .blue
        case "vztmpl": return .purple
        case "snippets": return .orange
        default: return AppTheme.textMuted
        }
    }

    private func countForFilter(_ f: ContentFilter) -> Int {
        switch f {
        case .all: return contents.count
        case .images: return contents.filter { $0.content == "images" || $0.content == "rootdir" }.count
        case .backups: return contents.filter { $0.content == "backup" }.count
        case .iso: return contents.filter { $0.content == "iso" }.count
        case .templates: return contents.filter { $0.content == "vztmpl" }.count
        case .snippets: return contents.filter { $0.content == "snippets" }.count
        }
    }

    private func labelForFilter(_ filter: ContentFilter) -> String {
        switch filter {
        case .all:
            return localizer.t.proxmoxAllItems
        case .images:
            return localizer.t.proxmoxImages
        case .backups:
            return localizer.t.proxmoxBackups
        case .iso:
            return localizer.t.proxmoxIsoImages
        case .templates:
            return localizer.t.proxmoxTemplates
        case .snippets:
            return localizer.t.proxmoxSnippets
        }
    }

    private func filteredRestoreStorages(_ storages: [ProxmoxStorage], for guestType: ProxmoxGuestType) -> [ProxmoxStorage] {
        let requiredContentType = guestType == .qemu ? "images" : "rootdir"
        let filtered = storages.filter {
            $0.isEnabled && ($0.contentTypes.contains(requiredContentType) || $0.contentTypes.isEmpty)
        }
        return (filtered.isEmpty ? storages.filter(\.isEnabled) : filtered)
            .sorted { $0.storage.localizedCaseInsensitiveCompare($1.storage) == .orderedAscending }
    }

    // MARK: - Data

    private func fetchContent() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            contents = try await client.getStorageContent(node: nodeName, storage: storageName)
            contents.sort { ($0.ctime ?? 0) > ($1.ctime ?? 0) }
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    private func deleteContent(_ item: ProxmoxStorageContent) async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
        do {
            try await client.deleteStorageContent(node: nodeName, storage: storageName, volume: item.volid)
            await fetchContent()
        } catch {
            showError = error.localizedDescription
        }
    }

    private func prepareRestore(for item: ProxmoxStorageContent) async {
        guard let metadata = item.backupMetadata else { return }
        HapticManager.medium()

        await MainActor.run {
            showRestoreSheet = item
            restoreTargetNode = nodeName
            restoreStorage = ""
            restoreVmid = ""
            restorePool = ""
            restoreUnique = true
            restoreForce = false
        }

        await loadRestoreContext(for: metadata)
    }

    private func loadRestoreContext(for metadata: ProxmoxBackupArchiveMetadata) async {
        restoreLoadingContext = true
        defer { restoreLoadingContext = false }

        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }

            async let nodesTask: [ProxmoxNode]? = try? await client.getNodes()
            async let poolsTask: [ProxmoxPool]? = try? await client.getPools()
            async let nextVmidTask: Int? = try? await client.getNextAvailableVmid()

            let nodeOptions = ((await nodesTask) ?? [])
                .filter(\.isOnline)
                .map(\.node)
                .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }

            restoreNodeOptions = nodeOptions.isEmpty ? [nodeName] : nodeOptions
            if restoreTargetNode.isEmpty || !restoreNodeOptions.contains(restoreTargetNode) {
                restoreTargetNode = restoreNodeOptions.contains(nodeName) ? nodeName : (restoreNodeOptions.first ?? nodeName)
            }

            restorePoolOptions = ((await poolsTask) ?? [])
                .map(\.poolid)
                .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }

            if restoreVmid.isEmpty {
                let suggestedVmid = (await nextVmidTask) ?? metadata.vmid
                restoreVmid = "\(suggestedVmid)"
            }

            await loadRestoreStorages(for: restoreTargetNode, guestType: metadata.guestType)
        }
    }

    private func loadRestoreStorages(for targetNode: String, guestType: ProxmoxGuestType) async {
        guard !targetNode.isEmpty,
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }

        do {
            let storages = try await client.getStorage(node: targetNode)
            let filtered = filteredRestoreStorages(storages, for: guestType)
            restoreStorageOptions = filtered
            if restoreStorage.isEmpty || !filtered.contains(where: { $0.storage == restoreStorage }) {
                restoreStorage = filtered.first?.storage ?? ""
            }
        } catch {
            restoreStorageOptions = []
            restoreStorage = ""
            showError = error.localizedDescription
        }
    }

    private func performRestore(_ item: ProxmoxStorageContent) async {
        guard let metadata = item.backupMetadata,
              let vmid = Int(restoreVmid.trimmingCharacters(in: .whitespacesAndNewlines)),
              !restoreTargetNode.isEmpty,
              !restoreStorage.isEmpty,
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }

        do {
            let reference: ProxmoxTaskReference
            switch metadata.guestType {
            case .qemu:
                reference = try await client.restoreVM(
                    node: restoreTargetNode,
                    request: ProxmoxVMRestoreRequest(
                        vmid: vmid,
                        archiveVolumeId: item.volid,
                        storage: restoreStorage,
                        unique: restoreUnique,
                        force: restoreForce,
                        pool: restorePool.isEmpty ? nil : restorePool
                    )
                )
            case .lxc:
                reference = try await client.restoreLXC(
                    node: restoreTargetNode,
                    request: ProxmoxLXCRestoreRequest(
                        vmid: vmid,
                        archiveVolumeId: item.volid,
                        storage: restoreStorage,
                        unique: restoreUnique,
                        force: restoreForce,
                        pool: restorePool.isEmpty ? nil : restorePool
                    )
                )
            }

            HapticManager.success()
            await beginTrackingRestore(
                reference: reference,
                guestType: metadata.guestType,
                guestNodeName: restoreTargetNode,
                vmid: vmid
            )
        } catch {
            HapticManager.error()
            showError = error.localizedDescription
        }
    }

    private func beginTrackingRestore(
        reference: ProxmoxTaskReference,
        guestType: ProxmoxGuestType,
        guestNodeName: String,
        vmid: Int
    ) async {
        let placeholderTask = ProxmoxTask(
            upid: reference.upid,
            type: localizer.t.proxmoxRestoreBackup,
            status: "running",
            starttime: Int(Date().timeIntervalSince1970),
            node: reference.node ?? guestNodeName
        )

        await MainActor.run {
            trackedRestore = ProxmoxStorageRestoreTrackedTask(
                reference: reference,
                guestType: guestType,
                guestNodeName: guestNodeName,
                vmid: vmid,
                task: placeholderTask,
                lastUpdated: Date()
            )
        }

        await refreshRestoreTask()
    }

    private func refreshRestoreTask() async {
        guard let trackedRestore,
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }

        do {
            let refreshedTask = try await client.getTaskStatus(node: trackedRestore.taskNode, upid: trackedRestore.reference.upid)
            let updatedTask = trackedRestore.updating(with: refreshedTask)

            await MainActor.run {
                self.trackedRestore = updatedTask
            }

            guard updatedTask.didFinish else { return }

            if updatedTask.task.isOk {
                HapticManager.success()
            } else {
                HapticManager.error()
            }
        } catch {
            await MainActor.run {
                self.trackedRestore = trackedRestore.touchingLastUpdated()
            }
        }
    }
}

private struct ProxmoxStorageRestoreTrackedTask: Identifiable {
    let reference: ProxmoxTaskReference
    let guestType: ProxmoxGuestType
    let guestNodeName: String
    let vmid: Int
    let task: ProxmoxTask
    let lastUpdated: Date

    var id: String { reference.upid }

    var taskNode: String {
        task.node ?? reference.node ?? guestNodeName
    }

    var isRunning: Bool {
        task.isRunning
    }

    var didFinish: Bool {
        !task.isRunning
    }

    var iconName: String {
        if task.isRunning {
            return "arrow.triangle.2.circlepath"
        }
        return task.isOk ? "checkmark.circle.fill" : "exclamationmark.triangle.fill"
    }

    var tintColor: Color {
        if task.isRunning {
            return .blue
        }
        return task.isOk ? AppTheme.running : AppTheme.stopped
    }

    var navigationTask: ProxmoxTask {
        task
    }

    var formattedLastUpdated: String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .medium
        return formatter.string(from: lastUpdated)
    }

    @MainActor
    func statusLabel(_ localizer: Localizer) -> String {
        if task.isRunning {
            return localizer.t.proxmoxRunning
        }
        return task.isOk ? localizer.t.proxmoxOk : localizer.t.error
    }

    func updating(with task: ProxmoxTask) -> ProxmoxStorageRestoreTrackedTask {
        ProxmoxStorageRestoreTrackedTask(
            reference: reference,
            guestType: guestType,
            guestNodeName: guestNodeName,
            vmid: vmid,
            task: task,
            lastUpdated: Date()
        )
    }

    func touchingLastUpdated() -> ProxmoxStorageRestoreTrackedTask {
        ProxmoxStorageRestoreTrackedTask(
            reference: reference,
            guestType: guestType,
            guestNodeName: guestNodeName,
            vmid: vmid,
            task: task,
            lastUpdated: Date()
        )
    }
}
