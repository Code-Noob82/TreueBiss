package com.dominikbaki.treuebiss.feature_weather.domain.model

/**
 * Ein sauberes Datenmodell für Wetterinformationen, entkoppelt von der API.
 */
data class WeatherData(
    val temperature: Double,
    val windSpeed: Double, // in km/h
    val humidity: Int,
    val pressure: Double,  // in hPa
    val weatherType: WeatherType // Ein strukturierter Typ statt nur eines Strings
)

/**
 * Eine 'sealed class', um die verschiedenen WMO-Wetter-Codes abzubilden.
 * Das ermöglicht eine typsichere Behandlung in der UI.
 */
sealed class WeatherType(open val description: String) {
    object ClearSky : WeatherType("Klarer Himmel")
    object MainlyClear : WeatherType("Größtenteils klar")
    object PartlyCloudy : WeatherType("Teilweise bewölkt")
    object Overcast : WeatherType("Bedeckt")
    object Fog : WeatherType("Nebel")
    data class Rain(val intensity: String) : WeatherType("Regen")
    data class Snow(val intensity: String) : WeatherType("Schnee")
    data class Thunderstorm(override val description: String) : WeatherType("Gewitter")
    object Unknown : WeatherType("Unbekanntes Wetter")
}

