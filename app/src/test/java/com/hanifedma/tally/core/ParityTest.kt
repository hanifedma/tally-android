package com.hanifedma.tally.core

import com.hanifedma.tally.i18n.Strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * The two apps have to agree.
 *
 * Every expected value below was produced by running the *web* app's
 * money.js in Node and pasting the answer here. That is the point: these are
 * not tests that Kotlin does what Kotlin does, they are tests that the phone
 * and the browser put the same number on the screen for the same row. If one
 * of them drifts, this fails.
 *
 * The vectors can be regenerated at any time — see tools/run-tests.mjs and
 * the snippet in the web repo's README.
 */
class ParityTest {

    // ------------------------------------------------------------
    //  Ids — the ones that make seeding a new account safe
    // ------------------------------------------------------------

    @Test
    fun `derived ids match the web app exactly`() {
        assertEquals("8f45d563-2e8a-55b4-adfc-61d8ecf861a3", Ids.derived("user-1", "category:food"))
        assertEquals("f338bdb9-9f84-5525-b3a7-0808213fa99a", Ids.derived("user-1", "category:transport"))
        assertEquals("ea39fb10-4893-5eab-9402-b376186da094", Ids.derived("user-2", "category:food"))
        assertEquals("6ac3b9ea-39a9-5f94-867b-302b2510ea82", Ids.derived("abc", "account:cash"))
    }

    @Test
    fun `a derived id is stable and distinct`() {
        assertEquals(Ids.derived("u", "a"), Ids.derived("u", "a"))
        assertNotEquals(Ids.derived("u", "a"), Ids.derived("u", "b"))
        assertNotEquals(Ids.derived("u", "a"), Ids.derived("v", "a"))
        assertTrue(
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
                .matches(Ids.derived("u", "a"))
        )
    }

    @Test
    fun `random ids look like v4 UUIDs and do not repeat`() {
        val seen = HashSet<String>()
        repeat(500) {
            val id = Ids.random()
            assertTrue(id, Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(id))
            assertTrue("collision", seen.add(id))
        }
    }

    // ------------------------------------------------------------
    //  Formatting
    // ------------------------------------------------------------

    @Test
    fun `amounts are formatted exactly as the web formats them`() {
        assertEquals("₩755,297", Money.format(755297, "KRW", Locale.US))
        assertEquals("−₩427,726", Money.format(-427726, "KRW", Locale.US))
        assertEquals("Rp118,200", Money.format(118200, "IDR", Locale.US))
        assertEquals("$12.40", Money.format(1240, "USD", Locale.US))
        assertEquals("120,000 ₫", Money.format(120000, "VND", Locale.US))
        assertEquals("₩0", Money.format(0, "KRW", Locale.US))
    }

    @Test
    fun `a negative uses a real minus sign`() {
        val s = Money.format(-427726, "KRW", Locale.US)
        assertTrue("should not contain an ASCII hyphen", !s.contains("-"))
        assertTrue(s.startsWith("−"))
    }

    @Test
    fun `signs`() {
        assertEquals("+₩500", Money.format(500, "KRW", Locale.US, Money.Sign.ALWAYS))
        assertEquals("−₩500", Money.format(-500, "KRW", Locale.US, Money.Sign.ALWAYS))
        assertEquals("₩500", Money.format(-500, "KRW", Locale.US, Money.Sign.NEVER))
        assertEquals("₩0", Money.format(0, "KRW", Locale.US, Money.Sign.ALWAYS))
    }

    @Test
    fun `compact form matches`() {
        assertEquals("₩8,400", Money.formatCompact(8400, "KRW", Locale.US))
        assertEquals("₩755K", Money.formatCompact(755297, "KRW", Locale.US))
        assertEquals("₩2.3M", Money.formatCompact(2260452, "KRW", Locale.US))
        assertEquals("−₩2.3M", Money.formatCompact(-2260452, "KRW", Locale.US))
    }

    @Test
    fun `an unknown currency still formats`() {
        assertEquals("12.34", Money.format(1234, "XYZ", Locale.US))
    }

    // ------------------------------------------------------------
    //  The amount field
    // ------------------------------------------------------------

