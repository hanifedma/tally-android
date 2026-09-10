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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.tally.core.Calc
import com.hanifedma.tally.core.Compute
import com.hanifedma.tally.core.Dates
import com.hanifedma.tally.core.Ids
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.Money
import com.hanifedma.tally.core.TransactionRow
import com.hanifedma.tally.ui.Fmt
import com.hanifedma.tally.ui.accountGlyph
import com.hanifedma.tally.ui.components.Divider
import com.hanifedma.tally.ui.components.FieldLabel
import com.hanifedma.tally.ui.components.GhostButton
import com.hanifedma.tally.ui.components.Help
import com.hanifedma.tally.ui.components.IconChip
import com.hanifedma.tally.ui.components.PickerRow
import com.hanifedma.tally.ui.components.PrimaryButton
import com.hanifedma.tally.ui.components.Segmented
import com.hanifedma.tally.ui.theme.LocalTallyColors

/**
 * The editor's working copy. Held as one object so that "save and add
 * another" can keep the shape of what was just entered — kind, account,
 * category, date — and clear only what changes each time.
 */
data class Draft(
    val id: String = Ids.random(),
    val kind: String = "expense",
    val amount: String = "",
    val currency: String = Money.DEFAULT_CURRENCY,
    val rate: Double = 1.0,
    val rateBase: String = Money.DEFAULT_CURRENCY,
    val accountId: String? = null,
    val toAccountId: String? = null,
    val toAmount: String = "",
    // The destination account's currency. `toAmount` is written in it, not
    // in `currency`, and the two rarely have the same number of decimals —
    // reading "10.50" as won and writing 1,050 minor units of dollars is a
    // hundredfold error in what landed.
    val toCurrency: String = Money.DEFAULT_CURRENCY,
    // Text, like the amount: blank means no fee, so an untouched transfer
    // does not come back reading "0".
    val fee: String = "",
    val categoryId: String? = null,
    val note: String = "",
    val occurredOn: String = Dates.today(),
    val occurredMin: Int = Dates.minuteOfDay(),
    val createdAt: String? = null,
) {
    fun toRow(): TransactionRow {
        val minor = Money.parseToMinor(amount, currency) ?: 0L
        return TransactionRow(
            id = id,
            kind = kind,
            amountMinor = minor,
            currency = currency,
            rate = rate,
            rateBase = rateBase,
            accountId = accountId,
            toAccountId = if (kind == "transfer") toAccountId else null,
            toAmountMinor = if (kind == "transfer" && toAmount.isNotBlank()) {
                Money.parseToMinor(toAmount, toCurrency)
            } else null,
            feeMinor = if (kind == "transfer") Money.parseToMinor(fee, currency) ?: 0L else 0L,
            categoryId = if (kind == "transfer") null else categoryId,
            note = note.trim().take(280),
            occurredOn = occurredOn,
            occurredMin = occurredMin,
            createdAt = createdAt ?: java.time.Instant.now().toString(),
            deletedAt = null,
        )
    }

    /**
     * The parts of a draft a person put there, in the order they meet them.
     *
     * Everything else on a Draft is bookkeeping — the id, the currency and
     * rate that follow whichever account is chosen, the timestamp — and none
     * of it is work that would be missed if this were closed.
     */
    fun typedFields(): List<Any?> = listOf(
        kind, amount, categoryId, accountId, toAccountId, toAmount, fee, note,
        occurredOn, occurredMin,
    )

    companion object {
        /**
         * @param toCurrency what the destination account holds. The row does
         *   not record it — only the account does — and `to_amount_minor` is
         *   counted in it, so reopening a transfer without it would show the
         *   landed amount at the wrong scale.
         */
        fun from(tx: TransactionRow, toCurrency: String? = null) = Draft(
            id = tx.id,
            kind = tx.kind,
            // Grouped, as the fields will show it: a draft that did not match
            // what is on screen would read as unsaved work the moment it
            // opened.
            amount = Money.groupAmount(Money.minorToInput(tx.amountMinor, tx.currency)),
            currency = tx.currency,
            rate = tx.rate,
            rateBase = tx.rateBase,
            accountId = tx.accountId,
            toAccountId = tx.toAccountId,
            toCurrency = toCurrency ?: tx.currency,
            toAmount = tx.toAmountMinor
                ?.let { Money.groupAmount(Money.minorToInput(it, toCurrency ?: tx.currency)) }
                ?: "",
            fee = if (tx.feeMinor != 0L) {
                Money.groupAmount(Money.minorToInput(tx.feeMinor, tx.currency))
            } else "",
            categoryId = tx.categoryId,
            note = tx.note,
            occurredOn = tx.occurredOn,
            occurredMin = tx.occurredMin,
            createdAt = tx.createdAt,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorSheet(
    ledger: Ledger,
    fmt: Fmt,
    initial: Draft,
    existing: TransactionRow?,
    onPickCategory: (String, String?, (String) -> Unit) -> Unit,
    onPickAccount: (String?, (String) -> Unit) -> Unit,
    onSetRate: (String, (Double) -> Unit) -> Unit,
    onSave: (TransactionRow, Boolean) -> Unit,
    onDelete: (TransactionRow) -> Unit,
    onUnsaved: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalTallyColors.current
    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    // The note's suggestion list is open whenever the field has focus, and
    // shut only until the note next changes. Latching it shut on a tap and
    // waiting for another focus event is what used to strand it: picking a
    // suggestion never moves focus, so nothing ever re-opened the list.
    var noteFocused by remember { mutableStateOf(false) }
    var noteDismissed by remember { mutableStateOf(false) }

    // Whether the fee has been typed in during this edit. A fact about the
    // editing, not about the transaction, which is why it lives here and not
    // on the draft: once it is true, no later change of account may overwrite
    // what the person put there themselves.
    var feeTouched by remember { mutableStateOf(false) }

    // The amount field owns its own caret: the operator buttons append to it
    // and have to be able to put the cursor after what they inserted.
    var amountField by remember {
        mutableStateOf(TextFieldValue(draft.amount, TextRange(draft.amount.length)))
    }
    // The note owns its own for the same reason: a tapped suggestion
    // replaces the whole text, and the caret belongs at the end of it.
    var noteField by remember {
        mutableStateOf(TextFieldValue(draft.note, TextRange(draft.note.length)))
    }

    val ctx = ledger.ctx
    val account = ledger.account(draft.accountId)
    val toAccount = ledger.account(draft.toAccountId)
    val currency = account?.currency ?: ctx.main
    // Follows the destination account, so that changing it re-reads what
    // landed at the new account's scale rather than the old one's.
    val toCurrency = toAccount?.currency ?: currency
    // Written in an effect rather than straight into the draft, because a
    // composable that writes state while it is composing has to be composed
    // again to see it, and that doubles the work of every frame that touches
    // this sheet.
    LaunchedEffect(currency, toCurrency) {
        if (currency != draft.currency || toCurrency != draft.toCurrency) {
            draft = draft.copy(currency = currency, toCurrency = toCurrency)
        }
    }

    val isTransfer = draft.kind == "transfer"
    val category = ledger.category(draft.categoryId)
    val minor = Money.parseToMinor(draft.amount, currency)

    // Is there anything here that closing would throw away?
    //
    // Compared field by field against the draft this opened with, so an
    // entry typed and then rubbed out again counts as nothing, and an
    // existing transaction reopened and left alone does too. The fields the
    // editor keeps in step by itself — the currency and rate that follow the
    // account — are not in the list: nobody typed those, and including them
    // would make an untouched form claim to be unsaved work.
    val unsaved = draft.typedFields() != initial.typedFields()
    LaunchedEffect(unsaved) { onUnsaved(unsaved) }

    /**
     * The same transfer as the fee it cost last time.
     *
     * Only for a new transfer, and only until the fee has been typed in: an
     * existing transaction already knows what it cost, and a number the
     * person put there themselves outranks anything the ledger remembers. A
     * pair with no history, or whose last transfer was free, leaves the field
     * blank rather than writing a nought into it.
     */
    fun withSuggestedFee(d: Draft): Draft {
        if (existing != null || feeTouched) return d
        val was = Compute.lastTransferFee(
            ledger.transactions, d.accountId, d.toAccountId, d.currency,
        )
        return d.copy(
            fee = if (was != null && was != 0L) {
                Money.groupAmount(Money.minorToInput(was, d.currency))
            } else "",
        )
    }

    fun save(another: Boolean) {
        val problem = Money.validate(
            draft.amount, currency, draft.kind,
            draft.accountId, draft.toAccountId, draft.categoryId, ctx,
            feeText = draft.fee,
        )
        if (problem != null) {
            error = problem
            return
        }
        error = null
        // The two currencies are stated here rather than trusted to have
        // arrived, so that a save in the same frame as a change of account
        // cannot file the amount against the currency before it.
        onSave(draft.copy(currency = currency, toCurrency = toCurrency).toRow(), another)
        if (another) {
            draft = draft.copy(
                id = Ids.random(),
                amount = "",
                note = "",
                toAmount = "",
                // The fee stays. The transfer just filed is now the last one
                // between this pair, so what it cost is exactly what the next
                // one would be told to expect — and taking it from the draft
                // rather than asking the ledger avoids racing the save, which
                // has not reached it yet.
                occurredMin = Dates.minuteOfDay(),
                createdAt = null,
            )
            // Typing a fee governs the transfer it was typed for, not every
            // transfer after it, so a change of account may fill it in again.
            feeTouched = false
            amountField = TextFieldValue("")
            noteField = TextFieldValue("")
        }
    }

    Column(Modifier.fillMaxWidth()) {
        SheetHeader(if (existing == null) fmt.t("tx.new") else fmt.t("tx.edit"), onClose)

        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // ---- kind ----
            Segmented(
                options = listOf(fmt.t("tx.expense"), fmt.t("tx.income"), fmt.t("tx.transfer")),
                selected = when (draft.kind) { "income" -> 1; "transfer" -> 2; else -> 0 },
                tints = listOf(c.expense, c.income, c.transfer),
            ) { index ->
                val next = when (index) { 1 -> "income"; 2 -> "transfer"; else -> "expense" }
                if (next != draft.kind) {
                    draft = when (next) {
                        "transfer" -> withSuggestedFee(
                            draft.copy(
                                kind = next,
                                categoryId = null,
                                toAccountId = draft.toAccountId
                                    ?: ledger.liveAccounts().firstOrNull { it.id != draft.accountId }?.id,
                            )
                        )
                        // A category from the other side of the ledger would
                        // be wrong, so drop one that no longer fits.
                        else -> {
                            // The typed fee goes with the transfer, so coming
                            // back to one starts from the ledger again rather
                            // than from a blank.
                            feeTouched = false
                            draft.copy(
                                kind = next,
                                toAccountId = null,
                                toAmount = "",
                                fee = "",
                                categoryId = draft.categoryId
                                    ?.takeIf { ledger.category(it)?.kind == next },
                            )
                        }
                    }
                    error = null
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- amount ----
            AmountField(
                value = amountField,
                currency = currency,
                kindColour = when (draft.kind) {
                    "income" -> c.income
                    "transfer" -> c.transfer
                    else -> c.expense
                },
                onChange = { next ->
                    val grouped = regrouped(amountField, next)
                    amountField = grouped
                    if (grouped.text != draft.amount) draft = draft.copy(amount = grouped.text)
                    error = null
                },
            )
            Spacer(Modifier.height(8.dp))
            OperatorRow { op ->
                // Move the caret past what was just inserted. A plain string
                // field leaves it at the offset it already had, which puts it
                // to the *left* of the operator you pressed — so the next
                // digit lands on the wrong side of it.
                //
                // Grouped on the way in, so that what the 000 key adds reads
                // the same as three noughts that were typed.
                val next = Money.groupAmount(amountField.text + op)
                amountField = TextFieldValue(next, TextRange(next.length))
                draft = draft.copy(amount = next)
                error = null
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val unreadable = draft.amount.isNotBlank() && minor == null
                val rateUnknown = minor != null && minor != 0L && currency != ctx.main &&
                    Money.rateMissing(currency, ctx.main, ctx.rates)
                // Someone typing a sum should see the answer while they type,
                // not have to save the transaction to find out what it is.
                val showsTotal = minor != null && minor != 0L && Calc.isExpression(draft.amount)

                val hint = when {
                    unreadable -> fmt.t("tx.calcBad")
                    minor == null || minor == 0L -> fmt.t("tx.calcHint")
                    rateUnknown -> fmt.t("tx.rateMissing", mapOf("code" to currency))
                    else -> buildList {
                        if (showsTotal) {
                            add(fmt.t("tx.calcEquals", mapOf("amount" to fmt.money(minor, currency))))
                        }
                        if (currency != ctx.main) {
                            add(
                                fmt.t(
                                    "tx.converted",
                                    mapOf(
                                        "amount" to fmt.money(
                                            Money.toMain(minor, currency, draft.rate, draft.rateBase, ctx)
                                        )
                                    ),
                                )
                            )
                        }
                    }.joinToString("  ·  ")
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (showsTotal) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        unreadable || rateUnknown -> c.warn
                        showsTotal -> c.text
                        else -> c.faint
                    },
                    modifier = Modifier.weight(1f),
                )
                if (currency != ctx.main) {
                    Text(
                        fmt.t("tx.rateFix"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSetRate(currency) { rate ->
                                    draft = draft.copy(rate = rate, rateBase = ctx.main)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- category ----
            if (!isTransfer) {
                PickerRow(
                    label = fmt.t("tx.category"),
                    value = category?.name ?: fmt.t("tx.categoryPick"),
                    placeholder = category == null,
                    invalid = error == "tx.needCategory",
                    leading = { IconChip(category?.icon ?: "•", category?.let { c.named(it.color) }, 26.dp, 14.sp) },
                ) {
                    onPickCategory(draft.kind, draft.categoryId) { id ->
                        draft = draft.copy(categoryId = id)
                        error = null
                    }
                }
            }

            // ---- accounts ----
            PickerRow(
                label = if (isTransfer) fmt.t("tx.accountFrom") else fmt.t("tx.account"),
                value = account?.let { it.name + " · " + it.currency } ?: fmt.t("tx.accountPick"),
                placeholder = account == null,
                invalid = error == "tx.needAccount",
                leading = { IconChip(accountGlyph(account?.kind ?: "cash"), account?.let { c.named(it.color) }, 26.dp, 13.sp) },
            ) {
                onPickAccount(draft.accountId) { id ->
                    val picked = ledger.account(id)
                    draft = withSuggestedFee(
                        draft.copy(
                            accountId = id,
                            currency = picked?.currency ?: currency,
                            // A new currency needs a new frozen rate; keeping
                            // the old one would price rupiah at the won rate.
                            rate = picked?.let { Money.rateForNew(it.currency, ctx) } ?: draft.rate,
                            rateBase = ctx.main,
                        )
                    )
                    error = null
                }
            }

            if (isTransfer) {
                PickerRow(
                    label = fmt.t("tx.accountTo"),
                    value = toAccount?.let { it.name + " · " + it.currency } ?: fmt.t("tx.accountPick"),
                    placeholder = toAccount == null,
                    invalid = error == "tx.needToAccount" || error == "tx.sameAccount",
                    leading = { IconChip(accountGlyph(toAccount?.kind ?: "cash"), toAccount?.let { c.named(it.color) }, 26.dp, 13.sp) },
                ) {
                    onPickAccount(draft.toAccountId) { id ->
                        draft = withSuggestedFee(draft.copy(toAccountId = id))
                        error = null
                    }
                }
                // What the bank kept. Blank almost always, so it is one short
                // field rather than a section: a transfer that cost nothing
                // should not have to say so.
                val feeBad = draft.fee.isNotBlank() && Money.parseToMinor(draft.fee, currency) == null
                FieldLabel(fmt.t("tx.fee") + " · " + currency)
                MoneyField(
                    value = draft.fee,
                    placeholder = Money.minorToInput(0L, currency),
                ) {
                    draft = draft.copy(fee = it)
                    // From here on this transfer's fee is the person's, not
                    // the ledger's.
                    feeTouched = true
                    error = null
                }
                Help(
                    if (feeBad) fmt.t("tx.feeBad") else fmt.t("tx.feeHelp"),
                    Modifier.padding(top = 6.dp, bottom = 14.dp),
                    warn = feeBad,
                )

                // Only a cross-currency transfer needs to say what landed.
                if (account != null && toAccount != null && account.currency != toAccount.currency) {
                    FieldLabel(fmt.t("tx.receives") + " · " + toAccount.currency)
                    MoneyField(
                        value = draft.toAmount,
                        placeholder = Money.groupAmount(
                            Money.minorToInput(
                                Money.convertMinor(minor ?: 0L, currency, toAccount.currency, ctx),
                                toAccount.currency,
                            )
                        ),
                    ) { draft = draft.copy(toAmount = it) }
                    Help(fmt.t("tx.receivesHelp"), Modifier.padding(top = 6.dp, bottom = 14.dp))
                } else if (draft.toAmount.isNotBlank()) {
                    // The field is gone, so what was typed into it must go
                    // too. Keeping it would send a number of dollars into a
                    // won account with nothing on screen saying where it
                    // came from.
                    draft = draft.copy(toAmount = "")
                }
            }

            // ---- note ----
            FieldLabel(fmt.t("tx.note"))
            PlainField(
                value = noteField,
                placeholder = fmt.t("tx.notePlaceholder"),
                onFocus = { focused ->
                    noteFocused = focused
                    // Coming back to the field is a fresh ask, the same as
                    // it is on the web, where focus re-runs the search.
                    if (focused) noteDismissed = false
                },
            ) { next ->
                // Typing — including clearing the box — is a new question,
                // so it deserves a new answer. Moving the caret is not.
                if (next.text != noteField.text) noteDismissed = false
                noteField = next
                draft = draft.copy(note = next.text)
            }

            if (noteFocused && !noteDismissed) {
                // Kept between compositions. Working these out means sorting
                // every transaction ever filed, and doing that again for each
                // frame of, say, a sheet being dragged is how a cheap phone
                // ends up stuttering over a list of five words.
                val suggestions = remember(ledger.transactions, draft.note) {
                    Compute.noteSuggestions(ledger.transactions, draft.note, 5)
                        .filter { !it.note.equals(draft.note.trim(), ignoreCase = true) }
                }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.surface)
                            .border(1.dp, c.border, RoundedCornerShape(10.dp))
                    ) {
                        suggestions.forEachIndexed { index, s ->
                            Text(
                                s.note,
                                style = MaterialTheme.typography.bodyLarge,
                                color = c.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Reusing a note almost always means
                                        // reusing what it was filed under.
                                        noteField = TextFieldValue(
                                            s.note, TextRange(s.note.length),
                                        )
                                        draft = draft.copy(
                                            note = s.note,
                                            categoryId = if (!isTransfer && s.kind == draft.kind && s.categoryId != null) {
                                                s.categoryId
                                            } else draft.categoryId,
                                        )
                                        // Answered — until the note changes
                                        // again. Tapping a suggestion does
                                        // not move focus, so this is the
                                        // only thing that closes the list.
                                        noteDismissed = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            )
                            if (index != suggestions.lastIndex) Divider()
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // ---- when ----
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    FieldLabel(fmt.t("tx.date"))
                    TapField(fmt.dayShort(draft.occurredOn)) { showDate = true }
                }
                Column(Modifier.weight(1f)) {
                    FieldLabel(fmt.t("tx.time"))
                    TapField(fmt.time(draft.occurredMin)) { showTime = true }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(fmt.t("tx.today"), Modifier.weight(1f)) {
                    draft = draft.copy(occurredOn = Dates.today())
                }
                GhostButton(fmt.t("tx.yesterday"), Modifier.weight(1f)) {
                    draft = draft.copy(occurredOn = Dates.addDays(Dates.today(), -1))
                }
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    fmt.t(error!!, mapOf("code" to currency)),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.danger,
                )
                if (error == "tx.needRate") {
                    Spacer(Modifier.height(8.dp))
                    GhostButton(fmt.t("tx.rateFix")) {
                        onSetRate(currency) { rate ->
                            draft = draft.copy(rate = rate, rateBase = ctx.main)
                            error = null
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // ---- footer ----
        Divider()
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (existing != null) {
                // A bin rather than the word "Delete", which is what the web
                // does here and for the same reason: three labelled buttons
                // do not fit across a phone, and the one that lost the
                // argument was "Save & another", which arrived as "Save &".
                // The label is still there for anyone listening to the screen
                // rather than looking at it.
                GhostButton(
                    "🗑",
                    danger = true,
                    compact = true,
                    modifier = Modifier.semantics { contentDescription = fmt.t("delete") },
                ) { onDelete(existing) }
            }
            GhostButton(fmt.t("tx.saveAnother"), Modifier.weight(1f)) { save(true) }
            PrimaryButton(fmt.t("tx.save"), Modifier.weight(1f)) { save(false) }
        }
    }

    if (showDate) {
        DatePickerSheet(
            initial = draft.occurredOn,
            fmt = fmt,
            onPick = { draft = draft.copy(occurredOn = it); showDate = false },
            onDismiss = { showDate = false },
        )
    }
    if (showTime) {
        TimePickerSheet(
            initialMinutes = draft.occurredMin,
            fmt = fmt,
            onPick = { draft = draft.copy(occurredMin = it); showTime = false },
            onDismiss = { showTime = false },
        )
    }
}

// ------------------------------------------------------------
//  Pieces
// ------------------------------------------------------------

@Composable
fun SheetHeader(title: String, onClose: () -> Unit) {
    val c = LocalTallyColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = c.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClose)
                    .padding(10.dp)
            ) {
                Text("✕", color = c.muted, fontSize = 16.sp)
            }
        }
        Divider()
    }
}

@Composable
private fun AmountField(
    value: TextFieldValue,
    currency: String,
    kindColour: androidx.compose.ui.graphics.Color,
    onChange: (TextFieldValue) -> Unit,
) {
    val c = LocalTallyColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            Money.of(currency).symbol.ifEmpty { currency },
            style = MaterialTheme.typography.titleLarge,
            color = c.muted,
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = kindColour,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                letterSpacing = (-0.8).sp,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterEnd) {
                    if (value.text.isEmpty()) {
                        Text(
                            "0",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.faint,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

/** The four operators and a "000", because splitting a bill is common. */
@Composable
private fun OperatorRow(onAppend: (String) -> Unit) {
    val c = LocalTallyColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (op in listOf("+", "−", "×", "÷", "000")) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface2)
                    .clickable { onAppend(op) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(op, style = MaterialTheme.typography.labelLarge, color = c.muted)
            }
        }
    }
}

@Composable
fun PlainField(
    value: String,
    placeholder: String = "",
    numeric: Boolean = false,
    alignEnd: Boolean = false,
    singleLine: Boolean = true,
    onFocus: ((Boolean) -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    val c = LocalTallyColors.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = plainTextStyle(alignEnd),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
        modifier = plainFieldModifier(onFocus),
        decorationBox = plainDecoration(value.isEmpty(), placeholder, alignEnd),
    )
}

/**
 * The same field, holding its own caret.
 *
 * Worth the second overload only where something other than typing changes
 * the text — picking a note from the suggestions, say. The string version
 * leaves the caret at the offset it had, which after a replacement is
 * usually the middle of a word.
 */
@Composable
fun PlainField(
    value: TextFieldValue,
    placeholder: String = "",
    numeric: Boolean = false,
    alignEnd: Boolean = false,
    singleLine: Boolean = true,
    onFocus: ((Boolean) -> Unit)? = null,
    onChange: (TextFieldValue) -> Unit,
) {
    val c = LocalTallyColors.current
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = plainTextStyle(alignEnd),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
        modifier = plainFieldModifier(onFocus),
        decorationBox = plainDecoration(value.text.isEmpty(), placeholder, alignEnd),
    )
}

/**
 * What a keystroke leaves in a field that groups its digits.
 *
 * A caret moved or a selection dragged is not an edit, and comes back
 * untouched: collapsing a selection to a caret would make it impossible to
 * select anything at all.
 */
fun regrouped(before: TextFieldValue, next: TextFieldValue): TextFieldValue {
    if (next.text == before.text) return next
    val typed = Money.groupAmountEdit(before.text, next.text, next.selection.start)
    if (typed.text == next.text) return next
    return TextFieldValue(typed.text, TextRange(typed.caret))
}

/**
 * A money field that puts the thousands separators in as they are typed:
 * 310575 becomes 310,575 under the person's hands.
 *
 * It holds its own caret, because the text handed back is not the text that
 * was typed — a separator appearing to the left of the cursor would
 * otherwise drag the cursor along with it. What [onChange] receives is the
 * grouped text, which every reader of an amount strips before parsing.
 */
@Composable
fun MoneyField(
    value: String,
    placeholder: String = "",
    onChange: (String) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Something other than typing changed it: a remembered fee arriving, or
    // a form clearing itself after a save. In an effect rather than in
    // composition, so that reading it never means composing twice.
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    PlainField(
        value = field,
        placeholder = placeholder,
        numeric = true,
        alignEnd = true,
    ) { next ->
        val grouped = regrouped(field, next)
        field = grouped
        if (grouped.text != value) onChange(grouped.text)
    }
}

@Composable
private fun plainTextStyle(alignEnd: Boolean): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(
        color = LocalTallyColors.current.text,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
    )

@Composable
private fun plainFieldModifier(onFocus: ((Boolean) -> Unit)?): Modifier {
    val c = LocalTallyColors.current
    return Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(10.dp))
        .background(c.surface2)
        .border(1.dp, c.border, RoundedCornerShape(10.dp))
        .heightIn(min = 44.dp)
        .then(
            if (onFocus != null) Modifier.onFocusChanged { onFocus(it.isFocused) }
            else Modifier
        )
        .padding(horizontal = 12.dp, vertical = 11.dp)
}

@Composable
private fun plainDecoration(
    empty: Boolean,
    placeholder: String,
    alignEnd: Boolean,
): @Composable (@Composable () -> Unit) -> Unit {
    val c = LocalTallyColors.current
    return { inner ->
        Box(contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
            if (empty && placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            inner()
        }
    }
}

@Composable
private fun TapField(value: String, onClick: () -> Unit) {
    val c = LocalTallyColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(value, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1)
    }
}
