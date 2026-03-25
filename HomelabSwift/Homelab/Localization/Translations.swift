import Foundation

// MARK: - Translations struct (maps 1:1 to constants/translations.ts)

struct Translations {
    // Common
    let loading: String
    let error: String
    let refresh: String
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
    let notAvailable: String
    let reconnect: String
    let offlineUnreachable: String

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
    let homeReorderServices: String

    // Tailscale
    let tailscaleConnect: String
    let tailscaleDesc: String

    // Status
    let statusUnreachable: String
    let statusVerifying: String
    let statusOnline: String
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
    let serviceAdguard: String
    let serviceBeszel: String
    let serviceHealthchecks: String
    let serviceGitea: String
    let serviceNpm: String
    let servicePatchmon: String
    let serviceJellystat: String
    let servicePortainerDesc: String
    let servicePiholeDesc: String
    let serviceAdguardDesc: String
    let serviceBeszelDesc: String
    let serviceHealthchecksDesc: String
    let serviceGiteaDesc: String
    let serviceNpmDesc: String
    let servicePatchmonDesc: String
    let serviceJellystatDesc: String
    let servicePlex: String
    let servicePlexDesc: String

    // Login
    let loginTitle: String
    let loginSubtitle: String
    let loginUrl: String
    let loginUrlPlaceholder: String
    let loginUsername: String
    let loginEmail: String
    let loginPassword: String
    let loginTokenKey: String
    let loginTokenSecret: String
    let loginConnect: String
    let loginConnecting: String
    let loginErrorUrl: String
    let loginErrorCredentials: String
    let loginErrorFailed: String
    let loginHintPihole: String
    let loginHintAdguard: String
    let loginHintGitea2FA: String
    let loginHintPortainer: String
    let loginHintHealthchecks: String
    let loginHintPatchmon: String
    let loginHintJellystat: String
    let loginHintPlex: String
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
    let portainerHealthStatus: String
    let portainerHost: String

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
    let actionKill: String
    let actionRemove: String
    let actionClear: String
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
    let detailId: String
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
    let detailRx: String
    let detailTx: String
    let detailUsed: String
    let detailContainerLogs: String
    let detailNotFound: String
    let detailComposeFile: String
    let detailComposeNotAvailable: String
    let detailComposeSave: String
    let detailComposeSaved: String
    let detailComposeSaveError: String
    let detailComposeLoading: String

    // Healthchecks
    let healthchecksChecks: String
    let healthchecksSearch: String
    let healthchecksAll: String
    let healthchecksUp: String
    let healthchecksGrace: String
    let healthchecksDown: String
    let healthchecksPaused: String
    let healthchecksNew: String
    let healthchecksNoChecks: String
    let healthchecksLastPing: String
    let healthchecksNextPing: String
    let healthchecksSchedule: String
    let healthchecksTimeout: String
    let healthchecksGracePeriod: String
    let healthchecksTimezone: String
    let healthchecksMethods: String
    let healthchecksManualResume: String
    let healthchecksMethodsPostOnly: String
    let healthchecksMethodsAll: String
    let healthchecksIntegrations: String
    let healthchecksBadges: String
    let healthchecksCopyPingUrl: String
    let healthchecksChannels: String
    let healthchecksPings: String
    let healthchecksFlips: String
    let healthchecksEditCheck: String
    let healthchecksCreateCheck: String
    let healthchecksDeleteCheck: String
    let healthchecksDeleteConfirmTitle: String
    let healthchecksDeleteConfirmMessage: String
    let healthchecksPingBody: String
    let healthchecksBadgeAll: String
    let healthchecksBasics: String
    let healthchecksAdvanced: String
    let healthchecksFieldName: String
    let healthchecksFieldSlug: String
    let healthchecksFieldTags: String
    let healthchecksFieldDesc: String
    let healthchecksFieldType: String
    let healthchecksTypeSimple: String
    let healthchecksTypeCron: String
    let healthchecksFieldTimeout: String
    let healthchecksFieldSchedule: String
    let healthchecksFieldTimezone: String
    let healthchecksFieldGrace: String
    let healthchecksFieldChannels: String
    let healthchecksSlugHint: String
    let healthchecksTagsHint: String
    let healthchecksTimeoutHint: String
    let healthchecksScheduleHint: String
    let healthchecksTimezoneHint: String
    let healthchecksGraceHint: String
    let healthchecksChannelsHint: String
    let healthchecksNameRequired: String
    let healthchecksScheduleRequired: String
    let healthchecksTimeoutRequired: String
    let healthchecksReadOnly: String
    let healthchecksReadOnlyTitle: String
    let healthchecksReadOnlyMessage: String
    let healthchecksApiKeyBannerTitle: String
    let healthchecksApiKeyBannerBody: String

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
    let piholeListType: String
    let piholeAllowed: String
    let piholeBlocked: String
    let piholeAddDomain: String
    let piholeDomainPlaceholder: String
    let piholeNoDomains: String
    let piholeAddDomainDesc: String