    @Test
    fun `arithmetic in the amount field`() {
        assertEquals(15400.0, Calc.eval("12000+3400")!!, 1e-9)
        assertEquals(10.0, Calc.eval("2*3+4")!!, 1e-9)
        assertEquals(14.0, Calc.eval("2+3*4")!!, 1e-9)
        assertEquals(20.0, Calc.eval("(2+3)*4")!!, 1e-9)
        assertEquals(2.5, Calc.eval("10/4")!!, 1e-9)
        assertEquals(50.0, Calc.eval("100 - 20 - 30")!!, 1e-9)
        assertEquals(3.0, Calc.eval("-5+8")!!, 1e-9)
        assertEquals(1200.0, Calc.eval("1,200")!!, 1e-9)
        assertEquals(36.0, Calc.eval("12×3")!!, 1e-9)
        assertEquals(3.0, Calc.eval("12÷4")!!, 1e-9)
    }

    @Test
    fun `an unfinished or malformed sum is null, not a guess`() {
        for (bad in listOf("12+", "(12", "12)", "1.2.3", "10/0", "", "abc", "alert(1)", "1;2")) {
            assertNull(bad, Calc.eval(bad))
        }
        assertNull(Calc.eval(null))
    }

    @Test
    fun `amounts become whole minor units`() {
        assertEquals(12000L, Money.parseToMinor("12000", "KRW"))
        assertEquals(1240L, Money.parseToMinor("12.40", "USD"))
        assertEquals(1235L, Money.parseToMinor("12.345", "USD"))
        assertEquals(118200L, Money.parseToMinor("118200.4", "IDR"))
        assertEquals(500L, Money.parseToMinor("-500", "KRW"))
        assertEquals(15400L, Money.parseToMinor("12000+3400", "KRW"))
        assertNull(Money.parseToMinor("1e20", "KRW"))
        assertNull(Money.parseToMinor("", "KRW"))
    }

    @Test
    fun `minorToInput round-trips`() {
        for ((minor, code) in listOf(755297L to "KRW", 1240L to "USD", 118200L to "IDR", 5L to "USD")) {
            assertEquals(code, minor, Money.parseToMinor(Money.minorToInput(minor, code), code))
        }
    }

    // ------------------------------------------------------------
    //  Dates
    // ------------------------------------------------------------

    @Test
    fun `day arithmetic crosses months and years`() {
        assertEquals("2026-09-01", Dates.addDays("2026-08-31", 1))
        assertEquals("2025-12-31", Dates.addDays("2026-01-01", -1))
        assertEquals("2024-02-29", Dates.addDays("2024-02-28", 1))
        assertEquals("2026-03-01", Dates.addDays("2026-02-28", 1))
    }

    @Test
    fun `adding months clamps to a shorter month`() {
        assertEquals("2026-02-28", Dates.addMonths("2026-01-31", 1))
        assertEquals("2026-02-28", Dates.addMonths("2026-03-31", -1))
        assertEquals("2027-01-15", Dates.addMonths("2026-12-15", 1))
        assertEquals("2025-12-15", Dates.addMonths("2026-01-15", -1))
    }

    @Test
    fun `weekday numbering matches getUTCDay`() {
        assertEquals(0, Dates.weekday("2026-08-30")) // a Sunday
        assertEquals(0, Dates.weekday("2024-01-07"))
        assertEquals(1, Dates.weekday("2026-08-31"))
        assertEquals(6, Dates.weekday("2026-08-29"))
    }

    @Test
    fun `a calendar month is the default period`() {
        assertEquals(Dates.Period("2026-08-01", "2026-08-31"), Dates.periodOf("2026-08-15", 1))
        assertEquals(Dates.Period("2026-02-01", "2026-02-28"), Dates.periodOf("2026-02-05", 1))
        assertEquals(Dates.Period("2026-12-01", "2026-12-31"), Dates.periodOf("2026-12-31", 1))
    }

    @Test
    fun `a month can start on payday instead`() {
        assertEquals(Dates.Period("2026-07-25", "2026-08-24"), Dates.periodOf("2026-08-24", 25))
        assertEquals(Dates.Period("2026-08-25", "2026-09-24"), Dates.periodOf("2026-08-25", 25))
        assertEquals(Dates.Period("2025-12-25", "2026-01-24"), Dates.periodOf("2026-01-03", 25))
    }

