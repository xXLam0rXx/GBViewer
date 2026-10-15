package com.lamer.gbviewer.ui
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils

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
import com.lamer.gbviewer.data.ThemeMode

@Composable
fun GBTheme(mode: ThemeMode, accent: Int? = null, content: @Composable () -> Unit) {
    val base = when (mode) {
        ThemeMode.LIGHT -> lightColorScheme()
        ThemeMode.DARK -> darkColorScheme()
        ThemeMode.AMOLED -> darkColorScheme(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(0xFF1C1B1F),
            onBackground = Color(0xFFE6E1E5),
            onSurface = Color(0xFFE6E1E5),
        )
    }
    val scheme = if (accent == null) base else base.withAccent(Color(accent), dark = mode != ThemeMode.LIGHT)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = scheme.surface.toArgb()
            window.navigationBarColor = scheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = mode == ThemeMode.LIGHT
            controller.isAppearanceLightNavigationBars = mode == ThemeMode.LIGHT
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Перекрашивает основные цвета схемы (кнопки, переключатели, выделения) в выбранный акцент. */
private fun ColorScheme.withAccent(accent: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)
    fun hsl(h: Float, s: Float, l: Float) =
        Color(ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

    val h = hsl[0]
    val s = hsl[1]
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val container = if (dark) hsl(h, s.coerceAtMost(0.6f), 0.25f) else hsl(h, s.coerceAtLeast(0.5f), 0.9f)
    val onContainer = if (dark) hsl(h, s.coerceAtMost(0.8f), 0.9f) else hsl(h, s, 0.15f)
    return copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        secondary = accent,
        onSecondary = onAccent,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
        surfaceTint = accent,
    )
}
