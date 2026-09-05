package com.hanifedma.tally.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.hanifedma.tally.R
import com.hanifedma.tally.core.AccountRow
import com.hanifedma.tally.core.BudgetRow
import com.hanifedma.tally.core.CategoryRow
import com.hanifedma.tally.core.Compute
import com.hanifedma.tally.core.Dates
import com.hanifedma.tally.core.Ids
import com.hanifedma.tally.core.Money
import com.hanifedma.tally.core.TransactionRow
import com.hanifedma.tally.data.LedgerRepository
import com.hanifedma.tally.data.Supabase
import com.hanifedma.tally.ui.components.Help
import com.hanifedma.tally.ui.components.Segmented
import com.hanifedma.tally.ui.screens.AccountEditorSheet
import com.hanifedma.tally.ui.screens.AccountPickerSheet
import com.hanifedma.tally.ui.screens.AccountsScreen
import com.hanifedma.tally.ui.screens.BudgetsSheet
import com.hanifedma.tally.ui.screens.CategoryEditorSheet
import com.hanifedma.tally.ui.screens.CategoryPickerSheet
import com.hanifedma.tally.ui.screens.Draft
import com.hanifedma.tally.ui.screens.EditorSheet
import com.hanifedma.tally.ui.screens.InsightsScreen
import com.hanifedma.tally.ui.screens.LogScreen
import com.hanifedma.tally.ui.screens.LoginScreen
import com.hanifedma.tally.ui.screens.ManageCategoriesSheet
import com.hanifedma.tally.ui.screens.PlainField
import com.hanifedma.tally.ui.screens.RateSheet
import com.hanifedma.tally.ui.screens.RatesSheet
import com.hanifedma.tally.ui.screens.SettingsSheet
import com.hanifedma.tally.ui.theme.LocalTallyColors
import com.hanifedma.tally.ui.theme.TallyTheme

/** Everything that can appear over the app, in the order it was opened. */
sealed interface Sheet {
    data class Editor(val draft: Draft, val existing: TransactionRow?) : Sheet
    data object Settings : Sheet
    data object Budgets : Sheet
    data object Rates : Sheet
    data class ManageCategories(val kind: String) : Sheet
    data class EditAccount(val account: AccountRow?) : Sheet
    data class EditCategory(val category: CategoryRow?, val kind: String) : Sheet
    data class PickCategory(
        val kind: String,
        val selected: String?,
        val onPick: (String) -> Unit,
    ) : Sheet
    data class PickAccount(val selected: String?, val onPick: (String) -> Unit) : Sheet
    data class EditRate(val code: String, val onPick: (Double) -> Unit) : Sheet
}

/**
 * A question asked before something irreversible.
 *
 * A dialog rather than another sheet: a sheet is a window of its own, and one
 * sheet cannot reliably ask a question on top of another. It is also the
 * right shape — this is not a place, it is a decision.
 */
