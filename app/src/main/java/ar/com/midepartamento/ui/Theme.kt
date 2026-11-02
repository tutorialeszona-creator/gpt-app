package ar.com.midepartamento.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Mint = Color(0xFFACE8C8)
val Peach = Color(0xFFF7BA99)
val Lavender = Color(0xFFC4BBED)
val ChartColors = listOf(Mint, Peach, Lavender, Color(0xFF8DBDDC), Color(0xFFE8CF83), Color(0xFFB4C897), Color(0xFFE5AFC4), Color(0xFF9CB3C7))
@Composable fun DepartmentTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, onPrimary = Color(0xFF153B2A), secondary = Lavender,
        background = Color(0xFF101419), surface = Color(0xFF101419),
        surfaceContainer = Color(0xFF1B222A), surfaceVariant = Color(0xFF27313A),
        onBackground = Color(0xFFF2F3F1), onSurface = Color(0xFFF2F3F1),
        onSurfaceVariant = Color(0xFFA8B4BD), outline = Color(0xFF56636D)
    ), content = content)
}
