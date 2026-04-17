import SwiftUI

struct ProxmoxNetworkView: View {
    let instanceId: UUID
    let nodeName: String

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var networks: [ProxmoxNetwork] = []
    @State private var dns: ProxmoxDNS?
    @State private var state: LoadableState<Void> = .idle

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchData
        ) {
            // DNS section
            if let dns {
                dnsSection(dns)
            }

            // Interfaces
            interfacesSection
        }
        .navigationTitle("\(localizer.t.proxmoxNetwork) - \(nodeName)")
        .navigationBarTitleDisplayMode(.inline)
        .task { await fetchData() }
    }

    // MARK: - DNS Section

    private func dnsSection(_ dns: ProxmoxDNS) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxDns)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            VStack(spacing: 0) {
                if let search = dns.search, !search.isEmpty {
                    infoRow(label: localizer.t.proxmoxSearchDomain, value: search)
                    Divider().padding(.leading, 16)
                }
                if let dns1 = dns.dns1, !dns1.isEmpty {
                    infoRow(label: "\(localizer.t.proxmoxDns) 1", value: dns1)
                    if dns.dns2 != nil || dns.dns3 != nil {
                        Divider().padding(.leading, 16)
                    }
                }
                if let dns2 = dns.dns2, !dns2.isEmpty {
                    infoRow(label: "\(localizer.t.proxmoxDns) 2", value: dns2)
                    if dns.dns3 != nil {
                        Divider().padding(.leading, 16)
                    }
                }
                if let dns3 = dns.dns3, !dns3.isEmpty {
                    infoRow(label: "\(localizer.t.proxmoxDns) 3", value: dns3)
                }
            }
            .glassCard()
        }
    }

    // MARK: - Interfaces Section

    private var interfacesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(localizer.t.proxmoxInterfaces) (\(networks.count))")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            if networks.isEmpty {
                HStack {
                    Spacer()
                    VStack(spacing: 8) {
                        Image(systemName: "network.slash")
                            .font(.title2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(localizer.t.proxmoxNoNetworkInterfaces)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .padding(.vertical, 30)
                    Spacer()
                }
                .glassCard()
            } else {
                ForEach(sortedNetworks) { net in
                    interfaceCard(net)
                }
            }
        }
    }

    private var sortedNetworks: [ProxmoxNetwork] {
        networks.sorted { a, b in
            let typeOrder = ["bridge": 0, "bond": 1, "eth": 2, "vlan": 3]
            let aOrder = typeOrder[a.type ?? ""] ?? 4
            let bOrder = typeOrder[b.type ?? ""] ?? 4
            if aOrder != bOrder { return aOrder < bOrder }
            return a.iface < b.iface
        }
    }

    private func interfaceCard(_ net: ProxmoxNetwork) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header
            HStack(spacing: 10) {
                Image(systemName: net.typeIcon)
                    .font(.subheadline)
                    .foregroundStyle(net.isActive ? proxmoxColor : AppTheme.textMuted)
                    .frame(width: 30, height: 30)
                    .background((net.isActive ? proxmoxColor : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 7, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(net.iface)
                            .font(.subheadline.bold())
                        if let type = net.type {
                            Text(type)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(proxmoxColor)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        }
                    }

                    HStack(spacing: 4) {
                        Circle()
                            .fill(net.isActive ? AppTheme.running : AppTheme.stopped)
                            .frame(width: 5, height: 5)
                        Text(net.isActive ? localizer.t.proxmoxActive : localizer.t.proxmoxInactive)
                            .font(.caption2)
                            .foregroundStyle(net.isActive ? AppTheme.running : AppTheme.stopped)

                        if net.isAutostart {
                            Text("•")
                                .foregroundStyle(AppTheme.textMuted)
                            Text(localizer.t.proxmoxAutostart)
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }
                }

                Spacer()

                if let method = net.method {
                    Text(method.uppercased())
                        .font(.system(size: 9, weight: .bold, design: .monospaced))
                        .foregroundStyle(AppTheme.textSecondary)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(AppTheme.textMuted.opacity(0.08), in: RoundedRectangle(cornerRadius: 5, style: .continuous))
                }
            }

            // Address info
            if net.address != nil || net.cidr != nil || net.gateway != nil {
                VStack(spacing: 0) {
                    if let cidr = net.cidr, !cidr.isEmpty {
                        miniRow(label: localizer.t.proxmoxCidr, value: cidr)
                    } else {
                        if let address = net.address, !address.isEmpty {
                            miniRow(label: localizer.t.proxmoxAddress, value: address)
                        }
                        if let netmask = net.netmask, !netmask.isEmpty {
                            miniRow(label: localizer.t.proxmoxNetmask, value: netmask)
                        }
                    }
                    if let gateway = net.gateway, !gateway.isEmpty {
                        miniRow(label: localizer.t.proxmoxGateway, value: gateway)
                    }
                }
                .padding(8)
                .background(AppTheme.textMuted.opacity(0.05), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }

            // IPv6
            if let addr6 = net.address6, !addr6.isEmpty {
                VStack(spacing: 0) {
                    miniRow(label: localizer.t.proxmoxIpv6, value: net.cidr6 ?? addr6)
                    if let gw6 = net.gateway6, !gw6.isEmpty {
                        miniRow(label: localizer.t.proxmoxIpv6Gateway, value: gw6)
                    }
                }
                .padding(8)
                .background(AppTheme.textMuted.opacity(0.05), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }

            // Bridge/Bond info
            if let ports = net.bridge_ports, !ports.isEmpty {
                miniRow(label: localizer.t.proxmoxBridgePorts, value: ports)
            }
            if let slaves = net.slaves, !slaves.isEmpty {
                miniRow(label: localizer.t.proxmoxBondSlaves, value: slaves)
            }
            if let bondMode = net.bond_mode, !bondMode.isEmpty {
                miniRow(label: localizer.t.proxmoxBondMode, value: bondMode)
            }
            if let comment = net.comments, !comment.isEmpty {
                Text(comment)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                    .padding(.top, 2)
            }
        }
        .padding(12)
        .glassCard()
    }

    // MARK: - Helpers

    private func infoRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.primary)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private func miniRow(label: String, value: String) -> some View {
        HStack {
            Text(label)
                .font(.caption2.bold())
                .foregroundStyle(AppTheme.textMuted)
                .frame(width: 80, alignment: .leading)
            Text(value)
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .foregroundStyle(.primary)
                .lineLimit(1)
            Spacer()
        }
        .padding(.vertical, 2)
    }

    // MARK: - Data

    private func fetchData() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            async let netsTask = client.getNetworks(node: nodeName)
            async let dnsTask = client.getNodeDNS(node: nodeName)
            networks = try await netsTask
            dns = try await dnsTask
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}