data class Confirm(
    val title: String,
    val body: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TallyApp(vm: TallyViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val localTheme by vm.localTheme.collectAsStateWithLifecycle()
    val localLang by vm.localLang.collectAsStateWithLifecycle()

    // Collected, not read off the view model. See TallyViewModel.localTheme
    // for why a getter over StateFlow.value cannot drive a recomposition —
    // it is the whole reason the login screen's theme chip did nothing.
    val active = ui.signedIn || ui.local
    val lang = if (active) ledger.settings.lang else localLang
    val dark = (if (active) ledger.settings.theme else localTheme) != "light"

    TallyTheme(dark = dark) {
        val c = LocalTallyColors.current
        val fmt = remember(lang, ledger.settings.mainCurrency) {
            Fmt(lang, ledger.settings.mainCurrency)
        }
        val context = LocalContext.current
        val snackbars = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val stack = remember { mutableStateListOf<Sheet>() }
        var confirm by remember { mutableStateOf<Confirm?>(null) }

        fun push(sheet: Sheet) = stack.add(sheet)
        fun popTo(index: Int) { while (stack.size > index) stack.removeAt(stack.lastIndex) }
        fun popAll() = popTo(0)

        // Messages from the view model, shown once.
        LaunchedEffect(ui.message) {
            val key = ui.message ?: return@LaunchedEffect
            snackbars.showSnackbar(fmt.t(key))
            vm.clearMessage()
        }

        if (ui.booting) {
            Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
                Image(
                    painterResource(R.drawable.ic_tally_mark),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)),
                )
            }
            return@TallyTheme
        }
        // A ledger on this device needs neither a project nor an account, so
        // neither screen below is a dead end any more.
        if (!ui.signedIn && !ui.local) {
            // With its own host for the snackbar.
            //
            // The one below belongs to the Scaffold, which this return never
            // reaches — so every message raised here was handed to a host
            // nothing was drawing, and `showSnackbar` suspended for ever
            // waiting for a host to take it. Sign-in is the only thing that
            // *can* fail on this screen, which made every one of its failures
            // invisible: the button was pressed, Google refused, and the app
            // said nothing at all.
            Box(Modifier.fillMaxSize()) {
                if (!Supabase.isConfigured) SetupScreen(fmt, onUseLocal = { vm.useLocal() })
                else LoginScreen(vm, fmt, ui.signingIn, dark)
                SnackbarHost(
                    snackbars,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                )
            }
            return@TallyTheme
        }

        // Signing in after working on the device alone: offer to bring that
        // ledger along, once per account, and never without being asked.
        if (ui.offerMigration > 0) {
            AlertDialog(
                onDismissRequest = { vm.dismissMigration() },
                containerColor = c.surface,
                title = { Text(fmt.t("migrate.title"), color = c.text) },
                text = {
                    Text(
                        fmt.t(
                            "migrate.body",
                            mapOf("n" to fmt.t("migrate.count", mapOf("n" to ui.offerMigration))),
                        ),
                        color = c.muted,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.acceptMigration() }, enabled = !ui.migrating) {
                        Text(
                            if (ui.migrating) fmt.t("migrate.working") else fmt.t("migrate.yes"),
                            color = c.accent,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissMigration() }, enabled = !ui.migrating) {
                        Text(fmt.t("migrate.no"), color = c.muted)
                    }
                },
            )
        }

        val period = vm.period()
        val repo = vm.repository()

        /** Delete with an undo, because a mis-tap should cost one tap back. */
        fun deleteTransaction(tx: TransactionRow) {
            repo?.delete(tx)
            popAll()
            scope.launch {
                val result = snackbars.showSnackbar(
                    message = fmt.t("tx.deleted"),
                    actionLabel = fmt.t("tx.undo"),
                    duration = androidx.compose.material3.SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) repo?.restore(tx)
            }
        }

        Scaffold(
            containerColor = c.bg,
            snackbarHost = { SnackbarHost(snackbars) },
            topBar = {
                Column(
                    Modifier.background(c.bg).windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    TopBar(vm, fmt, ui, sync, dark, onSettings = { push(Sheet.Settings) })
                    if (ui.searching) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(Modifier.weight(1f)) {
                                PlainField(ui.search, fmt.t("search.placeholder")) { vm.setSearch(it) }
                            }
                            IconButton("✕") { vm.setSearching(false) }
                        }
                    } else {
                        PeriodBar(vm, fmt, period, ledger.settings.monthStart)
                        SummaryBar(fmt, Compute.totals(Compute.inPeriod(ledger.transactions, period), ledger.ctx))
                    }
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Segmented(
                            options = listOf(fmt.t("tab.log"), fmt.t("tab.insights"), fmt.t("tab.accounts")),
                            selected = when (ui.tab) {
                                TallyViewModel.Tab.LOG -> 0
                                TallyViewModel.Tab.INSIGHTS -> 1
                                TallyViewModel.Tab.ACCOUNTS -> 2
                            },
                        ) {
                            vm.selectTab(
                                when (it) {
                                    1 -> TallyViewModel.Tab.INSIGHTS
                                    2 -> TallyViewModel.Tab.ACCOUNTS
                                    else -> TallyViewModel.Tab.LOG
                                }
                            )
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                }
            },
            floatingActionButton = {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.accent)
                        .clickable {
                            val accounts = ledger.liveAccounts()
                            if (accounts.isEmpty()) {
                                push(Sheet.EditAccount(null))
                                return@clickable
                            }
                            push(Sheet.Editor(newDraft(ledger), null))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", color = c.accentContrast, fontSize = 26.sp)
                }
            },
        ) { padding ->
            // Room for the floating button *and* for whatever the system puts
            // at the bottom. Without the inset the last row of a list comes to
            // rest under the navigation bar, where it can be seen but not
            // read — and a gesture-navigation phone, which has almost no
            // inset, would never show the problem.
            val listPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 6.dp,
                bottom = 92.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            )
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                when (ui.tab) {
                    TallyViewModel.Tab.LOG -> LogScreen(
                        ledger, fmt, period, ui.searching, ui.search,
                        listPadding,
                        onOpen = { push(Sheet.Editor(Draft.from(it), it)) },
                        onAddFirst = { push(Sheet.Editor(newDraft(ledger), null)) },
                    )
                    TallyViewModel.Tab.INSIGHTS -> InsightsScreen(
                        ledger, fmt, period, ui.insightsIncome,
                        listPadding,
                        onShowIncome = { vm.setInsightsIncome(it) },
                        onEditBudgets = { push(Sheet.Budgets) },
                        onPickPeriod = { vm.goToPeriod(it) },
                    )
                    TallyViewModel.Tab.ACCOUNTS -> AccountsScreen(
                        ledger, fmt, ui.showArchivedAccounts,
                        listPadding,
                        onOpen = { push(Sheet.EditAccount(it)) },
                        onAdd = { push(Sheet.EditAccount(null)) },
                        onToggleArchived = { vm.setShowArchivedAccounts(!ui.showArchivedAccounts) },
                    )
                }
            }
        }

        // ---- sheets, stacked in the order they were opened ----
        stack.forEachIndexed { index, sheet ->
            key(index) {
                val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { popTo(index) },
                    sheetState = state,
                    containerColor = c.elevated,
                    contentColor = c.text,
                    dragHandle = null,
                ) {
                    SheetContent(
                        sheet = sheet,
                        vm = vm,
                        fmt = fmt,
                        ledger = ledger,
                        email = ui.account?.email,
                        onClose = { popTo(index) },
                        onCloseAll = { popAll() },
                        onPush = { push(it) },
                        onDeleteTransaction = { deleteTransaction(it) },
                        onConfirm = { confirm = it },
                        onSignOut = {
                            confirm = Confirm(
                                title = fmt.t("signout.confirm"),
                                body = fmt.t("signout.body"),
                                confirmLabel = fmt.t("signout"),
                            ) {
                                popAll()
                                vm.signOut(context)
                            }
                        },
                    )
                }
            }
        }

        // ---- and, above everything, the one question worth asking ----
        confirm?.let { ask ->
            AlertDialog(
                onDismissRequest = { confirm = null },
                containerColor = c.surface,
                title = { Text(ask.title, color = c.text) },
                text = { Text(ask.body, color = c.muted) },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = null
                        ask.onConfirm()
                    }) { Text(ask.confirmLabel, color = c.danger) }
                },
                dismissButton = {
                    TextButton(onClick = { confirm = null }) {
                        Text(fmt.t("cancel"), color = c.muted)
                    }
                },
            )
        }
    }
}

