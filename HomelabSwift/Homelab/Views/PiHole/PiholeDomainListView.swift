import SwiftUI

struct PiholeDomainListView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var domains: [PiholeDomain] = []
    @State private var isLoading = true
    @State private var error: Error?
    @State private var selectedTab: PiholeDomainListType = .allow

    @State private var showingAddAlert = false
    @State private var newDomainText = ""

    var filteredDomains: [PiholeDomain] {
        domains.filter { $0.type == selectedTab }
    }

    var body: some View {
        Group {
            if isLoading {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if let error = error {
                VStack(spacing: 12) {
                    Image(systemName: "exclamationmark.triangle")
                        .font(.largeTitle)
                        .foregroundStyle(AppTheme.stopped)
                    Text(error.localizedDescription)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                    Button(localizer.t.retry) {
                        Task { await fetchDomains() }
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
            } else {
                VStack(spacing: 0) {
                    Picker("List Type", selection: $selectedTab) {
                        Text(localizer.t.piholeAllowed).tag(PiholeDomainListType.allow)
                        Text(localizer.t.piholeBlocked).tag(PiholeDomainListType.deny)
                    }
                    .pickerStyle(.segmented)
                    .padding()

                    List {
                        let filtered = filteredDomains
                        if filtered.isEmpty {
                            Text(localizer.t.piholeNoDomains)
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textMuted)
                                .listRowBackground(Color.clear)
                        } else {
                            ForEach(filtered) { domain in
                                HStack {
                                    Image(systemName: selectedTab == .allow ? "checkmark.circle.fill" : "xmark.circle.fill")
                                        .foregroundStyle(selectedTab == .allow ? AppTheme.running : AppTheme.stopped)
                                    Text(domain.domain)
                                        .font(.body)
                                }
                                .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                    Button(role: .destructive) {
                                        Task { await removeDomain(domain) }
                                    } label: {
                                        Label(localizer.t.delete, systemImage: "trash")
                                    }
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
        }
        .navigationTitle(localizer.t.piholeDomainManagement)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    newDomainText = ""
                    showingAddAlert = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .task {
            await fetchDomains()
        }
        .alert(localizer.t.piholeAddDomain, isPresented: $showingAddAlert) {
            TextField("example.com", text: $newDomainText)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.save) {
                Task { await addDomain() }
            }
            .disabled(newDomainText.trimmingCharacters(in: .whitespaces).isEmpty)
        } message: {
            Text(String(format: localizer.t.piholeAddDomainDesc, selectedTab == .allow ? localizer.t.piholeAllowed : localizer.t.piholeBlocked))
        }
    }

    private func fetchDomains() async {
        isLoading = true
        error = nil
        do {
            domains = try await servicesStore.piholeClient.getDomains()
        } catch {
            self.error = error
        }
        isLoading = false
    }

    private func addDomain() async {
        guard !newDomainText.isEmpty else { return }
        let url = newDomainText.trimmingCharacters(in: .whitespaces)
        do {
            try await servicesStore.piholeClient.addDomain(domain: url, to: selectedTab)
            await fetchDomains()
        } catch {
            self.error = error
        }
    }

    private func removeDomain(_ item: PiholeDomain) async {
        guard let type = item.type else { return }
        do {
            try await servicesStore.piholeClient.removeDomain(domain: item.domain, from: type)
            if let index = domains.firstIndex(where: { $0.id == item.id }) {
                domains.remove(at: index)
            }
        } catch {
            self.error = error
        }
    }
}
