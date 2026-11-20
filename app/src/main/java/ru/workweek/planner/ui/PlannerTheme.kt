package ru.workweek.planner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
fun PlannerTheme(theme: String, accent: String, content: @Composable () -> Unit) {
    val dark = theme == "DARK" || (theme == "SYSTEM" && isSystemInDarkTheme())
    val view = LocalView.current
    SideEffect {
        (view.context as? android.app.Activity)?.window?.let { window ->
            if (android.os.Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                window.navigationBarColor = if (dark) 0xff10151f.toInt() else 0xfff6f7fb.toInt()
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = plannerColors(dark, accent), content = content)
}

internal fun plannerColors(dark: Boolean, accent: String): androidx.compose.material3.ColorScheme {
    val primary = when (accent) {
        "TEAL" -> if (dark) Color(0xFF80D5CB) else Color(0xFF006B61)
        "PURPLE" -> if (dark) Color(0xFFD3B8FF) else Color(0xFF7040AB)
        "AMBER" -> if (dark) Color(0xFFFFCB70) else Color(0xFF805300)
        else -> if (dark) Color(0xFFA9BAFF) else Color(0xFF3857D5)
    }
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = Color(0xFF10151F), background = Color(0xFF10151F),
        primaryContainer = lerp(Color(0xFF1A2230), primary, 0.20f), onPrimaryContainer = Color(0xFFF1F4F8),
        onBackground = Color(0xFFF1F4F8),
        surface = Color(0xFF1A2230), onSurface = Color(0xFFF1F4F8),
        onSurfaceVariant = Color(0xFFB1BDCC), outline = Color(0xFF8C9AAF)
    ) else lightColorScheme(
        primary = primary, onPrimary = Color.White, background = Color(0xFFF6F7FB),
        primaryContainer = lerp(Color.White, primary, 0.16f), onPrimaryContainer = Color(0xFF18212F),
        onBackground = Color(0xFF18212F),
        surface = Color.White, onSurface = Color(0xFF18212F),
        onSurfaceVariant = Color(0xFF5C6677), outline = Color(0xFF69758A)
    )
}
