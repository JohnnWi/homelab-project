import SwiftUI

// Maps to app/service-login.tsx
// Modal sheet for connecting to a service.

struct ServiceLoginView: View {
    let serviceType: ServiceType

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    @State private var url = ""
    @State private var username = ""
    @State private var password = ""
    @State private var apiKey = ""
    @State private var showPassword = false
    @State private var useApiKeyMode = false
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var shakeOffset: CGFloat = 0

    private var serviceColor: Color { serviceType.colors.primary }
    private var needsUsername: Bool { serviceType != .pihole && serviceType != .portainer }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 0) {
                    headerSection
                    formSection
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 40)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(AppTheme.background)
            .onTapGesture { endEditing() }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .symbolRenderingMode(.hierarchical)
                            .font(.title3)
                            .foregroundStyle(AppTheme.textSecondary)
                    }
                }
            }
        }
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 16) {
            Image(systemName: serviceType.symbolName)
                .font(.largeTitle)
                .foregroundStyle(serviceColor)
                .frame(width: 80, height: 80)
                .background(serviceType.colors.bg, in: RoundedRectangle(cornerRadius: 24, style: .continuous))

            Text(serviceType.displayName)
                .font(.title.bold())
                .foregroundStyle(.primary)

            Text(localizer.t.loginSubtitle)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 20)
        .padding(.bottom, 36)
    }

    // MARK: - Form

    private var formSection: some View {
        VStack(spacing: 14) {
            // Hint banner
            if let hint = loginHint {
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "info.circle.fill")
                        .foregroundStyle(AppTheme.info)
                        .font(.subheadline)
                    Text(hint)
                        .font(.caption)
                        .foregroundStyle(AppTheme.info)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(AppTheme.info.opacity(0.08), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(AppTheme.info.opacity(0.2), lineWidth: 1)
                )
            }

            // Error banner
            if let errorMessage {
                HStack(spacing: 10) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundStyle(AppTheme.danger)
                    Text(errorMessage)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.danger)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(14)
                .background(AppTheme.danger.opacity(0.08), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .stroke(AppTheme.danger.opacity(0.2), lineWidth: 1)
                )
            }

            // URL field
            InputField(
                icon: "globe",
                placeholder: localizer.t.loginUrlPlaceholder,
                text: $url,
                keyboardType: .URL
            )


            // Credential fields
            if serviceType == .portainer {
                InputField(
                    icon: "key.fill",
                    placeholder: "API Key",
                    text: $apiKey,
                    isSecure: !showPassword,
                    showToggle: true,
                    toggleAction: { showPassword.toggle() },
                    showPassword: showPassword,
                    onSubmit: handleLogin
                )
            } else {
                if needsUsername {
                    InputField(
                        icon: serviceType == .beszel ? "envelope.fill" : "person.fill",
                        placeholder: serviceType == .beszel ? localizer.t.loginEmail : localizer.t.loginUsername,
                        text: $username,
                        keyboardType: serviceType == .beszel ? .emailAddress : .default
                    )
                }

                InputField(
                    icon: "lock.fill",
                    placeholder: localizer.t.loginPassword,
                    text: $password,
                    isSecure: !showPassword,
                    showToggle: true,
                    toggleAction: { showPassword.toggle() },
                    showPassword: showPassword,
                    onSubmit: handleLogin
                )
            }

            // Connect button
            Button(action: handleLogin) {
                Group {
                    if isLoading {
                        ProgressView()
                            .tint(.white)
                    } else {
                        Text(localizer.t.loginConnect)
                            .fontWeight(.semibold)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
            }
            .buttonStyle(.borderedProminent)
            .tint(serviceColor)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .disabled(isLoading)
            .padding(.top, 6)
        }
        .offset(x: shakeOffset)
    }

    // MARK: - Login Logic

    private var loginHint: String? {
        switch serviceType {
        case .portainer: return localizer.t.loginHintPortainer
        case .pihole: return localizer.t.loginHintPihole
        case .gitea:  return localizer.t.loginHintGitea2FA
        default: return nil
        }
    }

    private func handleLogin() {
        errorMessage = nil

        // Validate
        var cleanUrl = url.trimmingCharacters(in: .whitespaces)
        guard !cleanUrl.isEmpty else {
            showError(localizer.t.loginErrorUrl)
            return
        }
        if !cleanUrl.hasPrefix("http://") && !cleanUrl.hasPrefix("https://") {
            cleanUrl = "https://" + cleanUrl
        }

        if serviceType == .portainer {
            guard !apiKey.trimmingCharacters(in: .whitespaces).isEmpty else {
                showError(localizer.t.loginErrorCredentials)
                return
            }
        } else {
            if needsUsername && username.trimmingCharacters(in: .whitespaces).isEmpty {
                showError(localizer.t.loginErrorCredentials)
                return
            }
            if password.trimmingCharacters(in: .whitespaces).isEmpty {
                showError(localizer.t.loginErrorCredentials)
                return
            }
        }

        HapticManager.medium()
        isLoading = true

        Task {
            do {
                let connection = try await authenticate(url: cleanUrl)
                await servicesStore.connectService(connection)
                HapticManager.success()
                dismiss()
            } catch {
                showError(error.localizedDescription)
            }
            isLoading = false
        }
    }

    private func authenticate(url: String) async throws -> ServiceConnection {
        switch serviceType {
        case .portainer:
            try await servicesStore.portainerClient.authenticateWithApiKey(url: url, apiKey: apiKey.trimmingCharacters(in: .whitespaces))
            return ServiceConnection(type: .portainer, url: url, apiKey: apiKey.trimmingCharacters(in: .whitespaces))

        case .pihole:
            let sid = try await servicesStore.piholeClient.authenticate(url: url, password: password)
            return ServiceConnection(type: .pihole, url: url, token: sid)

        case .beszel:
            let token = try await servicesStore.beszelClient.authenticate(url: url, email: username.trimmingCharacters(in: .whitespaces), password: password)
            return ServiceConnection(type: .beszel, url: url, token: token, username: username.trimmingCharacters(in: .whitespaces))

        case .gitea:
            let result = try await servicesStore.giteaClient.authenticate(url: url, username: username.trimmingCharacters(in: .whitespaces), password: password)
            return ServiceConnection(type: .gitea, url: url, token: result.token, username: result.username)
        }
    }

    private func showError(_ message: String) {
        errorMessage = message
        HapticManager.error()
        // Shake animation
        withAnimation(.default) { shakeOffset = 10 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            withAnimation(.default) { shakeOffset = -10 }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            withAnimation(.default) { shakeOffset = 10 }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            withAnimation(.spring(response: 0.2, dampingFraction: 0.5)) { shakeOffset = 0 }
        }
    }
}

