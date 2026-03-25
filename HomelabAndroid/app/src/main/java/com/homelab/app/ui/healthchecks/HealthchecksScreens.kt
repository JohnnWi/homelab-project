@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.homelab.app.ui.healthchecks

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.healthchecks.*
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.BouncyCard
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusPurple
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import android.content.ClipData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val HealthchecksGreen = Color(0xFF16A34A)

private fun healthchecksPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF0A110D),
            Color(0xFF0F1913),
            accent.copy(alpha = 0.045f),
            Color(0xFF0A120E)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFF7FCF8),
            Color(0xFFF1F9F4),
            accent.copy(alpha = 0.03f),
            Color(0xFFF5FBF7)
        )
    )
}

private fun healthchecksCardColor(isDarkTheme: Boolean, accent: Color): Color {
    val neutral = if (isDarkTheme) Color(0xFF121A16) else Color(0xFFF3F8F4)
    val tintAmount = if (isDarkTheme) 0.17f else 0.10f
    return lerp(neutral, accent, tintAmount)
}

private fun healthchecksRaisedCardColor(isDarkTheme: Boolean, accent: Color): Color {
    val neutral = if (isDarkTheme) Color(0xFF18211D) else Color(0xFFF9FCFA)
    val tintAmount = if (isDarkTheme) 0.13f else 0.08f
    return lerp(neutral, accent, tintAmount)
}

private fun healthchecksBorderTone(isDarkTheme: Boolean, accent: Color): Color {
    val neutral = if (isDarkTheme) Color(0xFF2E3C35) else Color(0xFFBED4C8)
    val tintAmount = if (isDarkTheme) 0.34f else 0.22f
    return lerp(neutral, accent, tintAmount)
}

enum class HealthchecksStatusFilter(val status: String, val labelRes: Int) {
    ALL("all", R.string.healthchecks_all),
    UP("up", R.string.healthchecks_up),
    GRACE("grace", R.string.healthchecks_grace),
    DOWN("down", R.string.healthchecks_down),
    PAUSED("paused", R.string.healthchecks_paused),
    NEW("new", R.string.healthchecks_new)
}

@Composable
private fun healthchecksStatusColor(status: String): Color {
    return when (status.lowercase()) {
        "up" -> Color(0xFF22C55E)
        "down" -> Color(0xFFEF4444)
        "grace" -> Color(0xFFF59E0B)
        "paused" -> Color(0xFFFBBF24)
        "new" -> Color(0xFF38BDF8)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun HealthchecksDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    onNavigateToChecks: (HealthchecksStatusFilter) -> Unit,
    onNavigateToEditor: (String?) -> Unit,
    onNavigateToBadges: () -> Unit,
    onNavigateToChannels: () -> Unit,
    viewModel: HealthchecksViewModel = hiltViewModel()
) {
    val checks by viewModel.checks.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    var showOverflow by remember { mutableStateOf(false) }

    val isReadOnly = checks.isNotEmpty() && checks.all { it.uuid == null }
    val serviceColor = ServiceType.HEALTHCHECKS.primaryColor
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val pageBrush = remember(isDarkTheme) { healthchecksPageBackground(isDarkTheme, serviceColor) }
    val pageGlow = remember(isDarkTheme) {
        if (isDarkTheme) serviceColor.copy(alpha = 0.085f) else serviceColor.copy(alpha = 0.04f)
    }

    LaunchedEffect(Unit) { viewModel.fetchAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.service_healthchecks),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isReadOnly) {
                        IconButton(onClick = { onNavigateToEditor(null) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.healthchecks_create_check))
                        }
                    }
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
                    }
                    DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.refresh)) },
                            onClick = {
                                showOverflow = false
                                viewModel.fetchAll()
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.healthchecks_integrations)) },
                            onClick = {
                                showOverflow = false
                                onNavigateToChannels()
                            },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.healthchecks_badges)) },
                            onClick = {
                                showOverflow = false
                                onNavigateToBadges()
                            },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBrush)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(pageGlow, Color.Transparent),
                            center = Offset(160f, 90f),
                            radius = 560f
                        )
                    )
            )

            when (val state = uiState) {
                is UiState.Loading, is UiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = serviceColor)
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
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (instances.isNotEmpty()) {
                            item {
                                ServiceInstancePicker(
                                    instances = instances,
                                    selectedInstanceId = viewModel.instanceId,
                                    onInstanceSelected = { instance ->
                                        viewModel.setPreferredInstance(instance.id)
                                        onNavigateToInstance(instance.id)
                                    }
                                )
                            }
                        }

                        item {
                            HealthchecksOverviewCard(
                                checks = checks,
                                onSelect = { filter -> onNavigateToChecks(filter) }
                            )
                        }

                        if (isReadOnly) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDarkTheme) 0.24f else 0.8f),
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (isDarkTheme) 0.38f else 0.28f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                        Column {
                                            Text(
                                                text = stringResource(R.string.healthchecks_read_only_title),
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = stringResource(R.string.healthchecks_read_only_message),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Dashboard mirrors iOS: overview card only (checks list lives in its own screen).
                    }
                }
            }
        }
    }
}

