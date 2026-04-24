package com.homelab.app.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceLoginScreen(
    serviceType: ServiceType,
    onDismiss: () -> Unit,
    viewModel: ServiceLoginViewModel = hiltViewModel()
) {
    val existingInstance by viewModel.existingInstance.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    var label by remember { mutableStateOf(serviceType.displayName) }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var mfaCode by remember { mutableStateOf("") }
    var proxmoxRealm by remember { mutableStateOf("pam") }
    var proxmoxOtp by remember { mutableStateOf("") }
    var proxmoxUseApiToken by remember { mutableStateOf(false) }
    var fallbackUrl by remember { mutableStateOf("") }
    var allowSelfSigned by remember { mutableStateOf(true) }
    var showSecret by remember { mutableStateOf(false) }
    var hasSubmitted by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(existingInstance?.id) {
        val instance = existingInstance ?: return@LaunchedEffect
        label = instance.label
        url = instance.url
        if (serviceType == ServiceType.PROXMOX) {
            val storedUsername = instance.username.orEmpty()
            proxmoxUseApiToken = instance.apiKey.orEmpty().isNotBlank()
            if (storedUsername.contains("@")) {
                username = storedUsername.substringBeforeLast("@")
                proxmoxRealm = storedUsername.substringAfterLast("@").ifBlank { "pam" }
            } else {
                username = storedUsername
                proxmoxRealm = "pam"
            }
            apiKey = instance.apiKey.orEmpty()
            proxmoxOtp = instance.proxmoxOtp.orEmpty()
        } else {
            username = instance.username.orEmpty()
            apiKey = instance.apiKey.orEmpty()
        }
        fallbackUrl = instance.fallbackUrl.orEmpty()
        allowSelfSigned = instance.allowSelfSigned
        password = ""
        mfaCode = ""
    }

    LaunchedEffect(isLoading, error, existingInstance?.id) {
        if (hasSubmitted && !isLoading) {
            if (error == null) {
                onDismiss()
            } else {
                coroutineScope.launch {
                    shakeOffset.animateTo(12f, spring(stiffness = 800f, dampingRatio = 0.7f))
                    shakeOffset.animateTo(0f, spring(stiffness = 500f, dampingRatio = 0.8f))
                }
            }
        }
    }

    val isEditing = existingInstance != null
    val urlOnlyLogin = false
    val submitLabel = if (isEditing) stringResource(R.string.login_save_instance) else stringResource(R.string.login_button)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .offset { IntOffset(x = with(density) { shakeOffset.value.dp.roundToPx() }, y = 0) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ServiceIcon(
                type = serviceType,
                size = 80.dp,
                iconSize = 52.dp,
                cornerRadius = 24.dp
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isEditing) stringResource(R.string.login_edit_title, serviceType.displayName) else String.format(stringResource(R.string.login_title), serviceType.displayName),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )

            Text(
                text = if (isEditing) {
                    stringResource(R.string.login_edit_subtitle)
                } else if (urlOnlyLogin) {
                    stringResource(R.string.login_create_url_subtitle)
                } else {
                    stringResource(R.string.login_create_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))

            val hint = when (serviceType) {
                ServiceType.PORTAINER -> stringResource(R.string.login_hint_portainer_multi)
                ServiceType.PIHOLE -> stringResource(R.string.login_hint_pihole_multi)
                ServiceType.ADGUARD_HOME -> stringResource(R.string.login_hint_adguard)
                ServiceType.TECHNITIUM -> stringResource(R.string.login_hint_technitium)
                ServiceType.GITEA -> stringResource(R.string.login_hint_gitea_multi)
                ServiceType.NGINX_PROXY_MANAGER -> stringResource(R.string.login_hint_npm)
                ServiceType.HEALTHCHECKS -> stringResource(R.string.login_hint_healthchecks)
                ServiceType.PANGOLIN -> stringResource(R.string.login_hint_pangolin)
                ServiceType.LINUX_UPDATE -> stringResource(R.string.login_hint_linux_update)
                ServiceType.DOCKHAND -> stringResource(R.string.login_hint_dockhand)
                ServiceType.DOCKMON -> stringResource(R.string.login_hint_dockmon)
                ServiceType.KOMODO -> stringResource(R.string.login_hint_komodo)
                ServiceType.MALTRAIL -> stringResource(R.string.login_hint_maltrail)
                ServiceType.CRAFTY_CONTROLLER -> stringResource(R.string.login_hint_crafty_controller)
                ServiceType.JELLYSTAT -> stringResource(R.string.login_hint_jellystat)
                ServiceType.PATCHMON -> stringResource(R.string.login_hint_patchmon)
                ServiceType.PLEX -> stringResource(R.string.login_hint_plex)
                ServiceType.RADARR -> stringResource(R.string.login_hint_radarr)
                ServiceType.SONARR -> stringResource(R.string.login_hint_sonarr)
                ServiceType.LIDARR -> stringResource(R.string.login_hint_lidarr)
                ServiceType.QBITTORRENT -> stringResource(R.string.login_hint_qbittorrent)
                ServiceType.JELLYSEERR -> stringResource(R.string.login_hint_jellyseerr)
                ServiceType.PROWLARR -> stringResource(R.string.login_hint_prowlarr)
                ServiceType.BAZARR -> stringResource(R.string.login_hint_bazarr)
                ServiceType.GLUETUN -> stringResource(R.string.login_hint_gluetun)
                ServiceType.FLARESOLVERR -> stringResource(R.string.login_hint_flaresolverr)
                else -> null
            }

            if (hint != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = hint,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                if (serviceType == ServiceType.NGINX_PROXY_MANAGER) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = stringResource(R.string.warning),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.login_npm_2fa_warning),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            if (serviceType == ServiceType.HEALTHCHECKS) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.login_healthchecks_api_key_help),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.login_healthchecks_api_key_help),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            if (serviceType == ServiceType.PATCHMON) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.patchmon_login_help),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.patchmon_login_help),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            AnimatedVisibility(visible = error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = error.orEmpty(),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            fun submit() {
                hasSubmitted = true
                viewModel.clearError()
                keyboardController?.hide()
                viewModel.saveInstance(
                    serviceType = serviceType,
                    label = label,
                    url = url,
                    username = username,
                    password = password,
                    apiKey = apiKey,
                    fallbackUrl = fallbackUrl,
                    mfaCode = mfaCode,
                    allowSelfSigned = allowSelfSigned,
                    proxmoxRealm = proxmoxRealm,
                    proxmoxOtp = proxmoxOtp,
                    proxmoxUseApiToken = proxmoxUseApiToken
                )
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.login_label)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = stringResource(R.string.login_label)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.login_instance_url)) },
                placeholder = { Text(stringResource(R.string.login_url_hint)) },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = stringResource(R.string.login_instance_url)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(14.dp)
            )

            OutlinedTextField(
                value = fallbackUrl,
                onValueChange = { fallbackUrl = it },
                label = { Text(stringResource(R.string.login_fallback_url)) },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = stringResource(R.string.login_fallback_url)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                shape = RoundedCornerShape(14.dp)
            )

            Surface(
                color = if (allowSelfSigned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (allowSelfSigned) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (allowSelfSigned) {
                                stringResource(R.string.login_allow_self_signed)
                            } else {
                                stringResource(R.string.login_require_valid_tls)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (allowSelfSigned) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (allowSelfSigned) {
                                stringResource(R.string.login_tls_permissive)
                            } else {
                                stringResource(R.string.login_tls_strict)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allowSelfSigned) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Switch(
                        checked = allowSelfSigned,
                        onCheckedChange = { allowSelfSigned = it }
                    )
                }
            }

            if (
                serviceType == ServiceType.PORTAINER ||
                serviceType == ServiceType.HEALTHCHECKS ||
                serviceType == ServiceType.PANGOLIN ||
                serviceType == ServiceType.LINUX_UPDATE ||
                serviceType == ServiceType.DOCKMON ||
                serviceType == ServiceType.KOMODO ||
                serviceType == ServiceType.JELLYSTAT ||
                serviceType == ServiceType.PLEX ||
                serviceType == ServiceType.RADARR ||
                serviceType == ServiceType.SONARR ||
                serviceType == ServiceType.LIDARR ||
                serviceType == ServiceType.JELLYSEERR ||
                serviceType == ServiceType.PROWLARR ||
                serviceType == ServiceType.BAZARR ||
                serviceType == ServiceType.WAKAPI
            ) {
                SecretField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = stringResource(R.string.login_api_key_label),
                    showSecret = showSecret,
                    onToggleSecret = { showSecret = !showSecret }
                )

                if (serviceType == ServiceType.KOMODO) {
                    SecretField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.komodo_api_secret),
                        showSecret = showSecret,
                        onToggleSecret = { showSecret = !showSecret },
                        placeholder = if (isEditing) stringResource(R.string.login_keep_secret_placeholder) else null
                    )
                }

                if (serviceType == ServiceType.PANGOLIN) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.pangolin_org_id_hint)) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.pangolin_org_id_hint)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            } else if (urlOnlyLogin) {
                // No-op.
            } else if (
                serviceType == ServiceType.GLUETUN ||
                serviceType == ServiceType.FLARESOLVERR
            ) {
                SecretField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = stringResource(R.string.login_api_key_optional_label),
                    showSecret = showSecret,
                    onToggleSecret = { showSecret = !showSecret }
                )
            } else {
                if (serviceType == ServiceType.PROXMOX) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (proxmoxUseApiToken) Icons.Default.Key else Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.login_proxmox_auth_mode),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = if (proxmoxUseApiToken) {
                                        stringResource(R.string.login_proxmox_api_token_hint)
                                    } else {
                                        stringResource(R.string.login_proxmox_credentials_hint)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Switch(
                                checked = proxmoxUseApiToken,
                                onCheckedChange = {
                                    proxmoxUseApiToken = it
                                    proxmoxOtp = ""
                                    if (it) {
                                        password = ""
                                    } else {
                                        apiKey = ""
                                    }
                                }
                            )
                        }
                    }
                }

                if (serviceType == ServiceType.PROXMOX && proxmoxUseApiToken) {
                    SecretField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = stringResource(R.string.login_api_key_label),
                        showSecret = showSecret,
                        onToggleSecret = { showSecret = !showSecret },
                        placeholder = stringResource(R.string.login_proxmox_api_token_placeholder)
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = stringResource(R.string.login_proxmox_api_token_hint),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.login_proxmox_api_token_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                } else {
                if (serviceType != ServiceType.PIHOLE) {
                    val isEmailField = serviceType == ServiceType.BESZEL || serviceType == ServiceType.NGINX_PROXY_MANAGER
                    val usernameLabel = when {
                        serviceType == ServiceType.PATCHMON -> stringResource(R.string.patchmon_token_key)
                        isEmailField -> stringResource(R.string.login_email_label)
                        else -> stringResource(R.string.login_username_label)
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(usernameLabel) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = usernameLabel) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = if (isEmailField) KeyboardType.Email else KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                SecretField(
                    value = password,
                    onValueChange = { password = it },
                    label = if (serviceType == ServiceType.PATCHMON) {
                        stringResource(R.string.patchmon_token_secret)
                    } else {
                        stringResource(R.string.login_password_hint)
                    },
                    showSecret = showSecret,
                    onToggleSecret = { showSecret = !showSecret },
                    placeholder = if (isEditing) stringResource(R.string.login_keep_secret_placeholder) else null
                )

                if (serviceType == ServiceType.DOCKHAND || serviceType == ServiceType.TECHNITIUM) {
                    OutlinedTextField(
                        value = mfaCode,
                        onValueChange = { mfaCode = it },
                        label = {
                            Text(
                                if (serviceType == ServiceType.TECHNITIUM) {
                                    stringResource(R.string.login_technitium_totp_optional)
                                } else {
                                    stringResource(R.string.login_dockhand_2fa_optional)
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = if (serviceType == ServiceType.TECHNITIUM) {
                                    stringResource(R.string.login_technitium_totp_optional)
                                } else {
                                    stringResource(R.string.login_dockhand_2fa_optional)
                                }
                            )
                        },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                if (serviceType == ServiceType.PROXMOX) {
                    OutlinedTextField(
                        value = proxmoxRealm,
                        onValueChange = { proxmoxRealm = it },
                        label = { Text(stringResource(R.string.login_proxmox_realm)) },
                        leadingIcon = { Icon(Icons.Default.Apartment, contentDescription = stringResource(R.string.login_proxmox_realm)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(14.dp)
                    )

                    OutlinedTextField(
                        value = proxmoxOtp,
                        onValueChange = { proxmoxOtp = it },
                        label = { Text(stringResource(R.string.login_proxmox_otp)) },
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = stringResource(R.string.login_proxmox_otp)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                }
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "login_submit"
            )

            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    submit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(bottom = 24.dp),
                interactionSource = interactionSource,
                shape = RoundedCornerShape(14.dp),
                enabled = !isLoading,
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = submitLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    showSecret: Boolean,
    onToggleSecret: () -> Unit,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = { Icon(Icons.Default.Key, contentDescription = label) },
        trailingIcon = {
            IconButton(onClick = onToggleSecret) {
                Icon(
                    if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(if (showSecret) R.string.login_hide_secret else R.string.login_show_secret)
                )
            }
        },
        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        shape = RoundedCornerShape(14.dp)
    )
}
