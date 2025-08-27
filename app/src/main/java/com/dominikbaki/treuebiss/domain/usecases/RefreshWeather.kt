package com.dominikbaki.treuebiss.domain.usecases

import com.dominikbaki.treuebiss.domain.model.WeatherData
import com.dominikbaki.treuebiss.domain.repository.WeatherRepository
import javax.inject.Inject

/**
 * Use Case zum Abrufen und Aktualisieren der Wetterdaten für eine Stadt.
 *
 * Kapselt den Aufruf und gibt das Result-Objekt zurück, das entweder
 * die erfolgreichen Daten oder einen Fehler enthält.
 */
class RefreshWeather @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    suspend operator fun invoke(city: String): Result<WeatherData> {
        return weatherRepository.getForCity(city)
    }
}