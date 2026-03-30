import Foundation
import Observation
import UIKit

@Observable
@MainActor
final class SettingsStore {

    // MARK: - Persisted State

    var language: Language {
        didSet {
            UserDefaults.standard.set(language.rawValue, forKey: Keys.language)
        }
    }

    var theme: ThemeMode {
        didSet {
            UserDefaults.standard.set(theme.rawValue, forKey: Keys.theme)
        }
    }

    var hiddenServices: Set<String> {
        didSet {
            UserDefaults.standard.set(Array(hiddenServices), forKey: Keys.hiddenServices)
        }
    }

    private(set) var serviceOrder: [ServiceType] {
        didSet {
            UserDefaults.standard.set(serviceOrder.map(\.rawValue), forKey: Keys.serviceOrder)
        }
    }

    var biometricEnabled: Bool {
        didSet {
            UserDefaults.standard.set(biometricEnabled, forKey: Keys.biometricEnabled)
        }
    }

    var hasCompletedOnboarding: Bool {
        didSet {
            UserDefaults.standard.set(hasCompletedOnboarding, forKey: Keys.hasCompletedOnboarding)
        }
    }

    var homeCyberpunkCardsEnabled: Bool {
        didSet {
            UserDefaults.standard.set(homeCyberpunkCardsEnabled, forKey: Keys.homeCyberpunkCardsEnabled)
        }
    }

    private(set) var appIcon: AppIconOption {
        didSet {
            UserDefaults.standard.set(appIcon.rawValue, forKey: Keys.appIcon)
        }
    }

    var backupRememberSelectionEnabled: Bool {
        didSet {
            UserDefaults.standard.set(backupRememberSelectionEnabled, forKey: Keys.backupRememberSelectionEnabled)
        }
    }

    var backupSelectedServiceTypes: Set<ServiceType> {
        didSet {
            UserDefaults.standard.set(
                backupSelectedServiceTypes.map(\.rawValue).sorted(),
                forKey: Keys.backupSelectedServiceTypes
            )
        }
    }

    private(set) var availableUpdateVersion: String? = nil {
        didSet {
            UserDefaults.standard.set(availableUpdateVersion, forKey: Keys.availableUpdateVersion)
        }
    }

    private(set) var availableUpdateURL: String? = nil {
        didSet {
            UserDefaults.standard.set(availableUpdateURL, forKey: Keys.availableUpdateURL)
        }
    }

    private(set) var availableUpdateChangelog: String? = nil {
        didSet {
            UserDefaults.standard.set(availableUpdateChangelog, forKey: Keys.availableUpdateChangelog)
        }
    }

    private var dismissedUpdateVersion: String? {
        didSet {
            UserDefaults.standard.set(dismissedUpdateVersion, forKey: Keys.dismissedUpdateVersion)
        }
    }

    private var dismissedPopupVersion: String? {
        didSet {
            UserDefaults.standard.set(dismissedPopupVersion, forKey: Keys.dismissedPopupVersion)
        }
    }

    var showUpdatePopup: Bool = false

    private var lastUpdateCheckAt: Date? {
        didSet {
            UserDefaults.standard.set(lastUpdateCheckAt?.timeIntervalSince1970, forKey: Keys.lastUpdateCheckAt)
        }
    }

    var lastBackgroundDate: Date? = nil

    // MARK: - Keys

    private enum Keys {
        static let language = "homelab_language"
        static let theme = "homelab_theme"
        static let hiddenServices = "homelab_hidden_services"
        static let serviceOrder = "homelab_service_order"
        static let biometricEnabled = "homelab_biometric_enabled"
        static let hasCompletedOnboarding = "homelab_has_completed_onboarding"
        static let homeCyberpunkCardsEnabled = "homelab_home_cyberpunk_cards_enabled"
        static let appIcon = "homelab_app_icon"
        static let dismissedUpdateVersion = "homelab_dismissed_update_version"
        static let lastUpdateCheckAt = "homelab_last_update_check_at"
        static let availableUpdateVersion = "homelab_available_update_version"
        static let availableUpdateURL = "homelab_available_update_url"
        static let availableUpdateChangelog = "homelab_available_update_changelog"
        static let dismissedPopupVersion = "homelab_dismissed_popup_version"
        static let backupRememberSelectionEnabled = "homelab_backup_remember_selection_enabled"
        static let backupSelectedServiceTypes = "homelab_backup_selected_service_types"
    }

