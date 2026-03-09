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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.gitea.*
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.theme.primaryColor
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

import androidx.compose.foundation.isSystemInDarkTheme

val langColors = mapOf(
    "Go" to Color(0xFF00ADD8),
    "JavaScript" to Color(0xFFF7DF1E),
    "TypeScript" to Color(0xFF3178C6),
    "Python" to Color(0xFF3776AB),
    "Rust" to Color(0xFFDEA584),
    "Java" to Color(0xFFB07219),
    "C#" to Color(0xFF178600),
    "C++" to Color(0xFFF34B7D),
    "Ruby" to Color(0xFFCC342D),
    "PHP" to Color(0xFF777BB4),
    "Shell" to Color(0xFF89E051),
    "Dockerfile" to Color(0xFF384D54),
    "HTML" to Color(0xFFE34C26),
    "CSS" to Color(0xFF563D7C),
    "Dart" to Color(0xFF00B4AB),
    "Swift" to Color(0xFFF05138),
    "Kotlin" to Color(0xFFA97BFF)
)

val HeatmapColors: List<Color>
    @Composable
    get() = if (isSystemInDarkTheme()) {
        listOf(
            Color(0xFF161B22), // 0: Empty/Dark
            Color(0xFF0E4429), // 1: Low
            Color(0xFF006D32), // 2: Medium
            Color(0xFF26A641), // 3: High
            Color(0xFF39D353)  // 4: Critical
        )
    } else {
        listOf(
            Color(0xFFEBEDF0), // 0: Empty/Light
            Color(0xFF9BE9A8), // 1: Low
            Color(0xFF40C463), // 2: Medium
            Color(0xFF30A14E), // 3: High
            Color(0xFF216E39)  // 4: Critical
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiteaDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRepo: (owner: String, repo: String) -> Unit,
    viewModel: GiteaViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsStateWithLifecycle()
    val orgs by viewModel.orgs.collectAsStateWithLifecycle()
    val heatmap by viewModel.heatmap.collectAsStateWithLifecycle()
    val totalBranches by viewModel.totalBranches.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val repos by viewModel.repos.collectAsStateWithLifecycle()

    val sortedRepos = remember(repos, sortOrder) {
        when (sortOrder) {
            RepoSortOrder.RECENT -> repos.sortedByDescending { it.updated_at }
            RepoSortOrder.ALPHA -> repos.sortedBy { it.name.lowercase() }
        }
    }
    
    val repoStats = remember(repos) {
        Pair(repos.size, repos.sumOf { it.stars_count })
    }

    LaunchedEffect(Unit) {
        viewModel.fetchAll()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.service_gitea), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val haptic = LocalHapticFeedback.current
                    IconButton(onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        viewModel.fetchAll()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = uiState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ServiceType.GITEA.primaryColor)
                }
            }
            is UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { state.retryAction?.invoke() ?: viewModel.fetchAll() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Offline -> {
                ErrorScreen(
                    message = "",
                    onRetry = { viewModel.fetchAll() },
                    isOffline = true,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    user?.let { u ->
                        item { UserCard(user = u) }
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MiniStat(icon = Icons.Default.Folder, iconColor = Color(0xFF4CAF50), value = "${repoStats.first}", label = stringResource(R.string.gitea_repos), modifier = Modifier.weight(1f))
                            MiniStat(icon = Icons.Default.Star, iconColor = Color(0xFFFF9800), value = "${repoStats.second}", label = stringResource(R.string.gitea_stars), modifier = Modifier.weight(1f))
                            MiniStat(icon = Icons.AutoMirrored.Filled.CallMerge, iconColor = Color(0xFF2196F3), value = "$totalBranches", label = stringResource(R.string.gitea_branches), modifier = Modifier.weight(1f))
                        }
                    }

                    if (heatmap.isNotEmpty()) {
                        item { HeatmapSection(heatmap = heatmap) }
                    }

                    if (orgs.isNotEmpty()) {
                        item { OrgsSection(orgs = orgs) }
                    }

                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${stringResource(R.string.gitea_repos)} (${repoStats.first})", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.weight(1f))
                            val haptic = LocalHapticFeedback.current
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleSortOrder()
                                }
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (sortOrder == RepoSortOrder.RECENT) Icons.Default.AccessTime else Icons.Default.SortByAlpha, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (sortOrder == RepoSortOrder.RECENT) stringResource(R.string.gitea_sort_recent) else stringResource(R.string.gitea_sort_alpha), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (sortedRepos.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.gitea_no_repos), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(sortedRepos, key = { it.id }) { repo ->
                            RepoCard(repo = repo, onClick = { onNavigateToRepo(repo.owner.login, repo.name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCard(user: GiteaUser) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ServiceType.GITEA.primaryColor.copy(alpha = 0.1f),
                modifier = Modifier.size(52.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = ServiceType.GITEA.primaryColor, modifier = Modifier.padding(14.dp))
            }
            Column {
                Text(user.full_name.ifEmpty { user.login }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("@${user.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OrgsSection(orgs: List<GiteaOrg>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.gitea_orgs), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            orgs.forEach { org ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CorporateFare, contentDescription = null, tint = ServiceType.GITEA.primaryColor, modifier = Modifier.size(16.dp))
                        Text(org.username, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoCard(repo: GiteaRepo, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "BouncyScale"
    )
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                }
            )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (repo.isPrivate) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = null, tint = if (repo.isPrivate) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(repo.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = ServiceType.GITEA.primaryColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.weight(1f))
                if (repo.fork) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.gitea_fork), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Description
            if (repo.description.isNotEmpty()) {
                Text(repo.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            // Footer
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (repo.language?.isNotEmpty() == true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(langColors[repo.language!!] ?: MaterialTheme.colorScheme.onSurfaceVariant))
                        Text(repo.language!!, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFFF9800))
                    Text("${repo.stars_count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.CallMerge, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${repo.forks_count}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.weight(1f))
                val context = androidx.compose.ui.platform.LocalContext.current
                Text(formatRelativeDate(repo.updated_at, context), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ----- HEATMAP BUILDER -----

@Composable
private fun HeatmapSection(heatmap: List<GiteaHeatmapItem>) {
    val grid = buildHeatmapGrid(heatmap)
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.gitea_contributions), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    grid.forEach { week ->
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            week.forEach { level ->
                                Box(modifier = Modifier.size(11.dp).clip(RoundedCornerShape(2.dp)).background(HeatmapColors[level]))
                            }
                        }
                    }
                }
                
                // Legend
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.gitea_heatmap_less), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HeatmapColors.forEach { color ->
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    }
                    Text(stringResource(R.string.gitea_heatmap_more), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun buildHeatmapGrid(heatmap: List<GiteaHeatmapItem>): List<List<Int>> {
    val contributionMap = mutableMapOf<String, Int>()
    val cal = Calendar.getInstance()
    heatmap.forEach { item ->
        cal.timeInMillis = item.timestamp * 1000L
        val key = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        contributionMap[key] = (contributionMap[key] ?: 0) + item.contributions
    }

    val maxContrib = max(1, contributionMap.values.maxOrNull() ?: 1)
    
    val today = Calendar.getInstance()
    val dayOfWeek = today.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sunday
    val weeksToShow = 20
    val totalDays = weeksToShow * 7 + dayOfWeek + 1
    
    val startDate = today.clone() as Calendar
    startDate.add(Calendar.DAY_OF_YEAR, -(totalDays - 1))

    val weeks = mutableListOf<List<Int>>()
    var currentWeek = mutableListOf<Int>()

    for (i in 0 until totalDays) {
        val d = startDate.clone() as Calendar
        d.add(Calendar.DAY_OF_YEAR, i)
        val key = "${d.get(Calendar.YEAR)}-${d.get(Calendar.MONTH)}-${d.get(Calendar.DAY_OF_MONTH)}"
        val count = contributionMap[key] ?: 0

        val level = when {
            count == 0 -> 0
            count == 1 -> 1
            count in 2..3 -> 2
            count in 4..6 -> 3
            else -> 4
        }

        currentWeek.add(level)
        if (currentWeek.size == 7) {
            weeks.add(currentWeek.toList())
            currentWeek.clear()
        }
    }
    if (currentWeek.isNotEmpty()) {
        weeks.add(currentWeek.toList())
    }
    return weeks
}

private fun formatRelativeDate(dateString: String?, context: android.content.Context): String {
    // Basic approximate parsing for ISO format mostly used by Gitea
    val safeDate = dateString ?: return ""
    try {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = formatter.parse(safeDate.take(19)) ?: return safeDate
        val diff = (System.currentTimeMillis() - date.time) / 1000
        val days = (diff / 86400).toInt()
        
        if (days == 0) {
            val hours = (diff / 3600).toInt()
            if (hours > 0) return context.getString(R.string.gitea_date_hours_ago).format(hours)
            return context.getString(R.string.gitea_date_today)
        }
        if (days < 30) return context.getString(R.string.gitea_date_days_ago).format(days)
        val months = days / 30
        return context.getString(R.string.gitea_date_months_ago).format(months)
    } catch (e: Exception) {
        return safeDate
    }
}
