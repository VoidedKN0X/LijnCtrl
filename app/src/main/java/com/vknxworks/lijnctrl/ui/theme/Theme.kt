package com.vknxworks.lijnctrl.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.lijnctrl.R

/**
 * Ctrl / Signal: the shared look of the Ctrl series. Dark only, near-black with one accent.
 * Change [Accent] here to recolour the whole app.
 */
object CtrlColors {
    val Accent = Color(0xFFB48CFF)
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    /** Card borders. */
    val Outline = Color(0xFF232328)
    /** Borders on controls (buttons, fields, the tab bar). */
    val ControlOutline = Color(0xFF2A2A2F)
    val Divider = Color(0xFF1E1E23)
    val OnBackground = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    /** Disabled, stopped, timetable-only. */
    val Inactive = Color(0xFF8C8C94)
    val Error = Color(0xFFFF6B6B)

    val SwitchOffTrack = Color(0xFF2A2A30)
    val PinEmpty = Color(0xFF3A3A41)
    val ProgressTrack = Color(0xFF26262C)
}

/** Corner sizes of the Ctrl series. */
object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Control = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(18.dp)
    val Square = RoundedCornerShape(14.dp)
    val Badge = RoundedCornerShape(9.dp)
    val Dot = RoundedCornerShape(2.dp)
}

val CardBorder = BorderStroke(1.dp, CtrlColors.Outline)
val ControlBorder = BorderStroke(1.dp, CtrlColors.ControlOutline)

/** The 1dp edge on dialogs (their own shape is 26dp, from the theme). */
val CtrlDialogModifier: Modifier = Modifier.border(1.dp, CtrlColors.Outline, RoundedCornerShape(26.dp))

private val Scheme = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.OnBackground,
    onSecondary = CtrlColors.OnAccent,
    secondaryContainer = CtrlColors.Raised,
    onSecondaryContainer = CtrlColors.OnBackground,
    tertiary = CtrlColors.OnBackground,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.OnBackground,
    background = CtrlColors.Background,
    onBackground = CtrlColors.OnBackground,
    surface = CtrlColors.Surface,
    onSurface = CtrlColors.OnBackground,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    // Same as surface, so elevated surfaces aren't tinted with the accent.
    surfaceTint = CtrlColors.Surface,
    inverseSurface = CtrlColors.OnBackground,
    inverseOnSurface = CtrlColors.OnAccent,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Surface,
    surfaceContainerHighest = CtrlColors.Raised,
    outline = CtrlColors.ControlOutline,
    outlineVariant = CtrlColors.Outline,
    scrim = Color(0xCC000000),
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF2A1416),
    onErrorContainer = CtrlColors.OnBackground,
)

private fun grotesk(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Space Grotesk (variable, SIL OFL 1.1): every piece of UI text and the headings. */
val SpaceGrotesk = FontFamily(grotesk(400), grotesk(500), grotesk(600), grotesk(700))

/** JetBrains Mono (SIL OFL 1.1): numbers, times, data readouts, section labels and the wordmark. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(56, 58, 700, -0.04),
    displayMedium = style(48, 50, 700, -0.04),
    displaySmall = style(36, 40, 700, -0.035),
    headlineLarge = style(40, 42, 700, -0.035),
    headlineMedium = style(30, 34, 700, -0.03),
    headlineSmall = style(24, 28, 700, -0.02),
    titleLarge = style(20, 26, 600, -0.01),
    titleMedium = style(18, 24, 600),
    titleSmall = style(15, 20, 600),
    bodyLarge = style(16, 22, 400),
    bodyMedium = style(14, 20, 400),
    bodySmall = style(12, 17, 400),
    labelLarge = style(14, 20, 600),
    labelMedium = style(12, 16, 500),
    labelSmall = style(11, 14, 500, 0.02),
)

/** Text styles beyond Material's scale. */
object CtrlType {
    /** Section headers: mono, uppercase (call .uppercase()), tracked out, muted. */
    val Label = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.16.em,
    )
    val Wordmark = TextStyle(
        fontFamily = JetBrainsMono,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.14.em,
    )
    /** Small data: stop numbers, clock times next to a countdown, "LIVE". */
    val Data = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium)
    val DataSmall = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em)
    /** A time or count in a list row. */
    val Readout = TextStyle(fontFamily = JetBrainsMono, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em)
    /** The big countdown on a hero card. */
    val Countdown = TextStyle(fontFamily = JetBrainsMono, fontSize = 72.sp, lineHeight = 68.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.06).em)
    val Key = TextStyle(fontFamily = JetBrainsMono, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/** Dark only: no light theme and no wallpaper colours, so every Ctrl app looks the same. */
@Composable
fun LijnCtrlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, shapes = AppShapes, content = content)
}
