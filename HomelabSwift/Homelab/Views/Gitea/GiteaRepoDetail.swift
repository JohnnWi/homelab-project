import SwiftUI

// Maps to app/gitea/[repoId].tsx — 4-tab repo detail (files, commits, issues, branches)

private let giteaColor = Color(hex: "#609926")

struct GiteaRepoDetail: View {
    let owner: String
    let repoName: String
    var initialPath: String = ""
    var isFile: Bool = false
    var initialBranch: String? = nil

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var repo: GiteaRepo?
    @State private var activeTab: TabType = .files
    @State private var selectedBranch: String?
    @State private var showBranchSheet = false

    // Files state
    @State private var files: [GiteaFileContent] = []
    @State private var viewingFile: GiteaFileContent?
    @State private var readme: GiteaFileContent?
    @State private var viewMode: ViewMode = .preview

    // Other tabs state
    @State private var commits: [GiteaCommit] = []
    @State private var issues: [GiteaIssue] = []
    @State private var branches: [GiteaBranch] = []

    // Loading & Errors
    @State private var isLoadingRepo = true
    @State private var isLoadingContent = false
    @State private var fetchError: String?
    @State private var showFetchError = false

    private var effectiveBranch: String {
        selectedBranch ?? repo?.default_branch ?? "main"
    }

    private enum TabType: String, CaseIterable {
        case files, commits, issues, branches
    }

    private enum ViewMode {
        case preview, code
    }

    var body: some View {
        Group {
            if isFile {
                VStack(spacing: AppTheme.gridSpacing) {
                    if let file = viewingFile {
                        fileViewer(file)
                    } else if isLoadingContent {
                        ProgressView()
                            .tint(giteaColor)
                            .frame(maxWidth: .infinity, minHeight: 100)
                    } else {
                        Text(localizer.t.noData)
                    }
                }
                .padding(AppTheme.padding)
            } else if !initialPath.isEmpty {
                ScrollView {
                    LazyVStack(spacing: AppTheme.gridSpacing) {
                        fileBrowser
                    }
                    .padding(AppTheme.padding)
                }
                .refreshable { await fetchFiles() }
            } else {
                ScrollView {
                    LazyVStack(spacing: AppTheme.gridSpacing) {
                        if let repo {
                            repoHeader(repo)
                        }

                        tabBar

                        tabContent
                    }
                    .padding(AppTheme.padding)
                }
                .refreshable { await refreshCurrentTab() }
            }
        }

        .background(AppTheme.background)
        .navigationTitle(isFile ? (initialPath.components(separatedBy: "/").last ?? initialPath) : (!initialPath.isEmpty ? initialPath : repoName))
        .navigationBarTitleDisplayMode(isFile || !initialPath.isEmpty ? .inline : .large)
        .task { await initializeView() }
        .onChange(of: activeTab) { _, newTab in
            Task { await fetchTabContent(newTab) }
        }
        .sheet(isPresented: $showBranchSheet) {
            branchSelectorSheet
        }
        .alert(localizer.t.error, isPresented: $showFetchError) {
            Button(localizer.t.confirm, role: .cancel) { }
        } message: {
            Text(fetchError ?? localizer.t.errorUnknown)
        }
    }

    // MARK: - Repo Header

    @ViewBuilder
    private func repoHeader(_ repo: GiteaRepo) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            // Title row
            HStack(spacing: 8) {
                Image(systemName: repo.isPrivate ? "lock" : "lock.open")
                    .font(.subheadline)
                    .foregroundStyle(repo.isPrivate ? AppTheme.warning : AppTheme.textMuted)
                Text(repo.full_name)
                    .font(.title3.bold())
                    .lineLimit(1)
            }

            if !repo.description.isEmpty {
                Text(repo.description)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(3)
            }

