package fr.fh.rss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OrangeRss = Color(0xFFEE6A2A)
private val OrangeFonce = Color(0xFFC7501C)

private val clair = lightColorScheme(
    primary = OrangeRss,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D3),
    onPrimaryContainer = Color(0xFF3A1300),
    secondary = Color(0xFF4F6172),
    onSecondary = Color.White,
    background = Color(0xFFFDFBF9),
    onBackground = Color(0xFF1C1B1A),
    surface = Color(0xFFFDFBF9),
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFF2EDE9),
    onSurfaceVariant = Color(0xFF4A4541),
)

private val sombre = darkColorScheme(
    primary = Color(0xFFFFB68F),
    onPrimary = Color(0xFF552100),
    primaryContainer = OrangeFonce,
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFB8C8D8),
    onSecondary = Color(0xFF22323F),
    background = Color(0xFF131211),
    onBackground = Color(0xFFEDE7E3),
    surface = Color(0xFF131211),
    onSurface = Color(0xFFEDE7E3),
    surfaceVariant = Color(0xFF2A2725),
    onSurfaceVariant = Color(0xFFCFC8C3),
)

@Composable
fun RssTheme(theme: String, content: @Composable () -> Unit) {
    val sombreActif = when (theme) {
        "clair" -> false
        "sombre" -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (sombreActif) sombre else clair,
        content = content,
    )
}
