package com.hanifedma.tally.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Tally's theme.
 *
 * Deliberately not Material You dynamic colour: the app's identity is a
 * specific indigo, shared with the web app, and letting the wallpaper repaint
 * it would break that. It also follows the app's own setting rather than the
 * system's, because the setting lives on the account and has to match what
 * the browser is doing.
 */

/**
 * Not `staticCompositionLocalOf`.
 *
 * The static kind does not track who reads it: it is for values that are
 * fixed for the life of a composition, and it buys its speed by not
 * invalidating readers when the value changes. Tally's palette is not fixed —
 * it changes every time someone switches theme, and (more quietly) once at
 * every cold start, when the ledger loads a moment after the first frame and
 * says "light".
 *
 * With the static kind that produced a genuinely confusing bug: the theme
 * *value* propagated perfectly — the toggle's own icon flipped to a moon —
 * while every colour on the screen stayed dark, because nothing that read
 * `.current` had been told to look again.
 */
val LocalTallyColors = compositionLocalOf { DarkColors }

private fun scheme(c: TallyColors, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = c.accent, onPrimary = c.accentContrast,
        secondary = c.accent, onSecondary = c.accentContrast,
        background = c.bg, onBackground = c.text,
        surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
        outline = c.border, outlineVariant = c.border,
        error = c.danger, onError = c.accentContrast,
        scrim = c.scrim,
        surfaceContainer = c.elevated,
        surfaceContainerHigh = c.surface3,
        surfaceContainerHighest = c.surface3,
        surfaceContainerLow = c.surface2,
        surfaceContainerLowest = c.bg,
        inverseSurface = c.text, inverseOnSurface = c.bg,
    )
} else {
    lightColorScheme(
        primary = c.accent, onPrimary = c.accentContrast,
        secondary = c.accent, onSecondary = c.accentContrast,
        background = c.bg, onBackground = c.text,
        surface = c.surface, onSurface = c.text,
        surfaceVariant = c.surface2, onSurfaceVariant = c.muted,
        outline = c.border, outlineVariant = c.border,
        error = c.danger, onError = c.accentContrast,
        scrim = c.scrim,
        surfaceContainer = c.elevated,
        surfaceContainerHigh = c.surface3,
        surfaceContainerHighest = c.surface3,
        surfaceContainerLow = c.surface2,
        surfaceContainerLowest = c.bg,
        inverseSurface = c.text, inverseOnSurface = c.bg,
    )
}

// The system font, at the same sizes and weights the stylesheet uses. Trimmed
// line height on the display sizes, because an amount on its own line should
// not carry a paragraph's leading.
private val tight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

val TallyTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.8).sp,
        lineHeightStyle = tight,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 27.sp, letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.1).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 13.5f.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.5f.sp, lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 11.5f.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp,
    ),
)

@Composable
fun TallyTheme(
    dark: Boolean = true, // dark is the app's default, as on the web
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors

    // The app draws behind the system bars, so their icons are the app's
    // problem. Set once at startup they would be right exactly half the
    // time: switching to light left a white clock on a white header, which
    // is not a small thing when it is the only clock on the screen.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalTallyColors provides colors) {
        MaterialTheme(
            colorScheme = scheme(colors, dark),
            typography = TallyTypography,
            content = content,
        )
    }
}
