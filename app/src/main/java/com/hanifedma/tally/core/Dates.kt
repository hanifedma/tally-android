package com.hanifedma.tally.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeParseException

/**
 * Dates — the Kotlin half of the date section of money.js.
 *
 * A day key is "YYYY-MM-DD" and never anything else. The web app does its
 * arithmetic in UTC to dodge daylight saving; java.time's LocalDate has no
 * time zone to get wrong in the first place, so this is the simpler of the
 * two implementations and gives identical answers.
 */
object Dates {

    fun today(): String = LocalDate.now().toString()

    fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    fun isDayKey(key: String?): Boolean {
        if (key == null || key.length != 10) return false
        return try {
            LocalDate.parse(key)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }

    fun parse(key: String): LocalDate =
        if (isDayKey(key)) LocalDate.parse(key) else LocalDate.now()

    fun addDays(key: String, n: Long): String = parse(key).plusDays(n).toString()

    /** Clamps to the end of a shorter month, exactly as addMonths does in JS. */
    fun addMonths(key: String, n: Long): String = parse(key).plusMonths(n).toString()

    /** 0 = Sunday … 6 = Saturday, matching getUTCDay() and week_start. */
    fun weekday(key: String): Int = parse(key).dayOfWeek.value % 7

    fun daysBetween(a: String, b: String): Long =
        java.time.temporal.ChronoUnit.DAYS.between(parse(a), parse(b))

    data class Period(val start: String, val end: String) {
        operator fun contains(key: String) = key >= start && key <= end
        val days: Long get() = daysBetween(start, end) + 1
    }

    /**
     * The budget period containing `key`.
     *
     * With monthStart = 1 this is the calendar month. Set it to a payday and
     * the period runs payday to payday, which is how anyone paid monthly
     * actually thinks about "this month's money".
     */
    fun periodOf(key: String, monthStart: Int = 1): Period {
        val ms = monthStart.coerceIn(1, 28)
        val d = parse(key)
        val startMonth = if (d.dayOfMonth >= ms) YearMonth.from(d) else YearMonth.from(d).minusMonths(1)
        val start = startMonth.atDay(ms)
        val end = start.plusMonths(1).minusDays(1)
        return Period(start.toString(), end.toString())
    }

    fun shift(period: Period, n: Long, monthStart: Int = 1): Period =
        periodOf(addMonths(period.start, n), monthStart)

    /** How far through the period today is, 0..1. Past periods are 1. */
    fun paceThrough(period: Period, today: String = today()): Double {
        val days = period.days
        if (days <= 0) return 1.0
        val elapsed = when {
            today > period.end -> days
            today < period.start -> 0L
            else -> daysBetween(period.start, today) + 1
        }
        return elapsed.toDouble() / days
    }

    /** Days of the period already gone, counting today. */
    fun elapsedDays(period: Period, today: String = today()): Long = when {
        today > period.end -> period.days
        today < period.start -> 0L
        else -> daysBetween(period.start, today) + 1
    }
}
