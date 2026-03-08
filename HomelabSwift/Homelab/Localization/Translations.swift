import Foundation

// MARK: - Translations struct (maps 1:1 to constants/translations.ts)

struct Translations {
    // Common
    let loading: String
    let error: String
    let cancel: String
    let save: String
    let confirm: String
    let delete: String
    let back: String
    let close: String
    let copy: String
    let yes: String
    let no: String
    let noData: String
    let retry: String

    // Tabs
    let tabHome: String
    let tabBookmarks: String
    let tabSettings: String

    // Launcher
    let launcherTitle: String
    let launcherSubtitle: String
    let launcherConnected: String
    let launcherNotConfigured: String
    let launcherTapToConnect: String
    let launcherServices: String

    // Tailscale
    let tailscaleConnect: String
    let tailscaleDesc: String

    // Status
    let statusUnreachable: String
    let statusVerifying: String
    let actionReconnect: String

    // Greetings
    let greetingMorning: String
    let greetingAfternoon: String
    let greetingEvening: String
    let summaryTitle: String
    let summaryQueryTotal: String
    let summarySystemsOnline: String

    // Services
    let servicePortainer: String
    let servicePihole: String
    let serviceBeszel: String
    let serviceGitea: String
    let servicePortainerDesc: String
    let servicePiholeDesc: String
    let serviceBeszelDesc: String
    let serviceGiteaDesc: String

    // Login
    let loginTitle: String
    let loginSubtitle: String
    let loginUrl: String
    let loginUrlPlaceholder: String
    let loginUsername: String
    let loginEmail: String
    let loginPassword: String
    let loginConnect: String
    let loginConnecting: String
    let loginErrorUrl: String
    let loginErrorCredentials: String
    let loginErrorFailed: String
    let loginHintPihole: String
    let loginHintGitea2FA: String
    let loginHintPortainer: String
    let loginApiKey: String
    let done: String

    // Portainer
    let portainerDashboard: String
    let portainerEndpoints: String
    let portainerActive: String
    let portainerContainers: String
    let portainerResources: String
    let portainerTotal: String
    let portainerRunning: String
    let portainerStopped: String
    let portainerImages: String
    let portainerVolumes: String
    let portainerCpus: String
    let portainerMemory: String
    let portainerViewAll: String
    let portainerSelectEndpoint: String
    let portainerServerInfo: String
    let portainerOnline: String
    let portainerOffline: String
    let portainerStacks: String
    let portainerHealthy: String
    let portainerUnhealthy: String

    // Containers
    let containersSearch: String
    let containersAll: String
    let containersRunning: String
    let containersStopped: String
    let containersEmpty: String
    let containersNoEndpoint: String

    // Actions
    let actionStart: String
    let actionStop: String
    let actionRestart: String
    let actionPause: String
    let actionResume: String
    let actionRemove: String
    let actionKill: String
    let actionConfirm: String
    let actionConfirmMessage: String
    let actionRemoveConfirm: String
    let actionRemoveMessage: String

    // Container detail
    let detailInfo: String
    let detailStats: String
    let detailLogs: String
    let detailEnv: String
    let detailCompose: String
    let detailContainer: String
    let detailCreated: String
    let detailHostname: String
    let detailWorkDir: String
    let detailCommand: String
    let detailNetwork: String
    let detailMode: String
    let detailMounts: String
    let detailRestartPolicy: String
    let detailPolicy: String
    let detailMaxRetries: String
    let detailUptime: String
    let detailNotRunning: String
    let detailNoLogs: String
    let detailEnvVars: String
    let detailCpu: String
    let detailMemory: String
    let detailNetworkIO: String
    let detailUsed: String
    let detailContainerLogs: String
    let detailNotFound: String
    let detailComposeFile: String
    let detailComposeNotAvailable: String
    let detailComposeSave: String
    let detailComposeSaved: String
    let detailComposeSaveError: String
    let detailComposeLoading: String

    // Pi-hole
    let piholeBlocking: String
    let piholeEnabled: String
    let piholeDisabled: String
    let piholeTotalQueries: String
    let piholeBlockedQueries: String
    let piholePercentBlocked: String
    let piholeTopBlocked: String
    let piholeTopDomains: String
    let piholeClients: String
    let piholeDomains: String
    let piholeGravity: String
    let piholeToggle: String
    let piholeQueries: String
    let piholeCached: String
    let piholeForwarded: String
    let piholeUniqueDomains: String
    let piholeBlockingWarningTitle: String
    let piholeBlockingWarningEnable: String
    let piholeBlockingWarningDisable: String
    let piholeBlockingDesc: String
    let piholeDisableDesc: String
    let piholeGravityUpdated: String
    let piholeOverview: String
    let piholeQueryActivity: String
    let piholeQueriesOverTime: String
    let piholeDomainManagement: String
    let piholeAllowed: String
    let piholeBlocked: String
    let piholeAddDomain: String
    let piholeNoDomains: String
    let piholeAddDomainDesc: String

    let piholeDisablePermanently: String
    let piholeDisable1m: String
    let piholeDisable5m: String
    let piholeDisable1h: String

