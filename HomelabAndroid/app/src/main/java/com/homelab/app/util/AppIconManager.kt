package com.homelab.app.util

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.util.Log
import com.homelab.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppIconManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isApplied(option: AppIconOption): Boolean {
        return try {
            val packageManager = context.packageManager
            val packageName = context.packageName
            val selectedComponent = option.componentName(packageName)
            val selectedState = packageManager.getComponentEnabledSetting(selectedComponent)
            if (selectedState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                return false
            }

            AppIconOption.entries
                .filter { it != option }
                .all { candidate ->
                    packageManager.getComponentEnabledSetting(candidate.componentName(packageName)) ==
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
        } catch (error: Exception) {
            // On MIUI/Xiaomi devices the PackageManager may throw SecurityException or
            // NameNotFoundException for component aliases. Treat as "not applied" so the
            // caller can attempt to re-apply without crashing.
            Log.e("AppIconManager", "Failed to check icon state for ${option.persistedValue}", error)
            false
        }
    }

    fun apply(option: AppIconOption, relaunchApp: Boolean = false): Boolean {
        val packageManager = context.packageManager
        val packageName = context.packageName
        var changed = false

        if (BuildConfig.DEBUG) {
            val debugRunAlias = ComponentName(packageName, "$packageName.launcher.DebugLauncherAlias")
            if (packageManager.getComponentEnabledSetting(debugRunAlias) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                try {
                    packageManager.setComponentEnabledSetting(
                        debugRunAlias,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                    changed = true
                } catch (error: Exception) {
                    Log.e("AppIconManager", "Failed to keep debug run alias enabled", error)
                }
            }
        }

        val orderedOptions = listOf(option) + AppIconOption.entries.filter { it != option }

        for (candidate in orderedOptions) {
            val componentName = candidate.componentName(packageName)
            val desiredState = if (candidate == option) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            val currentState = packageManager.getComponentEnabledSetting(componentName)
            if (currentState == desiredState) {
                continue
            }

            try {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    desiredState,
                    PackageManager.DONT_KILL_APP
                )
                changed = true
            } catch (error: Exception) {
                Log.e("AppIconManager", "Failed to set launcher alias state for ${candidate.persistedValue}", error)
            }
        }

        return changed
    }
}
