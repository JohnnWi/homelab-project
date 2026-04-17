import SwiftUI
import LocalAuthentication

struct LockScreenView: View {
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    var onUnlock: () -> Void

    @State private var pin = ""
    @State private var errorMessage: String? = nil
    @State private var attempts = 0
    @State private var lockoutSeconds = 0
    @State private var lockoutTask: Task<Void, Never>?
    @State private var lockoutDeadline: Date?
    @State private var hasTriggedBiometric = false
    private var palette: SecurityPalette { .resolve(for: colorScheme) }

    private static let lockoutThreshold = 5

    var body: some View {
        ZStack {
            SecurityBackgroundView(palette: palette)

            VStack(spacing: 0) {
                Spacer(minLength: 40)

                PinEntryView(
                    pin: $pin,
                    title: localizer.t.securityEnterPin,
                    subtitle: localizer.t.securityEnterPinDesc,
                    errorMessage: errorMessage,
                    onComplete: { enteredPin in
                        verifyPin(enteredPin)
                    },
                    showBiometric: settingsStore.biometricEnabled && !lockoutInProgress,
                    onBiometricTap: {
                        authenticateWithBiometric()
                    },
                    lockoutSeconds: lockoutSeconds
                )

                Spacer()
            }
        }
        .onAppear {
            if settingsStore.biometricEnabled && !hasTriggedBiometric && !lockoutInProgress {
                hasTriggedBiometric = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    authenticateWithBiometric()
                }
            }
        }
        .onDisappear {
            lockoutTask?.cancel()
            lockoutTask = nil
            lockoutDeadline = nil
        }
    }

    private var lockoutInProgress: Bool { lockoutSeconds > 0 }

    private func calculateLockoutDuration(attempts: Int) -> Int {
        guard attempts > Self.lockoutThreshold else { return 0 }
        let excess = attempts - Self.lockoutThreshold
        // 1, 2, 4, 8, 16 seconds (capped at 16)
        return min(1 << (excess - 1), 16)
    }

    private func startLockout(seconds: Int) {
        lockoutTask?.cancel()
        let deadline = Date().addingTimeInterval(TimeInterval(seconds))
        lockoutDeadline = deadline
        lockoutSeconds = seconds

        lockoutTask = Task { @MainActor in
            while !Task.isCancelled {
                let remaining = max(0, Int(ceil(deadline.timeIntervalSinceNow)))
                lockoutSeconds = remaining

                if remaining <= 0 {
                    lockoutTask = nil
                    lockoutDeadline = nil
                    lockoutSeconds = 0
                    pin = ""
                    break
                }

                try? await Task.sleep(for: .seconds(1))
            }
        }
    }

    private func verifyPin(_ enteredPin: String) {
        guard !lockoutInProgress else { return }

        if settingsStore.verifyPin(enteredPin) {
            lockoutTask?.cancel()
            lockoutTask = nil
            lockoutDeadline = nil
            lockoutSeconds = 0
            HapticManager.success()
            onUnlock()
        } else {
            attempts += 1
            HapticManager.error()
            errorMessage = localizer.t.securityWrongPin
            pin = ""
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                errorMessage = nil
            }

            let duration = calculateLockoutDuration(attempts: attempts)
            if duration > 0 {
                startLockout(seconds: duration)
            }
        }
    }

    private func authenticateWithBiometric() {
        guard !lockoutInProgress else { return }

        let context = LAContext()
        var error: NSError?

        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            return
        }

        context.evaluatePolicy(
            .deviceOwnerAuthenticationWithBiometrics,
            localizedReason: localizer.t.securityBiometricReason
        ) { success, _ in
            DispatchQueue.main.async {
                if success {
                    lockoutTask?.cancel()
                    lockoutTask = nil
                    lockoutDeadline = nil
                    lockoutSeconds = 0
                    HapticManager.success()
                    onUnlock()
                }
            }
        }
    }
}
