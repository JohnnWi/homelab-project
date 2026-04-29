package com.homelab.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.util.ServiceType

enum class ConfiguredServicesGroup {
    HOME,
    ARR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfiguredServicesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: (ServiceType, String?) -> Unit,
    onNavigateToGroup: (ConfiguredServicesGroup) -> Unit = {},
    group: ConfiguredServicesGroup? = null,
    viewModel: SettingsViewModel
) {
    val instancesByType by viewModel.instancesByType.collectAsStateWithLifecycle()
    val preferredInstanceIdByType by viewModel.preferredInstanceIdByType.collectAsStateWithLifecycle()
    val hiddenServices by viewModel.hiddenServices.collectAsStateWithLifecycle()
    val serviceOrder by viewModel.serviceOrder.collectAsStateWithLifecycle()

    val groupedServices = when (group) {
        ConfiguredServicesGroup.HOME -> serviceOrder.filter { it.isHomeService }
        ConfiguredServicesGroup.ARR -> serviceOrder.filter { it.isArrStack }
        null -> emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (group) {
                            ConfiguredServicesGroup.HOME -> stringResource(R.string.settings_group_home_title)
                            ConfiguredServicesGroup.ARR -> stringResource(R.string.settings_group_arr_title)
                            null -> stringResource(R.string.settings_configured_services_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (group == null) {
                item {
                    ServiceGroupCard(
                        title = stringResource(R.string.settings_group_home_title),
                        subtitle = stringResource(R.string.settings_group_home_subtitle),
                        icon = Icons.Default.Home,
                        tint = MaterialTheme.colorScheme.primary,
                        configuredCount = serviceOrder.count { it.isHomeService && instancesByType[it].orEmpty().isNotEmpty() },
                        totalCount = serviceOrder.count { it.isHomeService },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToGroup(ConfiguredServicesGroup.HOME) }
                    )
                }
                item {
                    ServiceGroupCard(
                        title = stringResource(R.string.settings_group_arr_title),
                        subtitle = stringResource(R.string.settings_group_arr_subtitle),
                        icon = Icons.Default.PlayArrow,
                        tint = MaterialTheme.colorScheme.tertiary,
                        configuredCount = serviceOrder.count { it.isArrStack && instancesByType[it].orEmpty().isNotEmpty() },
                        totalCount = serviceOrder.count { it.isArrStack },
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onNavigateToGroup(ConfiguredServicesGroup.ARR) }
                    )
                }
            } else {
                items(groupedServices, key = { it.name }) { type ->
                    val index = serviceOrder.indexOf(type)
                    ServiceSettingsSection(
                        type = type,
                        instances = instancesByType[type].orEmpty(),
                        preferredInstanceId = preferredInstanceIdByType[type],
                        isHidden = hiddenServices.contains(type.name),
                        canMoveUp = index > 0,
                        canMoveDown = index in 0 until serviceOrder.lastIndex,
                        onToggleVisibility = { viewModel.toggleServiceVisibility(type) },
                        onMoveUp = { viewModel.moveService(type, -1) },
                        onMoveDown = { viewModel.moveService(type, 1) },
                        onAdd = { onNavigateToLogin(type, null) },
                        onEdit = { instance -> onNavigateToLogin(type, instance.id) },
                        onDelete = { instance -> viewModel.deleteInstance(instance.id) },
                        onSetDefault = { instance -> viewModel.setPreferredInstance(type, instance.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceGroupCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    configuredCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tint.copy(alpha = 0.15f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$configuredCount / $totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ServiceSettingsSection(
    type: ServiceType,
    instances: List<ServiceInstance>,
    preferredInstanceId: String?,
    isHidden: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleVisibility: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ServiceInstance) -> Unit,
    onDelete: (ServiceInstance) -> Unit,
    onSetDefault: (ServiceInstance) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ServiceInstance?>(null) }
    val serviceTitle = serviceDisplayNameForSettings(type)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ServiceIcon(
                            type = type,
                            size = 34.dp,
                            iconSize = 18.dp,
                            cornerRadius = 10.dp
                        )
                        Text(
                            text = serviceTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = instances.size.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (isHidden) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = stringResource(R.string.settings_hidden_badge),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false
                            )
                        }
                    }
                    Text(
                        text = if (instances.isEmpty()) {
                            stringResource(R.string.service_instances_empty)
                        } else {
                            pluralStringResource(R.plurals.settings_instances_available, instances.size, instances.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.settings_move_up)
                        )
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.settings_move_down)
                        )
                    }
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isHidden) stringResource(R.string.settings_show_service) else stringResource(R.string.settings_hide_service)
                        )
                    }
                }
            }

            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.settings_add_instance))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.settings_add_instance))
            }

            if (instances.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.settings_add_instance),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.service_instances_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                instances.forEach { instance ->
                    ServiceInstanceRow(
                        instance = instance,
                        isPreferred = instance.id == preferredInstanceId,
                        onEdit = { onEdit(instance) },
                        onDelete = { pendingDelete = instance },
                        onSetDefault = { onSetDefault(instance) }
                    )
                }
            }
        }
    }

    pendingDelete?.let { instance ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = stringResource(R.string.delete)) },
            title = { Text(stringResource(R.string.settings_delete_instance_title, instance.label.ifBlank { serviceTitle })) },
            text = { Text(stringResource(R.string.settings_delete_instance_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(instance)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}

@Composable
internal fun serviceDisplayNameForSettings(type: ServiceType): String {
    return when (type) {
        ServiceType.PORTAINER -> stringResource(R.string.service_portainer)
        ServiceType.PIHOLE -> stringResource(R.string.service_pihole)
        ServiceType.ADGUARD_HOME -> stringResource(R.string.service_adguard_home)
        ServiceType.TECHNITIUM -> stringResource(R.string.service_technitium)
        ServiceType.JELLYSTAT -> stringResource(R.string.service_jellystat)
        ServiceType.BESZEL -> stringResource(R.string.service_beszel)
        ServiceType.GITEA -> stringResource(R.string.service_gitea)
        ServiceType.NGINX_PROXY_MANAGER -> stringResource(R.string.service_nginx_proxy_manager_short)
        ServiceType.PANGOLIN -> stringResource(R.string.service_pangolin)
        ServiceType.HEALTHCHECKS -> stringResource(R.string.service_healthchecks)
        ServiceType.LINUX_UPDATE -> stringResource(R.string.service_linux_update)
        ServiceType.DOCKHAND -> stringResource(R.string.service_dockhand)
        ServiceType.DOCKMON -> stringResource(R.string.service_dockmon)
        ServiceType.KOMODO -> stringResource(R.string.service_komodo)
        ServiceType.MALTRAIL -> stringResource(R.string.service_maltrail)
        ServiceType.UPTIME_KUMA -> stringResource(R.string.service_uptime_kuma)
        ServiceType.UNIFI_NETWORK -> stringResource(R.string.service_unifi_network)
        ServiceType.CRAFTY_CONTROLLER -> stringResource(R.string.service_crafty_controller)
        ServiceType.PATCHMON -> stringResource(R.string.service_patchmon)
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

@Composable
private fun ServiceInstanceRow(
    instance: ServiceInstance,
    isPreferred: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceIcon(
                    type = instance.type,
                    size = 42.dp,
                    cornerRadius = 12.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = instance.label.ifBlank { serviceDisplayNameForSettings(instance.type) },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (isPreferred) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.home_default_badge),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Text(
                        text = instance.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    instance.fallbackUrl?.takeIf { it.isNotBlank() }?.let { fallback ->
                        Text(
                            text = stringResource(R.string.settings_fallback_value, fallback),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.edit),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!isPreferred) {
                    FilledTonalButton(
                        onClick = onSetDefault,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_default_badge),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
