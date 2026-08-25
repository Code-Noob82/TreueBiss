package com.dominikbaki.treuebiss.core.domain.location

/**
 * Kapselt die Logik zur Abfrage des Gerätestandorts.
 */
interface LocationTracker {
    /**
     * Ruft den aktuellen Standort ab.
     * @return Die Koordinaten bei Erfolg, sonst `null` (z. B. ohne Berechtigung).
     */
    suspend fun getCurrentLocation(): Coordinates?

    /** Prüft, ob der Standortdienst (GPS oder Netzwerk) aktiviert ist. */
    fun isLocationEnabled(): Boolean

    /** Prüft, ob die App die Standortberechtigung besitzt. */
    fun hasLocationPermission(): Boolean
}
