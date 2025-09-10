package com.dominikbaki.treuebiss.feature_weather.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Haupt-DTO für die Open-Meteo API-Antwort.
 * Enthält jetzt auch die stündlichen Vorhersagedaten.
 */
@Serializable
data class OpenMeteoResponseDto(
    @SerialName("latitude")
    val latitude: Double,
    @SerialName("longitude")
    val longitude: Double,
    // Annahme: Das 'current'-Feld könnte in einer reinen Vorhersage-Antwort fehlen,
    // daher machen wir es optional (nullable).
    @SerialName("current")
    val current: CurrentWeatherDto? = null,

    // KORREKTUR: Diese Felder sind optional, da wir sie nicht immer anfordern.
    // Wir machen sie nullable und geben ihnen einen Default-Wert von null.
    @SerialName("hourly")
    val hourly: HourlyDto? = null,
    @SerialName("hourly_units")
    val hourlyUnits: HourlyUnitsDto? = null
)

/**
 * DTO für den "current"-Block in der JSON-Antwort.
 */
@Serializable
data class CurrentWeatherDto(
    @SerialName("time")
    val time: String,
    @SerialName("interval")
    val interval: Int,
    @SerialName("temperature_2m")
    val temperature: Double,
    @SerialName("weather_code")
    val weatherCode: Int,
    @SerialName("wind_speed_10m")
    val windSpeed: Double,
    @SerialName("relative_humidity_2m")
    val humidity: Int,
    @SerialName("pressure_msl")
    val pressure: Double
)

/**
 * NEU: DTO für den "hourly"-Block, der die Vorhersagedaten als Listen enthält.
 */
@Serializable
data class HourlyDto(
    @SerialName("time")
    val time: List<String>,
    @SerialName("temperature_2m")
    val temperatures: List<Double>,
    @SerialName("precipitation")
    val precipitations: List<Double>
)

/**
 * NEU: DTO für den "hourly_units"-Block, der die Einheiten für die Vorhersagedaten enthält.
 */
@Serializable
data class HourlyUnitsDto(
    @SerialName("time")
    val time: String,
    @SerialName("temperature_2m")
    val temperature: String,
    @SerialName("precipitation")
    val precipitation: String
)