// MARK: - InputField Component

private struct InputField: View {
    let icon: String
    let placeholder: String
    @Binding var text: String
    var keyboardType: UIKeyboardType = .default
    var isSecure: Bool = false
    var showToggle: Bool = false
    var toggleAction: (() -> Void)? = nil
    var showPassword: Bool = false
    var onSubmit: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 0) {
            Image(systemName: icon)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
                .frame(width: 40)
                .padding(.leading, 4)

            if isSecure {
                SecureField(placeholder, text: $text)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.go)
                    .onSubmit { onSubmit?() }
            } else {
                TextField(placeholder, text: $text)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .keyboardType(keyboardType)
                    .submitLabel(onSubmit != nil ? .go : .next)
                    .onSubmit { onSubmit?() }
                    .toolbar {
                        ToolbarItemGroup(placement: .keyboard) {
                            Spacer()
                            Button("Done") {
                                endEditing()
                            }
                        }
                    }
            }

            if showToggle {
                Button {
                    HapticManager.light()
                    toggleAction?()
                } label: {
                    Image(systemName: showPassword ? "eye.slash" : "eye")
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textMuted)
                }
                .buttonStyle(.plain)
                .padding(.horizontal, 14)
            }
        }
        .padding(.vertical, 16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(Color(.separator), lineWidth: 1)
        )
    }
}
