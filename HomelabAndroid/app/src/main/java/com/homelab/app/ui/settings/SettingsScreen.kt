package com.homelab.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.homelab.app.R
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val languageMode by viewModel.languageMode.collectAsStateWithLifecycle()

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

            // --- DONATION ---
            item {
                val clipboardManager = LocalClipboardManager.current
                val context = LocalContext.current
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
                                clipboardManager.setText(AnnotatedString(cryptoAddress))
                                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
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
                val isPinSet by viewModel.isPinSet.collectAsState()
                val biometricEnabled by viewModel.biometricEnabled.collectAsState()
                val context = LocalContext.current
                val canUseBiometric = remember { com.homelab.app.util.BiometricHelper.canAuthenticate(context) }
                var showDisableDialog by remember { mutableStateOf(false) }
                var showChangePinSheet by remember { mutableStateOf(false) }

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
                                            contentDescription = null,
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
                                            contentDescription = null,
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
                                            contentDescription = null,
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
                                            contentDescription = null,
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.security_not_configured),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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

                // Change PIN flow
                if (showChangePinSheet) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showChangePinSheet = false },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        var changePinStep by remember { mutableStateOf(0) } // 0: current, 1: new, 2: confirm
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
                                            title = stringResource(R.string.security_new_pin),
                                            subtitle = stringResource(R.string.security_new_pin_desc),
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
                                                } else {
                                                    pinError = context.getString(R.string.security_pin_mismatch)
                                                    scope.launch {
                                                        kotlinx.coroutines.delay(1500)
                                                        pinError = null
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
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    }
                }
            }

            // --- CONNECTED SERVICES ---
            item {
                Text(
                    text = stringResource(R.string.settings_services_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 8.dp)
                )
            }

            items(ServiceType.entries.filter { it != ServiceType.UNKNOWN }) { type ->
                ServiceSettingsRow(type, viewModel)
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

                val uriHandler = LocalUriHandler.current

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Telegram
                    Surface(
                        onClick = { uriHandler.openUri("https://t.me/finalyxre") },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Telegram",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Reddit
                    Surface(
                        onClick = { uriHandler.openUri("https://www.reddit.com/user/finalyxre/") },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Reddit",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // GitHub
                    Surface(
                        onClick = { uriHandler.openUri("https://github.com/JohnnWi/homelab-project") },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "GitHub",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }


        }
    }
}

@Composable
fun ServiceSettingsRow(
    type: ServiceType,
    viewModel: SettingsViewModel
) {
    val connections by viewModel.connections.collectAsStateWithLifecycle()
    val connection = connections[type]
    val isConnected = connection != null

    var showDisconnectDialog by remember { mutableStateOf(false) }
    var fallbackInput by remember(connection?.fallbackUrl) { mutableStateOf(connection?.fallbackUrl ?: "") }

    val hiddenServices by viewModel.hiddenServices.collectAsStateWithLifecycle()
    val isHidden = hiddenServices.contains(type.name)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Placeholder Icon
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = type.name.first().toString(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isConnected) {
                            Text(
                                text = connection?.url ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                              )
                        } else {
                            Text(
                                text = stringResource(R.string.settings_not_connected),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Visibility toggle
                IconButton(
                    onClick = { viewModel.toggleServiceVisibility(type) }
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isHidden) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = if (isHidden) androidx.compose.material.icons.Icons.Filled.VisibilityOff
                                          else androidx.compose.material.icons.Icons.Filled.Visibility,
                            contentDescription = if (isHidden) stringResource(R.string.settings_show_service) else stringResource(R.string.settings_hide_service),
                            tint = if (isHidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp).size(16.dp)
                        )
                    }
                }

                if (isConnected) {
                    IconButton(
                        onClick = { showDisconnectDialog = true }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = stringResource(R.string.settings_disconnect),
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    )
                }
            }

            if (isConnected) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (fallbackInput.isEmpty()) {
                            Text(
                                stringResource(R.string.settings_fallback_url),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                        BasicTextField(
                            value = fallbackInput,
                            onValueChange = { fallbackInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { state ->
                                    if (!state.isFocused && fallbackInput != connection?.fallbackUrl) {
                                        viewModel.saveFallbackUrl(type, fallbackInput)
                                    }
                                },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    viewModel.saveFallbackUrl(type, fallbackInput)
                                }
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_disconnect_confirm_title, type.name.lowercase())) },
            text = { Text(stringResource(R.string.settings_disconnect_confirm_desc)) },
            confirmButton = {
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                Button(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        viewModel.disconnectService(type)
                        showDisconnectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_disconnect))
                }
            },
            dismissButton = {
                val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                TextButton(onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    showDisconnectDialog = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
