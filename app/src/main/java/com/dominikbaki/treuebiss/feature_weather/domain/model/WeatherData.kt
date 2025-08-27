package com.dominikbaki.treuebiss.feature_weather.domain.model

/**
 * Repräsentiert die Wetterdaten.
 */
data class WeatherData(
    val city: String,
    val temperatureCelsius: Double,
    val description: String,
    val iconUrl: String
)