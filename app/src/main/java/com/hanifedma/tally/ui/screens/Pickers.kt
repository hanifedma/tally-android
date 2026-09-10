package com.hanifedma.tally.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.tally.core.COLORS
import com.hanifedma.tally.core.Dates
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.Money
import com.hanifedma.tally.ui.Fmt
import com.hanifedma.tally.ui.accountGlyph
import com.hanifedma.tally.ui.components.GhostButton
import com.hanifedma.tally.ui.components.Help
import com.hanifedma.tally.ui.components.PrimaryButton
import com.hanifedma.tally.ui.theme.LocalTallyColors
import java.time.Instant
import java.time.ZoneOffset

/** A grid of categories, or of accounts. Two taps at most to pick anything. */
@Composable
fun CategoryPickerSheet(
    ledger: Ledger,
    fmt: Fmt,
    kind: String,
    selected: String?,
    onPick: (String) -> Unit,
    onManage: () -> Unit,
    onClose: () -> Unit,
) {
    val list = ledger.categoriesOf(kind)
    Column(Modifier.fillMaxWidth()) {
        SheetHeader(fmt.t("tx.categoryPick"), onClose)
        if (list.isEmpty()) {
            Help(fmt.t("tx.noCategories"), Modifier.padding(16.dp))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier.heightIn(max = 420.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(list, key = { it.id }) { category ->
                    ChipTile(
                        glyph = category.icon,
                        label = category.name,
                        selected = category.id == selected,
                        onClick = { onPick(category.id) },
                    )
                }
            }
        }
        SheetFooter {
            GhostButton(fmt.t("cat.manage"), Modifier.fillMaxWidth(), onClick = onManage)
        }
    }
}

@Composable
fun AccountPickerSheet(
    ledger: Ledger,
    fmt: Fmt,
    selected: String?,
    onPick: (String) -> Unit,
    onAdd: () -> Unit,
    onClose: () -> Unit,
) {
    val list = ledger.liveAccounts()
    Column(Modifier.fillMaxWidth()) {
        SheetHeader(fmt.t("tx.accountPick"), onClose)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.heightIn(max = 400.dp).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(list, key = { it.id }) { account ->
                ChipTile(
                    glyph = accountGlyph(account.kind),
                    label = account.name,
                    sublabel = account.currency,
                    selected = account.id == selected,
                    onClick = { onPick(account.id) },
                )
            }
        }
        SheetFooter {
            GhostButton(fmt.t("acc.add"), Modifier.fillMaxWidth(), onClick = onAdd)
        }
    }
}

