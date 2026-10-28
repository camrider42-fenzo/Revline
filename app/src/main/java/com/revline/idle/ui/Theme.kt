package com.revline.idle.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Lime = Color(0xFFE4FF47)
val Cyan = Color(0xFF4DFFF0)
val Amber = Color(0xFFFFC14A)
val Red = Color(0xFFFF3B30)
val Olive = Color(0xFF8A9A3A)

/** When this is on, the bench drawing and the speaker stay still. */
val LocalBatterySaver = compositionLocalOf { false }

private val scheme = darkColorScheme(
    primary = Lime,
    onPrimary = Black,
    secondary = Cyan,
    onSecondary = Black,
    tertiary = Amber,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Black,
    onSurfaceVariant = White,
    surfaceTint = Black,
    outline = White,
    outlineVariant = Lime,
    error = Red,
    onError = Black,
)

private val type = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = White),
    bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = White),
    bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = White),
    titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = White),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = Lime),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Lime),
)

@Composable
fun RevlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