/** A blank entry, opened on the account and category last used. */
private fun newDraft(ledger: com.hanifedma.tally.core.Ledger): Draft {
    val accounts = ledger.liveAccounts()
    val last = ledger.transactions
        .filter { it.kind == "expense" && it.categoryId != null }
        .minWithOrNull(Compute.newestFirst)
    val account = last?.accountId?.let { id -> accounts.firstOrNull { it.id == id } }
        ?: accounts.firstOrNull()
    return Draft(
        id = Ids.random(),
        accountId = account?.id,
        currency = account?.currency ?: ledger.ctx.main,
        rate = Money.rateForNew(account?.currency ?: ledger.ctx.main, ledger.ctx),
        rateBase = ledger.ctx.main,
        categoryId = last?.categoryId,
    )
}

@Composable
private fun SheetContent(
    sheet: Sheet,
    vm: TallyViewModel,
    fmt: Fmt,
    ledger: com.hanifedma.tally.core.Ledger,
    email: String?,
    onClose: () -> Unit,
    onCloseAll: () -> Unit,
    onPush: (Sheet) -> Unit,
    onDeleteTransaction: (TransactionRow) -> Unit,
    onConfirm: (Confirm) -> Unit,
    onSignOut: () -> Unit,
) {
    val repo = vm.repository()
    val context = LocalContext.current

    when (sheet) {
        is Sheet.Editor -> EditorSheet(
            ledger = ledger,
            fmt = fmt,
            initial = sheet.draft,
            existing = sheet.existing,
            onPickCategory = { kind, selected, pick ->
                onPush(Sheet.PickCategory(kind, selected, pick))
            },
            onPickAccount = { selected, pick -> onPush(Sheet.PickAccount(selected, pick)) },
            onSetRate = { code, pick -> onPush(Sheet.EditRate(code, pick)) },
            onSave = { row, another ->
                repo?.put(row)
                // Follow the entry: saving something dated last month and
                // staying on this one looks as though nothing happened.
                if (row.occurredOn !in vm.period()) vm.goToPeriod(row.occurredOn)
                if (!another) onClose()
            },
            onDelete = onDeleteTransaction,
            onClose = onClose,
        )

        is Sheet.PickCategory -> CategoryPickerSheet(
            ledger, fmt, sheet.kind, sheet.selected,
            onPick = { sheet.onPick(it); onClose() },
            onManage = { onClose(); onPush(Sheet.ManageCategories(sheet.kind)) },
            onClose = onClose,
        )

        is Sheet.PickAccount -> AccountPickerSheet(
            ledger, fmt, sheet.selected,
            onPick = { sheet.onPick(it); onClose() },
            onAdd = { onClose(); onPush(Sheet.EditAccount(null)) },
            onClose = onClose,
        )

        is Sheet.EditRate -> RateSheet(
            fmt = fmt,
            code = sheet.code,
            main = ledger.ctx.main,
            current = ledger.ctx.rates[sheet.code],
            onSave = { rate ->
                vm.writeSettings(ledger.settings.withRates(ledger.ctx.rates + (sheet.code to rate)))
                sheet.onPick(rate)
                onClose()
            },
            onClose = onClose,
        )

        // Deleting asks first, and says what it costs: the transactions
        // survive, but they stop belonging to anything. Until this it deleted
        // on the first tap, which the web app has never done.
        is Sheet.EditAccount -> AccountEditorSheet(
            ledger, fmt, sheet.account,
            onSave = { repo?.put(it); onClose() },
            onDelete = { account ->
                val n = ledger.transactions.count {
                    it.accountId == account.id || it.toAccountId == account.id
                }
                onConfirm(
                    Confirm(
                        title = fmt.t("acc.deleteConfirm", mapOf("name" to account.name)),
                        body = fmt.t("acc.deleteBody", mapOf("n" to n)),
                        confirmLabel = fmt.t("delete"),
                    ) { repo?.delete(account); onClose() }
                )
            },
            onClose = onClose,
        )

        is Sheet.EditCategory -> CategoryEditorSheet(
            ledger, fmt, sheet.category, sheet.kind,
            onSave = { repo?.put(it); onClose() },
            onDelete = { category ->
                val n = ledger.transactions.count { it.categoryId == category.id }
                onConfirm(
                    Confirm(
                        title = fmt.t("cat.deleteConfirm", mapOf("name" to category.name)),
                        body = fmt.t("cat.deleteBody", mapOf("n" to n)),
                        confirmLabel = fmt.t("delete"),
                    ) { repo?.delete(category); onClose() }
                )
            },
            onClose = onClose,
        )

        is Sheet.ManageCategories -> ManageCategoriesSheet(
            ledger, fmt, sheet.kind,
            onEdit = { onPush(Sheet.EditCategory(it, it.kind)) },
            onAdd = { kind -> onPush(Sheet.EditCategory(null, kind)) },
            onReorder = { list -> list.forEach { repo?.put(it) } },
            onClose = onClose,
        )

        Sheet.Budgets -> BudgetsSheet(
            ledger, fmt,
            onSave = { keep, drop ->
                keep.forEach { repo?.put(it) }
                drop.forEach { repo?.delete(it) }
                onClose()
            },
            onClose = onClose,
        )

        Sheet.Rates -> RatesSheet(
            ledger, fmt,
            onSave = { rates ->
                vm.writeSettings(ledger.settings.withRates(rates))
                onClose()
            },
            onClose = onClose,
        )

        Sheet.Settings -> SettingsSheet(
            ledger = ledger,
            fmt = fmt,
            email = email,
            local = vm.ui.value.local,
            canSignIn = Supabase.isConfigured,
            onSignIn = {
                onCloseAll()
                vm.leaveLocal()
            },
            // The one action in the app that cannot be undone by any means.
            onErase = {
                onConfirm(
                    Confirm(
                        title = fmt.t("local.eraseConfirm"),
                        body = fmt.t("local.eraseBody"),
                        confirmLabel = fmt.t("local.erase"),
                    ) { onCloseAll(); vm.eraseLocal() }
                )
            },
            onSettings = { next ->
                // The starting accounts follow the main currency while the
                // ledger is still empty — what someone outside Korea wants,
                // but done on their behalf, so say it happened rather than
                // let them find it out later.
                val before = ledger.accounts.joinToString { it.id + it.currency }
                vm.writeSettings(next)
                val after = repo?.ledger?.value?.accounts?.joinToString { it.id + it.currency }
                if (after != null && after != before) {
                    vm.showMessage(
                        "set.accountsFollowed",
                        literal = fmt.t(
                            "set.accountsFollowed",
                            mapOf("code" to next.mainCurrency),
                        ),
                    )
                }
            },
            onManageCategories = { onPush(Sheet.ManageCategories("expense")) },
            onManageAccounts = {
                onCloseAll()
                vm.selectTab(TallyViewModel.Tab.ACCOUNTS)
            },
            onBudgets = { onPush(Sheet.Budgets) },
            onRates = { onPush(Sheet.Rates) },
            onExport = { exportCsv(context, ledger, fmt, vm) },
            onSignOut = onSignOut,
            onClose = onClose,
        )
    }
}

