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
    let tabMedia: String

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
    let serviceRadarr: String
    let serviceSonarr: String
    let serviceLidarr: String
    let serviceQbittorrent: String
    let serviceJellyseerr: String
    let serviceProwlarr: String
    let serviceBazarr: String
    let serviceGluetun: String
    let serviceFlaresolverr: String
    let servicePlexDesc: String
    let serviceRadarrDesc: String
    let serviceSonarrDesc: String
    let serviceLidarrDesc: String
    let serviceQbittorrentDesc: String
    let serviceJellyseerrDesc: String
    let serviceProwlarrDesc: String
    let serviceBazarrDesc: String
    let serviceGluetunDesc: String
    let serviceFlaresolverrDesc: String

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
    let loginHintTechnitium: String
    let loginHintLinuxUpdate: String
    let loginHintDockhand: String
    let loginHintHealthchecks: String
    let loginHintPatchmon: String
    let loginHintJellystat: String
    let loginHintPlex: String
    let loginHintGluetun: String
    let loginHintFlaresolverr: String
    let loginOptional2FA: String
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

    // Dockhand
    let dockhandEnvironments: String
    let dockhandIssues: String
    let dockhandNoContainers: String
    let dockhandStacks: String
    let dockhandNoStacks: String
    let dockhandResources: String
    let dockhandImages: String
    let dockhandVolumes: String
    let dockhandNetworks: String
    let dockhandActivity: String
    let dockhandSchedules: String
    let dockhandDisabled: String
    let dockhandState: String
    let dockhandStatus: String
    let dockhandPorts: String
    let dockhandHealth: String
    let dockhandLogs: String
    let dockhandNoLogs: String
    let dockhandSettingsTitle: String
    let dockhandSettingsRefresh: String
    let dockhandSettingsData: String
    let dockhandAutoRefresh: String
    let dockhandRefreshInterval: String
    let dockhandActivityLimit: String
    let dockhandShowAdvancedActivity: String
    let dockhandLiveStats: String
    let dockhandShowLess: String
    let dockhandMoreLines: String
    let dockhandPlatform: String
    let dockhandRuntime: String
    let dockhandDriver: String
    let dockhandEntrypoint: String
    let dockhandServicesLabel: String
    let dockhandSource: String
    let dockhandSourceType: String
    let dockhandNextRun: String
    let dockhandLastRun: String
    let dockhandDescription: String
    let dockhandEntity: String
    let dockhandSystemLabel: String
    let dockhandEnabledLabel: String
    let dockhandRecentRuns: String

    // Linux Update
    let linuxUpdateActionCheckAll: String
    let linuxUpdateActionRefreshCache: String
    let linuxUpdateActionCheck: String
    let linuxUpdateActionUpgrade: String
    let linuxUpdateActionFullUpgrade: String
    let linuxUpdateActionPackageUpgrade: String
    let linuxUpdateHiddenUpdates: String
    let linuxUpdateNoHiddenUpdates: String
    let linuxUpdateKeptBack: String
    let linuxUpdateHidden: String

    // Technitium
    let technitiumUpdateBlockLists: String
    let technitiumDisableBlocking: String
    let technitiumBlockDomain: String
    let technitiumDisableFor5Minutes: String
    let technitiumDisableFor30Minutes: String
    let technitiumDisableUntilManual: String
    let technitiumCustomDisableTimer: String
    let technitiumCustomDisableDescription: String
    let technitiumMinutes: String
    let technitiumBlockDomainDescription: String
    let technitiumRange: String
    let technitiumDnsBlocking: String
    let technitiumEnabled: String
    let technitiumDisabled: String
    let technitiumDisabledUntil: String
    let technitiumBlockedQueries: String
    let technitiumBlockedZones: String
    let technitiumNoClients: String
    let technitiumNoDomains: String
    let technitiumNoBlockedDomains: String
    let technitiumZones: String
    let technitiumCacheEntries: String
    let technitiumBlocklistZones: String
    let technitiumVersion: String
    let technitiumBlocklistSources: String
    let technitiumRateLimited: String

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
    let settingsAppIcon: String
    let settingsAppIconDefault: String
    let settingsAppIconDark: String
    let settingsAppIconClearLight: String
    let settingsAppIconClearDark: String
    let settingsAppIconTintedLight: String
    let settingsAppIconTintedDark: String
    let settingsHomeCyberpunkCards: String
    let settingsHomeCyberpunkCardsDesc: String
    let settingsContacts: String
    let settingsContactTelegram: String
    let settingsContactReddit: String
    let settingsContactLinuxUpdate: String
    let settingsHideService: String
    let settingsShowService: String
    let settingsHideServiceGeneric: String
    let settingsShowServiceGeneric: String
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
    let backupSelectionTitle: String
    let backupSelectionSubtitle: String
    let backupSelectionAll: String
    let backupSelectionHome: String
    let backupSelectionArr: String
    let backupSelectionEmpty: String
    let backupSelectionRequired: String
    let backupSelectionSelectedCount: String
    let backupRememberSelectionTitle: String
    let backupRememberSelectionSubtitle: String
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
        let systemLang = Locale.preferredLanguages.first.flatMap { code -> Language? in
            Language(rawValue: String(code.prefix(2)).lowercased())
        } ?? .en
        let savedLang = UserDefaults.standard.string(forKey: "homelab_language") ?? systemLang.rawValue
        let language = Language(rawValue: savedLang) ?? systemLang
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
struct ArrStrings {
    let arrGroupTitle: String

