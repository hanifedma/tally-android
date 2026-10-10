package com.hanifedma.tally.data

import android.content.Context
import android.util.Log
import com.hanifedma.tally.core.AccountRow
import com.hanifedma.tally.core.BudgetRow
import com.hanifedma.tally.core.CategoryRow
import com.hanifedma.tally.core.Ids
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.Money
import com.hanifedma.tally.core.SEED_ACCOUNTS
import com.hanifedma.tally.core.SEED_CATEGORIES
import com.hanifedma.tally.core.SettingsRow
import com.hanifedma.tally.core.TransactionRow
import com.hanifedma.tally.core.starterRename
import com.hanifedma.tally.core.startersMayFollow
import io.github.jan.supabase.auth.exception.SessionRequiredException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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
    /**
     * Keep everything on this device: the same ledger and the same rules,
     * with every path to the network closed. The cache below stops being an
     * optimisation and becomes the ledger itself, which is why nothing is
     * ever queued in local mode — an outbox that could never drain would be
     * the only thing holding the row.
     */
    private val local: Boolean = false,
    /**
     * Theme and language to start from when this ledger has no settings of
     * its own yet — what was chosen on the way in, so that choice survives
     * the first sync rather than being overwritten by the defaults.
     */
    private val defaults: SettingsRow? = null,
) {

    enum class Status { OFFLINE, SYNCING, LIVE, ERROR, LOCAL }

    data class Sync(val status: Status = Status.SYNCING, val pending: Int = 0)

    private val store = LocalStore(context, uid)
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
            // Nothing to send it to, so nothing waits to be sent.
            if (!local) pending[id] = full
        }

        /** Everything that changed since last time. Returns true if anything did. */
        suspend fun pull(): Boolean {
            var changed = false
            var from = 0L
            // A first fetch of this session is a complete one, so what does
            // not come back is genuinely not there. Later fetches are deltas
            // and say nothing about the rows they leave out.
            val full = cursor == null
            val arrived = if (full) HashSet<String>() else null
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
                for (row in page) {
                    arrived?.add(idOf(row))
                    if (accept(row)) changed = true
                }
                if (page.size < PAGE) break
                from += PAGE
            }
            if (arrived != null) requeueMissing(arrived)
            return changed
        }

        /**
         * Put back anything this device has that the server has never seen.
         *
         * A write can leave the queue without arriving: the server refuses it
         * for good and it is dropped rather than jam every later row behind
         * it. Right for a row the server will never accept — wrong for one
         * refused because the project was half set up, as a missing GRANT
         * will do to every write an account ever makes. Either way the row is
         * still in the cache, still on screen, still counted, and it is the
         * only copy in existence.
         *
         * Tombstones are left out: a first fetch deliberately skips deleted
         * rows, so every one this ledger ever had would look missing, every
         * time the app opens. The same rule, in the same words, in store.js.
         */
        private fun requeueMissing(arrived: Set<String>) {
            val missing = rows.values.filter {
                deletedAtOf(it) == null && idOf(it) !in arrived
            }
            if (missing.isEmpty()) return
            Log.w(TAG, "Re-sending ${missing.size} $name the server never received")
            missing.forEach { enqueue(it) }
        }

        /** Send everything queued. Throws only for failures worth retrying. */
        suspend fun push() {
            if (pending.isEmpty()) return
            val batch = pending.values.toList()
            // updated_at belongs to the server — it is the delta cursor. The
            // trigger overrules us anyway; not sending it says so.
            //
            // "Not sending it" has to mean the key is absent, not present and
            // null: this Json writes explicit nulls, and both timestamps are
            // NOT NULL columns with a default. updated_at survives that on
            // the trigger alone; created_at has no trigger, so a null is
            // refused outright (23502) — permanently, so the outbox drops the
            // row and says "Couldn't save that". That is every write a new
            // account makes, since a row has no created_at until the server
            // hands one back. Dropping the key takes the default on an
            // insert and keeps the existing value on an update.
            val serverStamped = setOf("created_at", "updated_at")
            val payload = JsonArray(
                batch.map {
                    val obj = Supabase.json
                        .encodeToJsonElement(serializer, stripUpdatedAt(it))
                        .jsonObject
                    JsonObject(obj.filterNot { (k, v) -> k in serverStamped && v is JsonNull })
                }
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
            // Only a row with this table's own columns — see
            // Compute.rowFitsTable. Decoding is too forgiving to be the check:
            // every column has a default, so a transaction decodes happily as
            // a category with no name.
            if (!com.hanifedma.tally.core.Compute.rowFitsTable(name, record.keys)) return
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

    private var settings = defaults ?: SettingsRow()
    private var settingsPending: SettingsRow? = null
    private var haveSettings = false
    /** Said once per session: a missing GRANT does not need saying every 30s. */
    private var warnedSetup = false

    private val writeLock = Mutex()
    private var channel: RealtimeChannel? = null
    private var realtimeJob: Job? = null

    /** The last channel saying goodbye; see [subscribe] for why it is waited for. */
    private var leaving: Job? = null

    /** Looking in on a channel that is down; see [watch]. */
    private var mending: Job? = null

    /** One join at a time, of the ones this makes itself; see [join]. */
    private val joining = Mutex()
    private var retryJob: Job? = null
    private var retryDelayMs = 2_000L
    private var closed = false
    private var seeding = false

    /**
     * True while there is nobody to be: no session the server would accept.
     *
     * Not the same thing as closed, and the difference is the point. The
     * library puts its session down whenever the app leaves the screen, and a
     * session can run out with no network to renew it over; in neither case
     * has anyone signed out. So the ledger stays exactly where it is — every
     * row, every queued edit, everything on screen — and only the talking
     * stops. See [pause] and [resume].
     */
    private var paused = false

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

    /**
     * With no network there is nothing to be behind, so there is only one
     * honest thing the status line can say.
     */
    private fun setStatus(status: Status) {
        _sync.value = Sync(if (local) Status.LOCAL else status, pendingCount())
    }

    // ------------------------------------------------------------
    //  Starting up
    // ------------------------------------------------------------

    /**
     * @param paused open on what this device already holds and say nothing
     *   to the server yet — for a ledger shown before it is known whether
     *   the session is still good. [resume] starts the conversation.
     */
    fun start(paused: Boolean = false) {
        loadCache()
        publish()
        if (local) {
            // No fetch to wait for and no socket to open. Give a first-run
            // ledger its starting categories and accounts; that is all.
            ensureSeeded()
            setStatus(Status.LOCAL)
            publish()
            return
        }
        setStatus(Status.SYNCING)
        if (paused) {
            this.paused = true
            return
        }
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
        val cache = store.readCache() ?: return
        cache.settings?.let { settings = it.normalized(); haveSettings = true }
        for (table in tables) table.cursor = cache.cursors[table.name]
        accounts.loadCached(cache.accounts)
        categories.loadCached(cache.categories)
        transactions.loadCached(cache.transactions)
        budgets.loadCached(cache.budgets)

        val outbox = store.readOutbox()
        outbox.accounts.forEach { accounts.pending[it.id] = it }
        outbox.categories.forEach { categories.pending[it.id] = it }
        outbox.transactions.forEach { transactions.pending[it.id] = it }
        outbox.budgets.forEach { budgets.pending[it.id] = it }
        settingsPending = outbox.settings
    }

    private fun saveCache() {
        // A fetch or a send that was already on its way when this was closed
        // still comes back, and still tries to write down what it learned.
        // After a sign-out that would put the ledger back on a phone that had
        // just been told to forget it.
        if (closed) return
        store.writeCache(
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
        if (local || closed) return
        store.writeOutbox(
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
        if (closed || local || paused) return
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
        // A full pull can put rows back in the queue that were only ever on
        // this device. They now have somewhere to go.
        if (pendingCount() > 0) flush()
        retryDelayMs = 2_000L
    }

    /** Called on resume, and after a reconnection. */
    fun refresh() {
        if (closed || local || paused) return
        // Coming back to the app is the moment to find out whether the socket
        // survived. Fetching alone would leave the screen correct once and
        // then frozen — the catch-up has to be paired with the channel being
        // back, or "live" means "live as of when you opened it".
        //
        // Being back, though, is the library's job and has to be left to it.
        // A channel found dropped here used to be thrown away and replaced on
        // the spot, which looked like the cure and was the disease: Android
        // takes the socket from an app it has put to sleep, the library is
        // already on its way to a new one, and when it gets there it rejoins
        // every channel it knows about — including the replacement, which
        // was at that moment joining itself. See [join] for what a channel
        // joined twice turns into; what it looked like was an app saying
        // "Offline" on a perfectly good network, a few seconds after being
        // come back to. So a dropped channel is watched rather than replaced.
        val ch = channel
        if (ch == null) {
            subscribe()
        } else if (ch.status.value == RealtimeChannel.Status.UNSUBSCRIBED) {
            watch(ch)
        }
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
        if (closed || local || paused || channel != null) return
        // Not until the channel before this one has finished leaving.
        //
        // The library files a channel under its name, and every channel this
        // makes has the same one. Asked for a channel while the last is still
        // on its way out, it hands back the last — and then, when that one's
        // goodbye completes, takes the name off its books. What is left is a
        // channel that has joined and that nothing is delivered to: the app
        // says it is live and hears nothing.
        val goodbye = leaving
        if (goodbye?.isActive == true) {
            scope.launch {
                goodbye.join()
                subscribe()
            }
            return
        }
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
            // Every time the channel joins — the first time, and after every
            // reconnection the library makes on its own when a phone wakes up
            // or changes network. The rejoin brings back *future* changes;
            // nothing but this asks for the ones that happened while the
            // socket was down. Without it the app looks live and is a day
            // behind. The web gets the same thing from the callback its
            // subscribe() takes, which fires again on each state change.
            launch {
                var joinedOnce = false
                ch.status.collect { state ->
                    if (closed) return@collect
                    when (state) {
                        RealtimeChannel.Status.SUBSCRIBED -> {
                            joinedOnce = true
                            setStatus(if (pendingCount() > 0) Status.SYNCING else Status.LIVE)
                            try {
                                syncNow()
                                flushNow()
                            } catch (e: Exception) {
                                reportSyncFailure(e)
                            }
                        }
                        // The socket went. The library will rejoin; saying
                        // "offline" is what stops the status line claiming
                        // everything is live while nothing is arriving. Only
                        // after a first join — a channel starts unsubscribed,
                        // and "offline" before the first connection would be
                        // a flash of the wrong word on every launch. And not
                        // while paused, which is mostly an app nobody is
                        // looking at: the word would be read by no one until
                        // they came back, to a network that was fine.
                        RealtimeChannel.Status.UNSUBSCRIBED -> {
                            if (joinedOnce && !paused) setStatus(Status.OFFLINE)
                            watch(ch)
                        }
                        else -> Unit
                    }
                }
            }
            join(ch)
        }
    }

    /**
     * Join [ch], once, and only if nobody else is about to.
     *
     * "Once" is the whole difficulty, because this is not the only thing that
     * joins it. The library keeps a list of channels and, whenever it gets a
     * connection back, joins every one on it — which is exactly right, and
     * means that a join asked for here while it was reconnecting arrives
     * twice. A channel joined twice does not fail, which would be kinder. The
     * server closes the first join in favour of the second and says so; the
     * library hears "closed" and drops the channel, both joins and all, off
     * its list — but the second join is still there at the far end, with
     * nobody listening to it. The next channel of that name to join is told
     * that *that* one has now been closed in its favour, and is dropped in
     * its turn: closed, the moment it joins, by the ghost of the one before.
     *
     * Asking the library to join while it has no connection is precisely the
     * request that turns into two: it waits for the connection, and so does
     * the rejoin. So the connection is asked for first and by itself — which
     * waits its turn behind any reconnection already under way — and the
     * join is sent only once things have stopped moving, and only if the
     * channel is still not joined by then. If there is no connection to be
     * had, nothing is sent at all: the library goes on trying, and when it
     * gets one, joins this channel along with the rest of its list.
     */
    private suspend fun join(ch: RealtimeChannel) = joining.withLock {
        if (closed || paused || channel !== ch) return@withLock
        try {
            val realtime = client.realtime
            if (realtime.status.value != Realtime.Status.CONNECTED) realtime.connect()
            // A rejoin the library began as that returned has not said so yet.
            delay(SETTLE_MS)
            if (closed || paused || channel !== ch) return@withLock
            if (realtime.status.value != Realtime.Status.CONNECTED) return@withLock
            if (ch.status.value != RealtimeChannel.Status.UNSUBSCRIBED) return@withLock
            ch.subscribe()
        } catch (e: CancellationException) {
            // Told to stop, which is what putting a channel down looks like
            // from in here. Not a failure — though it used to be caught as
            // one, and set "Offline" and booked a retry every time a channel
            // was put down while it was still joining.
            throw e
        } catch (e: Exception) {
            // Nothing more to do about it here: the channel is still down,
            // and a channel that is down is being watched.
            Log.w(TAG, "Couldn't join the channel", e)
        }
    }

    /**
     * Look in on a channel that is down, for as long as it stays down.
     *
     * Almost always there is nothing to do. The library reconnects by itself,
     * however long that takes, and joins every channel on its list when it
     * does; anything done here in the meantime only gets in its way — see
     * [join]. But there are two ways for a channel to be down that nothing
     * will ever put right, and both of them leave the app looking live and
     * hearing nothing for as long as it stays open:
     *
     *   • the far end closed it, and the library has struck it off its list.
     *     Nothing rejoins what is not on the list, so it gets a new channel;
     *   • it is still on the list, but the library has stopped trying to
     *     connect — after dropping an idle socket, which it does on purpose,
     *     or after losing count of its own reconnection, which it does not.
     *     It gets a join, which asks for a connection first.
     *
     * Neither is distinguishable from "about to come back" at the moment the
     * channel drops, so each look is taken after the library has had time to
     * reconnect and rejoin by itself, and found the channel still down.
     */
    private fun watch(ch: RealtimeChannel) {
        if (closed || local || mending?.isActive == true) return
        mending = scope.launch {
            while (true) {
                delay(REJOIN_GRACE_MS)
                // Paused, there is nobody to join as. Resuming looks again.
                if (closed || paused || channel !== ch) return@launch
                if (ch.status.value != RealtimeChannel.Status.UNSUBSCRIBED) return@launch
                if (client.realtime.subscriptions[ch.topic] !== ch) {
                    Log.w(TAG, "The channel was closed from the far end; making another")
                    unsubscribe()
                    subscribe()
                    return@launch
                }
                join(ch)
            }
        }
    }

    private fun unsubscribe() {
        realtimeJob?.cancel()
        realtimeJob = null
        mending?.cancel()
        val ch = channel ?: return
        channel = null
        // Already struck off the library's list, by the far end closing it:
        // there is nothing left to remove, and asking anyway starts a second
        // countdown to dropping the socket that nothing ever cancels.
        if (client.realtime.subscriptions[ch.topic] !== ch) return
        leaving = scope.launch {
            try {
                // Bounded, because the next channel waits for this to end and
                // a goodbye is said over the network: on a connection that
                // has gone quiet without closing, it could take for ever.
                withTimeoutOrNull(GOODBYE_MS) { client.realtime.removeChannel(ch) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't remove the channel", e)
            }
        }
    }

    private fun scheduleRetry() {
        if (closed || local || paused || retryJob?.isActive == true) return
        val wait = retryDelayMs
        // Back off, but never so far that a reconnection feels like a hang.
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(30_000L)
        retryJob = scope.launch {
            delay(wait)
            if (closed) return@launch
            // A channel only if there is none. One that exists is either
            // joined or being rejoined by the library, and replacing it here
            // is how it came to be joined twice.
            if (channel == null) subscribe()
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
        // Paused is its own explanation, and being resumed is its own retry.
        if (paused) return
        setStatus(Status.ERROR)
        scheduleRetry()
    }

    /**
     * "Not allowed", "no such table", "no such column".
     *
     * Not facts about the row being sent — facts about a database that has
     * not finished being set up, whose fix is a line of SQL run somewhere
     * else entirely. The row has to still be here when that happens. The
     * same list, for the same reason, in store.js.
     */
    private fun isSetupProblem(e: Exception): Boolean {
        // Asked while the session was down. Nothing is wrong with the
        // database, and its message carries a URL that the patterns below
        // were never meant to be read against.
        if (e is SessionRequiredException) return false
        val m = e.message.orEmpty()
        return m.contains("permission denied", ignoreCase = true) ||
            m.contains("42501") || m.contains("42P01") || m.contains("42703") ||
            m.contains("PGRST") ||
            Regex("""\b40[13]\b""").containsMatchIn(m)
    }

    /** True for the kind of failure worth trying again later. */
    private fun isTransient(e: Exception): Boolean {
        // Not sent at all, so certainly not refused.
        if (e is SessionRequiredException) return true
        val message = e.message.orEmpty()
        // Keeps its place in the queue, however long that takes.
        if (isSetupProblem(e)) return true
        // "Not now" is not "never": a timeout or a rate limit says nothing
        // at all about the row, and dropping it would throw away the only
        // copy of it there is.
        if (Regex("""\b(408|429)\b""").containsMatchIn(message)) return true
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
        val was = settings.mainCurrency
        val wasLang = settings.lang
        settings = next.normalized()
        haveSettings = true
        if (!local) settingsPending = settings.copy(userId = uid, updatedAt = null)
        if (settings.mainCurrency != was) retuneStarterAccounts(was, settings.mainCurrency)
        if (settings.lang != wasLang) retranslateStarters(wasLang, settings.lang)
        saveCache()
        saveOutbox()
        publish()
        flush()
    }

    /**
     * The starting accounts follow the main currency, but only while they are
     * still the ones we made.
     *
     * Someone who opens Tally in Jakarta wants a Cash account in rupiah, and
     * should not have to fix by hand what the app got wrong by guessing. But
     * the moment a single amount is filed under an account, its currency is a
     * fact about that money and not a preference: [Compute.balances]
     * adds minor units without converting, on the promise that a transaction
     * is always in its account's currency. So this runs only for a ledger
     * with no live transactions, whose accounts are all untouched starters
     * holding nothing — which is true of a new account and of one just
     * started over.
     */
    private fun retuneStarterAccounts(from: String, to: String) {
        val all = accounts.rows.values.toList()
        val starters = SEED_ACCOUNTS.map { Ids.derived(uid, "account:${it.slug}") }.toSet()
        if (!startersMayFollow(all, transactions.rows.values.toList(), starters, from)) return
        all.filter { it.deletedAt == null }
            .forEach { accounts.enqueue(it.copy(currency = to)) }
    }

    /**
     * The starter categories and accounts follow the language, one row at a
     * time, and only while a row still carries the name we gave it.
     *
     * Names are data, not interface: someone who renamed "Food" to "밥값"
     * keeps their word for it for ever. But someone who never touched the
     * starter set should not be left reading an English list inside a Korean
     * app, and there is no ambiguity about which rows those are — the app
     * wrote them, at ids it can recompute, with names it can still recognise.
     */
    private fun retranslateStarters(from: String, to: String) {
        SEED_CATEGORIES.forEach { seed ->
            val row = categories.rows[Ids.derived(uid, "category:${seed.slug}")] ?: return@forEach
            if (row.deletedAt != null) return@forEach
            val name = starterRename(row.name, seed.name(from), seed.name(to)) ?: return@forEach
            categories.enqueue(row.copy(name = name))
        }
        SEED_ACCOUNTS.forEach { seed ->
            val row = accounts.rows[Ids.derived(uid, "account:${seed.slug}")] ?: return@forEach
            if (row.deletedAt != null) return@forEach
            val name = starterRename(row.name, seed.name(from), seed.name(to)) ?: return@forEach
            accounts.enqueue(row.copy(name = name))
        }
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
        // Paused, an edit waits in the outbox like any other made with no
        // signal, and goes when the session is back.
        if (closed || local || paused) return
        scope.launch { flushNow() }
    }

    private suspend fun flushNow() = writeLock.withLock {
        if (closed || paused) return@withLock
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
            // Paused while this was on its way. Whatever did not arrive is
            // still queued, and resuming sends it.
            if (paused) return@withLock
            // "Reconnecting…" is honest but useless when the connection is
            // fine and the database is the problem. Say what it actually is,
            // once — repeating it on every retry would be its own kind of
            // broken.
            if (isSetupProblem(e) && !warnedSetup) {
                warnedSetup = true
                _errors.value = "err.setup"
            }
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

    /**
     * Put the account back to its first day.
     *
     * Every transaction, budget, account and category goes; the starting set
     * is rebuilt; the money settings return to their defaults. What survives
     * is the account itself and the two things that belong to the person
     * rather than the ledger — the theme and the language. Resetting those
     * would hand someone back an English app in a colour scheme they did not
     * choose, which is not what "start over" is being asked for.
     *
     * Tombstones, not deletes, for the same reason the rest of this file
     * never really deletes: a row that is gone still has to reach the other
     * devices, and only an UPDATE carries the user_id that row level
     * security needs before it will let the change through.
     */
    fun resetAll() {
        seeding = true
        try {
            // Settings first, and through the field rather than
            // enqueueSettings: that helper re-denominates and re-translates
            // the starter accounts as a side effect, and it would be doing
            // it to the rows this function is one line away from replacing.
            settings = settings.copy(
                mainCurrency = Money.DEFAULT_CURRENCY,
                weekStart = 1,
                monthStart = 1,
                rates = JsonObject(emptyMap()),
            ).normalized()
            haveSettings = true
            if (!local) settingsPending = settings.copy(userId = uid, updatedAt = null)

            val stamp = nowIso()
            transactions.live().forEach { transactions.enqueue(it.copy(deletedAt = stamp)) }
            budgets.live().forEach { budgets.enqueue(it.copy(deletedAt = stamp)) }
            accounts.live().forEach { accounts.enqueue(it.copy(deletedAt = stamp)) }
            categories.live().forEach { categories.enqueue(it.copy(deletedAt = stamp)) }

            // Then the starting set, at the ids this account derives for
            // itself — the same ids just buried above. That collision is the
            // point: the outbox is keyed by row, so a starter goes up once,
            // as itself, rather than as a deletion followed by an insert.
            val lang = settings.lang
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

            saveCache()
            saveOutbox()
            publish()
            flush()
        } finally {
            seeding = false
        }
    }

    // ------------------------------------------------------------
    //  Bringing a device-only ledger into an account
    // ------------------------------------------------------------

    /**
     * Copy a device-only ledger into this signed-in one.
     *
     * The rows keep their own ids — they were made on the device and are
     * already unique — with one exception. The starter categories and
     * accounts have ids *derived* from the user id, so that two devices
     * seeding the same new account write one set of rows rather than two.
     * Carried across unchanged they would arrive as strangers beside this
     * account's own copy of the same sixteen categories, so those ids, and
     * every reference to them, are translated to what this account would
     * have derived for itself.
     *
     * Queued like any other write: if the network is not there, it goes when
     * it is. Nothing is erased at the far end, so a failure here loses
     * nothing.
     */
    fun importLocal(cache: LocalStore.Cache) {
        val map = HashMap<String, String>()
        for (seed in SEED_CATEGORIES) {
            map[Ids.derived(LOCAL_UID, "category:${seed.slug}")] =
                Ids.derived(uid, "category:${seed.slug}")
        }
        for (seed in SEED_ACCOUNTS) {
            map[Ids.derived(LOCAL_UID, "account:${seed.slug}")] =
                Ids.derived(uid, "account:${seed.slug}")
        }
        fun remap(value: String?): String? = value?.let { map[it] ?: it }

        for (row in cache.accounts.filter { it.deletedAt == null }) {
            accounts.enqueue(row.copy(id = remap(row.id)!!, userId = uid, updatedAt = null))
        }
        for (row in cache.categories.filter { it.deletedAt == null }) {
            categories.enqueue(row.copy(id = remap(row.id)!!, userId = uid, updatedAt = null))
        }
        for (row in cache.transactions.filter { it.deletedAt == null }) {
            transactions.enqueue(
                row.copy(
                    id = remap(row.id)!!,
                    userId = uid,
                    updatedAt = null,
                    accountId = remap(row.accountId),
                    toAccountId = remap(row.toAccountId),
                    categoryId = remap(row.categoryId),
                )
            )
        }
        for (row in cache.budgets.filter { it.deletedAt == null }) {
            budgets.enqueue(
                row.copy(
                    id = remap(row.id)!!,
                    userId = uid,
                    updatedAt = null,
                    categoryId = remap(row.categoryId),
                )
            )
        }
        saveCache()
        saveOutbox()
        publish()
        flush()
    }

    // ------------------------------------------------------------
    //  Lifecycle
    // ------------------------------------------------------------

    /**
     * Stop talking to the server, and keep everything else.
     *
     * For when there is no session to talk with — see [paused]. Nothing is
     * closed and nothing is forgotten: the rows stay, the screen stays, and
     * an edit made in the meantime is queued the way one made with no signal
     * is. Asking twice is harmless.
     *
     * @param unreachable the session has run out and could not be renewed,
     *   which is worth saying on screen. Merely leaving the app is not: the
     *   status would be read by nobody, and would still be showing "offline"
     *   for the first moment after coming back to a perfectly good network.
     */
    fun pause(unreachable: Boolean = false) {
        if (closed || local) return
        if (!paused) {
            paused = true
            retryJob?.cancel()
            // The channel is left as it is. The library keeps it, rejoins it
            // when it can, and hands it the new token when there is one —
            // none of which wants help; see [refresh].
        }
        if (unreachable) setStatus(Status.OFFLINE)
    }

    /**
     * There is a session again: fetch what was missed, send what was queued,
     * and open the channel if this ledger has not had one yet. Does nothing
     * to a ledger that was not paused.
     */
    fun resume() {
        if (closed || local || !paused) return
        paused = false
        refresh()
    }

    fun close() {
        if (closed) return
        retryJob?.cancel()
        unsubscribe()
        saveCache()
        saveOutbox()
        // Last, because the two saves above are the last ones allowed.
        closed = true
    }

    /**
     * Sign-out, or starting a device-only ledger over: nothing of it stays on
     * this device.
     *
     * Closes first, and for good. This used to delete the two files and leave
     * the closing to its caller — and closing saves, so the ledger was written
     * straight back a line after it had been erased. Signing out left it on
     * the phone, and "Start over" on a device-only ledger started nothing
     * over at all: it reopened the file it had just been told to delete.
     */
    fun forgetDevice() {
        close()
        store.forget()
    }

    companion object {
        /** The user id a device-only ledger is filed under, here and on the web. */
        const val LOCAL_UID = "local"

        private const val TAG = "TallyLedger"
        private const val PAGE = 1000L

        /** How long a channel is given to say goodbye before the next one stops waiting. */
        private const val GOODBYE_MS = 5_000L

        /**
         * How long a channel that is down is left alone before being looked
         * in on. The library waits seven seconds before each attempt to
         * reconnect, and rejoins as soon as one succeeds; this is long enough
         * for that to have happened if it was going to.
         */
        private const val REJOIN_GRACE_MS = 12_000L

        /**
         * How long after a connection is made a join is held back. A rejoin
         * the library has just begun shows on the channel within a few
         * milliseconds; a second is a wide margin on that, and nothing is
         * waiting on the first realtime event to arrive a second sooner.
         */
        private const val SETTLE_MS = 1_000L
    }
}
