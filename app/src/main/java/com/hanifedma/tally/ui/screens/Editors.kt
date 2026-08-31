package com.hanifedma.tally.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.tally.core.AccountRow
import com.hanifedma.tally.core.BudgetRow
import com.hanifedma.tally.core.CategoryRow
import com.hanifedma.tally.core.Ids
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.Money
import com.hanifedma.tally.ui.Fmt
import com.hanifedma.tally.ui.components.Divider
import com.hanifedma.tally.ui.components.FieldLabel
import com.hanifedma.tally.ui.components.GhostButton
import com.hanifedma.tally.ui.components.Help
import com.hanifedma.tally.ui.components.IconChip
import com.hanifedma.tally.ui.components.PrimaryButton
import com.hanifedma.tally.ui.components.Segmented
import com.hanifedma.tally.ui.theme.LocalTallyColors

// ------------------------------------------------------------
//  A dropdown, since two of these editors need one
// ------------------------------------------------------------

@Composable
fun DropdownField(
    value: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
) {
    val c = LocalTallyColors.current
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface2)
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                options.firstOrNull { it.first == value }?.second ?: value,
                style = MaterialTheme.typography.bodyLarge,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("⌄", color = c.faint, fontSize = 15.sp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.heightIn(max = 380.dp).background(c.elevated),
        ) {
            for ((key, label) in options) {
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (key == value) c.accent else c.text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    onClick = { open = false; onPick(key) },
                )
            }
        }
    }
}

// ------------------------------------------------------------
//  Account
// ------------------------------------------------------------

@Composable
fun AccountEditorSheet(
    ledger: Ledger,
    fmt: Fmt,
    existing: AccountRow?,
    onSave: (AccountRow) -> Unit,
    onDelete: (AccountRow) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalTallyColors.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.kind ?: "cash") }
    var currency by remember { mutableStateOf(existing?.currency ?: ledger.ctx.main) }
    var opening by remember {
        mutableStateOf(
            existing?.let { Money.minorToInput(it.openingMinor, it.currency) } ?: ""
        )
    }
    var colour by remember { mutableStateOf(existing?.color ?: "indigo") }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        SheetHeader(if (existing == null) fmt.t("acc.new") else fmt.t("acc.edit"), onClose)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            FieldLabel(fmt.t("acc.name"))
            PlainField(name, fmt.t("acc.namePlaceholder")) { name = it; error = null }
            Spacer(Modifier.height(14.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(fmt.t("acc.kind"))
                    DropdownField(
                        kind,
                        AccountRow.ACCOUNT_KINDS.map { it to fmt.t("acc.kind.$it") },
                    ) { kind = it }
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(fmt.t("acc.currency"))
                    DropdownField(currency, currencyOptions(fmt)) { currency = it }
                }
            }
            Spacer(Modifier.height(14.dp))

            FieldLabel(fmt.t("acc.opening") + " · " + currency)
            PlainField(opening, "0", numeric = true, alignEnd = true) { opening = it; error = null }
            Help(fmt.t("acc.openingHelp"), Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))

            FieldLabel(fmt.t("acc.colour"))
            ColourRow(colour) { colour = it }

            if (existing != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { archived = !archived },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = archived, onCheckedChange = { archived = it })
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            fmt.t("acc.archive"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.text,
                        )
                        Help(fmt.t("acc.archivedHelp"))
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(fmt.t(error!!), style = MaterialTheme.typography.labelLarge, color = c.danger)
            }
        }
        SheetFooter {
            if (existing != null) {
                GhostButton(fmt.t("delete"), danger = true) { onDelete(existing) }
            }
            PrimaryButton(fmt.t("save"), Modifier.weight(1f)) {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    error = "acc.name"
                    return@PrimaryButton
                }
                if (ledger.accounts.any {
                        it.id != (existing?.id ?: "") && it.name.equals(trimmed, ignoreCase = true)
                    }
                ) {
                    error = "err.nameTaken"
                    return@PrimaryButton
                }
                val magnitude = Money.parseToMinor(opening.ifBlank { "0" }, currency)
                if (magnitude == null) {
                    error = "tx.calcBad"
                    return@PrimaryButton
                }
                // A starting balance can be negative — that is what a credit
                // card is — so take the sign from what was actually typed.
                val negative = opening.trimStart().startsWith("-") || opening.trimStart().startsWith("−")
                onSave(
                    AccountRow(
                        id = existing?.id ?: Ids.random(),
                        name = trimmed,
                        kind = kind,
                        currency = currency,
                        openingMinor = if (negative) -magnitude else magnitude,
                        color = colour,
                        archived = archived,
                        position = existing?.position ?: ledger.accounts.size,
                        createdAt = existing?.createdAt,
                    )
                )
            }
        }
    }
}

