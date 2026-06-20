package com.example.pictureviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val bgGradient: Brush,
    val glassSurface: Color,
    val glassBorder: Color,
    val neuShadow: Color,
    val neuHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentGradient: Brush,
    val accent: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color
)

private val LightBase = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    background = BgLightStart,
    onBackground = TextPrimaryLight,
    surface = BgLightStart,
    onSurface = TextPrimaryLight,
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFF73777F)
)

private val DarkBase = darkColorScheme(
    primary = Color(0xFF64B5F6),
    onPrimary = Color(0xFF003063),
    background = BgDarkStart,
    onBackground = TextPrimaryDark,
    surface = BgDarkStart,
    onSurface = TextPrimaryDark,
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = TextSecondaryDark,
    outline = Color(0xFF8D9199)
)

private fun resolveAppColors(dark: Boolean): AppColors {
    return if (dark) {
        AppColors(
            bgGradient = Brush.verticalGradient(listOf(BgDarkStart, BgDarkEnd)),
            glassSurface = GlassDark,
            glassBorder = GlassBorderDark,
            neuShadow = NeuShadowDark,
            neuHighlight = NeuHighlightDark,
            textPrimary = TextPrimaryDark,
            textSecondary = TextSecondaryDark,
            textTertiary = TextTertiaryDark,
            accentGradient = Brush.horizontalGradient(listOf(AccentBlue, AccentBlueEnd)),
            accent = AccentBlue,
            statusSuccess = StatusSuccess,
            statusWarning = StatusWarning,
            statusError = StatusError
        )
    } else {
        AppColors(
            bgGradient = Brush.verticalGradient(listOf(BgLightStart, BgLightEnd)),
            glassSurface = GlassLight,
            glassBorder = GlassBorderLight,
            neuShadow = NeuShadowLight,
            neuHighlight = NeuHighlightLight,
            textPrimary = TextPrimaryLight,
            textSecondary = TextSecondaryLight,
            textTertiary = TextTertiaryLight,
            accentGradient = Brush.horizontalGradient(listOf(AccentBlue, AccentBlueEnd)),
            accent = AccentBlue,
            statusSuccess = StatusSuccess,
            statusWarning = StatusWarning,
            statusError = StatusError
        )
    }
}

val LocalAppColors = androidx.compose.runtime.staticCompositionLocalOf {
    resolveAppColors(false)
}

object AppTheme {
    val colors: AppColors
        @Composable
        get() = LocalAppColors.current
}

@Composable
fun PictureViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkBase else LightBase
    androidx.compose.runtime.CompositionLocalProvider(
        LocalAppColors provides resolveAppColors(darkTheme)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
