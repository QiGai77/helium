package tech.qigai.helium.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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

private val HeDarkScheme = darkColorScheme(
    primary = Color(0xFF6B84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0x2E6B84FF),
    onPrimaryContainer = Color(0xFF9FB0FF),
    secondary = Color(0xFFF0F2FA),
    onSecondary = Color(0xFF12131A),
    secondaryContainer = Color(0xFF202331),
    onSecondaryContainer = Color(0xFFF0F2FA),
    tertiary = HeColors.Mint,
    background = Color(0xFF0F1118),
    onBackground = Color(0xFFF0F2FA),
    surface = Color(0xFF181A24),
    onSurface = Color(0xFFF0F2FA),
    surfaceVariant = Color(0xFF202331),
    onSurfaceVariant = Color(0xFFA8AEBF),
    surfaceContainerLowest = Color(0xFF181A24),
    surfaceContainerLow = Color(0xFF1B1E2B),
    surfaceContainer = Color(0xFF202331),
    surfaceContainerHigh = Color(0xFF222534),
    surfaceContainerHighest = Color(0xFF272B3B),
    outline = Color(0xFF393E52),
    outlineVariant = Color(0xFF272B3B),
    error = HeColors.Coral,
    onError = Color.White,
    scrim = Color(0xA6000000),
)

private val HeM3Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(HeRadius.row),
    large = RoundedCornerShape(HeRadius.card),
    extraLarge = RoundedCornerShape(HeRadius.panel),
)

/**
 * Helium theme supporting Light, Dark, and System modes while keeping dynamic color off
 * so the brand accent stays consistent.
 */
@Composable
fun HeliumTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) HeDarkScheme else HeLightScheme,
        typography = HeTypography,
        shapes = HeM3Shapes,
        content = content,
    )
}
