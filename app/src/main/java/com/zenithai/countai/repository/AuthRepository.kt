package com.zenithai.countai.repository

/**
 * FUTURE — user accounts & sign-in (Firebase Auth / Supabase / custom backend).
 *
 * The interface is defined now so that no UI code ever talks to an auth SDK
 * directly; the real implementation will plug in here later. v1 ships with
 * [NoOpAuthRepository] and the app works fully without an account.
 */
interface AuthRepository {

    val isSignedIn: Boolean

    suspend fun signIn(email: String, password: String): Result<Unit>

    suspend fun signOut()
}

/** Placeholder used until real auth ships. Every call explains itself. */
class NoOpAuthRepository : AuthRepository {

    override val isSignedIn: Boolean = false

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        Result.failure(
            UnsupportedOperationException(
                "Accounts are a planned feature (see README → Roadmap). " +
                    "CountAI v1 works fully offline without sign-in."
            )
        )

    override suspend fun signOut() {
        // nothing to do
    }
}
