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
            || serviceType == .dockhand
    }

    private var usesApiKeyAuth: Bool {
        serviceType == .portainer
            || serviceType == .healthchecks
            || serviceType == .linuxUpdate
            || serviceType == .pangolin
            || serviceType == .jellystat
            || serviceType == .plex
            || serviceType == .radarr
            || serviceType == .sonarr
            || serviceType == .lidarr
            || serviceType == .jellyseerr
            || serviceType == .prowlarr
            || serviceType == .bazarr
            || serviceType == .jellyfin
            || serviceType == .immich
            || serviceType == .grafana
            || serviceType == .sabnzbd
            || serviceType == .proxmox
            || serviceType == .proxmoxBackupServer
    }

    private var supportsCredentiallessAuth: Bool {
        serviceType == .gluetun || serviceType == .flaresolverr || serviceType == .tdarr
    }

    private var supportsOptionalApiKey: Bool {
        serviceType == .gluetun || serviceType == .flaresolverr || serviceType == .tdarr
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

            Text(isEditing ? localizer.t.loginEditSubtitle : localizer.t.loginSubtitle)
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

            if usesApiKeyAuth {
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
                        keyboardType: .numberPad,
                        onSubmit: handleSave
                    )
                }
            }

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
            .disabled(isLoading)
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
        case .gitea:             return localizer.t.loginHintGitea2FA
        case .nginxProxyManager: return localizer.t.loginHintNpm
        case .pangolin:          return PangolinStrings.forLanguage(localizer.language).loginHint
        case .healthchecks:      return localizer.t.loginHintHealthchecks
        case .patchmon:          return localizer.t.loginHintPatchmon
        case .jellystat:         return localizer.t.loginHintJellystat
        case .plex:              return localizer.t.loginHintPlex
        case .gluetun:
                                 return localizer.t.loginHintGluetun
        case .flaresolverr:
                                 return localizer.t.loginHintFlaresolverr
        case .jellyfin:
                                 return "Enter your Jellyfin server URL and API key. Find the API key in Dashboard > API Keys."
        case .immich:
                                 return "Enter your Immich server URL and API key. Generate an API key in Account Settings > API Keys."
        case .grafana:
                                 return "Enter your Grafana URL and a Service Account token. Create one in Administration > Service accounts."
        case .sabnzbd:
                                 return "Enter your SABnzbd URL and API key. Find the API key in Config > General > Security."
        case .proxmox:
                                 return "Enter your Proxmox VE URL and API token. Format: user@realm!tokenid=token-value"
        case .proxmoxBackupServer:
                                 return "Enter your PBS URL and API token. Format: user@realm!tokenid=token-value"
        case .tdarr:
                                 return "Enter your Tdarr server URL. API key is optional unless server authentication is enabled."
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
        username = existing.username ?? ""
        apiKey = existing.apiKey ?? ""
        password = existing.piholePassword ?? existing.password ?? ""
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
                    fallbackUrl: fallbackUrl,
                    allowSelfSigned: existing.allowSelfSigned,
                    password: existing.password
                )
            }
        }

        switch serviceType {
        case .portainer:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = PortainerAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticateWithApiKey(url: url, apiKey: key)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .portainer,
                label: label,
                url: url,
                token: existingInstance?.token ?? "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .healthchecks:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = HealthchecksAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .healthchecks,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .linuxUpdate:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = LinuxUpdateAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiToken: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .linuxUpdate,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl
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
                password: resolvedPassword
            )

        case .jellystat:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = JellystatAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .jellystat,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .pihole:
            let secret = normalizedOptional(password) ?? existingInstance?.piHoleStoredSecret
            guard let secret, !secret.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = PiHoleAPIClient(instanceId: existingInstanceId ?? UUID())
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
                fallbackUrl: fallbackUrl
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
            try await client.authenticate(url: url, username: identity, password: secret)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .adguardHome,
                label: label,
                url: url,
                token: "",
                username: identity,
                fallbackUrl: fallbackUrl,
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
                token = try await client.authenticate(url: url, email: identity, password: secret)
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
                let result = try await client.authenticate(url: url, username: identity, password: secret)
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
                token = try await client.authenticate(url: url, email: identity, password: secret)
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
                password: storedPassword
            )

        case .pangolin:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = PangolinAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .pangolin,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
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
                password: resolvedSecret
            )

        case .plex:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = PlexAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, token: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .plex,
                label: label,
                url: url,
                token: "",
                username: existingInstance?.username,
                apiKey: key,
                fallbackUrl: fallbackUrl
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
            let sid = try await client.authenticate(url: url, username: identity, password: secret, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .qbittorrent,
                label: label,
                url: url,
                token: sid,
                username: identity,
                fallbackUrl: fallbackUrl,
                password: secret
            )
            
        case .radarr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = RadarrAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .radarr,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )
            
        case .sonarr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = SonarrAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .sonarr,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )
            
        case .lidarr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = LidarrAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .lidarr,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )
            
        case .jellyseerr, .prowlarr, .bazarr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let genericType = serviceType
            let client = GenericAPIClient(serviceType: genericType, instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, fallbackUrl: fallbackUrl, apiKey: key)
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
                fallbackUrl: fallbackUrl
            )

        case .jellyfin:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = JellyfinAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .jellyfin,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .immich:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = ImmichAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .immich,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .grafana:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = GrafanaAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .grafana,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .sabnzbd:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = SABnzbdAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .sabnzbd,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .proxmox:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = ProxmoxAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .proxmox,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .proxmoxBackupServer:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            guard let key, !key.isEmpty else {
                throw APIError.custom(localizer.t.loginErrorCredentials)
            }
            let client = PBSAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, apiKey: key, fallbackUrl: fallbackUrl)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .proxmoxBackupServer,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .tdarr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            let client = TdarrAPIClient(instanceId: existingInstanceId ?? UUID())
            try await client.authenticate(url: url, fallbackUrl: fallbackUrl, apiKey: key)
            return ServiceInstance(
                id: existingInstanceId ?? UUID(),
                type: .tdarr,
                label: label,
                url: url,
                token: "",
                apiKey: key,
                fallbackUrl: fallbackUrl
            )

        case .gluetun, .flaresolverr:
            let key = normalizedOptional(apiKey) ?? existingInstance?.apiKey
            let genericType = serviceType
            let client = GenericAPIClient(serviceType: genericType, instanceId: existingInstanceId ?? UUID())
            await client.configure(url: url, fallbackUrl: fallbackUrl, apiKey: key)
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
                fallbackUrl: fallbackUrl
            )
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
        if let mapped = APIError.localizedNetworkError(error) {
            return mapped
        }
        if let apiError = error as? APIError {
            return apiError.localizedDescription
        }
        return error.localizedDescription
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
