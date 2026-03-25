import SwiftUI
import LocalAuthentication

// MARK: - Auth-Gated Wrapper

/// Wraps ConfiguredServicesView with PIN/biometric authentication.
/// Requires auth every time the view appears (enter or return from background).
struct AuthGatedConfiguredServicesView: View {
    @Environment(SettingsStore.self) private var settingsStore

    @State private var isUnlocked = false

    var body: some View {
        ZStack {
            if settingsStore.isPinSet && !isUnlocked {
                LockScreenView {
                    isUnlocked = true
                }
                .toolbar(.hidden, for: .tabBar)
                .navigationBarBackButtonHidden(true)
            } else {
                ConfiguredServicesView()
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if !settingsStore.isPinSet {
                isUnlocked = true
            }
        }
        .onDisappear {
            isUnlocked = false
        }
    }
}

// MARK: - Service Editor Context

struct ServiceEditorContext: Identifiable {
    let serviceType: ServiceType
    let instanceId: UUID?

    var id: String {
        "\(serviceType.rawValue)-\(instanceId?.uuidString ?? "new")"
    }
}

struct ConfiguredServicesView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer

    @State private var showDeleteInstance: ServiceInstance? = nil
    @State private var showInstanceEditor: ServiceEditorContext? = nil

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()

            ScrollView {
                GlassGroup(spacing: 24) {
                    VStack(alignment: .leading, spacing: 8) {
                        VStack(spacing: 16) {
                            ForEach(settingsStore.serviceOrder) { type in
                                serviceSection(type)
                            }
                        }
                    }
                }
                .padding(16)
                .padding(.bottom, 32)
            }
        }
        .navigationTitle(localizer.t.settingsConfiguredServices)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $showInstanceEditor) { context in
            ServiceLoginView(serviceType: context.serviceType, existingInstanceId: context.instanceId)
        }
        .alert(localizer.t.settingsDeleteInstanceTitle, isPresented: .init(
            get: { showDeleteInstance != nil },
            set: { if !$0 { showDeleteInstance = nil } }
        )) {
            Button(localizer.t.cancel, role: .cancel) { }
            Button(localizer.t.delete, role: .destructive) {
                if let instance = showDeleteInstance {
                    HapticManager.medium()
                    servicesStore.deleteInstance(id: instance.id)
                }
            }
        } message: {
            Text(
                [
                    showDeleteInstance?.displayLabel ?? "",
                    localizer.t.settingsDeleteInstanceMessage
                ]
                .filter { !$0.isEmpty }
                .joined(separator: "\n")
            )
        }
    }

    @ViewBuilder
    private func serviceSection(_ type: ServiceType) -> some View {
        let instances = servicesStore.instances(for: type)
        let preferredId = servicesStore.preferredInstance(for: type)?.id
        
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                ServiceIconView(type: type, size: 20)
                    .frame(width: 32, height: 32)
                    .background(type.colors.bg, in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        Text(type.displayName)
                            .font(.body)
                            .fontWeight(.bold)
                        if settingsStore.isServiceHidden(type) {
                            Text(localizer.t.settingsHiddenBadge)
                                .font(.caption2.bold())
                                .foregroundStyle(.secondary)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color.secondary.opacity(0.12), in: Capsule())
                        }
                    }
                    Text(
                        instances.isEmpty
                            ? localizer.t.settingsNotConnected
                            : "\(instances.count) \(instances.count == 1 ? localizer.t.settingsInstanceSingular : localizer.t.settingsInstancePlural)"
                    )
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                Button {
                    settingsStore.toggleServiceVisibility(type)
                    HapticManager.light()
                } label: {
                    Image(systemName: settingsStore.isServiceHidden(type) ? "eye.slash" : "eye")
                        .font(.caption)
                        .foregroundStyle(settingsStore.isServiceHidden(type) ? .secondary : AppTheme.accent)
                        .frame(width: 32, height: 32)
                        .background(settingsStore.isServiceHidden(type) ? Color.secondary.opacity(0.1) : AppTheme.accent.opacity(0.1))
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(settingsStore.isServiceHidden(type) ? localizer.t.settingsShowService : localizer.t.settingsHideService)

                HStack(spacing: 6) {
                    Button {
                        settingsStore.moveService(type, offset: -1)
                        HapticManager.light()
                    } label: {
                        Image(systemName: "arrow.up")
                            .font(.caption.bold())
                            .foregroundStyle(settingsStore.canMoveService(type, offset: -1) ? AppTheme.accent : .secondary)
                            .frame(width: 32, height: 32)
                            .background(Color.secondary.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(localizer.t.settingsMoveUp)
                    .disabled(!settingsStore.canMoveService(type, offset: -1))

                    Button {
                        settingsStore.moveService(type, offset: 1)
                        HapticManager.light()
                    } label: {
                        Image(systemName: "arrow.down")
                            .font(.caption.bold())
                            .foregroundStyle(settingsStore.canMoveService(type, offset: 1) ? AppTheme.accent : .secondary)
                            .frame(width: 32, height: 32)
                            .background(Color.secondary.opacity(0.1))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(localizer.t.settingsMoveDown)
                    .disabled(!settingsStore.canMoveService(type, offset: 1))
                }
            }

            if instances.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: "plus.circle")
                        .foregroundStyle(.secondary)
                    Text(localizer.t.settingsNoInstances)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 14)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            } else {
                ForEach(instances) { instance in
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 12) {
                            ServiceIconView(type: type, size: 22)
                                .frame(width: 36, height: 36)
                                .background(type.colors.bg, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
                            
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 8) {
                                    Text(instance.displayLabel)
                                        .font(.subheadline.weight(.semibold))
                                    if preferredId == instance.id {
                                        Text(localizer.t.badgeDefault)
                                            .font(.caption2.bold())
                                            .foregroundStyle(type.colors.primary)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 3)
                                            .background(type.colors.primary.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                                    }
                                }
                                Text(instance.url)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                                if let fallback = instance.fallbackUrl, !fallback.isEmpty {
                                    Text("\(localizer.t.settingsFallbackPrefix): \(fallback)")
                                        .font(.caption2)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                }
                            }

                            Spacer()
                        }

                        FlowLayout(spacing: 8) {
                            if preferredId != instance.id {
                                Button {
                                    HapticManager.light()
                                    servicesStore.setPreferredInstance(id: instance.id, for: type)
                                } label: {
                                    Text(localizer.t.settingsSetDefault)
                                        .multilineTextAlignment(.center)
                                        .fixedSize(horizontal: false, vertical: true)
                                }
                                .font(.caption.bold())
                                .buttonStyle(.bordered)
                            }

                            Button {
                                showInstanceEditor = ServiceEditorContext(serviceType: type, instanceId: instance.id)
                            } label: {
                                Text(localizer.t.actionEdit)
                                    .multilineTextAlignment(.center)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .font(.caption.bold())
                            .buttonStyle(.bordered)
                            .tint(type.colors.primary)

                            Button(role: .destructive) {
                                showDeleteInstance = instance
                            } label: {
                                Text(localizer.t.delete)
                                    .multilineTextAlignment(.center)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .font(.caption.bold())
                            .buttonStyle(.bordered)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 12)
                    .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }

            Button {
                showInstanceEditor = ServiceEditorContext(serviceType: type, instanceId: nil)
            } label: {
                HStack {
                    Image(systemName: "plus.circle.fill")
                        .foregroundStyle(type.colors.primary)
                    Text(localizer.t.settingsAddInstance)
                        .fontWeight(.semibold)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .foregroundStyle(type.colors.primary)
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(type.colors.primary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .glassCard()
    }
}
