import SwiftUI
import LocalAuthentication
import UIKit

// Maps to app/(tabs)/settings/index.tsx

struct SettingsView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer

    @State private var showCopiedToast = false
    @State private var showDisableSecurityAlert = false
    @State private var showChangePinFlow = false
    @State private var changePinStep: ChangePinStep = .currentPin
    @State private var currentPinInput = ""
    @State private var newPinInput = ""
    @State private var confirmPinInput = ""
    @State private var changePinError: String? = nil
    @State private var showDebugLogs = false
    @State private var showDebugAuthPin = false
    @State private var debugAuthPin = ""
    @State private var debugAuthError: String? = nil
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

                            updateBannerSection
                            donationSection
                            servicesSection
                            themeSection
                            appIconSection


                            languageSection
                            securitySection
                            backupSection
                            contactsSection
                            debugSection
                            versionSection
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
                            endEditing()
                        }
                    }
                }
            }
            .onTapGesture { endEditing() }
            .navigationBarHidden(true)
        }
        .onAppear {
            Task {
                await settingsStore.checkForUpdatesIfNeeded()
            }
        }
        .sheet(isPresented: $showDebugLogs) {
            DebugLogsView()
        }
        .alert(localizer.t.securityEnterPin, isPresented: $showDebugAuthPin) {
            SecureField(localizer.t.securityEnterPinDesc, text: $debugAuthPin)
                .keyboardType(.numberPad)
            Button(localizer.t.cancel, role: .cancel) {
                debugAuthPin = ""
                debugAuthError = nil
            }
            Button(localizer.t.confirm) {
                if settingsStore.verifyPin(debugAuthPin) {
                    showDebugAuthPin = false
                    debugAuthPin = ""
                    debugAuthError = nil
                    showDebugLogs = true
                } else {
                    debugAuthError = localizer.t.securityWrongPin
                    debugAuthPin = ""
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        debugAuthError = nil
                    }
                }
            }
        } message: {
            Text(debugAuthError ?? localizer.t.debugLogsAuthMessage)
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
                    Text(localizer.t.copy.sentenceCased())
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

    @ViewBuilder
    private var updateBannerSection: some View {
        if let latest = settingsStore.availableUpdateVersion {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 10) {
                    Image(systemName: "arrow.down.circle.fill")
                        .font(.title3)
                        .foregroundStyle(AppTheme.accent)
                    Text(localizer.t.settingsUpdateBannerTitle)
                        .font(.headline.weight(.bold))
                        .foregroundStyle(AppTheme.accent)
                    Spacer()
                }

                Text(String(format: localizer.t.settingsUpdateBannerBody, latest, appVersion))
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                HStack(spacing: 10) {
                    Button {
                        if let urlString = settingsStore.availableUpdateURL,
                           let url = URL(string: urlString) {
                            UIApplication.shared.open(url)
                        }
                    } label: {
                        Label(localizer.t.settingsUpdateAction, systemImage: "arrow.up.right.circle.fill")
                            .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(AppTheme.accent)

                    Button {
                        settingsStore.dismissUpdateBanner()
                    } label: {
                        Text(localizer.t.settingsUpdateDismiss)
                            .font(.subheadline.weight(.semibold))
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding(16)
            .glassCard(tint: AppTheme.accent.opacity(0.08))
        }
    }

    private var themeSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsTheme.sentenceCased())
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
                            .padding(.horizontal, 4)
                            .background(
                                settingsStore.theme == mode ? AppTheme.accent.opacity(0.2) : Color.clear,
                                in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                            )
                            .foregroundStyle(settingsStore.theme == mode ? AppTheme.accent : .primary)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(4)
            .glassCard(cornerRadius: 12)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }


    @ViewBuilder
    private var appIconSection: some View {
        if UIApplication.shared.supportsAlternateIcons {
            let columns = [
                GridItem(.flexible(), spacing: 10),
                GridItem(.flexible(), spacing: 10),
                GridItem(.flexible(), spacing: 10)
            ]
            VStack(alignment: .leading, spacing: 8) {
                Text(localizer.t.settingsAppIcon.sentenceCased())
                    .font(.caption2)
                    .fontWeight(.bold)
                    .foregroundStyle(AppTheme.accent)
                    .padding(.leading, 8)

                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(AppIconOption.allCases, id: \.self) { iconOption in
                        let selected = settingsStore.appIcon == iconOption
                        Button {
                            settingsStore.setAppIcon(iconOption)
                            HapticManager.light()
                        } label: {
                            VStack(spacing: 0) {
                                ZStack(alignment: .topTrailing) {
                                    appIconPreview(for: iconOption)
                                        .frame(width: 56, height: 56)

                                    if selected {
                                        Image(systemName: "checkmark.circle.fill")
                                            .font(.caption.weight(.bold))
                                            .foregroundStyle(AppTheme.accent)
                                            .offset(x: 4, y: -4)
                                    }
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 14)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(selected ? AppTheme.accent.opacity(0.12) : Color.clear)
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(
                                        selected ? AppTheme.accent.opacity(0.45) : Color.primary.opacity(0.08),
                                        lineWidth: 1
                                    )
                            )
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(label(for: iconOption))
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
                .padding(10)
                .glassCard(cornerRadius: 14)
            }
        }
    }

    private var languageSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsLanguage.sentenceCased())
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
                            .overlay(
                                Circle()
                                    .stroke(
                                        settingsStore.language == lang ? AppTheme.accent.opacity(0.35) : .clear,
                                        lineWidth: 1
                                    )
                            )
                            .opacity(settingsStore.language == lang ? 1.0 : 0.6)
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .glassCard(cornerRadius: 16)
        }
    }

    private func label(for icon: AppIconOption) -> String {
        switch icon {
        case .default:
            return localizer.t.settingsAppIconDefault
        case .dark:
            return localizer.t.settingsAppIconDark
        case .clearLight:
            return localizer.t.settingsAppIconClearLight
        case .clearDark:
            return localizer.t.settingsAppIconClearDark
        case .tintedLight:
            return localizer.t.settingsAppIconTintedLight
        case .tintedDark:
            return localizer.t.settingsAppIconTintedDark
        }
    }

    private var contactsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsContacts.sentenceCased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)
                .padding(.top, 16)

            VStack(spacing: 0) {
                ContactRow(
                    title: localizer.t.settingsContactTelegram,
                    iconUrl: "https://cdn.jsdelivr.net/gh/selfhst/icons/png/telegram.png",
                    fallbackSystemName: "paperplane.fill",
                    url: "https://t.me/finalyxre",
                    color: Color(hex: "#26A5E4")
                )
                Divider().padding(.horizontal, 16)
                ContactRow(
                    title: localizer.t.settingsContactReddit,
                    iconUrl: "https://cdn.jsdelivr.net/gh/selfhst/icons/png/reddit.png",
                    fallbackSystemName: "bubble.left.and.bubble.right.fill",
                    url: "https://www.reddit.com/user/finalyxre/",
                    color: Color(hex: "#FF4500")
                )
                Divider().padding(.horizontal, 16)
                ContactRow(
                    title: localizer.t.settingsContactLinuxUpdate,
                    iconUrl: "https://cdn.jsdelivr.net/gh/selfhst/icons/png/github.png",
                    fallbackSystemName: "chevron.left.slash.chevron.right",
                    url: "https://github.com/JohnnWi/homelab-project",
                    color: Color(hex: "#24292F")
                )
            }
            .glassCard()
        }
    }

    @ViewBuilder
    private func appIconPreview(for icon: AppIconOption) -> some View {
        if let image = UIImage(named: icon.previewAssetName) {
            Image(uiImage: image)
                .resizable()
                .interpolation(.high)
                .scaledToFit()
        } else {
            Image(systemName: settingsStore.appIcon == icon ? "app.badge.fill" : "app")
                .font(.system(size: 30, weight: .semibold))
                .foregroundStyle(settingsStore.appIcon == icon ? AppTheme.accent : AppTheme.textMuted)
        }
    }

    private var versionSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsVersion.sentenceCased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)
                .padding(.top, 16)

            HStack(spacing: 12) {
                Image(systemName: "app.badge")
                    .font(.title3)
                    .foregroundStyle(AppTheme.accent)
                    .frame(width: 32, height: 32)
                    .background(AppTheme.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                Text(appVersion)
                    .font(.system(.body, design: .monospaced).weight(.semibold))
                    .foregroundStyle(.primary)

                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .glassCard(tint: AppTheme.accent.opacity(0.05))

        }
    }


    private var servicesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsConfiguredServices.sentenceCased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)
                .padding(.top, 16)

            VStack(spacing: 0) {
                NavigationLink {
                    AuthGatedConfiguredServicesView()
                } label: {
                    HStack(spacing: 16) {
                        Image(systemName: "server.rack")
                            .font(.title3)
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 40, height: 40)
                            .background(AppTheme.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                        Text(localizer.t.settingsConfiguredServices)
                            .font(.body.weight(.medium))
                            .foregroundStyle(.primary)

                        Spacer()

                        if settingsStore.isPinSet {
                            Image(systemName: "lock.fill")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }

                        Image(systemName: "chevron.right")
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.textMuted)
                            .accessibilityHidden(true)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .glassCard()
        }
    }


    private var debugSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.settingsDebug.sentenceCased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)
                .padding(.top, 16)

            VStack(spacing: 0) {
                Button {
                    HapticManager.light()
                    if settingsStore.isPinSet {
                        showDebugAuthPin = true
                    } else {
                        showDebugLogs = true
                    }
                } label: {
                    HStack(spacing: 16) {
                        Image(systemName: "terminal.fill")
                            .font(.title3)
                            .foregroundStyle(AppTheme.accent)
                            .frame(width: 40, height: 40)
                            .background(AppTheme.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                        Text(localizer.t.settingsDebugLogs)
                            .font(.body.weight(.medium))
                            .foregroundStyle(.primary)

                        Spacer()

                        Image(systemName: "chevron.right")
                            .font(.caption.bold())
                            .foregroundStyle(AppTheme.textMuted)
                            .accessibilityHidden(true)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
            .glassCard()
        }
    }


    // MARK: - Security Section

    private var securitySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.securityTitle.sentenceCased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)
                .padding(.top, 16)

            VStack(spacing: 0) {
                // Biometric toggle
                if settingsStore.isPinSet {
                    let context = LAContext()
                    let canUseBiometric = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
                    let biometricLabel = context.biometryType == .faceID ? localizer.t.securityFaceId : localizer.t.securityTouchId

                    if canUseBiometric {
                        HStack {
                            Image(systemName: context.biometryType == .faceID ? "faceid" : "touchid")
                                .font(.title3)
                                .foregroundStyle(AppTheme.accent)
                                .frame(width: 32, height: 32)
                                .accessibilityHidden(true)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(biometricLabel)
                                    .font(.body.weight(.medium))
                                Text(localizer.t.securityBiometricDesc)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            Toggle("", isOn: Binding(
                                get: { settingsStore.biometricEnabled },
                                set: { settingsStore.biometricEnabled = $0 }
                            ))
                            .labelsHidden()
                            .tint(AppTheme.accent)
                        }
                        .padding(16)

                        Divider().padding(.horizontal, 16)
                    }

                    // Change PIN
                    Button {
                        changePinStep = .currentPin
                        currentPinInput = ""
                        newPinInput = ""
                        confirmPinInput = ""
                        changePinError = nil
                        showChangePinFlow = true
                    } label: {
                        HStack {
                            Image(systemName: "key.fill")
                                .font(.title3)
                                .foregroundStyle(AppTheme.accent)
                                .frame(width: 32, height: 32)

                            Text(localizer.t.securityChangePin)
                                .font(.body.weight(.medium))
                                .foregroundStyle(.primary)

                            Spacer()

                            Image(systemName: "chevron.right")
                                .font(.caption.bold())
                                .foregroundStyle(AppTheme.textMuted)
                                .accessibilityHidden(true)
                        }
                        .padding(16)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    Divider().padding(.horizontal, 16)

                    // Disable security
                    Button {
                        showDisableSecurityAlert = true
                    } label: {
                        HStack {
                            Image(systemName: "lock.slash.fill")
                                .font(.title3)
                                .foregroundStyle(AppTheme.danger)
                                .frame(width: 32, height: 32)

                            Text(localizer.t.securityDisable)
                                .font(.body.weight(.medium))
                                .foregroundStyle(AppTheme.danger)

                            Spacer()
                        }
                        .padding(16)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                } else {
                    // No PIN set — offer to set up
                    Button {
                        changePinStep = .newPin
                        currentPinInput = ""
                        newPinInput = ""
                        confirmPinInput = ""
                        changePinError = nil
                        showChangePinFlow = true
                        HapticManager.light()
                    } label: {
                        HStack {
                            Image(systemName: "lock.open.fill")
                                .font(.title3)
                                .foregroundStyle(AppTheme.accent)
                                .frame(width: 32, height: 32)
                                .accessibilityHidden(true)

                            Text(localizer.t.securitySetupPin)
                                .font(.body.weight(.medium))
                                .foregroundStyle(.primary)

                            Spacer()

                            Image(systemName: "chevron.right")
                                .font(.caption.bold())
                                .foregroundStyle(AppTheme.textMuted)
                                .accessibilityHidden(true)
                        }
                        .padding(16)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .glassCard()
        }
        .alert(localizer.t.securityDisableConfirm, isPresented: $showDisableSecurityAlert) {
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.securityDisable, role: .destructive) {
                settingsStore.clearSecurity()
                HapticManager.medium()
            }
        } message: {
            Text(localizer.t.securityDisableMessage)
        }
        .fullScreenCover(isPresented: $showChangePinFlow) {
            changePinView
        }
    }

    // MARK: - Backup Section

    private var backupSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(localizer.t.backupTitle.uppercased())
                .font(.caption2)
                .fontWeight(.bold)
                .foregroundStyle(AppTheme.accent)
                .padding(.leading, 8)

            NavigationLink(destination: BackupView()) {
                HStack(spacing: 16) {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.title3)
                        .foregroundStyle(AppTheme.accent)
                        .frame(width: 40, height: 40)
                        .background(AppTheme.accent.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                    VStack(alignment: .leading, spacing: 2) {
                        Text(localizer.t.backupTitle)
                            .font(.body.weight(.medium))
                            .foregroundStyle(.primary)
                        Text(localizer.t.backupInfoDesc)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                    }

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
            .glassCard()
        }
    }

    // MARK: - Change PIN Flow

    @ViewBuilder
    private var changePinView: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()

            VStack {
                HStack {
                    Button {
                        showChangePinFlow = false
                    } label: {
                        Image(systemName: "xmark")
                            .font(.title3)
                            .foregroundStyle(.primary)
                            .padding(12)
                    }
                    .accessibilityLabel(localizer.t.close)
                    .buttonStyle(.plain)
                    Spacer()
                }
                .padding(.horizontal, 8)

                switch changePinStep {
                case .currentPin:
                    PinEntryView(
                        pin: $currentPinInput,
                        title: localizer.t.securityCurrentPin,
                        subtitle: localizer.t.securityCurrentPinDesc,
                        errorMessage: changePinError,
                        onComplete: { pin in
                            if settingsStore.verifyPin(pin) {
                                changePinError = nil
                                currentPinInput = ""
                                withAnimation {
                                    changePinStep = .newPin
                                }
                            } else {
                                changePinError = localizer.t.securityWrongPin
                                currentPinInput = ""
                                HapticManager.error()
                                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                    changePinError = nil
                                }
                            }
                        }
                    )

                case .newPin:
                    PinEntryView(
                        pin: $newPinInput,
                        title: localizer.t.securityNewPin,
                        subtitle: localizer.t.securityNewPinDesc,
                        onComplete: { _ in
                            withAnimation {
                                changePinStep = .confirmNewPin
                            }
                        }
                    )

                case .confirmNewPin:
                    PinEntryView(
                        pin: $confirmPinInput,
                        title: localizer.t.securityConfirmPin,
                        subtitle: localizer.t.securityConfirmPinDesc,
                        errorMessage: changePinError,
                        onComplete: { pin in
                            if pin == newPinInput {
                                settingsStore.savePin(pin)
                                HapticManager.success()
                                showChangePinFlow = false
                            } else {
                                changePinError = localizer.t.securityPinMismatch
                                confirmPinInput = ""
                                newPinInput = ""
                                HapticManager.error()
                                DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                    changePinError = nil
                                    withAnimation {
                                        changePinStep = .newPin
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // MARK: - Helpers

    private func themeLabel(_ mode: ThemeMode) -> String {
        switch mode {
        case .dark: return localizer.t.settingsThemeDark
        case .light: return localizer.t.settingsThemeLight
        case .system: return localizer.t.settingsThemeAuto
        }
    }

    private var appVersion: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        if let version, !version.isEmpty {
            return version
        }
        return "—"
    }

}

// MARK: - ChangePinStep

enum ChangePinStep {
    case currentPin, newPin, confirmNewPin
}



// MARK: - Subviews

struct ContactRow: View {
    let title: String
    let iconUrl: String
    let fallbackSystemName: String
    let url: String
    let color: Color

    private var iconAsset: some View {
        guard let url = URL(string: iconUrl) else {
            return AnyView(
                Image(systemName: fallbackSystemName)
                    .font(.title3)
                    .foregroundStyle(color)
            )
        }

        return AnyView(
            AsyncImage(url: url) { phase in
                switch phase {
                case .empty:
                    ProgressView()
                        .tint(color)
                case .success(let image):
                    image
                        .resizable()
                        .renderingMode(.original)
                        .scaledToFit()
                case .failure:
                    Image(systemName: fallbackSystemName)
                        .font(.title3)
                        .foregroundStyle(color)
                @unknown default:
                    Image(systemName: fallbackSystemName)
                        .font(.title3)
                        .foregroundStyle(color)
                }
            }
        )
    }

    var body: some View {
        Button {
            if let url = URL(string: url) {
                HapticManager.light()
                UIApplication.shared.open(url)
            }
        } label: {
            HStack(spacing: 16) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .fill(color.opacity(0.1))
                    iconAsset
                        .frame(width: 22, height: 22)
                }
                .frame(width: 40, height: 40)

                Text(title)
                    .font(.body.weight(.medium))
                    .foregroundStyle(.primary)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption.bold())
                    .foregroundStyle(AppTheme.textMuted)
                    .accessibilityHidden(true)
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

// MARK: - Debug Log View

struct DebugLogsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(Localizer.self) private var localizer
    @State private var logStore = LogStore.shared
    @State private var showCopiedBanner = false
    @State private var scrollProxy: ScrollViewProxy?

    // Filter state
    @State private var selectedLevels: Set<LogStore.LogLevel> = Set(LogStore.LogLevel.allCases)
    @State private var selectedSources: Set<String> = []
    @State private var searchText = ""
    @State private var showFilters = true

    // All unique sources currently in the log
    private var availableSources: [String] {
        Array(Set(logStore.entries.map { $0.source })).sorted()
    }

    private var filteredEntries: [LogStore.LogEntry] {
        let reversed = Array(logStore.entries.reversed())
        return reversed.filter { entry in
            // Level filter
            guard selectedLevels.contains(entry.level) else { return false }
            // Source filter (empty = show all)
            if !selectedSources.isEmpty && !selectedSources.contains(entry.source) {
                return false
            }
            // Text search
            if !searchText.isEmpty {
                let query = searchText.lowercased()
                return entry.message.lowercased().contains(query)
                    || entry.source.lowercased().contains(query)
                    || entry.level.rawValue.lowercased().contains(query)
            }
            return true
        }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                AppTheme.premiumGradient().ignoresSafeArea()

                VStack(spacing: 0) {
                    // Filter section (animated)
                    if showFilters {
                        filterSection
                            .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    // Count badge — always visible when there are logs
                    if !logStore.entries.isEmpty {
                        let filtered = filteredEntries
                        HStack {
                            Text("\(filtered.count) / \(logStore.entries.count)")
                                .font(.system(.caption2, design: .monospaced, weight: .semibold))
                                .foregroundStyle(AppTheme.textMuted)
                            Spacer()
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 4)
                    }

                    // Empty state — no logs at all
                    if logStore.entries.isEmpty {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "doc.text.magnifyingglass")
                                .font(.system(size: 40, weight: .light))
                                .foregroundStyle(AppTheme.textMuted)
                            Text(localizer.t.debugLogsEmpty)
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                        .padding(32)
                        Spacer()
                    }
                    // Empty state — filters too strict
                    else if filteredEntries.isEmpty {
                        Spacer()
                        VStack(spacing: 12) {
                            Image(systemName: "line.3.horizontal.decrease.circle")
                                .font(.system(size: 40, weight: .light))
                                .foregroundStyle(AppTheme.textMuted)
                            Text(localizer.t.debugLogsNoResults)
                                .font(.subheadline)
                                .foregroundStyle(AppTheme.textSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding(32)
                        Spacer()
                    }
                    // Log list
                    else {
                        ScrollViewReader { proxy in
                            List {
                                ForEach(filteredEntries) { entry in
                                    logRow(entry)
                                        .listRowBackground(Color.clear)
                                        .listRowSeparator(.hidden)
                                        .listRowInsets(EdgeInsets(top: 2, leading: 12, bottom: 2, trailing: 12))
                                        .id(entry.id)
                                }
                            }
                            .listStyle(.plain)
                            .background(Color.clear)
                            .onAppear { scrollProxy = proxy }
                        }
                    }
                }

                // Copied banner
                if showCopiedBanner {
                    VStack {
                        Spacer()
                        Text(localizer.t.debugLogsCopied)
                            .font(.subheadline.bold())
                            .foregroundStyle(.white)
                            .padding(.vertical, 12)
                            .padding(.horizontal, 24)
                            .background(AppTheme.accent.opacity(0.9), in: Capsule())
                            .shadow(radius: 10)
                            .padding(.bottom, 40)
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                    }
                    .ignoresSafeArea()
                    .zIndex(1)
                }
            }
            .navigationTitle(localizer.t.settingsDebugLogs)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(localizer.t.close) { dismiss() }
                }

                ToolbarItem(placement: .topBarTrailing) {
                    HStack(spacing: 4) {
                        // Scroll to newest
                        Button {
                            if let first = filteredEntries.first {
                                withAnimation(.snappy(duration: 0.3)) {
                                    scrollProxy?.scrollTo(first.id, anchor: .top)
                                }
                            }
                            HapticManager.light()
                        } label: {
                            Image(systemName: "arrow.up.to.line")
                        }

                        // Toggle filters
                        Button {
                            withAnimation(.snappy(duration: 0.25)) {
                                showFilters.toggle()
                            }
                            HapticManager.light()
                        } label: {
                            Image(systemName: showFilters ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
                        }

                        // Copy logs
                        Button {
                            copyLogs()
                        } label: {
                            Image(systemName: "doc.on.doc")
                        }

                        // Clear logs
                        Button {
                            logStore.clear()
                            HapticManager.light()
                        } label: {
                            Image(systemName: "trash")
                                .foregroundStyle(.red)
                        }
                    }
                }
            }
        }
    }

    // MARK: - Filter Section

    private var filterSection: some View {
        VStack(spacing: 10) {
            // Search bar
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textMuted)
                TextField(localizer.t.debugLogsSearchPlaceholder, text: $searchText)
                    .font(.subheadline)
                    .textFieldStyle(.plain)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                if !searchText.isEmpty {
                    Button {
                        withAnimation(.snappy(duration: 0.2)) {
                            searchText = ""
                        }
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .glassCard(cornerRadius: AppTheme.smallRadius)

            // Level chips
            GlassEffectContainer(spacing: 6) {
                HStack(spacing: 6) {
                    ForEach(LogStore.LogLevel.allCases, id: \.rawValue) { level in
                        levelChip(level)
                    }
                }
            }

            // Source chips (only show if there are multiple sources)
            if availableSources.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    GlassEffectContainer(spacing: 6) {
                        HStack(spacing: 6) {
                            ForEach(availableSources, id: \.self) { source in
                                sourceChip(source)
                            }
                        }
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }

    // MARK: - Level chip

    private func levelChip(_ level: LogStore.LogLevel) -> some View {
        let isSelected = selectedLevels.contains(level)
        let tint = colorForLevel(level)

        return Button {
            withAnimation(.snappy(duration: 0.2)) {
                if isSelected {
                    selectedLevels.remove(level)
                } else {
                    selectedLevels.insert(level)
                }
            }
            HapticManager.light()
        } label: {
            HStack(spacing: 5) {
                Image(systemName: level.icon)
                    .font(.system(size: 10, weight: .semibold))
                Text(level.rawValue)
                    .font(.system(.caption2, design: .monospaced, weight: .bold))
            }
            .foregroundStyle(isSelected ? tint : AppTheme.textMuted)
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .glassCard(cornerRadius: AppTheme.pillRadius, tint: isSelected ? tint.opacity(0.15) : nil)
            .opacity(isSelected ? 1.0 : 0.5)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Source chip

    private func sourceChip(_ source: String) -> some View {
        let isSelected = selectedSources.contains(source)
        let showAll = selectedSources.isEmpty

        return Button {
            withAnimation(.snappy(duration: 0.2)) {
                if isSelected {
                    selectedSources.remove(source)
                } else {
                    selectedSources.insert(source)
                }
            }
            HapticManager.light()
        } label: {
            Text(source)
                .font(.system(.caption2, design: .rounded, weight: .semibold))
                .foregroundStyle(isSelected ? AppTheme.accent : (showAll ? .primary : AppTheme.textMuted))
                .padding(.horizontal, 10)
                .padding(.vertical, 7)
                .glassCard(cornerRadius: AppTheme.pillRadius, tint: isSelected ? AppTheme.accent.opacity(0.15) : nil)
                .opacity(isSelected || showAll ? 1.0 : 0.5)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Log Row

    private func logRow(_ entry: LogStore.LogEntry) -> some View {
        HStack(spacing: 0) {
            // Color accent strip
            RoundedRectangle(cornerRadius: 2)
                .fill(colorForLevel(entry.level))
                .frame(width: 3)
                .padding(.vertical, 4)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Image(systemName: entry.level.icon)
                        .font(.caption2)
                        .foregroundStyle(colorForLevel(entry.level))

                    Text(entry.formattedTime)
                        .font(.system(.caption2, design: .monospaced))
                        .foregroundStyle(.secondary)

                    Text(entry.level.rawValue)
                        .font(.system(.caption2, design: .monospaced, weight: .bold))
                        .foregroundStyle(colorForLevel(entry.level))

                    Spacer()

                    Text(entry.source)
                        .font(.system(.caption2, design: .rounded, weight: .medium))
                        .foregroundStyle(AppTheme.accent.opacity(0.8))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(AppTheme.accent.opacity(0.08), in: Capsule())
                }

                Text(entry.message)
                    .font(.system(.caption, design: .monospaced))
                    .foregroundStyle(.primary)
                    .textSelection(.enabled)
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 10)
        }
        .background(Color.primary.opacity(0.05), in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: - Helpers

    private func colorForLevel(_ level: LogStore.LogLevel) -> Color {
        switch level {
        case .debug: return .secondary
        case .info: return .blue
        case .warn: return .orange
        case .error: return .red
        case .network: return .purple
        }
    }

    private func copyLogs() {
        UIPasteboard.general.string = logStore.export()
        HapticManager.success()
        withAnimation {
            showCopiedBanner = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            withAnimation {
                showCopiedBanner = false
            }
        }
    }
}
