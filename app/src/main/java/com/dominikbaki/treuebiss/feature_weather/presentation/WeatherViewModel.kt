package com.dominikbaki.treuebiss.feature_weather.presentation

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.location.LocationTracker
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import com.dominikbaki.treuebiss.feature_weather.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WeatherUiState {
    object Loading : WeatherUiState
    data class Success(val weatherData: WeatherData, val cityName: String? = null) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationTracker: LocationTracker,
    private val appContext: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    fun fetchWeatherDataForCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading

            // KORREKTUR: Wir prüfen die Berechtigungen und den GPS-Status
            // jetzt direkt hier, um präzise Fehlermeldungen zu geben.

            val hasPermission = ContextCompat.checkSelfPermission(
                // KORREKTUR: Verwendung des umbenannten Kontexts
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                _uiState.value = WeatherUiState.Error("Bitte erteile die Berechtigung, um das Wetter anzuzeigen.")
                return@launch
            }

            if (!locationTracker.isLocationEnabled()) {
                _uiState.value = WeatherUiState.Error("Bitte aktiviere den Standort (GPS) auf deinem Gerät.")
                return@launch
            }

            // Erst jetzt, wo wir sicher sind, dass alles bereit ist, rufen wir den Standort ab.
            val location = locationTracker.getCurrentLocation()
            if (location != null) {
                weatherRepository.getCurrentWeather(location.latitude, location.longitude)
                    .onSuccess { data ->
                        _uiState.value = WeatherUiState.Success(data, cityName = "Dein Standort")
                    }
                    .onFailure { error ->
                        _uiState.value = WeatherUiState.Error(error.message ?: "Unbekannter Fehler")
                    }
            } else {
                // Diese Meldung erscheint jetzt nur noch bei einem technischen Fehler
                // (z.B. kein GPS-Signal).
                _uiState.value = WeatherUiState.Error("Der Standort konnte nicht ermittelt werden. Bitte versuche es später erneut.")
            }
        }
    }
}