/** Write the ledger to a file and hand it to whatever can open it. */
private fun exportCsv(
    context: android.content.Context,
    ledger: com.hanifedma.tally.core.Ledger,
    fmt: Fmt,
    vm: TallyViewModel,
) {
    try {
        val csv = Compute.toCsv(ledger.transactions, ledger.accounts, ledger.categories, ledger.ctx)
        val dir = java.io.File(context.cacheDir, "export").apply { mkdirs() }
        val file = java.io.File(dir, "tally-" + Dates.today() + ".csv")
        file.writeText(csv)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, context.packageName + ".files", file
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, fmt.t("set.export"))
        )
    } catch (e: Exception) {
        android.util.Log.e("Tally", "Export failed", e)
        vm.showMessage("err.generic", isError = true)
    }
}

// ------------------------------------------------------------
//  Chrome
// ------------------------------------------------------------

@Composable
private fun TopBar(
    vm: TallyViewModel,
    fmt: Fmt,
    ui: TallyViewModel.UiState,
    sync: LedgerRepository.Sync,
    dark: Boolean,
    onSettings: () -> Unit,
) {
    val c = LocalTallyColors.current
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painterResource(R.drawable.ic_tally_mark),
            contentDescription = null,
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Tally",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = c.text,
        )
        Spacer(Modifier.width(6.dp))
        SyncChip(fmt, sync)
        Spacer(Modifier.weight(1f))
        IconButton("⌕") { vm.setSearching(true) }
        // U+FE0E asks for the text presentation of these two. Without it a
        // Samsung draws a full-colour emoji sun in the middle of a row of
        // monochrome glyphs, which reads as a mistake because it is one.
        IconButton(if (dark) "☀︎" else "☾︎") { vm.toggleTheme() }
        IconButton(if (fmt.lang == "ko") "EN" else "KO", small = true) { vm.toggleLang() }
        val avatar = ui.account?.avatarUrl
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(c.surface3)
                    .clickable(onClick = onSettings),
            )
        } else {
            IconButton("☰", onClick = onSettings)
        }
    }
}

