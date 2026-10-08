package com.egrmeister.siptimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Calm timer-tool palette. All text pairs meet WCAG AA contrast on the warm white background. */
object SipColors {
    val WarmWhite = Color(0xFFFAF7F2)
    val Surface = Color(0xFFFFFDFA)
    val Navy = Color(0xFF15253F)
    val Teal = Color(0xFF2F7F7A)
    val TealDark = Color(0xFF1F5F5B)
    val PaleBlue = Color(0xFFDCEAF5)
    val PaleBlueDeep = Color(0xFFB9D3E8)
    val SoftGray = Color(0xFFE9E5DF)
    val Muted = Color(0xFF55606E)
    val Outline = Color(0xFFCFC9C0)
}

private val scheme = lightColorScheme(
    primary = SipColors.Teal,
    onPrimary = Color.White,
    primaryContainer = SipColors.PaleBlue,
    onPrimaryContainer = SipColors.Navy,
    secondary = SipColors.Muted,
    onSecondary = Color.White,
    secondaryContainer = SipColors.SoftGray,
    onSecondaryContainer = SipColors.Navy,
    tertiary = SipColors.TealDark,
    background = SipColors.WarmWhite,
    onBackground = SipColors.Navy,
    surface = SipColors.WarmWhite,
    onSurface = SipColors.Navy,
    surfaceVariant = SipColors.SoftGray,
    onSurfaceVariant = SipColors.Muted,
    surfaceContainer = SipColors.Surface,
    surfaceContainerHigh = SipColors.Surface,
    surfaceContainerHighest = SipColors.SoftGray,
    surfaceContainerLow = SipColors.Surface,
    surfaceContainerLowest = Color.White,
    outline = SipColors.Outline,
    outlineVariant = SipColors.SoftGray,
    error = Color(0xFF9A3B2E),
    onError = Color.White,
)

private val base = Typography()

private val typography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, color = SipColors.Navy),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Medium),
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall,
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium,
    labelSmall = base.labelSmall,
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

@Composable
fun SipTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
