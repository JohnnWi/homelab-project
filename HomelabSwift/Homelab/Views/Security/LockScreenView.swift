import SwiftUI
import LocalAuthentication

struct LockScreenView: View {
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer

    var onUnlock: () -> Void

    @State private var pin = ""
    @State private var errorMessage: String? = nil
    @State private var attempts = 0
    @State private var hasTriggedBiometric = false

    var body: some View {
        ZStack {
            AppTheme.premiumGradient()

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
                    showBiometric: settingsStore.biometricEnabled,
                    onBiometricTap: {
                        authenticateWithBiometric()
                    }
                )

                Spacer()
            }
        }
        .onAppear {
            if settingsStore.biometricEnabled && !hasTriggedBiometric {
                hasTriggedBiometric = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    authenticateWithBiometric()
                }
            }
        }
    }

    private func verifyPin(_ enteredPin: String) {
        if settingsStore.verifyPin(enteredPin) {
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
        }
    }

    private func authenticateWithBiometric() {
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
                    HapticManager.success()
                    onUnlock()
                }
            }
        }
    }
}
