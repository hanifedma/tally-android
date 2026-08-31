package com.hanifedma.tally.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hanifedma.tally.core.Compute
import com.hanifedma.tally.core.Dates
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.Money
import com.hanifedma.tally.ui.Fmt
import com.hanifedma.tally.ui.components.Card
import com.hanifedma.tally.ui.components.CardHeader
import com.hanifedma.tally.ui.components.Divider
import com.hanifedma.tally.ui.components.Help
import com.hanifedma.tally.ui.components.ProgressBar
import com.hanifedma.tally.ui.components.Segmented
import com.hanifedma.tally.ui.theme.LocalTallyColors

/**
 * Where the money went — budgets first, because a limit you are about to
 * cross is more useful than a chart of what you already spent.
 */
@Composable
fun InsightsScreen(
    ledger: Ledger,
    fmt: Fmt,
    period: Dates.Period,
    showIncome: Boolean,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onShowIncome: (Boolean) -> Unit,
    onEditBudgets: () -> Unit,
    onPickPeriod: (String) -> Unit,
) {
    val rows = Compute.inPeriod(ledger.transactions, period)

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("budget") { BudgetCard(ledger, fmt, period, rows, onEditBudgets) }
        item("breakdown") { BreakdownCard(ledger, fmt, rows, showIncome, onShowIncome) }
        if (rows.isNotEmpty()) {
            item("stats") { StatsCard(ledger, fmt, period, rows) }
        }
        item("trend") { TrendCard(ledger, fmt, period, onPickPeriod) }
        item("tail") { Spacer(Modifier.height(4.dp)) }
    }
}

// ------------------------------------------------------------
//  Budgets
// ------------------------------------------------------------