    @Test
    fun `consecutive periods touch with no gap and no overlap`() {
        for (startDay in listOf(1, 15, 25, 28)) {
            var p = Dates.periodOf("2026-01-10", startDay)
            repeat(24) {
                val next = Dates.shift(p, 1, startDay)
                assertEquals("day $startDay", Dates.addDays(p.end, 1), next.start)
                p = next
            }
        }
    }

    @Test
    fun `periods step forward and back without drifting`() {
        var p = Dates.periodOf("2026-08-15", 25)
        repeat(14) { p = Dates.shift(p, 1, 25) }
        repeat(14) { p = Dates.shift(p, -1, 25) }
        assertEquals(Dates.periodOf("2026-08-15", 25), p)
    }

    @Test
    fun `a day key is validated, not trusted`() {
        assertTrue(Dates.isDayKey("2026-08-30"))
        assertTrue(!Dates.isDayKey("2026-8-30"))
        assertTrue(!Dates.isDayKey("2026-13-01"))
        assertTrue(!Dates.isDayKey("nope"))
        assertTrue(!Dates.isDayKey(null))
    }

    // ------------------------------------------------------------
    //  Exchange
    // ------------------------------------------------------------

    private val ctx = Money.Ctx("KRW", mapOf("IDR" to 0.0875, "USD" to 1380.0))

    private fun tx(
        amount: Long,
        currency: String = "KRW",
        rate: Double = 1.0,
        rateBase: String = "KRW",
        kind: String = "expense",
        categoryId: String? = null,
        accountId: String? = null,
        toAccountId: String? = null,
        toAmount: Long? = null,
        occurredOn: String = "2026-08-30",
        occurredMin: Int = 600,
        note: String = "",
        id: String = Ids.random(),
    ) = TransactionRow(
        id = id, kind = kind, amountMinor = amount, currency = currency, rate = rate,
        rateBase = rateBase, categoryId = categoryId, accountId = accountId,
        toAccountId = toAccountId, toAmountMinor = toAmount, note = note,
        occurredOn = occurredOn, occurredMin = occurredMin,
    ).normalized()

    @Test
    fun `conversion matches the web to the unit`() {
        assertEquals(12400L, Money.toMain(tx(12400, "KRW"), ctx))
        assertEquals(10343L, Money.toMain(tx(118200, "IDR", 0.0875), ctx))
        assertEquals(17112L, Money.toMain(tx(1240, "USD", 1380.0), ctx))
        assertEquals(6341L, Money.convertMinor(1000000, "IDR", "USD", ctx))
        assertEquals(500L, Money.convertMinor(500, "KRW", "KRW", ctx))
    }

    @Test
    fun `an old row keeps the price it was recorded at`() {
        val old = tx(100000, "IDR", 0.09)
        val now = Money.Ctx("KRW", mapOf("IDR" to 0.07))
        assertEquals(9000L, Money.toMain(old, now))
    }

    @Test
    fun `changing the main currency re-expresses old rows through settings`() {
        val row = tx(100000, "IDR", 0.0875)
        val usd = Money.Ctx("USD", mapOf("KRW" to 1.0 / 1380, "IDR" to 0.0875 / 1380))
        // 8,750 KRW at 1,380 to the dollar is $6.34, give or take a cent of
        // rounding in either implementation.
        val cents = Money.toMain(row, usd)
        assertTrue("expected about 634, got " + cents, cents in 633L..635L)
    }

    @Test
    fun `a currency with no rate cannot be saved in the first place`() {
        assertTrue(Money.rateMissing("THB", "KRW", ctx.rates))
        assertEquals(
            "tx.needRate",
            Money.validate("5000", "THB", "expense", "a1", null, "c1", ctx),
        )
        assertNull(Money.validate("5000", "IDR", "expense", "a1", null, "c1", ctx))
        assertNull(Money.validate("5000", "KRW", "expense", "a1", null, "c1", ctx))
    }

