package com.hanifedma.tally.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * The money itself — the Kotlin half of money.js.
 *
 * These two files have to agree on every number, because the same ledger is
 * read by both. Where the web version explains a decision, it is not
 * repeated here; where the platforms genuinely differ (Java's number
 * formatting, Long instead of double for minor units) the difference is
 * noted.
 */
object Money {

    data class Currency(val symbol: String, val decimals: Int, val en: String, val ko: String)

    /**
     * `decimals` is what Tally shows, which is not always what the standard
     * says: IDR is formally two-decimal and nobody has priced anything in sen
     * for decades. Must match money.js exactly — it is the scale of every
     * amount already stored.
     */
    val CURRENCIES: Map<String, Currency> = linkedMapOf(
        "KRW" to Currency("₩", 0, "Korean won", "대한민국 원"),
        "IDR" to Currency("Rp", 0, "Indonesian rupiah", "인도네시아 루피아"),
        "USD" to Currency("$", 2, "US dollar", "미국 달러"),
        "EUR" to Currency("€", 2, "Euro", "유로"),
        "JPY" to Currency("¥", 0, "Japanese yen", "일본 엔"),
        "GBP" to Currency("£", 2, "Pound sterling", "영국 파운드"),
        "CNY" to Currency("CN¥", 2, "Chinese yuan", "중국 위안"),
        "SGD" to Currency("S$", 2, "Singapore dollar", "싱가포르 달러"),
        "MYR" to Currency("RM", 2, "Malaysian ringgit", "말레이시아 링깃"),
        "THB" to Currency("฿", 2, "Thai baht", "태국 바트"),
        "PHP" to Currency("₱", 2, "Philippine peso", "필리핀 페소"),
        "VND" to Currency("₫", 0, "Vietnamese dong", "베트남 동"),
        "INR" to Currency("₹", 2, "Indian rupee", "인도 루피"),
        "HKD" to Currency("HK$", 2, "Hong Kong dollar", "홍콩 달러"),
        "TWD" to Currency("NT$", 0, "New Taiwan dollar", "신 타이완 달러"),
        "AUD" to Currency("A$", 2, "Australian dollar", "호주 달러"),
        "CAD" to Currency("C$", 2, "Canadian dollar", "캐나다 달러"),
        "CHF" to Currency("CHF", 2, "Swiss franc", "스위스 프랑"),
        "NZD" to Currency("NZ$", 2, "New Zealand dollar", "뉴질랜드 달러"),
        "AED" to Currency("AED", 2, "UAE dirham", "아랍에미리트 디르함"),
        "SAR" to Currency("SAR", 2, "Saudi riyal", "사우디 리얄"),
        "TRY" to Currency("₺", 2, "Turkish lira", "튀르키예 리라"),
        "BRL" to Currency("R$", 2, "Brazilian real", "브라질 헤알"),
        "MXN" to Currency("MX$", 2, "Mexican peso", "멕시코 페소"),
        "ZAR" to Currency("R", 2, "South African rand", "남아프리카 랜드"),
        "SEK" to Currency("kr", 2, "Swedish krona", "스웨덴 크로나"),
        "NOK" to Currency("kr", 2, "Norwegian krone", "노르웨이 크로네"),
        "DKK" to Currency("kr", 2, "Danish krone", "덴마크 크로네"),
        "PLN" to Currency("zł", 2, "Polish złoty", "폴란드 즈워티"),
        "RUB" to Currency("₽", 2, "Russian rouble", "러시아 루블"),
    )

    val CODES: List<String> = CURRENCIES.keys.toList()
    const val DEFAULT_CURRENCY = "KRW"

    /** Currencies whose symbol reads better after the number. */
    private val SUFFIX = setOf("SEK", "NOK", "DKK", "PLN", "VND")

    /** U+00A0 — the amount and its symbol are one word to a reader. */
    private const val NBSP = " "

    /** U+2212, a real minus, not a hyphen. */
    private const val MINUS = "−"

    private val UNKNOWN = Currency("", 2, "", "")

    fun isKnown(code: String?) = code != null && CURRENCIES.containsKey(code)
    fun of(code: String): Currency = CURRENCIES[code] ?: UNKNOWN
    fun decimals(code: String) = of(code).decimals
    fun minorPerUnit(code: String): Long {
        var n = 1L
        repeat(decimals(code)) { n *= 10 }
        return n
    }

    fun name(code: String, lang: String) = if (lang == "ko") of(code).ko else of(code).en

    // ------------------------------------------------------------
    //  Formatting
    // ------------------------------------------------------------

    // DecimalFormat is not thread-safe and is expensive to build; one per
    // (locale, decimals) is enough, and all use is from the main thread.
    private val formats = HashMap<String, DecimalFormat>()

    private fun formatter(locale: Locale, decimals: Int): DecimalFormat =
        formats.getOrPut(locale.toLanguageTag() + "|" + decimals) {
            DecimalFormat("#,##0", DecimalFormatSymbols(locale)).apply {
                minimumFractionDigits = decimals
                maximumFractionDigits = decimals
                isGroupingUsed = true
                roundingMode = RoundingMode.HALF_UP
            }
        }

    enum class Sign { AUTO, ALWAYS, NEVER }

    /** "₩12,400", "Rp118,200", "$12.40" — identical to formatMoney in money.js. */
    fun format(
        minor: Long,
        code: String,
        locale: Locale = Locale.US,
        sign: Sign = Sign.AUTO,
        symbol: Boolean = true,
    ): String {
        val cur = of(code)
        val absMinor = abs(minor)
        val digits = formatter(locale, cur.decimals)
            .format(BigDecimal(absMinor).movePointLeft(cur.decimals))

        val prefix = when {
            minor < 0 && sign != Sign.NEVER -> MINUS
            minor > 0 && sign == Sign.ALWAYS -> "+"
            else -> ""
        }
        if (!symbol || cur.symbol.isEmpty()) return prefix + digits
        return if (code in SUFFIX) prefix + digits + NBSP + cur.symbol
        else prefix + cur.symbol + digits
    }

