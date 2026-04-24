import SwiftUI
import Charts

struct MaltrailDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var selectedDate: Date?
    @State private var dashboard: MaltrailDashboardData?
    @State private var selectedEvent: MaltrailEvent?
    @State private var state: LoadableState<Void> = .idle

    private let maltrailColor = ServiceType.maltrail.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .maltrail,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(showLoading: false) }
        ) {
            instancePicker
            overviewCard
            countsCard
            eventsSection
        }
        .navigationTitle(ServiceType.maltrail.displayName)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await fetchDashboard(showLoading: false) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(state.isLoading)
                .accessibilityLabel(localizer.t.refresh)
            }
        }
        .task(id: selectedInstanceId) {
            selectedDate = nil
            dashboard = nil
            await fetchDashboard(showLoading: true)
        }
        .sheet(item: $selectedEvent) { event in
            eventDetailSheet(event)
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
        }
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .maltrail)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .maltrail)
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? maltrailColor : AppTheme.textMuted.opacity(0.4))
                                    .frame(width: 10, height: 10)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(instance.displayLabel)
                                        .font(.subheadline.weight(.semibold))
                                    Text(instance.url)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                            }
                            .padding(14)
                            .glassCard(tint: instance.id == selectedInstanceId ? maltrailColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var overviewCard: some View {
        let latest = dashboard?.latestCount
        let total = dashboard?.totalFindings ?? 0
        let events = dashboard?.events.count ?? 0

        return VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .center, spacing: 12) {
                ServiceIconView(type: .maltrail, size: 52)
                    .padding(6)
                    .background(
                        maltrailColor.opacity(colorScheme == .dark ? 0.18 : 0.12),
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )

                VStack(alignment: .leading, spacing: 4) {
                    Text(localizer.t.maltrailLatestDay)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                    Text(Formatters.formatNumber(latest?.count ?? 0))
                        .font(.system(size: 32, weight: .bold))
                        .contentTransition(.numericText())
                    Text(latest?.displayDate ?? localizer.t.notAvailable)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }

                Spacer()
            }

            HStack(spacing: 10) {
                metricCell(title: localizer.t.maltrailFindings, value: Formatters.formatNumber(latest?.count ?? 0), tint: maltrailColor)
                metricCell(title: localizer.t.maltrailTotalFindings, value: Formatters.formatNumber(total), tint: AppTheme.warning)
                metricCell(title: localizer.t.maltrailEvents, value: Formatters.formatNumber(events), tint: AppTheme.info)
            }
        }
        .padding(18)
        .glassCard(tint: maltrailColor.opacity(colorScheme == .dark ? 0.1 : 0.055))
    }

    private var countsCard: some View {
        let points = recentCountPoints
        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label(localizer.t.maltrailDailyCounts, systemImage: "chart.bar.xaxis")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                Text("\(dashboard?.counts.count ?? 0)")
                    .font(.caption.bold())
                    .foregroundStyle(maltrailColor)
            }

            if points.isEmpty {
                emptyState(title: localizer.t.maltrailNoCounts, icon: "chart.bar")
            } else {
                Chart(points) { point in
                    BarMark(
                        x: .value(localizer.t.maltrailSelectedDate, point.date, unit: .day),
                        y: .value(localizer.t.maltrailFindings, point.count)
                    )
                    .foregroundStyle(maltrailColor.gradient)
                    .cornerRadius(4)
                }
                .chartXAxis(.hidden)
                .chartYAxis {
                    AxisMarks(position: .leading)
                }
                .frame(height: 170)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(points.reversed()) { point in
                            dateChip(point)
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    private var eventsSection: some View {
        let events = dashboard?.events ?? []
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(localizer.t.maltrailEvents, systemImage: "exclamationmark.shield.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                Text("\(events.count)")
                    .font(.caption.bold())
                    .foregroundStyle(maltrailColor)
            }

            if events.isEmpty {
                emptyState(title: localizer.t.maltrailNoEvents, icon: "shield.slash")
                    .glassCard()
            } else {
                ForEach(events) { event in
                    eventCard(event)
                }
            }
        }
    }

    private var recentCountPoints: [MaltrailCountPoint] {
        Array((dashboard?.counts ?? []).prefix(14)).sorted { $0.timestamp < $1.timestamp }
    }

    private func metricCell(title: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(value)
                .font(.title3.bold())
                .foregroundStyle(tint)
                .contentTransition(.numericText())
                .lineLimit(1)
                .minimumScaleFactor(0.78)
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(2)
                .minimumScaleFactor(0.78)
        }
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
        .padding(.vertical, 6)
    }

    private func dateChip(_ point: MaltrailCountPoint) -> some View {
        let selected = point.apiDate == selectedApiDate
        return Button {
            HapticManager.light()
            selectedDate = point.date
            Task { await fetchDashboard(showLoading: false) }
        } label: {
            VStack(spacing: 4) {
                Text(point.displayDate)
                    .font(.caption2.weight(.semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
                Text(Formatters.formatNumber(point.count))
                    .font(.caption.bold())
                    .lineLimit(1)
                    .minimumScaleFactor(0.72)
            }
            .foregroundStyle(selected ? maltrailColor : AppTheme.textSecondary)
            .frame(width: 92, height: 54)
            .background(
                selected ? maltrailColor.opacity(0.15) : AppTheme.surface.opacity(0.82),
                in: RoundedRectangle(cornerRadius: 12, style: .continuous)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(selected ? maltrailColor.opacity(0.42) : .white.opacity(0.04), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private func eventCard(_ event: MaltrailEvent) -> some View {
        Button {
            selectedEvent = event
        } label: {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.subheadline)
                        .foregroundStyle(severityColor(event.normalizedSeverity))
                        .frame(width: 28, height: 28)
                        .background(severityColor(event.normalizedSeverity).opacity(0.12), in: Circle())

                    VStack(alignment: .leading, spacing: 4) {
                        Text(event.title)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                        Text(event.route)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(1)
                    }

                    Spacer()

                    Text(event.normalizedSeverity.uppercased())
                        .font(.caption2.bold())
                        .foregroundStyle(severityColor(event.normalizedSeverity))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }

                HStack(spacing: 10) {
                    if let protocolName = event.protocolName, !protocolName.isEmpty {
                        pill(protocolName.uppercased(), color: AppTheme.info)
                    }
                    if let sensor = event.sensor, !sensor.isEmpty {
                        pill(sensor, color: maltrailColor)
                    }
                    if let timestamp = event.timestamp, !timestamp.isEmpty {
                        Text(timestamp)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(1)
                    }
                }
            }
            .padding(15)
            .glassCard()
        }
        .buttonStyle(.plain)
    }

    private func eventDetailSheet(_ event: MaltrailEvent) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(event.title)
                            .font(.title3.bold())
                            .foregroundStyle(.primary)
                            .fixedSize(horizontal: false, vertical: true)
                        Text(event.route)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                    .padding(16)
                    .glassCard(tint: severityColor(event.normalizedSeverity).opacity(0.08))

                    detailGrid(event)

                    if !event.rawFields.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(localizer.t.maltrailEventDetails)
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(AppTheme.textMuted)
                                .textCase(.uppercase)

                            ForEach(event.rawFields.sorted(by: { $0.key < $1.key }), id: \.key) { key, value in
                                detailRow(title: key, value: value)
                            }
                        }
                        .padding(16)
                        .glassCard()
                    }
                }
                .padding(AppTheme.padding)
            }
            .background(AppTheme.background)
            .navigationTitle(localizer.t.maltrailEvents)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(localizer.t.done) {
                        selectedEvent = nil
                    }
                }
            }
        }
    }

    private func detailGrid(_ event: MaltrailEvent) -> some View {
        VStack(spacing: 10) {
            detailRow(title: localizer.t.maltrailSeverity, value: event.normalizedSeverity)
            detailRow(title: localizer.t.maltrailSource, value: event.source ?? "-")
            detailRow(title: localizer.t.maltrailDestination, value: event.destination ?? "-")
            detailRow(title: localizer.t.maltrailTrail, value: event.trail ?? "-")
            detailRow(title: localizer.t.maltrailProtocol, value: event.protocolName ?? "-")
            detailRow(title: localizer.t.maltrailSensor, value: event.sensor ?? "-")
        }
        .padding(16)
        .glassCard()
    }

    private func detailRow(title: String, value: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .frame(width: 110, alignment: .leading)
                .lineLimit(2)
                .minimumScaleFactor(0.78)
            Text(value.isEmpty ? "-" : value)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func pill(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.caption2.bold())
            .foregroundStyle(color)
            .lineLimit(1)
            .minimumScaleFactor(0.75)
            .padding(.horizontal, 8)
            .padding(.vertical, 5)
            .background(color.opacity(0.12), in: Capsule(style: .continuous))
    }

    private func emptyState(title: String, icon: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundStyle(AppTheme.textMuted)
            Text(title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(AppTheme.textMuted)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, minHeight: 118)
        .padding(18)
    }

    private var selectedApiDate: String? {
        selectedDate.map(MaltrailDateFormatting.apiDayString)
    }

    private func severityColor(_ value: String) -> Color {
        let normalized = value.lowercased()
        if normalized.contains("crit") || normalized.contains("high") || normalized.contains("severe") {
            return AppTheme.danger
        }
        if normalized.contains("medium") || normalized.contains("warn") {
            return AppTheme.warning
        }
        if normalized.contains("low") || normalized.contains("info") {
            return AppTheme.info
        }
        return maltrailColor
    }

    private func fetchDashboard(showLoading: Bool) async {
        if showLoading {
            state = .loading
        }

        guard let client = await servicesStore.maltrailClient(instanceId: selectedInstanceId) else {
            state = .error(.notConfigured)
            return
        }

        do {
            let response = try await client.getDashboard(selectedDate: selectedDate)
            if selectedDate == nil {
                selectedDate = response.selectedDate
            }
            dashboard = response
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}