@Composable
private fun SyncChip(fmt: Fmt, sync: LedgerRepository.Sync) {
    val c = LocalTallyColors.current
    if (sync.status == LedgerRepository.Status.LIVE && sync.pending == 0) return
    val (label, dot) = when {
        // Not a problem to fix, so it does not read like one — but it is the
        // one fact about this ledger nobody should be surprised by.
        sync.status == LedgerRepository.Status.LOCAL -> fmt.t("local.status") to c.faint
        sync.status == LedgerRepository.Status.OFFLINE -> fmt.t("sync.offline") to c.faint
        sync.status == LedgerRepository.Status.ERROR -> fmt.t("sync.reconnecting") to c.danger
        sync.pending > 0 -> fmt.t("sync.pending", mapOf("n" to sync.pending)) to c.warn
        else -> fmt.t("sync.syncing") to c.warn
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(c.surface2)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Text(label, style = MaterialTheme.typography.labelSmall, color = c.muted, maxLines = 1)
    }
}

@Composable
private fun IconButton(glyph: String, small: Boolean = false, onClick: () -> Unit) {
    val c = LocalTallyColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 8.dp else 7.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = c.muted,
            fontSize = if (small) 12.sp else 17.sp,
            fontWeight = if (small) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PeriodBar(vm: TallyViewModel, fmt: Fmt, period: Dates.Period, monthStart: Int) {
    val c = LocalTallyColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton("‹") { vm.shiftPeriod(-1) }
        Column(
            Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable { vm.goToToday() }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                fmt.monthYear(period.start),
                style = MaterialTheme.typography.titleLarge,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (monthStart != 1) {
                Text(
                    fmt.t(
                        "sum.periodRange",
                        mapOf("start" to fmt.dayShort(period.start), "end" to fmt.dayShort(period.end)),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.faint,
                    maxLines = 1,
                )
            }
        }
        IconButton("›") { vm.shiftPeriod(1) }
    }
}

@Composable
private fun SummaryBar(fmt: Fmt, totals: Compute.Totals) {
    val c = LocalTallyColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SummaryCell(fmt.t("sum.income"), fmt.money(totals.income), c.income, Modifier.weight(1f))
        SummaryCell(fmt.t("sum.expenses"), fmt.money(totals.expense), c.expense, Modifier.weight(1f))
        SummaryCell(
            fmt.t("sum.net"),
            fmt.money(totals.net, sign = Money.Sign.ALWAYS),
            if (totals.net < 0) c.expense else c.text,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    colour: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val c = LocalTallyColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(java.util.Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            color = c.faint,
            maxLines = 1,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shown when supabase.properties is still holding its placeholders. */
@Composable
private fun SetupScreen(fmt: Fmt, onUseLocal: () -> Unit) {
    val c = LocalTallyColors.current
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painterResource(R.drawable.ic_tally_mark),
            contentDescription = null,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)),
        )
        Spacer(Modifier.height(18.dp))
        Text(fmt.t("setup.h1"), style = MaterialTheme.typography.headlineSmall, color = c.text)
        Spacer(Modifier.height(10.dp))
        Help(fmt.t("setup.p1"))
        Spacer(Modifier.height(8.dp))
        Help(fmt.t("setup.p2"))
        Spacer(Modifier.height(18.dp))
        if (!Supabase.hasUrl) MissingRow(fmt.t("setup.missingUrl"))
        if (!Supabase.hasKey) MissingRow(fmt.t("setup.missingKey"))
        if (!Supabase.hasGoogleClientId) MissingRow(fmt.t("setup.missingClient"))

        // None of the above is needed to keep a ledger on this phone, so this
        // is a place to start rather than only a place to wait.
        Spacer(Modifier.height(20.dp))
        PrimaryButton(fmt.t("setup.tryLocal"), onClick = onUseLocal)
        Spacer(Modifier.height(10.dp))
        Help(fmt.t("setup.tryLocalHelp"))
    }
}

/** A filled button in the accent, used where there is one obvious action. */
@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    val c = LocalTallyColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = c.accentContrast,
        )
    }
}

@Composable
private fun MissingRow(text: String) {
    val c = LocalTallyColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = c.warn,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.warnSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
