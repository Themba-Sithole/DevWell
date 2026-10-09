package za.co.devwell.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DevWellColors = lightColorScheme(
    primary = DevGreen,
    onPrimary = Color.White,
    primaryContainer = AccentSurface,
    onPrimaryContainer = DevGreen,
    secondary = AccentGreen,
    onSecondary = Color.White,
    secondaryContainer = TrackGreen,
    onSecondaryContainer = DevGreen,
    tertiary = LightGreen,
    onTertiary = Ink,
    background = PageBackground,
    onBackground = Ink,
    surface = CardWhite,
    onSurface = Ink,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = InkMuted,
    surfaceTint = DevGreen,
    outline = BorderGreen,
    outlineVariant = BorderGreen,
    error = RedText,
    onError = Color.White,
    errorContainer = RedSurface,
    onErrorContainer = Color(0xFF991B1B),
    scrim = Color.Black,
)

private val DevWellShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun DevWellTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DevWellColors,
        typography = DevWellTypography,
        shapes = DevWellShapes,
        content = content,
    )
}
