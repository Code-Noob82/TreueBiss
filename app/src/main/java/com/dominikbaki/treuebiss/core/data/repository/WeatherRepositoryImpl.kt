package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.feature_weather.data.mapper.toWeatherData
import com.dominikbaki.treuebiss.feature_weather.data.remote.api.WeatherApiService
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import com.dominikbaki.treuebiss.feature_weather.domain.repository.WeatherRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val apiService: WeatherApiService
) : WeatherRepository {

    override suspend fun getCurrentWeather(latitude: Double, longitude: Double): Result<WeatherData> {
        return try {
            val response = apiService.getCurrentWeather(
                latitude = latitude,
                longitude = longitude
            )
            Result.success(response.toWeatherData())
        } catch (e: Exception) {
            Log.e("WeatherRepo", "Error fetching weather data from Open-Meteo", e)
            Result.failure(e)
        }
    }
}