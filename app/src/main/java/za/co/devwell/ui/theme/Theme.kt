package za.co.devwell.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF174A35)
val Sage = Color(0xFF82A98F)
val PaleGreen = Color(0xFFE2EFE5)
val WarmBackground = Color(0xFFF6F8F3)
val WarmCard = Color(0xFFFFFDF8)

private val DevWellColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    secondary = Sage,
    background = WarmBackground,
    surface = WarmCard,
    onBackground = Color(0xFF17211B),
    onSurface = Color(0xFF17211B),
)

@Composable
fun DevWellTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DevWellColors, typography = Typography(), content = content)
}
