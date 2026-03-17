import SwiftUI

struct AdGuardHomeBlockedServicesView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var services: [AdGuardBlockedService] = []
    @State private var groups: [AdGuardServiceGroup] = []
    @State private var blockedIds: Set<String> = []
    @State private var schedule: [String: AdGuardJSONValue]?
    @State private var isLoading = true
    @State private var isSaving = false
    @State private var error: Error?

    private var groupedServices: [(String, [AdGuardBlockedService])] {
        let groupNames = Dictionary(uniqueKeysWithValues: groups.map { ($0.id, $0.name) })
        var buckets: [String: [AdGuardBlockedService]] = [:]
        for service in services {
            let key = service.groupId ?? "other"
            buckets[key, default: []].append(service)
        }
        return buckets.keys.sorted().map { key in
            let name = groupNames[key] ?? localizer.t.adguardBlockedServicesOther
            let items = buckets[key]?.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending } ?? []
            return (name, items)
        }
    }

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.largeTitle)
                        .foregroundStyle(AppTheme.stopped)
                        .accessibilityHidden(true)
                    Text(error.localizedDescription)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                    Button(localizer.t.retry) {
                        Task { await fetchBlockedServices() }
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
            } else if services.isEmpty {
                ContentUnavailableView(localizer.t.adguardNoBlockedServices, systemImage: "nosign")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List {
                    ForEach(groupedServices, id: \.0) { groupName, items in
                        Section(groupName) {
                            ForEach(items) { service in
                                Toggle(isOn: binding(for: service.id)) {
                                    Text(service.name)
                                }
                                .tint(AppTheme.stopped)
                            }
                        }
                    }
                }
                .listStyle(.insetGrouped)
                .overlay(alignment: .bottom) {
                    if isSaving {
                        ProgressView()
                            .padding(12)
                            .background(.ultraThinMaterial, in: Capsule())
                            .padding(.bottom, 8)
                    }
                }
            }
        }
        .navigationTitle(localizer.t.adguardBlockedServices)
        .navigationBarTitleDisplayMode(.inline)
        .task { await fetchBlockedServices() }
    }

    private func binding(for id: String) -> Binding<Bool> {
        Binding(
            get: { blockedIds.contains(id) },
            set: { newValue in
                if newValue {
                    blockedIds.insert(id)
                } else {
                    blockedIds.remove(id)
                }
                Task { await saveBlockedServices() }
            }
        )
    }

    private func fetchBlockedServices() async {
        isLoading = true
        error = nil
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let all = try await client.getBlockedServicesAll()
            let schedule = try await client.getBlockedServicesSchedule()
            services = all.services
            groups = all.groups
            blockedIds = Set(schedule.ids)
            self.schedule = schedule.schedule
        } catch {
            self.error = error
        }
        isLoading = false
    }

    private func saveBlockedServices() async {
        guard !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            try await client.updateBlockedServices(ids: Array(blockedIds), schedule: schedule)
        } catch {
            self.error = error
        }
    }
}
