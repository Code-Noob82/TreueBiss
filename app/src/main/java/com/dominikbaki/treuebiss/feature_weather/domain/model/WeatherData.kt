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
 * Bildet die WMO-Wetter-Codes typsicher ab.
 *
 * Bewusst ohne Anzeigetexte: Die Beschriftung ist Sache der UI-Schicht
 * (siehe `weatherTypeLabel`), damit sie übersetzbar bleibt.
 */
sealed interface WeatherType {
    data object ClearSky : WeatherType
    data object MainlyClear : WeatherType
    data object PartlyCloudy : WeatherType
    data object Overcast : WeatherType
    data object Fog : WeatherType
    data class Rain(val intensity: Intensity) : WeatherType
    data class Snow(val intensity: Intensity) : WeatherType
    data class Thunderstorm(val intensity: Intensity) : WeatherType
    data object Unknown : WeatherType

    /** Stärke eines Niederschlags- oder Gewitterereignisses. */
    enum class Intensity { Light, Moderate, Heavy, Freezing }
}
