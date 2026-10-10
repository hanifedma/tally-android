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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/**
 * The room a dialog can really have: the window, less what the system keeps
 * of it for its own bars and for the hole the camera looks through.
 *
 * `Configuration.screenHeightDp` used to be this number and, for an app
 * built for Android 15 or later, no longer is: it now counts the status bar
 * and the navigation bar as room, which is up to 72dp of it that a dialog
 * cannot use. On a 640dp phone that was the whole difference between a
 * calendar that fits and one that does not, and a picker told it fits does
 * not scroll and does not shrink — see what it does instead, below.
 *
 * So the window is measured and the bars taken off it. The old number is
 * still consulted and the smaller of the two believed, since each is only
 * ever wrong by being too generous — the configuration on a new phone, the
 * window on a very old one sharing its screen with another app.
 */
@Composable
private fun dialogRoom(): DpSize {
    val configuration = LocalConfiguration.current
    val window = LocalWindowInfo.current.containerSize
    val kept = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val direction = LocalLayoutDirection.current
    return with(LocalDensity.current) {
        var width = configuration.screenWidthDp.dp
        var height = configuration.screenHeightDp.dp
        if (window.width > 0 && window.height > 0) {
            val across = window.width - kept.getLeft(this, direction) - kept.getRight(this, direction)
            val down = window.height - kept.getTop(this) - kept.getBottom(this)
            width = minOf(width, across.toDp())
            height = minOf(height, down.toDp())
        }
        DpSize(width.coerceAtLeast(0.dp), height.coerceAtLeast(0.dp))
    }
}

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
    val window = dialogRoom()
    val fit = minOf(1f, (window.width - 16.dp) / DIALOG_WIDTH)

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
    val room = window.height
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

/** What a dialog leaves between itself and the edge of the window. */
private val DIALOG_EDGE = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerSheet(initialMinutes: Int, fmt: Fmt, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = LocalTallyColors.current
    val start = initialMinutes.coerceIn(0, 24 * 60 - 1)
    val state = rememberTimePickerState(
        initialHour = start / 60,
        initialMinute = start % 60,
        // Counted the way the field behind this writes it — see Fmt.uses24Hour.
        // This used to follow the language: twelve hours in English, twenty-
        // four in Korean. The field follows the phone's own setting, so on a
        // phone set to 24 hours an English picker offered AM and PM for a
        // time shown as 22:41, and a Korean one did the reverse.
        is24Hour = fmt.uses24Hour,
    )

    // Not an AlertDialog. That is as wide as the platform says a dialog is —
    // 360dp of content on a large tablet, less on a phone — and has no way
    // to be told that what is inside it is wider. See ClockFit for what that
    // did to the clock. A plain Dialog takes the size of what is put in it,
    // so here the picker decides and the dialog follows.
    val room = dialogRoom().let {
        DpSize(
            (it.width - DIALOG_EDGE * 2).coerceAtLeast(0.dp),
            (it.height - DIALOG_EDGE * 2).coerceAtLeast(0.dp),
        )
    }
    val clock = ClockFit.choose(room.width.value, room.height.value, state.is24hour)

    // Typed rather than turned: because that was asked for, or because this
    // window has no room for a clock worth turning. Only the first is a
    // choice, so only then is there a button to take it back.
    var asked by rememberSaveable { mutableStateOf(false) }
    val typed = asked || clock == null
    val sideways = clock?.takeIf { !typed && it.shape == ClockFit.Shape.Wide }

    // The dial and the boxes the time sits in are, by default, the same grey
    // as the dialog they are drawn on, which leaves a ring of numbers floating
    // on nothing. And the selected half of AM/PM comes out pink, because it
    // is painted in a tertiary colour this app has never set. So every colour
    // here is Tally's own, and none is left to a default.
    val colors = TimePickerDefaults.colors(
        clockDialColor = c.bg,
        clockDialSelectedContentColor = c.accentContrast,
        clockDialUnselectedContentColor = c.text,
        selectorColor = c.accent,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        periodSelectorBorderColor = c.borderStrong,
        periodSelectorSelectedContainerColor = c.accentSoft,
        periodSelectorUnselectedContainerColor = Color.Transparent,
        periodSelectorSelectedContentColor = c.accent,
        periodSelectorUnselectedContentColor = c.muted,
        timeSelectorSelectedContainerColor = c.accentSoft,
        timeSelectorUnselectedContainerColor = c.bg,
        timeSelectorSelectedContentColor = c.accent,
        timeSelectorUnselectedContentColor = c.text,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        FitInside(room.width, room.height) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                // The same surface as the calendar this opens beside.
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = c.text,
            ) {
                PickerFrame(
                    sideways = sideways != null,
                    picker = {
                        when {
                            typed -> TimeInput(state = state, colors = colors)
                            sideways != null -> TimePicker(
                                state = state,
                                // Its dial is as big as the height it is
                                // offered allows, and this is how it is told
                                // which of the three fits.
                                modifier = Modifier.heightIn(max = sideways.offer.dp),
                                colors = colors,
                                layoutType = TimePickerLayoutType.Horizontal,
                            )
                            else -> TimePicker(
                                state = state,
                                colors = colors,
                                layoutType = TimePickerLayoutType.Vertical,
                            )
                        }
                    },
                    buttons = {
                        if (clock != null) {
                            CompositionLocalProvider(LocalContentColor provides c.muted) {
                                TypeOrTurn(
                                    typed = typed,
                                    onToggle = { asked = !asked },
                                    // The button is 48dp for the finger and
                                    // its picture 24dp for the eye. Moved out
                                    // by the difference, the picture lines up
                                    // with the edge of the clock above it.
                                    modifier = Modifier.offset(x = (-12).dp),
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text(fmt.t("cancel")) }
                        TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) {
                            Text(fmt.t("done"))
                        }
                    },
                )
            }
        }
    }
}

