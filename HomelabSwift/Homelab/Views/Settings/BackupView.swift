import SwiftUI
import UniformTypeIdentifiers

// MARK: - Homelab Backup UTType

extension UTType {
    static let homelabBackup = UTType(exportedAs: "com.homelab.backup", conformingTo: .data)
}

// MARK: - BackupView

struct BackupView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer

    // Export state
    @State private var showExportPasswordDialog = false
    @State private var exportPassword = ""
    @State private var exportConfirmPassword = ""
    @State private var exportError: String?
    @State private var exportFileURL: URL?
    @State private var showShareSheet = false
    @State private var isExporting = false
    @State private var selectedExportTypes: Set<ServiceType> = []
    @State private var exportSelectionInitialized = false

    // Import state
    @State private var showFilePicker = false
    @State private var showImportPasswordDialog = false
    @State private var importPassword = ""
    @State private var importError: String?
    @State private var importFileURL: URL?
    @State private var previewResult: BackupPreviewResult?
    @State private var selectedImportTypes: Set<ServiceType> = []
    @State private var showPreview = false
    @State private var isImporting = false

    // Success state
    @State private var showExportSuccess = false
    @State private var showImportSuccess = false
    @State private var importedCount = 0

    private var configuredInstancesByType: [ServiceType: [ServiceInstance]] {
        Dictionary(grouping: servicesStore.allInstances, by: \.type)
    }

    private var configuredTypes: [ServiceType] {
        ServiceType.allCases.filter { type in
            configuredInstancesByType[type]?.isEmpty == false
        }
    }

    private var exportableTypes: [ServiceType] {
        settingsStore.serviceOrder
    }

    private var configuredTypeSet: Set<ServiceType> {
        Set(configuredTypes)
    }

    private var configuredHomeTypeSet: Set<ServiceType> {
        Set(configuredTypes.filter { !$0.isMediaService })
    }

    private var configuredArrTypeSet: Set<ServiceType> {
        Set(configuredTypes.filter(\.isMediaService))
    }

    private var selectedServiceCount: Int {
        configuredInstancesByType.reduce(into: 0) { partialResult, pair in
            if selectedExportTypes.contains(pair.key) {
                partialResult += pair.value.count
            }
        }
    }

    private var hasValidSelection: Bool {
        !configuredTypes.isEmpty && !selectedExportTypes.isEmpty && selectedServiceCount > 0
    }

    private var availableImportTypeSet: Set<ServiceType> {
        previewResult?.knownServiceTypes ?? []
    }

    private var availableImportTypes: [ServiceType] {
        ServiceType.allCases.filter { availableImportTypeSet.contains($0) }
    }

    private var availableImportHomeTypeSet: Set<ServiceType> {
        Set(availableImportTypes.filter { !$0.isMediaService })
    }

    private var availableImportArrTypeSet: Set<ServiceType> {
        Set(availableImportTypes.filter(\.isMediaService))
    }

    private var selectedImportServiceCount: Int {
        guard let previewResult else { return 0 }
        return previewResult.knownServices.reduce(into: 0) { partialResult, entry in
            guard let type = BackupServiceTypeMapper.serviceType(from: entry.type),
                  selectedImportTypes.contains(type) else {
                return
            }
            partialResult += 1
        }
    }

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()

            ScrollView {
                GlassGroup(spacing: 24) {
                    VStack(spacing: 24) {
                        // Title
                        HStack {
                            Text(localizer.t.backupTitle)
                                .font(.system(size: 32, weight: .bold))
                            Spacer()
                        }
                        .padding(.top, 8)

                        infoSection

                        exportSection

                        importSection
                    }
                }
                .padding(16)
                .padding(.bottom, 32)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.homelabBackup, .data],
            allowsMultipleSelection: false
        ) { result in
            handleFilePickerResult(result)
        }
        .sheet(isPresented: $showShareSheet) {
            if let url = exportFileURL {
                ShareSheet(items: [url])
            }
        }
        .alert(localizer.t.backupExportTitle, isPresented: $showExportPasswordDialog) {
            SecureField(localizer.t.backupPasswordPlaceholder, text: $exportPassword)
            SecureField(localizer.t.backupPasswordConfirm, text: $exportConfirmPassword)
            Button(localizer.t.cancel, role: .cancel) { resetExportState() }
            Button(localizer.t.backupExportAction) { performExport() }
        } message: {
            Text(localizer.t.backupPasswordDesc)
        }
        .alert(localizer.t.backupImportTitle, isPresented: $showImportPasswordDialog) {
            SecureField(localizer.t.backupPasswordPlaceholder, text: $importPassword)
            Button(localizer.t.cancel, role: .cancel) { resetImportState() }
            Button(localizer.t.backupImportDecrypt) { performDecrypt() }
        } message: {
            Text(localizer.t.backupImportPasswordDesc)
        }
        .sheet(isPresented: $showPreview, onDismiss: { resetImportState() }) {
            NavigationStack {
                importPreviewSheet
            }
        }
        .alert(localizer.t.error, isPresented: .init(
            get: { exportError != nil || importError != nil },
            set: { if !$0 { exportError = nil; importError = nil } }
        )) {
            Button(localizer.t.confirm) {
                exportError = nil
                importError = nil
            }
        } message: {
            Text(exportError ?? importError ?? "")
        }
        .overlay(alignment: .bottom) {
            if showExportSuccess {
                ToastView(message: localizer.t.backupExportSuccess)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                            withAnimation { showExportSuccess = false }
                        }
                    }
            }
            if showImportSuccess {
                ToastView(message: String(format: localizer.t.backupImportSuccess, importedCount))
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                            withAnimation { showImportSuccess = false }
                        }
                    }
            }
        }
        .onAppear {
            syncExportSelection()
        }
        .onChange(of: configuredTypes) { _, _ in
            syncExportSelection()
        }
    }

    // MARK: - Info Section

    private var infoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                Image(systemName: "shield.lefthalf.filled")
                    .font(.title3)
                    .foregroundStyle(AppTheme.accent)
                Text(localizer.t.backupInfoTitle)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
            }
            Text(localizer.t.backupInfoDesc)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineSpacing(2)
        }
        .padding(16)
        .glassCard(tint: AppTheme.accent.opacity(0.06))
    }

    // MARK: - Export Section

    private var exportSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.backupExportTitle.uppercased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)

            VStack(alignment: .leading, spacing: 16) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(localizer.t.backupSelectionTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(localizer.t.backupSelectionSubtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(localizer.t.backupRememberSelectionTitle)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.primary)
                        Text(localizer.t.backupRememberSelectionSubtitle)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    Toggle(
                        "",
                        isOn: Binding(
                            get: { settingsStore.backupRememberSelectionEnabled },
                            set: { setRememberSelectionEnabled($0) }
                        )
                    )
                    .labelsHidden()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(Color(.tertiarySystemFill).opacity(0.65))
                )

                if configuredTypes.isEmpty {
                    Text(localizer.t.backupSelectionEmpty)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(Color(.tertiarySystemFill).opacity(0.7))
                        )
                }

                FlowLayout(spacing: 8) {
                    selectionChip(
                        title: localizer.t.backupSelectionAll,
                        selected: !configuredTypeSet.isEmpty && configuredTypeSet.isSubset(of: selectedExportTypes),
                        enabled: !configuredTypeSet.isEmpty
                    ) {
                        toggleAllExportTypes()
                    }
                    selectionChip(
                        title: localizer.t.backupSelectionHome,
                        selected: !configuredHomeTypeSet.isEmpty && configuredHomeTypeSet.isSubset(of: selectedExportTypes),
                        enabled: !configuredHomeTypeSet.isEmpty
                    ) {
                        toggleHomeExportTypes()
                    }
                    selectionChip(
                        title: localizer.t.backupSelectionArr,
                        selected: !configuredArrTypeSet.isEmpty && configuredArrTypeSet.isSubset(of: selectedExportTypes),
                        enabled: !configuredArrTypeSet.isEmpty
                    ) {
                        toggleArrExportTypes()
                    }
                }

                FlowLayout(spacing: 8) {
                    ForEach(exportableTypes, id: \.self) { type in
                        selectionChip(
                            title: localizedServiceName(for: type),
                            selected: selectedExportTypes.contains(type),
                            enabled: configuredTypeSet.contains(type)
                        ) {
                            toggleExportType(type)
                        }
                    }
                }

                Text(String(format: localizer.t.backupSelectionSelectedCount, selectedServiceCount))
                    .font(.caption2)
                    .foregroundStyle(.secondary)

                Button {
                    exportPassword = ""
                    exportConfirmPassword = ""
                    exportError = nil
                    showExportPasswordDialog = true
                } label: {
                    HStack(spacing: 16) {
                        Image(systemName: "arrow.up.doc.fill")
                            .font(.title3)
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 40, height: 40)
                            .background(AppTheme.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                        VStack(alignment: .leading, spacing: 2) {
                            Text(localizer.t.backupExportAction)
                                .font(.body.weight(.medium))
                                .foregroundStyle(.primary)
                            Text(String(format: localizer.t.backupExportDesc, selectedServiceCount))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        if isExporting {
                            ProgressView()
                                .tint(AppTheme.accent)
                        } else {
                            Image(systemName: "chevron.right")
                                .font(.caption.bold())
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(!hasValidSelection || isExporting)
            }
            .padding(16)
            .glassCard()
        }
    }

    // MARK: - Import Section

    private var importSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.backupImportTitle.uppercased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)

            Button {
                resetImportState()
                showFilePicker = true
            } label: {
                HStack(spacing: 16) {
                    Image(systemName: "arrow.down.doc.fill")
                        .font(.title3)
                        .foregroundStyle(AppTheme.accent)
                        .frame(width: 40, height: 40)
                        .background(AppTheme.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                    VStack(alignment: .leading, spacing: 2) {
                        Text(localizer.t.backupImportAction)
                            .font(.body.weight(.medium))
                            .foregroundStyle(.primary)
                        Text(localizer.t.backupImportDesc)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    if isImporting {
                        ProgressView()
                            .tint(AppTheme.accent)
                    } else {
                        Image(systemName: "chevron.right")
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(isImporting)
            .glassCard()
        }
    }

    // MARK: - Export Selection

    private func selectionChip(
        title: String,
        selected: Bool,
        enabled: Bool = true,
        tint: Color = AppTheme.accent,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(!enabled ? .secondary : (selected ? tint : .primary))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(selected ? tint.opacity(0.16) : Color(.tertiarySystemFill).opacity(0.65))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(selected ? tint.opacity(0.45) : Color.primary.opacity(0.08), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }

    private func localizedServiceName(for type: ServiceType) -> String {
        switch type {
        case .portainer: return localizer.t.servicePortainer
        case .pihole: return localizer.t.servicePihole
        case .adguardHome: return localizer.t.serviceAdguard
        case .technitium: return ServiceType.technitium.displayName
        case .beszel: return localizer.t.serviceBeszel
        case .healthchecks: return localizer.t.serviceHealthchecks
        case .linuxUpdate: return ServiceType.linuxUpdate.displayName
        case .dockhand: return ServiceType.dockhand.displayName
        case .dockmon: return ServiceType.dockmon.displayName
        case .komodo: return ServiceType.komodo.displayName
        case .maltrail: return ServiceType.maltrail.displayName
        case .craftyController: return ServiceType.craftyController.displayName
        case .gitea: return localizer.t.serviceGitea
        case .nginxProxyManager: return localizer.t.serviceNpm
        case .pangolin: return ServiceType.pangolin.displayName
        case .patchmon: return localizer.t.servicePatchmon
        case .jellystat: return localizer.t.serviceJellystat
        case .plex: return localizer.t.servicePlex
        case .radarr: return localizer.t.serviceRadarr
        case .sonarr: return localizer.t.serviceSonarr
        case .lidarr: return localizer.t.serviceLidarr
        case .qbittorrent: return localizer.t.serviceQbittorrent
        case .jellyseerr: return localizer.t.serviceJellyseerr
        case .prowlarr: return localizer.t.serviceProwlarr
        case .bazarr: return localizer.t.serviceBazarr
        case .gluetun: return localizer.t.serviceGluetun
        case .flaresolverr: return localizer.t.serviceFlaresolverr
        case .wakapi: return localizer.t.serviceWakapi
        case .proxmox: return localizer.t.serviceProxmox
        }
    }

    private func syncExportSelection() {
        let available = configuredTypeSet
        if !exportSelectionInitialized {
            guard !available.isEmpty else { return }
            if settingsStore.backupRememberSelectionEnabled {
                let restored = settingsStore.backupSelectedServiceTypes.intersection(available)
                selectedExportTypes = restored.isEmpty ? available : restored
                settingsStore.backupSelectedServiceTypes = selectedExportTypes
            } else {
                selectedExportTypes = available
            }
            exportSelectionInitialized = true
            return
        }
        let cleaned = selectedExportTypes.intersection(available)
        if cleaned != selectedExportTypes {
            selectedExportTypes = cleaned
            persistSelectionIfNeeded(cleaned)
        }
    }

    private func toggleExportType(_ type: ServiceType) {
        guard configuredTypeSet.contains(type) else { return }
        if selectedExportTypes.contains(type) {
            selectedExportTypes.remove(type)
        } else {
            selectedExportTypes.insert(type)
        }
        persistSelectionIfNeeded()
    }

    private func toggleAllExportTypes() {
        toggleExportGroup(configuredTypeSet)
    }

    private func toggleHomeExportTypes() {
        toggleExportGroup(configuredHomeTypeSet)
    }

    private func toggleArrExportTypes() {
        toggleExportGroup(configuredArrTypeSet)
    }

    private func toggleExportGroup(_ group: Set<ServiceType>) {
        guard !group.isEmpty else { return }
        if group.isSubset(of: selectedExportTypes) {
            selectedExportTypes.subtract(group)
        } else {
            selectedExportTypes.formUnion(group)
        }
        persistSelectionIfNeeded()
    }

    private func setRememberSelectionEnabled(_ enabled: Bool) {
        settingsStore.backupRememberSelectionEnabled = enabled
        if enabled {
            settingsStore.backupSelectedServiceTypes = selectedExportTypes
        } else {
            settingsStore.backupSelectedServiceTypes = []
        }
    }

    private func persistSelectionIfNeeded(_ selection: Set<ServiceType>? = nil) {
        guard settingsStore.backupRememberSelectionEnabled else { return }
        settingsStore.backupSelectedServiceTypes = selection ?? selectedExportTypes
    }

    // MARK: - Import Selection

    @ViewBuilder
    private var importPreviewSheet: some View {
        if let preview = previewResult {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(localizer.t.backupImportPreviewTitle)
                        .font(.title2.weight(.bold))

                    Text(String(format: localizer.t.backupPreviewServices, preview.totalCount))
                        .font(.subheadline)

                    if preview.unknownCount > 0 {
                        Text(String(format: localizer.t.backupPreviewUnknown, preview.unknownCount))
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.warning)
                    }

                    VStack(alignment: .leading, spacing: 4) {
                        Text(localizer.t.backupSelectionTitle)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                        Text(localizer.t.backupSelectionSubtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    FlowLayout(spacing: 8) {
                        selectionChip(
                            title: localizer.t.backupSelectionAll,
                            selected: !availableImportTypeSet.isEmpty && availableImportTypeSet.isSubset(of: selectedImportTypes),
                            enabled: !availableImportTypeSet.isEmpty
                        ) {
                            toggleAllImportTypes()
                        }
                        selectionChip(
                            title: localizer.t.backupSelectionHome,
                            selected: !availableImportHomeTypeSet.isEmpty && availableImportHomeTypeSet.isSubset(of: selectedImportTypes),
                            enabled: !availableImportHomeTypeSet.isEmpty
                        ) {
                            toggleHomeImportTypes()
                        }
                        selectionChip(
                            title: localizer.t.backupSelectionArr,
                            selected: !availableImportArrTypeSet.isEmpty && availableImportArrTypeSet.isSubset(of: selectedImportTypes),
                            enabled: !availableImportArrTypeSet.isEmpty
                        ) {
                            toggleArrImportTypes()
                        }
                    }

                    FlowLayout(spacing: 8) {
                        ForEach(availableImportTypes, id: \.self) { type in
                            selectionChip(
                                title: localizedServiceName(for: type),
                                selected: selectedImportTypes.contains(type)
                            ) {
                                toggleImportType(type)
                            }
                        }
                    }

                    Text(String(format: localizer.t.backupSelectionSelectedCount, selectedImportServiceCount))
                        .font(.caption2)
                        .foregroundStyle(.secondary)

                    Text(localizer.t.backupPreviewWarning)
                        .font(.caption)
                        .foregroundStyle(AppTheme.warning)
                        .padding(.top, 4)

                    Button(role: .destructive) {
                        performImport()
                    } label: {
                        HStack {
                            Spacer()
                            Text(localizer.t.backupImportApply)
                                .font(.body.weight(.semibold))
                            Spacer()
                        }
                    }
                    .disabled(selectedImportTypes.isEmpty || isImporting)
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.danger)
                }
                .padding(20)
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(localizer.t.cancel) {
                        showPreview = false
                    }
                }
            }
        } else {
            EmptyView()
        }
    }

    private func toggleImportType(_ type: ServiceType) {
        guard availableImportTypeSet.contains(type) else { return }
        if selectedImportTypes.contains(type) {
            selectedImportTypes.remove(type)
        } else {
            selectedImportTypes.insert(type)
        }
    }

    private func toggleAllImportTypes() {
        toggleImportGroup(availableImportTypeSet)
    }

    private func toggleHomeImportTypes() {
        toggleImportGroup(availableImportHomeTypeSet)
    }

    private func toggleArrImportTypes() {
        toggleImportGroup(availableImportArrTypeSet)
    }

    private func toggleImportGroup(_ group: Set<ServiceType>) {
        guard !group.isEmpty else { return }
        if group.isSubset(of: selectedImportTypes) {
            selectedImportTypes.subtract(group)
        } else {
            selectedImportTypes.formUnion(group)
        }
    }

    // MARK: - Actions

    private func performExport() {
        guard hasValidSelection else {
            exportError = localizer.t.backupSelectionRequired
            return
        }
        guard !exportPassword.isEmpty else {
            exportError = localizer.t.backupPasswordRequired
            return
        }
        guard exportPassword.count >= 6 else {
            exportError = localizer.t.backupPasswordTooShort
            return
        }
        guard exportPassword == exportConfirmPassword else {
            exportError = localizer.t.backupPasswordMismatch
            return
        }

        isExporting = true
        let manager = BackupManager(servicesStore: servicesStore)
        let password = exportPassword
        let selectedTypes = selectedExportTypes

        Task.detached(priority: .userInitiated) {
            do {
                let url = try await manager.exportBackup(password: password, includedTypes: selectedTypes)
                await MainActor.run {
                    exportFileURL = url
                    showShareSheet = true
                    withAnimation { showExportSuccess = true }
                    isExporting = false
                    resetExportState()
                }
            } catch {
                await MainActor.run {
                    exportError = error.localizedDescription
                    isExporting = false
                    resetExportState()
                }
            }
        }
    }

    private func handleFilePickerResult(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            // Need to start accessing the security-scoped resource
            guard url.startAccessingSecurityScopedResource() else {
                importError = localizer.t.backupImportFileError
                return
            }
            // Copy to temp so we can access it later after the dialog
            do {
                let tempURL = FileManager.default.temporaryDirectory
                    .appendingPathComponent(url.lastPathComponent)
                try? FileManager.default.removeItem(at: tempURL)
                try FileManager.default.copyItem(at: url, to: tempURL)
                url.stopAccessingSecurityScopedResource()
                importFileURL = tempURL
                importPassword = ""
                importError = nil
                showImportPasswordDialog = true
            } catch {
                url.stopAccessingSecurityScopedResource()
                importError = error.localizedDescription
            }

        case .failure(let error):
            importError = error.localizedDescription
        }
    }

    private func performDecrypt() {
        guard let fileURL = importFileURL else { return }
        guard !importPassword.isEmpty else {
            importError = localizer.t.backupPasswordRequired
            return
        }

        isImporting = true
        let manager = BackupManager(servicesStore: servicesStore)
        let password = importPassword

        Task.detached(priority: .userInitiated) {
            do {
                let preview = try manager.previewBackup(from: fileURL, password: password)
                await MainActor.run {
                    previewResult = preview
                    selectedImportTypes = preview.knownServiceTypes
                    showPreview = true
                    isImporting = false
                }
            } catch {
                await MainActor.run {
                    importError = error.localizedDescription
                    isImporting = false
                }
            }
        }
    }

    private func performImport() {
        guard let preview = previewResult else { return }
        guard !selectedImportTypes.isEmpty else {
            importError = localizer.t.backupSelectionRequired
            return
        }

        isImporting = true
        let manager = BackupManager(servicesStore: servicesStore)
        let selectedTypes = selectedImportTypes

        Task {
            let result = await manager.applyBackup(preview.envelope, includedTypes: selectedTypes)
            switch result {
            case .success(let count):
                importedCount = count
                withAnimation { showImportSuccess = true }
            case .failure(let message):
                importError = message
            }
            isImporting = false
            resetImportState()
        }
    }

    private func previewMessage(_ preview: BackupPreviewResult) -> String {
        var lines: [String] = []
        lines.append(String(format: localizer.t.backupPreviewServices, preview.knownCount))

        for group in preview.servicesByType {
            lines.append("• \(group.displayName): \(group.count)")
        }

        if !preview.unknownServiceTypes.isEmpty {
            lines.append("")
            lines.append(String(format: localizer.t.backupPreviewUnknown, preview.unknownCount))
        }

        lines.append("")
        lines.append(localizer.t.backupPreviewWarning)

        return lines.joined(separator: "\n")
    }

    private func resetExportState() {
        exportPassword = ""
        exportConfirmPassword = ""
    }

    private func resetImportState() {
        importPassword = ""
        importFileURL = nil
        previewResult = nil
        selectedImportTypes = []
        showPreview = false
    }
}

// MARK: - Share Sheet

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
