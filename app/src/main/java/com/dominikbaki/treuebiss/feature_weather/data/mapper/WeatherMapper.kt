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
        51, 53, 55 -> WeatherType.Rain(WeatherType.Intensity.Light)          // Niesel
        56, 57 -> WeatherType.Rain(WeatherType.Intensity.Freezing)           // gefrierender Niesel
        61, 63, 65 -> WeatherType.Rain(WeatherType.Intensity.Moderate)
        66, 67 -> WeatherType.Rain(WeatherType.Intensity.Freezing)
        80, 81, 82 -> WeatherType.Rain(WeatherType.Intensity.Heavy)          // Schauer
        71, 73, 75 -> WeatherType.Snow(WeatherType.Intensity.Moderate)
        77 -> WeatherType.Snow(WeatherType.Intensity.Light)                  // Schneekörner
        85, 86 -> WeatherType.Snow(WeatherType.Intensity.Heavy)
        95 -> WeatherType.Thunderstorm(WeatherType.Intensity.Moderate)
        96, 99 -> WeatherType.Thunderstorm(WeatherType.Intensity.Heavy)      // mit Hagel
        else -> WeatherType.Unknown
    }
}
