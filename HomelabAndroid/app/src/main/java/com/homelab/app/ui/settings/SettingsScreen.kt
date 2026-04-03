package com.homelab.app.ui.settings

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.homelab.app.BuildConfig
import com.homelab.app.R
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.AppIconOption
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToLogin: (ServiceType, String?) -> Unit = { _, _ -> },
    onNavigateToDebugLogs: () -> Unit = {},
    onNavigateToConfiguredServices: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val languageMode by viewModel.languageMode.collectAsStateWithLifecycle()
    val instancesByType by viewModel.instancesByType.collectAsStateWithLifecycle()
    val preferredInstanceIdByType by viewModel.preferredInstanceIdByType.collectAsStateWithLifecycle()
    val hiddenServices by viewModel.hiddenServices.collectAsStateWithLifecycle()
    val serviceOrder by viewModel.serviceOrder.collectAsStateWithLifecycle()
    val isPinSet by viewModel.isPinSet.collectAsStateWithLifecycle()


    val appIcon by viewModel.appIcon.collectAsStateWithLifecycle()
    val appIconApplying by viewModel.appIconApplying.collectAsStateWithLifecycle()
    val updateBannerState by viewModel.updateBannerState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val homeConfiguredCount = remember(serviceOrder, instancesByType) {
        serviceOrder.count { it.isHomeService && instancesByType[it].orEmpty().isNotEmpty() }
    }
    val homeTotalCount = remember(serviceOrder) { serviceOrder.count { it.isHomeService } }
    val arrConfiguredCount = remember(serviceOrder, instancesByType) {
        serviceOrder.count { it.isArrStack && instancesByType[it].orEmpty().isNotEmpty() }
    }
    val arrTotalCount = remember(serviceOrder) { serviceOrder.count { it.isArrStack } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            updateBannerState?.let { update ->
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.settings_update_banner_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Text(
                                text = stringResource(
                                    R.string.settings_update_banner_body,
                                    update.latestVersion,
                                    update.currentVersion
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { uriHandler.openUri(update.updateUrl) }
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_update_action),
                                        maxLines = 1
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.dismissUpdateBanner() }
                                ) {
                                    Text(
                                        text = stringResource(R.string.settings_update_dismiss),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val appVersion = BuildConfig.VERSION_NAME

            // --- DONATION ---
            item {
                val clipboard = LocalClipboard.current
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val cryptoAddress = "0x649641868e6876c2c1f04584a95679e01c1aaf0d"

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_support_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.settings_support_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )

                        Surface(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText(context.getString(R.string.settings_donation_clip_label), cryptoAddress))
                                    )
                                    Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cryptoAddress.take(8) + "..." + cryptoAddress.takeLast(6),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                                Text(
                                    text = stringResource(R.string.copy),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // --- CONFIGURED SERVICES ---
            item {
                Text(
                    text = stringResource(R.string.settings_configured_services_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 8.dp)
                )

                Surface(
                    onClick = onNavigateToConfiguredServices,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = stringResource(R.string.settings_configured_services_title),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_configured_services_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = "${stringResource(R.string.settings_group_home_title)} $homeConfiguredCount/$homeTotalCount",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = "ARR $arrConfiguredCount/$arrTotalCount",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (isPinSet) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.settings_configured_services_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- THEME SELECTOR ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_theme_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = themeMode == com.homelab.app.data.repository.ThemeMode.LIGHT,
                            onClick = { viewModel.setThemeMode(com.homelab.app.data.repository.ThemeMode.LIGHT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) { Text(stringResource(R.string.settings_theme_light)) }
                        
                        SegmentedButton(
                            selected = themeMode == com.homelab.app.data.repository.ThemeMode.DARK,
                            onClick = { viewModel.setThemeMode(com.homelab.app.data.repository.ThemeMode.DARK) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) { Text(stringResource(R.string.settings_theme_dark)) }
                        
                        SegmentedButton(
                            selected = themeMode == com.homelab.app.data.repository.ThemeMode.SYSTEM,
                            onClick = { viewModel.setThemeMode(com.homelab.app.data.repository.ThemeMode.SYSTEM) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) { Text(stringResource(R.string.settings_theme_auto)) }
                    }
                }
            }




            item {
                val iconRows = remember { AppIconOption.entries.chunked(3) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_app_icon_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (appIconApplying) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            iconRows.forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    row.forEach { option ->
                                        AppIconOptionCard(
                                            option = option,
                                            isSelected = appIcon == option,
                                            enabled = !appIconApplying,
                                            onClick = { viewModel.setAppIcon(option) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(3 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- LANGUAGE SELECTOR ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.settings_language_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        com.homelab.app.data.repository.LanguageMode.entries.forEach { lang ->
                            val isSelected = languageMode == lang
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(56.dp),
                                onClick = { viewModel.setLanguageMode(lang) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = lang.flag,
                                        style = MaterialTheme.typography.headlineMedium,
                                        modifier = if (!isSelected) Modifier.alpha(0.5f) else Modifier
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- SECURITY ---
            item {
                val biometricEnabled by viewModel.biometricEnabled.collectAsState()
                val context = LocalContext.current
                val canUseBiometric = remember { com.homelab.app.util.BiometricHelper.canAuthenticate(context) }
                var showDisableDialog by remember { mutableStateOf(false) }
                var showChangePinSheet by remember { mutableStateOf(false) }
                var showBiometricPrompt by remember { mutableStateOf(false) }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.security_title).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, start = 8.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp
                    ) {
                        Column {
                            if (isPinSet) {
                                // Biometric toggle
                                if (canUseBiometric) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = stringResource(R.string.security_enable_biometric),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.security_enable_biometric),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = stringResource(R.string.security_biometric_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = biometricEnabled,
                                            onCheckedChange = { viewModel.setBiometricEnabled(it) }
                                        )
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                }

                                // Change PIN
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { showChangePinSheet = true },
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = stringResource(R.string.security_change_pin),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.security_change_pin),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = stringResource(R.string.security_change_pin),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                                // Disable security
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { showDisableDialog = true },
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = stringResource(R.string.security_disable),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stringResource(R.string.security_disable),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { showChangePinSheet = true },
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = stringResource(R.string.security_setup_pin),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.security_setup_pin),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = stringResource(R.string.security_setup_pin_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = stringResource(R.string.security_setup_pin),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Disable security confirmation dialog
                if (showDisableDialog) {
                    AlertDialog(
                        onDismissRequest = { showDisableDialog = false },
                        title = { Text(stringResource(R.string.security_disable_confirm)) },
                        text = { Text(stringResource(R.string.security_disable_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.clearSecurity()
                                showDisableDialog = false
                            }) {
                                Text(
                                    stringResource(R.string.security_disable),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDisableDialog = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                    )
                }

                // Biometric prompt after PIN setup
                if (showBiometricPrompt) {
                    AlertDialog(
                        onDismissRequest = {
                            showBiometricPrompt = false
                            android.widget.Toast.makeText(context, context.getString(R.string.security_pin_saved), android.widget.Toast.LENGTH_SHORT).show()
                        },
                        title = { Text(stringResource(R.string.security_biometric_prompt_title)) },
                        text = { Text(stringResource(R.string.security_biometric_prompt_desc)) },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.setBiometricEnabled(true)
                                showBiometricPrompt = false
                                android.widget.Toast.makeText(context, context.getString(R.string.security_pin_saved), android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Text(stringResource(R.string.security_biometric_prompt_enable))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                showBiometricPrompt = false
                                android.widget.Toast.makeText(context, context.getString(R.string.security_pin_saved), android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Text(stringResource(R.string.security_skip))
                            }
                        }
                    )
                }

                // Change PIN flow
                if (showChangePinSheet) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showChangePinSheet = false },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        // Capture initial step once — do NOT use isPinSet as remember key or
                        // savePin() triggers recompose (isPinSet flips true), remember re-runs
                        // and resets step to 0 (current-pin) before the dialog closes → loop.
                        var changePinStep by remember { mutableStateOf(if (isPinSet) 0 else 1) } // 0: current, 1: new/setup, 2: confirm
                        var newPinInput by remember { mutableStateOf("") }
                        var pinError by remember { mutableStateOf<String?>(null) }
                        val scope = rememberCoroutineScope()
                        
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            androidx.compose.animation.AnimatedContent(
                                targetState = changePinStep,
                                transitionSpec = {
                                    androidx.compose.animation.slideInHorizontally { it } togetherWith androidx.compose.animation.slideOutHorizontally { -it }
                                },
                                label = "change_pin_step"
                            ) { step ->
                                when(step) {
                                    0 -> {
                                        com.homelab.app.ui.security.PinEntryScreen(
                                            title = stringResource(R.string.security_current_pin),
                                            subtitle = stringResource(R.string.security_current_pin_desc),
                                            errorMessage = pinError,
                                            onPinComplete = { pin ->
                                                if (viewModel.verifyPin(pin)) {
                                                    pinError = null
                                                    changePinStep = 1
                                                } else {
                                                    pinError = context.getString(R.string.security_wrong_pin)
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(1500)
                                                        pinError = null
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    1 -> {
                                        com.homelab.app.ui.security.PinEntryScreen(
                                            title = stringResource(if (isPinSet) R.string.security_new_pin else R.string.security_setup_pin),
                                            subtitle = stringResource(if (isPinSet) R.string.security_new_pin_desc else R.string.security_setup_pin_desc),
                                            onPinComplete = { pin ->
                                                newPinInput = pin
                                                changePinStep = 2
                                            }
                                        )
                                    }
                                    2 -> {
                                        com.homelab.app.ui.security.PinEntryScreen(
                                            title = stringResource(R.string.security_confirm_pin),
                                            subtitle = stringResource(R.string.security_confirm_pin_desc),
                                            errorMessage = pinError,
                                            onPinComplete = { pin ->
                                                if (pin == newPinInput) {
                                                    viewModel.savePin(pin)
                                                    showChangePinSheet = false
                                                    if (canUseBiometric && !biometricEnabled) {
                                                        showBiometricPrompt = true
                                                    } else {
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            context.getString(R.string.security_pin_saved),
                                                            android.widget.Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                } else {
                                                    pinError = context.getString(R.string.security_pin_mismatch)
                                                    newPinInput = ""
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(1500)
                                                        pinError = null
                                                        changePinStep = 1
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Close button
                            IconButton(
                                onClick = { showChangePinSheet = false },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(top = 48.dp, start = 8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                            }
                        }
                    }
                }
            }

            // --- BACKUP ---
            item {
                Text(
                    text = stringResource(R.string.backupTitle).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 8.dp)
                )

                Surface(
                    onClick = onNavigateToBackup,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.backupTitle),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.backupTitle),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.backupInfoDesc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.backupTitle),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // --- CONTACTS ---
            item {
                Text(
                    text = stringResource(R.string.settings_contacts_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ContactChip(
                        label = stringResource(R.string.settings_contact_telegram),
                        iconUrl = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/telegram.png",
                        onClick = { uriHandler.openUri("https://t.me/finalyxre") },
                        modifier = Modifier.weight(1f)
                    )
                    ContactChip(
                        label = stringResource(R.string.settings_contact_reddit),
                        iconUrl = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/reddit.png",
                        onClick = { uriHandler.openUri("https://www.reddit.com/user/finalyxre/") },
                        modifier = Modifier.weight(1f)
                    )
                    ContactChip(
                        label = stringResource(R.string.settings_contact_repository),
                        iconUrl = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/github.png",
                        onClick = { uriHandler.openUri("https://github.com/JohnnWi/homelab-project") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // --- DEBUG ---
            item {
                Text(
                    text = stringResource(R.string.settings_debug_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 8.dp)
                )

                Surface(
                    onClick = onNavigateToDebugLogs,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = stringResource(R.string.debug_logs_title),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.debug_logs_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.settings_debug_logs_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = stringResource(R.string.debug_logs_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.settings_version_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 8.dp)
                )

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.settings_version_label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = appVersion,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }


        }
    }
}

@Composable
private fun AppIconOptionCard(
    option: AppIconOption,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBorder = if (isSelected) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    }
    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = cardBorder,
        color = cardColor,
                                modifier = modifier
            .aspectRatio(1f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                Image(
                    painter = painterResource(option.previewDrawableRes),
                    contentDescription = stringResource(option.labelRes),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(74.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }

            if (isSelected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactChip(
    label: String,
    iconUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            AsyncImage(
                model = iconUrl,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}