@Composable
fun HealthchecksChecksScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCheckDetail: (String) -> Unit,
    onNavigateToEditor: (String?) -> Unit,
    initialFilter: HealthchecksStatusFilter = HealthchecksStatusFilter.ALL,
    viewModel: HealthchecksViewModel = hiltViewModel()
) {
    val checks by viewModel.checks.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var searchText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(initialFilter) }

    val isReadOnly = checks.isNotEmpty() && checks.all { it.uuid == null }
    val serviceColor = ServiceType.HEALTHCHECKS.primaryColor

    LaunchedEffect(Unit) { viewModel.fetchAll() }
    LaunchedEffect(initialFilter) { filter = initialFilter }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.healthchecks_checks), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isReadOnly) {
                        IconButton(onClick = { onNavigateToEditor(null) }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.healthchecks_create_check))
                        }
                    }
                    IconButton(onClick = { viewModel.fetchAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = serviceColor)
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
                val filtered = checks.filter {
                    val matchesFilter = filter == HealthchecksStatusFilter.ALL || it.status == filter.status
                    val matchesSearch = searchText.isBlank() || it.name.contains(searchText, ignoreCase = true) || (it.desc?.contains(searchText, ignoreCase = true) == true)
                    matchesFilter && matchesSearch
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text(stringResource(R.string.healthchecks_search)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = serviceColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }

                    item {
                        FilterBar(
                            selected = filter,
                            onSelect = { filter = it }
                        )
                    }

                    if (filtered.isEmpty()) {
                        item {
                            EmptyState(text = stringResource(R.string.healthchecks_no_checks))
                        }
                    } else {
                        items(filtered, key = { it.id }) { check ->
                            HealthchecksCheckCard(check = check) {
                                val apiId = check.apiIdentifier?.takeIf { it.isNotBlank() } ?: return@HealthchecksCheckCard
                                onNavigateToCheckDetail(apiId)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthchecksDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    viewModel: HealthchecksDetailViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val pings by viewModel.pings.collectAsStateWithLifecycle()
    val flips by viewModel.flips.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val pingBody by viewModel.pingBody.collectAsStateWithLifecycle()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showIntegrationsSheet by remember { mutableStateOf(false) }

    val isReadOnly = detail?.uuid == null
    val serviceColor = ServiceType.HEALTHCHECKS.primaryColor

    LaunchedEffect(Unit) { viewModel.fetchDetail() }

    if (pingBody != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearPingBody() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPingBody() }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.healthchecks_ping_body)) },
            text = { Text(pingBody ?: "") }
        )
    }

    if (actionError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearActionError() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearActionError() }) {
                    Text(stringResource(R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.error)) },
            text = { Text(actionError ?: "") }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCheck(onSuccess = onNavigateBack)
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.healthchecks_delete_check)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.healthchecks_delete_confirm_title)) },
            text = { Text(stringResource(R.string.healthchecks_delete_confirm_message)) }
        )
    }

    if (showIntegrationsSheet && detail != null) {
        IntegrationsSheet(
            check = detail!!,
            channels = channels,
            onDismiss = { showIntegrationsSheet = false },
            onSave = { selected, custom ->
                viewModel.updateChannels(selected, custom) {
                    showIntegrationsSheet = false
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.name ?: stringResource(R.string.healthchecks_checks), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isReadOnly && detail != null) {
                        IconButton(onClick = { onNavigateToEditor(detail!!.apiIdentifier ?: return@IconButton) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.healthchecks_edit_check))
                        }
                        IconButton(onClick = { viewModel.togglePause() }) {
                            Icon(
                                if (detail!!.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null
                            )
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.healthchecks_delete_check))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = serviceColor)
                }
            }
            is UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { state.retryAction?.invoke() ?: viewModel.fetchDetail() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Offline -> {
                ErrorScreen(
                    message = "",
                    onRetry = { viewModel.fetchDetail() },
                    isOffline = true,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Success -> {
                val check = detail
                if (check == null) return@Scaffold

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        CheckOverviewCard(check = check)
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.healthchecks_integrations),
                            icon = Icons.Default.Tune,
                            color = serviceColor,
                            action = if (!isReadOnly) {
                                { TextButton(onClick = { showIntegrationsSheet = true }) { Text(stringResource(R.string.edit)) } }
                            } else null
                        )
                    }

                    item {
                        IntegrationsCard(
                            channelIds = check.channelsList,
                            channels = channels
                        )
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.healthchecks_pings),
                            icon = Icons.Default.Bolt,
                            color = serviceColor
                        )
                    }

                    item {
                        PingsCard(
                            pings = pings.take(15),
                            onPingClick = { if (it.bodyUrl != null) viewModel.loadPingBody(it) }
                        )
                    }

                    item {
                        SectionHeader(
                            title = stringResource(R.string.healthchecks_flips),
                            icon = Icons.Default.Timeline,
                            color = serviceColor
                        )
                    }

                    item {
                        FlipsCard(flips = flips.take(15))
                    }
                }
            }
        }
    }
}

