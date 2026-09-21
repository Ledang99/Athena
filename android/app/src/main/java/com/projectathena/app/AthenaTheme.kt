package com.projectathena.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AthenaColors = lightColorScheme(
    primary = Color(0xFF173A32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4E7),
    onPrimaryContainer = Color(0xFF0B2A23),
    secondary = Color(0xFF60713A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F4B7),
    onSecondaryContainer = Color(0xFF293500),
    background = Color(0xFFF7F7F2),
    onBackground = Color(0xFF18201D),
    surface = Color(0xFFFFFEFA),
    onSurface = Color(0xFF18201D),
    surfaceVariant = Color(0xFFE8ECE8),
    onSurfaceVariant = Color(0xFF434A46),
    outline = Color(0xFF737A75),
    error = Color(0xFFBA1A1A),
)

private val AthenaDarkColors = darkColorScheme(
    primary = Color(0xFFB6DCCF),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF175044),
    onPrimaryContainer = Color(0xFFD1F5E9),
    secondary = Color(0xFFC9D98A),
    onSecondary = Color(0xFF323E00),
    secondaryContainer = Color(0xFF495600),
    onSecondaryContainer = Color(0xFFE6F6A3),
    background = Color(0xFF101411),
    onBackground = Color(0xFFE0E4DF),
    surface = Color(0xFF181D1A),
    onSurface = Color(0xFFE0E4DF),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF89938E),
    error = Color(0xFFFFB4AB),
)

@Composable
fun AthenaTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AthenaDarkColors else AthenaColors,
        content = content,
    )
}
