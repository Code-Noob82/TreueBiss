package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.core.domain.repository.AuthRepository
import com.dominikbaki.treuebiss.core.domain.repository.AuthStatus
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

    override fun observeAuthStatus(): Flow<AuthStatus> {
        return supabaseClient.auth.sessionStatus.map { status ->
            Log.d("AuthRepositoryImpl", "New auth status received: $status")
            when (status) {
                is SessionStatus.Authenticated -> AuthStatus.Authenticated
                is SessionStatus.NotAuthenticated -> AuthStatus.NotAuthenticated
                // Alles andere (Laden aus dem Speicher, Netzwerkfehler des SDK)
                // ist noch kein belastbares Ergebnis.
                else -> AuthStatus.Unknown
            }
        }
    }

    /**
     * Der Fehlerfall wird hier absichtlich nicht abgefangen: Ein geschluckter
     * Fehler würde dazu führen, dass der Aufrufer unbegrenzt auf eine Session
     * wartet, die nie kommt.
     */
    override suspend fun signInAnonymously() {
        Log.d("AuthRepositoryImpl", "Signing in anonymously...")
        supabaseClient.auth.signInAnonymously()
        val logMessage =
            "Successfully signed in. User ID: ${supabaseClient.auth.currentUserOrNull()?.id}"
        Log.d("AuthRepositoryImpl", logMessage)
    }
}