@Composable
fun HealthchecksEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: HealthchecksEditorViewModel = hiltViewModel()
) {
    val existing by viewModel.existing.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var timeout by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("") }
    var timezone by remember { mutableStateOf("") }
    var grace by remember { mutableStateOf("") }
    var manualResume by remember { mutableStateOf(false) }
    var postOnly by remember { mutableStateOf(false) }
    var checkType by remember { mutableStateOf(HealthchecksCheckType.SIMPLE) }
    var selectedChannels by remember { mutableStateOf(setOf<String>()) }
    var customChannels by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    val isReadOnly = existing?.uuid == null && viewModel.isEditing
    val nameRequired = stringResource(R.string.healthchecks_name_required)
    val scheduleRequired = stringResource(R.string.healthchecks_schedule_required)
    val timeoutRequired = stringResource(R.string.healthchecks_timeout_required)

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(existing) {
        val check = existing ?: return@LaunchedEffect
        name = check.name
        slug = check.slug.orEmpty()
        tags = check.tags.orEmpty()
        desc = check.desc.orEmpty()
        timeout = check.timeout?.toString().orEmpty()
        schedule = check.schedule.orEmpty()
        timezone = check.tz.orEmpty()
        grace = check.grace?.toString().orEmpty()
        manualResume = check.manualResume == true
        postOnly = check.methods == "POST"
        checkType = if (check.schedule.isNullOrBlank()) HealthchecksCheckType.SIMPLE else HealthchecksCheckType.CRON
        val tokens = check.channelsList
        selectedChannels = tokens.toSet()
        customChannels = check.channels.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (viewModel.isEditing) stringResource(R.string.healthchecks_edit_check) else stringResource(R.string.healthchecks_create_check),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val trimmedName = name.trim()
                            if (trimmedName.isBlank()) {
                                formError = nameRequired
                                return@TextButton
                            }
                            if (checkType == HealthchecksCheckType.CRON && schedule.trim().isBlank()) {
                                formError = scheduleRequired
                                return@TextButton
                            }
                            if (checkType == HealthchecksCheckType.SIMPLE && timeout.toIntOrNull() == null) {
                                formError = timeoutRequired
                                return@TextButton
                            }
                            formError = null
                            val scheduleValue = if (checkType == HealthchecksCheckType.CRON) {
                                schedule.trim().ifBlank { null }
                            } else null
                            val timezoneValue = if (checkType == HealthchecksCheckType.CRON) {
                                timezone.trim().ifBlank { null }
                            } else null
                            val channelsValue = buildChannels(selectedChannels, customChannels)
                            val payload = HealthchecksCheckPayload(
                                name = trimmedName,
                                slug = slug.trim().ifBlank { null },
                                tags = tags.trim().ifBlank { null },
                                desc = desc.trim().ifBlank { null },
                                timeout = if (checkType == HealthchecksCheckType.SIMPLE) timeout.toIntOrNull() else null,
                                grace = grace.toIntOrNull(),
                                schedule = scheduleValue,
                                tz = timezoneValue,
                                manualResume = manualResume,
                                methods = if (postOnly) "POST" else null,
                                channels = channelsValue
                            )
                            viewModel.save(payload) { onNavigateBack() }
                        },
                        enabled = !isSaving && !isReadOnly
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.save))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ServiceType.HEALTHCHECKS.primaryColor)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (formError != null) {
                        item { ErrorBanner(message = formError!!) }
                    }

                    if (error != null) {
                        item {
                            ErrorBanner(message = error!!)
                        }
                    }

                    if (isReadOnly) {
                        item {
                            ErrorBanner(
                                message = stringResource(R.string.healthchecks_read_only_message),
                                isWarning = true
                            )
                        }
                    }

                    item {
                        FormSection(
                            title = stringResource(R.string.healthchecks_basics),
                            icon = Icons.AutoMirrored.Filled.Article
                        ) {
                            IconTextField(label = stringResource(R.string.healthchecks_field_name), value = name, onValueChange = { name = it }, icon = Icons.Default.TextFields)
                            IconTextField(
                                label = stringResource(R.string.healthchecks_field_slug),
                                value = slug,
                                onValueChange = { slug = it },
                                icon = Icons.Default.Link,
                                placeholder = stringResource(R.string.healthchecks_slug_hint)
                            )
                            IconTextField(
                                label = stringResource(R.string.healthchecks_field_tags),
                                value = tags,
                                onValueChange = { tags = it },
                                icon = Icons.Default.Tag,
                                placeholder = stringResource(R.string.healthchecks_tags_hint)
                            )
                            IconTextArea(label = stringResource(R.string.healthchecks_field_desc), value = desc, onValueChange = { desc = it }, icon = Icons.AutoMirrored.Filled.Notes)
                        }
                    }

                    item {
                        FormSection(
                            title = stringResource(R.string.healthchecks_schedule),
                            icon = Icons.Default.Event
                        ) {
                            SegmentedSelector(
                                selected = checkType,
                                onSelect = { checkType = it }
                            )
                            if (checkType == HealthchecksCheckType.SIMPLE) {
                                IconTextField(
                                    label = stringResource(R.string.healthchecks_field_timeout),
                                    value = timeout,
                                    onValueChange = { timeout = it },
                                    icon = Icons.Default.Timer,
                                    placeholder = stringResource(R.string.healthchecks_timeout_hint)
                                )
                            } else {
                                IconTextField(
                                    label = stringResource(R.string.healthchecks_field_schedule),
                                    value = schedule,
                                    onValueChange = { schedule = it },
                                    icon = Icons.Default.CalendarMonth,
                                    placeholder = stringResource(R.string.healthchecks_schedule_hint)
                                )
                                IconTextField(
                                    label = stringResource(R.string.healthchecks_field_timezone),
                                    value = timezone,
                                    onValueChange = { timezone = it },
                                    icon = Icons.Default.Language,
                                    placeholder = stringResource(R.string.healthchecks_timezone_hint)
                                )
                            }
                            IconTextField(
                                label = stringResource(R.string.healthchecks_field_grace),
                                value = grace,
                                onValueChange = { grace = it },
                                icon = Icons.Default.HourglassTop,
                                placeholder = stringResource(R.string.healthchecks_grace_hint)
                            )
                        }
                    }

                    item {
                        FormSection(
                            title = stringResource(R.string.healthchecks_integrations),
                            icon = Icons.Default.Tune
                        ) {
                            if (channels.isEmpty()) {
                                Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                channels.forEach { channel ->
                                    IntegrationToggle(
                                        channel = channel,
                                        checked = selectedChannels.contains(channel.id),
                                        onCheckedChange = { checked ->
                                            selectedChannels = if (checked) selectedChannels + channel.id else selectedChannels - channel.id
                                        }
                                    )
                                }
                            }
                            IconTextField(
                                label = stringResource(R.string.healthchecks_field_channels),
                                value = customChannels,
                                onValueChange = { customChannels = it },
                                icon = Icons.Default.AlternateEmail,
                                placeholder = stringResource(R.string.healthchecks_channels_hint)
                            )
                        }
                    }

                    item {
                        FormSection(
                            title = stringResource(R.string.healthchecks_advanced),
                            icon = Icons.Default.Tune
                        ) {
                            SwitchRow(label = stringResource(R.string.healthchecks_manual_resume), checked = manualResume, onCheckedChange = { manualResume = it })
                            SwitchRow(label = stringResource(R.string.healthchecks_methods_post_only), checked = postOnly, onCheckedChange = { postOnly = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthchecksBadgesScreen(
    onNavigateBack: () -> Unit,
    viewModel: HealthchecksViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val serviceColor = ServiceType.HEALTHCHECKS.primaryColor
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.fetchBadges() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.healthchecks_badges), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchBadges() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = serviceColor)
                }
            }
            is UiState.Error -> {
                val state = uiState as UiState.Error
                ErrorScreen(message = state.message, onRetry = { state.retryAction?.invoke() }, modifier = Modifier.padding(paddingValues))
            }
            else -> {
                val badgeEntries = badges.entries.toList()
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (badgeEntries.isEmpty()) {
                        item { EmptyState(text = stringResource(R.string.no_data)) }
                    } else {
                        items(badgeEntries, key = { it.key }) { entry ->
                            BadgeCard(
                                name = entry.key,
                                formats = entry.value,
                                onCopy = { value ->
                                    clipboardScope.launch {
                                        clipboard.setClipEntry(
                                            ClipEntry(ClipData.newPlainText(context.getString(R.string.healthchecks_badges), value))
                                        )
                                    }
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.copied_to_clipboard),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthchecksChannelsScreen(
    onNavigateBack: () -> Unit,
    viewModel: HealthchecksViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val serviceColor = ServiceType.HEALTHCHECKS.primaryColor

    LaunchedEffect(Unit) { viewModel.fetchAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.healthchecks_integrations), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { paddingValues ->
        when (uiState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = serviceColor)
                }
            }
            is UiState.Error -> {
                val state = uiState as UiState.Error
                ErrorScreen(message = state.message, onRetry = { state.retryAction?.invoke() }, modifier = Modifier.padding(paddingValues))
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(channels, key = { it.id }) { channel ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = serviceColor.copy(alpha = 0.18f)
                                ) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = serviceColor,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(channel.name, fontWeight = FontWeight.Bold)
                                    Text("${channel.kind} • ${channel.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun HealthchecksOverviewCard(
    checks: List<HealthchecksCheck>,
    onSelect: (HealthchecksStatusFilter) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val serviceAccent = ServiceType.HEALTHCHECKS.primaryColor
    val total = checks.size
    val up = checks.count { it.status == "up" }
    val grace = checks.count { it.status == "grace" }
    val down = checks.count { it.status == "down" }
    val paused = checks.count { it.status == "paused" }
    val newCount = checks.count { it.status == "new" }
    val tiles = listOf(
        SummaryTileData(
            label = stringResource(R.string.healthchecks_checks),
            value = total.toString(),
            color = HealthchecksGreen,
            icon = Icons.Default.Verified
        ) { onSelect(HealthchecksStatusFilter.ALL) },
        SummaryTileData(
            label = stringResource(R.string.healthchecks_up),
            value = up.toString(),
            color = healthchecksStatusColor("up"),
            icon = Icons.Default.CheckCircle
        ) { onSelect(HealthchecksStatusFilter.UP) },
        SummaryTileData(
            label = stringResource(R.string.healthchecks_grace),
            value = grace.toString(),
            color = healthchecksStatusColor("grace"),
            icon = Icons.Default.Schedule
        ) { onSelect(HealthchecksStatusFilter.GRACE) },
        SummaryTileData(
            label = stringResource(R.string.healthchecks_down),
            value = down.toString(),
            color = healthchecksStatusColor("down"),
            icon = Icons.Default.Error
        ) { onSelect(HealthchecksStatusFilter.DOWN) },
        SummaryTileData(
            label = stringResource(R.string.healthchecks_paused),
            value = paused.toString(),
            color = healthchecksStatusColor("paused"),
            icon = Icons.Default.PauseCircle
        ) { onSelect(HealthchecksStatusFilter.PAUSED) },
        SummaryTileData(
            label = stringResource(R.string.healthchecks_new),
            value = newCount.toString(),
            color = healthchecksStatusColor("new"),
            icon = Icons.Default.AutoAwesome
        ) { onSelect(HealthchecksStatusFilter.NEW) }
    )

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = healthchecksCardColor(isDarkTheme, serviceAccent),
        border = BorderStroke(1.dp, healthchecksBorderTone(isDarkTheme, serviceAccent).copy(alpha = if (isDarkTheme) 0.72f else 0.58f)),
        modifier = Modifier.animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ServiceIcon(
                    type = ServiceType.HEALTHCHECKS,
                    size = 52.dp,
                    cornerRadius = 16.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.service_healthchecks),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(R.string.healthchecks_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = healthchecksBorderTone(isDarkTheme, serviceAccent).copy(alpha = if (isDarkTheme) 0.58f else 0.48f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tiles.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { tile ->
                            SummaryTile(
                                label = tile.label,
                                value = tile.value,
                                color = tile.color,
                                icon = tile.icon,
                                onClick = tile.onClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = healthchecksRaisedCardColor(isDarkTheme, color),
        border = BorderStroke(1.dp, healthchecksBorderTone(isDarkTheme, color).copy(alpha = if (isDarkTheme) 0.64f else 0.52f)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = if (isDarkTheme) 0.22f else 0.16f)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(6.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = false
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class SummaryTileData(
    val label: String,
    val value: String,
    val color: Color,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
private fun HealthchecksCheckCard(
    check: HealthchecksCheck,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    BouncyCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(status = check.status)
                Column(modifier = Modifier.weight(1f)) {
                    Text(check.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (!check.desc.isNullOrBlank()) {
                        Text(check.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatGrid(
                items = listOf(
                    StatItem(
                        icon = Icons.Default.Schedule,
                        label = stringResource(R.string.healthchecks_last_ping),
                        value = formatDate(check.lastPing),
                        tint = StatusBlue
                    ),
                    StatItem(
                        icon = Icons.Default.Event,
                        label = stringResource(R.string.healthchecks_next_ping),
                        value = formatDate(check.nextPing),
                        tint = StatusGreen
                    ),
                    StatItem(
                        icon = Icons.Default.Timer,
                        label = if (check.schedule.isNullOrBlank()) stringResource(R.string.healthchecks_timeout) else stringResource(R.string.healthchecks_schedule),
                        value = scheduleLabel(check),
                        tint = StatusPurple
                    ),
                    StatItem(
                        icon = Icons.Default.HourglassBottom,
                        label = stringResource(R.string.healthchecks_grace_period),
                        value = check.grace?.let { "${it}s" } ?: "—",
                        tint = StatusOrange
                    )
                )
            )
        }
    }
}

private data class StatItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val tint: Color
)

@Composable
private fun StatGrid(items: List<StatItem>) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        val rows = items.chunked(2)
        Column {
            rows.forEachIndexed { index, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell(item = row[0], modifier = Modifier.weight(1f))
                    if (row.size > 1) {
                        VerticalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh, thickness = 1.dp)
                        StatCell(item = row[1], modifier = Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                if (index != rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh, thickness = 1.dp)
                }
            }
        }
    }
}

@Composable
private fun StatCell(item: StatItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(item.icon, contentDescription = null, tint = item.tint, modifier = Modifier.size(14.dp))
            Text(item.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(item.value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = healthchecksStatusColor(status)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = when (status.lowercase()) {
                    "up" -> stringResource(R.string.healthchecks_up)
                    "grace" -> stringResource(R.string.healthchecks_grace)
                    "down" -> stringResource(R.string.healthchecks_down)
                    "paused" -> stringResource(R.string.healthchecks_paused)
                    "new" -> stringResource(R.string.healthchecks_new)
                    else -> stringResource(R.string.healthchecks_status)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = color
            )
        }
    }
}

@Composable
private fun CheckOverviewCard(check: HealthchecksCheck) {
    val stats = listOf(
        StatItem(Icons.Default.Schedule, stringResource(R.string.healthchecks_last_ping), formatDate(check.lastPing), StatusBlue),
        StatItem(Icons.Default.Event, stringResource(R.string.healthchecks_next_ping), formatDate(check.nextPing), StatusGreen),
        StatItem(Icons.Default.Timer, stringResource(R.string.healthchecks_timeout), check.timeout?.let { "${it}s" } ?: "—", StatusPurple),
        StatItem(Icons.Default.HourglassBottom, stringResource(R.string.healthchecks_grace_period), check.grace?.let { "${it}s" } ?: "—", StatusOrange),
        StatItem(Icons.Default.Language, stringResource(R.string.healthchecks_timezone), check.tz ?: "—", StatusBlue),
        StatItem(Icons.AutoMirrored.Filled.CallMade, stringResource(R.string.healthchecks_methods), if (check.methods == "POST") stringResource(R.string.healthchecks_methods_post_only) else stringResource(R.string.healthchecks_methods_all), StatusBlue),
        StatItem(Icons.Default.PanTool, stringResource(R.string.healthchecks_manual_resume), if (check.manualResume == true) stringResource(R.string.yes) else stringResource(R.string.no), StatusPurple),
        StatItem(Icons.Default.QueryStats, stringResource(R.string.healthchecks_pings), check.nPings?.toString() ?: "—", StatusGreen)
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        shadowElevation = 1.dp,
        modifier = Modifier.animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(status = check.status)
                Column(modifier = Modifier.weight(1f)) {
                    Text(check.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (!check.desc.isNullOrBlank()) {
                        Text(check.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (!check.tagsList.isNullOrEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    check.tagsList.take(8).forEach { tag ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Text(tag, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (!check.pingUrl.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(check.pingUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
            StatGrid(items = stats)
        }
    }
}

@Composable
private fun IntegrationsCard(
    channelIds: List<String>,
    channels: List<HealthchecksChannel>
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.animateContentSize()
    ) {
        if (channelIds.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column {
                channelIds.forEachIndexed { index, channelId ->
                    val channel = channels.firstOrNull { it.id == channelId || it.name == channelId }
                    IntegrationRowItem(channelId = channelId, channel = channel)
                    if (index != channelIds.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    }
                }
            }
        }
    }
}

@Composable
private fun IntegrationRowItem(channelId: String, channel: HealthchecksChannel?) {
    val icon = channelIcon(channel?.kind)
    val tint = channelTint(channel?.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = 0.16f)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(channel?.name ?: channelId, fontWeight = FontWeight.Bold)
            Text(channel?.let { "${it.kind} • ${it.id}" } ?: channelId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PingsCard(
    pings: List<HealthchecksPing>,
    onPingClick: (HealthchecksPing) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.animateContentSize()
    ) {
        if (pings.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column {
                pings.forEachIndexed { index, ping ->
                    PingRowItem(ping = ping, onClick = { onPingClick(ping) })
                    if (index != pings.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    }
                }
            }
        }
    }
}

@Composable
private fun PingRowItem(ping: HealthchecksPing, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = healthchecksStatusColor(ping.type).copy(alpha = 0.18f)) {
            Icon(Icons.Default.Bolt, contentDescription = null, tint = healthchecksStatusColor(ping.type), modifier = Modifier.padding(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(formatDate(ping.date), fontWeight = FontWeight.SemiBold)
            val meta = listOfNotNull(ping.method, ping.remoteAddr, ping.userAgent).firstOrNull().orEmpty()
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (ping.duration != null) {
            Text(String.format("%.2fs", ping.duration), style = MaterialTheme.typography.labelMedium)
        }
        if (ping.bodyUrl != null) {
            Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FlipsCard(flips: List<HealthchecksFlip>) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.animateContentSize()
    ) {
        if (flips.isEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column {
                flips.forEachIndexed { index, flip ->
                    FlipRowItem(flip = flip)
                    if (index != flips.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    }
                }
            }
        }
    }
}

@Composable
private fun FlipRowItem(flip: HealthchecksFlip) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = healthchecksStatusColor(if (flip.isUp) "up" else "down").copy(alpha = 0.18f)) {
            Icon(Icons.Default.Timeline, contentDescription = null, tint = healthchecksStatusColor(if (flip.isUp) "up" else "down"), modifier = Modifier.padding(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(formatDate(flip.timestamp), fontWeight = FontWeight.SemiBold)
            Text(if (flip.isUp) stringResource(R.string.healthchecks_up) else stringResource(R.string.healthchecks_down), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private data class BadgeFormatRow(val label: String, val value: String)

@Composable
private fun BadgeCard(
    name: String,
    formats: HealthchecksBadgeFormats,
    onCopy: (String) -> Unit
) {
    val rows = listOfNotNull(
        formats.svg?.let { BadgeFormatRow("SVG", it) },
        formats.svg3?.let { BadgeFormatRow("SVG3", it) },
        formats.json?.let { BadgeFormatRow("JSON", it) },
        formats.json3?.let { BadgeFormatRow("JSON3", it) },
        formats.shields?.let { BadgeFormatRow("Shields", it) },
        formats.shields3?.let { BadgeFormatRow("Shields3", it) }
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.animateContentSize()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                    Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                }
                Text(name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
            if (rows.isEmpty()) {
                Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column {
                    rows.forEachIndexed { index, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onCopy(row.value) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(row.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 64.dp))
                            Text(
                                row.value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (index != rows.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterBar(selected: HealthchecksStatusFilter, onSelect: (HealthchecksStatusFilter) -> Unit) {
    val scrollState = rememberScrollState()
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HealthchecksStatusFilter.values().forEach { filter ->
                val isSelected = filter == selected
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                    label = "filterBg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "filterText"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.02f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "filterScale"
                )
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(bgColor)
                        .clickable { onSelect(filter) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stringResource(filter.labelRes),
                        color = textColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
    color: Color,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.16f)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.weight(1f))
        if (action != null) {
            action()
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, isWarning: Boolean = false) {
    Surface(
        color = if (isWarning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.Error,
                contentDescription = null,
                tint = if (isWarning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                message,
                style = MaterialTheme.typography.labelMedium,
                color = if (isWarning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun FormSection(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
            content()
        }
    }
}

@Composable
private fun IconTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

@Composable
private fun IconTextArea(label: String, value: String, onValueChange: (String) -> Unit, icon: ImageVector) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SegmentedSelector(selected: HealthchecksCheckType, onSelect: (HealthchecksCheckType) -> Unit) {
    val haptic = LocalHapticFeedback.current
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selected == HealthchecksCheckType.SIMPLE,
            onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onSelect(HealthchecksCheckType.SIMPLE)
            },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
        ) {
            Text(stringResource(R.string.healthchecks_type_simple))
        }
        SegmentedButton(
            selected = selected == HealthchecksCheckType.CRON,
            onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onSelect(HealthchecksCheckType.CRON)
            },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
        ) {
            Text(stringResource(R.string.healthchecks_type_cron))
        }
    }
}

@Composable
private fun IntegrationToggle(
    channel: HealthchecksChannel,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val icon = channelIcon(channel.kind)
    val tint = channelTint(channel.kind)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = 0.16f)) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(6.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(channel.name, fontWeight = FontWeight.SemiBold)
            Text("${channel.kind} • ${channel.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun IntegrationsSheet(
    check: HealthchecksCheck,
    channels: List<HealthchecksChannel>,
    onDismiss: () -> Unit,
    onSave: (List<String>, String) -> Unit
) {
    var selected by remember { mutableStateOf(check.channelsList.toSet()) }
    var custom by remember { mutableStateOf(check.channels.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.healthchecks_integrations), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            channels.forEach { channel ->
                IntegrationToggle(
                    channel = channel,
                    checked = selected.contains(channel.id),
                    onCheckedChange = { checked ->
                        selected = if (checked) selected + channel.id else selected - channel.id
                    }
                )
            }
            IconTextField(
                label = stringResource(R.string.healthchecks_field_channels),
                value = custom,
                onValueChange = { custom = it },
                icon = Icons.Default.AlternateEmail,
                placeholder = stringResource(R.string.healthchecks_channels_hint)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = { onSave(selected.toList(), custom) }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@Composable
private fun channelIcon(kind: String?): ImageVector = when (kind?.lowercase()) {
    "email" -> Icons.Default.Email
    "sms" -> Icons.AutoMirrored.Filled.Message
    "slack" -> Icons.AutoMirrored.Filled.Chat
    "discord" -> Icons.Default.Forum
    "webhook" -> Icons.AutoMirrored.Filled.Send
    else -> Icons.Default.Notifications
}

@Composable
private fun channelTint(kind: String?): Color = when (kind?.lowercase()) {
    "email" -> StatusBlue
    "sms" -> StatusOrange
    "slack" -> StatusPurple
    "discord" -> StatusPurple
    "webhook" -> StatusGreen
    else -> MaterialTheme.colorScheme.primary
}

private fun formatDate(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    return try {
        val instant = Instant.parse(raw)
        DateTimeFormatter.ofPattern("dd MMM, HH:mm").withZone(ZoneId.systemDefault()).format(instant)
    } catch (_: Exception) {
        raw
    }
}

private fun scheduleLabel(check: HealthchecksCheck): String {
    return if (!check.schedule.isNullOrBlank()) check.schedule!! else check.timeout?.let { "${it}s" } ?: "—"
}

private fun buildChannels(selected: Set<String>, custom: String): String? {
    val tokens = custom.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val combined = (selected + tokens).distinct()
    if (combined.isEmpty()) return null
    return combined.joinToString(",")
}

enum class HealthchecksCheckType { SIMPLE, CRON }
