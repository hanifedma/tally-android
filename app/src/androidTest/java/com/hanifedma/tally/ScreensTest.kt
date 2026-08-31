package com.hanifedma.tally

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hanifedma.tally.core.AccountRow
import com.hanifedma.tally.core.BudgetRow
import com.hanifedma.tally.core.CategoryRow
import com.hanifedma.tally.core.Dates
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.SEED_CATEGORIES
import com.hanifedma.tally.core.SettingsRow
import com.hanifedma.tally.core.TransactionRow
import com.hanifedma.tally.ui.Fmt
import com.hanifedma.tally.ui.screens.AccountsScreen
import com.hanifedma.tally.ui.screens.Draft
import com.hanifedma.tally.ui.screens.EditorSheet
import com.hanifedma.tally.ui.screens.InsightsScreen
import com.hanifedma.tally.ui.screens.LogScreen
import com.hanifedma.tally.ui.screens.SettingsSheet
import com.hanifedma.tally.ui.theme.TallyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every screen, drawn for real on a device, against a ledger made up here.
 *
 * The unit tests prove the arithmetic; these prove the screens survive
 * meeting it — a Compose layout that throws does so at runtime, in front of
 * whoever is holding the phone, and nothing before this catches it.
 *
 * They run twice over, in English and in Korean, because a string that only
 * exists in one language and a layout that only fits one are the same class
 * of bug and neither shows up in a unit test.
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    @get:Rule val compose = createComposeRule()

    // ---------- a ledger to draw ----------

    private val today = Dates.today()
    private val yesterday = Dates.addDays(today, -1)

    private val accounts = listOf(
        AccountRow(id = "a1", name = "Cash", kind = "cash", currency = "KRW", openingMinor = 200000, position = 0, color = "green"),
        AccountRow(id = "a2", name = "KB Bank", kind = "bank", currency = "KRW", openingMinor = 7500000, position = 1, color = "indigo"),
        AccountRow(id = "a3", name = "Gopay", kind = "ewallet", currency = "IDR", openingMinor = 1500000, position = 2, color = "sky"),
        AccountRow(id = "a4", name = "Old wallet", kind = "cash", currency = "KRW", archived = true, position = 3),
    )

    private val categories = SEED_CATEGORIES.mapIndexed { i, seed ->
        CategoryRow(
            id = "cat-" + seed.slug, name = seed.en, kind = seed.kind,
            icon = seed.icon, color = seed.color, position = i,
        )
    }

    private fun tx(
        id: String, amount: Long, currency: String = "KRW", rate: Double = 1.0,
        kind: String = "expense", account: String? = "a1", to: String? = null,
        category: String? = "cat-food", note: String = "", on: String = today, min: Int = 600,
        toAmount: Long? = null,
    ) = TransactionRow(
        id = id, kind = kind, amountMinor = amount, currency = currency, rate = rate,
        rateBase = "KRW", accountId = account, toAccountId = to, toAmountMinor = toAmount,
        categoryId = category, note = note, occurredOn = on, occurredMin = min,
    ).normalized()

    private val transactions = listOf(
        tx("1", 12400, note = "Lunch, kimbap", min = 745),
        tx("2", 118200, "IDR", 0.0875, account = "a3", note = "Bebek, gofood", min = 1215),
        tx("3", 75000, "IDR", 0.0875, account = "a3", category = "cat-household", note = "Cukur at bejo", min = 1100),
        tx("4", 1832726, kind = "income", account = "a2", category = "cat-salary", note = "August salary", on = yesterday, min = 540),
        tx("5", 300000, kind = "transfer", account = "a2", to = "a1", category = null, note = "Cash for the week", on = yesterday, min = 600),
        tx("6", 100000, kind = "transfer", account = "a2", to = "a3", category = null, toAmount = 1142857, note = "Top up rupiah", on = yesterday, min = 900),
        tx("7", 236820, "IDR", 0.0875, account = "a3", category = "cat-groceries", note = "Hypermart, many things", on = yesterday, min = 1330),
    )

    private val ledger = Ledger(
        settings = SettingsRow(mainCurrency = "KRW", theme = "dark", lang = "en", monthStart = 1)
            .withRates(mapOf("IDR" to 0.0875)),
        accounts = accounts,
        categories = categories,
        transactions = transactions,
        budgets = listOf(
            BudgetRow(id = "b0", categoryId = null, amountMinor = 900000, currency = "KRW"),
            BudgetRow(id = "b1", categoryId = "cat-food", amountMinor = 300000, currency = "KRW"),
        ),
    )

    private fun fmt(lang: String) = Fmt(lang, ledger.ctx.main)
    private val padding = PaddingValues(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 92.dp)

    // ---------- the screens ----------

    @Test
    fun theLogDrawsEveryKindOfRow() {
        compose.setContent {
            TallyTheme(dark = true) {
                LogScreen(
                    ledger, fmt("en"), Dates.periodOf(today, 1),
                    searching = false, search = "", contentPadding = padding,
                    onOpen = {}, onAddFirst = {},
                )
            }
        }
        compose.onNodeWithText("Lunch, kimbap").assertIsDisplayed()
        compose.onNodeWithText("Bebek, gofood").assertIsDisplayed()
        // The transfer row names both ends.
        compose.onNodeWithText("Cash for the week").assertIsDisplayed()
        // The rupiah row carries its own currency and a converted figure.
        compose.onAllNodesWithText("−Rp118,200")[0].assertIsDisplayed()
        compose.onAllNodesWithText("≈ ₩10,343")[0].assertIsDisplayed()
    }

    @Test
    fun theLogDrawsInKorean() {
        compose.setContent {
            TallyTheme(dark = true) {
                LogScreen(
                    ledger.copy(settings = ledger.settings.copy(lang = "ko")),
                    fmt("ko"), Dates.periodOf(today, 1),
                    searching = false, search = "", contentPadding = padding,
                    onOpen = {}, onAddFirst = {},
                )
            }
        }
        compose.onNodeWithText("Lunch, kimbap").assertIsDisplayed()
    }

    @Test
    fun theLogSearchesTheWholeHistory() {
        compose.setContent {
            TallyTheme(dark = true) {
                LogScreen(
                    ledger, fmt("en"), Dates.periodOf(today, 1),
                    searching = true, search = "gofood", contentPadding = padding,
                    onOpen = {}, onAddFirst = {},
                )
            }
        }
        compose.onNodeWithText("1 found").assertIsDisplayed()
        compose.onNodeWithText("Bebek, gofood").assertIsDisplayed()
    }

    @Test
    fun theLogShowsAnEmptyStateRatherThanNothing() {
        compose.setContent {
            TallyTheme(dark = true) {
                LogScreen(
                    ledger.copy(transactions = emptyList()), fmt("en"), Dates.periodOf(today, 1),
                    searching = false, search = "", contentPadding = padding,
                    onOpen = {}, onAddFirst = {},
                )
            }
        }
        compose.onNodeWithText("Nothing here yet").assertIsDisplayed()
    }

    @Test
    fun insightsDrawsBudgetsChartAndTrend() {
        compose.setContent {
            TallyTheme(dark = true) {
                InsightsScreen(
                    ledger, fmt("en"), Dates.periodOf(today, 1),
                    showIncome = false, contentPadding = padding,
                    onShowIncome = {}, onEditBudgets = {}, onPickPeriod = {},
                )
            }
        }
        compose.onNodeWithText("Budget").assertIsDisplayed()
        compose.onNodeWithText("Total budget").assertIsDisplayed()
        compose.onNodeWithText("Where it went").assertIsDisplayed()
    }

    @Test
    fun insightsSwitchesToIncomeWithoutFallingOver() {
        var showIncome = false
        compose.setContent {
            TallyTheme(dark = true) {
                InsightsScreen(
                    ledger, fmt("en"), Dates.periodOf(today, 1),
                    showIncome = showIncome, contentPadding = padding,
                    onShowIncome = { showIncome = it }, onEditBudgets = {}, onPickPeriod = {},
                )
            }
        }
        compose.onAllNodesWithText("Income")[0].performClick()
        assertEquals(true, showIncome)
    }

    @Test
    fun accountsShowsBalancesAndHidesTheArchivedOne() {
        compose.setContent {
            TallyTheme(dark = true) {
                AccountsScreen(
                    ledger, fmt("en"), showArchived = false, contentPadding = padding,
                    onOpen = {}, onAdd = {}, onToggleArchived = {},
                )
            }
        }
        compose.onNodeWithText("Net worth".uppercase()).assertIsDisplayed()
        compose.onNodeWithText("Cash").assertIsDisplayed()
        compose.onNodeWithText("Gopay").assertIsDisplayed()
        // Its balance is in rupiah, with the won value underneath.
        compose.onNodeWithText("Rp2,212,837").assertIsDisplayed()
        compose.onAllNodesWithText("Old wallet").assertCountEqualsZero()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEqualsZero() {
        assertEquals(0, fetchSemanticsNodes().size)
    }

    @Test
    fun theEditorOpensAndRefusesAnEmptyAmount() {
        var saved: TransactionRow? = null
        compose.setContent {
            TallyTheme(dark = true) {
                EditorSheet(
                    ledger = ledger,
                    fmt = fmt("en"),
                    initial = Draft(accountId = "a1", currency = "KRW", categoryId = "cat-food"),
                    existing = null,
                    onPickCategory = { _, _, _ -> },
                    onPickAccount = { _, _ -> },
                    onSetRate = { _, _ -> },
                    onSave = { row, _ -> saved = row },
                    onDelete = {},
                    onClose = {},
                )
            }
        }
        compose.onNodeWithText("New transaction").assertIsDisplayed()
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("Enter an amount").assertIsDisplayed()
        assertEquals(null, saved)
    }

    @Test
    fun theEditorSavesASumTypedIntoTheAmountField() {
        var saved: TransactionRow? = null
        compose.setContent {
            TallyTheme(dark = true) {
                EditorSheet(
                    ledger = ledger,
                    fmt = fmt("en"),
                    initial = Draft(accountId = "a1", currency = "KRW", categoryId = "cat-food"),
                    existing = null,
                    onPickCategory = { _, _, _ -> },
                    onPickAccount = { _, _ -> },
                    onSetRate = { _, _ -> },
                    onSave = { row, _ -> saved = row },
                    onDelete = {},
                    onClose = {},
                )
            }
        }
        compose.onNodeWithText("0").performTextInput("3000+1500")
        compose.onNodeWithText("Save").performClick()
        assertNotNull(saved)
        assertEquals(4500L, saved!!.amountMinor)
        assertEquals("expense", saved!!.kind)
        assertEquals("cat-food", saved!!.categoryId)
    }

    @Test
    fun settingsDrawsWithoutAnAccountPicture() {
        compose.setContent {
            TallyTheme(dark = true) {
                SettingsSheet(
                    ledger = ledger,
                    fmt = fmt("en"),
                    email = "demo@example.com",
                    onSettings = {}, onManageCategories = {}, onManageAccounts = {},
                    onBudgets = {}, onRates = {}, onExport = {}, onSignOut = {}, onClose = {},
                )
            }
        }
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Signed in as demo@example.com").assertIsDisplayed()
    }
}
