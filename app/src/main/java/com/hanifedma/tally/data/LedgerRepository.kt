package com.hanifedma.tally.data

import android.content.Context
import android.util.Log
import com.hanifedma.tally.core.AccountRow
import com.hanifedma.tally.core.BudgetRow
import com.hanifedma.tally.core.CategoryRow
import com.hanifedma.tally.core.Ids
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.SEED_ACCOUNTS
import com.hanifedma.tally.core.SEED_CATEGORIES
import com.hanifedma.tally.core.SettingsRow
import com.hanifedma.tally.core.TransactionRow
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The ledger — the Kotlin half of store.js.
 *
 * One person's rows, in three places at once: Postgres (the truth), a file on
 * this device (what you see the instant the app opens), and an outbox (what
 * you changed with no signal). The rules that keep those three honest are the
 * same three the web app follows:
 *
 *   • Every id is made on the client, so replaying a queued write can never
 *     create a second copy of it.
 *   • Nothing is deleted, only marked deleted, so every change is an upsert
 *     of a whole row and replaying is order-independent.
 *   • A row from the server wins, unless this device is still holding an
 *     unsent change to that same row. That is the entire conflict policy.
 */
class LedgerRepository(
    context: Context,
    private val uid: String,
    private val scope: CoroutineScope,
) {

    enum class Status { OFFLINE, SYNCING, LIVE, ERROR }

    data class Sync(val status: Status = Status.SYNCING, val pending: Int = 0)

    private val local = LocalStore(context, uid)
    private val client get() = Supabase.client()

    private val _ledger = MutableStateFlow(Ledger())
    val ledger: StateFlow<Ledger> = _ledger.asStateFlow()

    private val _sync = MutableStateFlow(Sync())
    val syncState: StateFlow<Sync> = _sync.asStateFlow()

    /** Raised when a write was refused for good, so the UI can say so. */
    private val _errors = MutableStateFlow<String?>(null)
    val errors: StateFlow<String?> = _errors.asStateFlow()

    fun clearError() { _errors.value = null }

    // ------------------------------------------------------------
    //  One table's worth of state
    //
    //  An inner class with its own pull/push, rather than free functions
    //  over a list of tables: the four tables hold four different row types,
    //  so any list of them is projected and a generic function called on an
    //  element of it cannot bind its type parameter. Keeping the generic
    //  inside the class means every call site outside is ordinary.
    // ------------------------------------------------------------

    private inner class Table<T : Any>(
        val name: String,
        val serializer: KSerializer<T>,
        val idOf: (T) -> String,
        val updatedAtOf: (T) -> String?,
        val deletedAtOf: (T) -> String?,
        val normalize: (T) -> T,
        val stampUser: (T) -> T,
        val stripUpdatedAt: (T) -> T,
    ) {
        val rows = LinkedHashMap<String, T>()
        /** Rows this device has changed and not yet sent. */
        val pending = LinkedHashMap<String, T>()
        var cursor: String? = null

        fun live(): List<T> = rows.values.filter { deletedAtOf(it) == null }

        fun loadCached(cached: List<T>) {
            cached.forEach { rows[idOf(it)] = normalize(it) }
        }

        /**
         * Take a row from the server.
         *
         * Refused only while this device is still holding an unsent change to
         * that same row: the local version is strictly newer than anything
         * the server can be echoing back, because the server has not seen it.
         */
        fun accept(incoming: T): Boolean {
            val row = normalize(incoming)
            val id = idOf(row)
            if (id.isEmpty()) return false
            if (pending.containsKey(id)) return false
            val prev = rows[id]
            val prevAt = prev?.let { updatedAtOf(it) }
            val nextAt = updatedAtOf(row)
            // A realtime event that overtook a fetch must not put back a
            // version we have already moved past.
            if (prev != null && prevAt != null && nextAt != null && nextAt < prevAt) return false
            rows[id] = row
            val c = cursor
            if (nextAt != null && (c == null || nextAt > c)) cursor = nextAt
            return true
        }

        fun enqueue(row: T) {
            val full = stampUser(normalize(row))
            val id = idOf(full)
            rows[id] = full
            pending[id] = full
        }

        /** Everything that changed since last time. Returns true if anything did. */
        suspend fun pull(): Boolean {
            var changed = false
            var from = 0L
            while (true) {
                val at = cursor
                val result = client.from(name).select {
                    filter {
                        eq("user_id", uid)
                        // A later sync needs tombstones — a deletion is the
                        // only way it learns a row is gone.
                        if (at != null) gte("updated_at", at)
                    }
                    // A stable total order. Ordering by updated_at alone would
                    // let two rows written in the same microsecond swap places
                    // between pages, which is how one gets read twice and
                    // another not at all.
                    order("updated_at", Order.ASCENDING)
                    order("id", Order.ASCENDING)
                    range(from, from + PAGE - 1)
                }
                val page = Supabase.json.decodeFromString(ListSerializer(serializer), result.data)
                for (row in page) if (accept(row)) changed = true
                if (page.size < PAGE) break
                from += PAGE
            }
            return changed
        }

        /** Send everything queued. Throws only for failures worth retrying. */
        suspend fun push() {
            if (pending.isEmpty()) return
            val batch = pending.values.toList()
            // updated_at belongs to the server — it is the delta cursor. The
            // trigger overrules us anyway; not sending it says so.
            val payload = JsonArray(
                batch.map { Supabase.json.encodeToJsonElement(serializer, stripUpdatedAt(it)) }
            )
            val result = try {
                client.from(name).upsert(payload) {
                    onConflict = "id"
                    select()
                }
            } catch (e: Exception) {
                if (isTransient(e)) throw e
                // Rejected for good. Drop it rather than jam the queue behind
                // a row that will never be accepted, and say so.
                Log.e(TAG, "Rejected by the server ($name)", e)
                batch.forEach { pending.remove(idOf(it)) }
                _errors.value = "err.save"
                return
            }
            // Clear the hold before taking the canonical rows back, so
            // accept() stops refusing them.
            batch.forEach { pending.remove(idOf(it)) }
            val returned = Supabase.json.decodeFromString(ListSerializer(serializer), result.data)
            returned.forEach { accept(it) }
        }

        fun listen(ch: RealtimeChannel) {
            ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = name
                // Row level security already limits this to one person; the
                // filter means the server does not even send the rest.
                filter("user_id", FilterOperator.EQ, uid)
            }.onEach { action ->
                when (action) {
                    is PostgresAction.Insert -> onRecord(action.record)
                    is PostgresAction.Update -> onRecord(action.record)
                    is PostgresAction.Delete -> onHardDelete(action.oldRecord)
                    else -> Unit
                }
            }.launchIn(scope)
        }

        private fun onRecord(record: JsonObject) {
            val row = try {
                Supabase.json.decodeFromJsonElement(serializer, record)
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring an unreadable $name change", e)
                return
            }
            if (accept(row)) {
                saveCache()
                publish()
            }
        }

        private fun onHardDelete(old: JsonObject) {
            // Tally never makes a hard delete, but one done by hand in the
            // dashboard must not leave the row showing here for ever.
            val id = (old["id"] as? JsonPrimitive)?.content ?: return
            if (rows.remove(id) != null) {
                saveCache()
                publish()
            }
        }
    }

    private val accounts = Table(
        name = "accounts",
        serializer = AccountRow.serializer(),
        idOf = { it.id },
        updatedAtOf = { it.updatedAt },
        deletedAtOf = { it.deletedAt },
        normalize = { it.normalized() },
        stampUser = { it.copy(userId = uid) },
        stripUpdatedAt = { it.copy(updatedAt = null) },
    )
    private val categories = Table(
        name = "categories",
        serializer = CategoryRow.serializer(),
        idOf = { it.id },
        updatedAtOf = { it.updatedAt },
        deletedAtOf = { it.deletedAt },
        normalize = { it.normalized() },
        stampUser = { it.copy(userId = uid) },
        stripUpdatedAt = { it.copy(updatedAt = null) },
    )
    private val transactions = Table(
        name = "transactions",
        serializer = TransactionRow.serializer(),
        idOf = { it.id },
        updatedAtOf = { it.updatedAt },
        deletedAtOf = { it.deletedAt },
        normalize = { it.normalized() },
        stampUser = { it.copy(userId = uid) },
        stripUpdatedAt = { it.copy(updatedAt = null) },
    )
    private val budgets = Table(
        name = "budgets",
        serializer = BudgetRow.serializer(),
        idOf = { it.id },
        updatedAtOf = { it.updatedAt },
        deletedAtOf = { it.deletedAt },
        normalize = { it.normalized() },
        stampUser = { it.copy(userId = uid) },
        stripUpdatedAt = { it.copy(updatedAt = null) },
    )
    private val tables: List<Table<*>> = listOf(accounts, categories, transactions, budgets)

    private var settings = SettingsRow()
    private var settingsPending: SettingsRow? = null
    private var haveSettings = false

    private val writeLock = Mutex()
    private var channel: RealtimeChannel? = null
    private var realtimeJob: Job? = null
    private var retryJob: Job? = null
    private var retryDelayMs = 2_000L
    private var closed = false
    private var seeding = false

    // ------------------------------------------------------------
    //  Publishing
    // ------------------------------------------------------------

    private fun publish() {
        _ledger.value = Ledger(
            settings = settings,
            accounts = accounts.live(),
            categories = categories.live(),
            transactions = transactions.live(),
            budgets = budgets.live(),
        )
        _sync.value = _sync.value.copy(pending = pendingCount())
    }

    private fun pendingCount() =
        tables.sumOf { it.pending.size } + (if (settingsPending != null) 1 else 0)

    private fun setStatus(status: Status) { _sync.value = Sync(status, pendingCount()) }

    // ------------------------------------------------------------
    //  Starting up
    // ------------------------------------------------------------

    fun start() {
        loadCache()
        publish()
        setStatus(Status.SYNCING)
        scope.launch {
            try {
                syncNow()
            } catch (e: Exception) {
                reportSyncFailure(e)
            }
            subscribe()
            flush()
        }
    }

    private fun loadCache() {
        val cache = local.readCache() ?: return
        cache.settings?.let { settings = it.normalized(); haveSettings = true }
        for (table in tables) table.cursor = cache.cursors[table.name]
        accounts.loadCached(cache.accounts)
        categories.loadCached(cache.categories)
        transactions.loadCached(cache.transactions)
        budgets.loadCached(cache.budgets)

        val outbox = local.readOutbox()
        outbox.accounts.forEach { accounts.pending[it.id] = it }
        outbox.categories.forEach { categories.pending[it.id] = it }
        outbox.transactions.forEach { transactions.pending[it.id] = it }
        outbox.budgets.forEach { budgets.pending[it.id] = it }
        settingsPending = outbox.settings
    }

    private fun saveCache() {
        local.writeCache(
            LocalStore.Cache(
                cursors = tables.associate { it.name to it.cursor },
                settings = if (haveSettings) settings else null,
                accounts = accounts.rows.values.toList(),
                categories = categories.rows.values.toList(),
                transactions = transactions.rows.values.toList(),
                budgets = budgets.rows.values.toList(),
            )
        )
    }

    private fun saveOutbox() {
        local.writeOutbox(
            LocalStore.Outbox(
                accounts = accounts.pending.values.toList(),
                categories = categories.pending.values.toList(),
                transactions = transactions.pending.values.toList(),
                budgets = budgets.pending.values.toList(),
                settings = settingsPending,
            )
        )
    }

    private fun acceptSettings(incoming: SettingsRow): Boolean {
        if (settingsPending != null) return false
        settings = incoming.normalized()
        haveSettings = true
        return true
    }

    // ------------------------------------------------------------
    //  Fetching
    // ------------------------------------------------------------

    private suspend fun syncNow() {
        if (closed) return
        setStatus(Status.SYNCING)
        var changed = false

        val settingsResult = client.from("settings").select {
            filter { eq("user_id", uid) }
            limit(1)
        }
        val settingsRows = Supabase.json.decodeFromString(
            ListSerializer(SettingsRow.serializer()), settingsResult.data
        )
        if (settingsRows.isNotEmpty()) {
            if (acceptSettings(settingsRows.first())) changed = true
        } else if (settingsPending == null) {
            // First run on this account: publish whatever this device was
            // already set to, so the phone and the laptop start together.
            enqueueSettings(settings)
        }

        for (table in tables) if (table.pull()) changed = true

        ensureSeeded()

        if (changed) {
            saveCache()
            publish()
        }
        setStatus(if (pendingCount() > 0) Status.SYNCING else Status.LIVE)
        retryDelayMs = 2_000L
    }

    /** Called on resume, and after a reconnection. */
    fun refresh() {
        if (closed) return
        scope.launch {
            try {
                syncNow()
                flushNow()
            } catch (e: Exception) {
                reportSyncFailure(e)
            }
        }
    }

    // ------------------------------------------------------------
    //  Realtime
    // ------------------------------------------------------------

    private fun subscribe() {
        if (closed || channel != null) return
        val ch = client.channel("tally:$uid")
        channel = ch

        tables.forEach { it.listen(ch) }

        ch.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "settings"
            filter("user_id", FilterOperator.EQ, uid)
        }.onEach { action ->
            val record = when (action) {
                is PostgresAction.Insert -> action.record
                is PostgresAction.Update -> action.record
                else -> null
            } ?: return@onEach
            val row = try {
                Supabase.json.decodeFromJsonElement(SettingsRow.serializer(), record)
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring an unreadable settings change", e)
                return@onEach
            }
            if (acceptSettings(row)) {
                saveCache()
                publish()
            }
        }.launchIn(scope)

        realtimeJob = scope.launch {
            try {
                ch.subscribe(blockUntilSubscribed = true)
                setStatus(if (pendingCount() > 0) Status.SYNCING else Status.LIVE)
                // Anything that happened between the last fetch and the socket
                // coming up is in neither — ask for it.
                syncNow()
            } catch (e: Exception) {
                Log.w(TAG, "Realtime subscribe failed", e)
                setStatus(Status.OFFLINE)
                scheduleRetry()
            }
        }
    }

    private fun unsubscribe() {
        realtimeJob?.cancel()
        realtimeJob = null
        val ch = channel ?: return
        channel = null
        scope.launch {
            try {
                client.realtime.removeChannel(ch)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't remove the channel", e)
            }
        }
    }

    private fun scheduleRetry() {
        if (closed || retryJob?.isActive == true) return
        val wait = retryDelayMs
        // Back off, but never so far that a reconnection feels like a hang.
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
        retryJob = scope.launch {
            delay(wait)
            if (closed) return@launch
            unsubscribe()
            subscribe()
            try {
                flushNow()
                syncNow()
            } catch (e: Exception) {
                reportSyncFailure(e)
            }
        }
    }

    private fun reportSyncFailure(e: Exception) {
        Log.w(TAG, "Sync failed", e)
        setStatus(Status.ERROR)
        scheduleRetry()
    }

    /** True for the kind of failure worth trying again later. */
    private fun isTransient(e: Exception): Boolean {
        val message = e.message.orEmpty()
        // PostgREST rejects a bad row with a 4xx and a reason; a dropped
        // connection has neither.
        if (Regex("""\b4\d\d\b""").containsMatchIn(message)) return false
        if (message.contains("violates", ignoreCase = true)) return false
        if (message.contains("duplicate key", ignoreCase = true)) return false
        return true
    }

    // ------------------------------------------------------------
    //  Writing
    // ------------------------------------------------------------

    private fun <T : Any> commit(table: Table<T>, row: T) {
        table.enqueue(row)
        saveCache()
        saveOutbox()
        publish()
        flush()
    }

    private fun enqueueSettings(next: SettingsRow) {
        settings = next.normalized()
        haveSettings = true
        settingsPending = settings.copy(userId = uid, updatedAt = null)
        saveCache()
        saveOutbox()
        publish()
        flush()
    }

    fun put(row: AccountRow) = commit(accounts, row)
    fun put(row: CategoryRow) = commit(categories, row)
    fun put(row: TransactionRow) = commit(transactions, row)
    fun put(row: BudgetRow) = commit(budgets, row)
    fun putSettings(next: SettingsRow) = enqueueSettings(next)

    private fun nowIso(): String = java.time.Instant.now().toString()

    fun delete(row: AccountRow) = put(row.copy(deletedAt = nowIso()))
    fun delete(row: CategoryRow) = put(row.copy(deletedAt = nowIso()))
    fun delete(row: TransactionRow) = put(row.copy(deletedAt = nowIso()))
    fun delete(row: BudgetRow) = put(row.copy(deletedAt = nowIso()))

    fun restore(row: AccountRow) = put(row.copy(deletedAt = null))
    fun restore(row: CategoryRow) = put(row.copy(deletedAt = null))
    fun restore(row: TransactionRow) = put(row.copy(deletedAt = null))

    fun flush() {
        if (closed) return
        scope.launch { flushNow() }
    }

    private suspend fun flushNow() = writeLock.withLock {
        if (closed) return@withLock
        if (pendingCount() == 0) {
            setStatus(Status.LIVE)
            return@withLock
        }
        setStatus(Status.SYNCING)
        try {
            for (table in tables) table.push()
            settingsPending?.let { sendSettings(it) }
            saveOutbox()
            saveCache()
            publish()
            setStatus(if (pendingCount() > 0) Status.SYNCING else Status.LIVE)
            retryDelayMs = 2_000L
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't send changes yet", e)
            setStatus(Status.OFFLINE)
            scheduleRetry()
        }
    }

    private suspend fun sendSettings(row: SettingsRow) {
        val result = try {
            client.from("settings").upsert(row.copy(updatedAt = null)) {
                onConflict = "user_id"
                select()
            }
        } catch (e: Exception) {
            if (isTransient(e)) throw e
            Log.e(TAG, "Settings rejected", e)
            settingsPending = null
            _errors.value = "err.save"
            return
        }
        settingsPending = null
        val returned = Supabase.json.decodeFromString(
            ListSerializer(SettingsRow.serializer()), result.data
        )
        returned.firstOrNull()?.let { acceptSettings(it) }
    }

    // ------------------------------------------------------------
    //  First run
    // ------------------------------------------------------------

    /**
     * Give a brand-new account something to work with.
     *
     * "No rows at all", not "no live rows": someone who deliberately deleted
     * every category still has the tombstones, and must not have the starter
     * set pushed back at them on the next reconnect. The ids are derived from
     * the user id, so a phone and a laptop doing this in the same minute
     * write the same rows rather than two sets of them.
     */
    private fun ensureSeeded() {
        if (seeding) return
        val noCategories = categories.rows.isEmpty()
        val noAccounts = accounts.rows.isEmpty()
        if (!noCategories && !noAccounts) return
        seeding = true
        try {
            val lang = settings.lang
            if (noCategories) {
                SEED_CATEGORIES.forEachIndexed { i, seed ->
                    categories.enqueue(
                        CategoryRow(
                            id = Ids.derived(uid, "category:${seed.slug}"),
                            name = seed.name(lang),
                            kind = seed.kind,
                            icon = seed.icon,
                            color = seed.color,
                            position = i,
                        )
                    )
                }
            }
            if (noAccounts) {
                SEED_ACCOUNTS.forEachIndexed { i, seed ->
                    accounts.enqueue(
                        AccountRow(
                            id = Ids.derived(uid, "account:${seed.slug}"),
                            name = seed.name(lang),
                            kind = seed.kind,
                            currency = settings.mainCurrency,
                            color = seed.color,
                            position = i,
                        )
                    )
                }
            }
            saveCache()
            saveOutbox()
            publish()
            flush()
        } finally {
            seeding = false
        }
    }

    // ------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------

    fun close() {
        closed = true
        retryJob?.cancel()
        unsubscribe()
        saveCache()
        saveOutbox()
    }

    /** Sign-out: a shared phone should not keep the ledger. */
    fun forgetDevice() = local.forget()

    private companion object {
        const val TAG = "TallyLedger"
        const val PAGE = 1000L
    }
}
