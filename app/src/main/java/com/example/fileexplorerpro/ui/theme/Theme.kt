package com.example.fileexplorerpro.ui.theme
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    surface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFF262829),
    onSurface = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFFC4C6CF))
private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    surface = Color(0xFFFDFCFF),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF44474F))
@Composable fun FileExplorerTheme(darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit) {
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val c = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c)
    } else if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = scheme, content = content)
}
