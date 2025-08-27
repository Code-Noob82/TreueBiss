package com.dominikbaki.treuebiss.domain.repository

import com.dominikbaki.treuebiss.domain.model.WeatherData

/**
 * Port für das Abrufen von Wetterdaten.
 */
interface WeatherRepository {
    /** Liefert Wetterdaten als Result (Netzwerk/Mapping-Fehler werden gekapselt). */
    suspend fun getForCity(city: String): Result<WeatherData>
}