            // Meta row
            HStack(spacing: 14) {
                HStack(spacing: 4) {
                    Image(systemName: "star")
                        .font(.caption)
                        .foregroundStyle(AppTheme.warning)
                    Text("\(repo.stars_count)")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(AppTheme.textMuted)
                }
                HStack(spacing: 4) {
                    Image(systemName: "arrow.triangle.branch")
                        .font(.caption)
                        .foregroundStyle(AppTheme.info)
                    Text("\(branches.count)")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(AppTheme.textMuted)
                }
                HStack(spacing: 4) {
                    Image(systemName: "circle.dotted")
                        .font(.caption)
                        .foregroundStyle(AppTheme.running)
                    Text("\(repo.open_issues_count)")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(AppTheme.textMuted)
                }
                if !repo.language.isEmpty {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(giteaColor)
                            .frame(width: 8, height: 8)
                        Text(repo.language)
                            .font(.caption.weight(.medium))
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
            }
            .padding(.top, 4)

            HStack(spacing: 6) {
                Text(localizer.t.giteaBranchLabel)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)

                Button {
                    HapticManager.light()
                    showBranchSheet = true
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.triangle.branch")
                            .font(.system(size: 11))
                        Text(effectiveBranch)
                            .font(.caption.bold())
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 11))
                    }
                    .foregroundStyle(giteaColor)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(giteaColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }

                Text("•")
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                Text(Formatters.formatBytes(Double(repo.size * 1024)))
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
            }
            .padding(.top, 4)
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .glassCard()
    }

    // MARK: - Tab Bar

    private var tabBar: some View {
        HStack(spacing: 2) {
            ForEach(TabType.allCases, id: \.self) { tab in
                Button {
                    HapticManager.light()
                    if tab == .files {
                        viewingFile = nil
                    }
                    activeTab = tab
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: tabIcon(tab))
                            .font(.caption)
                        Text(tabLabel(tab))
                            .font(.caption.bold())
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                    }
                    .foregroundStyle(activeTab == tab ? giteaColor : AppTheme.textMuted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(
                        activeTab == tab ? giteaColor.opacity(0.1) : .clear,
                        in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                    )
                }
                .buttonStyle(.plain)
            }
        }
        .padding(4)
        .glassCard()
    }

    // MARK: - Tab Content

    @ViewBuilder
    private var tabContent: some View {
        switch activeTab {
        case .files:
            filesTab
        case .commits:
            commitsTab
        case .issues:
            issuesTab
        case .branches:
            branchesTab
        }
    }

    // MARK: - Files Tab

    @ViewBuilder
    private var filesTab: some View {
        if viewingFile != nil {
            EmptyView()
        } else {
            fileBrowser
        }
    }

    @ViewBuilder
    private var fileBrowser: some View {
        if isLoadingContent && sortedFiles.isEmpty {
            ProgressView()
                .tint(giteaColor)
                .frame(maxWidth: .infinity, minHeight: 100)
        } else if sortedFiles.isEmpty {
            VStack(spacing: 10) {
                Image(systemName: "doc.text")
                    .font(.system(size: 32))
                    .foregroundStyle(AppTheme.textMuted)
                Text(localizer.t.giteaNoFiles)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textMuted)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 40)
        } else {
            VStack(spacing: 0) {
                ForEach(Array(sortedFiles.enumerated()), id: \.element.id) { index, file in
                    NavigationLink(value: GiteaRepoRoute(owner: owner, repoName: repoName, path: file.path, isFile: !file.isDirectory, branch: effectiveBranch)) {
                        HStack(spacing: 12) {
                            Image(systemName: file.isDirectory ? "folder.fill" : "doc")
                                .font(.title3)
                                .foregroundStyle(file.isDirectory ? giteaColor : AppTheme.textMuted)

                            Text(file.name)
                                .font(.subheadline.weight(.medium))
                                .foregroundStyle(file.isDirectory ? giteaColor : .primary)
                                .lineLimit(1)

                            Spacer()

                            if file.isDirectory {
                                Image(systemName: "chevron.right")
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            } else if file.size > 0 {
                                Text(Formatters.formatBytes(Double(file.size)))
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 13)
                    }
                    .buttonStyle(.plain)

                    if index < sortedFiles.count - 1 {
                        Divider().padding(.leading, 48)
                    }
                }
            }
            .glassCard()

            // README
            if initialPath.isEmpty, let readme, let content = readme.decodedContent {
                readmeCard(content)
            }
        }
    }

    private var sortedFiles: [GiteaFileContent] {
        files.sorted { a, b in
            if a.isDirectory && !b.isDirectory { return true }
            if !a.isDirectory && b.isDirectory { return false }
            return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
        }
    }

    // MARK: - File Viewer

    @ViewBuilder
    private func fileViewer(_ file: GiteaFileContent) -> some View {
        // Toggle preview/code for markdown/images
        if file.isMarkdown || file.isImage {
            HStack(spacing: 0) {
                Button {
                    HapticManager.light()
                    viewMode = .preview
                } label: {
                    Text(localizer.t.giteaPreview)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(viewMode == .preview ? giteaColor : AppTheme.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            viewMode == .preview ? AppTheme.surface : .clear,
                            in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                        )
                }
                .buttonStyle(.plain)

                Button {
                    HapticManager.light()
                    viewMode = .code
                } label: {
                    Text(localizer.t.giteaCode)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(viewMode == .code ? giteaColor : AppTheme.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            viewMode == .code ? AppTheme.surface : .clear,
                            in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                        )
                }
                .buttonStyle(.plain)
            }
            .padding(4)
            .background(.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }

        if isLoadingContent {
            ProgressView()
                .tint(giteaColor)
                .frame(maxWidth: .infinity, minHeight: 100)
        } else {
            VStack(spacing: 0) {
                // File header
                HStack(spacing: 8) {
                    Image(systemName: "chevron.left.forwardslash.chevron.right")
                        .font(.caption)
                        .foregroundStyle(giteaColor)
                    Text(file.name)
                        .font(.subheadline.bold())
                        .lineLimit(1)
                    Spacer()
                    if file.size > 0 {
                        Text(Formatters.formatBytes(Double(file.size)))
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                Divider()

                // Content
                if file.size > 5_000_000 {
                    VStack(spacing: 12) {
                        Image(systemName: "doc.text")
                            .font(.system(size: 48))
                            .foregroundStyle(AppTheme.textMuted)
                        Text(String(format: localizer.t.giteaFileTooLarge, Formatters.formatBytes(Double(file.size))))
                            .font(.subheadline)
                            .foregroundStyle(AppTheme.textSecondary)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(24)
                } else if file.isImage, let raw = file.content,
                          let data = Data(base64Encoded: raw.replacingOccurrences(of: "\n", with: "")),
                          let uiImage = UIImage(data: data) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFit()
                        .frame(maxWidth: .infinity)
                        .padding(16)
                } else if let content = file.decodedContent {
                    if file.isMarkdown && viewMode == .preview {
                        ScrollView {
                            markdownView(content)
                                .padding(16)
                        }
                    } else {
                        ScrollView([.horizontal, .vertical], showsIndicators: true) {
                            syntaxHighlightedCode(content, ext: file.fileExtension)
                                .textSelection(.enabled)
                                .fixedSize(horizontal: false, vertical: true)
                                .padding(16)
                        }
                    }
                } else {
                    Text(localizer.t.noData)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textMuted)
                        .frame(maxWidth: .infinity)
                        .padding(24)
                }
            }
            .glassCard()
        }
    }

    // MARK: - README Card

    @ViewBuilder
    private func readmeCard(_ content: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Image(systemName: "book")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textMuted)
                Text("README.md")
                    .font(.subheadline.bold())
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            Divider()

            markdownView(content)
                .padding(16)
        }
        .glassCard()
    }

    // MARK: - Commits Tab

    @ViewBuilder
    private var commitsTab: some View {
        if isLoadingContent && commits.isEmpty {
            ProgressView().tint(giteaColor).frame(maxWidth: .infinity, minHeight: 100)
        } else if commits.isEmpty {
            emptyState(icon: "arrow.triangle.pull", text: localizer.t.giteaNoCommits)
        } else {
            VStack(spacing: 0) {
                ForEach(Array(commits.enumerated()), id: \.element.id) { index, commit in
                    HStack(alignment: .top, spacing: 12) {
                        // Dot + line
                        VStack(spacing: 0) {
                            Circle()
                                .fill(giteaColor)
                                .frame(width: 10, height: 10)
                            if index < commits.count - 1 {
                                Rectangle()
                                    .fill(AppTheme.textMuted.opacity(0.3))
                                    .frame(width: 2)
                                    .frame(maxHeight: .infinity)
                            }
                        }
                        .frame(width: 20)
                        .padding(.top, 4)

                        VStack(alignment: .leading, spacing: 4) {
                            Text(commit.commit.message.components(separatedBy: "\n").first ?? "")
                                .font(.subheadline.weight(.medium))
                                .lineLimit(2)

                            HStack(spacing: 8) {
                                Text(commit.commit.author.name)
                                    .font(.caption.weight(.medium))
                                    .foregroundStyle(AppTheme.textSecondary)
                                Text(formatCommitDate(commit.commit.author.date))
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            }

                            Text(Formatters.shortSHA(commit.sha))
                                .font(.caption2.bold())
                                .foregroundStyle(giteaColor)
                        }

                        Spacer()
                    }
                    .padding(.vertical, 14)
                    .padding(.horizontal, 16)

                    if index < commits.count - 1 {
                        Divider().padding(.leading, 48)
                    }
                }
            }
            .padding(.leading, 8)
            .glassCard()
        }
    }

    // MARK: - Issues Tab

    @ViewBuilder
    private var issuesTab: some View {
        if isLoadingContent && issues.isEmpty {
            ProgressView().tint(giteaColor).frame(maxWidth: .infinity, minHeight: 100)
        } else if issues.isEmpty {
            emptyState(icon: "circle.dotted", text: localizer.t.giteaNoIssues)
        } else {
            VStack(spacing: 0) {
                ForEach(Array(issues.enumerated()), id: \.element.id) { index, issue in
                    HStack(alignment: .top, spacing: 12) {
                        Image(systemName: "circle.dotted")
                            .font(.subheadline)
                            .foregroundStyle(issue.isOpen ? AppTheme.running : AppTheme.stopped)
                            .frame(width: 32, height: 32)
                            .background(
                                (issue.isOpen ? AppTheme.running : AppTheme.stopped).opacity(0.1),
                                in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                            )

                        VStack(alignment: .leading, spacing: 4) {
                            Text("#\(issue.number) \(issue.title)")
                                .font(.subheadline.weight(.medium))
                                .lineLimit(2)

                            HStack(spacing: 8) {
                                Text(issue.user.login)
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textSecondary)
                                Text(formatCommitDate(issue.created_at))
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                                if issue.comments > 0 {
                                    HStack(spacing: 2) {
                                        Image(systemName: "bubble.right")
                                            .font(.system(size: 10))
                                        Text("\(issue.comments)")
                                            .font(.caption2)
                                    }
                                    .foregroundStyle(AppTheme.textMuted)
                                }
                            }

                            if !issue.labels.isEmpty {
                                HStack(spacing: 4) {
                                    ForEach(issue.labels.prefix(3)) { label in
                                        Text(label.name)
                                            .font(.system(size: 10, weight: .semibold))
                                            .foregroundStyle(Color(hex: "#\(label.color)"))
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 2)
                                            .background(
                                                Color(hex: "#\(label.color)").opacity(0.2),
                                                in: RoundedRectangle(cornerRadius: 8, style: .continuous)
                                            )
                                    }
                                }
                                .padding(.top, 2)
                            }
                        }

                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)

                    if index < issues.count - 1 {
                        Divider().padding(.leading, 60)
                    }
                }
            }
            .glassCard()
        }
    }

    // MARK: - Branches Tab

    @ViewBuilder
    private var branchesTab: some View {
        if isLoadingContent && branches.isEmpty {
            ProgressView().tint(giteaColor).frame(maxWidth: .infinity, minHeight: 100)
        } else if branches.isEmpty {
            emptyState(icon: "arrow.triangle.branch", text: localizer.t.giteaNoFiles)
        } else {
            VStack(spacing: 0) {
                ForEach(Array(branches.enumerated()), id: \.element.id) { index, branch in
                    HStack(spacing: 12) {
                        Image(systemName: "arrow.triangle.branch")
                            .font(.subheadline)
                            .foregroundStyle(giteaColor)
                            .frame(width: 36, height: 36)
                            .background(giteaColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                        VStack(alignment: .leading, spacing: 3) {
                            HStack(spacing: 6) {
                                Text(branch.name)
                                    .font(.subheadline.bold())
                                    .lineLimit(1)

                                if branch.protected {
                                    Image(systemName: "shield")
                                        .font(.system(size: 10))
                                        .foregroundStyle(AppTheme.warning)
                                }

                                if branch.name == repo?.default_branch {
                                    Text("default")
                                        .font(.system(size: 10, weight: .semibold))
                                        .foregroundStyle(giteaColor)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(giteaColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                                }
                            }

                            Text(branch.commit.message.components(separatedBy: "\n").first ?? "")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                                .lineLimit(1)
                        }

                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)

                    if index < branches.count - 1 {
                        Divider().padding(.leading, 64)
                    }
                }
            }
            .glassCard()
        }
    }

    // MARK: - Branch Selector Sheet

    private var branchSelectorSheet: some View {
        NavigationStack {
            List(branches) { branch in
                Button {
                    HapticManager.light()
                    selectedBranch = branch.name
                    showBranchSheet = false
                    viewingFile = nil
                    Task { await initializeView() }
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "arrow.triangle.branch")
                            .foregroundStyle(effectiveBranch == branch.name ? giteaColor : AppTheme.textMuted)
                        Text(branch.name)
                            .foregroundStyle(effectiveBranch == branch.name ? giteaColor : .primary)
                            .fontWeight(effectiveBranch == branch.name ? .semibold : .regular)

                        Spacer()

                        if branch.name == repo?.default_branch {
                            Text("default")
                                .font(.caption2.bold())
                                .foregroundStyle(giteaColor)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(giteaColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                        }
                    }
                }
                .listRowBackground(effectiveBranch == branch.name ? giteaColor.opacity(0.1) : .clear)
            }
            .navigationTitle(localizer.t.giteaBranches)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(localizer.t.close) { showBranchSheet = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    // MARK: - Empty State

    private func emptyState(icon: String, text: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 32))
                .foregroundStyle(AppTheme.textMuted)
            Text(text)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }

    // MARK: - Helpers

    private func tabIcon(_ tab: TabType) -> String {
        switch tab {
        case .files: return "doc.text"
        case .commits: return "arrow.triangle.pull"
        case .issues: return "circle.dotted"
        case .branches: return "arrow.triangle.branch"
        }
    }

    private func tabLabel(_ tab: TabType) -> String {
        switch tab {
        case .files: return localizer.t.giteaFiles
        case .commits: return localizer.t.giteaCommits
        case .issues: return localizer.t.giteaIssues
        case .branches: return localizer.t.giteaBranches
        }
    }

    // MARK: - Data Fetching
    
    private func initializeView() async {
        if let ib = initialBranch { selectedBranch = ib }
        
        if initialPath.isEmpty && !isFile {
            await fetchRepo()
        } else if isFile {
            await fetchFileByPath()
        } else {
            await fetchFiles()
        }
    }
    
    private func fetchFileByPath() async {
        isLoadingContent = true
        defer { isLoadingContent = false }
        do {
            viewingFile = try await servicesStore.giteaClient.getFileContent(owner: owner, repo: repoName, path: initialPath, ref: effectiveBranch)
        } catch {
            fetchError = error.localizedDescription
            showFetchError = true
        }
    }

    private func fetchRepo() async {
        do {
            async let r = servicesStore.giteaClient.getRepo(owner: owner, repo: repoName)
            async let b = { try? await servicesStore.giteaClient.getRepoBranches(owner: owner, repo: repoName) }()

            repo = try await r
            if let fetchedBranches = await b { branches = fetchedBranches }
        } catch {
            fetchError = error.localizedDescription
            showFetchError = true
        }

        isLoadingRepo = false

        await fetchFiles()
    }

    private func fetchFiles() async {
        isLoadingContent = true
        defer { isLoadingContent = false }

        do {
            files = try await servicesStore.giteaClient.getRepoContents(owner: owner, repo: repoName, path: initialPath, ref: effectiveBranch)
        } catch {
            files = []
            // Only show error if we have no cached data
            if repo == nil {
                fetchError = error.localizedDescription
                showFetchError = true
            }
        }

        // Fetch README only at root (non-critical)
        if initialPath.isEmpty {
            readme = try? await servicesStore.giteaClient.getRepoReadme(owner: owner, repo: repoName, ref: effectiveBranch)
        } else {
            readme = nil
        }
    }

    private func fetchFileContent(_ file: GiteaFileContent) async {
        isLoadingContent = true
        defer { isLoadingContent = false }

        do {
            viewingFile = try await servicesStore.giteaClient.getFileContent(owner: owner, repo: repoName, path: file.path, ref: effectiveBranch)
        } catch {
            fetchError = error.localizedDescription
            showFetchError = true
        }
    }

    private func fetchTabContent(_ tab: TabType) async {
        isLoadingContent = true
        defer { isLoadingContent = false }

        do {
            switch tab {
            case .files:
                await fetchFiles()
            case .commits:
                commits = try await servicesStore.giteaClient.getRepoCommits(owner: owner, repo: repoName, page: 1, limit: 30, ref: effectiveBranch)
            case .issues:
                issues = try await servicesStore.giteaClient.getRepoIssues(owner: owner, repo: repoName, state: "open", page: 1, limit: 30)
            case .branches:
                branches = try await servicesStore.giteaClient.getRepoBranches(owner: owner, repo: repoName)
            }
        } catch {
            fetchError = error.localizedDescription
            showFetchError = true
        }
    }

    private func refreshCurrentTab() async {
        await fetchTabContent(activeTab)
    }

    // MARK: - Markdown View

    @ViewBuilder
    private func markdownView(_ text: String) -> some View {
        let attributed = (try? AttributedString(markdown: text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(text)
        Text(attributed)
            .font(.subheadline)
            .lineSpacing(6)
            .textSelection(.enabled)
    }

    // MARK: - Syntax Highlighting

    @ViewBuilder
    private func syntaxHighlightedCode(_ code: String, ext: String) -> some View {
        let highlighted = SyntaxHighlighter.highlight(code, fileExtension: ext)
        Text(highlighted)
            .font(.system(.caption, design: .monospaced))
            .lineSpacing(4)
    }
}

// MARK: - Date formatter

private func formatCommitDate(_ dateString: String) -> String {
    let t = Translations.current()
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    var date = formatter.date(from: dateString)
    if date == nil {
        formatter.formatOptions = [.withInternetDateTime]
        date = formatter.date(from: dateString)
    }
    guard let d = date else { return dateString }

    let diff = Date().timeIntervalSince(d)
    let hours = Int(diff / 3600)
    if hours < 1 { return t.timeNow }
    if hours < 24 { return String(format: t.timeHoursAgo, hours) }
    let days = hours / 24
    if days < 30 { return String(format: t.timeDaysAgo, days) }
    let fmt = DateFormatter()
    fmt.dateFormat = "dd/MM/yy"
    return fmt.string(from: d)
}
