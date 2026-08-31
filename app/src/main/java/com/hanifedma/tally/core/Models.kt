package com.hanifedma.tally.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The five tables, exactly as Postgres holds them.
 *
 * Property names are Kotlin's, the wire names are the database's — the same
 * snake_case columns the web app reads and writes, because there is only one
 * ledger and two apps looking at it.
 *
 * Every nullable-with-default field exists so that a row written by an older
 * version of either app still deserialises. A ledger that fails to load
 * because a column arrived that this build has not heard of would be a very
 * bad way to lose a year of records.
 */

@Serializable
data class AccountRow(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val name: String = "",
    val kind: String = "cash",
    val currency: String = Money.DEFAULT_CURRENCY,
    @SerialName("opening_minor") val openingMinor: Long = 0,
    val color: String = "indigo",
    val archived: Boolean = false,
    val position: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    /** Clamp anything that arrived damaged, rather than trust it downstream. */
    fun normalized(): AccountRow = copy(
        name = name.trim().take(60).ifEmpty { "—" },
        kind = if (kind in ACCOUNT_KINDS) kind else "cash",
        currency = if (Money.isKnown(currency)) currency else Money.DEFAULT_CURRENCY,
        color = if (color in COLORS) color else "indigo",
    )

    companion object {
        val ACCOUNT_KINDS = listOf("cash", "bank", "card", "ewallet", "savings")
    }
}

@Serializable
data class CategoryRow(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val name: String = "",
    val kind: String = "expense",
    val icon: String = "•",
    val color: String = "gray",
    val archived: Boolean = false,
    val position: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    fun normalized(): CategoryRow = copy(
        name = name.trim().take(60).ifEmpty { "—" },
        kind = if (kind == "income") "income" else "expense",
        icon = icon.trim().take(8).ifEmpty { "•" },
        color = if (color in COLORS) color else "gray",
    )
}

@Serializable
data class TransactionRow(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    val kind: String = "expense",
    @SerialName("amount_minor") val amountMinor: Long = 0,
    val currency: String = Money.DEFAULT_CURRENCY,
    val rate: Double = 1.0,
    @SerialName("rate_base") val rateBase: String = Money.DEFAULT_CURRENCY,
    @SerialName("account_id") val accountId: String? = null,
    @SerialName("to_account_id") val toAccountId: String? = null,
    @SerialName("to_amount_minor") val toAmountMinor: Long? = null,
    @SerialName("category_id") val categoryId: String? = null,
    val note: String = "",
    @SerialName("occurred_on") val occurredOn: String = Dates.today(),
    @SerialName("occurred_min") val occurredMin: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    val isTransfer get() = kind == "transfer"

    fun normalized(): TransactionRow {
        val k = if (kind == "income" || kind == "transfer") kind else "expense"
        return copy(
            kind = k,
            amountMinor = amountMinor.coerceAtLeast(0),
            currency = if (Money.isKnown(currency)) currency else Money.DEFAULT_CURRENCY,
            rate = if (rate.isFinite() && rate > 0) rate else 1.0,
            rateBase = if (Money.isKnown(rateBase)) rateBase else Money.DEFAULT_CURRENCY,
            // A transfer has no category and everything else has no
            // destination. Enforced here as well as in the database, so a
            // row can never be half of each on screen.
            toAccountId = if (k == "transfer") toAccountId else null,
            toAmountMinor = if (k == "transfer") toAmountMinor?.coerceAtLeast(0) else null,
            categoryId = if (k == "transfer") null else categoryId,
            note = note.trim().take(280),
            occurredOn = if (Dates.isDayKey(occurredOn)) occurredOn else Dates.today(),
            occurredMin = occurredMin.coerceIn(0, 1439),
        )
    }
}

@Serializable
data class BudgetRow(
    val id: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("amount_minor") val amountMinor: Long = 0,
    val currency: String = Money.DEFAULT_CURRENCY,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    fun normalized(): BudgetRow = copy(
        amountMinor = amountMinor.coerceAtLeast(0),
        currency = if (Money.isKnown(currency)) currency else Money.DEFAULT_CURRENCY,
    )
}

@Serializable
data class SettingsRow(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("main_currency") val mainCurrency: String = Money.DEFAULT_CURRENCY,
    val theme: String = "dark",
    val lang: String = "en",
    @SerialName("week_start") val weekStart: Int = 1,
    @SerialName("month_start") val monthStart: Int = 1,
    // Kept as raw JSON rather than a typed map: the column is jsonb and a
    // value written by the web app could be a number or a string, and one
    // unexpected shape must not fail the whole settings row.
    val rates: JsonElement = JsonObject(emptyMap()),
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    /** Only rates that are a real positive number for a currency we know. */
    val rateMap: Map<String, Double>
        get() {
            val obj = rates as? JsonObject ?: return emptyMap()
            val out = LinkedHashMap<String, Double>()
            for ((code, value) in obj) {
                if (!Money.isKnown(code)) continue
                val n = (value as? JsonPrimitive)?.let {
                    it.doubleOrNull ?: it.content.toDoubleOrNull()
                } ?: continue
                if (n.isFinite() && n > 0) out[code] = n
            }
            return out
        }

    fun normalized(): SettingsRow = copy(
        mainCurrency = if (Money.isKnown(mainCurrency)) mainCurrency else Money.DEFAULT_CURRENCY,
        theme = if (theme == "light") "light" else "dark",
        lang = if (lang == "ko") "ko" else "en",
        weekStart = weekStart.coerceIn(0, 6),
        monthStart = monthStart.coerceIn(1, 28),
    )

    fun withRates(next: Map<String, Double>): SettingsRow =
        copy(rates = JsonObject(next.mapValues { JsonPrimitive(it.value) }))

    /** Everything the money functions need to reach the main currency. */
    val ctx: Money.Ctx get() = Money.Ctx(mainCurrency, rateMap)
}

/** The palette a category or account can be painted with. */
val COLORS = listOf(
    "indigo", "blue", "sky", "teal", "green", "lime",
    "amber", "orange", "rose", "pink", "purple", "gray",
)

/** Everything on screen at once, as one immutable value. */
data class Ledger(
    val settings: SettingsRow = SettingsRow(),
    val accounts: List<AccountRow> = emptyList(),
    val categories: List<CategoryRow> = emptyList(),
    val transactions: List<TransactionRow> = emptyList(),
    val budgets: List<BudgetRow> = emptyList(),
) {
    val ctx get() = settings.ctx
    fun account(id: String?) = accounts.firstOrNull { it.id == id }
    fun category(id: String?) = categories.firstOrNull { it.id == id }
    fun liveAccounts(includeArchived: Boolean = false) =
        accounts.filter { includeArchived || !it.archived }
            .sortedWith(compareBy({ it.position }, { it.name }))
    fun categoriesOf(kind: String?, includeArchived: Boolean = false) =
        categories.filter { (kind == null || it.kind == kind) && (includeArchived || !it.archived) }
            .sortedWith(compareBy({ it.position }, { it.name }))
}
