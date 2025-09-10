package com.dominikbaki.treuebiss.core.domain.usecases

import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import com.dominikbaki.treuebiss.feature_weather.domain.repository.WeatherRepository
import javax.inject.Inject

/**
 * Use Case zum Abrufen und Aktualisieren der Wetterdaten für einen bestimmten Ort.
 *
 * Kapselt den Aufruf und gibt das Result-Objekt zurück, das entweder
 * die erfolgreichen Daten oder einen Fehler enthält.
 */
class RefreshWeather @Inject constructor(
    private val weatherRepository: WeatherRepository
) {
    /**
     * Führt den Use Case aus.
     * @param latitude Der Breitengrad des Ortes.
     * @param longitude Der Längengrad des Ortes.
     * @return Ein Result-Objekt mit den Wetterdaten oder einem Fehler.
     */
    suspend operator fun invoke(latitude: Double, longitude: Double): Result<WeatherData> {
        // KORREKTUR: Ruft die korrekte Funktion 'getCurrentWeather' mit Koordinaten auf.
        return weatherRepository.getCurrentWeather(latitude, longitude)
    }
}