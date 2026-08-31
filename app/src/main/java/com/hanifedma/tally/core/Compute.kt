package com.hanifedma.tally.core

import kotlin.math.roundToLong

/**
 * What the screens actually show — the Kotlin half of the computation
 * section of money.js. Pure functions over rows, so the numbers on a phone
 * and the numbers on a laptop come out of the same reasoning.
 */
object Compute {

    data class Totals(val income: Long, val expense: Long) {
        val net: Long get() = income - expense
    }

    data class Slice(val categoryId: String?, val amount: Long, val share: Double)

    data class Breakdown(val rows: List<Slice>, val total: Long)

    data class Day(
        val key: String,
        val items: List<TransactionRow>,
        val income: Long,
        val expense: Long,
    )

    data class BudgetProgress(
        val id: String,
        val categoryId: String?,
        val category: CategoryRow?,
        val limit: Long,
        val spent: Long,
    ) {
        val ratio: Double get() = if (limit > 0) spent.toDouble() / limit else 0.0
        val remaining: Long get() = limit - spent
        val isOver: Boolean get() = spent > limit
    }

    data class MonthPoint(val period: Dates.Period, val income: Long, val expense: Long)

    /** Newest first: by day, then time of day, then when it was written. */
    val newestFirst: Comparator<TransactionRow> = compareByDescending<TransactionRow> { it.occurredOn }
        .thenByDescending { it.occurredMin }
        .thenByDescending { it.createdAt ?: "" }
        .thenByDescending { it.id }

    fun inPeriod(rows: List<TransactionRow>, period: Dates.Period): List<TransactionRow> =
        rows.filter { it.occurredOn in period }

    /**
     * Every account's balance, in its own currency.
     *
     * No conversion happens here: a transaction is denominated in its
     * account's currency, so a balance in won stays a number of won. The one
     * exception is a transfer between accounts of different currencies, which
     * carries what actually landed.
     */
    fun balances(accounts: List<AccountRow>, transactions: List<TransactionRow>): Map<String, Long> {
        val out = HashMap<String, Long>(accounts.size)
        for (a in accounts) out[a.id] = a.openingMinor
        for (t in transactions) {
            when (t.kind) {
                "income" -> t.accountId?.let { id -> out[id]?.let { out[id] = it + t.amountMinor } }
                "expense" -> t.accountId?.let { id -> out[id]?.let { out[id] = it - t.amountMinor } }
                else -> {
                    t.accountId?.let { id -> out[id]?.let { out[id] = it - t.amountMinor } }
                    t.toAccountId?.let { id ->
                        out[id]?.let { out[id] = it + (t.toAmountMinor ?: t.amountMinor) }
                    }
                }
            }
        }
        return out
    }

    /**
     * Everything added up, in the main currency.
     *
     * Transfers appear in neither figure. Moving your own money between your
     * own accounts is not income and not spending, and counting it as both is
     * exactly why the reference app's monthly totals never matched the bank.
     */
    fun totals(rows: List<TransactionRow>, ctx: Money.Ctx): Totals {
        var income = 0L
        var expense = 0L
        for (t in rows) {
            when (t.kind) {
                "income" -> income += Money.toMain(t, ctx)
                "expense" -> expense += Money.toMain(t, ctx)
            }
        }
        return Totals(income, expense)
    }

    /** Every live account converted into one currency. */
    fun netWorth(
        accounts: List<AccountRow>,
        transactions: List<TransactionRow>,
        ctx: Money.Ctx,
    ): Long {
        val bal = balances(accounts, transactions)
        var total = 0L
        for (a in accounts) {
            if (a.archived) continue
            total += Money.toMain(bal[a.id] ?: 0L, a.currency, Money.rateForNew(a.currency, ctx), ctx.main, ctx)
        }
        return total
    }

    /**
     * Split by category, largest first. Money with no category collects under
     * a null id rather than being dropped — it is still money you spent.
     */
    fun byCategory(rows: List<TransactionRow>, kind: String, ctx: Money.Ctx): Breakdown {
        val sums = LinkedHashMap<String?, Long>()
        var total = 0L
        for (t in rows) {
            if (t.kind != kind) continue
            val v = Money.toMain(t, ctx)
            sums[t.categoryId] = (sums[t.categoryId] ?: 0L) + v
            total += v
        }
        val slices = sums.map { (id, amount) ->
            Slice(id, amount, if (total > 0) amount.toDouble() / total else 0.0)
        }.sortedByDescending { it.amount }
        return Breakdown(slices, total)
    }

    /** Totals per period, oldest first — the trend chart's data. */
    fun byPeriod(
        transactions: List<TransactionRow>,
        anchor: String,
        count: Int,
        monthStart: Int,
        ctx: Money.Ctx,
    ): List<MonthPoint> = (count - 1 downTo 0).map { back ->
        val period = Dates.periodOf(Dates.addMonths(anchor, -back.toLong()), monthStart)
        val t = totals(inPeriod(transactions, period), ctx)
        MonthPoint(period, t.income, t.expense)
    }