    private static let updateFeedURL = URL(string: "https://raw.githubusercontent.com/JohnnWi/homelab-project/main/app-version.json")
    private static let defaultUpdatePage = "https://github.com/JohnnWi/homelab-project/releases"
    private static let updateCheckInterval: TimeInterval = 15 * 60

    // MARK: - Init

    init() {
        let systemLang = Locale.preferredLanguages.first.flatMap { code -> Language? in
            Language(rawValue: String(code.prefix(2)).lowercased())
        } ?? .en
        let savedLang = UserDefaults.standard.string(forKey: Keys.language) ?? systemLang.rawValue
        self.language = Language(rawValue: savedLang) ?? systemLang

        let savedTheme = UserDefaults.standard.string(forKey: Keys.theme)
        self.theme = savedTheme.flatMap(ThemeMode.init) ?? .system

        let savedHidden = UserDefaults.standard.stringArray(forKey: Keys.hiddenServices) ?? []
        self.hiddenServices = Set(savedHidden.compactMap(Self.canonicalServiceRawValue))

        let savedOrder = UserDefaults.standard.stringArray(forKey: Keys.serviceOrder) ?? []
        self.serviceOrder = Self.normalizedServiceOrder(savedOrder.compactMap(Self.serviceType(fromStoredRawValue:)))

        self.biometricEnabled = UserDefaults.standard.bool(forKey: Keys.biometricEnabled)
        self.hasCompletedOnboarding = UserDefaults.standard.bool(forKey: Keys.hasCompletedOnboarding)
        self.homeCyberpunkCardsEnabled = UserDefaults.standard.object(forKey: Keys.homeCyberpunkCardsEnabled) as? Bool ?? false
        let savedAppIcon = UserDefaults.standard.string(forKey: Keys.appIcon)
        self.appIcon = AppIconOption(rawValue: savedAppIcon ?? "") ?? .default
        self.backupRememberSelectionEnabled = UserDefaults.standard.object(forKey: Keys.backupRememberSelectionEnabled) as? Bool ?? true
        let savedBackupSelection = UserDefaults.standard.stringArray(forKey: Keys.backupSelectedServiceTypes) ?? []
        self.backupSelectedServiceTypes = Set(savedBackupSelection.compactMap(Self.serviceType(fromStoredRawValue:)))
        self.dismissedUpdateVersion = UserDefaults.standard.string(forKey: Keys.dismissedUpdateVersion)
        self.dismissedPopupVersion = UserDefaults.standard.string(forKey: Keys.dismissedPopupVersion)
        self.availableUpdateVersion = UserDefaults.standard.string(forKey: Keys.availableUpdateVersion)
        self.availableUpdateURL = UserDefaults.standard.string(forKey: Keys.availableUpdateURL)
        self.availableUpdateChangelog = UserDefaults.standard.string(forKey: Keys.availableUpdateChangelog)

        if let timestamp = UserDefaults.standard.object(forKey: Keys.lastUpdateCheckAt) as? TimeInterval {
            self.lastUpdateCheckAt = Date(timeIntervalSince1970: timestamp)
        } else {
            self.lastUpdateCheckAt = nil
        }

        reconcileCachedUpdateState()
    }

    // MARK: - Service Visibility

    func isServiceHidden(_ type: ServiceType) -> Bool {
        hiddenServices.contains(type.rawValue)
    }

    func toggleServiceVisibility(_ type: ServiceType) {
        if hiddenServices.contains(type.rawValue) {
            hiddenServices.remove(type.rawValue)
        } else {
            hiddenServices.insert(type.rawValue)
        }
    }

    func canMoveService(_ type: ServiceType, offset: Int) -> Bool {
        guard let index = serviceOrder.firstIndex(of: type) else { return false }
        let destination = index + offset
        return serviceOrder.indices.contains(destination)
    }

    func moveService(_ type: ServiceType, offset: Int) {
        guard let index = serviceOrder.firstIndex(of: type) else { return }
        let destination = index + offset
        guard serviceOrder.indices.contains(destination) else { return }
        var updated = serviceOrder
        updated.swapAt(index, destination)
        serviceOrder = updated
    }

    func canMoveService(_ type: ServiceType, offset: Int, within allowedTypes: [ServiceType]) -> Bool {
        let allowedSet = Set(allowedTypes)
        let filtered = serviceOrder.filter { allowedSet.contains($0) }
        guard let index = filtered.firstIndex(of: type) else { return false }
        let destination = index + offset
        return filtered.indices.contains(destination)
    }