    @Test
    fun `what else stops a transaction being saved`() {
        assertNull(Money.validate("1000", "KRW", "expense", "a1", null, "c1", ctx))
        assertEquals("tx.needAmount", Money.validate("", "KRW", "expense", "a1", null, "c1", ctx))
        assertEquals("tx.needAmount", Money.validate("0", "KRW", "expense", "a1", null, "c1", ctx))
        assertEquals("tx.calcBad", Money.validate("12+", "KRW", "expense", "a1", null, "c1", ctx))
        assertEquals("tx.needAccount", Money.validate("1000", "KRW", "expense", null, null, "c1", ctx))
        assertEquals("tx.needCategory", Money.validate("1000", "KRW", "expense", "a1", null, null, ctx))
        assertEquals("tx.needToAccount", Money.validate("1000", "KRW", "transfer", "a1", null, null, ctx))
        assertEquals("tx.sameAccount", Money.validate("1000", "KRW", "transfer", "a1", "a1", null, ctx))
        assertNull(Money.validate("1000", "KRW", "transfer", "a1", "a2", null, ctx))
    }

    // ------------------------------------------------------------
    //  Balances and totals
    // ------------------------------------------------------------

    private val accounts = listOf(
        AccountRow(id = "a1", name = "Cash", currency = "KRW", openingMinor = 100000, position = 0),
        AccountRow(id = "a2", name = "Gopay", currency = "IDR", openingMinor = 50000, position = 1),
        AccountRow(id = "a3", name = "Card", kind = "card", currency = "KRW", openingMinor = -20000, position = 2),
    )

    @Test
    fun `a balance is opening, plus what came in, minus what went out`() {
        val rows = listOf(
            tx(12000, accountId = "a1"),
            tx(500000, accountId = "a1", kind = "income"),
            tx(118200, "IDR", 0.0875, accountId = "a2"),
        )
        val b = Compute.balances(accounts, rows)
        assertEquals(588000L, b["a1"])
        assertEquals(-68200L, b["a2"])
        assertEquals(-20000L, b["a3"])
    }

    @Test
    fun `a cross-currency transfer lands the amount that actually arrived`() {
        val rows = listOf(
            tx(100000, kind = "transfer", accountId = "a1", toAccountId = "a2", toAmount = 1142857)
        )
        val b = Compute.balances(accounts, rows)
        assertEquals(0L, b["a1"])
        assertEquals(50000L + 1142857L, b["a2"])
    }

    @Test
    fun `a transfer is neither income nor spending`() {
        val rows = listOf(
            tx(1832726, kind = "income", accountId = "a1"),
            tx(260452, accountId = "a1"),
            tx(5444798, kind = "transfer", accountId = "a1", toAccountId = "a3"),
        )
        val totals = Compute.totals(rows, ctx)
        assertEquals(1832726L, totals.income)
        assertEquals(260452L, totals.expense)
        assertEquals(1832726L - 260452L, totals.net)
    }

    @Test
    fun `net worth converts every account into one currency`() {
        assertEquals(100000L + 4375L - 20000L, Compute.netWorth(accounts, emptyList(), ctx))
    }

    @Test
    fun `an archived account is not part of net worth`() {
        val withArchived = accounts + AccountRow(
            id = "a4", name = "Old", currency = "KRW", openingMinor = 999999, archived = true,
        )
        assertEquals(
            Compute.netWorth(accounts, emptyList(), ctx),
            Compute.netWorth(withArchived, emptyList(), ctx),
        )
    }

    // ------------------------------------------------------------
    //  Breakdowns
    // ------------------------------------------------------------

    @Test
    fun `spending splits by category, largest first`() {
        val rows = listOf(
            tx(30000, categoryId = "c1"),
            tx(10000, categoryId = "c2"),
            tx(60000, categoryId = "c3"),
            tx(999999, kind = "income", categoryId = "c9"),
        )
        val breakdown = Compute.byCategory(rows, "expense", ctx)
        assertEquals(100000L, breakdown.total)
        assertEquals(listOf("c3", "c1", "c2"), breakdown.rows.map { it.categoryId })
        assertEquals(1.0, breakdown.rows.sumOf { it.share }, 1e-9)
    }

    @Test
    fun `money with no category is still counted`() {
        val rows = listOf(tx(5000, categoryId = null), tx(5000, categoryId = "c1"))
        val breakdown = Compute.byCategory(rows, "expense", ctx)
        assertEquals(10000L, breakdown.total)
        assertEquals(2, breakdown.rows.size)
        assertTrue(breakdown.rows.any { it.categoryId == null })
    }

