package com.example.omnicontrol.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Color(0xFF1C1C1E),
    tertiary = Color(0xFF558BFF),
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onPrimary = Color.White,
    onSecondary = OnBackground,
    onTertiary = OnBackground,
    onBackground = OnBackground,
    onSurface = OnSurface,
    onSurfaceVariant = OnBackground,
    error = Color(0xFFFF3B30),
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Color(0xFFE5E5EA),
    tertiary = Color(0xFF007AFF),
    background = Color.White,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun OmniControlTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
