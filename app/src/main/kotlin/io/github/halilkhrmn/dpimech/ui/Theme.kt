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

// The octopus purple, for phones without wallpaper colours (before Android 12).
private val Purple = Color(0xFF7B3FB3)

/** Material You (wallpaper) colours on Android 12+ unless turned off; the octopus purple otherwise. */
@Composable
fun DpimechTheme(dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFD9B8FF))
        else -> lightColorScheme(primary = Purple)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
