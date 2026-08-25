package com.dominikbaki.treuebiss.feature_weather

import com.dominikbaki.treuebiss.feature_weather.data.mapper.toWeatherData
import com.dominikbaki.treuebiss.feature_weather.data.remote.dto.CurrentWeatherDto
import com.dominikbaki.treuebiss.feature_weather.data.remote.dto.OpenMeteoResponseDto
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherType
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherMapperTest {

    private fun response(weatherCode: Int) = OpenMeteoResponseDto(
        latitude = 49.48,
        longitude = 8.46,
        current = CurrentWeatherDto(
            time = "2026-08-25T10:00",
            interval = 900,
            temperature = 21.5,
            weatherCode = weatherCode,
            windSpeed = 12.0,
            humidity = 55,
            pressure = 1013.2
        )
    )

    @Test
    fun `uebernimmt die Messwerte unveraendert`() {
        val data = response(weatherCode = 0).toWeatherData()

        assertEquals(21.5, data.temperature, 0.0)
        assertEquals(12.0, data.windSpeed, 0.0)
        assertEquals(55, data.humidity)
        assertEquals(1013.2, data.pressure, 0.0)
    }

    @Test
    fun `bildet WMO-Codes auf die passenden Wetterlagen ab`() {
        assertEquals(WeatherType.ClearSky, response(0).toWeatherData().weatherType)
        assertEquals(WeatherType.Fog, response(45).toWeatherData().weatherType)
        assertEquals(
            WeatherType.Rain(WeatherType.Intensity.Light),
            response(51).toWeatherData().weatherType
        )
        assertEquals(
            WeatherType.Rain(WeatherType.Intensity.Freezing),
            response(66).toWeatherData().weatherType
        )
        assertEquals(
            WeatherType.Snow(WeatherType.Intensity.Heavy),
            response(85).toWeatherData().weatherType
        )
        assertEquals(
            WeatherType.Thunderstorm(WeatherType.Intensity.Heavy),
            response(99).toWeatherData().weatherType
        )
    }

    @Test
    fun `unbekannte Codes werden nicht zu einer Ausnahme`() {
        assertEquals(WeatherType.Unknown, response(weatherCode = 4711).toWeatherData().weatherType)
    }

    @Test(expected = IllegalStateException::class)
    fun `wirft wenn die Antwort keine aktuellen Werte enthaelt`() {
        OpenMeteoResponseDto(latitude = 0.0, longitude = 0.0, current = null).toWeatherData()
    }
}
