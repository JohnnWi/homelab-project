import SwiftUI

struct AdGuardHomeRewritesView: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var rewrites: [AdGuardRewriteEntry] = []
    @State private var isLoading = true
    @State private var error: Error?
    @State private var isPresentingEditor = false
    @State private var editingEntry: AdGuardRewriteEntry?
    @State private var newDomain = ""
    @State private var newAnswer = ""
    @State private var isSubmitting = false

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
                        .accessibilityHidden(true)
                    Text(error.localizedDescription)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                    Button(localizer.t.retry) {
                        Task { await fetchRewrites() }
                    }
                    .buttonStyle(.bordered)
                }
                .padding()
            } else {
                List {
                    if rewrites.isEmpty {
                        Text(localizer.t.adguardNoRewrites)
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                            .listRowBackground(Color.clear)
                            .listRowSeparator(.hidden)
                    } else {
                        ForEach(rewrites) { entry in
                            rewriteRow(entry)
                                .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                        }
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .navigationTitle(localizer.t.adguardRewrites)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    beginAdd()
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel(localizer.t.adguardAddRewrite)
            }
        }
        .task { await fetchRewrites() }
        .sheet(isPresented: $isPresentingEditor) {
            rewriteEditorSheet
        }
    }

    private func fetchRewrites() async {
        isLoading = true
        error = nil
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            rewrites = try await client.getRewrites()
        } catch {
            self.error = error
        }
        isLoading = false
    }

    private func addRewrite() async {
        let domain = newDomain.trimmingCharacters(in: .whitespacesAndNewlines)
        let answer = newAnswer.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !domain.isEmpty, !answer.isEmpty else { return }
        isSubmitting = true
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            try await client.addRewrite(domain: domain, answer: answer)
            rewrites = try await client.getRewrites()
            isPresentingEditor = false
        } catch {
            self.error = error
        }
        isSubmitting = false
    }

    private func removeRewrite(_ entry: AdGuardRewriteEntry) async {
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            try await client.deleteRewrite(domain: entry.domain, answer: entry.answer)
            rewrites = try await client.getRewrites()
        } catch {
            self.error = error
        }
    }

    private func updateRewrite(_ entry: AdGuardRewriteEntry) async {
        let domain = newDomain.trimmingCharacters(in: .whitespacesAndNewlines)
        let answer = newAnswer.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !domain.isEmpty, !answer.isEmpty else { return }
        isSubmitting = true
        do {
            guard let client = await servicesStore.adguardClient(instanceId: instanceId) else {
                throw APIError.notConfigured
            }
            let updated = AdGuardRewriteEntry(domain: domain, answer: answer, enabled: entry.enabled)
            try await client.updateRewrite(target: entry, update: updated)
            rewrites = try await client.getRewrites()
            isPresentingEditor = false
        } catch {
            self.error = error
        }
        isSubmitting = false
    }

    private func beginAdd() {
        editingEntry = nil
        newDomain = ""
        newAnswer = ""
        isPresentingEditor = true
    }

    private func beginEdit(_ entry: AdGuardRewriteEntry) {
        editingEntry = entry
        newDomain = entry.domain
        newAnswer = entry.answer
        isPresentingEditor = true
    }

    private func rewriteRow(_ entry: AdGuardRewriteEntry) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "arrow.triangle.2.circlepath.circle.fill")
                .font(.title3)
                .foregroundStyle(AppTheme.accent)
                .frame(width: 40, height: 40)
                .background(AppTheme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(entry.domain)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                Text(entry.answer)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

        }
        .padding(12)
        .glassCard()
        .contentShape(Rectangle())
        .onTapGesture { beginEdit(entry) }
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                Task { await removeRewrite(entry) }
            } label: {
                Image(systemName: "trash")
            }
        }
    }

    private var rewriteEditorSheet: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(localizer.t.adguardAddRewriteDesc)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)

                        VStack(spacing: 10) {
                            TextField(localizer.t.adguardRewriteDomain, text: $newDomain)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .padding(12)
                                .glassCard()
                            TextField(localizer.t.adguardRewriteAnswer, text: $newAnswer)
                                .textInputAutocapitalization(.never)
                                .autocorrectionDisabled()
                                .padding(12)
                                .glassCard()
                        }
                        .padding(12)
                        .glassCard()
                    }
                }
                .padding(AppTheme.padding)
            }
            .navigationTitle(editingEntry == nil ? localizer.t.adguardAddRewrite : localizer.t.adguardRewrites)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(localizer.t.cancel) { isPresentingEditor = false }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button(localizer.t.save) {
                        Task {
                            if let entry = editingEntry {
                                await updateRewrite(entry)
                            } else {
                                await addRewrite()
                            }
                        }
                    }
                    .disabled(isSubmitting || newDomain.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || newAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
