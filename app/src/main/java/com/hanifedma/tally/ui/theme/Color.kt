package com.hanifedma.tally.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tally's palette — the same hex values as styles.css, so a screenshot of
 * the phone and a screenshot of the browser are the same app.
 *
 * The two ledger directions are deliberately not the brand colour: a colour
 * that means "Tally" and "money coming in" at the same time means neither.
 */
data class TallyColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val elevated: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentContrast: Color,
    val income: Color,
    val expense: Color,
    val transfer: Color,
    val danger: Color,
    val dangerSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val track: Color,
    val scrim: Color,
    val palette: Map<String, Color>,
) {
    /** A category or account colour by name, falling back to grey. */
    fun named(name: String): Color = palette[name] ?: palette.getValue("gray")
}

val DarkColors = TallyColors(
    bg = Color(0xFF0F0F10),
    surface = Color(0xFF18181B),
    surface2 = Color(0xFF1F1F23),
    surface3 = Color(0xFF27272C),
    elevated = Color(0xFF1C1C20),
    border = Color(0xFF2E2E34),
    borderStrong = Color(0xFF40404A),
    text = Color(0xFFF2F2F4),
    muted = Color(0xFFA3A3AD),
    faint = Color(0xFF6C6C78),
    accent = Color(0xFF8B93FF),
    accentSoft = Color(0x298B93FF),
    accentContrast = Color(0xFF12122A),
    income = Color(0xFF4ADE80),
    expense = Color(0xFFFB7185),
    transfer = Color(0xFF7DD3FC),
    danger = Color(0xFFF4566B),
    dangerSoft = Color(0x24F4566B),
    warn = Color(0xFFFBBF24),
    warnSoft = Color(0x21FBBF24),
    track = Color(0xFF2A2A30),
    scrim = Color(0x9E000000),
    palette = mapOf(
        "indigo" to Color(0xFFA5B4FC),
        "blue" to Color(0xFF7AA2F7),
        "sky" to Color(0xFF67E8F9),
        "teal" to Color(0xFF5EEAD4),
        "green" to Color(0xFF86EFAC),
        "lime" to Color(0xFFBEF264),
        "amber" to Color(0xFFFCD34D),
        "orange" to Color(0xFFFDBA74),
        "rose" to Color(0xFFFDA4AF),
        "pink" to Color(0xFFF9A8D4),
        "purple" to Color(0xFFD8B4FE),
        "gray" to Color(0xFFB4B4BE),
    ),
)

val LightColors = TallyColors(
    bg = Color(0xFFFBFBFD),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF3F3F6),
    surface3 = Color(0xFFE9E9EF),
    elevated = Color(0xFFFFFFFF),
    border = Color(0xFFE6E6EC),
    borderStrong = Color(0xFFD2D2DC),
    text = Color(0xFF17171C),
    muted = Color(0xFF5B5B68),
    faint = Color(0xFF8A8A98),
    accent = Color(0xFF4F46E5),
    accentSoft = Color(0x1A4F46E5),
    accentContrast = Color(0xFFFFFFFF),
    income = Color(0xFF16A34A),
    expense = Color(0xFFE11D48),
    transfer = Color(0xFF0284C7),
    danger = Color(0xFFDC2626),
    dangerSoft = Color(0x17DC2626),
    // Darker than the dark theme's amber: #FBBF24 on white fails contrast.
    warn = Color(0xFFB45309),
    warnSoft = Color(0x1AB45309),
    track = Color(0xFFE9E9EF),
    scrim = Color(0x66141420),
    palette = mapOf(
        "indigo" to Color(0xFF6366F1),
        "blue" to Color(0xFF2563EB),
        "sky" to Color(0xFF0891B2),
        "teal" to Color(0xFF14B8A6),
        "green" to Color(0xFF22C55E),
        "lime" to Color(0xFF84CC16),
        "amber" to Color(0xFFF59E0B),
        "orange" to Color(0xFFF97316),
        "rose" to Color(0xFFF43F5E),
        "pink" to Color(0xFFEC4899),
        "purple" to Color(0xFFA855F7),
        "gray" to Color(0xFF71717A),
    ),
)