    let piholeDisablePermanently: String
    let piholeDisable1m: String
    let piholeDisable5m: String
    let piholeDisable1h: String
    let piholeDisableCustom: String
    let piholeCustomDisableTitle: String
    let piholeCustomDisableDesc: String
    let piholeCustomDisableMinutes: String
    let piholeQueryLog: String
    let piholeFilterSearch: String
    let piholeFilterAll: String
    let piholeFilterBlocked: String
    let piholeFilterAllowed: String
    let piholeFilterClient: String
    let piholeNoQueryResults: String

    // AdGuard Home
    let adguardProtection: String
    let adguardEnabled: String
    let adguardDisabled: String
    let adguardProtectionDesc: String
    let adguardDisableDesc: String
    let adguardDisablePermanently: String
    let adguardDisable1m: String
    let adguardDisable5m: String
    let adguardDisable1h: String
    let adguardDisableCustom: String
    let adguardCustomDisableTitle: String
    let adguardCustomDisableDesc: String
    let adguardCustomDisableMinutes: String
    let adguardOverview: String
    let adguardTotalQueries: String
    let adguardBlockedQueries: String
    let adguardPercentBlocked: String
    let adguardAvgProcessing: String
    let adguardTopQueried: String
    let adguardTopBlocked: String
    let adguardTopClients: String
    let adguardQueryActivity: String
    let adguardQuickActions: String
    let adguardSafety: String
    let adguardSafeBrowsing: String
    let adguardSafeSearch: String
    let adguardParental: String
    let adguardServerInfo: String
    let adguardVersion: String
    let adguardDnsAddress: String
    let adguardDnsPort: String
    let adguardHttpPort: String
    let adguardFilters: String
    let adguardBlocklists: String
    let adguardAllowlists: String
    let adguardFiltersEnabled: String
    let adguardRules: String
    let adguardQueryLog: String
    let adguardFilterSearch: String
    let adguardFilterAll: String
    let adguardFilterBlocked: String
    let adguardFilterAllowed: String
    let adguardFilterClient: String
    let adguardNoQueryResults: String
    let adguardAllow: String
    let adguardQueriesAxis: String
    let adguardUserRules: String
    let adguardAddRule: String
    let adguardAddRuleDesc: String
    let adguardRulePlaceholder: String
    let adguardNoRules: String
    let adguardBlockedServices: String
    let adguardNoBlockedServices: String
    let adguardBlockedServicesOther: String
    let adguardRewrites: String
    let adguardAddRewrite: String
    let adguardAddRewriteDesc: String
    let adguardRewriteDomain: String
    let adguardRewriteAnswer: String
    let adguardNoRewrites: String
    let adguardAddFilterList: String
    let adguardListType: String
    let adguardPresetLists: String
    let adguardCustomList: String
    let adguardAllowlistHint: String
    let adguardCustomListHint: String
    let adguardListName: String
    let adguardListUrl: String

