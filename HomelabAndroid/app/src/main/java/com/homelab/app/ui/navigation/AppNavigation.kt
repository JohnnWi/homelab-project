package com.homelab.app.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.homelab.app.ui.home.HomeScreen
import com.homelab.app.ui.settings.SettingsScreen
import com.homelab.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Bookmark

sealed class Screen(val route: String, val titleResId: Int, val activeIcon: androidx.compose.ui.graphics.vector.ImageVector, val inactiveIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home)
    object Bookmarks : Screen("bookmarks", R.string.nav_bookmarks, Icons.Filled.Bookmark, Icons.Outlined.Bookmark)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
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
                shadowElevation = 8.dp, // Aggiunge una leggera ombra per separare la barra dal contenuto
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 1. Spinge il contenuto sopra i pulsanti/gesture di sistema
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        // 2. Dà un margine interno snello per non farla sembrare enorme
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true || 
                                       (screen.route == "home" && isServiceChild)
                                       
                        val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        val label = stringResource(screen.titleResId)
                        
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null // Rimuove il cerchio grigio di default al click per un look più pulito
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
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToService = { type ->
                        when (type) {
                            com.homelab.app.util.ServiceType.PORTAINER -> navController.navigate("portainer/dashboard")
                            com.homelab.app.util.ServiceType.PIHOLE -> navController.navigate("pihole/dashboard")
                            com.homelab.app.util.ServiceType.BESZEL -> navController.navigate("beszel/dashboard")
                            com.homelab.app.util.ServiceType.GITEA -> navController.navigate("gitea/dashboard")
                            else -> {}
                        }
                    },
                    onNavigateToLogin = { type ->
                        navController.navigate("login/${type.name}")
                    }
                )
            }
            
            composable(Screen.Bookmarks.route) {
                com.homelab.app.ui.bookmarks.BookmarksScreen()
            }
            
            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            // --- Shared Login ---
            composable(
                route = "login/{serviceType}",
                arguments = listOf(androidx.navigation.navArgument("serviceType") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val typeName = backStackEntry.arguments?.getString("serviceType") ?: return@composable
                val serviceType = com.homelab.app.util.ServiceType.valueOf(typeName)
                com.homelab.app.ui.login.ServiceLoginScreen(
                    serviceType = serviceType,
                    onDismiss = { navController.popBackStack() }
                )
            }

            // --- Portainer Routes ---
            composable("portainer/dashboard") {
                com.homelab.app.ui.portainer.PortainerDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToContainers = { endpointId ->
                        navController.navigate("portainer/containers/$endpointId")
                    }
                )
            }

            composable(
                route = "portainer/containers/{endpointId}",
                arguments = listOf(androidx.navigation.navArgument("endpointId") { type = androidx.navigation.NavType.IntType })
            ) { backStackEntry ->
                val endpointId = backStackEntry.arguments?.getInt("endpointId") ?: return@composable
                com.homelab.app.ui.portainer.ContainerListScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { epId, containerId ->
                        navController.navigate("portainer/containers/$epId/detail/$containerId")
                    }
                )
            }

            composable(
                route = "portainer/containers/{endpointId}/detail/{containerId}",
                arguments = listOf(
                    androidx.navigation.navArgument("endpointId") { type = androidx.navigation.NavType.IntType },
                    androidx.navigation.navArgument("containerId") { type = androidx.navigation.NavType.StringType }
                )
            ) { backStackEntry ->
                com.homelab.app.ui.portainer.ContainerDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("pihole/dashboard") {
                com.homelab.app.ui.pihole.PiholeDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDomains = { navController.navigate("pihole/domains") },
                    onNavigateToQueryLog = { navController.navigate("pihole/queries") }
                )
            }

            composable("pihole/domains") {
                com.homelab.app.ui.pihole.PiholeDomainListScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable("pihole/queries") {
                com.homelab.app.ui.pihole.PiholeQueryLogScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // --- Beszel Routes ---
            composable("beszel/dashboard") {
                com.homelab.app.ui.beszel.BeszelDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSystem = { systemId ->
                        navController.navigate("beszel/system/$systemId")
                    }
                )
            }

            composable(
                route = "beszel/system/{systemId}",
                arguments = listOf(androidx.navigation.navArgument("systemId") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val systemId = backStackEntry.arguments?.getString("systemId") ?: return@composable
                com.homelab.app.ui.beszel.BeszelSystemDetailScreen(
                    systemId = systemId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // --- Gitea Routes ---
            composable("gitea/dashboard") {
                com.homelab.app.ui.gitea.GiteaDashboardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToRepo = { owner, repo ->
                        navController.navigate("gitea/repo/$owner/$repo")
                    }
                )
            }
            
            composable(
                route = "gitea/repo/{owner}/{repo}",
                arguments = listOf(
                    androidx.navigation.navArgument("owner") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("repo") { type = androidx.navigation.NavType.StringType }
                )
            ) {
                com.homelab.app.ui.gitea.GiteaRepoDetailScreen(
                    onNavigateBack = { navController.navigateUp() }
                )
            }
        }
    }
}
