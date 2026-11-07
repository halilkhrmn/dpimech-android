package io.github.halilkhrmn.dpimech.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Tones of the logo's green (#397256), for phones without wallpaper colours (before Android 12)
// or with them turned off. Every role is set, so no purple from Material's baseline is left.
private val Light = lightColorScheme(
    primary = Color(0xFF397256),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCEDD1),
    onPrimaryContainer = Color(0xFF002113),
    secondary = Color(0xFF4E6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D7),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF3C6472),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBFE9F9),
    onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFF6FBF5),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FBF5),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DC),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707972),
    outlineVariant = Color(0xFFC0C9C0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5EF),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE5EAE4),
    surfaceContainerHighest = Color(0xFFDFE4DE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA1D1B6),
    onPrimary = Color(0xFF053824),
    primaryContainer = Color(0xFF205039),
    onPrimaryContainer = Color(0xFFBCEDD1),
    secondary = Color(0xFFB4CCBB),
    onSecondary = Color(0xFF203529),
    secondaryContainer = Color(0xFF364B3F),
    onSecondaryContainer = Color(0xFFD0E8D7),
    tertiary = Color(0xFFA4CDDD),
    onTertiary = Color(0xFF053542),
    tertiaryContainer = Color(0xFF234C59),
    onTertiaryContainer = Color(0xFFBFE9F9),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFDFE4DE),
    surface = Color(0xFF0F1511),
    onSurface = Color(0xFFDFE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8A938B),
    outlineVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFF0A0F0C),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF262B27),
    surfaceContainerHighest = Color(0xFF303632),
)

/** Material You (wallpaper) colours on Android 12+ unless turned off; the logo's green otherwise. */
@Composable
fun DpimechTheme(dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
