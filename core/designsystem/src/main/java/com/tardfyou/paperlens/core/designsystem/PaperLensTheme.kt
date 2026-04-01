package com.tardfyou.paperlens.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tardfyou.paperlens.core.model.ContrastMode
import com.tardfyou.paperlens.core.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4E8DF5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    background = Color(0xFFF4F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2F8),
    onBackground = Color(0xFF0F1728),
    onSurface = Color(0xFF0F1728),
    onSurfaceVariant = Color(0xFF5A6780),
    outline = Color(0xFFDCE3EE),
    error = Color(0xFFDB5B5B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB5FF),
    onPrimary = Color(0xFF0E1117),
    primaryContainer = Color(0xFF223149),
    background = Color(0xFF0E1117),
    surface = Color(0xFF171C24),
    surfaceVariant = Color(0xFF1E2530),
    onBackground = Color(0xFFF3F7FF),
    onSurface = Color(0xFFF3F7FF),
    onSurfaceVariant = Color(0xFFAAB6CB),
    outline = Color(0xFF364151),
    error = Color(0xFFFF9A9A),
)

val PaperLensTypography = Typography(
    displaySmall = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

val PaperLensShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Immutable
data class PaperLensSpacing(
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 12.dp,
    val lg: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 20.dp,
    val xxl: androidx.compose.ui.unit.Dp = 24.dp,
    val giant: androidx.compose.ui.unit.Dp = 32.dp,
)

val LocalPaperLensSpacing = staticCompositionLocalOf { PaperLensSpacing() }

object PaperLensTheme {
    val spacing: PaperLensSpacing
        @Composable get() = LocalPaperLensSpacing.current
}

@Composable
fun PaperLensTheme(
    themeMode: ThemeMode = ThemeMode.System,
    contrastMode: ContrastMode = ContrastMode.Standard,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colorScheme = selectColors(darkTheme = darkTheme, contrastMode = contrastMode)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPaperLensSpacing provides PaperLensSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PaperLensTypography,
            shapes = PaperLensShapes,
            content = content,
        )
    }
}

private fun selectColors(darkTheme: Boolean, contrastMode: ContrastMode): ColorScheme {
    val base = if (darkTheme) DarkColors else LightColors
    if (contrastMode != ContrastMode.High) return base
    return base.copy(
        background = if (darkTheme) Color.Black else Color.White,
        surface = if (darkTheme) Color(0xFF05070A) else Color.White,
        onBackground = if (darkTheme) Color.White else Color.Black,
        onSurface = if (darkTheme) Color.White else Color.Black,
        onSurfaceVariant = if (darkTheme) Color(0xFFE5ECFA) else Color(0xFF263147),
        outline = if (darkTheme) Color(0xFF99A8C2) else Color(0xFF465267),
    )
}
