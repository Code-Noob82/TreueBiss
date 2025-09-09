package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.core.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient
) : AuthRepository {
    override fun observeAuthState(): Flow<Boolean> {
        return supabaseClient.auth.sessionStatus.map { status ->
            Log.d("AuthRepositoryImpl", "New auth status received: $status")
            status is SessionStatus.Authenticated
        }
    }

    override suspend fun signInAnonymously() {
        try {
            Log.d("AuthRepositoryImpl", "Signing in anonymously...")
            supabaseClient.auth.signInAnonymously()
            val logMessage =
                "Successfully signed in. User ID: ${supabaseClient.auth.currentUserOrNull()?.id}"
            Log.d("AuthRepositoryImpl", logMessage)
        } catch (e: Exception) {
            Log.e("AuthRepositoryImpl", "Error signing in anonymously: ${e.message}", e)
        }
    }
}