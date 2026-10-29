package com.noheadphonegamerz.amplitudebooster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NhgAccentGreen,
    onPrimary = Color.Black,
    secondary = NhgAccentCyan,
    onSecondary = Color.Black,
    background = NhgDarkBackground,
    surface = NhgDarkSurface,
    surfaceVariant = NhgDarkSurfaceVariant,
    onBackground = NhgTextPrimary,
    onSurface = NhgTextPrimary,
    onSurfaceVariant = NhgTextSecondary,
    outline = NhgBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00A854),
    onPrimary = Color.White,
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun NHGTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Audio engineering tools are natively designed in restrained dark theme
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NhgTypography,
        content = content
    )
}
