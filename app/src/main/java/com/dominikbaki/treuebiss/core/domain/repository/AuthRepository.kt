package com.dominikbaki.treuebiss.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Anmeldestatus der aktuellen Installation.
 *
 * Bewusst kein `Boolean`: Beim Start meldet das Backend kurz "lädt noch", und
 * dieser Zustand darf nicht mit "nicht angemeldet" verwechselt werden - sonst
 * legt die App einen zweiten anonymen Account an oder wartet unnötig ab.
 */
enum class AuthStatus {
    /** Noch unbekannt, z. B. während eine gespeicherte Session geladen wird. */
    Unknown,
    Authenticated,
    NotAuthenticated
}

/**
 * Interface für die Authentifizierungs-Logik.
 */
interface AuthRepository {
    fun observeAuthStatus(): Flow<AuthStatus>

    /**
     * Meldet den Nutzer anonym bei Supabase an.
     * Erstellt einen neuen Account, falls noch keiner existiert.
     *
     * Wirft eine Exception, wenn die Anmeldung fehlschlägt (z. B. keine
     * Internetverbindung). Der Fehler wird bewusst nicht geschluckt, damit
     * der Aufrufer ihn behandeln kann.
     */
    suspend fun signInAnonymously()
}