    let tutorialTitle: String
    let tutorialBody: String
    let tutorialStepConnect: String
    let tutorialStepOpen: String
    let tutorialStepAutomations: String
    let tutorialActionConfigure: String
    let tutorialActionDismiss: String
    let quickSetupTitle: String
    let quickSetupSubtitle: String
    let addService: (String) -> String

    let connection: String
    let download: String
    let upload: String
    let torrents: String
    let searchTorrents: String

    let filterAll: String
    let filterActive: String
    let filterDone: String
    let filterPaused: String

    let recheck: String
    let reannounce: String
    let deleteWithData: String

    let altLimitsToggled: String
    let allResumed: String
    let allPaused: String
    let torrentResumed: String
    let torrentPaused: String
    let torrentDeleted: String
    let torrentAndDataDeleted: String
    let recheckStarted: String
    let reannounceQueued: String

    let radarrVersion: String
    let sonarrVersion: String
    let lidarrVersion: String
    let branch: String

    let searchMissing: String
    let refreshIndex: String
    let rssSync: String
    let rescan: String
    let downloadedScan: String
    let healthCheck: String
    let contentSearchTitle: String
    let contentSearchPlaceholder: (String) -> String
    let searchNow: String
    let clearSearch: String
    let searchNoResults: String
    let searchStatusInLibrary: String
    let searchStatusMonitored: String
    let searchStatusUnmonitored: String
    let searchStatusEnded: String
    let searchStatusPending: String
    let searchStatusApproved: String
    let searchStatusAvailable: String
    let searchStatusProcessing: String
    let openDetails: String
    let requestContent: String
    let requestQueued: String
    let requestConfigurationTitle: (String) -> String
    let requestConfigurationMessage: String
    let requestQualityProfile: String
    let requestRootFolder: String
    let requestLanguageProfile: String
    let requestMetadataProfile: String

    let downloadingWithCount: (Int) -> String
    let latestAdditions: String
    let tvSeriesLibrary: String
    let latestAlbums: String
    let recentHistory: String
    let health: String
    let upcoming: String
    let noUpcoming: String

    let movieSearchQueued: String
    let movieRefreshQueued: String
    let seriesSearchQueued: String
    let seriesRefreshQueued: String
    let albumSearchQueued: String
    let artistRefreshQueued: String
    let rssSyncQueued: String
    let rescanQueued: String
    let downloadedScanQueued: String
    let healthCheckQueued: String

    let requests: String
    let approveOldestPending: String
    let declineOldestPending: String
    let recentMediaScan: String
    let fullMediaScan: String
    let indexers: String
    let apps: String
    let issues: String
    let testIndexers: String
    let syncApps: String
    let indexerTestStarted: String
    let applicationSyncStarted: String

    let subtitles: String
    let vpn: String
    let restartVpnTunnel: String
    let provider: String
    let forwardedPort: String
    let service: String
    let newSession: String
    let sessionIds: String
    let statusLabel: String
    let versionLabel: String
    let messageLabel: String
    let urlLabel: String
    let fallbackURLLabel: String
    let apiKeyLabel: String
    let publicIPLabel: String
    let countryLabel: String
    let serverLabel: String
    let dhtLabel: String
    let diskFreeLabel: String
    let altSpeedLabel: String
    let etaLabel: String
    let seedsLeechersLabel: String
    let ratioLabel: String
    let eventFallback: String
    let openService: String
    let openFallback: String
    let sessions: String

    let total: String
    let pending: String
    let approved: String
    let available: String

    let sessionCreatedPrefix: String
    let sessionDeleted: String
    let oldestPendingApproved: String
    let oldestPendingDeclined: String
    let recentScanStarted: String
    let fullScanStarted: String
    let vpnRestartQueued: String
    let requestApproved: String
    let requestDeclined: String

    let showLess: String
    let showMore: (Int) -> String
}

