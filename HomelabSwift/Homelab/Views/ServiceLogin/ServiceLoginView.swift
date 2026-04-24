import SwiftUI

struct ServiceLoginView: View {
    let serviceType: ServiceType
    var existingInstanceId: UUID? = nil

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss

    @State private var label = ""
    @State private var url = ""
    @State private var fallbackUrl = ""
    @State private var username = ""
    @State private var password = ""
    @State private var mfaCode = ""
    @State private var apiKey = ""
    @State private var showPassword = false
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var shakeOffset: CGFloat = 0
    @State private var didPrefill = false
    @State private var allowSelfSigned = true
    @State private var proxmoxAuthMode: Int = 0 // 0 = Credentials, 1 = API Token
    @State private var proxmoxRealm = "pam"
    @State private var proxmoxApiTokenEntryMode = 0 // 0 = Guided, 1 = Raw
    @State private var proxmoxApiUser = ""
    @State private var proxmoxApiRealm = "pam"
    @State private var proxmoxApiTokenId = ""
    @State private var proxmoxApiTokenSecret = ""

    private var existingInstance: ServiceInstance? {
        existingInstanceId.flatMap { servicesStore.instance(id: $0) }
    }

    private var isEditing: Bool { existingInstance != nil }
    private var serviceColor: Color { serviceType.colors.primary }
    private var needsUsername: Bool {
        serviceType == .beszel
            || serviceType == .gitea
            || serviceType == .nginxProxyManager
            || serviceType == .adguardHome
            || serviceType == .technitium
            || serviceType == .patchmon
            || serviceType == .qbittorrent
            || serviceType == .craftyController
            || serviceType == .dockhand
            || serviceType == .maltrail
    }

    private var usesApiKeyAuth: Bool {
        serviceType == .portainer
            || serviceType == .healthchecks
            || serviceType == .linuxUpdate
            || serviceType == .dockmon
            || serviceType == .pangolin
            || serviceType == .jellystat
            || serviceType == .plex
            || serviceType == .radarr
            || serviceType == .sonarr
            || serviceType == .lidarr
            || serviceType == .jellyseerr
            || serviceType == .prowlarr
            || serviceType == .bazarr
            || serviceType == .wakapi
    }

    private var usesKomodoAuth: Bool {
        serviceType == .komodo
    }

    private var supportsCredentiallessAuth: Bool {
        serviceType == .gluetun || serviceType == .flaresolverr
    }

    private var supportsOptionalApiKey: Bool {
        serviceType == .gluetun || serviceType == .flaresolverr
    }

    private var isProxmox: Bool {
        serviceType == .proxmox
    }

