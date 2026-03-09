package com.homelab.app.ui.security

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import com.homelab.app.R
import com.homelab.app.util.BiometricHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    onUnlock: () -> Unit,
    onVerifyPin: (String) -> Boolean
) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val wrongPinText = stringResource(R.string.security_wrong_pin)
    val biometricTitle = stringResource(R.string.biometric_reason)
    val biometricSubtitle = stringResource(R.string.security_biometric_desc)
    val cancelText = stringResource(R.string.cancel)

    val showBiometric = biometricEnabled && activity != null && BiometricHelper.canAuthenticate(context)

    // Auto-trigger biometric on appear
    LaunchedEffect(biometricEnabled) {
        if (showBiometric && activity != null) {
            delay(300)
            BiometricHelper.authenticate(
                activity = activity,
                title = biometricTitle,
                subtitle = biometricSubtitle,
                negativeButtonText = cancelText,
                onSuccess = { onUnlock() },
                onError = { /* User cancelled or error, show PIN pad */ }
            )
        }
    }

    PinEntryScreen(
        title = stringResource(R.string.security_enter_pin),
        subtitle = stringResource(R.string.security_enter_pin_desc),
        errorMessage = errorMessage,
        showBiometric = showBiometric,
        onBiometricTap = {
            if (activity != null) {
                BiometricHelper.authenticate(
                    activity = activity,
                    title = biometricTitle,
                    subtitle = biometricSubtitle,
                    negativeButtonText = cancelText,
                    onSuccess = { onUnlock() },
                    onError = { /* ignored */ }
                )
            }
        },
        onPinComplete = { pin ->
            if (onVerifyPin(pin)) {
                onUnlock()
            } else {
                errorMessage = wrongPinText
                scope.launch {
                    delay(1500)
                    errorMessage = null
                }
            }
        }
    )
}