@Composable
private fun BudgetCard(
    ledger: Ledger,
    fmt: Fmt,
    period: Dates.Period,
    rows: List<com.hanifedma.tally.core.TransactionRow>,
    onEdit: () -> Unit,
) {
    val c = LocalTallyColors.current
    val progress = Compute.budgetProgress(ledger.budgets, rows, ledger.categories, ledger.ctx)

    Card {
        CardHeader(
            fmt.t("ins.budget"),
            if (progress.isEmpty()) fmt.t("ins.budgetSet") else fmt.t("ins.budgetEdit"),
            onEdit,
        )
        if (progress.isEmpty()) {
            Help(fmt.t("ins.budgetNone.p"))
            return@Card
        }
        val pace = Dates.paceThrough(period).toFloat()
        progress.forEachIndexed { index, b ->
            if (index > 0) {
                Spacer(Modifier.height(12.dp))
                Divider()
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                b.category?.let {
                    Text(it.icon, modifier = Modifier.padding(end = 7.dp))
                }
                Text(
                    b.category?.name ?: fmt.t("ins.budgetTotal"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    fmt.t(
                        "ins.spentOf",
                        mapOf("spent" to fmt.money(b.spent), "limit" to fmt.money(b.limit)),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.muted,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(7.dp))
            ProgressBar(
                ratio = b.ratio.toFloat(),
                color = when {
                    b.isOver -> c.expense
                    b.ratio > pace + 0.1 -> c.warn
                    else -> c.accent
                },
                pace = pace,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Help(fmt.percent(b.ratio))
                Text(
                    if (b.isOver) {
                        fmt.t("ins.budgetOver", mapOf("amount" to fmt.money(-b.remaining)))
                    } else {
                        fmt.t("ins.budgetLeft", mapOf("amount" to fmt.money(b.remaining)))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (b.isOver) c.expense else c.income,
                )
            }
        }
    }
}

// ------------------------------------------------------------
//  The donut
// ------------------------------------------------------------

@Composable
private fun BreakdownCard(
    ledger: Ledger,
    fmt: Fmt,
    rows: List<com.hanifedma.tally.core.TransactionRow>,
    showIncome: Boolean,
    onShowIncome: (Boolean) -> Unit,
) {
    val c = LocalTallyColors.current
    val kind = if (showIncome) "income" else "expense"
    val breakdown = Compute.byCategory(rows, kind, ledger.ctx)

    Card {
        Text(
            if (showIncome) fmt.t("ins.breakdownIncome") else fmt.t("ins.breakdown"),
            style = MaterialTheme.typography.titleMedium,
            color = c.text,
        )
        Spacer(Modifier.height(10.dp))
        Segmented(
            options = listOf(fmt.t("ins.showExpense"), fmt.t("ins.showIncome")),
            selected = if (showIncome) 1 else 0,
            tints = listOf(c.expense, c.income),
        ) { onShowIncome(it == 1) }
        Spacer(Modifier.height(16.dp))

        if (breakdown.rows.isEmpty()) {
            Help(fmt.t("ins.empty"), Modifier.fillMaxWidth().padding(vertical = 18.dp))
            return@Card
        }

        // Ten slices is as many as a 150dp ring can distinguish; the rest
        // become one grey "everything else" rather than a fringe of slivers.
        val top = breakdown.rows.take(9)
        val rest = breakdown.rows.drop(9)
        val drawn = if (rest.isEmpty()) top else {
            top + Compute.Slice(null, rest.sumOf { it.amount }, rest.sumOf { it.share })
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.Center) {
                Donut(
                    slices = drawn.map { slice ->
                        (ledger.category(slice.categoryId)?.let { c.named(it.color) } ?: c.named("gray")) to
                            slice.share.toFloat()
                    },
                    track = c.track,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        fmt.compact(breakdown.total),
                        style = MaterialTheme.typography.titleLarge,
                        color = c.text,
                    )
                    Text(
                        if (showIncome) fmt.t("sum.income") else fmt.t("sum.expenses"),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.faint,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        for (slice in breakdown.rows.take(12)) {
            val category = ledger.category(slice.categoryId)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(category?.let { c.named(it.color) } ?: c.named("gray"))
                )
                Text(
                    (category?.let { it.icon + "  " + it.name }) ?: fmt.t("log.uncategorised"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    fmt.percent(slice.share),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.faint,
                )
                Text(
                    fmt.money(slice.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = c.text,
                )
            }
        }
    }
}

/**
 * A ring of proportional arcs.
 *
 * Each slice is inset by a degree or so on each side, so that two similar
 * colours still read as two slices rather than one long one.
 */
@Composable
private fun Donut(slices: List<Pair<Color, Float>>, track: Color) {
    Canvas(Modifier.size(156.dp)) {
        val stroke = size.minDimension * 0.115f
        val inset = stroke / 2
        val topLeft = Offset(inset, inset)
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
        var start = -90f
        for ((colour, share) in slices) {
            val sweep = share * 360f
            if (sweep <= 0f) continue
            val gap = if (slices.size > 1) 1.2f else 0f
            drawArc(
                color = colour,
                startAngle = start + gap / 2,
                sweepAngle = (sweep - gap).coerceAtLeast(0.6f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            start += sweep
        }
    }
}

// ------------------------------------------------------------
//  The shape of the month
// ------------------------------------------------------------

@Composable
private fun StatsCard(
    ledger: Ledger,
    fmt: Fmt,
    period: Dates.Period,
    rows: List<com.hanifedma.tally.core.TransactionRow>,
) {
    val totals = Compute.totals(rows, ledger.ctx)
    val days = period.days
    val elapsed = Dates.elapsedDays(period)
    val perDay = if (elapsed > 0) totals.expense / elapsed else 0L
    val projected = if (elapsed > 0) perDay * days else totals.expense

    val biggest = rows.filter { it.kind == "expense" }
        .maxByOrNull { Money.toMain(it, ledger.ctx) }

    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat(fmt.t("ins.avgDay"), fmt.money(perDay), Modifier.weight(1f))
            Stat(fmt.t("ins.avgProjected"), fmt.money(projected), Modifier.weight(1f))
        }
        if (biggest != null) {
            Spacer(Modifier.height(10.dp))
            Stat(
                fmt.t("ins.biggest"),
                fmt.money(Money.toMain(biggest, ledger.ctx)),
                Modifier.fillMaxWidth(),
                note = biggest.note.ifBlank { ledger.category(biggest.categoryId)?.name ?: "" },
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    val c = LocalTallyColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            // Locale.ROOT rather than the user's: uppercasing is
            // locale-sensitive (Turkish dotless i is the classic trap) and
            // these are labels, not content. Korean has no case, so this is
            // correctly a no-op there.
            label.uppercase(java.util.Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            color = c.faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = c.text,
            maxLines = 1,
        )
        if (!note.isNullOrBlank()) {
            Text(
                note,
                style = MaterialTheme.typography.labelMedium,
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ------------------------------------------------------------
//  Six months
// ------------------------------------------------------------

@Composable
private fun TrendCard(ledger: Ledger, fmt: Fmt, period: Dates.Period, onPick: (String) -> Unit) {
    val c = LocalTallyColors.current
    val months = Compute.byPeriod(
        ledger.transactions, period.start, 6, ledger.settings.monthStart, ledger.ctx
    )
    val peak = maxOf(1L, months.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1L)

    Card {
        CardHeader(fmt.t("ins.trend"))
        Row(
            Modifier.fillMaxWidth().height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (month in months) {
                val now = month.period.start == period.start
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(month.period.start) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Row(
                        Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Bar(month.income.toFloat() / peak, c.income, now)
                        Bar(month.expense.toFloat() / peak, c.expense, now)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        fmt.monthShort(month.period.start),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (now) c.text else c.faint,
                        fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        ) {
            LegendKey(c.income, fmt.t("ins.trendIncome"))
            LegendKey(c.expense, fmt.t("ins.trendExpense"))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Bar(
    fraction: Float,
    colour: Color,
    solid: Boolean,
) {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight(fraction.coerceIn(0.015f, 1f))
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
            .background(if (solid) colour else colour.copy(alpha = 0.35f))
    )
}

@Composable
private fun LegendKey(colour: Color, label: String) {
    val c = LocalTallyColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(colour))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.muted)
    }
}
