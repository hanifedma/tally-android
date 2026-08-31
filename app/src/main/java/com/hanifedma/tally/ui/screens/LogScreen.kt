package com.hanifedma.tally.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hanifedma.tally.core.Compute
import com.hanifedma.tally.core.Dates
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.Money
import com.hanifedma.tally.core.TransactionRow
import com.hanifedma.tally.ui.Fmt
import com.hanifedma.tally.ui.accountGlyph
import com.hanifedma.tally.ui.components.Divider
import com.hanifedma.tally.ui.components.EmptyState
import com.hanifedma.tally.ui.components.IconChip
import com.hanifedma.tally.ui.theme.LocalTallyColors

/**
 * The ledger itself: days, newest first, each with its own two totals.
 *
 * When a search is running this shows matches from the whole history rather
 * than the month on screen — looking for something you half remember is not a
 * question about August.
 */
@Composable
fun LogScreen(
    ledger: Ledger,
    fmt: Fmt,
    period: Dates.Period,
    searching: Boolean,
    search: String,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onOpen: (TransactionRow) -> Unit,
    onAddFirst: () -> Unit,
) {
    val c = LocalTallyColors.current
    val ctx = ledger.ctx

    val rows = if (searching && search.isNotBlank()) {
        Compute.search(ledger.transactions, search, ledger.categories, ledger.accounts)
    } else {
        Compute.inPeriod(ledger.transactions, period)
    }

    if (rows.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(contentPadding)) {
            if (searching && search.isNotBlank()) {
                EmptyState("🔍", fmt.t("search.open"), fmt.t("search.none", mapOf("q" to search.trim())))
            } else if (ledger.transactions.isEmpty()) {
                EmptyState(
                    "🧾",
                    fmt.t("log.empty.h"),
                    fmt.t("log.empty.p"),
                    fmt.t("log.empty.cta"),
                    onAddFirst,
                )
            } else {
                EmptyState(
                    "🗓",
                    fmt.t("log.emptyMonth.h"),
                    fmt.t(
                        "log.emptyMonth.p",
                        mapOf("start" to fmt.dayShort(period.start), "end" to fmt.dayShort(period.end)),
                    ),
                )
            }
        }
        return
    }

    val days = Compute.groupByDay(rows, ctx)

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (searching && search.isNotBlank()) {
            item("count") {
                Text(
                    fmt.t("search.results", mapOf("n" to rows.size)),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
            }
        }
        for (day in days) {
            item(key = "head-" + day.key) { DayHeader(day, fmt) }
            item(key = "rows-" + day.key) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.surface)
                        .border(1.dp, c.border, RoundedCornerShape(14.dp))
                ) {
                    day.items.forEachIndexed { index, tx ->
                        TransactionRowView(tx, ledger, fmt) { onOpen(tx) }
                        if (index != day.items.lastIndex) Divider()
                    }
                }
            }
        }
        item("tail") { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun DayHeader(day: Compute.Day, fmt: Fmt) {
    val c = LocalTallyColors.current
    val weekday = Dates.weekday(day.key)
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            fmt.dayLong(day.key),
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
        )
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(
            fmt.weekdayShort(day.key),
            style = MaterialTheme.typography.labelMedium,
            // Saturday and Sunday, the way a paper diary marks them.
            color = when (weekday) {
                0 -> c.expense
                6 -> c.transfer
                else -> c.faint
            },
        )
        Spacer(Modifier.weight(1f))
        if (day.income > 0) {
            Text(
                fmt.money(day.income),
                style = MaterialTheme.typography.labelMedium,
                color = c.income,
            )
            Spacer(Modifier.padding(horizontal = 5.dp))
        }
        if (day.expense > 0) {
            Text(
                fmt.money(day.expense),
                style = MaterialTheme.typography.labelMedium,
                color = c.expense,
            )
        }
    }
}

@Composable
private fun TransactionRowView(tx: TransactionRow, ledger: Ledger, fmt: Fmt, onClick: () -> Unit) {
    val c = LocalTallyColors.current
    val category = ledger.category(tx.categoryId)
    val from = ledger.account(tx.accountId)
    val to = ledger.account(tx.toAccountId)

    val title = tx.note.ifBlank {
        when {
            tx.isTransfer -> fmt.t("tx.transfer")
            category != null -> category.name
            else -> fmt.t("log.uncategorised")
        }
    }
    val subtitle = buildString {
        append(fmt.time(tx.occurredMin))
        append(" · ")
        if (tx.isTransfer) {
            append(
                fmt.t(
                    "log.transferTo",
                    mapOf(
                        "from" to (from?.name ?: fmt.t("log.noAccount")),
                        "to" to (to?.name ?: fmt.t("log.noAccount")),
                    ),
                )
            )
        } else {
            if (tx.note.isNotBlank() && category != null) {
                append(category.name)
                append(" · ")
            }
            append(from?.name ?: fmt.t("log.noAccount"))
        }
    }

    val amountColour = when (tx.kind) {
        "income" -> c.income
        "transfer" -> c.transfer
        else -> c.expense
    }
    val prefix = when (tx.kind) {
        "income" -> "+"
        "expense" -> "−"
        else -> ""
    }
    // The converted figure only earns its line when it says something new.
    val converted = if (!tx.isTransfer && tx.currency != ledger.ctx.main) {
        fmt.t("tx.converted", mapOf("amount" to fmt.money(Money.toMain(tx, ledger.ctx))))
    } else null

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        IconChip(
            glyph = if (tx.isTransfer) "⇄" else category?.icon ?: "•",
            tint = category?.let { c.named(it.color) },
            size = 34.dp,
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = c.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                prefix + fmt.money(tx.amountMinor, tx.currency, Money.Sign.NEVER),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = amountColour,
                maxLines = 1,
            )
            if (converted != null) {
                Text(
                    converted,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.faint,
                    maxLines = 1,
                )
            }
        }
    }
}