    // Beszel
    let beszelSystems: String
    let beszelUp: String
    let beszelDown: String
    let beszelCpu: String
    let beszelMemory: String
    let beszelDisk: String
    let beszelNetwork: String
    let beszelUptime: String
    let beszelNoSystems: String
    let beszelSystemDetail: String
    let beszelOs: String
    let beszelKernel: String
    let beszelHostname: String
    let beszelCpuModel: String
    let beszelTotalMemory: String
    let beszelUsedMemory: String
    let beszelTotalDisk: String
    let beszelUsedDisk: String
    let beszelNetworkSent: String
    let beszelNetworkReceived: String
    let beszelRefreshRate: String
    let beszelCores: String
    let beszelSystemInfo: String
    let beszelResources: String
    let beszelNetworkTraffic: String
    let beszelContainers: String
    let beszelNoContainers: String

    // Gitea
    let giteaRepos: String
    let giteaOrgs: String
    let giteaStars: String
    let giteaForks: String
    let giteaIssues: String
    let giteaPrivate: String
    let giteaPublic: String
    let giteaNoRepos: String
    let giteaLanguage: String
    let gitea2FAHint: String
    let gitea2FAHintMessage: String
    let giteaFiles: String
    let giteaCommits: String
    let giteaBranches: String
    let giteaNoFiles: String
    let giteaNoCommits: String
    let giteaNoIssues: String
    let giteaOpenIssues: String
    let giteaClosedIssues: String
    let giteaDefaultBranch: String
    let giteaSize: String
    let giteaLastUpdate: String
    let giteaReadme: String
    let giteaOk: String
    let giteaContributions: String
    let giteaFileContent: String
    let giteaLessActive: String
    let giteaMoreActive: String
    let giteaMyForks: String
    let giteaPreview: String
    let giteaCode: String
    let giteaSortRecent: String
    let giteaSortAlpha: String

    // Settings
    let settingsPreferences: String
    let settingsLanguage: String
    let settingsTheme: String
    let settingsThemeLight: String
    let settingsThemeDark: String
    let settingsItalian: String
    let settingsEnglish: String
    let settingsFrench: String
    let settingsSpanish: String
    let settingsGerman: String
    let settingsServices: String
    let settingsDisconnect: String
    let settingsDisconnectConfirm: String
    let settingsDisconnectMessage: String
    let settingsAbout: String
    let settingsVersion: String
    let settingsConnected: String
    let settingsNotConnected: String
    let settingsFallbackUrl: String
    let settingsSupportTitle: String
    let settingsSupportDesc: String
    let settingsCopied: String
    let settingsThemeAuto: String
    let settingsContacts: String
    let settingsHideService: String
    let settingsShowService: String

    // Security
    let securityTitle: String
    let securitySetupPin: String
    let securitySetupPinDesc: String
    let securityConfirmPin: String
    let securityConfirmPinDesc: String
    let securityEnterPin: String
    let securityEnterPinDesc: String
    let securityWrongPin: String
    let securityEnableBiometric: String
    let securityBiometricDesc: String
    let securityChangePin: String
    let securityDisable: String
    let securityDisableConfirm: String
    let securityDisableMessage: String
    let securityPinMismatch: String
    let securityBiometricReason: String
    let securityNewPin: String
    let securityNewPinDesc: String
    let securityCurrentPin: String
    let securityCurrentPinDesc: String
    let securityNotConfigured: String
    let securitySkip: String

    // Bookmarks
    let bookmarkTitle: String
    let bookmarkDesc: String
    let bookmarkUrl: String
    let bookmarkCategory: String
    let bookmarkCategoryNew: String
    let bookmarkIcon: String
    let bookmarkAdd: String
    let bookmarkEdit: String
    let categoryName: String
    let categoryAdd: String
    let categoryEdit: String
    let categoryDelete: String
    let categoryDeleteConfirm: String
    let categoryEmpty: String
    let categoryUncategorized: String

    // Tailscale v2
    let tailscaleOpen: String
    let tailscaleOpenDesc: String
    let tailscaleSecure: String
    let tailscaleConnected: String
    let tailscaleNotConnected: String

    // Bookmarks v2
    let categoryColor: String
    let bookmarkFavicon: String
    let bookmarkSymbol: String
    let bookmarkSelfhst: String
    let bookmarkAutoFavicon: String
    let bookmarkEnterUrl: String
    let bookmarkTags: String
    let bookmarkSearchPrompt: String
    let bookmarkEnterSelfhst: String
    let bookmarkPreviewSelfhst: String

    // Onboarding v2
    let onboardingWelcome: String
    let onboardingWelcomeDesc: String
    let onboardingAskPin: String
    let onboardingAskPinYes: String
    let onboardingAskPinNo: String
}

// MARK: - Factory

extension Translations {
    static func forLanguage(_ language: Language) -> Translations {
        switch language {
        case .it: return .italian
        case .en: return .english
        case .fr: return .french
        case .es: return .spanish
        case .de: return .german
        }
    }
}

// MARK: - Localizer (accessed via environment)

@Observable
@MainActor
final class Localizer {
    var language: Language = .en

    var t: Translations { Translations.forLanguage(language) }

    func greetingKey() -> String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12:  return t.greetingMorning
        case 12..<18: return t.greetingAfternoon
        default:      return t.greetingEvening
        }
    }
}