extension ArrStrings {
    static func forLanguage(_ language: Language) -> ArrStrings {
        switch language {
        case .it:
            return ArrStrings(
                arrGroupTitle: "Servizi ARR",
                tutorialTitle: "Media ARR pronto in pochi minuti",
                tutorialBody: "Configura solo i servizi che usi: la pagina resta dinamica e pulita.",
                tutorialStepConnect: "Aggiungi URL e credenziali/API key per ogni servizio.",
                tutorialStepOpen: "Apri ogni card per azioni rapide, stato e controlli.",
                tutorialStepAutomations: "Backup e impostazioni mantengono tutto allineato.",
                tutorialActionConfigure: "Configura servizi",
                tutorialActionDismiss: "Nascondi guida",
                quickSetupTitle: "Servizi non configurati",
                quickSetupSubtitle: "Aggiungi solo quelli che usi davvero.",
                addService: { "Aggiungi \($0)" },
                connection: "Connessione",
                download: "Download",
                upload: "Upload",
                torrents: "Torrent",
                searchTorrents: "Cerca torrent",
                filterAll: "Tutti",
                filterActive: "Attivi",
                filterDone: "Completati",
                filterPaused: "In pausa",
                recheck: "Ricontrolla",
                reannounce: "Riannuncia",
                deleteWithData: "Elimina + Dati",
                altLimitsToggled: "Limiti alternativi aggiornati",
                allResumed: "Tutti i torrent ripresi",
                allPaused: "Tutti i torrent in pausa",
                torrentResumed: "Torrent ripreso",
                torrentPaused: "Torrent in pausa",
                torrentDeleted: "Torrent eliminato",
                torrentAndDataDeleted: "Torrent e dati eliminati",
                recheckStarted: "Verifica avviata",
                reannounceQueued: "Riannuncio accodato",
                radarrVersion: "Versione Radarr",
                sonarrVersion: "Versione Sonarr",
                lidarrVersion: "Versione Lidarr",
                branch: "Branch",
                searchMissing: "Cerca Mancanti",
                refreshIndex: "Aggiorna Indice",
                rssSync: "Sincronizza RSS",
                rescan: "Rescan",
                downloadedScan: "Scansione scaricati",
                healthCheck: "Controllo salute",
                contentSearchTitle: "Ricerca contenuti",
                contentSearchPlaceholder: { "Cerca in \($0)" },
                searchNow: "Cerca",
                clearSearch: "Pulisci",
                searchNoResults: "Nessun risultato",
                searchStatusInLibrary: "In libreria",
                searchStatusMonitored: "Monitorato",
                searchStatusUnmonitored: "Non monitorato",
                searchStatusEnded: "Concluso",
                searchStatusPending: "In attesa",
                searchStatusApproved: "Approvato",
                searchStatusAvailable: "Disponibile",
                searchStatusProcessing: "In elaborazione",
                openDetails: "Apri dettagli",
                requestContent: "Richiedi contenuto",
                requestQueued: "Richiesta inviata",
                requestConfigurationTitle: { "Configura richiesta per \($0)" },
                requestConfigurationMessage: "Seleziona il profilo e la cartella da usare per questa richiesta.",
                requestQualityProfile: "Profilo qualità",
                requestRootFolder: "Cartella root",
                requestLanguageProfile: "Profilo lingua",
                requestMetadataProfile: "Profilo metadata",
                downloadingWithCount: { "Download (\($0))" },
                latestAdditions: "Ultimi Aggiunti",
                tvSeriesLibrary: "Libreria Serie TV",
                latestAlbums: "Ultimi Album",
                recentHistory: "Cronologia Recente",
                health: "Salute",
                upcoming: "In Arrivo",
                noUpcoming: "Nessuna uscita imminente",
                movieSearchQueued: "Ricerca film accodata",
                movieRefreshQueued: "Refresh film accodato",
                seriesSearchQueued: "Ricerca serie accodata",
                seriesRefreshQueued: "Refresh serie accodato",
                albumSearchQueued: "Ricerca album accodata",
                artistRefreshQueued: "Refresh artisti accodato",
                rssSyncQueued: "Sync RSS accodata",
                rescanQueued: "Rescan accodato",
                downloadedScanQueued: "Scansione scaricati accodata",
                healthCheckQueued: "Controllo salute accodato",
                requests: "Richieste",
                approveOldestPending: "Approva attesa più vecchia",
                declineOldestPending: "Rifiuta attesa più vecchia",
                recentMediaScan: "Scan media recenti",
                fullMediaScan: "Scan media completo",
                indexers: "Indexer",
                apps: "App",
                issues: "Problemi",
                testIndexers: "Test Indexer",
                syncApps: "Sync App",
                indexerTestStarted: "Test indexer avviato",
                applicationSyncStarted: "Sync applicazioni avviata",
                subtitles: "Sottotitoli",
                vpn: "VPN",
                restartVpnTunnel: "Riavvia tunnel VPN",
                provider: "Provider",
                forwardedPort: "Porta Inoltrata",
                service: "Servizio",
                newSession: "Nuova Sessione",
                sessionIds: "ID Sessioni",
                statusLabel: "Stato",
                versionLabel: "Versione",
                messageLabel: "Messaggio",
                urlLabel: "URL",
                fallbackURLLabel: "URL fallback",
                apiKeyLabel: "API Key",
                publicIPLabel: "IP pubblico",
                countryLabel: "Paese",
                serverLabel: "Server",
                dhtLabel: "Nodi DHT",
                diskFreeLabel: "Disco libero",
                altSpeedLabel: "Velocità alternativa",
                etaLabel: "ETA",
                seedsLeechersLabel: "Seed/Leech",
                ratioLabel: "Rapporto",
                eventFallback: "Evento",
                openService: "Apri Servizio",
                openFallback: "Apri Fallback",
                sessions: "Sessioni",
                total: "Totale",
                pending: "In Attesa",
                approved: "Approvate",
                available: "Disponibili",
                sessionCreatedPrefix: "Sessione creata:",
                sessionDeleted: "Sessione eliminata",
                oldestPendingApproved: "Richiesta in attesa approvata",
                oldestPendingDeclined: "Richiesta in attesa rifiutata",
                recentScanStarted: "Scan recente avviato",
                fullScanStarted: "Scan completo avviato",
                vpnRestartQueued: "Riavvio tunnel VPN avviato",
                requestApproved: "Richiesta approvata",
                requestDeclined: "Richiesta rifiutata",
                showLess: "Mostra meno",
                showMore: { "Mostra altri \($0)" }
            )
        case .fr:
            return ArrStrings(
                arrGroupTitle: "Services ARR",
                tutorialTitle: "Media ARR prêt en quelques minutes",
                tutorialBody: "Configurez uniquement les services que vous utilisez.",
                tutorialStepConnect: "Ajoutez URL et identifiants/API key pour chaque service.",
                tutorialStepOpen: "Ouvrez chaque carte pour actions rapides et statut.",
                tutorialStepAutomations: "Sauvegarde et réglages restent synchronisés.",
                tutorialActionConfigure: "Configurer les services",
                tutorialActionDismiss: "Masquer le guide",
                quickSetupTitle: "Services non configurés",
                quickSetupSubtitle: "Ajoutez seulement ceux que vous utilisez.",
                addService: { "Ajouter \($0)" },
                connection: "Connexion",
                download: "Téléchargement",
                upload: "Envoi",
                torrents: "Torrents",
                searchTorrents: "Rechercher des torrents",
                filterAll: "Tous",
                filterActive: "Actifs",
                filterDone: "Terminés",
                filterPaused: "En pause",
                recheck: "Revérifier",
                reannounce: "Réannoncer",
                deleteWithData: "Suppr. + Données",
                altLimitsToggled: "Limites alternatives basculées",
                allResumed: "Tous les torrents repris",
                allPaused: "Tous les torrents en pause",
                torrentResumed: "Torrent repris",
                torrentPaused: "Torrent en pause",
                torrentDeleted: "Torrent supprimé",
                torrentAndDataDeleted: "Torrent et données supprimés",
                recheckStarted: "Revérification lancée",
                reannounceQueued: "Réannonce mise en file",
                radarrVersion: "Version Radarr",
                sonarrVersion: "Version Sonarr",
                lidarrVersion: "Version Lidarr",
                branch: "Branche",
                searchMissing: "Rechercher manquants",
                refreshIndex: "Rafraîchir l'index",
                rssSync: "Sync RSS",
                rescan: "Rescan",
                downloadedScan: "Scan téléchargements",
                healthCheck: "Contrôle santé",
                contentSearchTitle: "Recherche de contenu",
                contentSearchPlaceholder: { "Rechercher dans \($0)" },
                searchNow: "Rechercher",
                clearSearch: "Effacer",
                searchNoResults: "Aucun résultat",
                searchStatusInLibrary: "En bibliothèque",
                searchStatusMonitored: "Surveillé",
                searchStatusUnmonitored: "Non surveillé",
                searchStatusEnded: "Terminé",
                searchStatusPending: "En attente",
                searchStatusApproved: "Approuvé",
                searchStatusAvailable: "Disponible",
                searchStatusProcessing: "Traitement",
                openDetails: "Ouvrir les détails",
                requestContent: "Demander le contenu",
                requestQueued: "Demande envoyée",
                requestConfigurationTitle: { "Configurer la demande pour \($0)" },
                requestConfigurationMessage: "Sélectionnez le profil et le dossier à utiliser pour cette demande.",
                requestQualityProfile: "Profil qualité",
                requestRootFolder: "Dossier racine",
                requestLanguageProfile: "Profil de langue",
                requestMetadataProfile: "Profil de métadonnées",
                downloadingWithCount: { "Téléchargements (\($0))" },
                latestAdditions: "Derniers ajouts",
                tvSeriesLibrary: "Bibliothèque Séries TV",
                latestAlbums: "Derniers albums",
                recentHistory: "Historique récent",
                health: "Santé",
                upcoming: "À venir",
                noUpcoming: "Aucune sortie imminente",
                movieSearchQueued: "Recherche films en file",
                movieRefreshQueued: "Rafraîchissement films en file",
                seriesSearchQueued: "Recherche séries en file",
                seriesRefreshQueued: "Rafraîchissement séries en file",
                albumSearchQueued: "Recherche albums en file",
                artistRefreshQueued: "Rafraîchissement artistes en file",
                rssSyncQueued: "Sync RSS en file",
                rescanQueued: "Rescan en file",
                downloadedScanQueued: "Scan téléchargements en file",
                healthCheckQueued: "Contrôle santé en file",
                requests: "Demandes",
                approveOldestPending: "Approuver plus ancienne attente",
                declineOldestPending: "Refuser plus ancienne attente",
                recentMediaScan: "Scan médias récents",
                fullMediaScan: "Scan médias complet",
                indexers: "Indexers",
                apps: "Apps",
                issues: "Problèmes",
                testIndexers: "Tester indexers",
                syncApps: "Sync apps",
                indexerTestStarted: "Test indexers lancé",
                applicationSyncStarted: "Sync applications lancée",
                subtitles: "Sous-titres",
                vpn: "VPN",
                restartVpnTunnel: "Redémarrer tunnel VPN",
                provider: "Fournisseur",
                forwardedPort: "Port redirigé",
                service: "Service",
                newSession: "Nouvelle session",
                sessionIds: "ID de session",
                statusLabel: "Statut",
                versionLabel: "Version",
                messageLabel: "Message",
                urlLabel: "URL",
                fallbackURLLabel: "URL de secours",
                apiKeyLabel: "Clé API",
                publicIPLabel: "IP publique",
                countryLabel: "Pays",
                serverLabel: "Serveur",
                dhtLabel: "Nœuds DHT",
                diskFreeLabel: "Disque libre",
                altSpeedLabel: "Vitesse alternative",
                etaLabel: "ETA",
                seedsLeechersLabel: "Seed/Leech",
                ratioLabel: "Ratio",
                eventFallback: "Événement",
                openService: "Ouvrir le service",
                openFallback: "Ouvrir le fallback",
                sessions: "Sessions",
                total: "Total",
                pending: "En attente",
                approved: "Approuvées",
                available: "Disponibles",
                sessionCreatedPrefix: "Session créée :",
                sessionDeleted: "Session supprimée",
                oldestPendingApproved: "Demande en attente approuvée",
                oldestPendingDeclined: "Demande en attente refusée",
                recentScanStarted: "Scan récent lancé",
                fullScanStarted: "Scan complet lancé",
                vpnRestartQueued: "Redémarrage du tunnel VPN lancé",
                requestApproved: "Demande approuvée",
                requestDeclined: "Demande refusée",
                showLess: "Voir moins",
                showMore: { "Voir \($0) de plus" }
            )
        case .es:
            return ArrStrings(
                arrGroupTitle: "Servicios ARR",
                tutorialTitle: "Media ARR listo en minutos",
                tutorialBody: "Configura solo los servicios que uses.",
                tutorialStepConnect: "Añade URL y credenciales/API key de cada servicio.",
                tutorialStepOpen: "Abre cada tarjeta para acciones rápidas y estado.",
                tutorialStepAutomations: "Backup y ajustes se mantienen alineados.",
                tutorialActionConfigure: "Configurar servicios",
                tutorialActionDismiss: "Ocultar guía",
                quickSetupTitle: "Servicios no configurados",
                quickSetupSubtitle: "Añade solo los que realmente uses.",
                addService: { "Añadir \($0)" },
                connection: "Conexión",
                download: "Descarga",
                upload: "Subida",
                torrents: "Torrents",
                searchTorrents: "Buscar torrents",
                filterAll: "Todos",
                filterActive: "Activos",
                filterDone: "Completados",
                filterPaused: "Pausados",
                recheck: "Revisar",
                reannounce: "Reanunciar",
                deleteWithData: "Borrar + Datos",
                altLimitsToggled: "Límites alternativos cambiados",
                allResumed: "Todos los torrents reanudados",
                allPaused: "Todos los torrents pausados",
                torrentResumed: "Torrent reanudado",
                torrentPaused: "Torrent pausado",
                torrentDeleted: "Torrent eliminado",
                torrentAndDataDeleted: "Torrent y datos eliminados",
                recheckStarted: "Revisión iniciada",
                reannounceQueued: "Reanuncio en cola",
                radarrVersion: "Versión de Radarr",
                sonarrVersion: "Versión de Sonarr",
                lidarrVersion: "Versión de Lidarr",
                branch: "Rama",
                searchMissing: "Buscar faltantes",
                refreshIndex: "Actualizar índice",
                rssSync: "Sincronizar RSS",
                rescan: "Rescan",
                downloadedScan: "Escaneo descargados",
                healthCheck: "Control de salud",
                contentSearchTitle: "Búsqueda de contenido",
                contentSearchPlaceholder: { "Buscar en \($0)" },
                searchNow: "Buscar",
                clearSearch: "Limpiar",
                searchNoResults: "Sin resultados",
                searchStatusInLibrary: "En biblioteca",
                searchStatusMonitored: "Monitorizado",
                searchStatusUnmonitored: "No monitorizado",
                searchStatusEnded: "Finalizado",
                searchStatusPending: "Pendiente",
                searchStatusApproved: "Aprobado",
                searchStatusAvailable: "Disponible",
                searchStatusProcessing: "Procesando",
                openDetails: "Abrir detalles",
                requestContent: "Solicitar contenido",
                requestQueued: "Solicitud enviada",
                requestConfigurationTitle: { "Configurar solicitud para \($0)" },
                requestConfigurationMessage: "Selecciona el perfil y la carpeta que se usarán para esta solicitud.",
                requestQualityProfile: "Perfil de calidad",
                requestRootFolder: "Carpeta raíz",
                requestLanguageProfile: "Perfil de idioma",
                requestMetadataProfile: "Perfil de metadatos",
                downloadingWithCount: { "Descargando (\($0))" },
                latestAdditions: "Últimos agregados",
                tvSeriesLibrary: "Biblioteca de series",
                latestAlbums: "Últimos álbumes",
                recentHistory: "Historial reciente",
                health: "Salud",
                upcoming: "Próximamente",
                noUpcoming: "Sin lanzamientos próximos",
                movieSearchQueued: "Búsqueda de películas en cola",
                movieRefreshQueued: "Actualización de películas en cola",
                seriesSearchQueued: "Búsqueda de series en cola",
                seriesRefreshQueued: "Actualización de series en cola",
                albumSearchQueued: "Búsqueda de álbumes en cola",
                artistRefreshQueued: "Actualización de artistas en cola",
                rssSyncQueued: "Sync RSS en cola",
                rescanQueued: "Rescan en cola",
                downloadedScanQueued: "Escaneo de descargados en cola",
                healthCheckQueued: "Control de salud en cola",
                requests: "Solicitudes",
                approveOldestPending: "Aprobar espera más antigua",
                declineOldestPending: "Rechazar espera más antigua",
                recentMediaScan: "Escaneo medios recientes",
                fullMediaScan: "Escaneo completo",
                indexers: "Indexadores",
                apps: "Apps",
                issues: "Problemas",
                testIndexers: "Probar indexadores",
                syncApps: "Sync apps",
                indexerTestStarted: "Prueba de indexadores iniciada",
                applicationSyncStarted: "Sincronización de aplicaciones iniciada",
                subtitles: "Subtítulos",
                vpn: "VPN",
                restartVpnTunnel: "Reiniciar túnel VPN",
                provider: "Proveedor",
                forwardedPort: "Puerto reenviado",
                service: "Servicio",
                newSession: "Nueva sesión",
                sessionIds: "IDs de sesión",
                statusLabel: "Estado",
                versionLabel: "Versión",
                messageLabel: "Mensaje",
                urlLabel: "URL",
                fallbackURLLabel: "URL de respaldo",
                apiKeyLabel: "API Key",
                publicIPLabel: "IP pública",
                countryLabel: "País",
                serverLabel: "Servidor",
                dhtLabel: "Nodos DHT",
                diskFreeLabel: "Disco libre",
                altSpeedLabel: "Velocidad alternativa",
                etaLabel: "ETA",
                seedsLeechersLabel: "Seed/Leech",
                ratioLabel: "Ratio",
                eventFallback: "Evento",
                openService: "Abrir servicio",
                openFallback: "Abrir fallback",
                sessions: "Sesiones",
                total: "Total",
                pending: "Pendientes",
                approved: "Aprobadas",
                available: "Disponibles",
                sessionCreatedPrefix: "Sesión creada:",
                sessionDeleted: "Sesión eliminada",
                oldestPendingApproved: "Solicitud pendiente aprobada",
                oldestPendingDeclined: "Solicitud pendiente rechazada",
                recentScanStarted: "Escaneo reciente iniciado",
                fullScanStarted: "Escaneo completo iniciado",
                vpnRestartQueued: "Reinicio de túnel VPN iniciado",
                requestApproved: "Solicitud aprobada",
                requestDeclined: "Solicitud rechazada",
                showLess: "Ver menos",
                showMore: { "Ver \($0) más" }
            )
        case .de:
            return ArrStrings(
                arrGroupTitle: "ARR-Dienste",
                tutorialTitle: "Media ARR in wenigen Minuten bereit",
                tutorialBody: "Konfiguriere nur die Dienste, die du wirklich nutzt.",
                tutorialStepConnect: "URL und Zugangsdaten/API key je Dienst eintragen.",
                tutorialStepOpen: "Jede Karte für Schnellaktionen und Status öffnen.",
                tutorialStepAutomations: "Backup und Einstellungen bleiben synchron.",
                tutorialActionConfigure: "Dienste konfigurieren",
                tutorialActionDismiss: "Guide ausblenden",
                quickSetupTitle: "Nicht konfigurierte Dienste",
                quickSetupSubtitle: "Füge nur benötigte Dienste hinzu.",
                addService: { "\($0) hinzufügen" },
                connection: "Verbindung",
                download: "Download",
                upload: "Upload",
                torrents: "Torrents",
                searchTorrents: "Torrents suchen",
                filterAll: "Alle",
                filterActive: "Aktiv",
                filterDone: "Fertig",
                filterPaused: "Pausiert",
                recheck: "Erneut prüfen",
                reannounce: "Neu ankündigen",
                deleteWithData: "Löschen + Daten",
                altLimitsToggled: "Alternative Limits umgeschaltet",
                allResumed: "Alle Torrents fortgesetzt",
                allPaused: "Alle Torrents pausiert",
                torrentResumed: "Torrent fortgesetzt",
                torrentPaused: "Torrent pausiert",
                torrentDeleted: "Torrent gelöscht",
                torrentAndDataDeleted: "Torrent und Daten gelöscht",
                recheckStarted: "Prüfung gestartet",
                reannounceQueued: "Neuankündigung eingereiht",
                radarrVersion: "Radarr-Version",
                sonarrVersion: "Sonarr-Version",
                lidarrVersion: "Lidarr-Version",
                branch: "Branch",
                searchMissing: "Fehlende suchen",
                refreshIndex: "Index aktualisieren",
                rssSync: "RSS Sync",
                rescan: "Rescan",
                downloadedScan: "Downloads scannen",
                healthCheck: "Health-Check",
                contentSearchTitle: "Inhaltssuche",
                contentSearchPlaceholder: { "In \($0) suchen" },
                searchNow: "Suchen",
                clearSearch: "Leeren",
                searchNoResults: "Keine Ergebnisse",
                searchStatusInLibrary: "In Bibliothek",
                searchStatusMonitored: "Überwacht",
                searchStatusUnmonitored: "Nicht überwacht",
                searchStatusEnded: "Beendet",
                searchStatusPending: "Ausstehend",
                searchStatusApproved: "Genehmigt",
                searchStatusAvailable: "Verfügbar",
                searchStatusProcessing: "Verarbeitung",
                openDetails: "Details öffnen",
                requestContent: "Inhalt anfragen",
                requestQueued: "Anfrage gesendet",
                requestConfigurationTitle: { "Anfrage für \($0) konfigurieren" },
                requestConfigurationMessage: "Wähle das Profil und den Ordner für diese Anfrage aus.",
                requestQualityProfile: "Qualitätsprofil",
                requestRootFolder: "Stammordner",
                requestLanguageProfile: "Sprachprofil",
                requestMetadataProfile: "Metadatenprofil",
                downloadingWithCount: { "Herunterladen (\($0))" },
                latestAdditions: "Neueste hinzugefügt",
                tvSeriesLibrary: "TV-Serienbibliothek",
                latestAlbums: "Neueste Alben",
                recentHistory: "Neueste Historie",
                health: "Zustand",
                upcoming: "Demnächst",
                noUpcoming: "Keine bevorstehenden Veröffentlichungen",
                movieSearchQueued: "Filmsuche eingereiht",
                movieRefreshQueued: "Film-Refresh eingereiht",
                seriesSearchQueued: "Seriensuche eingereiht",
                seriesRefreshQueued: "Serien-Refresh eingereiht",
                albumSearchQueued: "Alben-Suche eingereiht",
                artistRefreshQueued: "Künstler-Refresh eingereiht",
                rssSyncQueued: "RSS Sync eingereiht",
                rescanQueued: "Rescan eingereiht",
                downloadedScanQueued: "Download-Scan eingereiht",
                healthCheckQueued: "Health-Check eingereiht",
                requests: "Anfragen",
                approveOldestPending: "Älteste Wartende genehmigen",
                declineOldestPending: "Älteste Wartende ablehnen",
                recentMediaScan: "Aktuelle Medien scannen",
                fullMediaScan: "Vollständiger Scan",
                indexers: "Indexer",
                apps: "Apps",
                issues: "Probleme",
                testIndexers: "Indexer testen",
                syncApps: "Apps syncen",
                indexerTestStarted: "Indexer-Test gestartet",
                applicationSyncStarted: "App-Sync gestartet",
                subtitles: "Untertitel",
                vpn: "VPN",
                restartVpnTunnel: "VPN-Tunnel neu starten",
                provider: "Anbieter",
                forwardedPort: "Weitergeleiteter Port",
                service: "Dienst",
                newSession: "Neue Sitzung",
                sessionIds: "Sitzungs-IDs",
                statusLabel: "Status",
                versionLabel: "Version",
                messageLabel: "Nachricht",
                urlLabel: "URL",
                fallbackURLLabel: "Fallback-URL",
                apiKeyLabel: "API-Key",
                publicIPLabel: "Öffentliche IP",
                countryLabel: "Land",
                serverLabel: "Server",
                dhtLabel: "DHT-Knoten",
                diskFreeLabel: "Freier Speicher",
                altSpeedLabel: "Alternatives Limit",
                etaLabel: "ETA",
                seedsLeechersLabel: "Seed/Leech",
                ratioLabel: "Verhältnis",
                eventFallback: "Ereignis",
                openService: "Dienst öffnen",
                openFallback: "Fallback öffnen",
                sessions: "Sitzungen",
                total: "Gesamt",
                pending: "Ausstehend",
                approved: "Genehmigt",
                available: "Verfügbar",
                sessionCreatedPrefix: "Sitzung erstellt:",
                sessionDeleted: "Sitzung gelöscht",
                oldestPendingApproved: "Ausstehende Anfrage genehmigt",
                oldestPendingDeclined: "Ausstehende Anfrage abgelehnt",
                recentScanStarted: "Aktueller Scan gestartet",
                fullScanStarted: "Vollständiger Scan gestartet",
                vpnRestartQueued: "VPN-Tunnel-Neustart gestartet",
                requestApproved: "Anfrage genehmigt",
                requestDeclined: "Anfrage abgelehnt",
                showLess: "Weniger anzeigen",
                showMore: { "\($0) weitere anzeigen" }
            )
        case .en:
            return ArrStrings(
                arrGroupTitle: "ARR Services",
                tutorialTitle: "Media ARR ready in minutes",
                tutorialBody: "Configure only the services you actually use.",
                tutorialStepConnect: "Add URL and credentials/API key for each service.",
                tutorialStepOpen: "Open each card for quick actions and status.",
                tutorialStepAutomations: "Backup and settings stay in sync.",
                tutorialActionConfigure: "Configure services",
                tutorialActionDismiss: "Hide guide",
                quickSetupTitle: "Unconfigured services",
                quickSetupSubtitle: "Add only what you really use.",
                addService: { "Add \($0)" },
                connection: "Connection",
                download: "Download",
                upload: "Upload",
                torrents: "Torrents",
                searchTorrents: "Search torrents",
                filterAll: "All",
                filterActive: "Active",
                filterDone: "Done",
                filterPaused: "Paused",
                recheck: "Recheck",
                reannounce: "Reannounce",
                deleteWithData: "Delete + Data",
                altLimitsToggled: "Alternative limits toggled",
                allResumed: "All torrents resumed",
                allPaused: "All torrents paused",
                torrentResumed: "Torrent resumed",
                torrentPaused: "Torrent paused",
                torrentDeleted: "Torrent deleted",
                torrentAndDataDeleted: "Torrent and data deleted",
                recheckStarted: "Recheck started",
                reannounceQueued: "Reannounce queued",
                radarrVersion: "Radarr Version",
                sonarrVersion: "Sonarr Version",
                lidarrVersion: "Lidarr Version",
                branch: "Branch",
                searchMissing: "Search Missing",
                refreshIndex: "Refresh Index",
                rssSync: "RSS Sync",
                rescan: "Rescan",
                downloadedScan: "Downloaded Scan",
                healthCheck: "Health Check",
                contentSearchTitle: "Content Search",
                contentSearchPlaceholder: { "Search in \($0)" },
                searchNow: "Search",
                clearSearch: "Clear",
                searchNoResults: "No results",
                searchStatusInLibrary: "In library",
                searchStatusMonitored: "Monitored",
                searchStatusUnmonitored: "Unmonitored",
                searchStatusEnded: "Ended",
                searchStatusPending: "Pending",
                searchStatusApproved: "Approved",
                searchStatusAvailable: "Available",
                searchStatusProcessing: "Processing",
                openDetails: "Open details",
                requestContent: "Request content",
                requestQueued: "Request sent",
                requestConfigurationTitle: { "Configure request for \($0)" },
                requestConfigurationMessage: "Choose the profile and folder to use for this request.",
                requestQualityProfile: "Quality profile",
                requestRootFolder: "Root folder",
                requestLanguageProfile: "Language profile",
                requestMetadataProfile: "Metadata profile",
                downloadingWithCount: { "Downloading (\($0))" },
                latestAdditions: "Latest Additions",
                tvSeriesLibrary: "TV Series Library",
                latestAlbums: "Latest Albums",
                recentHistory: "Recent History",
                health: "Health",
                upcoming: "Upcoming",
                noUpcoming: "No upcoming releases",
                movieSearchQueued: "Movie search queued",
                movieRefreshQueued: "Movie refresh queued",
                seriesSearchQueued: "Series search queued",
                seriesRefreshQueued: "Series refresh queued",
                albumSearchQueued: "Album search queued",
                artistRefreshQueued: "Artist refresh queued",
                rssSyncQueued: "RSS sync queued",
                rescanQueued: "Rescan queued",
                downloadedScanQueued: "Downloaded scan queued",
                healthCheckQueued: "Health check queued",
                requests: "Requests",
                approveOldestPending: "Approve oldest pending",
                declineOldestPending: "Decline oldest pending",
                recentMediaScan: "Recent media scan",
                fullMediaScan: "Full media scan",
                indexers: "Indexers",
                apps: "Apps",
                issues: "Issues",
                testIndexers: "Test Indexers",
                syncApps: "Sync Apps",
                indexerTestStarted: "Indexer test started",
                applicationSyncStarted: "Application sync started",
                subtitles: "Subtitles",
                vpn: "VPN",
                restartVpnTunnel: "Restart VPN tunnel",
                provider: "Provider",
                forwardedPort: "Forwarded Port",
                service: "Service",
                newSession: "New Session",
                sessionIds: "Session IDs",
                statusLabel: "Status",
                versionLabel: "Version",
                messageLabel: "Message",
                urlLabel: "URL",
                fallbackURLLabel: "Fallback URL",
                apiKeyLabel: "API Key",
                publicIPLabel: "Public IP",
                countryLabel: "Country",
                serverLabel: "Server",
                dhtLabel: "DHT Nodes",
                diskFreeLabel: "Disk Free",
                altSpeedLabel: "Alt Speed",
                etaLabel: "ETA",
                seedsLeechersLabel: "Seeds/Leechers",
                ratioLabel: "Ratio",
                eventFallback: "Event",
                openService: "Open Service",
                openFallback: "Open Fallback",
                sessions: "Sessions",
                total: "Total",
                pending: "Pending",
                approved: "Approved",
                available: "Available",
                sessionCreatedPrefix: "Session created:",
                sessionDeleted: "Session deleted",
                oldestPendingApproved: "Oldest pending request approved",
                oldestPendingDeclined: "Oldest pending request declined",
                recentScanStarted: "Recent scan started",
                fullScanStarted: "Full scan started",
                vpnRestartQueued: "VPN tunnel restart started",
                requestApproved: "Request approved",
                requestDeclined: "Request declined",
                showLess: "Show less",
                showMore: { "Show \($0) more" }
            )
        }
    }
}

extension Localizer {
    var arr: ArrStrings {
        ArrStrings.forLanguage(language)
    }
}