    func moveService(_ type: ServiceType, offset: Int, within allowedTypes: [ServiceType]) {
        let allowedSet = Set(allowedTypes)
        let filtered = serviceOrder.filter { allowedSet.contains($0) }
        guard let filteredIndex = filtered.firstIndex(of: type) else { return }
        let filteredDestination = filteredIndex + offset
        guard filtered.indices.contains(filteredDestination) else { return }

        let sourceType = filtered[filteredIndex]
        let destinationType = filtered[filteredDestination]
        guard let sourceGlobal = serviceOrder.firstIndex(of: sourceType),
              let destinationGlobal = serviceOrder.firstIndex(of: destinationType) else {
            return
        }

        var updated = serviceOrder
        updated.swapAt(sourceGlobal, destinationGlobal)
        serviceOrder = updated
    }

    // MARK: - PIN Security

    var isPinSet: Bool {
        KeychainService.loadPin() != nil
    }

    func savePin(_ pin: String) {
        KeychainService.savePin(pin)
    }

    func verifyPin(_ pin: String) -> Bool {
        KeychainService.loadPin() == pin
    }

    func clearSecurity() {
        KeychainService.deletePin()
        biometricEnabled = false
    }

    func setAppIcon(_ icon: AppIconOption) {
        guard appIcon != icon else { return }
        let previousIcon = appIcon
        appIcon = icon

        Task { @MainActor in
            do {
                try await applyAppIcon(icon)
            } catch {
                appIcon = previousIcon
            }
        }
    }

    func syncAppIconWithSystem() {
        guard UIApplication.shared.supportsAlternateIcons else {
            appIcon = .default
            return
        }

        let currentSystemIcon = AppIconOption.fromAlternateIconName(UIApplication.shared.alternateIconName)
        if currentSystemIcon != appIcon {
            appIcon = currentSystemIcon
        }
    }

    func checkForUpdatesIfNeeded(force: Bool = false) async {
        if !force, let lastUpdateCheckAt, Date().timeIntervalSince(lastUpdateCheckAt) < Self.updateCheckInterval {
            return
        }
        guard let url = Self.updateFeedURL else { return }

        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { return }
            let feed = try JSONDecoder().decode(AppVersionFeed.self, from: data)
            lastUpdateCheckAt = Date()
            apply(feed: feed)
        } catch {
            // Keep existing state when update feed is temporarily unreachable.
        }
    }

    func dismissUpdateBanner() {
        guard let availableUpdateVersion else { return }
        dismissedUpdateVersion = availableUpdateVersion
        self.availableUpdateVersion = nil
        self.availableUpdateURL = nil
    }

    func dismissUpdatePopup() {
        guard let availableUpdateVersion else { return }
        dismissedPopupVersion = availableUpdateVersion
        showUpdatePopup = false
    }

    private func apply(feed: AppVersionFeed) {
        let latest = feed.latest.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !latest.isEmpty else {
            availableUpdateVersion = nil
            availableUpdateURL = nil
            availableUpdateChangelog = nil
            showUpdatePopup = false
            return
        }

        let current = appVersion
        guard compareVersions(latest, current) == .orderedDescending else {
            availableUpdateVersion = nil
            availableUpdateURL = nil
            availableUpdateChangelog = nil
            showUpdatePopup = false
            return
        }

        availableUpdateVersion = latest
        availableUpdateURL = feed.iosURL ?? Self.defaultUpdatePage
        availableUpdateChangelog = feed.changelog

        if dismissedUpdateVersion != latest {
            // Banner stays visible
        } else {
            availableUpdateVersion = nil
            availableUpdateURL = nil
            availableUpdateChangelog = nil
        }

        // Popup: show only if not dismissed for this version
        if dismissedPopupVersion != latest && dismissedUpdateVersion != latest {
            showUpdatePopup = true
        }
    }

    private func reconcileCachedUpdateState() {
        guard let latest = availableUpdateVersion?.trimmingCharacters(in: .whitespacesAndNewlines), !latest.isEmpty else {
            availableUpdateVersion = nil
            availableUpdateURL = nil
            availableUpdateChangelog = nil
            showUpdatePopup = false
            return
        }

        if compareVersions(latest, appVersion) != .orderedDescending || dismissedUpdateVersion == latest {
            availableUpdateVersion = nil
            availableUpdateURL = nil
            availableUpdateChangelog = nil
            showUpdatePopup = false
            return
        }

        if availableUpdateURL?.isEmpty != false {
            availableUpdateURL = Self.defaultUpdatePage
        }

        // Restore popup state from cache
        if dismissedPopupVersion != latest {
            showUpdatePopup = true
        }
    }

    private var appVersion: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String
        return (version?.isEmpty == false) ? version! : "0.0.0"
    }

    private func compareVersions(_ lhs: String, _ rhs: String) -> ComparisonResult {
        let left = lhs.split(separator: ".").map { Int($0) ?? 0 }
        let right = rhs.split(separator: ".").map { Int($0) ?? 0 }
        let count = max(left.count, right.count)
        for index in 0..<count {
            let l = index < left.count ? left[index] : 0
            let r = index < right.count ? right[index] : 0
            if l != r {
                return l < r ? .orderedAscending : .orderedDescending
            }
        }
        return .orderedSame
    }

    private func applyAppIcon(_ icon: AppIconOption) async throws {
        guard UIApplication.shared.supportsAlternateIcons else { return }
        let requestedName = icon.alternateIconName
        if UIApplication.shared.alternateIconName == requestedName { return }

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            UIApplication.shared.setAlternateIconName(requestedName) { error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume(returning: ())
                }
            }
        }
    }

    private static func normalizedServiceOrder(_ order: [ServiceType]) -> [ServiceType] {
        var seen = Set<ServiceType>()
        let unique = order.filter { seen.insert($0).inserted }
        let missing = ServiceType.allCases.filter { !unique.contains($0) }
        return unique + missing
    }

    private static func serviceType(fromStoredRawValue rawValue: String) -> ServiceType? {
        ServiceType.fromStoredRawValue(rawValue)
    }

    private static func canonicalServiceRawValue(_ rawValue: String) -> String? {
        let trimmed = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        return ServiceType.fromStoredRawValue(trimmed)?.rawValue ?? trimmed
    }
}

