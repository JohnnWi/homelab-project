import SwiftUI

struct AdGuardHomeUserRulesView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var rules: [String] = []
    @State private var isLoading = true
    @State private var error: Error?
    @State private var showingAddAlert = false
    @State private var newRuleText = ""

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
                        Task { await fetchRules() }
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
            } else {
                List {
                    if rules.isEmpty {
                        Text(localizer.t.adguardNoRules)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                            .listRowBackground(Color.clear)
                    } else {
                        ForEach(rules, id: \.self) { rule in
                            let isAllow = rule.hasPrefix("@@")
                            HStack(spacing: 12) {
                                Image(systemName: isAllow ? "checkmark.seal.fill" : "xmark.seal.fill")
                                    .foregroundStyle(isAllow ? AppTheme.running : AppTheme.stopped)
                                    .accessibilityHidden(true)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(cleanRule(rule))
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(.primary)
                                        .lineLimit(2)
                                    Text(isAllow ? localizer.t.adguardFilterAllowed : localizer.t.adguardFilterBlocked)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted)
                                    Text(rule)
                                        .font(.caption2)
                                        .foregroundStyle(AppTheme.textMuted.opacity(0.7))
                                        .lineLimit(1)
                                }

                                Spacer()
                            }
                            .padding(12)
                            .glassCard()
                            .contentShape(Rectangle())
                            .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) {
                                    Task { await removeRule(rule) }
                                } label: {
                                    Label(localizer.t.delete, systemImage: "trash")
                                }
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .listRowSeparator(.hidden)
            }
        }
        .navigationTitle(localizer.t.adguardUserRules)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    newRuleText = ""
                    showingAddAlert = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel(localizer.t.adguardAddRule)
            }
        }
        .task { await fetchRules() }
        .alert(localizer.t.adguardAddRule, isPresented: $showingAddAlert) {
            TextField(localizer.t.adguardRulePlaceholder, text: $newRuleText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.save) {
                Task { await addRule() }
            }
            .disabled(newRuleText.trimmingCharacters(in: .whitespaces).isEmpty)
        } message: {
            Text(localizer.t.adguardAddRuleDesc)
        }
    }

    private func fetchRules() async {
        isLoading = true
        error = nil
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let status = try await client.getFilteringStatus()
            rules = status.userRules
        } catch {
            self.error = error
        }
        isLoading = false
    }

    private func addRule() async {
        let rule = newRuleText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !rule.isEmpty else { return }
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let updated = rules + [rule]
            try await client.setUserRules(updated)
            rules = updated
        } catch {
            self.error = error
        }
    }

    private func removeRule(_ rule: String) async {
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let updated = rules.filter { $0 != rule }
            try await client.setUserRules(updated)
            rules = updated
        } catch {
            self.error = error
        }
    }

    private func cleanRule(_ rule: String) -> String {
        var cleaned = rule
        if cleaned.hasPrefix("@@||") {
            cleaned.removeFirst(4)
        } else if cleaned.hasPrefix("@@") {
            cleaned.removeFirst(2)
        }
        if cleaned.hasPrefix("||") {
            cleaned.removeFirst(2)
        }
        if cleaned.hasSuffix("^") {
            cleaned.removeLast()
        }
        return cleaned
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
