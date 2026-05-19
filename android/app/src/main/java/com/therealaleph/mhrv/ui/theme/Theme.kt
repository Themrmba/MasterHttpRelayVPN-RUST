package com.therealaleph.mhrv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------- رنگ‌های نئونی ----------
val NeonCyan       = Color(0xFF00E5FF)
val NeonMagenta    = Color(0xFFFF007F)
val NeonPurple     = Color(0xFFB000FF)
val NeonGreen      = Color(0xFF39FF14)

// ---------- پس‌زمینه‌های تیره ----------
val DarkBg         = Color(0xFF0A0A0A)
val DarkSurface    = Color(0xFF121212)
val DarkCard       = Color(0xFF1E1E1E)
val DarkBorder     = Color(0xFF2C2C2C)

private val NeonDarkScheme = darkColorScheme(
    primary              = NeonCyan,
    onPrimary            = Color.Black,
    primaryContainer     = NeonCyan.copy(alpha = 0.12f),
    onPrimaryContainer   = NeonCyan,

    secondary            = NeonMagenta,
    onSecondary          = Color.Black,
    secondaryContainer   = NeonMagenta.copy(alpha = 0.12f),
    onSecondaryContainer = NeonMagenta,

    tertiary             = NeonPurple,
    onTertiary           = Color.White,
    tertiaryContainer    = NeonPurple.copy(alpha = 0.12f),
    onTertiaryContainer  = NeonPurple,

    error                = Color(0xFFFF5252),
    onError              = Color.Black,

    background           = DarkBg,
    onBackground         = Color(0xFFE0E0E0),

    surface              = DarkSurface,
    onSurface            = Color(0xFFE0E0E0),

    surfaceVariant       = DarkCard,
    onSurfaceVariant     = Color(0xFFB0B0B0),

    outline              = DarkBorder,
    outlineVariant       = DarkBorder,
)

private val NeonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun MhrvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonDarkScheme,
        shapes      = NeonShapes,
        content     = content,
    )
}
