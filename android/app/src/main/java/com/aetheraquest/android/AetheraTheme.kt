package com.aetheraquest.android

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Aethera Neon Palette ──────────────────────────────────────────────────────

/** Deepest navy: matches the logo's dark background. */
val SpaceNavy       = Color(0xFF00040E)

/** Panel background: one step lighter than SpaceNavy. */
val DarkPanel       = Color(0xFF0A1628)

/** Surface for cards and input fields. */
val DarkSurface     = Color(0xFF0F1E33)

/** Electric blue: primary action colour from the dragon's glow. */
val NeonCyan        = Color(0xFF00BFFF)

/** Cyan glow for the primary container / selected state. */
val NeonCyanDark    = Color(0xFF004466)

/** Neon green: matches the circuit-board / teal accent inside the triangle. */
val NeonGreen       = Color(0xFF1DE9B6)

/** Hot magenta / pink: decorative accent (dragon claws / lightning). */
val NeonPink        = Color(0xFFFF3D7F)

/** Full white for body text. */
val PureWhite       = Color(0xFFFFFFFF)

/** Dimmed white for secondary / placeholder text. */
val DimWhite        = Color(0xFFB0C4DE)

/** Error / danger red. */
val NeonRed         = Color(0xFFFF5252)

/** Warning amber. */
val NeonAmber       = Color(0xFFFFAB40)

/** HP bar colours */
val HpGreen         = Color(0xFF00E676)
val HpYellow        = Color(0xFFFFEA00)
val HpRed           = Color(0xFFFF1744)

// ── Colour Scheme ─────────────────────────────────────────────────────────────

private val AetheraDark = darkColorScheme(
    primary           = NeonCyan,
    onPrimary        = SpaceNavy,
    primaryContainer  = NeonCyanDark,
    onPrimaryContainer = PureWhite,
    secondary        = NeonGreen,
    onSecondary      = SpaceNavy,
    tertiary         = NeonPink,
    onTertiary       = SpaceNavy,
    error            = NeonRed,
    onError          = PureWhite,
    errorContainer    = Color(0xFF5A0010),
    onErrorContainer  = Color(0xFFFFDAD6),
    background        = SpaceNavy,
    onBackground      = PureWhite,
    surface           = DarkSurface,
    onSurface         = PureWhite,
    surfaceVariant    = DarkPanel,
    onSurfaceVariant  = DimWhite,
    outline           = NeonCyan.copy(alpha = 0.45f),
    outlineVariant    = NeonCyan.copy(alpha = 0.20f),
    inverseSurface    = PureWhite,
    inverseOnSurface  = SpaceNavy,
    inversePrimary    = NeonCyanDark,
)

// ── Typography ────────────────────────────────────────────────────────────────

private val AetheraTypography = Typography(
    displayLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,  fontSize = 57.sp, letterSpacing = (-0.25).sp, color = PureWhite),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,  fontSize = 45.sp, color = PureWhite),
    displaySmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,  fontSize = 36.sp, color = PureWhite),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,  fontSize = 32.sp, color = PureWhite),
    headlineMedium= TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,  fontSize = 28.sp, color = PureWhite),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = PureWhite),
    titleLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = PureWhite),
    titleMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.15.sp, color = PureWhite),
    titleSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,  fontSize = 14.sp, letterSpacing = 0.1.sp, color = PureWhite),
    bodyLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,  fontSize = 16.sp, letterSpacing = 0.5.sp, color = PureWhite),
    bodyMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,  fontSize = 14.sp, letterSpacing = 0.25.sp, color = PureWhite),
    bodySmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,  fontSize = 12.sp, letterSpacing = 0.4.sp, color = DimWhite),
    labelLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,  fontSize = 14.sp, letterSpacing = 0.1.sp, color = NeonCyan),
    labelMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,  fontSize = 12.sp, letterSpacing = 0.5.sp, color = DimWhite),
    labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,  fontSize = 11.sp, letterSpacing = 0.5.sp, color = DimWhite),
)

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun AetheraTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AetheraDark,
        typography  = AetheraTypography,
        content     = content,
    )
}