    @Test
    fun `budgets report how far over, not just that they are over`() {
        val categories = listOf(CategoryRow(id = "c1", name = "Food", kind = "expense"))
        val budgets = listOf(
            BudgetRow(id = "b0", categoryId = null, amountMinor = 300000, currency = "KRW"),
            BudgetRow(id = "b1", categoryId = "c1", amountMinor = 200000, currency = "KRW"),
        )
        val rows = listOf(tx(500000, categoryId = "c1"), tx(55297, categoryId = "c2"))
        val progress = Compute.budgetProgress(budgets, rows, categories, ctx)
        assertNull(progress[0].categoryId)
        assertEquals(555297L, progress[0].spent)
        assertEquals(300000L, progress[0].limit)
        assertEquals(-255297L, progress[0].remaining)
        assertEquals(1.85099, progress[0].ratio, 0.0001)
        assertEquals(500000L, progress.first { it.categoryId == "c1" }.spent)
    }

    @Test
    fun `a budget set in another currency is converted`() {
        val budgets = listOf(BudgetRow(id = "b", amountMinor = 100, currency = "USD"))
        assertEquals(1380L, Compute.budgetProgress(budgets, emptyList(), emptyList(), ctx)[0].limit)
    }

    // ------------------------------------------------------------
    //  Ordering, notes, search
    // ------------------------------------------------------------

    @Test
    fun `the log runs newest first, within the day as well as across days`() {
        val rows = listOf(
            tx(1, id = "x", occurredOn = "2026-08-29", occurredMin = 900),
            tx(1, id = "y", occurredOn = "2026-08-30", occurredMin = 480),
            tx(1, id = "z", occurredOn = "2026-08-30", occurredMin = 1200),
        )
        val days = Compute.groupByDay(rows, ctx)
        assertEquals(listOf("2026-08-30", "2026-08-29"), days.map { it.key })
        assertEquals(listOf("z", "y"), days[0].items.map { it.id })
    }

    @Test
    fun `note suggestions match anywhere in the note, and only once each`() {
        val rows = listOf(
            tx(1, id = "1", note = "Coupang many things", occurredOn = "2026-08-30"),
            tx(1, id = "2", note = "Jinlo, food and fruit", occurredOn = "2026-08-29"),
            tx(1, id = "3", note = "Coupang many things", occurredOn = "2026-08-28"),
        )
        assertEquals(1, Compute.noteSuggestions(rows, "coup").size)
        assertEquals("Coupang many things", Compute.noteSuggestions(rows, "coup")[0].note)
        assertEquals(1, Compute.noteSuggestions(rows, "food").size)
        assertEquals(2, Compute.noteSuggestions(rows, "").size)
    }

    @Test
    fun `search reaches notes, categories and accounts`() {
        val categories = listOf(CategoryRow(id = "c1", name = "Food", kind = "expense"))
        val rows = listOf(
            tx(1, id = "1", note = "Bebek, gofood", accountId = "a2", categoryId = "c1"),
            tx(1, id = "2", note = "Haircut", accountId = "a1"),
        )
        assertEquals(1, Compute.search(rows, "gofood", categories, accounts).size)
        assertEquals(listOf("2"), Compute.search(rows, "cash", categories, accounts).map { it.id })
        assertEquals(listOf("1"), Compute.search(rows, "food", categories, accounts).map { it.id })
        assertEquals(2, Compute.search(rows, "", categories, accounts).size)
    }

    // ------------------------------------------------------------
    //  Rows arriving damaged
    // ------------------------------------------------------------

    @Test
    fun `nonsense values are clamped rather than believed`() {
        val t = TransactionRow(
            id = "x", kind = "sideways", amountMinor = -500, currency = "NOPE",
            rate = -3.0, occurredMin = 99999, note = "n".repeat(400),
        ).normalized()
        assertEquals("expense", t.kind)
        assertEquals(0L, t.amountMinor)
        assertEquals("KRW", t.currency)
        assertEquals(1.0, t.rate, 1e-9)
        assertEquals(1439, t.occurredMin)
        assertEquals(280, t.note.length)
    }

