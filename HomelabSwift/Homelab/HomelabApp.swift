import SwiftUI

@main
struct HomelabApp: App {
    @State private var servicesStore = ServicesStore()
    @State private var settingsStore = SettingsStore()
    @State private var localizer = Localizer()
    @State private var isUnlocked = false
    @State private var needsSetup = false
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            Group {
                if needsSetup {
                    PinSetupView {
                        needsSetup = false
                        isUnlocked = true
                    }
                } else if settingsStore.isPinSet && !isUnlocked {
                    LockScreenView {
                        isUnlocked = true
                    }
                } else {
                    ContentView()
                }
            }
            .environment(servicesStore)
            .environment(settingsStore)
            .environment(localizer)
            .task {
                localizer.language = settingsStore.language
                needsSetup = !settingsStore.hasCompletedOnboarding
                if needsSetup {
                    isUnlocked = false
                }
                await servicesStore.initialize()
            }
            .onChange(of: scenePhase) { _, newPhase in
                guard settingsStore.isPinSet else { return }
                switch newPhase {
                case .background:
                    settingsStore.lastBackgroundDate = Date()
                    servicesStore.stopPeriodicHealthChecks()
                case .active:
                    servicesStore.startPeriodicHealthChecks()
                    Task { await servicesStore.checkAllReachability() }
                    if settingsStore.isPinSet {
                        if let bg = settingsStore.lastBackgroundDate {
                            let elapsed = Date().timeIntervalSince(bg)
                            if elapsed > 60 {
                                isUnlocked = false
                            }
                        } else if !isUnlocked {
                            // First launch or killed: stay locked
                            isUnlocked = false
                        }
                    }
                    settingsStore.lastBackgroundDate = nil
                default:
                    break
                }
            }
        }
    }
}
