package com.dominikbaki.treuebiss.feature_weather.data.remote.api

import com.dominikbaki.treuebiss.feature_weather.data.remote.dto.OpenMeteoResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {

    @GET("forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") currentData: String = "temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m,pressure_msl",
        @Query("timezone") timezone: String = "Europe/Berlin"
    ) : OpenMeteoResponseDto
}