package com.hanifedma.tally.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide when the starting rows follow a setting.
 *
 * Every case here is also a case in the web app's tests.js, against the same
 * two functions written twice. They are small, and they are the only thing
 * standing between "the app guessed Korea and you are in Jakarta" and "the
 * app rewrote the currency of an account that had money in it".
 */
class StarterRulesTest {

    private fun starter(
        id: String = "s1",
        currency: String = "KRW",
        openingMinor: Long = 0,
        deletedAt: String? = null,
    ) = AccountRow(id = id, currency = currency, openingMinor = openingMinor, deletedAt = deletedAt)

    private fun spent(id: String = "t1", deletedAt: String? = null) =
        TransactionRow(id = id, amountMinor = 12000, currency = "KRW", deletedAt = deletedAt)

    private fun buried(id: String) = spent(id, deletedAt = "2026-01-01T00:00:00Z")

    private val noRows = emptyList<TransactionRow>()

    @Test
    fun `empty starter accounts may follow the main currency`() {
        val ids = setOf("s1", "s2")
        assertTrue(startersMayFollow(listOf(starter(), starter(id = "s2")), noRows, ids, "KRW"))
    }

    @Test
    fun `accounts stop following the moment the ledger means something`() {
        val ids = setOf("s1")
        // One transaction anywhere is enough: an amount is filed in this currency.
        assertFalse(startersMayFollow(listOf(starter()), listOf(spent()), ids, "KRW"))
        // An opening balance is money too, even with no transactions.
        assertFalse(startersMayFollow(listOf(starter(openingMinor = 5000)), noRows, ids, "KRW"))
        // An account the person made themselves.
        assertFalse(startersMayFollow(listOf(starter(id = "mine")), noRows, ids, "KRW"))
        // One starter already moved by hand: leave the whole set alone.
        assertFalse(
            startersMayFollow(
                listOf(starter(), starter(id = "s2", currency = "IDR")),
                noRows, setOf("s1", "s2"), "KRW",
            )
        )
        // Nothing to move.
        assertFalse(startersMayFollow(emptyList(), noRows, ids, "KRW"))
        // Deleted starters are not in the way.
        assertTrue(
            startersMayFollow(
                listOf(starter(), starter(id = "s2", deletedAt = "2026-01-01T00:00:00Z")),
                noRows, ids, "KRW",
            )
        )
    }

    @Test
    fun `starting over leaves the accounts free to follow the currency again`() {
        // What "Start over" leaves behind: a drawer full of tombstones and a
        // fresh set of starters. Counting those tombstones is what used to
        // strand every reset ledger in the currency it happened to be reset
        // into — set the main currency to rupiah afterwards and the accounts
        // stayed in won, with a "no rate set for KRW" warning and nothing on
        // screen saying why.
        val ids = setOf("s1", "s2")
        val afterReset = listOf(starter(), starter(id = "s2"))
        val graveyard = listOf(buried("t1"), buried("t2"), buried("t3"))
        assertTrue(startersMayFollow(afterReset, graveyard, ids, "KRW"))

        // And one live row among them is still enough to stop it.
        assertFalse(startersMayFollow(afterReset, graveyard + spent("t4"), ids, "KRW"))
    }

    @Test
    fun `a starter row is renamed only while it still has the name we gave it`() {
        assertEquals("식비", starterRename("Food", "Food", "식비"))
        assertEquals("Food", starterRename("식비", "식비", "Food"))
        // Renamed by the person: their word for it survives the switch.
        assertNull(starterRename("밥값", "Food", "식비"))
        // Already right — nothing to write, and nothing to sync.
        assertNull(starterRename("식비", "Food", "식비"))
        // A seed whose two languages happen to match must never be rewritten
        // to itself.
        assertNull(starterRename("Other", "Other", "Other"))
    }
}
