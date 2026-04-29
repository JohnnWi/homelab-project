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
        ServiceType.DOCKMON -> "dockmon/$instanceId/dashboard"
        ServiceType.KOMODO -> "komodo/$instanceId/dashboard"
        ServiceType.MALTRAIL -> "maltrail/$instanceId/dashboard"
        ServiceType.UPTIME_KUMA -> "uptime-kuma/$instanceId/dashboard"
        ServiceType.UNIFI_NETWORK -> "unifi/$instanceId/dashboard"
        ServiceType.CRAFTY_CONTROLLER -> "crafty/$instanceId/dashboard"
        ServiceType.NGINX_PROXY_MANAGER -> "nginxpm/$instanceId/dashboard"
        ServiceType.PANGOLIN -> "pangolin/$instanceId/dashboard"
        ServiceType.HEALTHCHECKS -> "healthchecks/$instanceId/dashboard"
        ServiceType.PATCHMON -> "patchmon/$instanceId/dashboard"
        ServiceType.WAKAPI -> "wakapi/$instanceId/dashboard"
        ServiceType.PLEX -> "plex/$instanceId/dashboard"
        ServiceType.PROXMOX -> "proxmox/$instanceId/dashboard"
        ServiceType.PTERODACTYL -> "pterodactyl/$instanceId/dashboard"
        ServiceType.CALAGOPUS -> "calagopus/$instanceId/dashboard"
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
                route = "unifi/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.unifi.UnifiDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.UNIFI_NETWORK, newInstanceId)) {
                                popUpTo("unifi/$instanceId/dashboard") { inclusive = true }
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
                route = "dockmon/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.dockmon.DockmonDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.DOCKMON, newInstanceId)) {
                                popUpTo("dockmon/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "komodo/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.komodo.KomodoDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.KOMODO, newInstanceId)) {
                                popUpTo("komodo/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "maltrail/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.maltrail.MaltrailDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.MALTRAIL, newInstanceId)) {
                                popUpTo("maltrail/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "uptime-kuma/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.uptimekuma.UptimeKumaDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.UPTIME_KUMA, newInstanceId)) {
                                popUpTo("uptime-kuma/$instanceId/dashboard") { inclusive = true }
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
                route = "crafty/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.crafty.CraftyDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.CRAFTY_CONTROLLER, newInstanceId)) {
                                popUpTo("crafty/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "wakapi/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.wakapi.WakapiDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.WAKAPI, newInstanceId)) {
                                popUpTo("wakapi/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
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
                route = "proxmox/{instanceId}/dashboard",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        navController.navigate(dashboardRoute(ServiceType.PROXMOX, newInstanceId)) {
                            popUpTo("proxmox/$instanceId/dashboard") { inclusive = true }
                        }
                    },
                    onNavigateToNode = { nodeName ->
                        navController.navigate("proxmox/$instanceId/node/$nodeName")
                    },
                    onNavigateToGuest = { nodeName, vmid, isQemu ->
                        navController.navigate("proxmox/$instanceId/guest/$nodeName/$vmid/$isQemu")
                    },
                    onNavigateToStorage = { nodeName, storageName ->
                        navController.navigate("proxmox/$instanceId/storage/$nodeName/$storageName")
                    },
                    onNavigateToPool = { poolId ->
                        navController.navigate("proxmox/$instanceId/pool/$poolId")
                    },
                    onNavigateToBackup = {
                        navController.navigate("proxmox/$instanceId/backup")
                    },
                    onNavigateToFirewall = {
                        navController.navigate("proxmox/$instanceId/firewall")
                    },
                    onNavigateToHA = {
                        navController.navigate("proxmox/$instanceId/ha")
                    },
                    onNavigateToClusterResources = {
                        navController.navigate("proxmox/$instanceId/cluster-resources")
                    },
                    onNavigateToReplication = {
                        navController.navigate("proxmox/$instanceId/replication")
                    },
                    onNavigateToCreate = {
                        navController.navigate("proxmox/$instanceId/create")
                    },
                    onNavigateToGlobalTasks = {
                        navController.navigate("proxmox/$instanceId/global-tasks")
                    }
                )
            }

            composable(
                route = "proxmox/{instanceId}/node/{node}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxNodeDetailScreen(
                    node = node,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGuest = { nodeName, vmid, isQemu ->
                        navController.navigate("proxmox/$instanceId/guest/$nodeName/$vmid/$isQemu")
                    },
                    onNavigateToUpdates = { nodeName ->
                        navController.navigate("proxmox/$instanceId/apt/$nodeName")
                    },
                    onNavigateToCeph = { nodeName ->
                        navController.navigate("proxmox/$instanceId/ceph/$nodeName")
                    },
                    onNavigateToJournal = { nodeName ->
                        navController.navigate("proxmox/$instanceId/journal/$nodeName")
                    },
                    onNavigateToNetwork = { nodeName ->
                        navController.navigate("proxmox/$instanceId/network/$nodeName")
                    }
                )
            }

            composable(
                route = "proxmox/{instanceId}/guest/{node}/{vmid}/{isQemu}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType },
                    androidx.navigation.navArgument("vmid") { type = NavType.IntType },
                    androidx.navigation.navArgument("isQemu") { type = NavType.BoolType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                val vmid = backStackEntry.arguments?.getInt("vmid") ?: return@composable
                val isQemu = backStackEntry.arguments?.getBoolean("isQemu") ?: true
                com.homelab.app.ui.proxmox.ProxmoxGuestDetailScreen(
                    node = node,
                    vmid = vmid,
                    isQemu = isQemu,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToConsole = { n, v, q ->
                        navController.navigate("proxmox/$instanceId/guest/$n/$v/$q/console")
                    },
                    onNavigateToConfig = { n, v, q ->
                        navController.navigate("proxmox/$instanceId/guest/$n/$v/$q/config")
                    }
                )
            }

            composable(
                route = "proxmox/{instanceId}/guest/{node}/{vmid}/{isQemu}/console",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType },
                    androidx.navigation.navArgument("vmid") { type = NavType.IntType },
                    androidx.navigation.navArgument("isQemu") { type = NavType.BoolType }
                )
            ) { backStackEntry ->
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                val vmid = backStackEntry.arguments?.getInt("vmid") ?: return@composable
                val isQemu = backStackEntry.arguments?.getBoolean("isQemu") ?: true
                com.homelab.app.ui.proxmox.ProxmoxConsoleScreen(
                    node = node,
                    vmid = vmid,
                    isQemu = isQemu,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/guest/{node}/{vmid}/{isQemu}/config",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType },
                    androidx.navigation.navArgument("vmid") { type = NavType.IntType },
                    androidx.navigation.navArgument("isQemu") { type = NavType.BoolType }
                )
            ) { backStackEntry ->
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                val vmid = backStackEntry.arguments?.getInt("vmid") ?: return@composable
                val isQemu = backStackEntry.arguments?.getBoolean("isQemu") ?: true
                com.homelab.app.ui.proxmox.ProxmoxConfigScreen(
                    node = node,
                    vmid = vmid,
                    isQemu = isQemu,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/storage/{node}/{storage}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType },
                    androidx.navigation.navArgument("storage") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                val storage = backStackEntry.arguments?.getString("storage") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxStorageContentScreen(
                    node = node,
                    storage = storage,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/tasklog/{node}/{upid}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType },
                    androidx.navigation.navArgument("upid") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                val upid = backStackEntry.arguments?.getString("upid") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxTaskLogScreen(
                    node = node,
                    upid = upid,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/pool/{poolId}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("poolId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                val poolId = backStackEntry.arguments?.getString("poolId") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxPoolDetailScreen(
                    poolId = poolId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToGuest = { nodeName, vmid, isQemu ->
                        navController.navigate("proxmox/$instanceId/guest/$nodeName/$vmid/$isQemu")
                    },
                    onNavigateToStorage = { nodeName, storageName ->
                        navController.navigate("proxmox/$instanceId/storage/$nodeName/$storageName")
                    }
                )
            }

            composable(
                route = "proxmox/{instanceId}/backup",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) {
                com.homelab.app.ui.proxmox.ProxmoxBackupScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/firewall",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) {
                com.homelab.app.ui.proxmox.ProxmoxFirewallScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/apt/{node}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxAptUpdatesScreen(
                    node = node,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/ha",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) {
                com.homelab.app.ui.proxmox.ProxmoxHAScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/cluster-resources",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxClusterResourcesScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToNode = { nodeName ->
                        navController.navigate("proxmox/$instanceId/node/$nodeName")
                    },
                    onNavigateToGuest = { nodeName, vmid, isQemu ->
                        navController.navigate("proxmox/$instanceId/guest/$nodeName/$vmid/$isQemu")
                    }
                )
            }

            composable(
                route = "proxmox/{instanceId}/ceph/{node}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxCephScreen(
                    node = node,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/replication",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) {
                com.homelab.app.ui.proxmox.ProxmoxReplicationScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/global-tasks",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxGlobalTasksScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTaskLog = { nodeName, upid ->
                        navController.navigate("proxmox/$instanceId/tasklog/$nodeName/$upid")
                    }
                )
            }

            composable(
                route = "proxmox/{instanceId}/create",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxGuestCreateScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToJournal = { nodeName ->
                        navController.navigate("proxmox/$instanceId/journal/$nodeName")
                    }
                )
            }

            composable(
                route = "proxmox/{instanceId}/journal/{node}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxJournalScreen(
                    node = node,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "proxmox/{instanceId}/network/{node}",
                arguments = listOf(
                    androidx.navigation.navArgument("instanceId") { type = NavType.StringType },
                    androidx.navigation.navArgument("node") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val node = backStackEntry.arguments?.getString("node") ?: return@composable
                com.homelab.app.ui.proxmox.ProxmoxNetworkScreen(
                    node = node,
                    onNavigateBack = { navController.popBackStack() }
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

            composable(
                route = "pterodactyl/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.pterodactyl.PterodactylDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.PTERODACTYL, newInstanceId)) {
                                popUpTo("pterodactyl/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(
                route = "calagopus/{instanceId}/dashboard",
                arguments = listOf(androidx.navigation.navArgument("instanceId") { type = NavType.StringType })
            ) { backStackEntry ->
                val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
                com.homelab.app.ui.calagopus.CalagopusDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToInstance = { newInstanceId ->
                        if (newInstanceId != instanceId) {
                            navController.navigate(dashboardRoute(ServiceType.CALAGOPUS, newInstanceId)) {
                                popUpTo("calagopus/$instanceId/dashboard") { inclusive = true }
                            }
                        }
                    }
                )
            }
        }
    }
}
