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
     *
     * Wirft eine Exception, wenn die Anmeldung fehlschlägt (z. B. keine
     * Internetverbindung). Der Fehler wird bewusst nicht geschluckt, damit
     * der Aufrufer einen Fehlerzustand anzeigen kann.
     */
    suspend fun signInAnonymously()
}
