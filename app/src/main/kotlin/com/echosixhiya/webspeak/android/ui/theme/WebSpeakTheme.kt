package com.echosixhiya.webspeak.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val WebSpeakLight = lightColorScheme(
    primary = Color(0xFF2455C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFE5FF),
    onPrimaryContainer = Color(0xFF00164F),
    secondary = Color(0xFF35645C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB9EBDD),
    onSecondaryContainer = Color(0xFF002019),
    tertiary = Color(0xFF735472),
    background = Color(0xFFF8F7FA),
    surface = Color(0xFFF8F7FA),
    surfaceContainer = Color(0xFFF0EFF4),
    surfaceContainerHigh = Color(0xFFEAE9EF),
    outline = Color(0xFF777983),
)

private val WebSpeakDark = darkColorScheme(
    primary = Color(0xFFB8C5FF),
    onPrimary = Color(0xFF082A78),
    primaryContainer = Color(0xFF23409A),
    onPrimaryContainer = Color(0xFFDFE5FF),
    secondary = Color(0xFF9DD3C4),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF1D4D43),
    onSecondaryContainer = Color(0xFFB9EBDD),
    tertiary = Color(0xFFE0BBDD),
    background = Color(0xFF111318),
    surface = Color(0xFF111318),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF282A31),
    outline = Color(0xFF90919B),
)

@Composable
fun WebSpeakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> WebSpeakDark
        else -> WebSpeakLight
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
