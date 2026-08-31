package com.hanifedma.tally.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanifedma.tally.auth.AuthManager
import com.hanifedma.tally.core.Dates
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.SettingsRow
import com.hanifedma.tally.data.LedgerRepository
import com.hanifedma.tally.data.Prefs
import com.hanifedma.tally.data.Supabase
import com.hanifedma.tally.i18n.Strings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Everything the screens need, and nothing they do not.
 *
 * The repository decides what is true; this holds only what is true about
 * *looking* at it — which tab, which month, what is typed in the search box —
 * and forwards edits straight through.
 */
class TallyViewModel(app: Application) : AndroidViewModel(app) {

    enum class Tab { LOG, INSIGHTS, ACCOUNTS }

    data class UiState(
        val booting: Boolean = true,
        val signedIn: Boolean = false,
        val account: AuthManager.Account? = null,
        val tab: Tab = Tab.LOG,
        /** Any day inside the period being shown. */
        val anchor: String = Dates.today(),
        val search: String = "",
        val searching: Boolean = false,
        val insightsIncome: Boolean = false,
        val showArchivedAccounts: Boolean = false,
        val signingIn: Boolean = false,
        /** A Strings key, shown once and cleared. */
        val message: String? = null,
        val messageIsError: Boolean = false,
    )

    private val auth = AuthManager()
    private val prefs = Prefs(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _ledger = MutableStateFlow(Ledger())
    val ledger: StateFlow<Ledger> = _ledger.asStateFlow()

    private val _sync = MutableStateFlow(LedgerRepository.Sync())
    val sync: StateFlow<LedgerRepository.Sync> = _sync.asStateFlow()

    /** Local theme/language, used until the account's own settings arrive. */
    private val _localTheme = MutableStateFlow("dark")
    private val _localLang = MutableStateFlow("en")

    private var repo: LedgerRepository? = null

    /** The theme actually in force. */
    val theme: String
        get() = if (_ui.value.signedIn) _ledger.value.settings.theme else _localTheme.value

    val lang: String
        get() = if (_ui.value.signedIn) _ledger.value.settings.lang else _localLang.value

    val isDark: Boolean get() = theme != "light"

    fun t(key: String, vars: Map<String, Any?> = emptyMap()): String =
        Strings.get(lang, key, vars)

    init {
        viewModelScope.launch {
            _localTheme.value = prefs.theme.first()
            _localLang.value = prefs.lang.first()
            if (!Supabase.isConfigured) {
                _ui.value = _ui.value.copy(booting = false)
                return@launch
            }
            auth.accounts().collect { account -> onAccount(account) }
        }
    }

    private fun onAccount(account: AuthManager.Account?) {
        val previous = _ui.value.account
        if (account == null) {
            repo?.close()
            repo = null
            _ledger.value = Ledger()
            _ui.value = _ui.value.copy(
                booting = false, signedIn = false, account = null, signingIn = false,
            )
            return
        }
        // The same account arriving twice — a token refresh, a resume — must
        // not tear the ledger down and build it again.
        if (previous?.id == account.id && repo != null) {
            _ui.value = _ui.value.copy(account = account, booting = false)
            return
        }

        val repository = LedgerRepository(getApplication<Application>(), account.id, viewModelScope)
        repo = repository
        _ui.value = _ui.value.copy(
            booting = false,
            signedIn = true,
            account = account,
            signingIn = false,
            anchor = Dates.today(),
        )
        viewModelScope.launch {
            repository.ledger.collect { ledger ->
                _ledger.value = ledger
                // The account's own appearance wins over this device's, and
                // is mirrored locally so the next cold start paints correctly.
                if (ledger.settings.theme != _localTheme.value) {
                    _localTheme.value = ledger.settings.theme
                    prefs.setTheme(ledger.settings.theme)
                }
                if (ledger.settings.lang != _localLang.value) {
                    _localLang.value = ledger.settings.lang
                    prefs.setLang(ledger.settings.lang)
                }
            }
        }
        viewModelScope.launch { repository.syncState.collect { _sync.value = it } }
        viewModelScope.launch {
            repository.errors.collect { key ->
                if (key != null) {
                    showMessage(key, isError = true)
                    repository.clearError()
                }
            }
        }
        repository.start()
    }

    // ------------------------------------------------------------
    //  Navigation and view state
    // ------------------------------------------------------------

    fun selectTab(tab: Tab) { _ui.value = _ui.value.copy(tab = tab, searching = false, search = "") }

    fun period(): Dates.Period = Dates.periodOf(_ui.value.anchor, _ledger.value.settings.monthStart)

    fun shiftPeriod(months: Long) {
        val next = Dates.shift(period(), months, _ledger.value.settings.monthStart)
        _ui.value = _ui.value.copy(anchor = next.start)
    }

    fun goToToday() { _ui.value = _ui.value.copy(anchor = Dates.today()) }

    fun goToPeriod(start: String) { _ui.value = _ui.value.copy(anchor = start) }

    fun setSearching(on: Boolean) {
        _ui.value = _ui.value.copy(searching = on, search = if (on) _ui.value.search else "")
    }

    fun setSearch(query: String) { _ui.value = _ui.value.copy(search = query) }

    fun setInsightsIncome(income: Boolean) { _ui.value = _ui.value.copy(insightsIncome = income) }

    fun setShowArchivedAccounts(show: Boolean) {
        _ui.value = _ui.value.copy(showArchivedAccounts = show)
    }

    fun showMessage(key: String, isError: Boolean = false, literal: String? = null) {
        _ui.value = _ui.value.copy(message = literal ?: key, messageIsError = isError)
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }

    // ------------------------------------------------------------
    //  Settings
    // ------------------------------------------------------------

    fun writeSettings(next: SettingsRow) {
        repo?.putSettings(next) ?: run {
            viewModelScope.launch {
                prefs.setTheme(next.theme)
                prefs.setLang(next.lang)
                _localTheme.value = next.theme
                _localLang.value = next.lang
            }
        }
    }

    fun toggleTheme() {
        val next = if (isDark) "light" else "dark"
        if (_ui.value.signedIn) {
            writeSettings(_ledger.value.settings.copy(theme = next))
        } else {
            _localTheme.value = next
            viewModelScope.launch { prefs.setTheme(next) }
        }
    }

    fun toggleLang() {
        val next = if (lang == "ko") "en" else "ko"
        if (_ui.value.signedIn) {
            writeSettings(_ledger.value.settings.copy(lang = next))
        } else {
            _localLang.value = next
            viewModelScope.launch { prefs.setLang(next) }
        }
    }

    // ------------------------------------------------------------
    //  Writing
    // ------------------------------------------------------------

    fun repository(): LedgerRepository? = repo

    fun refresh() = repo?.refresh()

    // ------------------------------------------------------------
    //  Account
    // ------------------------------------------------------------

    fun signIn(context: Context) {
        if (_ui.value.signingIn) return
        _ui.value = _ui.value.copy(signingIn = true)
        viewModelScope.launch {
            val error = auth.signIn(context)
            if (error != null) {
                _ui.value = _ui.value.copy(signingIn = false)
                // Cancelling is a decision, not a failure; saying "sign-in
                // failed" to someone who pressed back is just noise.
                if (error != "err.auth.cancelled") showMessage(error, isError = true)
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            repo?.forgetDevice()
            repo?.close()
            repo = null
            _ledger.value = Ledger()
            auth.signOut(context)
        }
    }

    override fun onCleared() {
        repo?.close()
        super.onCleared()
    }
}