    @Test
    fun `a transfer cannot smuggle in a category, and an expense cannot smuggle a destination`() {
        val move = TransactionRow(id = "1", kind = "transfer", categoryId = "c1", toAccountId = "a2").normalized()
        assertNull(move.categoryId)
        assertEquals("a2", move.toAccountId)
        val spend = TransactionRow(id = "2", kind = "expense", toAccountId = "a2", toAmountMinor = 5).normalized()
        assertNull(spend.toAccountId)
        assertNull(spend.toAmountMinor)
    }

    @Test
    fun `settings keep only rates that are real numbers for real currencies`() {
        val raw = kotlinx.serialization.json.buildJsonObject {
            put("KRW", kotlinx.serialization.json.JsonPrimitive("11.4"))
            put("NOPE", kotlinx.serialization.json.JsonPrimitive(2))
            put("USD", kotlinx.serialization.json.JsonPrimitive(0))
            put("EUR", kotlinx.serialization.json.JsonPrimitive("abc"))
            put("JPY", kotlinx.serialization.json.JsonPrimitive(-1))
        }
        val s = SettingsRow(
            mainCurrency = "IDR", theme = "sideways", lang = "fr",
            monthStart = 99, weekStart = -4, rates = raw,
        ).normalized()
        assertEquals("IDR", s.mainCurrency)
        assertEquals("dark", s.theme)
        assertEquals("en", s.lang)
        assertEquals(28, s.monthStart)
        assertEquals(0, s.weekStart)
        assertEquals(mapOf("KRW" to 11.4), s.rateMap)
    }

    // ------------------------------------------------------------
    //  Export
    // ------------------------------------------------------------

    @Test
    fun `the CSV escapes anything that would break a column`() {
        val categories = listOf(CategoryRow(id = "c1", name = "Food, \"real\"", kind = "expense"))
        val rows = listOf(
            tx(118200, "IDR", 0.0875, id = "1", accountId = "a2", categoryId = "c1",
                note = "He said \"hi\", then left", occurredMin = 725)
        )
        val csv = Compute.toCsv(rows, accounts, categories, ctx)
        val lines = csv.split("\r\n")
        assertEquals('﻿', csv[0])
        assertTrue(lines[0].contains("krw_value"))
        assertTrue(lines[1].contains("\"He said \"\"hi\"\", then left\""))
        assertTrue(lines[1].contains("\"Food, \"\"real\"\"\""))
        assertTrue(lines[1].startsWith("2026-08-30,12:05,expense,"))
        assertTrue(lines[1].endsWith(",10343"))
    }

    // ------------------------------------------------------------
    //  Translations
    // ------------------------------------------------------------

    @Test
    fun `every string exists in both languages`() {
        val en = Strings.keys("en")
        val ko = Strings.keys("ko")
        assertEquals("keys missing from Korean: " + (en - ko), en, ko)
        assertTrue("the table looks empty", en.size > 200)
    }

    @Test
    fun `placeholders survive translation`() {
        // A {name} that exists in one language and not the other is a blank
        // where a number should be, and nothing else notices.
        val placeholder = Regex("""\{(\w+)}""")
        for (key in Strings.keys("en")) {
            val enVars = placeholder.findAll(Strings.get("en", key)).map { it.value }.toSet()
            val koVars = placeholder.findAll(Strings.get("ko", key)).map { it.value }.toSet()
            assertEquals("placeholders differ for $key", enVars, koVars)
        }
    }

    @Test
    fun `a missing key returns the key rather than nothing`() {
        assertEquals("no.such.key", Strings.get("en", "no.such.key"))
    }

    @Test
    fun `the seed lists have no duplicate slugs and cover both sides`() {
        assertEquals(SEED_CATEGORIES.size, SEED_CATEGORIES.map { it.slug }.toSet().size)
        assertTrue(SEED_CATEGORIES.any { it.kind == "income" })
        assertTrue(SEED_CATEGORIES.any { it.kind == "expense" })
        for (s in SEED_CATEGORIES) {
            assertTrue(s.slug, s.en.isNotBlank() && s.ko.isNotBlank())
            assertTrue(s.slug, s.color in COLORS)
        }
        for (s in SEED_ACCOUNTS) {
            assertTrue(s.slug, s.en.isNotBlank() && s.ko.isNotBlank())
        }
    }
}