    // Beszel
    let beszelSystems: String
    let beszelUp: String
    let beszelDown: String
    let beszelCpu: String
    let beszelMemory: String
    let beszelRam: String
    let beszelDisk: String
    let beszelNetwork: String
    let beszelUptime: String
    let beszelNoSystems: String
    let beszelSystemDetail: String
    let beszelOs: String
    let beszelKernel: String
    let beszelArch: String
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
    let beszelCpuBreakdown: String
    let beszelCpuUser: String
    let beszelCpuSystem: String
    let beszelCpuNice: String
    let beszelCpuWait: String
    let beszelCpuIdle: String
    let beszelExtraMetrics: String
    let beszelGpu: String
    let beszelGpuUsage: String
    let beszelGpuPower: String
    let beszelGpuVram: String
    let beszelTemperature: String
    let beszelLoadAverage: String
    let beszelDiskIO: String
    let beszelBattery: String
    let beszelSwap: String
    let beszelSmartDevices: String
    let beszelHealthNone: String
    let beszelHealthStarting: String
    let beszelHealthHealthy: String
    let beszelHealthUnhealthy: String
    let beszelPerCoreCpu: String
    let beszelPerCoreSummary: String
    let beszelCpuCoreLabel: String
    let beszelDocker: String
    let beszelNetworkInterfaces: String
    let beszelExternalFilesystems: String
    let beszelRead: String
    let beszelWrite: String
    let beszelUpload: String
    let beszelDownload: String
    let beszelTotalUpload: String
    let beszelTotalDownload: String
    let beszelPassed: String
    let beszelFailing: String
    let beszelLevel: String
    let beszelRemaining: String
    let beszelTotal: String
    let beszelUsed: String
    let beszelModel: String
    let beszelCapacity: String
    let beszelType: String
    let beszelPowerOnHours: String
    let beszelPowerCycles: String
    let beszelSmartAttributes: String
    let beszelPodman: String
    let beszelMemoryUsage: String
    let beszelDockerCpuUsage: String
    let beszelDockerMemoryUsage: String
    let beszelDockerNetworkIO: String
    let beszelContainerInfo: String
    let beszelContainerLogs: String
    let beszelContainerDetails: String
    let beszelContainerFilter: String
    let beszelShowCharts: String
    let beszelHideCharts: String

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
    let giteaFork: String
    let giteaDefault: String
    let giteaCommits: String
    let giteaBranches: String
    let giteaNoFiles: String
    let giteaNoBranches: String
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
    let giteaBranchLabel: String
    let giteaFileTooLarge: String

    // Nginx Proxy Manager
    let npmProxyHosts: String
    let npmRedirections: String
    let npmStreams: String
    let npm404Hosts: String
    let npmHostReport: String
    let npmNoProxyHosts: String
    let npmDisabled: String
    let npmOffline: String
    let npmCache: String
    let npmSecurity: String
    let loginHintNpm: String

    // PatchMon
    let patchmonHosts: String
    let patchmonSecurity: String
    let patchmonUpdates: String
    let patchmonNoHosts: String
    let patchmonStatusActive: String
    let patchmonStatusPending: String
    let patchmonReboot: String
    let patchmonLastUpdate: String
    let patchmonOverview: String
    let patchmonSystem: String
    let patchmonPackages: String
    let patchmonReports: String
    let patchmonRepositories: String
    let patchmonAgentQueue: String
    let patchmonNotes: String
    let patchmonIntegrations: String
    let patchmonHostGroups: String
    let patchmonAllGroups: String
    let patchmonOpenDetails: String
    let patchmonNoHostsInGroup: String
    let patchmonUpdatesOnly: String
    let patchmonShowAllPackages: String
    let patchmonNoPackages: String
    let patchmonNoReports: String
    let patchmonNoJobs: String
    let patchmonNoNotes: String
    let patchmonNoIntegrations: String
    let patchmonDocker: String
    let patchmonMachineId: String
    let patchmonAgentVersion: String
    let patchmonArchitecture: String
    let patchmonKernel: String
    let patchmonInstalledKernel: String
    let patchmonUptime: String
    let patchmonLoadAverage: String
    let patchmonGateway: String
    let patchmonDnsServers: String
    let patchmonInterfaces: String
    let patchmonQueueWaiting: String
    let patchmonQueueActive: String
    let patchmonQueueDelayed: String
    let patchmonQueueFailed: String
    let patchmonCores: String
    let patchmonSwap: String
    let patchmonExecutionTime: String
    let patchmonErrorBadRequest: String
    let patchmonErrorForbidden: String
    let patchmonErrorNotFound: String
    let patchmonErrorRateLimited: String
    let patchmonErrorServer: String
    let patchmonErrorInvalidCredentials: String
    let patchmonErrorIpNotAllowed: String
    let patchmonErrorAccessDenied: String
    let patchmonErrorHostNotFound: String
    let patchmonErrorInvalidHostId: String
    let patchmonErrorDeleteConstraint: String
    let patchmonErrorRetrying: String

