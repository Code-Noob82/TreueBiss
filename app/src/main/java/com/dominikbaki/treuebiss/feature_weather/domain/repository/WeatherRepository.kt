package com.dominikbaki.treuebiss.feature_weather.domain.repository

import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData

/**
 * Port für das Abrufen von Wetterdaten.
 */
interface WeatherRepository {
    /** Liefert Wetterdaten als Result (Netzwerk/Mapping-Fehler werden gekapselt). */
    suspend fun getForCity(city: String): Result<WeatherData>
}