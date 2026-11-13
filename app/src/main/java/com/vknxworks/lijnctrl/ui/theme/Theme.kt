package com.vknxworks.lijnctrl.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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

/** One Ctrl / Signal palette. [CtrlColors.Dark] and [CtrlColors.Light] are the two; screens read the current one. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    /** Accent as a fill: hero cards, primary buttons, the selected tab, switch tracks, PIN cells. */
    val accent: Color,
    val onAccent: Color,
    /** Accent as text or an icon on the background (wordmark slash, LIVE, links, focused fields). */
    val accentText: Color,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val outline: Color,
    val controlOutline: Color,
    val divider: Color,
    val onBackground: Color,
    val muted: Color,
    val inactive: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val switchOffTrack: Color,
    val switchOffKnob: Color,
    val pinEmpty: Color,
    val progressTrack: Color,
    val progressFill: Color,
)

val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

/**
 * Ctrl / Signal: the shared look of the Ctrl series, dark or light following the phone's setting.
 * The accent is defined once here ([ACCENT]); everything else reads the current palette through these
 * properties (or [LocalCtrlColors]).
 */
object CtrlColors {
    private val ACCENT = Color(0xFFB48CFF)
    private val ON_ACCENT = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        accent = ACCENT,
        onAccent = ON_ACCENT,
        accentText = ACCENT,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        outline = Color(0xFF232328),
        controlOutline = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        onBackground = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        inactive = Color(0xFF8C8C94),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        errorContainer = Color(0xFF2A1416),
        switchOffTrack = Color(0xFF2A2A30),
        switchOffKnob = Color(0xFF8C8C94),
        pinEmpty = Color(0xFF3A3A41),
        progressTrack = Color(0xFF26262C),
        progressFill = Color(0xFFF4F4F2),
    )

    /** "Signal Light". The accent fill stays the same; accent text is darker so it reads on white. */
    val Light = CtrlPalette(
        isDark = false,
        accent = ACCENT,
        onAccent = ON_ACCENT,
        accentText = Color(0xFF6A3BDB),
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        outline = Color(0xFFDEDED7),
        controlOutline = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        onBackground = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        inactive = Color(0xFF74747C),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE9E9),
        switchOffTrack = Color(0xFFDCDCD5),
        switchOffKnob = Color(0xFF74747C),
        pinEmpty = Color(0xFFC9C9C1),
        progressTrack = Color(0xFFE2E2DC),
        progressFill = Color(0xFF0B0B0D),
    )

    private val p: CtrlPalette @Composable @ReadOnlyComposable get() = LocalCtrlColors.current

    val Accent: Color @Composable @ReadOnlyComposable get() = p.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = p.onAccent
    val AccentText: Color @Composable @ReadOnlyComposable get() = p.accentText
    val Background: Color @Composable @ReadOnlyComposable get() = p.background
    val Surface: Color @Composable @ReadOnlyComposable get() = p.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = p.raised
    /** Card borders. */
    val Outline: Color @Composable @ReadOnlyComposable get() = p.outline
    /** Borders on controls (buttons, fields, the tab bar). */
    val ControlOutline: Color @Composable @ReadOnlyComposable get() = p.controlOutline
    val Divider: Color @Composable @ReadOnlyComposable get() = p.divider
    val OnBackground: Color @Composable @ReadOnlyComposable get() = p.onBackground
    val Muted: Color @Composable @ReadOnlyComposable get() = p.muted
    /** Disabled, stopped, timetable-only. */
    val Inactive: Color @Composable @ReadOnlyComposable get() = p.inactive
    val Error: Color @Composable @ReadOnlyComposable get() = p.error
    val SwitchOffTrack: Color @Composable @ReadOnlyComposable get() = p.switchOffTrack
    val SwitchOffKnob: Color @Composable @ReadOnlyComposable get() = p.switchOffKnob
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = p.pinEmpty
    val ProgressTrack: Color @Composable @ReadOnlyComposable get() = p.progressTrack
    val ProgressFill: Color @Composable @ReadOnlyComposable get() = p.progressFill
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

val CardBorder: BorderStroke @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, CtrlColors.Outline)
val ControlBorder: BorderStroke @Composable @ReadOnlyComposable get() = BorderStroke(1.dp, CtrlColors.ControlOutline)

/** The 1dp edge on dialogs (their own shape is 26dp, from the theme). */
val CtrlDialogModifier: Modifier @Composable @ReadOnlyComposable get() = Modifier.border(1.dp, CtrlColors.Outline, RoundedCornerShape(26.dp))

private fun scheme(c: CtrlPalette): ColorScheme {
    // Material's "primary" is mostly text and icons (links, selected items, pickers), so it takes the accent text
    // colour; fills that should be the accent use CtrlColors.Accent directly.
    val primary = c.accentText
    val onPrimary = if (c.isDark) c.onAccent else Color.White
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = c.accent,
        onPrimaryContainer = c.onAccent,
        inversePrimary = c.accent,
        secondary = c.onBackground,
        onSecondary = c.background,
        secondaryContainer = c.raised,
        onSecondaryContainer = c.onBackground,
        tertiary = c.onBackground,
        onTertiary = c.background,
        tertiaryContainer = c.raised,
        onTertiaryContainer = c.onBackground,
        background = c.background,
        onBackground = c.onBackground,
        surface = c.surface,
        onSurface = c.onBackground,
        surfaceVariant = c.raised,
        onSurfaceVariant = c.muted,
        // Same as surface, so elevated surfaces aren't tinted with the accent.
        surfaceTint = c.surface,
        inverseSurface = c.onBackground,
        inverseOnSurface = c.background,
        surfaceBright = c.raised,
        surfaceDim = c.background,
        surfaceContainerLowest = c.background,
        surfaceContainerLow = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.raised,
        outline = c.controlOutline,
        outlineVariant = c.outline,
        scrim = if (c.isDark) Color(0xCC000000) else Color(0x66000000),
        error = c.error,
        onError = c.onError,
        errorContainer = c.errorContainer,
        onErrorContainer = c.onBackground,
    )
}

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

/**
 * Follows the phone: dark when the system is dark, Signal Light otherwise. There's no in-app theme choice and
 * no wallpaper colours, so every Ctrl app looks the same.
 */
@Composable
fun LijnCtrlTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = scheme(palette), typography = AppTypography, shapes = AppShapes, content = content)
    }
}