    // Nginx Proxy Manager – CRUD
    let npmOverview: String
    let npmSslCertificates: String
    let npmAddProxyHost: String
    let npmEditProxyHost: String
    let npmAddRedirection: String
    let npmEditRedirection: String
    let npmAddStream: String
    let npmEditStream: String
    let npmAddDeadHost: String
    let npmEditDeadHost: String
    let npmAddCertificate: String
    let npmDomainNames: String
    let npmDomainNamesHint: String
    let npmForwardScheme: String
    let npmForwardHost: String
    let npmForwardPort: String
    let npmSslForced: String
    let npmCachingEnabled: String
    let npmWebsocket: String
    let npmHttp2: String
    let npmHsts: String
    let npmHstsSubdomains: String
    let npmAdvancedConfig: String
    let npmEnabled: String
    let npmForwardHttpCode: String
    let npmForwardDomain: String
    let npmPreservePath: String
    let npmIncomingPort: String
    let npmForwardingHost: String
    let npmForwardingPort: String
    let npmTcpForwarding: String
    let npmUdpForwarding: String
    let npmCertificate: String
    let npmCertificateNone: String
    let npmNiceName: String
    let npmLetsencryptEmail: String
    let npmDnsChallenge: String
    let npmLetsencryptAgree: String
    let npmRenew: String
    let npmDelete: String
    let npmDeleteConfirm: String
    let npmDeleteConfirmTitle: String
    let npmNoRedirections: String
    let npmNoStreams: String
    let npmNoDeadHosts: String
    let npmNoCertificates: String
    let npmExpires: String
    let npmExpired: String
    let npmLetsencrypt: String
    let npmCustomCert: String
    let npmProvider: String
    let npmAccessList: String
    let npmAccessListNone: String
    let npmAddAccessList: String
    let npmEditAccessList: String
    let npmUsers: String
    let npmAuditLogs: String
    let npmSettings: String
    let npmComingSoon: String
    let npmNoUsers: String
    let npmNoAuditLogs: String
    let npmNoSettings: String
    let npmAddUser: String
    let npmEditUser: String
    let npmUserEmail: String
    let npmUserName: String
    let npmUserNickname: String
    let npmUserPassword: String
    let npmUserPasswordHint: String
    let npmUserRole: String
    let npmUserRoleAdmin: String
    let npmUserRoleUser: String
    let npmAuditActionCreated: String
    let npmAuditActionUpdated: String
    let npmAuditActionDeleted: String
    let npmAccessListUsers: String
    let npmAccessListClients: String
    let npmAccessListUsername: String
    let npmAccessListPassword: String
    let npmAccessListAddress: String
    let npmAccessListAllow: String
    let npmAccessListDeny: String
    let npmAccessListNoUsers: String
    let npmAccessListNoClients: String
    let npmAccessListRules: String
    let npmSaveSuccess: String
    let npmDeleteSuccess: String
    let npmRenewSuccess: String
    let loginHintNpm2FAWarning: String

    // Units
    let unitDays: String
    let unitHours: String
    let unitMinutes: String
    let unitGB: String
    let unitMB: String
    let unitKB: String
    
    let timeToday: String
    let timeNow: String
    let timeHoursAgo: String
    let timeDayAgo: String
    let timeDaysAgo: String
    let timeMonthsAgo: String

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
    let settingsConfiguredServices: String
    let settingsDisconnect: String
    let settingsDisconnectConfirm: String
    let settingsDisconnectMessage: String
    let settingsAbout: String
    let settingsVersion: String
    let settingsUpdateBannerTitle: String
    let settingsUpdateBannerBody: String
    let settingsUpdateAction: String
    let settingsUpdateDismiss: String
    let updatePopupTitle: String
    let updatePopupBody: String
    let settingsConnected: String
    let settingsNotConnected: String
    let settingsFallbackUrl: String
    let settingsSupportTitle: String
    let settingsSupportDesc: String
    let settingsCopied: String
    let settingsThemeAuto: String
    let settingsHomeCyberpunkCards: String
    let settingsHomeCyberpunkCardsDesc: String
    let settingsContacts: String
    let settingsContactTelegram: String
    let settingsContactReddit: String
    let settingsContactGithub: String
    let settingsHideService: String
    let settingsShowService: String
    let settingsHiddenBadge: String
    let settingsNoInstances: String
    let settingsInstanceSingular: String
    let settingsInstancePlural: String
    let settingsAddInstance: String
    let settingsSetDefault: String
    let settingsDeleteInstanceTitle: String
    let settingsDeleteInstanceMessage: String
    let settingsFallbackPrefix: String
    let settingsMoveUp: String
    let settingsMoveDown: String
    let settingsDebug: String
    let settingsDebugLogs: String
    let debugLogsCopied: String
    let debugLogsErrorTitle: String
    let debugLogsOpenSettings: String
    let actionEdit: String

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
    let securityFaceId: String
    let securityTouchId: String
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