@Composable
fun ChipTile(
    glyph: String,
    label: String,
    sublabel: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalTallyColors.current
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.accentSoft else c.surface2)
            .border(
                1.5.dp,
                if (selected) c.accent else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(glyph, fontSize = 20.sp, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (sublabel != null) {
            Text(
                sublabel,
                style = MaterialTheme.typography.labelSmall,
                color = c.faint,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun SheetFooter(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = LocalTallyColors.current
    Column {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

// ------------------------------------------------------------
//  Date and time
// ------------------------------------------------------------

/**
 * What Material's date picker measures out to: seven columns of calendar and
 * the header around them, and the row of buttons under it. None of these are
 * sizes this code chooses — they are what the picker takes — and they are
 * here only so that a window too small to hold it can be recognised before it
 * draws itself over its own edges.
 */
private val DIALOG_WIDTH = 360.dp
private val PICKER_HEIGHT = 568.dp
private val BUTTONS_HEIGHT = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(initial: String, fmt: Fmt, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    // The picker speaks in epoch milliseconds at UTC midnight; a day key is a
    // plain calendar date. Converting through UTC on both sides is what keeps
    // "the 31st" from becoming "the 30th" for anyone west of Greenwich.
    val startMillis = Dates.parse(initial).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = startMillis)
    // DatePickerDialog, not the AlertDialog the other sheets here use.
    //
    // A calendar is seven columns of a fixed width — the grid does not reflow
    // and does not shrink — and an AlertDialog is as wide as the platform
    // says a dialog is, less 24dp of padding down each side for its text. On
    // a 360dp phone that leaves about 312 for 360dp of calendar, and the
    // Saturday column is what falls off the right: the header reads "FS" with
    // the two letters on top of each other, and the 11th, 18th and 25th are
    // sliced in half. This dialog is the one Material ships for the job and
    // sizes itself to the picker instead.
    //
    // That fixes 360dp and wider, which is most phones, and moves the problem
    // rather than solving it on anything smaller: the dialog is then wider
    // than the window and it is the buttons that go over the edge, with Done
    // half off the right. A window is under 360dp on a small phone and on any
    // phone with the display size turned up, which is the same screen
    // measured in fewer dp. So what does not fit is scaled until it does.
    // Compose maps touches through the transform, so the days stay where they
    // look.
    //
    // Width only, because height needs the other half of the answer below.
    val window = LocalConfiguration.current
    val fit = minOf(1f, (window.screenWidthDp.dp - 16.dp) / DIALOG_WIDTH)

    // Landscape, where there is nothing like 568dp of window to put a
    // calendar in. Left alone the picker does not shrink and does not
    // scroll — it draws itself at full size in a box too short for it and
    // lets the pieces land on top of each other, so the weekday letters sit
    // across the first row of dates and Cancel and Done sit across the last.
    // Scaling it down does not help either: the squeeze happens first, and
    // all scaling does is make the overlap smaller.
    //
    // So in a short window the calendar keeps its full height and is given
    // somewhere to scroll instead. Two rows of the month at a time is not
    // generous, but every date is reachable and nothing is drawn over
    // anything else — and the two commonest answers, today and yesterday,
    // are buttons in the editor behind this and never need it at all.
    val room = window.screenHeightDp.dp
    val scrolls = room < PICKER_HEIGHT + BUTTONS_HEIGHT
    DatePickerDialog(
        modifier = if (fit < 1f) {
            Modifier.graphicsLayer { scaleX = fit; scaleY = fit }
        } else {
            Modifier
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onPick(date.toString())
                } else onDismiss()
            }) { Text(fmt.t("done")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(fmt.t("cancel")) } },
    ) {
        if (scrolls) {
            // Capped so the buttons below keep their room, and scrolled so
            // the calendar can be its full self inside it.
            //
            // Without the title and the headline, which between them are a
            // third of the picker's height and are spending it to say a date
            // the editor behind this is already showing. Dropping them buys
            // back three rows of the month, which is the difference between a
            // calendar and a peephole.
            Box(
                Modifier
                    .heightIn(max = room - BUTTONS_HEIGHT)
                    .verticalScroll(rememberScrollState())
            ) {
                DatePicker(
                    state = state,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                )
            }
        } else {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(initialMinutes: Int, fmt: Fmt, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = fmt.lang == "ko",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(fmt.t("done")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(fmt.t("cancel")) } },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
    )
}

// ------------------------------------------------------------
//  One exchange rate
// ------------------------------------------------------------

@Composable
fun RateSheet(
    fmt: Fmt,
    code: String,
    main: String,
    current: Double?,
    onSave: (Double) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalTallyColors.current
    var text by remember { mutableStateOf(current?.let { trimTrailingZeros(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        SheetHeader(fmt.t("set.rateFor", mapOf("code" to code)) + " " + main, onClose)
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "1 $code =",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = c.text,
                )
                Box(Modifier.weight(1f)) {
                    PlainField(value = text, placeholder = "0.0", numeric = true, alignEnd = true) {
                        text = it
                        error = null
                    }
                }
                Text(main, style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
            Spacer(Modifier.height(10.dp))
            Help(fmt.t("set.ratesHelp", mapOf("main" to main)))
            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(fmt.t(error!!), style = MaterialTheme.typography.labelLarge, color = c.danger)
            }
        }
        SheetFooter {
            GhostButton(fmt.t("cancel"), Modifier.weight(1f), onClick = onClose)
            PrimaryButton(fmt.t("save"), Modifier.weight(1f)) {
                val v = com.hanifedma.tally.core.Calc.eval(text)
                if (v == null || v <= 0) error = "err.rateBad" else onSave(v)
            }
        }
    }
}

/** 0.0875 rather than 0.0875000000001, and 1380 rather than 1380.0. */
fun trimTrailingZeros(value: Double): String {
    val asLong = value.toLong()
    if (value == asLong.toDouble()) return asLong.toString()
    return java.math.BigDecimal(value)
        .setScale(10, java.math.RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

// ------------------------------------------------------------
//  A question
// ------------------------------------------------------------

@Composable
fun ConfirmSheet(
    fmt: Fmt,
    title: String,
    body: String,
    confirmLabel: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SheetHeader(title, onClose)
        Help(body, Modifier.padding(16.dp))
        SheetFooter {
            GhostButton(fmt.t("cancel"), Modifier.weight(1f), onClick = onClose)
            if (danger) {
                GhostButton(confirmLabel, Modifier.weight(1f), danger = true, onClick = onConfirm)
            } else {
                PrimaryButton(confirmLabel, Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

/** The palette swatches shared by the account and category editors. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColourRow(selected: String, onPick: (String) -> Unit) {
    val c = LocalTallyColors.current
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (name in COLORS) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.named(name))
                    .border(
                        2.dp,
                        if (name == selected) c.text else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onPick(name) }
            )
        }
    }
}

/** Currency codes with their names, for the pickers. */
fun currencyOptions(fmt: Fmt): List<Pair<String, String>> =
    Money.CODES.map { it to (it + " · " + fmt.currencyName(it)) }
