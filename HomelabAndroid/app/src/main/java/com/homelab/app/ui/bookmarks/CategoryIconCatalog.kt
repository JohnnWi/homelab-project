package com.homelab.app.ui.bookmarks

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

internal data class CategoryIconChoice(
    val id: String,
    val icon: ImageVector
)

internal val categoryIconChoices: List<CategoryIconChoice> = listOf(
    CategoryIconChoice("folder", Icons.Filled.Folder),
    CategoryIconChoice("home", Icons.Filled.Home),
    CategoryIconChoice("storage", Icons.Filled.Storage),
    CategoryIconChoice("dns", Icons.Filled.Dns),
    CategoryIconChoice("router", Icons.Filled.Router),
    CategoryIconChoice("terminal", Icons.Filled.Terminal),
    CategoryIconChoice("code", Icons.Filled.Code),
    CategoryIconChoice("apps", Icons.Filled.Apps),
    CategoryIconChoice("settings", Icons.Filled.Settings),
    CategoryIconChoice("security", Icons.Filled.Security),
    CategoryIconChoice("lock", Icons.Filled.Lock),
    CategoryIconChoice("cloud", Icons.Filled.Cloud),
    CategoryIconChoice("computer", Icons.Filled.Computer),
    CategoryIconChoice("phone", Icons.Filled.PhoneAndroid),
    CategoryIconChoice("memory", Icons.Filled.Memory),
    CategoryIconChoice("analytics", Icons.Filled.Analytics),
    CategoryIconChoice("media", Icons.Filled.Movie),
    CategoryIconChoice("tv", Icons.Filled.Tv),
    CategoryIconChoice("images", Icons.Filled.Image),
    CategoryIconChoice("gallery", Icons.Filled.PhotoLibrary),
    CategoryIconChoice("book", Icons.Filled.Book),
    CategoryIconChoice("news", Icons.Filled.Newspaper),
    CategoryIconChoice("shop", Icons.Filled.ShoppingCart),
    CategoryIconChoice("work", Icons.Filled.Work),
    CategoryIconChoice("game", Icons.Filled.Gamepad),
    CategoryIconChoice("tools", Icons.Filled.Build),
    CategoryIconChoice("alerts", Icons.Filled.Notifications),
    CategoryIconChoice("server", Icons.Filled.Storage),
    CategoryIconChoice("network", Icons.Filled.Router),
    CategoryIconChoice("monitor", Icons.Filled.Computer),
    CategoryIconChoice("infra", Icons.Filled.Build),
    CategoryIconChoice("web", Icons.Filled.Language),
    CategoryIconChoice("bookmark", Icons.Filled.Book)
)

internal fun categoryIconForId(id: String): ImageVector? {
    return categoryIconChoices.firstOrNull { it.id == id }?.icon
}
