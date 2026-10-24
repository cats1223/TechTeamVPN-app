package com.techteam.vpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object Brand {
    val Background = Color(0xFF0B1020)
    val Surface = Color(0xFF151B2E)
    val SurfaceHigh = Color(0xFF1E2640)
    val Cyan = Color(0xFF00D1FF)
    val Violet = Color(0xFF7B5CFF)
    val Green = Color(0xFF2BD98B)
    val Amber = Color(0xFFFFB547)
    val Red = Color(0xFFFF5C7A)
    val TextMuted = Color(0xFF8A93B2)
    val Gradient = Brush.linearGradient(listOf(Cyan, Violet))
}

@Composable
fun TechTeamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Brand.Cyan,
            secondary = Brand.Violet,
            background = Brand.Background,
            surface = Brand.Surface,
            surfaceVariant = Brand.SurfaceHigh,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Brand.TextMuted,
            error = Brand.Red,
        ),
        content = content,
    )
}