    /** 1.2M, 843K — only above 10,000 units, where it actually saves reading. */
    fun formatCompact(minor: Long, code: String, locale: Locale = Locale.US): String {
        val cur = of(code)
        val units = abs(minor).toDouble() / 10.0.pow(cur.decimals)
        if (units < 10_000) return format(minor, code, locale)
        val neg = if (minor < 0) MINUS else ""
        val tiers = listOf(1_000_000_000.0 to "B", 1_000_000.0 to "M", 1_000.0 to "K")
        for ((div, suffix) in tiers) {
            if (units >= div) {
                val v = units / div
                val s = if (v >= 100) v.roundToLong().toString()
                else ((v * 10).roundToLong() / 10.0).toString().removeSuffix(".0")
                return if (code in SUFFIX) neg + s + suffix + NBSP + cur.symbol
                else neg + cur.symbol + s + suffix
            }
        }
        return format(minor, code, locale)
    }

    // ------------------------------------------------------------
    //  Reading an amount someone typed
    // ------------------------------------------------------------

    /** The largest amount Tally will accept, in major units. */
    const val MAX_AMOUNT = 1e13

    /**
     * @return minor units, or null when the field does not hold a usable
     *         amount — an unfinished sum is not the same as zero.
     */
    fun parseToMinor(input: String?, code: String): Long? {
        val value = Calc.eval(input) ?: return null
        val a = abs(value)
        if (a > MAX_AMOUNT) return null
        val minor = BigDecimal(a)
            .movePointRight(decimals(code))
            .setScale(0, RoundingMode.HALF_UP)
        return try {
            minor.longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Minor units back into something the amount field can show and re-parse. */
    fun minorToInput(minor: Long, code: String): String {
        val d = decimals(code)
        val v = BigDecimal(minor).movePointLeft(d).setScale(d, RoundingMode.HALF_UP)
        return v.toPlainString()
    }

    // ------------------------------------------------------------
    //  Exchange
    // ------------------------------------------------------------

    /** `rates` maps a currency to what one of its units is worth in `main`. */
    data class Ctx(val main: String, val rates: Map<String, Double>)

    fun rateOf(code: String, main: String, rates: Map<String, Double>): Double {
        if (code == main) return 1.0
        val r = rates[code] ?: return 0.0
        return if (r.isFinite() && r > 0) r else 0.0
    }

    fun rateMissing(code: String, main: String, rates: Map<String, Double>) =
        rateOf(code, main, rates) == 0.0

    /**
     * A transaction's value in the main currency.
     *
     * The rate frozen on the row is used first — it is what this cost on the
     * day it happened. If the main currency has changed since, settings carry
     * the result the rest of the way.
     */
    fun toMain(tx: TransactionRow, ctx: Ctx): Long =
        toMain(tx.amountMinor, tx.currency, tx.rate, tx.rateBase, ctx)

    fun toMain(minor: Long, currency: String, rate: Double, rateBase: String, ctx: Ctx): Long {
        val major = minor.toDouble() / minorPerUnit(currency)
        val frozen = if (rate.isFinite() && rate > 0) rate else rateOf(currency, rateBase, ctx.rates)
        var value = major * frozen
        if (rateBase != ctx.main) value *= rateOf(rateBase, ctx.main, ctx.rates)
        return (value * minorPerUnit(ctx.main)).roundToLong()
    }

    /** What to freeze on a row written now. */
    fun rateForNew(code: String, ctx: Ctx): Double {
        val r = rateOf(code, ctx.main, ctx.rates)
        return if (r > 0) r else 1.0
    }

    /** Convert between two currencies, through the main one. */
    fun convertMinor(minor: Long, from: String, to: String, ctx: Ctx): Long {
        if (from == to) return minor
        val inMain = toMain(minor, from, rateForNew(from, ctx), ctx.main, ctx)
        val back = rateOf(to, ctx.main, ctx.rates)
        if (back == 0.0) return 0
        val majorMain = inMain.toDouble() / minorPerUnit(ctx.main)
        return ((majorMain / back) * minorPerUnit(to)).roundToLong()
    }

    // ------------------------------------------------------------
    //  Is this transaction storable?
    // ------------------------------------------------------------

    /**
     * Mirrors validateTransaction in money.js.
     * @return null when it can be saved, otherwise a Strings key.
     */
    fun validate(
        amountText: String,
        currency: String,
        kind: String,
        accountId: String?,
        toAccountId: String?,
        categoryId: String?,
        ctx: Ctx,
    ): String? {
        val minor = parseToMinor(amountText, currency)
            ?: return if (amountText.isNotBlank()) "tx.calcBad" else "tx.needAmount"
        if (minor <= 0) return "tx.needAmount"
        if (accountId.isNullOrEmpty()) return "tx.needAccount"
        if (kind == "transfer") {
            if (toAccountId.isNullOrEmpty()) return "tx.needToAccount"
            if (toAccountId == accountId) return "tx.sameAccount"
        } else if (categoryId.isNullOrEmpty()) {
            return "tx.needCategory"
        }
        // Guessing 1:1 would file 50 baht as 50 won and never say so.
        if (currency != ctx.main && rateMissing(currency, ctx.main, ctx.rates)) return "tx.needRate"
        return null
    }
}