    /**
     * Budget progress for one period. Over 100% is reported as it is rather
     * than clamped: how far over matters more than the fact of it.
     */
    fun budgetProgress(
        budgets: List<BudgetRow>,
        rows: List<TransactionRow>,
        categories: List<CategoryRow>,
        ctx: Money.Ctx,
    ): List<BudgetProgress> {
        val spentByCat = HashMap<String?, Long>()
        var spentTotal = 0L
        for (t in rows) {
            if (t.kind != "expense") continue
            val v = Money.toMain(t, ctx)
            spentTotal += v
            spentByCat[t.categoryId] = (spentByCat[t.categoryId] ?: 0L) + v
        }
        val byId = categories.associateBy { it.id }
        return budgets.map { b ->
            BudgetProgress(
                id = b.id,
                categoryId = b.categoryId,
                category = b.categoryId?.let { byId[it] },
                limit = Money.toMain(b.amountMinor, b.currency, Money.rateForNew(b.currency, ctx), ctx.main, ctx),
                spent = if (b.categoryId != null) spentByCat[b.categoryId] ?: 0L else spentTotal,
            )
        }.sortedWith(
            // The overall budget first, then the tightest categories.
            compareBy<BudgetProgress> { it.categoryId != null }.thenByDescending { it.ratio }
        )
    }

    /** Group into days, newest day first, newest row first within a day. */
    fun groupByDay(rows: List<TransactionRow>, ctx: Money.Ctx): List<Day> =
        rows.groupBy { it.occurredOn }
            .map { (key, items) ->
                var income = 0L
                var expense = 0L
                for (t in items) {
                    when (t.kind) {
                        "income" -> income += Money.toMain(t, ctx)
                        "expense" -> expense += Money.toMain(t, ctx)
                    }
                }
                Day(key, items.sortedWith(newestFirst), income, expense)
            }
            .sortedByDescending { it.key }

    data class Suggestion(
        val note: String,
        val categoryId: String?,
        val accountId: String?,
        val kind: String,
    )

    /**
     * Notes seen before, most recent first.
     *
     * Matching is a case-insensitive substring on purpose: typing "coup"
     * should find "Coupang, many things", which a prefix match never would.
     */
    fun noteSuggestions(
        transactions: List<TransactionRow>,
        query: String,
        limit: Int = 6,
    ): List<Suggestion> {
        val q = query.trim().lowercase()
        val seen = HashSet<String>()
        val out = ArrayList<Suggestion>()
        for (t in transactions.sortedWith(newestFirst)) {
            val note = t.note.trim()
            if (note.isEmpty()) continue
            val key = note.lowercase()
            if (!seen.add(key)) continue
            if (q.isNotEmpty() && !key.contains(q)) continue
            out.add(Suggestion(note, t.categoryId, t.accountId, t.kind))
            if (out.size >= limit) break
        }
        return out
    }

    /** Free text across notes, category names and account names. */
    fun search(
        transactions: List<TransactionRow>,
        query: String,
        categories: List<CategoryRow>,
        accounts: List<AccountRow>,
    ): List<TransactionRow> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return transactions
        val cats = categories.associateBy { it.id }
        val accs = accounts.associateBy { it.id }
        return transactions.filter { t ->
            t.note.lowercase().contains(q) ||
                cats[t.categoryId]?.name?.lowercase()?.contains(q) == true ||
                accs[t.accountId]?.name?.lowercase()?.contains(q) == true ||
                accs[t.toAccountId]?.name?.lowercase()?.contains(q) == true
        }
    }

    /**
     * A spreadsheet of everything, one row per transaction — byte for byte
     * the same shape toCsv produces on the web, so an export from either
     * device opens the same way.
     */
    fun toCsv(
        transactions: List<TransactionRow>,
        accounts: List<AccountRow>,
        categories: List<CategoryRow>,
        ctx: Money.Ctx,
    ): String {
        val accs = accounts.associateBy { it.id }
        val cats = categories.associateBy { it.id }
        val rows = ArrayList<List<String>>()
        rows.add(
            listOf(
                "date", "time", "type", "category", "account", "to_account",
                "note", "currency", "amount", "rate", ctx.main.lowercase() + "_value",
            )
        )
        for (t in transactions.sortedWith(newestFirst)) {
            val hh = (t.occurredMin / 60).toString().padStart(2, '0')
            val mm = (t.occurredMin % 60).toString().padStart(2, '0')
            rows.add(
                listOf(
                    t.occurredOn,
                    "$hh:$mm",
                    t.kind,
                    cats[t.categoryId]?.name ?: "",
                    accs[t.accountId]?.name ?: "",
                    accs[t.toAccountId]?.name ?: "",
                    t.note,
                    t.currency,
                    Money.minorToInput(t.amountMinor, t.currency),
                    trimRate(t.rate),
                    if (t.isTransfer) "" else Money.minorToInput(Money.toMain(t, ctx), ctx.main),
                )
            )
        }
        val body = rows.joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } }
        // A byte order mark, so Excel opens Korean notes as UTF-8 rather than
        // mojibake. Every other reader ignores it.
        return "﻿$body"
    }

    private fun trimRate(rate: Double): String {
        if (rate == rate.roundToLong().toDouble()) return rate.roundToLong().toString()
        return rate.toString()
    }

    private fun escape(value: String): String =
        if (value.any { it == '"' || it == ',' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
