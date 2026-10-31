package com.dsh.tavern.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD98A94),
    onPrimary = Color(0xFF2A0E14),
    primaryContainer = Color(0xFF4A2028),
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Color(0xFFE0C27E),
    onSecondary = Color(0xFF2B2105),
    secondaryContainer = Color(0xFF433716),
    onSecondaryContainer = Color(0xFFF7E4B4),
    tertiary = Color(0xFFB9A0D6),
    onTertiary = Color(0xFF21122E),
    background = Color(0xFF0E0A10),
    onBackground = Color(0xFFF0E4E8),
    surface = Color(0xFF171017),
    onSurface = Color(0xFFF0E4E8),
    surfaceVariant = Color(0xFF261B27),
    onSurfaceVariant = Color(0xFFCBB8C6),
    outline = Color(0xFF5A4558),
    error = Color(0xFFCF6679),
    onError = Color(0xFF37080F)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB4565F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9DE),
    onPrimaryContainer = Color(0xFF3F0010),
    secondary = Color(0xFF8A6A1F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF7E4B4),
    onSecondaryContainer = Color(0xFF2B2105),
    tertiary = Color(0xFF6A5583),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF7F9),
    onBackground = Color(0xFF201A1C),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF201A1C),
    surfaceVariant = Color(0xFFF1E4E8),
    onSurfaceVariant = Color(0xFF504348),
    outline = Color(0xFF837378),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

/** 主题：dark / light / system（跟随系统）。 */
@Composable
fun TavernTheme(themeMode: String = "dark", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
