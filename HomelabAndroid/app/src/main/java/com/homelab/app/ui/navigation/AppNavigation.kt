package com.homelab.app.ui.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.homelab.app.R
import com.homelab.app.ui.home.HomeScreen
import com.homelab.app.ui.settings.SettingsScreen
import com.homelab.app.ui.settings.DebugLogsScreen
import com.homelab.app.ui.settings.ConfiguredServicesScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.homelab.app.ui.security.LockScreen
import com.homelab.app.util.ServiceType

sealed class Screen(
    val route: String,
    val titleResId: Int,
    val activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Home : Screen("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home)
    data object Media : Screen("media", R.string.nav_media, Icons.Filled.PlayArrow, Icons.Outlined.PlayArrow)
    data object Bookmarks : Screen("bookmarks", R.string.nav_bookmarks, Icons.Filled.Bookmark, Icons.Outlined.Bookmark)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

private fun dashboardRoute(type: ServiceType, instanceId: String): String {
    return when (type) {
        ServiceType.PORTAINER -> "portainer/$instanceId/dashboard"
        ServiceType.PIHOLE -> "pihole/$instanceId/dashboard"
        ServiceType.ADGUARD_HOME -> "adguard/$instanceId/dashboard"
        ServiceType.TECHNITIUM -> "technitium/$instanceId/dashboard"
        ServiceType.JELLYSTAT -> "jellystat/$instanceId/dashboard"
        ServiceType.BESZEL -> "beszel/$instanceId/dashboard"
        ServiceType.GITEA -> "gitea/$instanceId/dashboard"
        ServiceType.LINUX_UPDATE -> "linux-update/$instanceId/dashboard"
        ServiceType.DOCKHAND -> "dockhand/$instanceId/dashboard"
        ServiceType.NGINX_PROXY_MANAGER -> "nginxpm/$instanceId/dashboard"
        ServiceType.PANGOLIN -> "pangolin/$instanceId/dashboard"
        ServiceType.HEALTHCHECKS -> "healthchecks/$instanceId/dashboard"
        ServiceType.PATCHMON -> "patchmon/$instanceId/dashboard"
        ServiceType.PLEX -> "plex/$instanceId/dashboard"
        ServiceType.JELLYFIN -> "jellyfin/$instanceId/dashboard"
        ServiceType.IMMICH -> "immich/$instanceId/dashboard"
        ServiceType.GRAFANA -> "grafana/$instanceId/dashboard"
        ServiceType.SABNZBD -> "sabnzbd/$instanceId/dashboard"
        ServiceType.PROXMOX -> "proxmox/$instanceId/dashboard"
        ServiceType.PROXMOX_BACKUP_SERVER -> "pbs/$instanceId/dashboard"
        ServiceType.TDARR -> "tdarr/$instanceId/dashboard"
        ServiceType.RADARR,
        ServiceType.SONARR,
        ServiceType.LIDARR,
        ServiceType.QBITTORRENT,
        ServiceType.JELLYSEERR,
        ServiceType.PROWLARR,
        ServiceType.BAZARR,
        ServiceType.GLUETUN,
        ServiceType.FLARESOLVERR -> "media/${type.name}/$instanceId/dashboard"
        ServiceType.UNKNOWN -> Screen.Home.route
    }
}

private fun technitiumDashboardRoute(instanceId: String): String {
    return "technitium/$instanceId/dashboard"
}

private fun loginRoute(type: ServiceType, instanceId: String? = null): String {
    val encodedId = instanceId?.let(Uri::encode)
    return if (encodedId == null) {
        "login/${type.name}"
    } else {
        "login/${type.name}?instanceId=$encodedId"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Media, Screen.Bookmarks, Screen.Settings)
    var configuredServicesUnlocked by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val isServiceChild = currentDestination?.route?.contains("/") == true
            val isMediaChild = currentDestination?.route?.startsWith("media/") == true

            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true ||
                            (screen.route == Screen.Home.route && isServiceChild && !isMediaChild) ||
                            (screen.route == Screen.Media.route && isMediaChild)
                        val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        val label = stringResource(screen.titleResId)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Avoid navigating if the user taps the tab for the screen they're already on
                                    if (currentDestination?.route == screen.route) return@clickable

                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                    if (screen == Screen.Settings && isServiceChild) {
                                        navController.navigate(screen.route) {
                                            launchSingleTop = true
                                        }
                                    } else if (screen == Screen.Home && currentDestination?.route == Screen.Settings.route) {
                                        // If Settings was opened from a service dashboard, return there instead of resetting to main Home.
                                        if (navController.popBackStack()) return@clickable
                                    } else {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = false
                                            }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (selected) screen.activeIcon else screen.inactiveIcon,
                                contentDescription = label,
                                tint = color,
                                modifier = Modifier.size(if (selected) 28.dp else 26.dp)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = color,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToService = { type, instanceId ->
                        navController.navigate(dashboardRoute(type, instanceId))
                    },
                    onNavigateToLogin = { type, instanceId ->
                        navController.navigate(loginRoute(type, instanceId))
                    }
                )
            }

            composable(Screen.Media.route) {
                com.homelab.app.ui.media.MediaArrScreen(
                    onNavigateToLogin = { type, instanceId ->
                        navController.navigate(loginRoute(type, instanceId))
                    },
                    onNavigateToService = { type, instanceId ->
                        navController.navigate(dashboardRoute(type, instanceId))
                    }
                )
            }

            composable(Screen.Bookmarks.route) {
                com.homelab.app.ui.bookmarks.BookmarksScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToLogin = { type, instanceId ->
                        navController.navigate(loginRoute(type, instanceId))
                    },
                    onNavigateToDebugLogs = {
                        navController.navigate("settings/debug-logs")
                    },
                    onNavigateToConfiguredServices = {
                        navController.navigate("settings/configured-services")
                    },
                    onNavigateToBackup = {
                        navController.navigate("settings/backup")
                    }
                )
            }

            composable("settings/debug-logs") {
                DebugLogsScreen(onNavigateBack = { navController.popBackStack() })
            }

            composable("settings/configured-services") {
                val settingsVm: com.homelab.app.ui.settings.SettingsViewModel = hiltViewModel()
                val isPinSet by settingsVm.isPinSet.collectAsStateWithLifecycle()
                val biometricEnabled by settingsVm.biometricEnabled.collectAsStateWithLifecycle()

                if (isPinSet && !configuredServicesUnlocked) {
                    LockScreen(
                        biometricEnabled = biometricEnabled,
                        onUnlock = { configuredServicesUnlocked = true },
                        onVerifyPin = { settingsVm.verifyPin(it) }
                    )
                } else {
                    ConfiguredServicesScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLogin = { type, instanceId ->
                            navController.navigate(loginRoute(type, instanceId))
                        },
                        onNavigateToGroup = { group ->
                            navController.navigate("settings/configured-services/${group.name}")
                        },
                        viewModel = settingsVm
                    )
                }
            }

            composable(
                route = "settings/configured-services/{group}",
                arguments = listOf(androidx.navigation.navArgument("group") { type = NavType.StringType })
            ) { backStackEntry ->
                val settingsVm: com.homelab.app.ui.settings.SettingsViewModel = hiltViewModel()
                val groupArg = backStackEntry.arguments?.getString("group")
                val group = runCatching {
                    com.homelab.app.ui.settings.ConfiguredServicesGroup.valueOf(groupArg.orEmpty())
                }.getOrNull() ?: com.homelab.app.ui.settings.ConfiguredServicesGroup.HOME
                ConfiguredServicesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogin = { type, instanceId ->
                        navController.navigate(loginRoute(type, instanceId))
                    },
                    group = group,
                    viewModel = settingsVm
                )
            }

            composable("settings/backup") {
                com.homelab.app.ui.backup.BackupScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "login/{serviceType}?instanceId={instanceId}",
                arguments = listOf(
                    androidx.navigation.navArgument("serviceType") { type = NavType.StringType },
                    androidx.navigation.navArgument("instanceId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val typeName = backStackEntry.arguments?.getString("serviceType") ?: return@composable
                val serviceType = ServiceType.fromStoredName(typeName)
                if (serviceType == ServiceType.UNKNOWN) return@composable
                com.homelab.app.ui.login.ServiceLoginScreen(
                    serviceType = serviceType,
                    onDismiss = { navController.popBackStack() }
                )
            }

            composable(
                route = "portainer/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.portainer.PortainerDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.PORTAINER, newInstanceId)) {
                                popUpTo("portainer/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    },
                    onNavigateToContainers = { endpointId ->
                        navController.navigate("portainer/$instanceId/containers/$endpointId")
                    }
                )
            }

            composable(
                route = "portainer/{instanceId}/containers/{endpointId}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("endpointId") { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.portainer.ContainerListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { endpointId, containerId ->
                        navController.navigate("portainer/$instanceId/containers/$endpointId/detail/${Uri.encode(containerId)}")
                    }
                )
            }

            composable(
                route = "portainer/{instanceId}/containers/{endpointId}/detail/{containerId}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("endpointId") { type = NavType.IntType },
                    androidx.navigation.navArgument("containerId") { type = NavType.StringType }
                )
            ) {
                com.homelab.app.ui.portainer.ContainerDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "pihole/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.pihole.PiholeDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.PIHOLE, newInstanceId)) {
                                popUpTo("pihole/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    },
                    onNavigateToDomains = { navController.navigate("pihole/$instanceId/domains") },
                    onNavigateToQueryLog = { navController.navigate("pihole/$instanceId/queries") }
                )
            }

            composable(
                route = "pihole/{instanceId}/domains",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) {
                com.homelab.app.ui.pihole.PiholeDomainListScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "pihole/{instanceId}/queries",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) {
                com.homelab.app.ui.pihole.PiholeQueryLogScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "adguard/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.adguard.AdGuardHomeDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.ADGUARD_HOME, newInstanceId)) {
                                popUpTo("adguard/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    },
                    onNavigateToQueryLog = { status ->
                        val encodedStatus = Uri.encode(status)
                        navController.navigate("adguard/$instanceId/queries?status=$encodedStatus")
                    },
                    onNavigateToFilters = { navController.navigate("adguard/$instanceId/filters") },
                    onNavigateToRewrites = { navController.navigate("adguard/$instanceId/rewrites") },
                    onNavigateToUserRules = { navController.navigate("adguard/$instanceId/user-rules") },
                    onNavigateToBlockedServices = { navController.navigate("adguard/$instanceId/blocked-services") }
                )
            }

            composable(
                route = "adguard/{instanceId}/queries?status={status}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("status") { type = NavType.StringType; defaultValue = "all" }
                )
            ) { backStackEntry ->
                val status = backStackEntry.arguments?.getString("status") ?: "all"
                com.homelab.app.ui.adguard.AdGuardHomeQueryLogScreen(
                    initialStatus = status,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "adguard/{instanceId}/filters",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) {
                com.homelab.app.ui.adguard.AdGuardHomeFiltersScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "adguard/{instanceId}/rewrites",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) {
                com.homelab.app.ui.adguard.AdGuardHomeRewritesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "adguard/{instanceId}/user-rules",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) {
                com.homelab.app.ui.adguard.AdGuardHomeUserRulesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "adguard/{instanceId}/blocked-services",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) {
                com.homelab.app.ui.adguard.AdGuardHomeBlockedServicesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "jellystat/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.jellystat.JellystatDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.JELLYSTAT, newInstanceId)) {
                                popUpTo("jellystat/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "beszel/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.beszel.BeszelDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.BESZEL, newInstanceId)) {
                                popUpTo("beszel/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    },
                    onNavigateToSystem = { systemId ->
                        navController.navigate("beszel/$instanceId/system/${Uri.encode(systemId)}")
                    }
                )
            }

            composable(
                route = "beszel/{instanceId}/system/{systemId}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("systemId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                val systemId = android.net.Uri.decode(backStackEntry.arguments?.getString("systemId") ?: return@composable)
                com.homelab.app.ui.beszel.BeszelSystemDetailScreen(
                    systemId = systemId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToContainers = {
                        navController.navigate("beszel/$instanceId/system/${Uri.encode(systemId)}/containers")
                    }
                )
            }

            composable(
                route = "beszel/{instanceId}/system/{systemId}/containers",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("systemId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val systemId = android.net.Uri.decode(backStackEntry.arguments?.getString("systemId") ?: return@composable)
                com.homelab.app.ui.beszel.BeszelContainersScreen(
                    systemId = systemId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "gitea/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.gitea.GiteaDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.GITEA, newInstanceId)) {
                                popUpTo("gitea/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    },
                    onNavigateToRepo = { owner, repo ->
                        navController.navigate("gitea/$instanceId/repo/${Uri.encode(owner)}/${Uri.encode(repo)}")
                    }
                )
            }

            composable(
                route = "linux-update/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.linux_update.LinuxUpdateDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.LINUX_UPDATE, newInstanceId)) {
                                popUpTo("linux-update/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "technitium/{instanceId}/dashboard",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.technitium.TechnitiumDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.TECHNITIUM, newInstanceId)) {
                                popUpTo("technitium/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "dockhand/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.dockhand.DockhandDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.DOCKHAND, newInstanceId)) {
                                popUpTo("dockhand/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "healthchecks/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.healthchecks.HealthchecksDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.HEALTHCHECKS, newInstanceId)) {
                                popUpTo("healthchecks/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    },
                    onNavigateToChecks = { filter ->
                        navController.navigate("healthchecks/$instanceId/checks?filter=${filter.status}")
                    },
                    onNavigateToEditor = { checkId ->
                        val encoded = checkId?.let(Uri::encode)
                        val route = if (encoded == null) {
                            "healthchecks/$instanceId/editor"
                        } else {
                            "healthchecks/$instanceId/editor?checkId=$encoded"
                        }
                        navController.navigate(route)
                    },
                    onNavigateToBadges = { navController.navigate("healthchecks/$instanceId/badges") },
                    onNavigateToChannels = { navController.navigate("healthchecks/$instanceId/channels") }
                )
            }

            composable(
                route = "pangolin/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) {
                com.homelab.app.ui.pangolin.PangolinDashboardScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "healthchecks/{instanceId}/checks?filter={filter}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("filter") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                val filter = backStackEntry.arguments?.getString("filter")
                val initialFilter = com.homelab.app.ui.healthchecks.HealthchecksStatusFilter.values()
                    .firstOrNull { it.status == filter } ?: com.homelab.app.ui.healthchecks.HealthchecksStatusFilter.ALL
                com.homelab.app.ui.healthchecks.HealthchecksChecksScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCheckDetail = { checkId ->
                        val encoded = Uri.encode(checkId)
                        navController.navigate("healthchecks/$instanceId/check?checkId=$encoded")
                    },
                    onNavigateToEditor = { checkId ->
                        val encoded = checkId?.let(Uri::encode)
                        val route = if (encoded == null) {
                            "healthchecks/$instanceId/editor"
                        } else {
                            "healthchecks/$instanceId/editor?checkId=$encoded"
                        }
                        navController.navigate(route)
                    },
                    initialFilter = initialFilter
                )
            }

            composable(
                route = "healthchecks/{instanceId}/check?checkId={checkId}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("checkId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.healthchecks.HealthchecksDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEditor = { id ->
                        val encoded = Uri.encode(id)
                        navController.navigate("healthchecks/$instanceId/editor?checkId=$encoded")
                    }
                )
            }

            composable(
                route = "healthchecks/{instanceId}/editor?checkId={checkId}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("checkId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                com.homelab.app.ui.healthchecks.HealthchecksEditorScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "healthchecks/{instanceId}/badges",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) {
                com.homelab.app.ui.healthchecks.HealthchecksBadgesScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "healthchecks/{instanceId}/channels",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) {
                com.homelab.app.ui.healthchecks.HealthchecksChannelsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "nginxpm/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.nginxpm.NpmDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.NGINX_PROXY_MANAGER, newInstanceId)) {
                                popUpTo("nginxpm/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "gitea/{instanceId}/repo/{owner}/{repo}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("owner") { type = NavType.StringType },
                    androidx.navigation.navArgument("repo") { type = NavType.StringType }
                )
            ) {
                com.homelab.app.ui.gitea.GiteaRepoDetailScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }

            composable(
                route = "patchmon/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.patchmon.PatchmonDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.PATCHMON, newInstanceId)) {
                                popUpTo("patchmon/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    },
                    onNavigateToHostDetail = { hostId ->
                        navController.navigate("patchmon/$instanceId/host/${Uri.encode(hostId)}")
                    }
                )
            }

            composable(
                route = "patchmon/{instanceId}/host/{hostId}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("hostId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.patchmon.PatchmonHostDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.PATCHMON, newInstanceId)) {
                                popUpTo("patchmon/$instanceId/dashboard") { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }

            composable(
                route = "plex/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.plex.PlexDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.PLEX, newInstanceId)) {
                                popUpTo("plex/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "jellyfin/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.jellyfin.JellyfinDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.JELLYFIN, newInstanceId)) {
                                popUpTo("jellyfin/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "immich/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.immich.ImmichDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.IMMICH, newInstanceId)) {
                                popUpTo("immich/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "grafana/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.grafana.GrafanaDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.GRAFANA, newInstanceId)) {
                                popUpTo("grafana/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "sabnzbd/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.sabnzbd.SABnzbdDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.SABNZBD, newInstanceId)) {
                                popUpTo("sabnzbd/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "proxmox/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.PROXMOX, newInstanceId)) {
                                popUpTo("proxmox/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "pbs/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.pbs.PBSDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.PROXMOX_BACKUP_SERVER, newInstanceId)) {
                                popUpTo("pbs/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "tdarr/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.tdarr.TdarrDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.TDARR, newInstanceId)) {
                                popUpTo("tdarr/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "media/{serviceType}/{instanceId}/dashboard",
                arguments = listOf(
                    androidx.navigation.navArgument("serviceType") { type = NavType.StringType },
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val typeName = backStackEntry.arguments?.getString("serviceType") ?: return@composable
                val mediaType = ServiceType.fromStoredName(typeName)
                com.homelab.app.ui.media.MediaServiceDashboardScreen(
                    serviceType = mediaType,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
