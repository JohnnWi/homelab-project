import SwiftUI
import LocalAuthentication

struct SecurityPalette {
    let backgroundTop: Color
    let backgroundBottom: Color
    let accent: Color
    let accentSoft: Color
    let primaryText: Color
    let secondaryText: Color
    let dotEmpty: Color
    let keypadFill: Color
    let keypadStroke: Color
    let keypadText: Color
    let iconFill: Color
    let iconStroke: Color
}

extension SecurityPalette {
    static func resolve(for colorScheme: ColorScheme) -> SecurityPalette {
        if colorScheme == .dark {
            return SecurityPalette(
                backgroundTop: Color(hex: "#040814"),
                backgroundBottom: Color(hex: "#18253E"),
                accent: Color(hex: "#12D6B3"),
                accentSoft: Color(hex: "#1D314F"),
                primaryText: .white,
                secondaryText: Color.white.opacity(0.62),
                dotEmpty: Color.white.opacity(0.12),
                keypadFill: Color.white.opacity(0.075),
                keypadStroke: Color(hex: "#35507B").opacity(0.72),
                keypadText: Color(hex: "#12D6B3"),
                iconFill: Color.white.opacity(0.085),
                iconStroke: Color.white.opacity(0.12)
            )
        }

        return SecurityPalette(
            backgroundTop: Color(hex: "#F3FAFF"),
            backgroundBottom: Color(hex: "#DCEEFE"),
            accent: Color(hex: "#0F9F8C"),
            accentSoft: Color(hex: "#D9F5EF"),
            primaryText: Color(hex: "#0F172A"),
            secondaryText: Color(hex: "#475569"),
            dotEmpty: Color(hex: "#B9CBD9"),
            keypadFill: Color.white.opacity(0.9),
            keypadStroke: Color(hex: "#B7CFDF"),
            keypadText: Color(hex: "#0F9F8C"),
            iconFill: Color.white.opacity(0.72),
            iconStroke: Color(hex: "#B7CFDF").opacity(0.9)
        )
    }
}

struct SecurityBackgroundView: View {
    let palette: SecurityPalette

    var body: some View {
        LinearGradient(
            colors: [palette.backgroundTop, palette.backgroundBottom],
            startPoint: .top,
            endPoint: .bottom
        )
        .overlay(alignment: .topTrailing) {
            Circle()
                .fill(palette.accent.opacity(0.08))
                .frame(width: 220, height: 220)
                .blur(radius: 24)
                .offset(x: 80, y: -20)
        }
        .overlay(alignment: .bottomLeading) {
            Circle()
                .fill(palette.accent.opacity(0.06))
                .frame(width: 260, height: 260)
                .blur(radius: 36)
                .offset(x: -80, y: 80)
        }
        .ignoresSafeArea()
    }
}

struct PinEntryView: View {
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme
    @Binding var pin: String
    let title: String
    let subtitle: String
    var errorMessage: String? = nil
    var onComplete: ((String) -> Void)?
    var showBiometric: Bool = false
    var onBiometricTap: (() -> Void)? = nil
    var lockoutSeconds: Int = 0

    private let pinLength = 6
    private let buttonSize: CGFloat = 72
    private var palette: SecurityPalette { .resolve(for: colorScheme) }

    private var isLockedOut: Bool { lockoutSeconds > 0 }

    private var lockoutMessage: String {
        String(format: localizer.t.securityLockoutMessage, lockoutSeconds)
    }

    var body: some View {
        VStack(spacing: 28) {
            Spacer(minLength: 40)

            // Header
            VStack(spacing: 12) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 36, weight: .medium))
                    .foregroundStyle(palette.primaryText)
                    .frame(width: 72, height: 72)
                    .background(
                        Circle()
                            .fill(palette.iconFill)
                            .overlay(
                                Circle()
                                    .stroke(palette.iconStroke, lineWidth: 1.2)
                            )
                    )

