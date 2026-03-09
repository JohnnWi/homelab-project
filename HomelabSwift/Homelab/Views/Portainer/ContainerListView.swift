import SwiftUI

// Maps to app/portainer/containers.tsx

struct ContainerListView: View {
    let endpointId: Int

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var containers: [PortainerContainer] = []
    @State private var search = ""
    @State private var filter: FilterType = .all
    @State private var isLoading = true
    @State private var actionInProgress: String?
    @State private var actionError: String?
    @State private var showActionError = false

    private let portainerColor = ServiceType.portainer.colors.primary

    enum FilterType: String, CaseIterable {
        case all, running, stopped
    }

    private var filteredContainers: [PortainerContainer] {
        var result = containers
        switch filter {
        case .all: break
        case .running: result = result.filter { $0.State == "running" }
        case .stopped: result = result.filter { $0.State == "exited" || $0.State == "dead" }
        }
        if !search.trimmingCharacters(in: .whitespaces).isEmpty {
            let q = search.lowercased()
            result = result.filter { $0.displayName.lowercased().contains(q) || $0.Image.lowercased().contains(q) }
        }
        return result
    }

    private func filterLabel(_ f: FilterType) -> String {
        switch f {
        case .all: return localizer.t.containersAll
        case .running: return localizer.t.containersRunning
        case .stopped: return localizer.t.containersStopped
        }
    }

    private func filterCount(_ f: FilterType) -> Int {
        switch f {
        case .all: return containers.count
        case .running: return containers.filter { $0.State == "running" }.count
        case .stopped: return containers.filter { $0.State == "exited" || $0.State == "dead" }.count
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Search bar
            searchBar

            // Filter chips
            filterChips

            // Content
            if isLoading {
                Spacer()
                ProgressView()
                    .tint(portainerColor)
                Spacer()
            } else if filteredContainers.isEmpty {
                Spacer()
                ContentUnavailableView {
                    Label(localizer.t.containersEmpty, systemImage: "line.3.horizontal.decrease.circle")
                } description: {
                    Text(localizer.t.containersEmpty)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                }
                Spacer()
            } else {
                containerList
            }
        }
        .background(AppTheme.background)
        .navigationTitle(localizer.t.portainerContainers)
        .navigationDestination(for: PortainerRoute.self) { route in
            switch route {
            case .containerDetail(let epId, let cId):
                ContainerDetailView(endpointId: epId, containerId: cId)
            default: EmptyView()
            }
        }
        .task { await fetchContainers() }
        .alert(localizer.t.error, isPresented: $showActionError) {
            Button(localizer.t.confirm, role: .cancel) { }
        } message: {
            Text(actionError ?? localizer.t.errorUnknown)
        }
    }

    // MARK: - Search Bar

    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
            TextField(localizer.t.containersSearch, text: $search)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !search.isEmpty {
                Button { search = "" } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .stroke(Color(.separator), lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.top, 12)
    }

    // MARK: - Filter Chips

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(FilterType.allCases, id: \.self) { f in
                    Button {
                        HapticManager.light()
                        filter = f
                    } label: {
                        Text("\(filterLabel(f)) (\(filterCount(f)))")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(filter == f ? portainerColor : AppTheme.textSecondary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                filter == f
                                    ? portainerColor.opacity(0.1)
                                    : Color(.tertiarySystemFill)
                            )
                            .clipShape(Capsule())
                            .overlay(
                                Capsule()
                                    .stroke(filter == f ? portainerColor.opacity(0.3) : .clear, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
    }

    // MARK: - Container List

    private var containerList: some View {
        ScrollView {
            LazyVStack(spacing: 10) {
                ForEach(filteredContainers) { container in
                    NavigationLink(value: PortainerRoute.containerDetail(endpointId: endpointId, containerId: container.Id)) {
                        ContainerRow(
                            container: container,
                            t: localizer.t,
                            onAction: { action in
                                handleAction(containerId: container.Id, action: action)
                            }
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 20)
        }
        .refreshable { await fetchContainers() }
    }

    // MARK: - Actions

    private func handleAction(containerId: String, action: ContainerAction) {
        HapticManager.medium()
        actionInProgress = containerId
        Task {
            do {
                try await servicesStore.portainerClient.containerAction(endpointId: endpointId, containerId: containerId, action: action)
                HapticManager.success()
                await fetchContainers()
            } catch {
                HapticManager.error()
                actionError = error.localizedDescription
                showActionError = true
            }
            actionInProgress = nil
        }
    }

    // MARK: - Fetch

    private func fetchContainers() async {
        isLoading = containers.isEmpty
        defer { isLoading = false }
        do {
            containers = try await servicesStore.portainerClient.getContainers(endpointId: endpointId)
        } catch {
            if containers.isEmpty {
                actionError = error.localizedDescription
                showActionError = true
            }
        }
    }
}

// MARK: - Container Row

struct ContainerRow: View {
    let container: PortainerContainer
    let t: Translations
    let onAction: (ContainerAction) -> Void

    private let portainerColor = ServiceType.portainer.colors.primary

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header: name + status
            HStack {
                Text(container.displayName)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Spacer(minLength: 8)
                StatusBadge(status: container.State, compact: true)
            }

            // Image
            Text(container.Image)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)

            // Status + created
            HStack {
                Text(container.Status)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                Spacer()
                Text(Formatters.formatUnixDate(container.Created))
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }

            // Ports
            if !container.Ports.isEmpty {
                let visiblePorts = container.Ports.filter { $0.PublicPort != nil }.prefix(3)
                if !visiblePorts.isEmpty {
                    HStack(spacing: 6) {
                        ForEach(Array(visiblePorts.enumerated()), id: \.offset) { _, port in
                            Text("\(port.PublicPort ?? 0):\(port.PrivatePort)/\(port.portType)")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.info)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(AppTheme.info.opacity(0.1), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                        }
                    }
                }
            }

            // Actions
            Divider()
            HStack(spacing: 8) {
                if container.State == "running" {
                    actionButton(action: .stop, color: AppTheme.stopped)
                    actionButton(action: .restart, color: AppTheme.warning)
                } else {
                    actionButton(action: .start, color: AppTheme.running)
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    private func actionButton(action: ContainerAction, color: Color) -> some View {
        Button {
            onAction(action)
        } label: {
            HStack(spacing: 5) {
                Image(systemName: action.symbolName)
                    .font(.caption)
                Text(action.displayName)
                    .font(.caption.weight(.semibold))
            }
            .foregroundStyle(color)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
