package com.hanifedma.tally.data

import android.content.Context
import android.util.Log
import com.hanifedma.tally.core.AccountRow
import com.hanifedma.tally.core.BudgetRow
import com.hanifedma.tally.core.CategoryRow
import com.hanifedma.tally.core.SettingsRow
import com.hanifedma.tally.core.TransactionRow
import kotlinx.serialization.Serializable
import java.io.File

/**
 * What this device knows without asking.
 *
 * Two files, both plain JSON in the app's private storage:
 *
 *   • the cache — the last ledger this device saw, so the app opens on a
 *     year of transactions instantly and stays readable with no signal;
 *   • the outbox — edits made while offline, waiting to be sent.
 *
 * A file rather than a database because the whole ledger is one object that
 * is always read and written whole, and because a schema migration is a
 * thing that can go wrong at four in the morning on someone's only copy.
 */
class LocalStore(context: Context, private val uid: String) {

    @Serializable
    data class Cache(
        val version: Int = VERSION,
        val cursors: Map<String, String?> = emptyMap(),
        val settings: SettingsRow? = null,
        val accounts: List<AccountRow> = emptyList(),
        val categories: List<CategoryRow> = emptyList(),
        val transactions: List<TransactionRow> = emptyList(),
        val budgets: List<BudgetRow> = emptyList(),
    )

    /**
     * One pending write per row. Every change is an upsert of a whole row —
     * a deletion is just a row with deleted_at set — so the newest version of
     * a row is all that has to be kept, and replaying is idempotent.
     */
    @Serializable
    data class Outbox(
        val accounts: List<AccountRow> = emptyList(),
        val categories: List<CategoryRow> = emptyList(),
        val transactions: List<TransactionRow> = emptyList(),
        val budgets: List<BudgetRow> = emptyList(),
        val settings: SettingsRow? = null,
    ) {
        val size: Int
            get() = accounts.size + categories.size + transactions.size +
                budgets.size + (if (settings != null) 1 else 0)
    }

    private val dir = File(context.filesDir, "tally").apply { mkdirs() }
    private val cacheFile = File(dir, "cache-$uid.json")
    private val outboxFile = File(dir, "outbox-$uid.json")

    fun readCache(): Cache? = read(cacheFile, Cache.serializer())?.takeIf { it.version == VERSION }

    fun writeCache(cache: Cache) = write(cacheFile, Cache.serializer(), cache)

    fun readOutbox(): Outbox = read(outboxFile, Outbox.serializer()) ?: Outbox()

    fun writeOutbox(outbox: Outbox) = write(outboxFile, Outbox.serializer(), outbox)

    /** Used on sign-out: a shared phone should not keep the ledger around. */
    fun forget() {
        cacheFile.delete()
        outboxFile.delete()
    }

    private fun <T> read(file: File, serializer: kotlinx.serialization.KSerializer<T>): T? {
        if (!file.exists()) return null
        return try {
            Supabase.json.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            // A cache that cannot be read is not worth failing over: the
            // server has the ledger. Drop it and fetch again.
            Log.w(TAG, "Discarding unreadable ${file.name}", e)
            file.delete()
            null
        }
    }

    private fun <T> write(file: File, serializer: kotlinx.serialization.KSerializer<T>, value: T) {
        try {
            // Write beside it and rename. A process killed mid-write must not
            // be able to leave half a ledger where the whole one was.
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(Supabase.json.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write ${file.name}", e)
        }
    }

    private companion object {
        const val TAG = "TallyLocalStore"
        const val VERSION = 1
    }
}
