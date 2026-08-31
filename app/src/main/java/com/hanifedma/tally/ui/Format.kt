package com.hanifedma.tally.ui

import com.hanifedma.tally.core.Dates
import com.hanifedma.tally.core.Money
import com.hanifedma.tally.i18n.Strings
import java.time.format.TextStyle
import java.util.Locale

/**
 * Money, dates and words in whichever language is on screen.
 *
 * The names of days and months come from java.time rather than a table, for
 * the same reason the web app takes them from Intl: there is no version of
 * that list worth maintaining by hand, in either language.
 */
class Fmt(val lang: String, private val mainCurrency: String) {

    val locale: Locale = if (lang == "ko") Locale.KOREA else Locale.US

    fun t(key: String, vars: Map<String, Any?> = emptyMap()): String =
        Strings.get(lang, key, vars)

    fun money(
        minor: Long,
        code: String = mainCurrency,
        sign: Money.Sign = Money.Sign.AUTO,
        symbol: Boolean = true,
    ): String = Money.format(minor, code, locale, sign, symbol)

    fun compact(minor: Long, code: String = mainCurrency): String =
        Money.formatCompact(minor, code, locale)

    /** "August 2026" / "2026년 8월" */
    fun monthYear(dayKey: String): String {
        val d = Dates.parse(dayKey)
        return if (lang == "ko") "${d.year}년 ${d.monthValue}월"
        else "${d.month.getDisplayName(TextStyle.FULL, locale)} ${d.year}"
    }

    /** Just the month, for a chart axis. */
    fun monthShort(dayKey: String): String {
        val d = Dates.parse(dayKey)
        return if (lang == "ko") "${d.monthValue}월"
        else d.month.getDisplayName(TextStyle.SHORT, locale)
    }

    /** "5 August" / "8월 5일" — the day heading in the log. */
    fun dayLong(dayKey: String): String {
        val d = Dates.parse(dayKey)
        return if (lang == "ko") "${d.monthValue}월 ${d.dayOfMonth}일"
        else "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.FULL, locale)}"
    }

    /** "5 Aug 2026" / "2026. 8. 5." — compact, for ranges and pickers. */
    fun dayShort(dayKey: String): String {
        val d = Dates.parse(dayKey)
        return if (lang == "ko") "${d.year}. ${d.monthValue}. ${d.dayOfMonth}."
        else "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, locale)} ${d.year}"
    }

    fun weekdayShort(dayKey: String): String =
        Dates.parse(dayKey).dayOfWeek.getDisplayName(TextStyle.SHORT, locale)

    /** Minutes past midnight as a clock reading. */
    fun time(minutes: Int): String {
        val t = java.time.LocalTime.of(minutes / 60, minutes % 60)
        return t.format(
            java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
                .withLocale(locale)
        )
    }

    fun percent(ratio: Double): String {
        val nf = java.text.NumberFormat.getPercentInstance(locale)
        nf.maximumFractionDigits = 0
        return nf.format(if (ratio.isFinite()) ratio else 0.0)
    }

    fun currencyName(code: String): String = Money.name(code, lang)
}

/** The glyph for each kind of account, matching the web app's list. */
fun accountGlyph(kind: String): String = when (kind) {
    "cash" -> "💵"
    "bank" -> "🏦"
    "card" -> "💳"
    "ewallet" -> "📱"
    "savings" -> "🐖"
    else -> "💰"
}
