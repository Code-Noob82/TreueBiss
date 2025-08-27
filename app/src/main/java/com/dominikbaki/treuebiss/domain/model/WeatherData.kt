package com.dominikbaki.treuebiss.domain.model

/**
 * Repräsentiert die Wetterdaten.
 */
data class WeatherData(
    val city: String,
    val temperatureCelsius: Double,
    val description: String
)
