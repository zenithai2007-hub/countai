package com.zenithai.countai.sync

import com.zenithai.countai.BuildConfig

/**
 * Cloud configuration. Credentials live in `local.properties`
 * (SUPABASE_URL / SUPABASE_ANON_KEY) and are baked into BuildConfig at
 * compile time — never hardcoded, never committed.
 *
 * If the keys are missing the app runs 100% offline: counting, history and
 * reports all work, and Settings shows "Cloud: off (not configured)".
 * Fail-soft by design — a missing backend must never crash the app.
 */
object SupabaseConfig {
    val url: String get() = BuildConfig.SUPABASE_URL.trim()
    val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY.trim()

    /** True only when both URL and anon key are present. */
    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
}
