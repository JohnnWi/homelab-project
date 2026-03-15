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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Home
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
import com.homelab.app.util.ServiceType

sealed class Screen(
    val route: String,
    val titleResId: Int,
    val activeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    data object Home : Screen("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home)
    data object Bookmarks : Screen("bookmarks", R.string.nav_bookmarks, Icons.Filled.Bookmark, Icons.Outlined.Bookmark)
    data object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

private fun dashboardRoute(type: ServiceType, instanceId: String): String {
    return when (type) {
        ServiceType.PORTAINER -> "portainer/$instanceId/dashboard"
        ServiceType.PIHOLE -> "pihole/$instanceId/dashboard"
        ServiceType.BESZEL -> "beszel/$instanceId/dashboard"
        ServiceType.GITEA -> "gitea/$instanceId/dashboard"
        ServiceType.NGINX_PROXY_MANAGER -> "nginxpm/$instanceId/dashboard"
        ServiceType.UNKNOWN -> Screen.Home.route
    }
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
    val items = listOf(Screen.Home, Screen.Bookmarks, Screen.Settings)

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            val isServiceChild = currentDestination?.route?.contains("/") == true

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
                            (screen.route == Screen.Home.route && isServiceChild)
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
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
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

            composable(Screen.Bookmarks.route) {
                com.homelab.app.ui.bookmarks.BookmarksScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToLogin = { type, instanceId ->
                        navController.navigate(loginRoute(type, instanceId))
                    }
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
                com.homelab.app.ui.login.ServiceLoginScreen(
                    serviceType = ServiceType.valueOf(typeName),
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
                val systemId = backStackEntry.arguments?.getString("systemId") ?: return@composable
                com.homelab.app.ui.beszel.BeszelSystemDetailScreen(
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
        }
    }
}
