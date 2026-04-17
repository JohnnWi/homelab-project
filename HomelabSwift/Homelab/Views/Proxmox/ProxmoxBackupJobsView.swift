import SwiftUI

struct ProxmoxBackupJobsView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var jobs: [ProxmoxBackupJob] = []
    @State private var state: LoadableState<Void> = .idle
    @State private var triggeringJobId: String?
    @State private var triggerError: String?
    @State private var triggeredTask: ProxmoxTaskReference?

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchData
        ) {
            summarySection

            jobsSection
        }
        .navigationTitle(localizer.t.proxmoxBackupJobs)
        .navigationBarTitleDisplayMode(.inline)
        .alert(localizer.t.error, isPresented: .init(
            get: { triggerError != nil },
            set: { if !$0 { triggerError = nil } }
        )) {
            Button(localizer.t.done) { triggerError = nil }
        } message: {
            Text(triggerError ?? "")
        }
        .task(id: triggeredTask?.upid) {
            guard triggeredTask != nil else { return }
            while true {
                try? await Task.sleep(for: .seconds(3))
                guard !Task.isCancelled else { break }
                guard let task = triggeredTask else { break }
                await checkTriggeredTask(task)
                guard triggeredTask != nil else { break }
            }
        }
        .task { await fetchData() }
    }

    // MARK: - Summary

    private var summarySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxOverview.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            HStack(spacing: 10) {
                summaryCard(
                    icon: "clock.badge.checkmark.fill",
                    label: localizer.t.proxmoxTotalJobs,
                    value: "\(jobs.count)",
                    color: proxmoxColor
                )
                summaryCard(
                    icon: "checkmark.circle.fill",
                    label: localizer.t.proxmoxActive,
                    value: "\(jobs.filter { $0.isEnabled }.count)",
                    color: AppTheme.running
                )
                summaryCard(
                    icon: "pause.circle.fill",
                    label: localizer.t.proxmoxDisabled,
                    value: "\(jobs.filter { !$0.isEnabled }.count)",
                    color: AppTheme.textMuted
                )
            }
        }
    }

    private func summaryCard(icon: String, label: String, value: String, color: Color) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(color)
            Text(value)
                .font(.title2.bold())
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .glassCard()
    }

    // MARK: - Jobs List

    private var jobsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxScheduledJobs)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            if jobs.isEmpty {
                HStack {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "calendar.badge.clock")
                            .font(.title2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(localizer.t.proxmoxNoBackupJobs)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .padding(.vertical, 30)
                    Spacer()
                }
                .glassCard()
            } else {
                ForEach(jobs) { job in
                    jobCard(job)
                }
            }
        }
    }

    private func jobCard(_ job: ProxmoxBackupJob) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header
            HStack(spacing: 10) {
                Image(systemName: job.isEnabled ? "clock.badge.checkmark.fill" : "clock.badge.xmark.fill")
                    .font(.title3)
                    .foregroundStyle(job.isEnabled ? proxmoxColor : AppTheme.textMuted)

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(job.id)
                            .font(.subheadline.bold())
                            .lineLimit(1)

                        if !job.isEnabled {
                                Text(localizer.t.proxmoxDisabled)
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(AppTheme.stopped)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(AppTheme.stopped.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        }
                    }

                    if let schedule = job.schedule, !schedule.isEmpty {
                        HStack(spacing: 4) {
                            Image(systemName: "calendar")
                                .font(.caption2)
                            Text(schedule)
                                .font(.caption2)
                        }
                        .foregroundStyle(AppTheme.textSecondary)
                    }
                }

                Spacer()

                if let mode = job.mode {
                    Text(mode.capitalized)
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(proxmoxColor)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                }

                // Trigger button
                Button {
                    Task { await triggerJob(job) }
                } label: {
                    Image(systemName: triggeringJobId == job.id ? "arrow.clockwise" : "play.circle.fill")
                        .font(.body.bold())
                        .foregroundStyle(job.isEnabled ? AppTheme.running : AppTheme.textMuted)
                }
                .disabled(triggeringJobId != nil)
                .buttonStyle(.plain)
            }

            // Details
            VStack(spacing: 0) {
                if let storage = job.storage, !storage.isEmpty {
                    detailRow(icon: "externaldrive.fill", label: localizer.t.proxmoxStorage, value: storage)
                }
                if let compress = job.compress, !compress.isEmpty {
                    detailRow(icon: "archivebox.fill", label: localizer.t.proxmoxCompress, value: compress.uppercased())
                }
                if let node = job.node, !node.isEmpty {
                    detailRow(icon: "server.rack", label: localizer.t.proxmoxNodes, value: node)
                }
                if job.backupAll {
                    detailRow(icon: "square.stack.fill", label: localizer.t.proxmoxScope, value: localizer.t.proxmoxAllGuests)
                } else if let pool = job.pool, !pool.isEmpty {
                    detailRow(icon: "folder.fill", label: localizer.t.proxmoxPool, value: pool)
                } else if !job.vmidList.isEmpty {
                    detailRow(icon: "number", label: localizer.t.proxmoxVmidLabel + "s", value: job.vmidList.joined(separator: ", "))
                }
                if let mailto = job.mailto, !mailto.isEmpty {
                    detailRow(icon: "envelope.fill", label: localizer.t.proxmoxNotify, value: mailto)
                }
            }
            .padding(8)
            .background(AppTheme.textMuted.opacity(0.05), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .padding(12)
        .glassCard()
    }

    private func detailRow(icon: String, label: String, value: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .frame(width: 16)
            Text(label)
                .font(.caption2.bold())
                .foregroundStyle(AppTheme.textMuted)
                .frame(width: 70, alignment: .leading)
            Text(value)
                .font(.caption2)
                .foregroundStyle(.primary)
                .lineLimit(1)
            Spacer()
        }
        .padding(.vertical, 3)
    }

    // MARK: - Data

    private func fetchData() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            jobs = try await client.getBackupJobs()
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }

    // MARK: - Trigger Job

    private func triggerJob(_ job: ProxmoxBackupJob) async {
        triggeringJobId = job.id
        triggerError = nil
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                triggerError = localizer.t.proxmoxClientNotConfigured
                triggeringJobId = nil
                return
            }
            let task = try await client.triggerBackupJob(jobId: job.id)
            triggeredTask = task
            HapticManager.success()
        } catch {
            triggerError = error.localizedDescription
            HapticManager.error()
        }
        triggeringJobId = nil
    }

    private func checkTriggeredTask(_ task: ProxmoxTaskReference) async {
        guard let node = task.node else { return }
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else { return }
            let taskStatus = try await client.getTaskStatus(node: node, upid: task.upid)
            if !taskStatus.isRunning {
                triggeredTask = nil
            }
        } catch {
            triggeredTask = nil
        }
    }
}