    private var canSubmit: Bool {
        let cleanUrl = normalizedURL(url)
        guard !cleanUrl.isEmpty else { return false }

        if !isProxmox {
            return true
        }

        if proxmoxAuthMode == 1 {
            if proxmoxApiTokenEntryMode == 0 {
                return ProxmoxAPITokenParts(
                    user: proxmoxApiUser,
                    realm: proxmoxApiRealm,
                    tokenID: proxmoxApiTokenId,
                    secret: proxmoxApiTokenSecret
                ) != nil
            }
            return normalizedOptional(apiKey) != nil
        }

        let identity = normalizedOptional(username) ?? existingInstance?.username
        if identity == nil { return false }
        let storedPassword = existingInstance?.password
        return normalizedOptional(password) != nil || (isEditing && storedPassword?.isEmpty == false)
    }

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
                        Image(systemName: "xmark")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(Color(uiColor: .secondaryLabel))
                            .padding(8)
                            .background(Color(uiColor: .tertiarySystemFill), in: Circle())
                    }
                    .accessibilityLabel(localizer.t.close)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button(localizer.t.done) {
                        endEditing()
                    }
                }
            }
            .task {
                prefillIfNeeded()
            }
            .onChange(of: proxmoxApiTokenEntryMode) { _, newValue in
                guard isProxmox, proxmoxAuthMode == 1 else { return }
                if newValue == 0, let parts = ProxmoxAPITokenParts(rawValue: apiKey) {
                    proxmoxApiUser = parts.user
                    proxmoxApiRealm = parts.realm
                    proxmoxApiTokenId = parts.tokenID
                    proxmoxApiTokenSecret = parts.secret
                } else if newValue == 1, let token = ProxmoxAPITokenParts(
                    user: proxmoxApiUser,
                    realm: proxmoxApiRealm,
                    tokenID: proxmoxApiTokenId,
                    secret: proxmoxApiTokenSecret
                )?.rawValue {
                    apiKey = token
                }
            }
        }
    }

    private var headerSection: some View {
        VStack(spacing: 16) {
            ServiceIconView(type: serviceType, size: 46)
                .frame(width: 80, height: 80)
                .background(serviceType.colors.bg, in: RoundedRectangle(cornerRadius: 24, style: .continuous))

            Text(isEditing ? String(format: localizer.t.loginEditTitle, serviceType.displayName) : serviceType.displayName)
                .font(.title.bold())
                .foregroundStyle(.primary)

            Text(isEditing ? localizer.t.loginEditSubtitle : (supportsCredentiallessAuth ? localizer.t.loginUrlOnlySubtitle : localizer.t.loginSubtitle))
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 20)
        .padding(.bottom, 36)
    }

    private var formSection: some View {
        VStack(spacing: 14) {
            if let hint = loginHint {
                VStack(spacing: 8) {
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

                    if serviceType == .nginxProxyManager {
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundStyle(AppTheme.danger)
                                .font(.subheadline)
                            Text(localizer.t.loginHintNpm2FAWarning)
                                .font(.caption)
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
                }
            }

            if serviceType == .healthchecks {
                healthchecksApiKeyBanner
            }

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

            InputField(
                icon: "tag.fill",
                placeholder: localizer.t.loginLabel,
                text: $label
            )

            InputField(
                icon: "globe",
                placeholder: localizer.t.loginUrlPlaceholder,
                text: $url,
                keyboardType: .URL
            )

            InputField(
                icon: "link",
                placeholder: localizer.t.loginFallbackOptional,
                text: $fallbackUrl,
                keyboardType: .URL
            )

            if supportsOptionalApiKey {
                InputField(
                    icon: "key.fill",
                    placeholder: localizer.t.loginApiKey,
                    text: $apiKey,
                    isSecure: !showPassword,
                    showToggle: true,
                    toggleAction: { showPassword.toggle() },
                    showPassword: showPassword
                )
            }

            if isProxmox {
                proxmoxAuthSection
            } else if usesKomodoAuth {
                InputField(
                    icon: "key.fill",
                    placeholder: localizer.t.loginApiKey,
                    text: $apiKey,
                    isSecure: !showPassword,
                    showToggle: true,
                    toggleAction: { showPassword.toggle() },
                    showPassword: showPassword
                )

                InputField(
                    icon: "lock.fill",
                    placeholder: isEditing ? localizer.t.loginPasswordIfChanging : localizer.t.komodoApiSecret,
                    text: $password,
                    isSecure: !showPassword,
                    showToggle: true,
                    toggleAction: { showPassword.toggle() },
                    showPassword: showPassword,
                    onSubmit: handleSave
                )
            } else if usesApiKeyAuth {
                InputField(
                    icon: "key.fill",
                    placeholder: localizer.t.loginApiKey,
                    text: $apiKey,
                    isSecure: !showPassword,
                    showToggle: true,
                    toggleAction: { showPassword.toggle() },
                    showPassword: showPassword,
                    onSubmit: handleSave
                )

                if serviceType == .pangolin {
                    InputField(
                        icon: "building.2.fill",
                        placeholder: localizer.t.loginPangolinOrgIdPlaceholder,
                        text: $username,
                        onSubmit: handleSave
                    )
                }
            } else if !supportsCredentiallessAuth {
                if needsUsername {
                    let isEmailField = serviceType == .beszel || serviceType == .nginxProxyManager
                    InputField(
                        icon: serviceType == .patchmon ? "key.fill" : (isEmailField ? "envelope.fill" : "person.fill"),
                        placeholder: serviceType == .patchmon ? localizer.t.loginTokenKey : (isEmailField ? localizer.t.loginEmail : localizer.t.loginUsername),
                        text: $username,
                        keyboardType: isEmailField ? .emailAddress : .default
                    )
                }

                InputField(
                    icon: "lock.fill",
                    placeholder: isEditing
                        ? localizer.t.loginPasswordIfChanging
                        : (serviceType == .patchmon ? localizer.t.loginTokenSecret : localizer.t.loginPassword),
                    text: $password,
                    isSecure: !showPassword,
                    showToggle: true,
                    toggleAction: { showPassword.toggle() },
                    showPassword: showPassword,
                    onSubmit: handleSave
                )

                if serviceType == .dockhand || serviceType == .technitium {
                    InputField(
                        icon: "lock.rotation",
                        placeholder: localizer.t.loginOptional2FA,
                        text: $mfaCode,
                        keyboardType: .asciiCapable,
                        onSubmit: handleSave
                    )
                }
            }

            // TLS validation toggle
            HStack(spacing: 12) {
                Image(systemName: allowSelfSigned ? "lock.open.fill" : "lock.fill")
                    .font(.title3)
                    .foregroundStyle(allowSelfSigned ? AppTheme.warning : AppTheme.running)
                    .frame(width: 32, height: 32)
                    .background(
                        (allowSelfSigned ? AppTheme.warning : AppTheme.running).opacity(0.1),
                        in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(allowSelfSigned ? localizer.t.loginAllowSelfSigned : localizer.t.loginRequireValidTLS)
                        .font(.body.weight(.medium))
                    Text(localizer.t.loginTLSDesc)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textSecondary)
                }

                Spacer()

                Toggle("", isOn: $allowSelfSigned)
                    .labelsHidden()
                    .tint(AppTheme.accent)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .glassCard()

            Button(action: handleSave) {
                Group {
                    if isLoading {
                        ProgressView()
                            .tint(.white)
                    } else {
                        Text(isEditing ? localizer.t.save : localizer.t.loginConnect)
                            .fontWeight(.semibold)
                            .lineLimit(2)
                            .multilineTextAlignment(.center)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
            }
            .buttonStyle(.borderedProminent)
            .tint(serviceColor)
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            .disabled(isLoading || !canSubmit)
            .padding(.top, 6)

        }
        .offset(x: shakeOffset)
    }

    private var loginHint: String? {
        switch serviceType {
        case .portainer:         return localizer.t.loginHintPortainer
        case .pihole:            return localizer.t.loginHintPihole
        case .adguardHome:       return localizer.t.loginHintAdguard
        case .technitium:        return localizer.t.loginHintTechnitium
        case .linuxUpdate:       return localizer.t.loginHintLinuxUpdate
        case .dockhand:          return localizer.t.loginHintDockhand
        case .dockmon:           return localizer.t.loginHintDockmon
        case .komodo:            return localizer.t.loginHintKomodo
        case .maltrail:          return localizer.t.loginHintMaltrail
        case .craftyController:  return localizer.t.loginHintCraftyController
        case .gitea:             return localizer.t.loginHintGitea2FA
        case .nginxProxyManager: return localizer.t.loginHintNpm
        case .pangolin:          return localizer.t.loginHintPangolin
        case .healthchecks:      return localizer.t.loginHintHealthchecks
        case .patchmon:          return localizer.t.loginHintPatchmon
        case .jellystat:         return localizer.t.loginHintJellystat
        case .plex:              return localizer.t.loginHintPlex
        case .gluetun:
                                 return localizer.t.loginHintGluetun
        case .flaresolverr:
                                 return localizer.t.loginHintFlaresolverr
        case .wakapi:            return localizer.t.loginHintWakapi
        case .proxmox:           return localizer.t.loginHintProxmox
        case .qbittorrent, .radarr, .sonarr, .lidarr, .jellyseerr, .prowlarr, .bazarr:
                                 return nil
        default: return nil
        }
    }

    private var healthchecksApiKeyBanner: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "key.fill")
                .foregroundStyle(serviceColor)
                .font(.subheadline)
                .frame(width: 24, height: 24)
                .background(serviceColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 6) {
                Text(localizer.t.healthchecksApiKeyBannerTitle)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
                Text(localizer.t.healthchecksApiKeyBannerBody)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
        }
        .padding(14)
        .glassCard(tint: serviceColor.opacity(0.08))
    }

    private func prefillIfNeeded() {
        guard !didPrefill, let existing = existingInstance else {
            if !didPrefill && label.isEmpty {
                label = serviceType.displayName
            }
            didPrefill = true
            return
        }

        label = existing.displayLabel
        url = existing.url
        fallbackUrl = existing.fallbackUrl ?? ""
        allowSelfSigned = existing.allowSelfSigned
        proxmoxAuthMode = existing.proxmoxAuthMode == .apiToken ? 1 : 0
        proxmoxRealm = existing.proxmoxRealm ?? "pam"
        proxmoxApiRealm = "pam"
        proxmoxApiTokenEntryMode = 0
        proxmoxApiUser = ""
        proxmoxApiTokenId = ""
        proxmoxApiTokenSecret = ""

        if serviceType == .proxmox {
            username = existing.username ?? ""
            apiKey = proxmoxAuthMode == 1 ? (existing.apiKey ?? "") : ""
            password = proxmoxAuthMode == 0 ? (existing.password ?? "") : ""
            if proxmoxAuthMode == 1, let token = existing.apiKey, let parts = ProxmoxAPITokenParts(rawValue: token) {
                proxmoxApiUser = parts.user
                proxmoxApiRealm = parts.realm
                proxmoxApiTokenId = parts.tokenID
                proxmoxApiTokenSecret = parts.secret
                proxmoxApiTokenEntryMode = 0
            } else if proxmoxAuthMode == 1 {
                proxmoxApiTokenEntryMode = 1
            }
        } else {
            username = existing.username ?? ""
            apiKey = existing.apiKey ?? ""
            password = existing.piholePassword ?? existing.password ?? ""
        }
        mfaCode = ""
        didPrefill = true
    }

    private func handleSave() {
        errorMessage = nil

        let cleanUrl = normalizedURL(url)
        guard !cleanUrl.isEmpty else {
            showError(localizer.t.loginErrorUrl)
            return
        }

        let cleanFallback = normalizedOptionalURL(fallbackUrl)
        let cleanLabel = label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? serviceType.displayName : label.trimmingCharacters(in: .whitespacesAndNewlines)

        HapticManager.medium()
        isLoading = true

        Task {
            do {
                let instance = try await buildInstance(label: cleanLabel, url: cleanUrl, fallbackUrl: cleanFallback)
                await servicesStore.saveInstance(instance, refreshPiHoleAuth: false)
                HapticManager.success()
                dismiss()
            } catch {
                showError(resolveErrorMessage(error))
            }
            isLoading = false
        }
    }

    private func buildInstance(label: String, url: String, fallbackUrl: String?) async throws -> ServiceInstance {
        if let existing = existingInstance {
            let metadataOnly = existing.url == url
                && existing.username == normalizedOptional(username)
                && existing.apiKey == normalizedOptional(apiKey)
                && normalizedOptional(password).map { !$0.isEmpty } != true
                && serviceType != .proxmox

            if metadataOnly {
                return ServiceInstance(
                    id: existing.id,
                    type: existing.type,
                    label: label,
                    url: existing.url,
                    token: existing.token,
                    username: existing.username,
                    apiKey: existing.apiKey,
                    piholePassword: existing.piholePassword,
                    piholeAuthMode: existing.piholeAuthMode,
                    proxmoxAuthMode: existing.proxmoxAuthMode,
                    proxmoxRealm: existing.proxmoxRealm,
                    fallbackUrl: fallbackUrl,
                    allowSelfSigned: allowSelfSigned,
                    password: existing.password
                )
            }

            if serviceType == .proxmox, existing.url == url {
                let desiredAuthMode: ProxmoxAuthMode = proxmoxAuthMode == 1 ? .apiToken : .credentials

                if desiredAuthMode == .apiToken {
                    let desiredToken = resolvedProxmoxApiToken() ?? existing.apiKey
                    if existing.proxmoxAuthMode == .apiToken,
                       desiredToken == existing.apiKey {
                        return existing.updating(
                            label: label,
                            username: normalizedOptional(proxmoxApiUser) ?? existing.username,
                            apiKey: desiredToken,
                            proxmoxAuthMode: .apiToken,
                            proxmoxRealm: normalizedOptional(proxmoxApiRealm) ?? existing.proxmoxRealm,
                            fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
                        )
                    }
                } else {
                    let desiredUsername = normalizedOptional(username) ?? existing.username
                    let desiredRealm = normalizedOptional(proxmoxRealm) ?? existing.proxmoxRealm ?? "pam"
                    let passwordChanged = normalizedOptional(password) != nil
                    let otpProvided = normalizedOptional(mfaCode) != nil

                    if existing.proxmoxAuthMode != .apiToken,
                       desiredUsername == existing.username,
                       desiredRealm == (existing.proxmoxRealm ?? "pam"),
                       !passwordChanged,
                       !otpProvided {
                        return existing.updating(
                            label: label,
                            username: desiredUsername,
                            proxmoxAuthMode: .credentials,
                            proxmoxRealm: desiredRealm,
                            fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
                        )
                    }
                }
            }
        }

        switch serviceType {
        case .portainer:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = PortainerAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configureWithApiKey(url: url, apiKey: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticateWithApiKey(url: url, apiKey: key)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .portainer,
                label: label,
                url: url,
                token: existingInstance?.token ?? "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .healthchecks:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = HealthchecksAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiKey: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .healthchecks,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .wakapi:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = WakapiAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiKey: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .wakapi,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .linuxUpdate:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = LinuxUpdateAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiToken: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiToken: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .linuxUpdate,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .dockmon:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = DockmonAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiKey: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .dockmon,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .komodo:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            let secret = normalizedOptional(password)
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && (url != existingInstance?.url || key != existingInstance?.apiKey) && secret == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }
            let resolvedSecret: String
            if let secret, !secret.isEmpty {
                resolvedSecret = secret
            } else if let existingSecret = existingInstance?.password, !existingSecret.isEmpty {
                resolvedSecret = existingSecret
            } else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }

            let client = KomodoAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiKey: key, apiSecret: resolvedSecret, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiKey: key, apiSecret: resolvedSecret, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .komodo,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: resolvedSecret
            )

        case .maltrail:
            let identity = normalizedOptional(username) ?? existingInstance?.username
            let client = MaltrailAPIClient(instanceId: existingInstanceId ?? UUID())
            let secret = normalizedOptional(password)
            if (identity == nil) != (secret == nil) {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }

            let resolvedPassword: String?
            if let secret, !secret.isEmpty {
                resolvedPassword = secret
            } else {
                resolvedPassword = existingInstance?.password
            }

            await client.configure(
                url: url,
                fallbackUrl: fallbackUrl,
                username: identity,
                password: resolvedPassword,
                sessionCookie: existingInstance?.token,
                allowSelfSigned: allowSelfSigned
            )
            let cookie = try await client.authenticate(
                url: url,
                username: identity,
                password: resolvedPassword,
                fallbackUrl: fallbackUrl
            )
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .maltrail,
                label: label,
                url: url,
                token: cookie,
                username: identity,
                apiKey: existingInstance?.apiKey,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: resolvedPassword
            )

        case .technitium:
            let identity = normalizedOptional(username) ?? existingInstance?.username
            let secret = normalizedOptional(password)
            guard let identity, !identity.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && url != existingInstance?.url && secret == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }

            let resolvedPassword: String
            if let secret, !secret.isEmpty {
                resolvedPassword = secret
            } else if let existing = existingInstance?.password, !existing.isEmpty {
                resolvedPassword = existing
            } else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }

            let client = TechnitiumAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(
                url: url,
                token: existingInstance?.token ?? "",
                fallbackUrl: fallbackUrl,
                username: identity,
                password: resolvedPassword,
                allowSelfSigned: allowSelfSigned
            )
            let token = try await client.authenticate(
                url: url,
                username: identity,
                password: resolvedPassword,
                totp: mfaCode.trimmingCharacters(in: .whitespacesAndNewlines),
                fallbackUrl: fallbackUrl
            )

            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .technitium,
                label: label,
                url: url,
                token: token,
                username: identity,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: resolvedPassword
            )

        case .dockhand:
            let identity = normalizedOptional(username) ?? existingInstance?.username
            let secret = normalizedOptional(password)
            guard let identity, !identity.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && url != existingInstance?.url && secret == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }

            let resolvedPassword: String
            if let secret, !secret.isEmpty {
                resolvedPassword = secret
            } else if let existing = existingInstance?.password, !existing.isEmpty {
                resolvedPassword = existing
            } else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }

            let client = DockhandAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(
                url: url,
                sessionCookie: existingInstance?.token ?? "",
                fallbackUrl: fallbackUrl,
                username: identity,
                password: resolvedPassword,
                allowSelfSigned: allowSelfSigned
            )
            let sessionCookie = try await client.authenticate(
                url: url,
                username: identity,
                password: resolvedPassword,
                mfaCode: mfaCode.trimmingCharacters(in: .whitespacesAndNewlines),
                fallbackUrl: fallbackUrl
            )
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .dockhand,
                label: label,
                url: url,
                token: sessionCookie,
                username: identity,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: resolvedPassword
            )

        case .craftyController:
            let identity = normalizedOptional(username) ?? existingInstance?.username
            let secret = normalizedOptional(password)
            guard let identity, !identity.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && url != existingInstance?.url && secret == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }

            let resolvedPassword: String
            if let secret, !secret.isEmpty {
                resolvedPassword = secret
            } else if let existing = existingInstance?.password, !existing.isEmpty {
                resolvedPassword = existing
            } else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }

            let client = CraftyAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(
                url: url,
                username: identity,
                password: resolvedPassword,
                token: existingInstance?.token ?? "",
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )
            let token = try await client.authenticate(
                url: url,
                username: identity,
                password: resolvedPassword,
                fallbackUrl: fallbackUrl
            )
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .craftyController,
                label: label,
                url: url,
                token: token,
                username: identity,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: resolvedPassword
            )

        case .jellystat:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = JellystatAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiKey: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .jellystat,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .pihole:
            let secret = normalizedOptional(password) ?? existingInstance?.piHoleStoredSecret
            guard let secret, !secret.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = PiHoleAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(
                url: url,
                sid: existingInstance?.token ?? "",
                authMode: existingInstance?.piholeAuthMode,
                fallbackUrl: fallbackUrl,
                password: secret,
                allowSelfSigned: allowSelfSigned
            )
            let sid = try await client.authenticate(url: url, password: secret, fallbackUrl: fallbackUrl)
            let authMode: PiHoleAuthMode = sid == secret ? .legacy : .session
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .pihole,
                label: label,
                url: url,
                token: sid,
                username: existingInstance?.username,
                apiKey: existingInstance?.apiKey,
                piholePassword: secret,
                piholeAuthMode: authMode,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .adguardHome:
            let identity = normalizedOptional(username) ?? existingInstance?.username
            let secret = normalizedOptional(password) ?? existingInstance?.password
            guard let identity, !identity.isEmpty, let secret, !secret.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && url != existingInstance?.url && normalizedOptional(password) == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }
            let client = AdGuardHomeAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, username: identity, password: secret, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, username: identity, password: secret, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .adguardHome,
                label: label,
                url: url,
                token: "",
                username: identity,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: secret
            )

        case .beszel:
            let identity = normalizedOptional(username) ?? existingInstance?.username
            let secret = normalizedOptional(password)
            guard let identity, !identity.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && url != existingInstance?.url && secret == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }
            let token: String
            let storedPassword: String?
            if let secret, !secret.isEmpty {
                let client = BeszelAPIClient(instanceId: existingInstanceId ?? UUID())
                await client.configure(url: url, token: existingInstance?.token ?? "", fallbackUrl: fallbackUrl, email: identity, password: secret, allowSelfSigned: allowSelfSigned)
                token = try await client.authenticate(url: url, email: identity, password: secret, fallbackUrl: fallbackUrl)
                storedPassword = secret
            } else if let existingToken = existingInstance?.token, !existingToken.isEmpty {
                token = existingToken
                storedPassword = existingInstance?.password
            } else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .beszel,
                label: label,
                url: url,
                token: token,
                username: identity,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: storedPassword
            )

        case .gitea:
            let identity = normalizedOptional(username) ?? existingInstance?.username
            let secret = normalizedOptional(password)
            guard let identity, !identity.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && url != existingInstance?.url && secret == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }
            let token: String
            let resolvedUsername: String
            let storedPassword: String?
            if let secret, !secret.isEmpty {
                let client = GiteaAPIClient(instanceId: existingInstanceId ?? UUID())
                await client.configure(url: url, token: existingInstance?.token ?? "", fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
                let result = try await client.authenticate(url: url, username: identity, password: secret, fallbackUrl: fallbackUrl)
                token = result.token
                resolvedUsername = result.username
                storedPassword = secret
            } else if let existing = existingInstance, !existing.token.isEmpty {
                token = existing.token
                resolvedUsername = existing.username ?? identity
                storedPassword = existing.password
            } else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .gitea,
                label: label,
                url: url,
                token: token,
                username: resolvedUsername,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: storedPassword
            )

        case .nginxProxyManager:
            let identity = normalizedOptional(username) ?? existingInstance?.username
            let secret = normalizedOptional(password)
            guard let identity, !identity.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && url != existingInstance?.url && secret == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }
            let token: String
            let storedPassword: String?
            if let secret, !secret.isEmpty {
                let client = NginxProxyManagerAPIClient(instanceId: existingInstanceId ?? UUID())
                await client.configure(url: url, token: existingInstance?.token ?? "", fallbackUrl: fallbackUrl, email: identity, password: secret, allowSelfSigned: allowSelfSigned)
                token = try await client.authenticate(url: url, email: identity, password: secret, fallbackUrl: fallbackUrl)
                storedPassword = secret
            } else if let existingToken = existingInstance?.token, !existingToken.isEmpty {
                token = existingToken
                storedPassword = existingInstance?.password
            } else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .nginxProxyManager,
                label: label,
                url: url,
                token: token,
                username: identity,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: storedPassword
            )

        case .pangolin:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let orgId = normalizedOptional(username)
            let client = PangolinAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiKey: key, fallbackUrl: fallbackUrl, orgId: orgId, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl, orgId: orgId)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .pangolin,
                label: label,
                url: url,
                token: "",
                username: orgId,
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .patchmon:
            let tokenKey = normalizedOptional(username) ?? existingInstance?.username
            let tokenSecret = normalizedOptional(password)
            guard let tokenKey, !tokenKey.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && url != existingInstance?.url && tokenSecret == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }
            let resolvedSecret: String
            if let tokenSecret, !tokenSecret.isEmpty {
                let client = PatchmonAPIClient(instanceId: existingInstanceId ?? UUID())
                await client.configure(url: url, tokenKey: tokenKey, tokenSecret: tokenSecret, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
                try await client.authenticate(url: url, tokenKey: tokenKey, tokenSecret: tokenSecret, fallbackUrl: fallbackUrl)
                resolvedSecret = tokenSecret
            } else if let existingSecret = existingInstance?.password, !existingSecret.isEmpty {
                resolvedSecret = existingSecret
            } else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .patchmon,
                label: label,
                url: url,
                token: existingInstance?.token ?? "",
                username: tokenKey,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: resolvedSecret
            )

        case .plex:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = PlexAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, token: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, token: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .plex,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )
        
        case .qbittorrent:
            let identity = normalizedOptional(username) ?? existingInstance?.username
            let secret = normalizedOptional(password) ?? existingInstance?.password
            guard let identity, !identity.isEmpty, let secret, !secret.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            if existingInstance != nil && url != existingInstance?.url && normalizedOptional(password) == nil {
                throw APIError.custom(localizer.t.loginErrorPasswordRequired)
            }
            let client = QbittorrentAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(
                url: url,
                sid: existingInstance?.token ?? "",
                fallbackUrl: fallbackUrl,
                username: identity,
                password: secret,
                allowSelfSigned: allowSelfSigned
            )
            let sid = try await client.authenticate(url: url, username: identity, password: secret, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .qbittorrent,
                label: label,
                url: url,
                token: sid,
                username: identity,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned,
                password: secret
            )
            
        case .radarr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = RadarrAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiKey: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .radarr,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )
            
        case .sonarr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = SonarrAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiKey: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .sonarr,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )
            
        case .lidarr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = LidarrAPIClient(instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, apiKey: key, fallbackUrl: fallbackUrl, allowSelfSigned: allowSelfSigned)
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .lidarr,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )
            
        case .jellyseerr, .prowlarr, .bazarr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let genericType = serviceType
            let client = GenericAPIClient(serviceType: genericType, instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, fallbackUrl: fallbackUrl, apiKey: key, allowSelfSigned: allowSelfSigned)
            guard await client.ping() else {
                throw APIError.custom(localizer.t.loginErrorFailed)
            }
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: genericType,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .gluetun, .flaresolverr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            let genericType = serviceType
            let client = GenericAPIClient(serviceType: genericType, instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, fallbackUrl: fallbackUrl, apiKey: key, allowSelfSigned: allowSelfSigned)
            guard await client.ping() else {
                throw APIError.custom(localizer.t.loginErrorFailed)
            }
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: genericType,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
            )

        case .proxmox:
            let authMode: ProxmoxAuthMode = proxmoxAuthMode == 1 ? .apiToken : .credentials

            if authMode == .apiToken {
                let token = resolvedProxmoxApiToken()
                    ?? (existingInstance?.proxmoxAuthMode == .apiToken ? existingInstance?.apiKey : nil)
                guard let token, !token.isEmpty else {
                    throw APIError.custom(localizer.t.proxmoxInvalidApiToken)
                }
                let client = ProxmoxAPIClient(instanceId: existingInstanceId ?? UUID())
                await client.configure(
                    url: url,
                    fallbackUrl: fallbackUrl,
                    apiTokenString: token,
                    allowSelfSigned: allowSelfSigned
                )
                try await client.authenticateWithApiToken(url: url, apiToken: token)
                return ServiceInstance(
                    id: existingInstanceId ?? UUID(),
                    type: .proxmox,
                    label: label,
                    url: url,
                    token: "",
                    username: proxmoxApiTokenEntryMode == 0 ? normalizedOptional(proxmoxApiUser) : nil,
                    apiKey: token,
                    proxmoxAuthMode: .apiToken,
                    proxmoxRealm: proxmoxApiTokenEntryMode == 0 ? normalizedOptional(proxmoxApiRealm) : nil,
                    fallbackUrl: fallbackUrl,
                allowSelfSigned: allowSelfSigned
                )
            } else {
                let identity = normalizedOptional(username) ?? existingInstance?.username
                let secret = normalizedOptional(password)
                guard let identity, !identity.isEmpty else {
                    throw APIError.custom(localizer.t.loginErrorCredentials)
                }
                if existingInstance != nil && url != existingInstance?.url && secret == nil {
                    throw APIError.custom(localizer.t.loginErrorPasswordRequired)
                }
                let resolvedPassword: String
                if let secret, !secret.isEmpty {
                    resolvedPassword = secret
                } else if let existing = existingInstance?.password, !existing.isEmpty {
                    resolvedPassword = existing
                } else {
                    throw APIError.custom(localizer.t.loginErrorCredentials)
                }
                let client = ProxmoxAPIClient(instanceId: existingInstanceId ?? UUID())
                let resolvedRealm = proxmoxRealm.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "pam" : proxmoxRealm
                await client.configure(
                    url: url,
                    fallbackUrl: fallbackUrl,
                    username: identity,
                    password: resolvedPassword,
                    otp: mfaCode.trimmingCharacters(in: .whitespacesAndNewlines),
                    realm: resolvedRealm,
                    allowSelfSigned: allowSelfSigned
                )
                let result = try await client.authenticate(
                    url: url,
                    username: identity,
                    password: resolvedPassword,
                    otp: mfaCode.trimmingCharacters(in: .whitespacesAndNewlines),
                    realm: resolvedRealm
                )
                return ServiceInstance(
                    id: existingInstanceId ?? UUID(),
                    type: .proxmox,
                    label: label,
                    url: url,
                    token: result.ticket,
                    username: identity,
                    apiKey: result.csrf,
                    proxmoxAuthMode: .credentials,
                    proxmoxRealm: resolvedRealm,
                    proxmoxOTP: normalizedOptional(mfaCode),
                    fallbackUrl: fallbackUrl,
                    allowSelfSigned: allowSelfSigned,
                    password: resolvedPassword
                )
            }
        }
    }

    private func normalizedURL(_ raw: String) -> String {
        var clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return "" }
        let trailing = CharacterSet(charactersIn: ")]},;")
        while let last = clean.unicodeScalars.last, trailing.contains(last) {
            clean = String(clean.dropLast())
        }
        if !clean.hasPrefix("http://") && !clean.hasPrefix("https://") {
            clean = "https://" + clean
        }
        return clean.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private func normalizedOptionalURL(_ raw: String) -> String? {
        let clean = normalizedURL(raw)
        return clean.isEmpty ? nil : clean
    }

    private func normalizedOptional(_ raw: String) -> String? {
        let clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return clean.isEmpty ? nil : clean
    }

    private func showError(_ message: String) {
        errorMessage = message
        HapticManager.error()
        let shake = Animation.easeInOut(duration: 0.06)
        withAnimation(shake) { shakeOffset = 8 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            withAnimation(shake) { shakeOffset = -8 }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            withAnimation(shake) { shakeOffset = 8 }
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            withAnimation(.spring(response: 0.2, dampingFraction: 0.75)) { shakeOffset = 0 }
        }
    }

    private func resolveErrorMessage(_ error: Error) -> String {
        let message = error.localizedDescription.lowercased()
        if message.contains("webauthn") || message.contains("u2f") || message.contains("tfa-challenge") {
            return localizer.t.proxmoxCredentialsHint
        }
        if message.contains("api token") && message.contains("format") {
            return localizer.t.proxmoxInvalidApiToken
        }
        if let mapped = APIError.localizedNetworkError(error) {
            return mapped
        }
        if let apiError = error as? APIError {
            return apiError.localizedDescription
        }
        return error.localizedDescription
    }

    private func resolvedProxmoxApiToken() -> String? {
        if proxmoxApiTokenEntryMode == 0 {
            return ProxmoxAPITokenParts(
                user: proxmoxApiUser,
                realm: proxmoxApiRealm,
                tokenID: proxmoxApiTokenId,
                secret: proxmoxApiTokenSecret
            )?.rawValue
        }
        return normalizedOptional(apiKey)
    }

    private var proxmoxRealmSuggestions: [String] {
        ["pam", "pve", "ldap", "ad", "openid"]
    }

    private func proxmoxInfoCard(icon: String, title: String, body: String, tint: Color) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.subheadline.bold())
                .foregroundStyle(tint)
                .frame(width: 28, height: 28)
                .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
                Text(body)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer()
        }
        .padding(14)
        .glassCard(tint: tint.opacity(0.06))
    }

    private func proxmoxInlineNotice(icon: String, message: String, tint: Color) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon)
                .font(.caption.bold())
                .foregroundStyle(tint)
                .frame(width: 18, height: 18)

            Text(message)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 2)
    }

    private func proxmoxRealmInput(_ realm: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            InputField(
                icon: "building.2.fill",
                placeholder: localizer.t.proxmoxRealm,
                text: realm
            )

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(proxmoxRealmSuggestions, id: \.self) { suggestion in
                        Button {
                            HapticManager.light()
                            realm.wrappedValue = suggestion
                        } label: {
                            Text(suggestion.uppercased())
                                .font(.caption.bold())
                                .foregroundStyle(realm.wrappedValue.caseInsensitiveCompare(suggestion) == .orderedSame ? .white : serviceColor)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(
                                    realm.wrappedValue.caseInsensitiveCompare(suggestion) == .orderedSame
                                    ? AnyShapeStyle(serviceColor)
                                    : AnyShapeStyle(serviceColor.opacity(0.08)),
                                    in: Capsule()
                                )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 2)
            }

            Text(localizer.t.proxmoxCustomRealmHint)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
    }

    // MARK: - Proxmox Auth Section

    @ViewBuilder
    private var proxmoxAuthSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxAuthMode.sentenceCased())
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            Picker(localizer.t.proxmoxAuthMode, selection: $proxmoxAuthMode) {
                Text(localizer.t.proxmoxCredentialsMode).tag(0)
                Text(localizer.t.proxmoxApiTokenMode).tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.vertical, 4)

            if proxmoxAuthMode == 1 {
                proxmoxInfoCard(
                    icon: "key.fill",
                    title: localizer.t.proxmoxApiTokenMode,
                    body: localizer.t.proxmoxApiTokenRecommendedHint,
                    tint: serviceColor
                )

                Text(localizer.t.proxmoxApiTokenMode.sentenceCased())
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .padding(.horizontal, 2)

                Picker(localizer.t.proxmoxApiTokenMode, selection: $proxmoxApiTokenEntryMode) {
                    Text(localizer.t.proxmoxApiTokenStructuredMode).tag(0)
                    Text(localizer.t.proxmoxApiTokenPasteMode).tag(1)
                }
                .pickerStyle(.segmented)

                if proxmoxApiTokenEntryMode == 0 {
                    InputField(
                        icon: "person.fill",
                        placeholder: localizer.t.proxmoxApiUser,
                        text: $proxmoxApiUser
                    )

                    proxmoxRealmInput($proxmoxApiRealm)

                    InputField(
                        icon: "number",
                        placeholder: localizer.t.proxmoxApiTokenId,
                        text: $proxmoxApiTokenId
                    )

                    InputField(
                        icon: "key.fill",
                        placeholder: localizer.t.proxmoxApiTokenSecret,
                        text: $proxmoxApiTokenSecret,
                        isSecure: !showPassword,
                        showToggle: true,
                        toggleAction: { showPassword.toggle() },
                        showPassword: showPassword,
                        onSubmit: handleSave
                    )
                } else {
                    InputField(
                        icon: "key.fill",
                        placeholder: localizer.t.proxmoxApiTokenPlaceholder,
                        text: $apiKey,
                        isSecure: !showPassword,
                        showToggle: true,
                        toggleAction: { showPassword.toggle() },
                        showPassword: showPassword,
                        onSubmit: handleSave
                    )
                }

                proxmoxInlineNotice(
                    icon: "terminal.fill",
                    message: localizer.t.proxmoxConsoleCredentialsOnly,
                    tint: .orange
                )

                Text(localizer.t.proxmoxApiTokenHint)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                    .padding(.horizontal, 2)
            } else {
                proxmoxInfoCard(
                    icon: "lock.shield.fill",
                    title: localizer.t.proxmoxCredentialsMode,
                    body: localizer.t.proxmoxCredentialsHint,
                    tint: serviceColor
                )

                InputField(
                    icon: "person.fill",
                    placeholder: localizer.t.loginUsername,
                    text: $username
                )

                proxmoxRealmInput($proxmoxRealm)

                InputField(
                    icon: "lock.fill",
                    placeholder: isEditing ? localizer.t.loginPasswordIfChanging : localizer.t.loginPassword,
                    text: $password,
                    isSecure: !showPassword,
                    showToggle: true,
                    toggleAction: { showPassword.toggle() },
                    showPassword: showPassword
                )

                InputField(
                    icon: "lock.rotation",
                    placeholder: localizer.t.loginOptional2FA,
                    text: $mfaCode,
                    keyboardType: .asciiCapable,
                    onSubmit: handleSave
                )

                Text(localizer.t.proxmoxOtpRecoveryHint)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
                    .padding(.horizontal, 2)
            }
        }
    }
}

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
    @Environment(Localizer.self) private var localizer

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
                .accessibilityLabel(showPassword ? localizer.t.loginHidePassword : localizer.t.loginShowPassword)
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
