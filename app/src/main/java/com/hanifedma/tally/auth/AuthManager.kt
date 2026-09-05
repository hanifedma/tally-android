package com.hanifedma.tally.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.hanifedma.tally.data.Supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Google sign-in through Credential Manager — the current API; the old
 * GoogleSignInClient/startActivityForResult flow is deprecated.
 *
 * Google issues an ID token, and Supabase's servers re-verify its signature,
 * issuer and audience before minting a session. What crosses that boundary is
 * a proof, not a claim — a token forged on the device is refused there rather
 * than trusted here.
 *
 * The web app signs in the same way, with the same OAuth *web* client id, so
 * both end up as the same Supabase user looking at the same ledger.
 */
class AuthManager {

    data class Account(val id: String, val email: String?, val avatarUrl: String?, val name: String?)

    private val client get() = Supabase.client()

    /**
     * The signed-in account, or null. Emits immediately with whatever was
     * restored from storage, then on every later change.
     */
    fun accounts(): Flow<Account?> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> status.session.user?.let { user ->
                val meta = user.userMetadata
                Account(
                    id = user.id,
                    email = user.email,
                    avatarUrl = meta?.get("avatar_url")?.let { stringOrNull(it) },
                    name = meta?.get("full_name")?.let { stringOrNull(it) },
                )
            }
            else -> null
        }
    }

    private fun stringOrNull(element: kotlinx.serialization.json.JsonElement): String? =
        (element as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * @return null on success, otherwise a Strings key describing what went
     *         wrong — so the screen can say something true rather than
     *         "error".
     */
    suspend fun signIn(context: Context): String? {
        if (!Supabase.hasGoogleClientId) return "err.auth.config"
        val manager = CredentialManager.create(context)

        val startedAt = android.os.SystemClock.elapsedRealtime()
        val token = try {
            requestToken(manager, context)
        } catch (e: GetCredentialCancellationException) {
            // Play Services reports a *configuration* failure the same way a
            // person pressing back is reported: the chooser is dismissed and
            // Credential Manager calls it a cancellation
            // (status: CANCELED, source: REMOTE_PROVIDER). Swallowing both
            // silently is what made a wrong client id look like a dead
            // button — nothing happened, and nothing said why.
            //
            // The two are still tellable apart by the clock. Refusing the
            // request needs one round trip to Play Services and comes back
            // in well under a second; cancelling means a human read a dialog
            // and decided, which does not. So a cancellation this fast is
            // not a decision, and is worth saying out loud.
            //
            // What it is not is a diagnosis. Play Services refuses for two
            // quite different reasons — no Google account on the device at
            // all, and a client id it will not accept — and reports them
            // identically, so the message names both rather than guessing
            // one and sending someone to edit a file that was already right.
            val elapsed = android.os.SystemClock.elapsedRealtime() - startedAt
            Log.w(TAG, "Credential request cancelled after ${elapsed}ms", e)
            return if (elapsed < NO_UI_SHOWN_MS) "err.auth.unavailable" else "err.auth.cancelled"
        } catch (e: NoCredentialException) {
            return "err.auth.noAccount"
        } catch (e: IOException) {
            return "err.auth.network"
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager failed", e)
            // No account on the device and a device with no Play Services
            // both arrive here, and the difference matters to whoever is
            // holding the phone.
            return if (e.message?.contains("no credentials", ignoreCase = true) == true) {
                "err.auth.noAccount"
            } else "err.auth.generic"
        } catch (e: Exception) {
            Log.e(TAG, "Sign-in failed", e)
            return "err.auth.generic"
        } ?: return "err.auth.noAccount"

        return try {
            client.auth.signInWith(IDToken) {
                idToken = token
                provider = Google
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Supabase sign-in failed", e)
            val message = e.message.orEmpty()
            when {
                message.contains("network", ignoreCase = true) ||
                    message.contains("unable to resolve host", ignoreCase = true) -> "err.auth.network"
                // The most common setup mistake by a mile: the client id is
                // not on Supabase's list of authorised client ids.
                message.contains("audience", ignoreCase = true) ||
                    message.contains("provider", ignoreCase = true) -> "err.auth.config"
                else -> "err.auth.generic"
            }
        }
    }

    private suspend fun requestToken(manager: CredentialManager, context: Context): String? {
        val option = GetGoogleIdOption.Builder()
            // The *web* client id, not the Android one: Credential Manager
            // asks for a server client id, and Supabase verifies the token
            // against that same id.
            .setServerClientId(Supabase.googleWebClientId)
            // false = offer every Google account on the device, not only the
            // ones that have signed into this app before. Filtering makes the
            // common case one tap but hides every other account behind no
            // visible affordance.
            .setFilterByAuthorizedAccounts(false)
            // Skip "one tap" auto-select so the chooser is always shown and
            // switching accounts stays possible.
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        val credential = manager.getCredential(context, request).credential
        return if (
            credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } else {
            null
        }
    }

    suspend fun signOut(context: Context) {
        try {
            client.auth.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "Sign-out call failed; clearing locally anyway", e)
        }
        try {
            // So the next sign-in shows the account chooser instead of
            // silently reusing the same account.
            CredentialManager.create(context)
                .clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't clear credential state", e)
        }
    }

    private companion object {
        const val TAG = "TallyAuth"

        /**
         * Below this, nobody read a dialog and decided anything — so a
         * "cancellation" that arrives this fast came from Play Services
         * refusing the request, not from the person holding the phone.
         */
        const val NO_UI_SHOWN_MS = 1200L
    }
}
