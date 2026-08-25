package com.dominikbaki.treuebiss.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Interface für den Zugriff auf gespeicherte Benutzereinstellungen.
 */
interface UserPreferencesRepository {
    /**
     * Ein Flow, der `true` emittiert, wenn der Nutzer das Onboarding abgeschlossen hat.
     */
    val hasCompletedOnboarding: Flow<Boolean>

    /**
     * Setzt den Status des Onboardings auf "abgeschlossen".
     */
    suspend fun setOnboardingCompleted(completed: Boolean)

    /**
     * `true`, sobald die Daten dieser Installation einmal vom Server
     * wiederhergestellt wurden.
     *
     * Die Wiederherstellung läuft bewusst nur einmal pro Installation: Ein
     * Abgleich bei jedem Start könnte lokal gelöschte Stempel (Kartenreset)
     * wieder einspielen, falls die Löschung auf dem Server fehlgeschlagen ist.
     */
    val hasRestoredRemoteData: Flow<Boolean>

    suspend fun setRemoteDataRestored(restored: Boolean)
}