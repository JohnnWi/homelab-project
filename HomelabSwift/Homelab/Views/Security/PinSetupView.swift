import SwiftUI
import LocalAuthentication

// MARK: - PIN Setup (shown on first launch)

struct PinSetupView: View {
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer

    var onComplete: () -> Void

    @State private var step: SetupStep = .welcome
    @State private var firstPin = ""
    @State private var confirmPin = ""
    @State private var errorMessage: String? = nil

    private enum SetupStep {
        case welcome, ask, create, confirm
    }

    var body: some View {
        ZStack {
            AppTheme.premiumGradient()

            VStack(spacing: 0) {
                Spacer(minLength: 40)

                Group {
                    switch step {
                    case .welcome:
                        welcomeView
                            .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .move(edge: .leading).combined(with: .opacity)))

                    case .ask:
                        askView
                            .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .move(edge: .leading).combined(with: .opacity)))

                    case .create:
                        PinEntryView(
                            pin: $firstPin,
                            title: localizer.t.securitySetupPin,
                            subtitle: localizer.t.securitySetupPinDesc,
                            errorMessage: nil,
                            onComplete: { _ in
                                withAnimation {
                                    step = .confirm
                                }
                            }
                        )
                        .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .move(edge: .leading).combined(with: .opacity)))

                    case .confirm:
                        PinEntryView(
                            pin: $confirmPin,
                            title: localizer.t.securityConfirmPin,
                            subtitle: localizer.t.securityConfirmPinDesc,
                            errorMessage: errorMessage,
                            onComplete: { pin in
                                if pin == firstPin {
                                    settingsStore.savePin(pin)
                                    HapticManager.success()
                                    checkAndEnableBiometric()
                                } else {
                                    errorMessage = localizer.t.securityPinMismatch
                                    HapticManager.error()
                                    confirmPin = ""
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                        errorMessage = nil
                                    }
                                }
                            }
                        )
                        .transition(.asymmetric(insertion: .move(edge: .trailing).combined(with: .opacity), removal: .move(edge: .leading).combined(with: .opacity)))
                    }
                }

                Spacer()
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.8), value: step)
        }
    }

    // MARK: - Views

    private var welcomeView: some View {
        VStack(spacing: 32) {
            Spacer(minLength: 20)

            ZStack {
                Circle()
                    .fill(AppTheme.primary.opacity(0.15))
                    .frame(width: 200, height: 200)
                    .blur(radius: 50)

                Image("AppIconImage")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 140, height: 140)
                    .clipShape(RoundedRectangle(cornerRadius: 32))
                    .shadow(color: AppTheme.primary.opacity(0.4), radius: 25, x: 0, y: 15)
            }

            VStack(spacing: 32) {
                VStack(spacing: 16) {
                    Text(localizer.t.onboardingWelcome)
                        .font(.system(size: 48, weight: .bold, design: .rounded))
                        .foregroundStyle(.primary)

                    Text(localizer.t.onboardingWelcomeDesc)
                        .font(.title3)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }

                Button {
                    HapticManager.light()
                    withAnimation {
                        step = .ask
                    }
                } label: {
                    Text(localizer.t.actionResume.replacingOccurrences(of: "Resume", with: "Continue"))
                        .font(.headline.bold())
                        .foregroundStyle(.white)
                        .frame(width: 240)
                        .padding(.vertical, 18)
                        .background(
                            RoundedRectangle(cornerRadius: 18, style: .continuous)
                                .fill(AppTheme.accent)
                                .shadow(color: AppTheme.accent.opacity(0.3), radius: 10, y: 5)
                        )
                }
            }

            Spacer()
            Spacer()
        }
    }

    private var askView: some View {
        VStack(spacing: 32) {
            Spacer(minLength: 20)

            Image(systemName: "lock.shield.fill")
                .font(.system(size: 80))
                .foregroundStyle(AppTheme.accent)
                .frame(width: 140, height: 140)
                .glassEffect(.regular, in: .circle)

            VStack(spacing: 32) {
                VStack(spacing: 16) {
                    Text(localizer.t.securityTitle)
                        .font(.system(size: 36, weight: .bold, design: .rounded))
                        .foregroundStyle(.primary)

                    Text(localizer.t.onboardingAskPin)
                        .font(.title3)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }

                VStack(spacing: 20) {
                    Button {
                        HapticManager.light()
                        withAnimation {
                            step = .create
                        }
                    } label: {
                        Text(localizer.t.onboardingAskPinYes)
                            .font(.headline.bold())
                            .foregroundStyle(.white)
                            .frame(width: 240)
                            .padding(.vertical, 18)
                            .background(
                                RoundedRectangle(cornerRadius: 18, style: .continuous)
                                    .fill(AppTheme.accent)
                                    .shadow(color: AppTheme.accent.opacity(0.3), radius: 10, y: 5)
                            )
                    }

                    Button {
                        HapticManager.light()
                        completeSetup()
                    } label: {
                        Text(localizer.t.onboardingAskPinNo)
                            .font(.subheadline.bold())
                            .foregroundStyle(.secondary)
                            .padding(.vertical, 8)
                    }
                }
            }

            Spacer()
            Spacer()
        }
    }

    // MARK: - Helpers

    private func checkAndEnableBiometric() {
        let context = LAContext()
        var error: NSError?

        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            let reason = localizer.t.securityBiometricReason
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, authenticationError in
                DispatchQueue.main.async {
                    if success {
                        settingsStore.biometricEnabled = true
                    } else {
                        settingsStore.biometricEnabled = false
                    }
                    completeSetup()
                }
            }
        } else {
            completeSetup()
        }
    }

    private func completeSetup() {
        settingsStore.hasCompletedOnboarding = true
        onComplete()
    }
}
