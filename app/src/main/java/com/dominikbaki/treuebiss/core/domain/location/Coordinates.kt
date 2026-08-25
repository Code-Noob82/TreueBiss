package com.dominikbaki.treuebiss.core.domain.location

/**
 * Geografische Koordinaten als reines Domain-Modell.
 *
 * Bewusst kein `android.location.Location`: Die Domain-Schicht soll ohne
 * Android-Framework auskommen, damit sie in reinen JVM-Tests nutzbar bleibt.
 */
data class Coordinates(
    val latitude: Double,
    val longitude: Double
)
