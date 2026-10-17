package com.zenithai.countai.repository

import android.content.Context
import com.zenithai.countai.sync.SupabaseClientProvider
import com.zenithai.countai.sync.SyncManager
import io.github.jan_tennert.supabase.auth.auth
import io.github.jan_tennert.supabase.auth.providers.builtin.Email
import io.github.jan_tennert.supabase.auth.providers.builtin.OTP

/**
 * Accounts via Supabase Auth (free tier: 50k monthly active users).
 *
 * The app works fully WITHOUT an account — sign-in only unlocks cloud sync.
 * Auth state lives in supabase-kt (secure storage); the single shared client
 * in [SupabaseClientProvider] owns the session so refresh works under load.
 */
interface AuthRepository {

    val isSignedIn: Boolean

    /** Supabase user UUID, or null when signed out. Used as `user_id` on rows. */
    val currentUserId: String?

    val currentEmail: String?

    suspend fun signIn(email: String, password: String): Result<Unit>

    suspend fun signUp(email: String, password: String): Result<Unit>

    /** Passwordless email link. Returns failure with a message on error. */
    suspend fun sendMagicLink(email: String): Result<Unit>

    suspend fun signOut()
}

/** Used when Supabase isn't configured — the app stays fully offline. */
class NoOpAuthRepository : AuthRepository {
    override val isSignedIn: Boolean = false
    override val currentUserId: String? = null
    override val currentEmail: String? = null

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        Result.failure(
            UnsupportedOperationException(
                "Cloud accounts aren't configured in this build. " +
                    "CountAI works fully offline without sign-in."
            )
        )

    override suspend fun signUp(email: String, password: String): Result<Unit> =
        signIn(email, password)

    override suspend fun sendMagicLink(email: String): Result<Unit> =
        signIn(email, "")

    override suspend fun signOut() { /* nothing to do */ }
}

class SupabaseAuthRepository(private val context: Context) : AuthRepository {

    private fun client() = SupabaseClientProvider.get(context)
        ?: throw IllegalStateException("Supabase is not configured.")

    override val isSignedIn: Boolean
        get() = try {
            SupabaseClientProvider.get(context)?.auth?.currentUserOrNull() != null
        } catch (e: Exception) {
            false
        }

    override val currentUserId: String?
        get() = try {
            SupabaseClientProvider.get(context)?.auth?.currentUserOrNull()?.id
        } catch (e: Exception) {
            null
        }

    override val currentEmail: String?
        get() = try {
            SupabaseClientProvider.get(context)?.auth?.currentUserOrNull()?.email
        } catch (e: Exception) {
            null
        }

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        runCatching {
            client().auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            // Rows created while signed out now belong to this user.
            SyncManager.schedulePeriodic(context)
            SyncManager.syncNow(context)
        }.mapFailure()

    override suspend fun signUp(email: String, password: String): Result<Unit> =
        runCatching {
            client().auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            SyncManager.schedulePeriodic(context)
        }.mapFailure()

    override suspend fun sendMagicLink(email: String): Result<Unit> =
        runCatching {
            client().auth.signInWith(OTP) {
                this.email = email
            }
        }.mapFailure()

    override suspend fun signOut() {
        try {
            SupabaseClientProvider.get(context)?.auth?.signOut()
        } catch (e: Exception) {
            // Best effort — local state is cleared regardless.
        }
        // Stop background sync, drop the session, KEEP local data.
        // ("Delete local data" is a separate explicit button in Settings.)
        SyncManager.cancelAll(context)
        SupabaseClientProvider.reset()
    }

    /** Turn SDK exceptions into short human-readable messages. */
    private fun <T> Result<T>.mapFailure(): Result<T> = recoverCatching { e ->
        val msg = e.message.orEmpty()
        throw AuthFriendlyException(
            when {
                msg.contains("Invalid login credentials", ignoreCase = true) ->
                    "Wrong email or password."
                msg.contains("User already registered", ignoreCase = true) ->
                    "This email already has an account — try signing in."
                msg.contains("Password", ignoreCase = true) &&
                    msg.contains("short", ignoreCase = true) ->
                    "Password must be at least 6 characters."
                msg.contains("Unable to validate", ignoreCase = true) ->
                    "That doesn't look like a valid email address."
                else -> "Sign-in failed. Check your connection and try again."
            }
        )
    }
}

/** Marker so the UI can show the message without a scary stack trace. */
class AuthFriendlyException(message: String) : Exception(message)
