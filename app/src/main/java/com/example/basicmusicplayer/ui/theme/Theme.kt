package com.example.basicmusicplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// App colors
val Black = Color(0xFF000000)
val SoftBlack = Color(0xFF070707)
val White = Color(0xFFFFFFFF)
val SoftWhite = Color(0xFFF3F4F5)

// Button colors
val GreenButton = Color(0xFF34C759)
val BlueButton = Color(0xFF007AFF)
val RedButton = Color(0xFFFF0000)

private val DarkColorScheme = darkColorScheme(
    primary = BlueButton,
    secondary = GreenButton,
    tertiary = RedButton,
    background = Black,
    surface = SoftBlack,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = White,
    onSurface = SoftWhite
)

@Composable
fun WXYCTheme(
    content: @Composable () -> Unit
) {
    // Always use dark theme since the app has a black background design
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
