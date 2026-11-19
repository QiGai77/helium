package tech.qigai.helium.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

private val HeLightScheme = lightColorScheme(
    primary = HeColors.Accent,
    onPrimary = Color.White,
    primaryContainer = HeColors.AccentTint,
    onPrimaryContainer = HeColors.Accent,
    secondary = HeColors.Ink,
    onSecondary = Color.White,
    secondaryContainer = HeColors.Mist2,
    onSecondaryContainer = HeColors.Ink,
    tertiary = HeColors.Mint,
    background = HeColors.Canvas,
    onBackground = HeColors.Ink,
    surface = HeColors.Surface,
    onSurface = HeColors.Ink,
    surfaceVariant = HeColors.Mist2,
    onSurfaceVariant = HeColors.Ink2,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = HeColors.Mist3,
    surfaceContainer = HeColors.Mist2,
    surfaceContainerHigh = HeColors.Mist,
    surfaceContainerHighest = HeColors.Mist,
    outline = HeColors.Outline,
    outlineVariant = HeColors.Hair,
    error = HeColors.Coral,
    onError = Color.White,
    scrim = Color(0x6612131A),
)

private val HeM3Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(HeRadius.row),
    large = RoundedCornerShape(HeRadius.card),
    extraLarge = RoundedCornerShape(HeRadius.panel),
)

/**
 * Helium is light-first in v0.1 (the design system has no dark spec yet).
 * Dynamic colour is intentionally off so the single accent stays the brand accent.
 */
@Composable
fun HeliumTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HeLightScheme,
        typography = HeTypography,
        shapes = HeM3Shapes,
        content = content,
    )
}