// ------------------------------------------------------------
//  Category
// ------------------------------------------------------------

@Composable
fun CategoryEditorSheet(
    ledger: Ledger,
    fmt: Fmt,
    existing: CategoryRow?,
    startKind: String,
    onSave: (CategoryRow) -> Unit,
    onDelete: (CategoryRow) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalTallyColors.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: "•") }
    var kind by remember { mutableStateOf(existing?.kind ?: startKind) }
    var colour by remember { mutableStateOf(existing?.color ?: "gray") }
    var archived by remember { mutableStateOf(existing?.archived ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        SheetHeader(if (existing == null) fmt.t("cat.new") else fmt.t("cat.edit"), onClose)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.width(76.dp)) {
                    FieldLabel(fmt.t("cat.icon"))
                    PlainField(icon, "•", alignEnd = false) { icon = it.take(8) }
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(fmt.t("cat.name"))
                    PlainField(name, fmt.t("cat.namePlaceholder")) { name = it; error = null }
                }
            }
            Help(fmt.t("cat.iconHelp"), Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))

            FieldLabel(fmt.t("cat.side"))
            Segmented(
                options = listOf(fmt.t("cat.expense"), fmt.t("cat.income")),
                selected = if (kind == "income") 1 else 0,
                tints = listOf(c.expense, c.income),
            ) { kind = if (it == 1) "income" else "expense" }
            Spacer(Modifier.height(16.dp))

            FieldLabel(fmt.t("cat.colour"))
            ColourRow(colour) { colour = it }

            if (existing != null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { archived = !archived },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = archived, onCheckedChange = { archived = it })
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            fmt.t("cat.archive"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.text,
                        )
                        Help(fmt.t("cat.archivedHelp"))
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(fmt.t(error!!), style = MaterialTheme.typography.labelLarge, color = c.danger)
            }
        }
        SheetFooter {
            if (existing != null) {
                GhostButton(fmt.t("delete"), danger = true) { onDelete(existing) }
            }
            PrimaryButton(fmt.t("save"), Modifier.weight(1f)) {
                val trimmed = name.trim()
                if (trimmed.isEmpty()) {
                    error = "cat.name"
                    return@PrimaryButton
                }
                if (ledger.categories.any {
                        it.id != (existing?.id ?: "") && it.kind == kind &&
                            it.name.equals(trimmed, ignoreCase = true)
                    }
                ) {
                    error = "err.nameTaken"
                    return@PrimaryButton
                }
                onSave(
                    CategoryRow(
                        id = existing?.id ?: Ids.random(),
                        name = trimmed,
                        kind = kind,
                        icon = icon.trim().ifEmpty { "•" }.take(8),
                        color = colour,
                        archived = archived,
                        position = existing?.position ?: ledger.categories.size,
                        createdAt = existing?.createdAt,
                    )
                )
            }
        }
    }
}

// ------------------------------------------------------------
//  Managing categories
// ------------------------------------------------------------

