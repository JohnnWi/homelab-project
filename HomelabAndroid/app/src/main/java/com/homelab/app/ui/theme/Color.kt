package com.homelab.app.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

val StatusGreen: Color
    @androidx.compose.runtime.Composable
    get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF4CAF50)

val StatusRed: Color
    @androidx.compose.runtime.Composable
    get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFE57373) else Color(0xFFF44336)

val StatusOrange: Color
    @androidx.compose.runtime.Composable
    get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFFFF9800)

val StatusBlue: Color
    @androidx.compose.runtime.Composable
    get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF64B5F6) else Color(0xFF2196F3)

val StatusPurple: Color
    @androidx.compose.runtime.Composable
    get() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFA78BFA) else Color(0xFF8B5CF6)
