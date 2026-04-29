package com.homelab.app.ui.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.homelab.app.R
import com.homelab.app.domain.model.BackupServiceTypeMapper
import com.homelab.app.util.ServiceType
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val exportData by viewModel.exportDataEvent.collectAsState()
    val instancesByType by viewModel.instancesByType.collectAsState()
    val selectedExportTypes by viewModel.selectedExportTypes.collectAsState()
    val selectedImportTypes by viewModel.selectedImportTypes.collectAsState()
    val rememberSelection by viewModel.rememberSelection.collectAsState()
    val context = LocalContext.current

    val configuredTypes = remember(instancesByType) {
        (ServiceType.homeTypes + ServiceType.arrStackTypes)
            .distinct()
            .filter { instancesByType[it].orEmpty().isNotEmpty() }
    }
    val configuredTypeSet = remember(configuredTypes) { configuredTypes.toSet() }
    val homeTypeSet = remember(configuredTypes) { configuredTypes.filter { it.isHomeService }.toSet() }
    val arrTypeSet = remember(configuredTypes) { configuredTypes.filter { it.isArrStack }.toSet() }
    val exportableTypes = remember {
        ServiceType.homeTypes.plus(ServiceType.arrStackTypes).distinct()
    }
    val selectedInstanceCount = remember(instancesByType, selectedExportTypes) {
        instancesByType.entries.sumOf { (type, instances) ->
            if (selectedExportTypes.contains(type)) instances.size else 0
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let {
            val data = exportData
            if (data != null) {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(data)
                }
                viewModel.onExportDataConsumed()
                // Show success toast or scaffold snackbar ideally
            }
        } ?: viewModel.onExportDataConsumed()
    }

    LaunchedEffect(exportData) {
        if (exportData != null) {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            exportLauncher.launch("homelab_backup_$dateStr.homelab")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val input: InputStream? = context.contentResolver.openInputStream(it)
                val bytes = input?.readBytes()
                input?.close()
                if (bytes != null) {
                    viewModel.onFileSelectedForImport(bytes)
                }
            } catch (e: Exception) {
                // handle
            }
        }
    }

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backupTitle)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backupInfoTitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.backupInfoDesc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Export Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backupExportTitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(R.string.backupSelectionTitle),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.backupSelectionSubtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.backupRememberSelectionTitle),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.backupRememberSelectionSubtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = rememberSelection,
                                onCheckedChange = viewModel::setRememberSelection
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    if (configuredTypes.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.backupSelectionEmpty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BackupSelectionChip(
                            label = stringResource(R.string.backupSelectionAll),
                            selected = configuredTypeSet.isNotEmpty() && selectedExportTypes.containsAll(configuredTypeSet),
                            enabled = configuredTypeSet.isNotEmpty(),
                            onClick = viewModel::toggleAllExportTypes
                        )
                        BackupSelectionChip(
                            label = stringResource(R.string.backupSelectionHome),
                            selected = homeTypeSet.isNotEmpty() && selectedExportTypes.containsAll(homeTypeSet),
                            enabled = homeTypeSet.isNotEmpty(),
                            onClick = viewModel::toggleHomeExportTypes
                        )
                        BackupSelectionChip(
                            label = stringResource(R.string.backupSelectionArr),
                            selected = arrTypeSet.isNotEmpty() && selectedExportTypes.containsAll(arrTypeSet),
                            enabled = arrTypeSet.isNotEmpty(),
                            onClick = viewModel::toggleArrExportTypes
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        exportableTypes.forEach { type ->
                            BackupSelectionChip(
                                selected = selectedExportTypes.contains(type),
                                enabled = configuredTypeSet.contains(type),
                                onClick = { viewModel.toggleExportType(type) },
                                label = backupServiceDisplayName(type)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.backupSelectionSelectedCount, selectedInstanceCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            passwordInput = ""
                            passwordConfirm = ""
                            showExportPasswordDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = configuredTypes.isNotEmpty() && selectedExportTypes.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.backupExportAction))
                    }
                }
            }

            // Import Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.backupImportTitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.backupImportDesc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(stringResource(R.string.backupImportAction))
                    }
                }
            }
        }
    }

    // Dialogs
    if (showExportPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            title = { Text(stringResource(R.string.backupExportTitle)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backupPasswordDesc))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.backupPasswordPlaceholder)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = passwordConfirm,
                        onValueChange = { passwordConfirm = it },
                        label = { Text(stringResource(R.string.backupPasswordConfirm)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = passwordInput.isNotEmpty() && passwordConfirm.isNotEmpty() && passwordInput != passwordConfirm
                    )
                    if (passwordInput.isNotEmpty() && passwordInput.length < 6) {
                        Text(
                            text = stringResource(R.string.backupPasswordTooShort),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (passwordInput.isNotEmpty() && passwordConfirm.isNotEmpty() && passwordInput != passwordConfirm) {
                        Text(
                            text = stringResource(R.string.backupPasswordMismatch),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.startExport(passwordInput)
                        showExportPasswordDialog = false
                    },
                    enabled = passwordInput.length >= 6 && passwordInput == passwordConfirm
                ) {
                    Text(stringResource(R.string.backupExportAction))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    when (val state = uiState) {
        is BackupUiState.ImportPasswordRequired -> {
            var importPass by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = viewModel::resetState,
                title = { Text(stringResource(R.string.backupImportDecrypt)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.backupImportPasswordDesc))
                        OutlinedTextField(
                            value = importPass,
                            onValueChange = { importPass = it },
                            label = { Text(stringResource(R.string.backupPasswordPlaceholder)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.decryptAndPreview(importPass) },
                        enabled = importPass.isNotBlank()
                    ) {
                        Text(stringResource(R.string.backupImportDecrypt))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::resetState) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
        is BackupUiState.ImportPreview -> {
            val configuration = LocalConfiguration.current
            val previewScroll = rememberScrollState()
            val previewKnownTypes = remember(state.previewInfo.envelope.services) {
                state.previewInfo.envelope.services
                    .mapNotNull { BackupServiceTypeMapper.serviceType(it.type) }
                    .toSet()
            }
            val previewHomeTypeSet = remember(previewKnownTypes) {
                previewKnownTypes.filter { it.isHomeService }.toSet()
            }
            val previewArrTypeSet = remember(previewKnownTypes) {
                previewKnownTypes.filter { it.isArrStack }.toSet()
            }
            val selectedImportInstanceCount = remember(state.previewInfo.envelope.services, selectedImportTypes) {
                state.previewInfo.envelope.services.count { entry ->
                    BackupServiceTypeMapper.serviceType(entry.type)?.let { selectedImportTypes.contains(it) } == true
                }
            }

            Dialog(onDismissRequest = viewModel::resetState) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .heightIn(max = configuration.screenHeightDp.dp * 0.82f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(previewScroll),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.backupImportPreviewTitle),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val totalStr = stringResource(R.string.backupPreviewServices, state.previewInfo.totalFound)
                            Text(totalStr)

                            if (state.previewInfo.unknownCount > 0) {
                                val unknownStr = stringResource(R.string.backupPreviewUnknown, state.previewInfo.unknownCount)
                                Text(
                                    text = unknownStr,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Text(
                                text = stringResource(R.string.backupSelectionTitle),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.backupSelectionSubtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                BackupSelectionChip(
                                    label = stringResource(R.string.backupSelectionAll),
                                    selected = previewKnownTypes.isNotEmpty() && selectedImportTypes.containsAll(previewKnownTypes),
                                    onClick = { viewModel.toggleAllImportTypes() }
                                )
                                BackupSelectionChip(
                                    label = stringResource(R.string.backupSelectionHome),
                                    selected = previewHomeTypeSet.isNotEmpty() && selectedImportTypes.containsAll(previewHomeTypeSet),
                                    enabled = previewHomeTypeSet.isNotEmpty(),
                                    onClick = { viewModel.toggleHomeImportTypes() }
                                )
                                BackupSelectionChip(
                                    label = stringResource(R.string.backupSelectionArr),
                                    selected = previewArrTypeSet.isNotEmpty() && selectedImportTypes.containsAll(previewArrTypeSet),
                                    enabled = previewArrTypeSet.isNotEmpty(),
                                    onClick = { viewModel.toggleArrImportTypes() }
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                ServiceType.homeTypes.plus(ServiceType.arrStackTypes).distinct().forEach { type ->
                                    if (previewKnownTypes.contains(type)) {
                                        BackupSelectionChip(
                                            label = backupServiceDisplayName(type),
                                            selected = selectedImportTypes.contains(type),
                                            onClick = { viewModel.toggleImportType(type) }
                                        )
                                    }
                                }
                            }

                            Text(
                                text = stringResource(R.string.backupSelectionSelectedCount, selectedImportInstanceCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(12.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.backupPreviewWarning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = viewModel::resetState) {
                                Text(stringResource(R.string.cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = viewModel::applyImport,
                                enabled = selectedImportTypes.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.backupImportApply))
                            }
                        }
                    }
                }
            }
        }
        is BackupUiState.Exporting, is BackupUiState.ImportDecrypting, is BackupUiState.ImportApplying -> {
            Dialog(onDismissRequest = {}) {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = when (uiState) {
                                is BackupUiState.Exporting -> stringResource(R.string.backupExporting)
                                is BackupUiState.ImportApplying -> stringResource(R.string.backupApplying)
                                else -> stringResource(R.string.backupDecrypting)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        is BackupUiState.ImportSuccess -> {
            LaunchedEffect(Unit) {
                delay(2500)
                viewModel.resetState()
            }
            AlertDialog(
                onDismissRequest = viewModel::resetState,
                title = { Text(stringResource(R.string.backupImportTitle)) },
                text = { Text(stringResource(R.string.backupImportSuccess)) },
                confirmButton = {
                    Button(onClick = viewModel::resetState) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            )
        }
        is BackupUiState.Error -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text(stringResource(R.string.error)) },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = viewModel::dismissError) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            )
        }
        else -> {}
    }
}

@Composable
private fun BackupSelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLowest
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val strokeColor = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, strokeColor),
        modifier = Modifier,
        enabled = enabled,
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun backupServiceDisplayName(type: ServiceType): String {
    return when (type) {
        ServiceType.PORTAINER -> stringResource(R.string.service_portainer)
        ServiceType.PIHOLE -> stringResource(R.string.service_pihole)
        ServiceType.ADGUARD_HOME -> stringResource(R.string.service_adguard_home)
        ServiceType.TECHNITIUM -> stringResource(R.string.service_technitium)
        ServiceType.BESZEL -> stringResource(R.string.service_beszel)
        ServiceType.HEALTHCHECKS -> stringResource(R.string.service_healthchecks)
        ServiceType.LINUX_UPDATE -> stringResource(R.string.service_linux_update)
        ServiceType.DOCKHAND -> stringResource(R.string.service_dockhand)
        ServiceType.DOCKMON -> stringResource(R.string.service_dockmon)
        ServiceType.KOMODO -> stringResource(R.string.service_komodo)
        ServiceType.MALTRAIL -> stringResource(R.string.service_maltrail)
        ServiceType.UPTIME_KUMA -> stringResource(R.string.service_uptime_kuma)
        ServiceType.UNIFI_NETWORK -> stringResource(R.string.service_unifi_network)
        ServiceType.CRAFTY_CONTROLLER -> stringResource(R.string.service_crafty_controller)
        ServiceType.GITEA -> stringResource(R.string.service_gitea)
        ServiceType.NGINX_PROXY_MANAGER -> stringResource(R.string.service_nginx_proxy_manager)
        ServiceType.PANGOLIN -> stringResource(R.string.service_pangolin)
        ServiceType.PATCHMON -> stringResource(R.string.service_patchmon)
        ServiceType.JELLYSTAT -> stringResource(R.string.service_jellystat)
        ServiceType.PLEX -> stringResource(R.string.service_plex)
        ServiceType.RADARR -> stringResource(R.string.service_radarr)
        ServiceType.SONARR -> stringResource(R.string.service_sonarr)
        ServiceType.LIDARR -> stringResource(R.string.service_lidarr)
        ServiceType.QBITTORRENT -> stringResource(R.string.service_qbittorrent)
        ServiceType.JELLYSEERR -> stringResource(R.string.service_jellyseerr)
        ServiceType.PROWLARR -> stringResource(R.string.service_prowlarr)
        ServiceType.BAZARR -> stringResource(R.string.service_bazarr)
        ServiceType.GLUETUN -> stringResource(R.string.service_gluetun)
        ServiceType.FLARESOLVERR -> stringResource(R.string.service_flaresolverr)
        ServiceType.WAKAPI -> stringResource(R.string.service_wakapi)
        ServiceType.PROXMOX -> stringResource(R.string.service_proxmox)
        ServiceType.UNKNOWN -> type.displayName
    }
}
