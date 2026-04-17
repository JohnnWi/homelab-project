import SwiftUI

struct ProxmoxHAView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var resources: [ProxmoxHAResource] = []
    @State private var groups: [ProxmoxHAGroup] = []
    @State private var replicationJobs: [ProxmoxReplicationJob] = []
    @State private var state: LoadableState<Void> = .idle

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchData
        ) {
            summarySection

            if !resources.isEmpty {
                resourcesSection
            }

            if !groups.isEmpty {
                groupsSection
            }

            if !replicationJobs.isEmpty {
                replicationSection
            }
        }
        .navigationTitle(localizer.t.proxmoxHaReplication)
        .navigationBarTitleDisplayMode(.inline)
        .task { await fetchData() }
    }

    // MARK: - Summary

    private var summarySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxOverview.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            HStack(spacing: 10) {
                summaryCard(icon: "shield.lefthalf.filled", label: localizer.t.proxmoxHaResources, value: "\(resources.count)", color: proxmoxColor)
                summaryCard(icon: "person.3.fill", label: localizer.t.proxmoxHaGroups, value: "\(groups.count)", color: .blue)
                summaryCard(icon: "arrow.triangle.2.circlepath", label: localizer.t.proxmoxReplicationJobs, value: "\(replicationJobs.count)", color: .purple)
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
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .glassCard()
    }

    // MARK: - HA Resources

    private var resourcesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(localizer.t.proxmoxHaResources) (\(resources.count))")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 0) {
                ForEach(resources) { res in
                    HStack(spacing: 10) {
                        Image(systemName: res.resourceType == "vm" ? "desktopcomputer" : "shippingbox.fill")
                            .font(.caption)
                            .foregroundStyle(proxmoxColor)
                            .frame(width: 28, height: 28)
                            .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 7, style: .continuous))

                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Text(res.sid)
                                    .font(.subheadline.bold())
                                if let group = res.group, !group.isEmpty {
                                    Text(group)
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundStyle(proxmoxColor)
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                                }
                            }
                            HStack(spacing: 6) {
                                if let state = res.state {
                                    Text(state)
                                        .font(.caption2)
                                        .foregroundStyle(state == "started" ? AppTheme.running : AppTheme.textMuted)
                                }
                                if let status = res.status, !status.isEmpty {
                                    Text("· \(status)")
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                }
                            }
                        }

                        Spacer()

                        VStack(alignment: .trailing, spacing: 2) {
                            if let maxRestart = res.max_restart {
                                Text("↻ \(maxRestart)")
                                    .font(.system(size: 9, weight: .medium, design: .monospaced))
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            if let maxRelocate = res.max_relocate {
                                Text("⇄ \(maxRelocate)")
                                    .font(.system(size: 9, weight: .medium, design: .monospaced))
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)

                    if res.id != resources.last?.id {
                        Divider().padding(.leading, 50)
                    }
                }
            }
            .glassCard()
        }
    }

    // MARK: - HA Groups

    private var groupsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(localizer.t.proxmoxHaGroups) (\(groups.count))")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            ForEach(groups) { group in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "person.3.fill")
                            .font(.subheadline)
                            .foregroundStyle(.blue)
                        Text(group.group)
                            .font(.subheadline.bold())
                        Spacer()
                        if group.restricted == 1 {
                            Text(localizer.t.proxmoxRestricted)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.orange)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        }
                    }

                    if !group.nodeList.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: "server.rack")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                            Text(group.nodeList.joined(separator: ", "))
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }

                    if let comment = group.comment, !comment.isEmpty {
                        Text(comment)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }

                    HStack(spacing: 12) {
                        if group.nofailback == 1 {
                            Label(localizer.t.proxmoxNoFailback, systemImage: "arrow.uturn.backward.circle")
                                .font(.system(size: 9, weight: .medium))
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }
                }
                .padding(12)
                .glassCard()
            }
        }
    }

    // MARK: - Replication

    private var replicationSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(localizer.t.proxmoxReplicationJobs) (\(replicationJobs.count))")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 0) {
                ForEach(replicationJobs) { job in
                    HStack(spacing: 10) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.caption)
                            .foregroundStyle(job.isEnabled ? .purple : AppTheme.textMuted)

                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Text(job.id)
                                    .font(.subheadline.bold())
                                if !job.isEnabled {
                                    Text(localizer.t.proxmoxDisabled)
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundStyle(AppTheme.stopped)
                                        .padding(.horizontal, 5)
                                        .padding(.vertical, 2)
                                        .background(AppTheme.stopped.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                                }
                            }
                            HStack(spacing: 4) {
                                if let source = job.source {
                                    Text(source)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textSecondary)
                                }
                                Image(systemName: "arrow.right")
                                    .font(.system(size: 7, weight: .bold))
                                    .foregroundStyle(AppTheme.textMuted)
                                if let target = job.target {
                                    Text(target)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textSecondary)
                                }
                            }
                            if let schedule = job.schedule {
                                Text(schedule)
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }

                        Spacer()

                        if let error = job.error, !error.isEmpty {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.caption)
                                .foregroundStyle(.red)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)

                    if job.id != replicationJobs.last?.id {
                        Divider().padding(.leading, 30)
                    }
                }
            }
            .glassCard()
        }
    }

    // MARK: - Data

    private func fetchData() async {
        state = .loading
        guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
            state = .error(.notConfigured)
            return
        }

        do {
            resources = try await client.getHAResources()
            groups = try await client.getHAGroups()
            replicationJobs = try await client.getReplicationJobs()
            state = .loaded(())
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}
