package com.zenithai.countai.sync

import android.content.Context
import io.github.jan_tennert.supabase.SupabaseClient
import io.github.jan_tennert.supabase.auth.Auth
import io.github.jan_tennert.supabase.createSupabaseClient
import io.github.jan_tennert.supabase.postgrest.Postgrest
import io.github.jan_tennert.supabase.storage.Storage
import io.ktor.client.engine.android.Android

/**
 * Single shared supabase-kt client for the whole app.
 *
 * One instance = one connection pool = one auth session. Creating a client
 * per screen would multiply sockets and break session refresh under load,
 * so everything (auth, sync, storage) goes through here.
 *
 * Timeouts are deliberately short: a stalled request must fail fast and let
 * WorkManager retry later, never hang the UI or drain the battery.
 */
object SupabaseClientProvider {

    @Volatile
    private var client: SupabaseClient? = null

    /**
     * Returns the shared client, or null when Supabase is not configured.
     * Callers must handle null by running fully offline (fail-soft).
     */
    fun get(context: Context): SupabaseClient? {
        if (!SupabaseConfig.isConfigured) return null
        return client ?: synchronized(this) {
            client ?: createSupabaseClient(
                supabaseUrl = SupabaseConfig.url,
                supabaseKey = SupabaseConfig.anonKey,
                httpEngine = Android.create {
                    // Fail fast: 10s to connect, 30s for the whole request.
                    // WorkManager retries with backoff; the user never waits.
                    connectTimeout = 10_000
                    socketTimeout = 30_000
                },
            ) {
                install(Auth)
                install(Postgrest)
                install(Storage)
            }.also { client = it }
        }
    }

    /** Drop the client (used on sign-out so no stale session lingers). */
    fun reset() {
        synchronized(this) { client = null }
    }
}
