import SwiftUI
import UniformTypeIdentifiers

// MARK: - Homelab Backup UTType

extension UTType {
    static let homelabBackup = UTType(exportedAs: "com.homelab.backup", conformingTo: .data)
}

// MARK: - BackupView

struct BackupView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    // Export state
    @State private var showExportPasswordDialog = false
    @State private var exportPassword = ""
    @State private var exportConfirmPassword = ""
    @State private var exportError: String?
    @State private var exportFileURL: URL?
    @State private var showShareSheet = false
    @State private var isExporting = false

    // Import state
    @State private var showFilePicker = false
    @State private var showImportPasswordDialog = false
    @State private var importPassword = ""
    @State private var importError: String?
    @State private var importFileURL: URL?
    @State private var previewResult: BackupPreviewResult?
    @State private var showPreview = false
    @State private var isImporting = false

    // Success state
    @State private var showExportSuccess = false
    @State private var showImportSuccess = false
    @State private var importedCount = 0

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
        .alert(localizer.t.backupImportPreviewTitle, isPresented: $showPreview) {
            Button(localizer.t.cancel, role: .cancel) { resetImportState() }
            Button(localizer.t.backupImportApply, role: .destructive) { performImport() }
        } message: {
            if let preview = previewResult {
                Text(previewMessage(preview))
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
                        Text(String(format: localizer.t.backupExportDesc, servicesStore.connectedCount))
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
            .disabled(servicesStore.connectedCount == 0 || isExporting)
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

    // MARK: - Actions

    private func performExport() {
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

        Task.detached(priority: .userInitiated) {
            do {
                let url = try await manager.exportBackup(password: password)
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

        isImporting = true
        let manager = BackupManager(servicesStore: servicesStore)

        Task {
            await manager.applyBackup(preview.envelope)
            importedCount = preview.knownCount
            withAnimation { showImportSuccess = true }
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