private struct AppVersionFeed: Decodable {
    let latest: String
    let changelog: String?
    let iosURL: String?
    let androidURL: String?

    enum CodingKeys: String, CodingKey {
        case latest
        case changelog
        case iosURL = "ios_url"
        case androidURL = "android_url"
    }
}

enum AppIconOption: String, CaseIterable {
    case `default`
    case dark
    case clearLight
    case clearDark
    case tintedLight
    case tintedDark

    var alternateIconName: String? {
        switch self {
        case .default:
            return nil
        case .dark:
            return "AppIconDark"
        case .clearLight:
            return "AppIconClearLight"
        case .clearDark:
            return "AppIconClearDark"
        case .tintedLight:
            return "AppIconTintedLight"
        case .tintedDark:
            return "AppIconTintedDark"
        }
    }

    static func fromAlternateIconName(_ name: String?) -> AppIconOption {
        switch name {
        case "AppIconDark":
            return .dark
        case "AppIconClearLight":
            return .clearLight
        case "AppIconClearDark":
            return .clearDark
        case "AppIconTintedLight":
            return .tintedLight
        case "AppIconTintedDark":
            return .tintedDark
        default:
            return .default
        }
    }

    var previewAssetName: String {
        switch self {
        case .default:
            return "AppIconPreviewDefault"
        case .dark:
            return "AppIconPreviewDark"
        case .clearLight:
            return "AppIconPreviewClearLight"
        case .clearDark:
            return "AppIconPreviewClearDark"
        case .tintedLight:
            return "AppIconPreviewTintedLight"
        case .tintedDark:
            return "AppIconPreviewTintedDark"
        }
    }
}

// MARK: - Language

enum Language: String, CaseIterable, Codable {
    case it, en, fr, es, de

    var displayName: String {
        switch self {
        case .it: return "Italiano"
        case .en: return "English"
        case .fr: return "Français"
        case .es: return "Español"
        case .de: return "Deutsch"
        }
    }

    var flagEmoji: String {
        switch self {
        case .it: return "🇮🇹"
        case .en: return "🇬🇧"
        case .fr: return "🇫🇷"
        case .es: return "🇪🇸"
        case .de: return "🇩🇪"
        }
    }
}

// MARK: - ThemeMode

enum ThemeMode: String, CaseIterable, Codable {
    case light, dark, system
}
