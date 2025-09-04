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
}