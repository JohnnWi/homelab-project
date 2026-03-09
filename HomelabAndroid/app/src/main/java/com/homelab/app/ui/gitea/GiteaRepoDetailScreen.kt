package com.homelab.app.ui.gitea

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.gitea.*
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.util.ResourceFormatters
import java.text.SimpleDateFormat
import java.util.Locale
import com.homelab.app.ui.gitea.langColors
import dev.jeziellago.compose.markdowntext.MarkdownText
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiteaRepoDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: GiteaRepoDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val repo = (uiState as? UiState.Success)?.data
    
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val selectedBranch by viewModel.selectedBranch.collectAsStateWithLifecycle()
    val viewingFile by viewModel.viewingFile.collectAsStateWithLifecycle()
    val currentPath by viewModel.currentPath.collectAsStateWithLifecycle()

    val isLoadingContent by viewModel.isLoadingContent.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showBranchSheet by remember { mutableStateOf(false) }

    val effectiveBranch = selectedBranch ?: repo?.default_branch ?: "main"

    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewingFile != null) viewingFile!!.name else if (currentPath.isNotEmpty()) currentPath else repo?.name ?: stringResource(R.string.loading),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    val haptic = LocalHapticFeedback.current
                    IconButton(onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (viewingFile != null || currentPath.isNotEmpty()) {
                            viewModel.navigateUp()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val haptic = LocalHapticFeedback.current
                    val context = LocalContext.current
                    
                    if (viewingFile != null && viewingFile!!.decodedContent != null && !viewingFile!!.isImage) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, viewingFile!!.decodedContent)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.share) + " " + viewingFile!!.name)
                            context.startActivity(shareIntent)
                        }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                        }
                    }
                    
                    IconButton(onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        if (viewingFile == null && currentPath.isEmpty()) {
                            viewModel.fetchRepo()
                        } else {
                            viewModel.fetchTabContent()
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (viewingFile != null) {
                FileViewerContent(viewModel = viewModel, file = viewingFile!!)
            } else if (currentPath.isNotEmpty()) {
                FileBrowserContent(viewModel = viewModel, modifier = Modifier.fillMaxSize())
            } else {
                when (val state = uiState) {
                    is UiState.Loading, is UiState.Idle -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ServiceType.GITEA.primaryColor)
                        }
                    }
                    is UiState.Error -> {
                        ErrorScreen(
                            message = state.message,
                            onRetry = { viewModel.fetchRepo() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is UiState.Offline -> {
                        ErrorScreen(
                            message = "",
                            onRetry = { viewModel.fetchRepo() },
                            isOffline = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is UiState.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                RepoHeader(
                                    repo = state.data,
                                    branchesCount = branches.size,
                                    effectiveBranch = effectiveBranch,
                                    onBranchClick = { showBranchSheet = true }
                                )
                            }
                            item {
                                TabBar(
                                    activeTab = activeTab,
                                    onTabSelected = { viewModel.setActiveTab(it) }
                                )
                            }
                            item {
                                when (activeTab) {
                                    GiteaRepoTab.FILES -> FileBrowserContent(viewModel = viewModel)
                                    GiteaRepoTab.COMMITS -> CommitsTabContent(viewModel = viewModel)
                                    GiteaRepoTab.ISSUES -> IssuesTabContent(viewModel = viewModel)
                                    GiteaRepoTab.BRANCHES -> BranchesTabContent(
                                        viewModel = viewModel,
                                        defaultBranch = state.data.default_branch
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showBranchSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBranchSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.gitea_branches), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 16.dp))
                    LazyColumn {
                        items(branches, key = { it.id }) { branch ->
                            val haptic = LocalHapticFeedback.current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        viewModel.setBranch(branch.name)
                                        showBranchSheet = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.CallMerge,
                                    contentDescription = null,
                                    tint = if (effectiveBranch == branch.name) ServiceType.GITEA.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = branch.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (effectiveBranch == branch.name) FontWeight.Bold else FontWeight.Normal),
                                    color = if (effectiveBranch == branch.name) ServiceType.GITEA.primaryColor else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (branch.name == repo?.default_branch) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = ServiceType.GITEA.primaryColor.copy(alpha = 0.1f)) {
                                        Text(stringResource(R.string.default_branch), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = ServiceType.GITEA.primaryColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoHeader(repo: GiteaRepo, branchesCount: Int, effectiveBranch: String, onBranchClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (repo.isPrivate) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null, tint = if (repo.isPrivate) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Text(repo.full_name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
            if (!repo.description.isNullOrEmpty()) {
                Text(repo.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    Text("${repo.stars_count}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = Color(0xFF2196F3), modifier = Modifier.size(16.dp))
                    Text("$branchesCount", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.TripOrigin, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                    Text("${repo.open_issues_count}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!repo.language.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(langColors[repo.language] ?: MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(repo.language, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gitea_branch_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val haptic = LocalHapticFeedback.current
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ServiceType.GITEA.primaryColor.copy(alpha = 0.1f),
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onBranchClick()
                    }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = ServiceType.GITEA.primaryColor, modifier = Modifier.size(14.dp))
                        Text(effectiveBranch, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = ServiceType.GITEA.primaryColor)
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = ServiceType.GITEA.primaryColor, modifier = Modifier.size(14.dp))
                    }
                }
                Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ResourceFormatters.formatBytes(repo.size * 1024.0, LocalContext.current), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TabBar(activeTab: GiteaRepoTab, onTabSelected: (GiteaRepoTab) -> Unit) {
    val haptic = LocalHapticFeedback.current
    SecondaryScrollableTabRow(
        selectedTabIndex = activeTab.ordinal,
        edgePadding = 0.dp
    ) {
        GiteaRepoTab.entries.forEachIndexed { index, tab ->
            val isSelected = activeTab.ordinal == index
            Tab(
                selected = isSelected,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onTabSelected(tab)
                },
                text = { 
                    val tabTitle = when (tab) {
                        GiteaRepoTab.FILES -> stringResource(R.string.gitea_tab_files)
                        GiteaRepoTab.COMMITS -> stringResource(R.string.gitea_tab_commits)
                        GiteaRepoTab.ISSUES -> stringResource(R.string.gitea_tab_issues)
                        GiteaRepoTab.BRANCHES -> stringResource(R.string.gitea_tab_branches)
                    }
                    Text(
                        tabTitle, 
                        color = if (isSelected) ServiceType.GITEA.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Visible
                    ) 
                },
                icon = {
                    val icon = when (tab) {
                        GiteaRepoTab.FILES -> Icons.Default.Description
                        GiteaRepoTab.COMMITS -> Icons.AutoMirrored.Filled.CallMerge
                        GiteaRepoTab.ISSUES -> Icons.Default.TripOrigin
                        GiteaRepoTab.BRANCHES -> Icons.AutoMirrored.Filled.CallMerge
                    }
                    Icon(icon, contentDescription = null, tint = if (isSelected) ServiceType.GITEA.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            )
        }
    }
}

@Composable
private fun FileBrowserContent(viewModel: GiteaRepoDetailViewModel, modifier: Modifier = Modifier) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val readme by viewModel.readme.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    if (isLoading && files.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ServiceType.GITEA.primaryColor)
        }
    } else if (files.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                Text(stringResource(R.string.gitea_no_files), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Column(modifier = modifier) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column {
                    files.forEachIndexed { index, file ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.98f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                            label = "FileBouncy"
                        )
                        val haptic = LocalHapticFeedback.current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(scale)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = { 
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        viewModel.navigateToPath(file.path, file.isFile)
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = if (file.isDirectory) ServiceType.GITEA.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Text(file.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (file.isDirectory) ServiceType.GITEA.primaryColor else MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.weight(1f))
                            if (file.isDirectory) {
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                            } else if (file.size > 0) {
                                val context = LocalContext.current
                                Text(ResourceFormatters.formatBytes(file.size.toDouble(), context), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (index < files.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }

            readme?.decodedContent?.let { content ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Text("README.md", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        MarkdownText(
                            markdown = content.take(25000) + if (content.length > 25000) "\n\n" + stringResource(R.string.gitea_preview_truncated) else "",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileViewerContent(viewModel: GiteaRepoDetailViewModel, file: GiteaFileContent) {
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (file.isMarkdown || file.isImage) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.padding(bottom = 16.dp).fillMaxWidth()) {
                Row(modifier = Modifier.padding(4.dp)) {
                    Surface(shape = RoundedCornerShape(8.dp), color = if (viewMode == GiteaViewMode.PREVIEW) MaterialTheme.colorScheme.surface else Color.Transparent, modifier = Modifier.weight(1f).clickable { viewModel.setViewMode(GiteaViewMode.PREVIEW) }) {
                        Text(stringResource(R.string.preview), modifier = Modifier.padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (viewMode == GiteaViewMode.PREVIEW) ServiceType.GITEA.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = if (viewMode == GiteaViewMode.CODE) MaterialTheme.colorScheme.surface else Color.Transparent, modifier = Modifier.weight(1f).clickable { viewModel.setViewMode(GiteaViewMode.CODE) }) {
                        Text(stringResource(R.string.code), modifier = Modifier.padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = if (viewMode == GiteaViewMode.CODE) ServiceType.GITEA.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ServiceType.GITEA.primaryColor)
                }
            } else {
                Column {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Code, contentDescription = null, tint = ServiceType.GITEA.primaryColor, modifier = Modifier.size(14.dp))
                        Text(file.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(modifier = Modifier.weight(1f))
                        if (file.size > 0) {
                            Text(ResourceFormatters.formatBytes(file.size.toDouble(), LocalContext.current), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    if (file.size > 5_000_000) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.gitea_file_too_large, ResourceFormatters.formatBytes(file.size.toDouble(), LocalContext.current)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else if (file.isImage && !file.content.isNullOrEmpty()) {
                        val encodedContent = file.content.orEmpty()
                        var bitmap: android.graphics.Bitmap? = null
                        var errorMsg: String? = null
                        val context = androidx.compose.ui.platform.LocalContext.current
                        try {
                            val decodedBytes = Base64.decode(encodedContent.replace("\n", ""), Base64.DEFAULT)
                            bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            if (bitmap == null) {
                                errorMsg = context.getString(R.string.gitea_image_decode_error)
                            }
                        } catch (e: Exception) {
                            errorMsg = "${context.getString(R.string.error_unknown)}: ${e.message}"
                        }
                        
                        if (bitmap != null) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Image(bitmap = bitmap.asImageBitmap(), contentDescription = file.name, modifier = Modifier.fillMaxWidth())
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                Text(errorMsg ?: stringResource(R.string.error_unknown), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else if (file.decodedContent != null) {
                        if (viewMode == GiteaViewMode.PREVIEW && file.isMarkdown) {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                                MarkdownText(
                                    markdown = file.decodedContent!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        } else {
                            val extension = file.name.substringAfterLast('.', "")
                            val lang = if (extension.isNotEmpty() && file.name != extension) extension else "text"
                            
                            val htmlContent = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1">
                                    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css">
                                    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                                    <script>hljs.highlightAll();</script>
                                    <style>
                                        body { 
                                            margin: 0; padding: 16px; background-color: #1E1E1E; color: #D4D4D4; 
                                            font-family: monospace; font-size: 14px; 
                                            -webkit-user-select: text;
                                            user-select: text;
                                        }
                                        pre { margin: 0; white-space: pre-wrap; word-wrap: break-word; }
                                        code { padding: 0 !important; background: transparent !important; }
                                    </style>
                                </head>
                                <body>
                                    <pre><code class="language-$lang">${android.text.TextUtils.htmlEncode(file.decodedContent!!)}</code></pre>
                                </body>
                                </html>
                            """.trimIndent()
                            
                            androidx.compose.ui.viewinterop.AndroidView(
                                factory = { context ->
                                    android.webkit.WebView(context).apply {
                                        layoutParams = android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        settings.javaScriptEnabled = true
                                        settings.setSupportZoom(true)
                                        settings.builtInZoomControls = true
                                        settings.displayZoomControls = false
                                        setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
                                        loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                                    }
                                },
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.not_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitsTabContent(viewModel: GiteaRepoDetailViewModel) {
    val commits by viewModel.commits.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    if (isLoading && commits.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ServiceType.GITEA.primaryColor) }
    } else if (commits.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.gitea_no_commits), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
                commits.forEachIndexed { index, commit ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ServiceType.GITEA.primaryColor))
                            if (index < commits.lastIndex) {
                                Box(modifier = Modifier.width(2.dp).height(50.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(commit.commit.message.split("\n").firstOrNull() ?: "", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(commit.commit.author?.name ?: stringResource(R.string.not_available), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ResourceFormatters.formatDate(commit.commit.author?.date ?: ""), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(commit.sha.take(7), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = ServiceType.GITEA.primaryColor)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssuesTabContent(viewModel: GiteaRepoDetailViewModel) {
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    if (isLoading && issues.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ServiceType.GITEA.primaryColor) }
    } else if (issues.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.gitea_no_issues), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                issues.forEachIndexed { index, issue ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Surface(shape = RoundedCornerShape(10.dp), color = if (issue.isOpen) Color(0xFF4CAF50).copy(alpha = 0.1f) else Color(0xFFF44336).copy(alpha = 0.1f), modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.TripOrigin, contentDescription = null, tint = if (issue.isOpen) Color(0xFF4CAF50) else Color(0xFFF44336), modifier = Modifier.padding(6.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("#${issue.number} ${issue.title}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(issue.user?.login ?: stringResource(R.string.not_available), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(ResourceFormatters.formatDate(issue.created_at), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (issue.comments > 0) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${issue.comments}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (issue.labels.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    issue.labels.take(3).forEach { label ->
                                        val color = try { Color(android.graphics.Color.parseColor("#${label.color}")) } catch (e: Exception) { Color.Gray }
                                        Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.2f)) {
                                            Text(label.name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (index < issues.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BranchesTabContent(viewModel: GiteaRepoDetailViewModel, defaultBranch: String?) {
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingContent.collectAsStateWithLifecycle()

    if (isLoading && branches.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = ServiceType.GITEA.primaryColor) }
    } else if (branches.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.gitea_no_branches), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                branches.forEachIndexed { index, branch ->
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(10.dp), color = ServiceType.GITEA.primaryColor.copy(alpha = 0.1f), modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, tint = ServiceType.GITEA.primaryColor, modifier = Modifier.padding(8.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(branch.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (branch.protected) {
                                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(10.dp))
                                }
                                if (branch.name == defaultBranch) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = ServiceType.GITEA.primaryColor.copy(alpha = 0.1f)) {
                                        Text(stringResource(R.string.default_branch), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = ServiceType.GITEA.primaryColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text(branch.commit.message.split("\n").firstOrNull() ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (index < branches.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}

// --- Formatters moved to ResourceFormatters ---
