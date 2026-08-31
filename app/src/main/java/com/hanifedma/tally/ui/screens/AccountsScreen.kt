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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hanifedma.tally.core.AccountRow
import com.hanifedma.tally.core.Compute
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.Money
import com.hanifedma.tally.ui.Fmt
import com.hanifedma.tally.ui.accountGlyph
import com.hanifedma.tally.ui.components.Divider
import com.hanifedma.tally.ui.components.EmptyState
import com.hanifedma.tally.ui.components.GhostButton
import com.hanifedma.tally.ui.components.IconChip
import com.hanifedma.tally.ui.theme.LocalTallyColors

/**
 * Where the money sits.
 *
 * Each account keeps its own currency and shows its own balance in it; the
 * one figure at the top is the only place they are added together, and it
 * says which currency it is speaking.
 */
@Composable
fun AccountsScreen(
    ledger: Ledger,
    fmt: Fmt,
    showArchived: Boolean,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
    onOpen: (AccountRow) -> Unit,
    onAdd: () -> Unit,
    onToggleArchived: () -> Unit,
) {
    val c = LocalTallyColors.current
    val all = ledger.liveAccounts(includeArchived = true)
    val live = all.filter { !it.archived }
    val balances = Compute.balances(all, ledger.transactions)

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = contentPadding,
    ) {
        item("networth") {
            Column(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    fmt.t("acc.netWorth").uppercase(java.util.Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.faint,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    fmt.money(Compute.netWorth(all, ledger.transactions, ledger.ctx)),
                    style = MaterialTheme.typography.displaySmall,
                    color = c.text,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (all.isEmpty()) {
            item("empty") {
                EmptyState("👛", fmt.t("acc.empty.h"), fmt.t("acc.empty.p"), fmt.t("acc.add"), onAdd)
            }
            return@LazyColumn
        }

        item("list") {
            val visible = if (showArchived) all else live
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(14.dp))
            ) {
                visible.forEachIndexed { index, account ->
                    AccountRowView(
                        account = account,
                        balance = balances[account.id] ?: 0L,
                        ledger = ledger,
                        fmt = fmt,
                        onClick = { onOpen(account) },
                    )
                    if (index != visible.lastIndex) Divider()
                }
            }
        }

        item("actions") {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GhostButton(fmt.t("acc.add"), Modifier.weight(1f), onClick = onAdd)
                if (all.size != live.size) {
                    GhostButton(
                        if (showArchived) fmt.t("close") else fmt.t("acc.showArchived"),
                        Modifier.weight(1f),
                        onClick = onToggleArchived,
                    )
                }
            }
        }
        item("tail") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun AccountRowView(
    account: AccountRow,
    balance: Long,
    ledger: Ledger,
    fmt: Fmt,
    onClick: () -> Unit,
) {
    val c = LocalTallyColors.current
    val converted = if (account.currency != ledger.ctx.main) {
        Money.toMain(
            balance, account.currency,
            Money.rateForNew(account.currency, ledger.ctx), ledger.ctx.main, ledger.ctx,
        )
    } else null

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (account.archived) 0.55f else 1f)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        IconChip(accountGlyph(account.kind), c.named(account.color), 34.dp)
        Column(Modifier.weight(1f)) {
            Text(
                account.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    append(fmt.t("acc.kind." + account.kind))
                    append(" · ")
                    append(account.currency)
                    if (account.archived) {
                        append(" · ")
                        append(fmt.t("acc.archived"))
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = c.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                fmt.money(balance, account.currency),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (balance < 0) c.expense else c.text,
                maxLines = 1,
            )
            if (converted != null) {
                Text(
                    fmt.t("acc.inMain", mapOf("amount" to fmt.money(converted))),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.faint,
                    maxLines = 1,
                )
            }
        }
    }
}
