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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanifedma.tally.BuildConfig
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.SettingsRow
import com.hanifedma.tally.ui.Fmt
import com.hanifedma.tally.ui.components.FieldLabel
import com.hanifedma.tally.ui.components.GhostButton
import com.hanifedma.tally.ui.components.Help
import com.hanifedma.tally.ui.components.Segmented
import com.hanifedma.tally.ui.theme.LocalTallyColors

/**
 * Everything that is a preference rather than a transaction.
 *
 * Theme and language sit at the top because they are the two anyone changes
 * twice; the account and its sign-out sit at the bottom because they are the
 * two nobody wants to hit by accident.
 */
@Composable
fun SettingsSheet(
    ledger: Ledger,
    fmt: Fmt,
    email: String?,
    /** True when this ledger is on this device only. */
    local: Boolean,
    /** False when there is no project configured to sign in to. */
    canSignIn: Boolean,
    onSignIn: () -> Unit,
    onErase: () -> Unit,
    onSettings: (SettingsRow) -> Unit,
    onManageCategories: () -> Unit,
    onManageAccounts: () -> Unit,
    onBudgets: () -> Unit,
    onRates: () -> Unit,
    onExport: () -> Unit,
    onSignOut: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalTallyColors.current
    val s = ledger.settings

    Column(Modifier.fillMaxWidth()) {
        SheetHeader(fmt.t("set.title"), onClose)
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Section(fmt.t("set.appearance"))
            FieldLabel(fmt.t("set.theme"))
            Segmented(
                options = listOf(fmt.t("set.theme.dark"), fmt.t("set.theme.light")),
                selected = if (s.theme == "light") 1 else 0,
            ) { onSettings(s.copy(theme = if (it == 1) "light" else "dark")) }
            Spacer(Modifier.height(14.dp))
            FieldLabel(fmt.t("set.language"))
            Segmented(
                options = listOf("English", "한국어"),
                selected = if (s.lang == "ko") 1 else 0,
            ) { onSettings(s.copy(lang = if (it == 1) "ko" else "en")) }

            Section(fmt.t("set.money"))
            FieldLabel(fmt.t("set.mainCurrency"))
            DropdownField(s.mainCurrency, currencyOptions(fmt)) {
                onSettings(s.copy(mainCurrency = it))
            }
            Help(fmt.t("set.mainCurrencyHelp"), Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))

            LinkRow(
                "💱",
                fmt.t("set.rates"),
                s.rateMap.keys.joinToString(", ").ifEmpty { fmt.t("none") },
                onRates,
            )
            Spacer(Modifier.height(14.dp))

            FieldLabel(fmt.t("set.monthStart"))
            DropdownField((1..28).first { it == s.monthStart }.toString(),
                (1..28).map { it.toString() to it.toString() }) {
                onSettings(s.copy(monthStart = it.toIntOrNull() ?: 1))
            }
            Help(fmt.t("set.monthStartHelp"), Modifier.padding(top = 6.dp))

            Section(fmt.t("set.data"))
            LinkRow("🏷", fmt.t("set.categories"), null, onManageCategories)
            Spacer(Modifier.height(8.dp))
            LinkRow("👛", fmt.t("set.accounts"), null, onManageAccounts)
            Spacer(Modifier.height(8.dp))
            LinkRow("🎯", fmt.t("bud.title"), null, onBudgets)
            Spacer(Modifier.height(8.dp))
            LinkRow("📄", fmt.t("set.export"), fmt.t("set.exportHelp"), onExport)

            if (local) {
                Section(fmt.t("local.title"))
                Help(fmt.t("local.help"))
                if (canSignIn) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton(fmt.t("local.signIn"), Modifier.fillMaxWidth(), onClick = onSignIn)
                    Help(fmt.t("local.signInHelp"), Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.height(10.dp))
                // Marked destructive: it sits in exactly the place "Sign out"
                // does for a signed-in account, and erasing a ledger for ever
                // must not look identical to leaving one behind.
                GhostButton(
                    fmt.t("local.erase"),
                    Modifier.fillMaxWidth(),
                    danger = true,
                    onClick = onErase,
                )
            } else {
                Section(fmt.t("set.account"))
                if (email != null) {
                    Help(fmt.t("set.signedInAs", mapOf("email" to email)))
                    Spacer(Modifier.height(10.dp))
                }
                GhostButton(fmt.t("signout"), Modifier.fillMaxWidth(), onClick = onSignOut)
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Tally · " + fmt.t("set.version", mapOf("v" to BuildConfig.VERSION_NAME)),
                style = MaterialTheme.typography.labelMedium,
                color = c.faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    val c = LocalTallyColors.current
    Text(
        title.uppercase(java.util.Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        color = c.faint,
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun LinkRow(glyph: String, label: String, sublabel: String?, onClick: () -> Unit) {
    val c = LocalTallyColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .border(1.dp, c.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(glyph, fontSize = 16.sp)
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sublabel != null) {
                Text(
                    sublabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text("›", color = c.faint, fontSize = 18.sp)
    }
}
