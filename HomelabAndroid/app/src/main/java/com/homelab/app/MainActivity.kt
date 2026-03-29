package com.homelab.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import com.homelab.app.ui.theme.HomelabTheme
import com.homelab.app.ui.navigation.AppNavigation
import com.homelab.app.ui.security.LockScreen
import com.homelab.app.ui.security.PinSetupScreen
import com.homelab.app.ui.security.SecurityViewModel
import com.homelab.app.ui.settings.SettingsViewModel
import com.homelab.app.ui.settings.UpdatePopupDialog
import com.homelab.app.util.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

import javax.inject.Inject
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.ThemeMode
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.util.AppIconManager

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesRepository: LocalPreferencesRepository

    @Inject
    lateinit var servicesRepository: ServicesRepository

    @Inject
    lateinit var appIconManager: AppIconManager

    private var isUnlocked by mutableStateOf(false)
    private var needsSetup by mutableStateOf(true)
    private var servicesReady by mutableStateOf(false)
    private var lastBackgroundTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !servicesReady }
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                // Safety timeout: if any DataStore read or DB call hangs (e.g. corrupted
                // storage, MIUI restrictions), the splash screen must still dismiss within 10s.
                withTimeoutOrNull(10_000L) {
                    val languageMode = preferencesRepository.languageMode.first()
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageMode.code))
                    val selectedIcon = preferencesRepository.appIcon.first()
                    // Isolate icon switching: on MIUI/Xiaomi the PackageManager can throw
                    // SecurityException or NameNotFoundException for component aliases.
                    runCatching {
                        if (!appIconManager.isApplied(selectedIcon)) {
                            appIconManager.apply(selectedIcon)
                        }
                    }
                    servicesRepository.initialize()

                    val hasCompletedOnboarding = preferencesRepository.hasCompletedOnboarding.first()
                    needsSetup = !hasCompletedOnboarding
                    if (needsSetup) isUnlocked = false
                }
            } catch (e: CancellationException) {
                // Re-throw so the coroutine lifecycle is properly cancelled when the
                // Activity is destroyed before init completes.
                throw e
            } catch (_: Exception) {
                // Any other error (DataStore IO, Room exception, etc.) — continue with
                // safe defaults so the app is still usable.
            } finally {
                // Always unblock the splash screen, no matter what happened above.
                servicesReady = true
            }
        }

        enableEdgeToEdge()

        NotificationHelper.createChannels(this)

        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val biometricEnabled by preferencesRepository.biometricEnabled.collectAsState(initial = false)
            val isPinSet by preferencesRepository.appPin.collectAsState(initial = null)

            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }

            val securityVm = ViewModelProvider(this)[SecurityViewModel::class.java]

            HomelabTheme(darkTheme = darkTheme) {
                when {
                    !servicesReady -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    needsSetup -> {
                        PinSetupScreen(
                            onComplete = {
                                lifecycleScope.launch {
                                    preferencesRepository.setOnboardingCompleted(true)
                                    needsSetup = false
                                    isUnlocked = true
                                }
                            },
                            onSavePin = { pin ->
                                securityVm.savePin(pin)
                            },
                            onEnableBiometric = { enabled ->
                                securityVm.setBiometricEnabled(enabled)
                            }
                        )
                    }
                    isPinSet != null && !isUnlocked -> {
                        LockScreen(
                            biometricEnabled = biometricEnabled,
                            onUnlock = { isUnlocked = true },
                            onVerifyPin = { pin -> securityVm.verifyPin(pin) }
                        )
                    }
                    else -> {
                        val settingsVm = ViewModelProvider(this@MainActivity)[SettingsViewModel::class.java]
                        val popupState by settingsVm.updatePopupState.collectAsStateWithLifecycle()

                        AppNavigation()

                        popupState?.let { popup ->
                            UpdatePopupDialog(
                                version = popup.latestVersion,
                                changelog = popup.changelog,
                                updateUrl = popup.updateUrl,
                                onUpdate = {
                                    settingsVm.dismissUpdatePopup()
                                },
                                onDismiss = {
                                    settingsVm.dismissUpdatePopup()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastBackgroundTime = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        if (lastBackgroundTime > 0L) {
            val elapsed = System.currentTimeMillis() - lastBackgroundTime
            val gracePeriodMs = 60_000L // 1 minute
            if (elapsed > gracePeriodMs) {
                lifecycleScope.launch {
                    try {
                        val pin = preferencesRepository.appPin.first()
                        if (pin != null) {
                            isUnlocked = false
                        }
                    } catch (_: Exception) {
                        // If DataStore fails, leave the current unlock state unchanged.
                    }
                }
            }
            lastBackgroundTime = 0L
        }
    }

    override fun onResume() {
        super.onResume()
        // Run Tailscale detection off the main thread: iterating NetworkInterface can be slow
        // on devices with many interfaces and would cause jank/ANR if run synchronously.
        lifecycleScope.launch(Dispatchers.Default) {
            servicesRepository.checkTailscale()
        }
        // Refresh all services reachability on app resume
        lifecycleScope.launch {
            servicesRepository.checkAllReachability()
        }
    }
}
