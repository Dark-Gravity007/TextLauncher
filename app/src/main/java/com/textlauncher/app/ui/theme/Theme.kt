package com.textlauncher.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Text colours tuned to sit on top of any wallpaper (with the scrim and shadow below). The solid Background
 * is only used by full-screen pages such as Settings; the home screen and drawer show the wallpaper instead.
 */
object Palette {
    val Background = Color(0xFF000000)
    val Label = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFC7C7CC)
    val Tertiary = Color(0xFF9A9AA0)
    val Separator = Color(0x33FFFFFF)
    val Scrim = Color(0xB8000000)
}

@Composable
fun TextLauncherTheme(content: @Composable () -> Unit) {
    // Single dark theme on purpose: a distraction-free launcher has nothing to switch.
    content()
}
