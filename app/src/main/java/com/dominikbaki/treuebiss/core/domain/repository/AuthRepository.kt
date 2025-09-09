package com.dominikbaki.treuebiss.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Interface für die Authentifizierungs-Logik.
 */
interface AuthRepository {
    fun observeAuthState(): Flow<Boolean>

    /**
     * Meldet den Nutzer anonym bei Supabase an.
     * Erstellt einen neuen Account, falls noch keiner existiert.
     */
    suspend fun signInAnonymously()
}