/**
 * Swaps the clock for two boxes to type in, and back again.
 *
 * Material has this button ready-made, and it is not used, because it
 * introduces itself backwards: showing a keyboard, it tells a screen reader
 * "switch to clock mode", and showing a clock it says "switch to text
 * input". The pictures are right and the words are the wrong way round —
 * which nobody looking at it would ever find, and somebody listening to it
 * could not get past. The words are still Material's own, so that they are
 * in the same language as every other label in its picker; they are just
 * attached to the right picture.
 */
@Composable
private fun TypeOrTurn(typed: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (typed) Icons.Outlined.Schedule else Icons.Outlined.Keyboard,
            contentDescription = stringResource(
                if (typed) {
                    androidx.compose.material3.R.string.m3c_time_picker_toggle_touch
                } else {
                    androidx.compose.material3.R.string.m3c_time_picker_toggle_keyboard
                }
            ),
        )
    }
}

/**
 * A picker, and a row of buttons under it exactly as wide.
 *
 * A layout of its own because nothing off the shelf does the one thing
 * wanted: let the picker be whatever size it is, and only then size the row
 * to match. A Column would need to be told a width, and the only width
 * anyone could tell it is a number copied out of the picker's source.
 *
 * @param sideways the clock with the time beside it, which is for a window
 *   short of height and so is given less room above and below.
 */
@Composable
private fun PickerFrame(
    sideways: Boolean,
    picker: @Composable () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
) {
    Layout(
        contents = listOf(
            { Box { picker() } },
            { Row(verticalAlignment = Alignment.CenterVertically, content = buttons) },
        ),
    ) { (pickers, rows), _ ->
        val side = ClockFit.SIDE.dp.roundToPx()
        val head = (if (sideways) ClockFit.WIDE_HEAD else ClockFit.TALL_HEAD).dp.roundToPx()
        // The upright clock and the typed fields both end in room of their
        // own; the sideways clock ends at the bottom of its dial.
        val gap = (if (sideways) ClockFit.WIDE_GAP else 0).dp.roundToPx()
        val tail = (if (sideways) ClockFit.WIDE_TAIL else ClockFit.TALL_TAIL).dp.roundToPx()

        val body = pickers.first().measure(Constraints())
        // As wide as the picker — or as the buttons, should they ever need
        // more: two words at the largest text size beside the narrow,
        // 24-hour fields is the one case where they do.
        val row = rows.first()
        val width = maxOf(body.width, row.maxIntrinsicWidth(Constraints.Infinity))
        val foot = row.measure(Constraints.fixedWidth(width))

        layout(width + side * 2, head + body.height + gap + foot.height + tail) {
            body.place(side + (width - body.width) / 2, head)
            foot.place(side, head + body.height + gap)
        }
    }
}

/**
 * Draws [content] at the size it wants, or as much smaller as it takes to
 * fit in [width] by [height].
 *
 * The last line of defence, and on most screens it does nothing at all. The
 * pickers are fixed sizes that do not reflow, so on a window smaller than
 * one of them — a small phone, a large display-size setting, a pop-up window
 * dragged down to a corner — the choice is between drawing the whole thing a
 * little smaller and drawing most of it at full size with the rest cut off.
 * Compose maps touches through the transform, so everything stays where it
 * looks.
 */
@Composable
private fun FitInside(width: Dp, height: Dp, content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val body = measurables.first().measure(Constraints())
        val widest = minOf(width.roundToPx(), constraints.maxWidth).coerceAtLeast(0)
        val tallest = minOf(height.roundToPx(), constraints.maxHeight).coerceAtLeast(0)
        val scale = if (body.width == 0 || body.height == 0) 1f else {
            minOf(1f, widest.toFloat() / body.width, tallest.toFloat() / body.height)
        }
        layout((body.width * scale).toInt(), (body.height * scale).toInt()) {
            if (scale == 1f) body.place(0, 0) else body.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
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
    onUnsaved: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalTallyColors.current
    var text by remember { mutableStateOf(current?.let { trimTrailingZeros(it) } ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    val opened = remember { text }
    val unsaved = text.trim() != opened.trim()
    LaunchedEffect(unsaved) { onUnsaved(unsaved) }

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