@Composable
fun ManageCategoriesSheet(
    ledger: Ledger,
    fmt: Fmt,
    startKind: String,
    onEdit: (CategoryRow) -> Unit,
    onAdd: (String) -> Unit,
    onReorder: (List<CategoryRow>) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalTallyColors.current
    var kind by remember { mutableStateOf(startKind) }
    var showArchived by remember { mutableStateOf(false) }
    val list = ledger.categoriesOf(kind, showArchived)

    Column(Modifier.fillMaxWidth()) {
        SheetHeader(fmt.t("cat.manage"), onClose)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Segmented(
                options = listOf(fmt.t("cat.expense"), fmt.t("cat.income")),
                selected = if (kind == "income") 1 else 0,
                tints = listOf(c.expense, c.income),
            ) { kind = if (it == 1) "income" else "expense" }
            Spacer(Modifier.height(14.dp))

            if (list.isEmpty()) {
                Help(fmt.t("cat.empty"))
            }
            list.forEachIndexed { index, category ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconChip(category.icon, c.named(category.color), 30.dp, 15.sp)
                    Text(
                        category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (category.archived) c.faint else c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (category.archived) {
                        Text(
                            fmt.t("cat.archived"),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.muted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(c.surface3)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    MiniButton("↑", enabled = index > 0) {
                        onReorder(swap(list, index, index - 1))
                    }
                    MiniButton("↓", enabled = index < list.lastIndex) {
                        onReorder(swap(list, index, index + 1))
                    }
                    MiniButton("✎") { onEdit(category) }
                }
                if (index != list.lastIndex) Divider()
            }
        }
        SheetFooter {
            if (ledger.categoriesOf(kind, true).any { it.archived }) {
                GhostButton(
                    if (showArchived) fmt.t("close") else fmt.t("cat.archived"),
                    Modifier.weight(1f),
                ) { showArchived = !showArchived }
            }
            PrimaryButton(fmt.t("cat.add"), Modifier.weight(1f)) { onAdd(kind) }
        }
    }
}

/**
 * Swap two neighbours and renumber the whole side, so positions stay dense —
 * a list dragged about for a year otherwise ends up with everything at zero.
 */
private fun swap(list: List<CategoryRow>, from: Int, to: Int): List<CategoryRow> {
    val out = list.toMutableList()
    val moved = out.removeAt(from)
    out.add(to, moved)
    return out.mapIndexed { i, category -> category.copy(position = i) }
}

@Composable
private fun MiniButton(glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalTallyColors.current
    Box(
        Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(c.surface2)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = if (enabled) c.muted else c.faint.copy(alpha = 0.4f), fontSize = 14.sp)
    }
}

// ------------------------------------------------------------
//  Budgets
// ------------------------------------------------------------

@Composable
fun BudgetsSheet(
    ledger: Ledger,
    fmt: Fmt,
    onSave: (List<BudgetRow>, List<BudgetRow>) -> Unit,
    onClose: () -> Unit,
) {
    val main = ledger.ctx.main
    val existingByCategory = ledger.budgets.associateBy { it.categoryId }
    val categories = ledger.categoriesOf("expense")

    // Keyed by category id, with "" standing for the overall budget.
    val values = remember {
        val initial = LinkedHashMap<String, String>()
        initial[""] = existingByCategory[null]
            ?.let { Money.minorToInput(it.amountMinor, it.currency) } ?: ""
        for (category in categories) {
            initial[category.id] = existingByCategory[category.id]
                ?.let { Money.minorToInput(it.amountMinor, it.currency) } ?: ""
        }
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(initial) }
    }

    Column(Modifier.fillMaxWidth()) {
        SheetHeader(fmt.t("bud.title"), onClose)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Help(fmt.t("bud.help"))
            Spacer(Modifier.height(14.dp))
            FieldLabel(fmt.t("bud.total"))
            BudgetRowField("∑", fmt.t("ins.budgetTotal"), values[""] ?: "", main, fmt) {
                values[""] = it
            }
            Spacer(Modifier.height(18.dp))
            FieldLabel(fmt.t("bud.perCategory"))
            for (category in categories) {
                BudgetRowField(
                    category.icon, category.name, values[category.id] ?: "", main, fmt,
                ) { values[category.id] = it }
            }
        }
        SheetFooter {
            GhostButton(fmt.t("cancel"), Modifier.weight(1f), onClick = onClose)
            PrimaryButton(fmt.t("save"), Modifier.weight(1f)) {
                val keep = ArrayList<BudgetRow>()
                val drop = ArrayList<BudgetRow>()
                for ((key, text) in values) {
                    val categoryId = key.ifEmpty { null }
                    val existing = existingByCategory[categoryId]
                    val minor = if (text.isBlank()) null else Money.parseToMinor(text, main)
                    if (minor == null || minor <= 0) {
                        // Blank, or nonsense, both mean "no limit".
                        existing?.let { drop.add(it) }
                        continue
                    }
                    keep.add(
                        BudgetRow(
                            id = existing?.id ?: Ids.random(),
                            categoryId = categoryId,
                            amountMinor = minor,
                            currency = main,
                            createdAt = existing?.createdAt,
                        )
                    )
                }
                onSave(keep, drop)
            }
        }
    }
}

