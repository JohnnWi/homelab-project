package com.homelab.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import com.homelab.app.ui.theme.HomelabTheme
import com.homelab.app.ui.navigation.AppNavigation
import com.homelab.app.util.NotificationHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import javax.inject.Inject
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.ThemeMode

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesRepository: LocalPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val languageMode = preferencesRepository.languageMode.first()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageMode.code))
        }

        enableEdgeToEdge()
        
        NotificationHelper.createChannels(this)

        setContent {
            val themeMode by preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            HomelabTheme(darkTheme = darkTheme) {
                AppNavigation()
            }
        }
    }
}
