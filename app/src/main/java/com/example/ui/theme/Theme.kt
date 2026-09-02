package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val GlassColorScheme = darkColorScheme(
    primary = GlassBlue,
    secondary = GlassPurple,
    tertiary = GlassPink,
    background = GlassBackground,
    surface = GlassSurface,
    onBackground = OnGlassPrimary,
    onSurface = OnGlassPrimary,
    primaryContainer = GlassBlueLight,
    onPrimaryContainer = OnGlassPrimary,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = OnGlassSecondary,
    outline = GlassBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Frosted Glass uses our custom GlassColorScheme for both dark & light modes to maintain the deep glassmorphic aesthetic
    val colorScheme = GlassColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