@Composable
private fun BudgetRowField(
    glyph: String,
    label: String,
    value: String,
    currency: String,
    fmt: Fmt,
    onChange: (String) -> Unit,
) {
    val c = LocalTallyColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(glyph, fontSize = 15.sp)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(96.dp),
        )
        Box(Modifier.weight(1f)) {
            PlainField(value, fmt.t("bud.none"), numeric = true, alignEnd = true, onChange = onChange)
        }
        Text(currency, style = MaterialTheme.typography.labelMedium, color = c.faint)
    }
}

// ------------------------------------------------------------
//  Exchange rates
// ------------------------------------------------------------

@Composable
fun RatesSheet(
    ledger: Ledger,
    fmt: Fmt,
    onSave: (Map<String, Double>) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalTallyColors.current
    val main = ledger.ctx.main
    // Every currency actually in use, plus any already given a rate.
    val codes = remember(ledger) {
        (ledger.accounts.map { it.currency } + ledger.ctx.rates.keys)
            .toSortedSet().filter { it != main }
    }
    val values = remember {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply {
            for (code in codes) put(code, ledger.ctx.rates[code]?.let { trimTrailingZeros(it) } ?: "")
        }
    }
    var adding by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        SheetHeader(fmt.t("set.rates"), onClose)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Help(fmt.t("set.ratesHelp", mapOf("main" to main)))
            Spacer(Modifier.height(14.dp))
            if (values.isEmpty()) Help(fmt.t("ins.empty"))
            for (code in values.keys.sorted()) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "1 $code =",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = c.text,
                        modifier = Modifier.width(80.dp),
                    )
                    Box(Modifier.weight(1f)) {
                        PlainField(
                            values[code] ?: "", fmt.t("set.rateUnset"),
                            numeric = true, alignEnd = true,
                        ) { values[code] = it }
                    }
                    Text(main, style = MaterialTheme.typography.labelMedium, color = c.faint)
                }
            }
            Spacer(Modifier.height(16.dp))
            FieldLabel(fmt.t("set.rateAdd"))
            DropdownField(
                adding ?: "",
                currencyOptions(fmt).filter { it.first != main && !values.containsKey(it.first) },
            ) { code ->
                adding = null
                values[code] = ""
            }
        }
        SheetFooter {
            GhostButton(fmt.t("cancel"), Modifier.weight(1f), onClick = onClose)
            PrimaryButton(fmt.t("save"), Modifier.weight(1f)) {
                val next = LinkedHashMap<String, Double>()
                for ((code, text) in values) {
                    val v = com.hanifedma.tally.core.Calc.eval(text)
                    if (v != null && v > 0) next[code] = v
                }
                onSave(next)
            }
        }
    }
}
