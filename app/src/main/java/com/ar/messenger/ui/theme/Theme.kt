package com.ar.messenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Vibrant purple -> pink -> coral palette (replaces the old flat indigo/amber look)
val ArPrimary = Color(0xFF7C4DFF)
val ArPrimaryDark = Color(0xFF4A2FBF)
val ArAccent = Color(0xFFFF6EC7)
val ArAccentWarm = Color(0xFFFF9F5A)
val ArBackground = Color(0xFF0B0A17)
val ArBackgroundDeep = Color(0xFF17132B)
val ArSurface = Color(0xFF1E1A34)
val ArBubbleOutStart = Color(0xFF8E5CFF)
val ArBubbleOutEnd = Color(0xFFFF6EC7)
val ArBubbleIn = Color(0xFF241F3D)
val ArTextPrimary = Color(0xFFF5F3FF)
val ArTextSecondary = Color(0xFFAFA8C9)
val ArOnline = Color(0xFF4CE0B3)
val ArReadTick = Color(0xFF4FC3F7)

/** Signature background gradient — used behind login, splash and app bars. */
val ArHeroGradient = Brush.linearGradient(
    colors = listOf(ArBackgroundDeep, ArBackground, Color(0xFF1B1030))
)

/** Gradient used for the "outgoing message" bubble and primary CTAs. */
val ArPrimaryGradient = Brush.linearGradient(
    colors = listOf(ArBubbleOutStart, ArBubbleOutEnd)
)

/** Pool of gradient pairs used to auto-generate a unique avatar color per user. */
val ArAvatarGradients = listOf(
    listOf(Color(0xFF8E5CFF), Color(0xFFFF6EC7)),
    listOf(Color(0xFFFF9F5A), Color(0xFFFF5C8A)),
    listOf(Color(0xFF4CE0B3), Color(0xFF3B82F6)),
    listOf(Color(0xFFFFD166), Color(0xFFFF6EC7)),
    listOf(Color(0xFF3B82F6), Color(0xFF8E5CFF)),
    listOf(Color(0xFFFF5C8A), Color(0xFF8E5CFF)),
    listOf(Color(0xFF4CE0B3), Color(0xFFFFD166)),
)

private val ArDarkScheme = darkColorScheme(
    primary = ArPrimary,
    secondary = ArAccent,
    tertiary = ArAccentWarm,
    background = ArBackground,
    surface = ArSurface,
    onPrimary = ArTextPrimary,
    onBackground = ArTextPrimary,
    onSurface = ArTextPrimary
)

@Composable
fun ARTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArDarkScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
