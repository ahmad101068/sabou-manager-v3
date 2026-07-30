package ir.sabou.inventory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SabouColors = lightColorScheme(
    primary = Color(0xFF0A6457),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F2EC),
    onPrimaryContainer = Color(0xFF063B33),
    secondary = Color(0xFFB98736),
    onSecondary = Color.White,
    background = Color(0xFFF7F1E7),
    onBackground = Color(0xFF25211C),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF25211C),
    error = Color(0xFFB3261E),
)

@Composable
fun SabouTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SabouColors,
        content = content,
    )
}

