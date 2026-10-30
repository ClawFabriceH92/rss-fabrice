package fr.fh.rss.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette pastel : vert sauge, bleu brume et crème. Les accents restent assez
// soutenus pour que le texte posé dessus garde un contraste lisible.
private val Sauge = Color(0xFF6E8B7B)
private val SaugeClair = Color(0xFFDCE8E1)
private val SaugeSombre = Color(0xFF2C4035)
private val SaugeNuit = Color(0xFFA9C6B4)
private val Brume = Color(0xFFA3B5C9)
private val Creme = Color(0xFFFBF9F6)
private val EncreDouce = Color(0xFF2C2A28)

private val clair = lightColorScheme(
    primary = Sauge,
    onPrimary = Color.White,
    primaryContainer = SaugeClair,
    onPrimaryContainer = SaugeSombre,
    secondary = Brume,
    onSecondary = Color(0xFF20303F),
    secondaryContainer = Color(0xFFE2EAF3),
    onSecondaryContainer = Color(0xFF27384A),
    tertiary = Color(0xFFC9A9A6),
    onTertiary = Color(0xFF3D2622),
    background = Creme,
    onBackground = EncreDouce,
    surface = Color(0xFFFFFFFF),
    onSurface = EncreDouce,
    surfaceVariant = Color(0xFFF2EEE7),
    onSurfaceVariant = Color(0xFF5B5651),
    outline = Color(0xFFD9D2C9),
    outlineVariant = Color(0xFFEAE4DC),
    error = Color(0xFFA3565A),
    onError = Color.White,
    errorContainer = Color(0xFFF6E1E2),
    onErrorContainer = Color(0xFF4A1D20),
)

private val sombre = darkColorScheme(
    primary = SaugeNuit,
    onPrimary = Color(0xFF20342A),
    primaryContainer = SaugeSombre,
    onPrimaryContainer = Color(0xFFD3E6DA),
    secondary = Color(0xFFB8C8D9),
    onSecondary = Color(0xFF22323F),
    secondaryContainer = Color(0xFF2E3B47),
    onSecondaryContainer = Color(0xFFD7E2EC),
    tertiary = Color(0xFFD8B7B3),
    onTertiary = Color(0xFF3D2622),
    background = Color(0xFF16181A),
    onBackground = Color(0xFFE8E3DC),
    surface = Color(0xFF1B1E20),
    onSurface = Color(0xFFE8E3DC),
    surfaceVariant = Color(0xFF262A2C),
    onSurfaceVariant = Color(0xFFBCB6AE),
    outline = Color(0xFF3C4144),
    outlineVariant = Color(0xFF2C3033),
    error = Color(0xFFE0A5A7),
    onError = Color(0xFF3D1417),
    errorContainer = Color(0xFF4A1D20),
    onErrorContainer = Color(0xFFF6E1E2),
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
