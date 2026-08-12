package com.radar.news.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * There is exactly one colour scheme. The app is dark whatever the system says —
 * [isSystemInDarkTheme] is deliberately not consulted, because a light variant of this
 * design does not exist.
 */
private val RadarColorScheme = darkColorScheme(
    primary = RadarColors.AccentRed,
    onPrimary = RadarColors.TextPrimary,
    secondary = RadarColors.TextSecondary,
    onSecondary = RadarColors.TextPrimary,
    background = RadarColors.Background,
    onBackground = RadarColors.TextPrimary,
    surface = RadarColors.Surface,
    onSurface = RadarColors.TextPrimary,
    surfaceVariant = RadarColors.Surface,
    onSurfaceVariant = RadarColors.TextSecondary,
    outline = RadarColors.Divider,
    outlineVariant = RadarColors.Divider,
    error = RadarColors.AccentRed,
    onError = RadarColors.TextPrimary,
)

private val RadarRipple = RippleConfiguration(color = RadarColors.Ripple)

@Composable
fun RadarTheme(content: @Composable () -> Unit) {
    // RTL is forced app-wide rather than inherited from the device locale: the app is
    // Arabic-only, so a user running an English phone must still get a mirrored layout.
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        androidx.compose.material3.LocalRippleConfiguration provides RadarRipple,
    ) {
        MaterialTheme(
            colorScheme = RadarColorScheme,
            typography = RadarTypography,
            content = content,
        )
    }
}
