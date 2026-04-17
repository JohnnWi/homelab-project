import SwiftUI

struct ProxmoxTaskLogView: View {
    let instanceId: UUID
    let nodeName: String
    let task: ProxmoxTask

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var logText: String = ""
    @State private var isLoading = true
    @State private var autoRefresh = true
    @State private var currentTaskStatus: ProxmoxTask?
    @State private var showAllLogs = false

    private let proxmoxColor = ServiceType.proxmox.colors.primary
    private let maxVisibleLines = 500
    private var displayTask: ProxmoxTask { currentTaskStatus ?? task }
    private var pollingTaskID: String { "\(task.upid)-\(autoRefresh)" }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    taskInfoCard
                    logOutputCard
                }
                .padding()
            }
        }
        .background(AppTheme.background)
        .navigationTitle(displayTask.type ?? localizer.t.proxmoxTaskLog)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                if displayTask.isRunning {
                    Button {
                        autoRefresh.toggle()
                    } label: {
                        Image(systemName: autoRefresh ? "pause.circle.fill" : "play.circle.fill")
                            .foregroundStyle(proxmoxColor)
                    }
                }
            }
        }
        .task { await fetchLog() }
        .task(id: pollingTaskID) {
            // Track actual task status from API to avoid infinite polling
            if currentTaskStatus == nil {
                currentTaskStatus = task
            }
            guard autoRefresh && displayTask.isRunning else { return }
            while true {
                try? await Task.sleep(for: .seconds(3))
                guard !Task.isCancelled else { break }
                guard autoRefresh else { break }

                // Re-fetch task status from API to check if it actually completed
                if let currentStatus = currentTaskStatus,
                   let node = currentStatus.node,
                   let client = await servicesStore.proxmoxClient(instanceId: instanceId) {
                    do {
                        let freshStatus = try await client.getTaskStatus(node: node, upid: currentStatus.upid)
                        currentTaskStatus = freshStatus
                        await fetchLog()
                        guard freshStatus.isRunning else { break }
                    } catch {
                        break // Stop polling on error
                    }
                } else {
                    break
                }
            }
        }
    }

    // MARK: - Task Info Card

    private var taskInfoCard: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        Image(systemName: displayTask.isRunning ? "arrow.triangle.2.circlepath" : (displayTask.isOk ? "checkmark.circle.fill" : "xmark.circle.fill"))
                            .foregroundStyle(displayTask.isRunning ? .blue : (displayTask.isOk ? AppTheme.running : AppTheme.stopped))
                            .font(.title2)
                            .symbolEffect(.pulse, isActive: displayTask.isRunning)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(displayTask.type ?? localizer.t.proxmoxTaskLog)
                                .font(.headline)
                            Text(displayTask.upid)
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                                .lineLimit(1)
                        }
                    }
                }
                Spacer()
                statusBadge
            }
            .padding(16)

            Divider().padding(.leading, 16)

            infoRow(label: localizer.t.proxmoxNode, value: nodeName)
            Divider().padding(.leading, 16)
            infoRow(label: localizer.t.proxmoxUser, value: displayTask.user ?? "-")
            Divider().padding(.leading, 16)
            infoRow(label: localizer.t.proxmoxStartTime, value: displayTask.formattedStart)
            Divider().padding(.leading, 16)
            infoRow(label: localizer.t.proxmoxDuration, value: displayTask.duration)

            if let exitstatus = displayTask.exitstatus, !displayTask.isRunning {
                Divider().padding(.leading, 16)
                infoRow(label: localizer.t.proxmoxExitStatus, value: exitstatus)
            }
        }
        .glassCard()
    }

    private var statusBadge: some View {
        Text(displayTask.isRunning ? localizer.t.proxmoxRunning : (displayTask.isOk ? localizer.t.proxmoxOk : localizer.t.error.uppercased()))
            .font(.caption.bold())
            .foregroundStyle(displayTask.isRunning ? .blue : (displayTask.isOk ? AppTheme.running : AppTheme.stopped))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(
                (displayTask.isRunning ? Color.blue : (displayTask.isOk ? AppTheme.running : AppTheme.stopped)).opacity(0.12),
                in: RoundedRectangle(cornerRadius: 8, style: .continuous)
            )
    }

    // MARK: - Log Output

    private var logOutputCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text(localizer.t.proxmoxLogOutput)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)

                Spacer()

                if isLoading {
                    ProgressView()
                        .controlSize(.small)
                }

                Button {
                    UIPasteboard.general.string = logText
                } label: {
                    Image(systemName: "doc.on.doc")
                        .font(.caption)
                        .foregroundStyle(proxmoxColor)
                }
            }

            ScrollView {
                if logText.isEmpty {
                    Text(localizer.t.proxmoxNoLogData)
                        .font(.system(size: 12, weight: .regular, design: .monospaced))
                        .foregroundStyle(AppTheme.textMuted)
                        .padding(14)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    let allLines = logText.components(separatedBy: "\n")
                    let visibleLines = showAllLogs || allLines.count <= maxVisibleLines
                        ? allLines
                        : Array(allLines.prefix(maxVisibleLines))

                    VStack(alignment: .leading, spacing: 2) {
                        ForEach(visibleLines.indices, id: \.self) { index in
                            colorizedLogLine(visibleLines[index])
                        }
                    }
                    .padding(14)

                    if !showAllLogs && allLines.count > maxVisibleLines {
                        Button {
                            withAnimation(.easeOut(duration: 0.25)) {
                                showAllLogs = true
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "arrow.down.circle")
                                    .font(.caption)
                                Text(String(format: localizer.t.proxmoxShowAllLogs, allLines.count - maxVisibleLines))
                                    .font(.caption.bold())
                            }
                            .foregroundStyle(proxmoxColor)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .frame(minHeight: 250)
            .glassCard()
        }
    }

    private func colorizedLogLine(_ line: String) -> some View {
        let trimmedLine = line.trimmingCharacters(in: .whitespacesAndNewlines)
        let lowercased = trimmedLine.lowercased()

        var lineColor: Color = .primary.opacity(0.85)
        var prefixIcon: String? = nil

        if lowercased.contains("error") || lowercased.contains("failed") || lowercased.contains("fatal") || lowercased.contains("critical") {
            lineColor = .red
            prefixIcon = "xmark.circle.fill"
        } else if lowercased.contains("warning") || lowercased.contains("warn") || lowercased.contains("deprecated") {
            lineColor = .orange
            prefixIcon = "exclamationmark.triangle.fill"
        } else if lowercased.contains("success") || lowercased.contains("completed") || lowercased.contains("finished") || lowercased.contains("ok") {
            lineColor = .green
            prefixIcon = "checkmark.circle.fill"
        } else if lowercased.contains("start") || lowercased.contains("begin") {
            lineColor = .blue
            prefixIcon = "play.circle.fill"
        }

        return HStack(alignment: .firstTextBaseline, spacing: 6) {
            if let icon = prefixIcon {
                Image(systemName: icon)
                    .font(.caption2)
                    .foregroundStyle(lineColor)
                    .frame(width: 14)
            }
            Text(trimmedLine)
                .font(.system(size: 11, weight: .regular, design: .monospaced))
                .foregroundStyle(lineColor)
                .frame(maxWidth: .infinity, alignment: .leading)
                .textSelection(.enabled)
        }
    }

    // MARK: - Helper

    private func infoRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
                .lineLimit(1)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // MARK: - Data

    private func fetchLog() async {
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            let entries = try await client.getTaskLog(node: nodeName, upid: task.upid, limit: 500)
            logText = entries
                .sorted { $0.n < $1.n }
                .compactMap(\.t)
                .joined(separator: "\n")
        } catch {
            logText = "\(localizer.t.proxmoxFailedLoadLog): \(error.localizedDescription)"
        }
    }
}
