import SwiftUI
import LocalAuthentication

struct PinEntryView: View {
    @Binding var pin: String
    let title: String
    let subtitle: String
    var errorMessage: String? = nil
    var onComplete: ((String) -> Void)?
    var showBiometric: Bool = false
    var onBiometricTap: (() -> Void)? = nil

    private let pinLength = 6
    private let buttonSize: CGFloat = 72

    var body: some View {
        VStack(spacing: 28) {
            Spacer(minLength: 40)

            // Header
            VStack(spacing: 12) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 36, weight: .medium))
                    .foregroundStyle(.primary)
                    .frame(width: 72, height: 72)
                    .glassEffect(.regular, in: .circle)

                Text(title)
                    .font(.title2.bold())
                    .foregroundStyle(.primary)

                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            // PIN dots
            HStack(spacing: 14) {
                ForEach(0..<pinLength, id: \.self) { index in
                    Circle()
                        .fill(index < pin.count ? AppTheme.accent : Color(.tertiarySystemFill))
                        .frame(width: 14, height: 14)
                        .scaleEffect(index < pin.count ? 1.2 : 1.0)
                        .animation(.spring(duration: 0.2), value: pin.count)
                }
            }
            .padding(.vertical, 4)
            .modifier(ShakeModifier(shaking: errorMessage != nil))

            // Error message
            if let error = errorMessage {
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
                                .foregroundStyle(.primary)
                                .frame(width: buttonSize, height: buttonSize)
                        }
                        .glassEffect(.regular, in: .circle)
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
                            .foregroundStyle(.primary)
                            .frame(width: buttonSize, height: buttonSize)
                    }
                    .glassEffect(.regular, in: .circle)
                }
            }
            .padding(.bottom, 16)
        }
    }

    private func numberButton(_ digit: String) -> some View {
        Button {
            guard pin.count < pinLength else { return }
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
                .foregroundStyle(.primary)
                .frame(width: buttonSize, height: buttonSize)
        }
        .glassEffect(.regular, in: .circle)
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
                    withAnimation(.default.repeatCount(4, autoreverses: true).speed(6)) {
                        shakeOffset = 10
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                        shakeOffset = 0
                    }
                }
            }
    }
}
