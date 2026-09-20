package com.projectathena.app

import androidx.compose.material3.MaterialTheme
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

@Composable
fun AthenaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AthenaColors,
        content = content,
    )
}
