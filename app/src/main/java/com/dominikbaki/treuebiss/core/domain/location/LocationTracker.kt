package com.dominikbaki.treuebiss.core.domain.location

import android.location.Location

/**
 * Ein Interface, das die Logik zur Abfrage des Gerätestandorts kapselt.
 */
interface LocationTracker {
    /**
     * Ruft den aktuellen Standort des Geräts ab.
     * @return Das Location-Objekt bei Erfolg, ansonsten null (z.B. wenn Berechtigungen fehlen).
     */
    suspend fun getCurrentLocation(): Location?

    /**
     * NEU: Prüft, ob der Standortdienst (GPS oder Netzwerk) auf dem Gerät aktiviert ist.
     * @return True, wenn der Standort aktiviert ist, ansonsten false.
     */
    fun isLocationEnabled(): Boolean
}