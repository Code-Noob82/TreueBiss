package com.dominikbaki.treuebiss.feature_weather.domain.repository

import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData

/**
 * Port für das Abrufen von Wetterdaten.
 */
interface WeatherRepository {
    // Verwendet Result, um Erfolgs- und Fehlerfälle sauber zu behandeln
    suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherData>
}