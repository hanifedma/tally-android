package com.hanifedma.tally.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hanifedma.tally.auth.AuthManager
import com.hanifedma.tally.core.Dates
import com.hanifedma.tally.core.Ledger
import com.hanifedma.tally.core.SettingsRow
import com.hanifedma.tally.data.LedgerRepository
import com.hanifedma.tally.data.LocalStore
import com.hanifedma.tally.data.Prefs
import com.hanifedma.tally.data.Supabase
import com.hanifedma.tally.i18n.Strings
import kotlinx.coroutines.Job
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
 *
 * @param saved where the looking is kept, so that it outlives the process.
 *   Android stops an app it cannot see whenever it wants the memory, and
 *   starts it again when the person comes back — a new process, a new one of
 *   these, and until this was here, the app's first screen: this month, every
 *   account, no search. Coming back from looking something up in a banking
 *   app is exactly when that happens, and exactly when it is least welcome.
 *   What is saved here lasts as long as the app's place among the recent
 *   apps does; closing it from there is a fresh start, as it should be.
 */
class TallyViewModel(
    app: Application,
    private val saved: SavedStateHandle,
) : AndroidViewModel(app) {

    enum class Tab { LOG, INSIGHTS, ACCOUNTS }

    data class UiState(
        val booting: Boolean = true,
        val signedIn: Boolean = false,
        /** True when this ledger lives on this device and goes nowhere else. */
        val local: Boolean = false,
        /** Transactions waiting on this device, when signing in could take them. */
        val offerMigration: Int = 0,
        val migrating: Boolean = false,
        val account: AuthManager.Account? = null,
        val tab: Tab = Tab.LOG,
        /** Any day inside the period being shown. */
        val anchor: String = Dates.today(),
        val search: String = "",
        val searching: Boolean = false,
        val insightsIncome: Boolean = false,
        val showArchivedAccounts: Boolean = false,
        /** Showing one account only, by id. Null is every account. */
        val accountFilter: String? = null,
        val signingIn: Boolean = false,
        /** A Strings key, shown once and cleared. */
        val message: String? = null,
        val messageIsError: Boolean = false,
    )

    private val auth = AuthManager()
    private val prefs = Prefs(app)

    private val _ui = MutableStateFlow(
        // Picked up where it was left, if it was left anywhere. Each value is
        // checked rather than trusted: a tab this build has no name for, or a
        // day that is not one, falls back to where a first launch starts.
        UiState(
            tab = saved.get<String>(TAB)
                ?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: Tab.LOG,
            anchor = saved.get<String>(ANCHOR)?.takeIf { Dates.isDayKey(it) } ?: Dates.today(),
            search = saved.get<String>(SEARCH) ?: "",
            searching = saved.get<Boolean>(SEARCHING) ?: false,
            insightsIncome = saved.get<Boolean>(INCOME) ?: false,
            showArchivedAccounts = saved.get<Boolean>(ARCHIVED) ?: false,
            accountFilter = saved.get<String>(FILTER),
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _ledger = MutableStateFlow(Ledger())
    val ledger: StateFlow<Ledger> = _ledger.asStateFlow()

    private val _sync = MutableStateFlow(LedgerRepository.Sync())
    val sync: StateFlow<LedgerRepository.Sync> = _sync.asStateFlow()

    /** Local theme/language, used until the account's own settings arrive. */
    private val _localTheme = MutableStateFlow("dark")
    private val _localLang = MutableStateFlow("en")

    /**
     * The same two values, for the screens.
     *
     * They have to be collectable rather than read through `theme`/`lang`
     * below. Those are plain getters over a StateFlow's `.value`, which is
     * not a snapshot read: Compose never learns that anyone looked, so
     * changing one recomposes nothing. Signed in that went unnoticed, because
     * every change also emitted a new ledger and `ledger` *is* collected —
     * the recomposition it caused re-read the getter and the theme appeared
     * to work. On the login screen there is no ledger to emit, so the toggle
     * did nothing at all.
     */
    val localTheme: StateFlow<String> = _localTheme.asStateFlow()
    val localLang: StateFlow<String> = _localLang.asStateFlow()

    private var repo: LedgerRepository? = null

    /** What is listening to [repo], so it can be told to stop; see [detach]. */
    private var feed: Job? = null

    /** The one collector of `auth.sessions()`; see watchAccount. */
    private var authJob: Job? = null

    /** True from asking to be signed out until the library has done it. */
    private var signingOut = false

    /** True once a ledger is open, whichever way in was taken. */
    private val active: Boolean
        get() = _ui.value.signedIn || _ui.value.local

    /** The theme actually in force. */
    val theme: String
        get() = if (active) _ledger.value.settings.theme else _localTheme.value

    val lang: String
        get() = if (active) _ledger.value.settings.lang else _localLang.value

    val isDark: Boolean get() = theme != "light"

    fun t(key: String, vars: Map<String, Any?> = emptyMap()): String =
        Strings.get(lang, key, vars)

    init {
        viewModelScope.launch {
            _localTheme.value = prefs.theme.first()
            _localLang.value = prefs.lang.first()

            // Someone who chose to work without an account is not asked again
            // every morning, and never waits on a network they said they did
            // not want.
            if (prefs.mode.first() == "local") {
                startLocal()
                return@launch
            }
            if (!Supabase.isConfigured) {
                _ui.value = _ui.value.copy(booting = false)
                return@launch
            }
            // Whoever was here last, opened on what this device already
            // holds, before the network has been asked anything.
            //
            // The session takes a moment to be read back, and after an hour
            // away it has run out and has to be renewed over the network
            // first — a second on a good connection and never on none. The
            // ledger is on the device precisely so that nobody has to wait
            // for that, so it is shown now and connected when the answer
            // comes. If the answer is "signed out", it is closed again.
            prefs.account.first()?.let { open(it, connected = false) }
            watchAccount()
        }
    }

    /**
     * Follow the signed-in account, and only ever from one place.
     *
     * `sessionStatus` is a hot flow that emits more than once on the way up —
     * initialising, then not-authenticated. Left running after someone
     * chooses to work without an account, that second emission arrives as a
     * sign-out, which closes the repository and empties the ledger: tap
     * "without an account" quickly enough and the device ledger was shut
     * down underneath the screen still showing it. Cancelling first also
     * stops `leaveLocal` stacking a second collector on every visit.
     */
    private fun watchAccount() {
        authJob?.cancel()
        authJob = viewModelScope.launch {
            auth.sessions().collect { session -> onSession(session) }
        }
    }

    // ------------------------------------------------------------
    //  Without an account
    // ------------------------------------------------------------

    /**
     * Work on this device only: the same ledger and the same rules, with
     * every path to the network closed.
     */
    fun useLocal() {
        // Stop following the account first: an emission arriving after this
        // would tear down the very ledger being opened.
        authJob?.cancel()
        authJob = null
        viewModelScope.launch {
            prefs.setMode("local")
            startLocal()
        }
    }

    private fun startLocal() {
        val repository = LedgerRepository(
            getApplication<Application>(),
            LedgerRepository.LOCAL_UID,
            viewModelScope,
            local = true,
            // Carry whatever was chosen on the way in, so a first-run ledger
            // is seeded in the right language and painted in the right theme.
            // Only used if this device has no ledger yet — writing it here
            // would save an empty cache over a real one.
            defaults = SettingsRow(theme = _localTheme.value, lang = _localLang.value),
        )
        lookAt(LedgerRepository.LOCAL_UID)
        attach(repository)
        _ui.value = _ui.value.copy(
            booting = false,
            signedIn = false,
            local = true,
            account = null,
            signingIn = false,
        )
    }

    /**
     * Leave device-only mode for a real account. The device ledger is left
     * exactly where it is — signing in offers to copy it, and refusing that
     * offer must not be the same as throwing it away.
     */
    fun leaveLocal() {
        // Put down before anything is asked about an account, not after. Done
        // the other way round — behind a write to disk, with the question
        // already out — a session that answered quickly had its ledger opened
        // and then closed by the tidying-up that was meant for this one.
        detach()
        _ledger.value = Ledger()
        _ui.value = _ui.value.copy(local = false, signedIn = false, account = null)
        viewModelScope.launch { prefs.setMode("cloud") }
        if (Supabase.isConfigured) watchAccount()
    }

    /**
     * Back to a first day, whichever kind of ledger this is.
     *
     * A device-only one has nowhere to send a tombstone and no other reader
     * to convince, so the cache goes and it seeds itself again — the same
     * destination by a shorter road, and without leaving thousands of
     * tombstones behind for nobody. A signed-in one has to say what happened
     * out loud, so every row is buried in place and the starting set written
     * back over it.
     */
    fun resetAll() {
        if (_ui.value.local) {
            eraseLocal()
            return
        }
        val repository = repo ?: return
        viewModelScope.launch {
            try {
                repository.resetAll()
                showMessage("reset.done")
            } catch (e: Exception) {
                Log.w("TallyViewModel", "Couldn't finish the reset", e)
                showMessage("reset.failed", isError = true)
            }
        }
    }

    /** Throw away the device-only ledger and start again, still local. */
    fun eraseLocal() {
        viewModelScope.launch {
            repo?.forgetDevice()
            detach()
            _ledger.value = Ledger()
            // A first day shows this month and every account, not wherever
            // the ledger that has just gone was being read.
            forgetView()
            startLocal()
            showMessage("local.erased")
        }
    }

    fun dismissMigration() {
        val uid = _ui.value.account?.id
        _ui.value = _ui.value.copy(offerMigration = 0)
        if (uid != null) viewModelScope.launch { prefs.rememberMigrated(uid) }
    }

    /** Copy the device-only ledger into the account just signed into. */
    fun acceptMigration() {
        val uid = _ui.value.account?.id ?: return
        val repository = repo ?: return
        _ui.value = _ui.value.copy(migrating = true)
        viewModelScope.launch {
            try {
                val cache = LocalStore(getApplication(), LedgerRepository.LOCAL_UID).readCache()
                if (cache != null) repository.importLocal(cache)
                prefs.rememberMigrated(uid)
                showMessage("migrate.done")
            } catch (e: Exception) {
                Log.w("TallyViewModel", "Couldn't copy the device ledger", e)
                showMessage("migrate.failed", isError = true)
            }
            _ui.value = _ui.value.copy(migrating = false, offerMigration = 0)
        }
    }

    // ------------------------------------------------------------
    //  With one
    // ------------------------------------------------------------

    /**
     * What the library has to say about who is signed in.
     *
     * Only two of its four answers are about that. The other two are about
     * whether the server can be spoken to at the moment, and a ledger that is
     * open stays open through both — see [AuthManager.Session] for what it
     * cost when they were all heard as "signed out".
     */
    private fun onSession(session: AuthManager.Session) {
        when (session) {
            // Not while a sign-out is on its way to the server: until it
            // lands the library still holds the old session, and anything it
            // says about it — a token refreshed at just the wrong moment —
            // would open the ledger that was closed a moment ago.
            is AuthManager.Session.SignedIn -> if (!signingOut) onSignedIn(session.account)
            AuthManager.Session.SignedOut -> onSignedOut()
            // No answer yet. With a ledger open it goes quiet until there is
            // one; with none open there is nothing to do but keep waiting,
            // and the splash screen is still up to do the waiting behind.
            AuthManager.Session.Loading -> repo?.pause()
            AuthManager.Session.Unreachable -> {
                val repository = repo
                if (repository != null) {
                    repository.pause(unreachable = true)
                } else {
                    // Nothing of this account on this device to show, and no
                    // way to fetch it. The sign-in screen is at least true.
                    _ui.value = _ui.value.copy(booting = false, signingIn = false)
                }
            }
        }
    }

    private fun onSignedIn(account: AuthManager.Account) {
        val previous = _ui.value.account
        if (previous?.id == account.id && repo != null) {
            // The same account arriving again — a token refresh, the session
            // picked back up after a trip to another app, or the answer to a
            // ledger opened before it was known. None of them is a reason to
            // tear the ledger down and build it again; the last two are the
            // moment to start talking to the server.
            _ui.value = _ui.value.copy(account = account, booting = false, signingIn = false)
            repo?.resume()
        } else {
            open(account, connected = true)
        }
        if (account != previous) viewModelScope.launch { prefs.rememberAccount(account) }
    }

    /**
     * Open [account]'s ledger.
     *
     * @param connected false to show what this device holds without a word
     *   to the server, for a ledger opened ahead of its session.
     */
    private fun open(account: AuthManager.Account, connected: Boolean) {
        val repository = LedgerRepository(
            getApplication<Application>(),
            account.id,
            viewModelScope,
            // What this device was last painted in, for the one case where
            // the ledger has nothing to say about it: an account's first
            // sign-in anywhere. The choice made on the sign-in screen is then
            // the one the account starts with, here and on the web, rather
            // than being flattened to dark and English a moment later.
            defaults = SettingsRow(theme = _localTheme.value, lang = _localLang.value),
        )
        lookAt(account.id)
        // The ledger before the flag that puts it on screen, so there is
        // never a frame of somebody's account with nothing in it.
        attach(repository, paused = !connected)
        _ui.value = _ui.value.copy(
            booting = false,
            signedIn = true,
            local = false,
            account = account,
            signingIn = false,
        )
        offerMigrationIfAny(account.id)
    }

    private fun onSignedOut() {
        detach()
        _ledger.value = Ledger()
        // Whoever signs in next starts from the top, even if it is the same
        // person: signing out is a way of saying "done here".
        forgetView()
        _ui.value = _ui.value.copy(
            booting = false, signedIn = false, account = null, signingIn = false,
            offerMigration = 0, migrating = false,
        )
        viewModelScope.launch {
            if (prefs.account.first() != null) prefs.rememberAccount(null)
        }
    }

    /**
     * Point the screens at a ledger. The only difference between a signed-in
     * one and a device-only one is how it was built — everything from here
     * on is the same either way.
     */
    private fun attach(repository: LedgerRepository, paused: Boolean = false) {
        detach()
        repo = repository
        // Started before anything listens, and its first answer taken by
        // hand. Listening first meant the first thing heard was the empty
        // ledger every repository begins as: a frame of no accounts and no
        // transactions, in the default theme and the default language, before
        // the real one arrived a moment later — and long enough for anything
        // that depended on an account existing to conclude that it did not.
        repository.start(paused)
        show(repository.ledger.value)
        _localTheme.value = repository.ledger.value.settings.theme
        _localLang.value = repository.ledger.value.settings.lang
        _sync.value = repository.syncState.value

        feed = viewModelScope.launch {
            launch {
                repository.ledger.collect { ledger ->
                    show(ledger)
                    // The account's own appearance wins over this device's,
                    // and is mirrored locally so the next cold start paints
                    // correctly.
                    _localTheme.value = ledger.settings.theme
                    _localLang.value = ledger.settings.lang
                    if (ledger.settings.theme != prefs.theme.first()) {
                        prefs.setTheme(ledger.settings.theme)
                    }
                    if (ledger.settings.lang != prefs.lang.first()) {
                        prefs.setLang(ledger.settings.lang)
                    }
                }
            }
            launch { repository.syncState.collect { _sync.value = it } }
            launch {
                repository.errors.collect { key ->
                    if (key != null) {
                        showMessage(key, isError = true)
                        repository.clearError()
                    }
                }
            }
        }
    }

    /**
     * Let go of the ledger on screen, if there is one.
     *
     * The listeners go first. A repository that has been closed can still
     * have a fetch on its way back, and it publishes what it learned to
     * whoever is still listening — which, left attached, was the screens:
     * sign out of one account and into another quickly enough and the first
     * one's ledger could arrive on top of the second's.
     */
    private fun detach() {
        feed?.cancel()
        feed = null
        repo?.close()
        repo = null
    }

    /** Put a ledger on screen. */
    private fun show(ledger: Ledger) {
        val before = _ledger.value
        _ledger.value = ledger
        // The account being looked at has been deleted — here, or on another
        // device. Its filter goes with it, or it would come back to life,
        // unasked, the day an account happened to reappear under that id.
        //
        // "Was there and now is not", and nothing weaker. An account that is
        // merely not there *yet* — a ledger still being fetched, the instant
        // after the app is started again — is the one case that must not
        // count, because that is the moment a saved filter is put back.
        val shown = _ui.value.accountFilter ?: return
        if (before.account(shown) != null && ledger.account(shown) == null) filterByAccount(null)
    }

    /**
     * Is there device-only data worth offering to copy into this account?
     * Asked once per account: someone who said "start fresh" is not asked
     * again every time they open the app.
     */
    private fun offerMigrationIfAny(uid: String) {
        viewModelScope.launch {
            if (uid in prefs.migrated.first()) return@launch
            val cache = LocalStore(getApplication(), LedgerRepository.LOCAL_UID).readCache()
            val n = cache?.transactions?.count { it.deletedAt == null } ?: 0
            // Still the account that was asked about, by the time the answer
            // is in: reading a ledger off the disk takes long enough for
            // someone to have signed out.
            if (n > 0 && _ui.value.account?.id == uid) {
                _ui.value = _ui.value.copy(offerMigration = n)
            }
        }
    }

    // ------------------------------------------------------------
    //  Navigation and view state
    // ------------------------------------------------------------

    /**
     * Change what is being looked at, and write it down — see [saved].
     *
     * Every change to the view goes through here, so that there is no way to
     * move the screen without the move being remembered.
     */
    private inline fun look(change: (UiState) -> UiState) {
        val next = change(_ui.value)
        _ui.value = next
        saved[TAB] = next.tab.name
        saved[ANCHOR] = next.anchor
        saved[SEARCH] = next.search
        saved[SEARCHING] = next.searching
        saved[INCOME] = next.insightsIncome
        saved[ARCHIVED] = next.showArchivedAccounts
        saved[FILTER] = next.accountFilter
    }

    /**
     * Start looking at one ledger: [owner] is an account's id, or the name a
     * device-only ledger goes by.
     *
     * A view that was saved is kept if it was a view of this ledger, which is
     * what coming back after the process was stopped looks like. Any other
     * ledger starts from the top — one account's filter means nothing in
     * another's, and its month is nobody's business.
     */
    private fun lookAt(owner: String) {
        if (saved.get<String>(OWNER) == owner) return
        forgetView()
        saved[OWNER] = owner
    }

    private fun forgetView() {
        saved.remove<String>(OWNER)
        look {
            it.copy(
                tab = Tab.LOG,
                anchor = Dates.today(),
                search = "",
                searching = false,
                insightsIncome = false,
                showArchivedAccounts = false,
                accountFilter = null,
            )
        }
    }

    fun selectTab(tab: Tab) = look { it.copy(tab = tab, searching = false, search = "") }

    fun period(): Dates.Period = Dates.periodOf(_ui.value.anchor, _ledger.value.settings.monthStart)

    fun shiftPeriod(months: Long) {
        val next = Dates.shift(period(), months, _ledger.value.settings.monthStart)
        look { it.copy(anchor = next.start) }
    }

    fun goToToday() = look { it.copy(anchor = Dates.today()) }

    fun goToPeriod(start: String) = look { it.copy(anchor = start) }

    fun setSearching(on: Boolean) = look {
        it.copy(searching = on, search = if (on) it.search else "")
    }

    fun setSearch(query: String) = look { it.copy(search = query) }

    fun setInsightsIncome(income: Boolean) = look { it.copy(insightsIncome = income) }

    fun setShowArchivedAccounts(show: Boolean) = look { it.copy(showArchivedAccounts = show) }

    /**
     * Show one account's money and nothing else, or all of it again.
     *
     * Picking one moves to the log, because that is what was asked for; a
     * search in progress is left alone, and simply searches inside the
     * account now.
     */
    fun filterByAccount(id: String?) = look {
        it.copy(accountFilter = id, tab = if (id != null) Tab.LOG else it.tab)
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
        if (active) {
            writeSettings(_ledger.value.settings.copy(theme = next))
        } else {
            _localTheme.value = next
            viewModelScope.launch { prefs.setTheme(next) }
        }
    }

    fun toggleLang() {
        val next = if (lang == "ko") "en" else "ko"
        if (active) {
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
        if (signingOut) return
        signingOut = true
        // A shared phone should not keep the ledger, nor remember whose it
        // was — and should say so at once, rather than sit on an empty ledger
        // for as long as the server takes to be told.
        repo?.forgetDevice()
        onSignedOut()
        viewModelScope.launch {
            try {
                prefs.rememberAccount(null)
                auth.signOut(context)
            } finally {
                signingOut = false
                // Ask again, now that answers are being listened to: whatever
                // the library said in the meantime went unheard. Unless the
                // person has since chosen to work without an account, which
                // is not a question for the library at all.
                if (authJob != null) watchAccount()
            }
        }
    }

    override fun onCleared() {
        detach()
        super.onCleared()
    }

    private companion object {
        // Where the view is kept. See the note on `saved`, up top.
        const val OWNER = "view.owner"
        const val TAB = "view.tab"
        const val ANCHOR = "view.anchor"
        const val SEARCH = "view.search"
        const val SEARCHING = "view.searching"
        const val INCOME = "view.income"
        const val ARCHIVED = "view.archived"
        const val FILTER = "view.account"
    }
}
