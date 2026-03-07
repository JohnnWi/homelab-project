import SwiftUI

// Maps to app/(tabs)/settings/index.tsx

struct SettingsView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer

    @State private var fallbackInputs: [ServiceType: String] = [:]
    @State private var showDisconnectAlert: ServiceType? = nil
    @State private var showCopiedToast = false
    @FocusState private var focusedField: ServiceType?

    private let cryptoAddress = "0x649641868e6876c2c1f04584a95679e01c1aaf0d"

    var body: some View {
        NavigationStack {
            ZStack {
                AppTheme.background.ignoresSafeArea()

                ScrollView {
                    GlassGroup(spacing: 24) {
                        VStack(spacing: 24) {
                            // Title
                            HStack {
                                Text(localizer.t.tabSettings)
                                    .font(.system(size: 32, weight: .bold))
                                Spacer()
                            }
                            .padding(.top, 8)

                            donationSection
                            themeSection
                            languageSection
                            servicesSection
                            contactsSection
                        }
                    }
                    .padding(16)
                    .padding(.bottom, 32)
                }
                .scrollDismissesKeyboard(.interactively)
                .toolbar {
                    ToolbarItemGroup(placement: .keyboard) {
                        Spacer()
                        Button(localizer.t.confirm) {
                            focusedField = nil
                            endEditing()
                        }
                    }
                }
                .onChange(of: focusedField) { oldValue, newValue in
                    if let old = oldValue, newValue != old {
                        saveFallback(for: old)
                    }
                }
            }
            .onTapGesture { endEditing() }
            .navigationBarHidden(true)
        }
        .overlay(alignment: .bottom) {
            if showCopiedToast {
                ToastView(message: localizer.t.settingsCopied)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                            withAnimation { showCopiedToast = false }
                        }
                    }
            }
        }
    }

    // MARK: - Sections

    private var donationSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.settingsSupportTitle)
                .font(.headline)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)

            Text(localizer.t.settingsSupportDesc)
                .font(.footnote)
                .foregroundStyle(.secondary)
                .lineSpacing(2)

            Button {
                UIPasteboard.general.string = cryptoAddress
                HapticManager.medium()
                withAnimation { showCopiedToast = true }
            } label: {
                HStack {
                    let masked = cryptoAddress.prefix(8) + "..." + cryptoAddress.suffix(6)
                    Text(masked)
                        .font(.system(.subheadline, design: .monospaced))
                        .foregroundStyle(AppTheme.accent)
                    Spacer()
                    Text(localizer.t.copy.uppercased())
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundStyle(AppTheme.accent)
                }
                .padding(12)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .glassCard(tint: AppTheme.accent.opacity(0.1))
    }

    private var themeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsTheme.uppercased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)

            HStack(spacing: 0) {
                ForEach(ThemeMode.allCases, id: \.self) { mode in
                    Button {
                        settingsStore.theme = mode
                        HapticManager.light()
                    } label: {
                        Text(themeLabel(mode))
                            .font(.subheadline)
                            .fontWeight(settingsStore.theme == mode ? .bold : .regular)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(settingsStore.theme == mode ? AppTheme.accent.opacity(0.2) : Color.clear)
                            .foregroundStyle(settingsStore.theme == mode ? AppTheme.accent : .primary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .glassCard(cornerRadius: 12)
        }
    }

    private var languageSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsLanguage.uppercased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)

            HStack(spacing: 20) {
                ForEach(Language.allCases, id: \.self) { lang in
                    Button {
                        settingsStore.language = lang
                        localizer.language = lang
                        HapticManager.light()
                    } label: {
                        Text(lang.flagEmoji)
                            .font(.system(size: 32))
                            .frame(width: 56, height: 56)
                            .background(settingsStore.language == lang ? AppTheme.accent.opacity(0.2) : Color(.tertiarySystemFill))
                            .clipShape(Circle())
                            .opacity(settingsStore.language == lang ? 1.0 : 0.5)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity)
        }
    }

    private var servicesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsServices.uppercased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)
                .padding(.top, 16)

            VStack(spacing: 0) {
                let services = ServiceType.allCases
                ForEach(Array(services.enumerated()), id: \.element.id) { index, type in
                    serviceRow(type)
                    if index < services.count - 1 {
                        Divider().padding(.horizontal, 16)
                    }
                }
            }
            .glassCard()
        }
        .alert(localizer.t.settingsDisconnectConfirm, isPresented: .init(
            get: { showDisconnectAlert != nil },
            set: { if !$0 { showDisconnectAlert = nil } }
        )) {
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.settingsDisconnect, role: .destructive) {
                if let type = showDisconnectAlert {
                    HapticManager.medium()
                    servicesStore.disconnectService(type)
                }
            }
        } message: {
            Text(localizer.t.settingsDisconnectMessage)
        }
    }

    private var contactsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsContacts.uppercased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)
                .padding(.top, 16)

            VStack(spacing: 0) {
                ContactRow(title: "Telegram", icon: "paperplane.fill", url: "https://t.me/finalyxre", color: Color(hex: "#26A5E4"))
                Divider().padding(.horizontal, 16)
                ContactRow(title: "Reddit", icon: "bubble.left.and.bubble.right.fill", url: "https://www.reddit.com/user/finalyxre/", color: Color(hex: "#FF4500"))
                Divider().padding(.horizontal, 16)
                ContactRow(title: "GitHub", icon: "terminal.fill", url: "https://github.com/JohnnWi/homelab-project", color: .primary)
            }
            .glassCard()
        }
    }


    // MARK: - Helpers

    @ViewBuilder
    private func serviceRow(_ type: ServiceType) -> some View {
        let connected = servicesStore.isConnected(type)
        let conn = servicesStore.connection(for: type)

        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                // Icon (placeholder style as Android)
                Text(String(type.displayName.prefix(1)))
                    .font(.headline)
                    .fontWeight(.bold)
                    .foregroundStyle(AppTheme.accent)
                    .frame(width: 32, height: 32)
                    .frame(width: 32, height: 32)
                    .glassCard(cornerRadius: 10)

                VStack(alignment: .leading, spacing: 2) {
                    Text(type.displayName)
                        .font(.body)
                        .fontWeight(.bold)

                    if connected {
                        Text(conn?.url ?? "")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    } else {
                        Text(localizer.t.settingsNotConnected)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Spacer()

                // Visibility toggle
                Button {
                    settingsStore.toggleServiceVisibility(type)
                    HapticManager.light()
                } label: {
                    Image(systemName: settingsStore.isServiceHidden(type) ? "eye.slash" : "eye")
                        .font(.caption)
                        .foregroundStyle(settingsStore.isServiceHidden(type) ? .secondary : AppTheme.accent)
                        .frame(width: 32, height: 32)
                        .background(settingsStore.isServiceHidden(type) ? Color.secondary.opacity(0.1) : AppTheme.accent.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)

                if connected {
                    Button {
                        showDisconnectAlert = type
                    } label: {
                        Image(systemName: "rectangle.portrait.and.arrow.forward")
                            .font(.caption)
                            .foregroundStyle(AppTheme.danger)
                            .frame(width: 32, height: 32)
                            .background(AppTheme.danger.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                } else {
                    Circle()
                        .fill(.secondary.opacity(0.3))
                        .frame(width: 8, height: 8)
                }
            }

            if connected {
                HStack {
                    Image(systemName: "link")
                        .font(.caption2)
                        .foregroundStyle(.secondary)

                    TextField(localizer.t.settingsFallbackUrl, text: fallbackBinding(for: type, current: conn?.fallbackUrl))
                        .font(.caption)
                        .focused($focusedField, equals: type)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .onSubmit { saveFallback(for: type) }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.secondary.opacity(0.2), lineWidth: 1)
                )
            }
        }
        .padding(16)
    }

    private func themeLabel(_ mode: ThemeMode) -> String {
        switch mode {
        case .dark: return localizer.t.settingsThemeDark
        case .light: return localizer.t.settingsThemeLight
        case .system: return localizer.t.settingsThemeAuto
        }
    }

    private func fallbackBinding(for type: ServiceType, current: String?) -> Binding<String> {
        Binding(
            get: { fallbackInputs[type] ?? current ?? "" },
            set: { fallbackInputs[type] = $0 }
        )
    }

    private func saveFallback(for type: ServiceType) {
        let value = (fallbackInputs[type] ?? "").trimmingCharacters(in: .whitespaces)
        Task { await servicesStore.updateFallbackURL(for: type, fallbackUrl: value) }
        HapticManager.light()
    }
}

// MARK: - Subviews

struct ContactRow: View {
    let title: String
    let icon: String
    let url: String
    let color: Color

    var body: some View {
        Button {
            if let url = URL(string: url) {
                HapticManager.light()
                UIApplication.shared.open(url)
            }
        } label: {
            HStack(spacing: 16) {
                Image(systemName: icon)
                    .font(.title3)
                    .foregroundStyle(color)
                    .frame(width: 40, height: 40)
                    .background(color.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                Text(title)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

struct ToastView: View {
    let message: String

    var body: some View {
        Text(message)
            .font(.subheadline)
            .fontWeight(.medium)
            .foregroundStyle(.white)
            .padding(.horizontal, 20)
            .padding(.vertical, 12)
            .background(Color.black.opacity(0.8))
            .clipShape(Capsule())
            .padding(.bottom, 24)
            .shadow(radius: 10)
    }
}
