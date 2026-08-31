package com.hanifedma.tally.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsStore by preferencesDataStore(name = "tally-prefs")

/**
 * The two settings the app needs before it knows who is using it.
 *
 * Theme and language live on the account, so that changing either on a laptop
 * changes it on the phone. But the sign-in screen has to be painted before
 * there is an account to ask, and repainting it a moment later would be a
 * flash of the wrong colours in the wrong language. So the last known values
 * are mirrored here and used until the real ones arrive.
 */
class Prefs(private val context: Context) {

    val theme: Flow<String> = context.prefsStore.data.map { it[THEME] ?: "dark" }
    val lang: Flow<String> = context.prefsStore.data.map { it[LANG] ?: "en" }

    /**
     * "local" once someone has chosen to work without an account, so they are
     * not asked again every morning and never wait on a network they said
     * they did not want.
     */
    val mode: Flow<String> = context.prefsStore.data.map { it[MODE] ?: "cloud" }

    /** Accounts a device-only ledger has already been offered to, by user id. */
    val migrated: Flow<Set<String>> = context.prefsStore.data.map { it[MIGRATED] ?: emptySet() }

    suspend fun setTheme(value: String) {
        context.prefsStore.edit { it[THEME] = if (value == "light") "light" else "dark" }
    }

    suspend fun setLang(value: String) {
        context.prefsStore.edit { it[LANG] = if (value == "ko") "ko" else "en" }
    }

    suspend fun setMode(value: String) {
        context.prefsStore.edit { it[MODE] = if (value == "local") "local" else "cloud" }
    }

    suspend fun rememberMigrated(uid: String) {
        context.prefsStore.edit { it[MIGRATED] = (it[MIGRATED] ?: emptySet()) + uid }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LANG = stringPreferencesKey("lang")
        val MODE = stringPreferencesKey("mode")
        val MIGRATED = stringSetPreferencesKey("migrated")
    }
}
