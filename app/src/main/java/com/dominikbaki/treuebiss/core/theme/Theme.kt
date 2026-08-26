package com.dominikbaki.treuebiss.core.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat


private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    secondary = SecondaryBlue,
    tertiary = TertiaryYellow,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkOnPrimary,
    onSecondary = DarkOnSecondary,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    secondary = SecondaryBlue,
    tertiary = TertiaryYellow,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightOnPrimary,
    onSecondary = LightOnSecondary,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
)

/**
 * @param brandPrimaryColor Primärfarbe des Betriebs als Hex-String (z. B. "#4CAF50").
 *   Ist sie null oder unlesbar, bleibt es beim Standard-Grün.
 */
@Composable
fun TreueBissTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    brandPrimaryColor: String? = null,
    content: @Composable () -> Unit
) {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val colorScheme = remember(base, brandPrimaryColor) {
        parseHexColor(brandPrimaryColor)?.let { base.copy(primary = it) } ?: base
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Die Statusleiste wird nicht mehr eingefärbt: ab Android 15 ist
            // Edge-to-Edge verpflichtend und `statusBarColor` wirkungslos.
            // Gesteuert wird nur noch die Icon-Helligkeit.
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

/**
 * Wandelt "#RRGGBB" oder "#AARRGGBB" in eine [Color] um.
 * Liefert null bei leerem oder ungültigem Wert - die App soll wegen einer
 * falsch gepflegten Farbe nicht abstürzen.
 */
private fun parseHexColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        Color(android.graphics.Color.parseColor(hex.trim()))
    } catch (e: IllegalArgumentException) {
        null
    }
}