                Text(title)
                    .font(.title2.bold())
                    .foregroundStyle(palette.primaryText)

                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(palette.secondaryText)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            // PIN dots
            HStack(spacing: 14) {
                ForEach(0..<pinLength, id: \.self) { index in
                    Circle()
                        .fill(index < pin.count ? palette.accent : palette.dotEmpty)
                        .frame(width: 14, height: 14)
                        .scaleEffect(index < pin.count ? 1.2 : 1.0)
                        .animation(.spring(duration: 0.2), value: pin.count)
                }
            }
            .padding(.vertical, 4)
            .modifier(ShakeModifier(shaking: errorMessage != nil))

            // Error message / Lockout timer
            if isLockedOut {
                HStack(spacing: 6) {
                    Image(systemName: "lock.slash.fill")
                        .font(.caption2)
                    Text(lockoutMessage)
                        .font(.caption.weight(.medium))
                }
                .foregroundStyle(AppTheme.danger)
                .transition(.opacity)
            } else if let error = errorMessage {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(AppTheme.danger)
                    .transition(.opacity)
            }

            Spacer(minLength: 16)

            // Number pad
            VStack(spacing: 16) {
                ForEach(0..<3, id: \.self) { row in
                    HStack(spacing: 24) {
                        ForEach(1...3, id: \.self) { col in
                            let number = row * 3 + col
                            numberButton(String(number))
                        }
                    }
                }

                // Last row: biometric, 0, delete
                HStack(spacing: 24) {
                    if showBiometric {
                        Button {
                            onBiometricTap?()
                        } label: {
                            let context = LAContext()
                            let icon = context.biometryType == .faceID ? "faceid" : "touchid"
                            Image(systemName: icon)
                                .font(.title2)
                                .foregroundStyle(isLockedOut ? palette.keypadText.opacity(0.3) : palette.keypadText)
                                .frame(width: buttonSize, height: buttonSize)
                        }
                        .background(keypadCircle.opacity(isLockedOut ? 0.5 : 1.0))
                        .disabled(isLockedOut)
                    } else {
                        Color.clear
                            .frame(width: buttonSize, height: buttonSize)
                    }

                    numberButton("0")

                    Button {
                        if !pin.isEmpty {
                            pin.removeLast()
                        }
                        HapticManager.light()
                    } label: {
                        Image(systemName: "delete.left.fill")
                            .font(.title2)
                            .foregroundStyle(isLockedOut ? palette.keypadText.opacity(0.3) : palette.keypadText)
                            .frame(width: buttonSize, height: buttonSize)
                    }
                    .background(keypadCircle.opacity(isLockedOut ? 0.5 : 1.0))
                    .disabled(isLockedOut)
                    .accessibilityLabel(localizer.t.delete)
                }
            }
            .padding(.bottom, 16)
        }
    }

    private func numberButton(_ digit: String) -> some View {
        Button {
            guard !isLockedOut, pin.count < pinLength else { return }
            pin.append(digit)
            HapticManager.light()
            if pin.count == pinLength {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    onComplete?(pin)
                }
            }
        } label: {
            Text(digit)
                .font(.title.bold())
                .foregroundStyle(isLockedOut ? palette.keypadText.opacity(0.3) : palette.keypadText)
                .frame(width: buttonSize, height: buttonSize)
        }
        .background(keypadCircle.opacity(isLockedOut ? 0.5 : 1.0))
        .disabled(isLockedOut)
    }

    private var keypadCircle: some View {
        Circle()
            .fill(palette.keypadFill)
            .overlay(
                Circle()
                    .stroke(palette.keypadStroke, lineWidth: 1.1)
            )
    }
}

struct ShakeModifier: ViewModifier {
    var shaking: Bool
    @State private var shakeOffset: CGFloat = 0

    func body(content: Content) -> some View {
        content
            .offset(x: shakeOffset)
            .onChange(of: shaking) { _, newValue in
                if newValue {
                    withAnimation(.easeInOut(duration: 0.06).repeatCount(3, autoreverses: true)) {
                        shakeOffset = 8
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.36) {
                        withAnimation(.spring(response: 0.2, dampingFraction: 0.8)) {
                            shakeOffset = 0
                        }
                    }
                }
            }
    }
}