    // Multi-instance
    let badgeDefault: String
    let dashboardInstances: String
    let jellystatWatchTimeHome: String
    let jellystatOverviewSubtitle: String
    let jellystatWatchTime: String
    let jellystatViews: String
    let jellystatWindowDaysFormat: String
    let jellystatActiveDays: String
    let jellystatDaysWithPlayback: String
    let jellystatTopLibrary: String
    let jellystatNoActivity: String
    let jellystatAvgPerDay: String
    let jellystatAverageWatchTime: String
    let jellystatMediaTypeBreakdown: String
    let jellystatSongs: String
    let jellystatMovies: String
    let jellystatEpisodes: String
    let jellystatOther: String
    let jellystatRecentTrend: String
    let jellystatNoDataForPeriod: String
    let jellystatNoData: String
    let jellystatViewsSuffix: String

    // Plex
    let plexOverviewSubtitle: String
    let plexLibraries: String
    let plexTotalItems: String
    let plexActiveSessions: String
    let plexRecentlyAdded: String
    let plexWatchHistory: String
    let plexNoRecentItems: String
    let plexNoData: String
    let plexMovies: String
    let plexShows: String
    let plexEpisodes: String
    let plexMusic: String

    let loginEditTitle: String
    let loginEditSubtitle: String
    let loginLabel: String
    let loginFallbackOptional: String
    let loginPasswordIfChanging: String
    let loginErrorPasswordRequired: String
    let loginShowPassword: String
    let loginHidePassword: String
    let tailscaleBadge: String

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
    let categorySymbolPlaceholder: String
    let categorySymbolExample: String
    let bookmarkUseFavicon: String
    let bookmarkSfSymbolPrompt: String

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
    let bookmarkToggleView: String
    let bookmarkEnterSelfhst: String
    let bookmarkPreviewSelfhst: String
    let bookmarkImagePreview: String
    let bookmarkSelfhstHint: String
    let bookmarkReorder: String
    let bookmarkReorderCategoryLabel: String
    let bookmarkReorderBookmarkLabel: String
    let bookmarkExpandCategory: String
    let bookmarkCollapseCategory: String
    let bookmarkMoveToCategory: String
    let categoryActions: String

    // Onboarding v2
    let onboardingWelcome: String
    let onboardingWelcomeDesc: String
    let onboardingWelcomeButton: String
    let onboardingAskPin: String
    let onboardingAskPinYes: String
    let onboardingAskPinNo: String

    // Errors
    let errorNotConfigured: String
    let errorInvalidURL: String
    let errorNetwork: String
    let errorHttp: String
    let errorDecoding: String
    let errorUnauthorized: String
    let errorBothFailed: String
    let errorUnknown: String
    let errorAtsRequiresSecure: String
    let unknown: String
    let none: String
    let statusOn: String
    let statusOff: String

    // Backup & Restore
    let backupTitle: String
    let backupInfoTitle: String
    let backupInfoDesc: String
    let backupExportTitle: String
    let backupExportAction: String
    let backupExportDesc: String
    let backupExportSuccess: String
    let backupImportTitle: String
    let backupImportAction: String
    let backupImportDesc: String
    let backupImportDecrypt: String
    let backupImportPasswordDesc: String
    let backupImportFileError: String
    let backupImportApply: String
    let backupImportSuccess: String
    let backupImportPreviewTitle: String
    let backupPreviewServices: String
    let backupPreviewUnknown: String
    let backupPreviewWarning: String
    let backupPasswordPlaceholder: String
    let backupPasswordConfirm: String
    let backupPasswordDesc: String
    let backupPasswordRequired: String
    let backupPasswordTooShort: String
    let backupPasswordMismatch: String
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

    static func current() -> Translations {
        let savedLang = UserDefaults.standard.string(forKey: "homelab_language") ?? "en"
        let language = Language(rawValue: savedLang) ?? .en
        return forLanguage(language)
    }
}

// MARK: - Localizer (accessed via environment)

@Observable
@MainActor
final class Localizer {
    static let shared = Localizer()
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
