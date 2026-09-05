package com.hanifedma.tally.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.tally.ui.theme.LocalTallyColors

/**
 * The handful of shapes the whole app is built from. Each one exists because
 * it appears on at least two screens and had started to drift between them.
 */

/** A bordered panel — the phone's version of the web app's `.card`. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = LocalTallyColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(18.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
fun CardHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalTallyColors.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = c.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/** The round tile that carries a category emoji or an account glyph. */
@Composable
fun IconChip(
    glyph: String,
    tint: Color? = null,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 17.sp,
) {
    val c = LocalTallyColors.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint?.copy(alpha = 0.20f) ?: c.surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = fontSize, maxLines = 1)
    }
}

/**
 * A segmented control. Material3 has one, but its shape and its ripple do not
 * match the web app's, and these two screens sit side by side in a folder.
 */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    tints: List<Color?> = emptyList(),
    onSelect: (Int) -> Unit,
) {
    val c = LocalTallyColors.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(c.surface2)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            val tint = tints.getOrNull(index)
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) c.segOn else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = when {
                        active && tint != null -> tint
                        active -> c.text
                        else -> c.muted
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** A progress bar with an optional mark for how far through the month it is. */
@Composable
fun ProgressBar(ratio: Float, color: Color, pace: Float? = null) {
    val c = LocalTallyColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(color)
        )
        if (pace != null && pace > 0.02f && pace < 0.98f) {
            // The difference between "spent 60%" and "spent 60% on the 5th".
            Box(
                Modifier
                    .fillMaxWidth(pace)
                    .height(8.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(c.text.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/** A hairline between rows in a list. */
@Composable
fun Divider(inset: androidx.compose.ui.unit.Dp = 0.dp) {
    val c = LocalTallyColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(1.dp)
            .background(c.border)
    )
}

/** A label above a field, in the same voice as the web app's `.field-label`. */
@Composable
fun FieldLabel(text: String) {
    val c = LocalTallyColors.current
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = c.muted,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
fun Help(text: String, modifier: Modifier = Modifier) {
    val c = LocalTallyColors.current
    Text(text, style = MaterialTheme.typography.labelMedium, color = c.faint, modifier = modifier)
}

/** The centred "nothing here yet" state, with an optional way out of it. */
@Composable
fun EmptyState(
    glyph: String,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = LocalTallyColors.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, fontSize = 34.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = c.muted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            PrimaryButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalTallyColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) c.accent else c.surface3)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) c.accentContrast else c.faint,
            maxLines = 1,
        )
    }
}

@Composable
fun GhostButton(
    label: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalTallyColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (danger) c.dangerSoft else Color.Transparent)
            .border(1.dp, if (danger) Color.Transparent else c.borderStrong, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) c.danger else c.text,
            maxLines = 1,
        )
    }
}

/** A tappable row that opens something else: label on the left, chevron right. */
@Composable
fun PickerRow(
    label: String,
    value: String,
    placeholder: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    invalid: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalTallyColors.current
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
        FieldLabel(label)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface2)
                .border(1.dp, if (invalid) c.danger else c.border, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading?.invoke()
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = if (placeholder) c.faint else c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("›", color = c.faint, fontSize = 18.sp)
        }
    }
}
