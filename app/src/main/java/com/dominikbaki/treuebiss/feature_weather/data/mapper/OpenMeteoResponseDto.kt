package com.dominikbaki.treuebiss.feature_weather.data.mapper

import com.dominikbaki.treuebiss.feature_weather.data.remote.dto.OpenMeteoResponseDto
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherType

/**
 * Wandelt das Open-Meteo DTO in das saubere Domain-Modell um.
 */
fun OpenMeteoResponseDto.toWeatherData(): WeatherData {
    // Wir prüfen, ob 'current' vorhanden ist. Wenn nicht, werfen wir
    // eine Exception, die im Repository abgefangen wird und zu einem Result.failure führt.
    val current = this.current ?: throw IllegalStateException("Current weather data is missing in API response")

    return WeatherData(
        temperature = current.temperature,
        windSpeed = current.windSpeed,
        humidity = current.humidity,
        pressure = current.pressure,
        weatherType = mapWeatherCodeToType(current.weatherCode)
    )
}

/**
 * Hilfsfunktion, um den WMO-Wetter-Code zu interpretieren.
 *
 */
private fun mapWeatherCodeToType(code: Int): WeatherType {
    return when (code) {
        0 -> WeatherType.ClearSky
        1 -> WeatherType.MainlyClear
        2 -> WeatherType.PartlyCloudy
        3 -> WeatherType.Overcast
        45, 48 -> WeatherType.Fog
        51, 53, 55 -> WeatherType.Rain("leicht")
        56, 57 -> WeatherType.Rain("gefrierend")
        61, 63, 65 -> WeatherType.Rain("mäßig")
        66, 67 -> WeatherType.Rain("starker gefrierender Regen")
        80, 81, 82 -> WeatherType.Rain("stark")
        71, 73, 75 -> WeatherType.Snow("mäßig")
        77 -> WeatherType.Snow("Schneekörner")
        85, 86 -> WeatherType.Snow("stark")
        95 -> WeatherType.Thunderstorm("leicht bis mäßig")
        96, 99 -> WeatherType.Thunderstorm("mit Hagel")
        else -> WeatherType.Unknown
    }
}