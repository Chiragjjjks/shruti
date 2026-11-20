package com.chirag.shruti.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF080C14)
    val Surface = Color(0xFF111827)
    val SurfaceHi = Color(0xFF1A2333)
    val Text = Color(0xFFF5F7FB)
    val TextDim = Color(0xFFAAB4C5)
    val Link = Color(0xFF8EA8FF)
    val Nav = Color(0xFF7B93E8)
    val Accent = Color(0xFFF7991C)
    val PillBorder = Color(0xFF4F74D9)
    val PillFill = Color(0xFF0E1426)
    val Badge = Color(0xFFE9E7FB)
    val BadgeText = Color(0xFF1D2340)
    val Divider = Color(0xFF263042)
}

@Composable
fun ShrutiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Color.Black,
            secondary = Palette.Link,
            background = Palette.Bg,
            onBackground = Palette.Text,
            surface = Palette.Surface,
            onSurface = Palette.Text,
            surfaceVariant = Palette.SurfaceHi,
            onSurfaceVariant = Palette.TextDim,
            surfaceContainer = Palette.Surface,
            surfaceContainerHigh = Palette.SurfaceHi,
            surfaceContainerLow = Palette.Surface,
            outline = Palette.Divider,
        ),
        typography = Typography(),
    ) {
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
