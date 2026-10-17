package com.zenithai.countai.di

import android.content.Context
import com.zenithai.countai.data.CountAiDatabase
import com.zenithai.countai.data.SessionRepository
import com.zenithai.countai.repository.AuthRepository
import com.zenithai.countai.repository.CloudSyncRepository
import com.zenithai.countai.repository.NoOpAuthRepository
import com.zenithai.countai.repository.NoOpCloudSyncRepository
import com.zenithai.countai.repository.SupabaseAuthRepository
import com.zenithai.countai.repository.SupabaseSyncRepository
import com.zenithai.countai.sync.SupabaseConfig

/**
 * Tiny hand-rolled service locator (no DI framework — keeps the APK small).
 *
 * The key decision: when Supabase isn't configured, every cloud surface
 * resolves to a NoOp that explains itself, and the app runs 100% offline.
 * UI code never checks BuildConfig directly; it just asks for a repository.
 */
object ServiceLocator {

    @Volatile
    private var sessionRepository: SessionRepository? = null

    fun sessionRepository(context: Context): SessionRepository =
        sessionRepository ?: synchronized(this) {
            sessionRepository ?: SessionRepository(context.applicationContext)
                .also { sessionRepository = it }
        }

    fun database(context: Context) =
        CountAiDatabase.getInstance(context.applicationContext)

    fun authRepository(context: Context): AuthRepository =
        if (SupabaseConfig.isConfigured) {
            SupabaseAuthRepository(context.applicationContext)
        } else {
            NoOpAuthRepository()
        }

    fun syncRepository(context: Context): CloudSyncRepository =
        if (SupabaseConfig.isConfigured) {
            SupabaseSyncRepository(context.applicationContext)
        } else {
            NoOpCloudSyncRepository()
        }
}
