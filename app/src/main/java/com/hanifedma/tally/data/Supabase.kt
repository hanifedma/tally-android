package com.hanifedma.tally.data

import com.hanifedma.tally.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json

/**
 * The one Supabase client, and the question of whether there is anything to
 * connect to.
 *
 * All three configuration values are public by design and live in
 * supabase.properties (see app/build.gradle.kts). Missing, the app opens on a
 * short setup screen rather than a sign-in button that could never work.
 */
object Supabase {

    val url: String = BuildConfig.SUPABASE_URL
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY
    val googleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID

    val hasUrl: Boolean = url.startsWith("https://") && url.contains(".supabase.")
    val hasKey: Boolean = anonKey.length > 20
    val hasGoogleClientId: Boolean = googleWebClientId.endsWith(".apps.googleusercontent.com")

    /** True when there is a database to talk to at all. */
    val isConfigured: Boolean get() = hasUrl && hasKey

    /**
     * Lenient on purpose. A column added to a table by a newer version of the
     * web app must not stop this build from reading its own ledger — the
     * models already give every field a default for the same reason.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        coerceInputValues = false
    }

    private val instance: SupabaseClient by lazy {
        createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            install(Auth) {
                // The session is kept on the device so the app opens signed
                // in; the refresh token is what keeps it that way for months.
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest) {
                // No session, no request. Left at its default the library
                // sends the request anyway, signed with the anon key — and
                // row level security does not refuse that, it answers it:
                // as nobody, with no rows. An empty answer is one the sync
                // layer believes. It is how a first fetch concludes that the
                // server has never heard of this ledger and sends the whole
                // of it again, or that the account is new and needs its
                // starting categories written over the ones already there.
                //
                // The library is without a session more often than it
                // sounds: it puts the session down every time the app leaves
                // the screen and picks it up again on the way back. Asked in
                // between, this now throws, which the repository already
                // knows how to hear — as "not now", to be tried again.
                requireValidSession = true
            }
            install(Realtime)
            defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(json)
            httpEngine = OkHttp.create()
        }
    }

    fun client(): SupabaseClient {
        check(isConfigured) { "Supabase is not configured — see SETUP.md" }
        return instance
    }
}
