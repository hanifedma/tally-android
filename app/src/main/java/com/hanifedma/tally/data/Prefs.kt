package com.hanifedma.tally.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hanifedma.tally.auth.AuthManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefsStore by preferencesDataStore(name = "tally-prefs")

/**
 * What the app needs before it knows who is using it.
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

    /**
     * Whoever was signed in the last time anyone looked, or null.
     *
     * The ledger on this device is filed under an account's id, and the only
     * other place that id is written down is inside the session — which the
     * library will not show to anyone until it has finished reading it back,
     * and will not show at all if it has run out and there is no network to
     * renew it over. Kept here, the app can open what it already holds
     * straight away, and can still open it on a plane.
     *
     * A name and an address, not a credential: nothing here signs anyone in.
     */
    val account: Flow<AuthManager.Account?> = context.prefsStore.data.map { p ->
        p[ACCOUNT_ID]?.let { id ->
            AuthManager.Account(
                id = id,
                email = p[ACCOUNT_EMAIL],
                avatarUrl = p[ACCOUNT_AVATAR],
                name = p[ACCOUNT_NAME],
            )
        }
    }

    suspend fun rememberAccount(account: AuthManager.Account?) {
        context.prefsStore.edit { p ->
            listOf(
                ACCOUNT_ID to account?.id,
                ACCOUNT_EMAIL to account?.email,
                ACCOUNT_AVATAR to account?.avatarUrl,
                ACCOUNT_NAME to account?.name,
            ).forEach { (key, value) -> if (value == null) p.remove(key) else p[key] = value }
        }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val LANG = stringPreferencesKey("lang")
        val MODE = stringPreferencesKey("mode")
        val MIGRATED = stringSetPreferencesKey("migrated")
        val ACCOUNT_ID = stringPreferencesKey("account.id")
        val ACCOUNT_EMAIL = stringPreferencesKey("account.email")
        val ACCOUNT_AVATAR = stringPreferencesKey("account.avatar")
        val ACCOUNT_NAME = stringPreferencesKey("account.name")
    }
}
