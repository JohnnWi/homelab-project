import SwiftUI
import WebKit

struct ProxmoxConsoleView: View {
    let instanceId: UUID
    let nodeName: String
    let vmid: Int
    let guestType: ProxmoxGuestType

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var consoleSession: ProxmoxConsoleSession?
    @State private var consoleError: String?
    @State private var isLoading = true
    @State private var allowSelfSigned = false

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    var body: some View {
        VStack(spacing: 0) {
            if let session = consoleSession {
                ProxmoxWebView(session: session, isLoading: $isLoading, allowSelfSigned: allowSelfSigned)
                    .ignoresSafeArea(edges: .bottom)
            } else if let consoleError {
                VStack(spacing: 16) {
                    Image(systemName: "terminal")
                        .font(.largeTitle)
                        .foregroundStyle(AppTheme.textMuted)
                    Text(localizer.t.proxmoxConsole)
                        .font(.headline)
                    Text(consoleError)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 320)
                    Button {
                        Task { await loadConsoleURL() }
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.clockwise")
                            Text(localizer.t.proxmoxRetry)
                                .fontWeight(.semibold)
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .background(proxmoxColor, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .foregroundStyle(.white)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                VStack(spacing: 16) {
                    Image(systemName: "terminal.fill")
                        .font(.largeTitle)
                        .foregroundStyle(AppTheme.textMuted)
                    Text(localizer.t.proxmoxConsoleLoading)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                    ProgressView()
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }

            if isLoading && consoleSession != nil {
                ProgressView()
                    .padding()
            }
        }
        .navigationTitle(localizer.t.proxmoxConsole)
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await loadConsoleURL()
        }
        .task(id: consoleSession?.cookieValue) {
            // Auto-refresh console session every 90 minutes (cookie expires ~2 hours)
            guard consoleSession != nil else { return }
            try? await Task.sleep(for: .seconds(90 * 60))
            guard !Task.isCancelled else { return }
            await refreshConsoleSession()
        }
    }

    private func loadConsoleURL() async {
        isLoading = true
        consoleError = nil

        guard let instance = servicesStore.instance(id: instanceId),
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
            consoleError = localizer.t.proxmoxClientNotConfigured
            isLoading = false
            return
        }

        allowSelfSigned = instance.allowSelfSigned

        do {
            let type = guestType == .qemu ? "kvm" : "lxc"
            consoleSession = try await client.consoleSession(node: nodeName, vmid: vmid, type: type)
            consoleError = nil
        } catch {
            consoleSession = nil
            consoleError = resolveConsoleError(error)
            isLoading = false
        }
    }

    private func refreshConsoleSession() async {
        guard let _ = servicesStore.instance(id: instanceId),
              let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
            return
        }

        do {
            let type = guestType == .qemu ? "kvm" : "lxc"
            let newSession = try await client.consoleSession(node: nodeName, vmid: vmid, type: type)
            await MainActor.run {
                consoleSession = newSession
            }
        } catch {
            // Silently fail - user will see error on next interaction
        }
    }

    private func resolveConsoleError(_ error: Error) -> String {
        let message = error.localizedDescription

        if message.contains("cookie") || message.contains("session") {
            return String(format: localizer.t.proxmoxConsoleCookieError, error.localizedDescription)
        }
        if message.contains("401") || message.contains("unauthorized") {
            return localizer.t.proxmoxConsoleAuthError
        }
        if message.contains("certificate") || message.contains("SSL") {
            return localizer.t.proxmoxConsoleCertError
        }
        return String(format: localizer.t.proxmoxConsoleGenericError, error.localizedDescription)
    }
}

// MARK: - WebView Wrapper

private struct ProxmoxWebView: UIViewRepresentable {
    let session: ProxmoxConsoleSession
    @Binding var isLoading: Bool
    let allowSelfSigned: Bool

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.scrollView.isScrollEnabled = true
        webView.scrollView.bounces = false
        webView.allowsBackForwardNavigationGestures = false
        loadConsole(in: webView)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        if webView.url != session.url {
            loadConsole(in: webView)
        }
    }

    private func loadConsole(in webView: WKWebView) {
        // Build cookie properties with proper domain handling
        var domain = session.cookieDomain

        // Ensure domain starts with "." for cross-domain cookies
        if !domain.hasPrefix(".") && !domain.hasPrefix("localhost") {
            domain = ".\(domain)"
        }

        guard let cookie = HTTPCookie(properties: [
            .domain: domain,
            .path: "/",
            .name: session.cookieName,
            .value: session.cookieValue,
            .secure: session.isSecure ? "TRUE" : "FALSE",
            .expires: Date().addingTimeInterval(3600) // 1 hour expiry
        ]) else {
            // Cookie creation failed - show error instead of loading without auth
            Task { @MainActor in
                isLoading = false
            }
            return
        }

        webView.configuration.websiteDataStore.httpCookieStore.setCookie(cookie) {
            webView.load(URLRequest(url: session.url))
        }
    }

    @MainActor
    final class Coordinator: NSObject, WKNavigationDelegate {
        let parent: ProxmoxWebView

        init(_ parent: ProxmoxWebView) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.isLoading = false
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
        }

        // Handle certificate validation based on instance settings
        func webView(
            _ webView: WKWebView,
            didReceive challenge: URLAuthenticationChallenge,
            completionHandler: @escaping @MainActor (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
                  let trust = challenge.protectionSpace.serverTrust else {
                completionHandler(.performDefaultHandling, nil)
                return
            }

            // Only bypass certificate validation if explicitly allowed
            if parent.allowSelfSigned {
                completionHandler(.useCredential, URLCredential(trust: trust))
            } else {
                // Perform default certificate validation
                completionHandler(.performDefaultHandling, nil)
            }
        }
    }
}
