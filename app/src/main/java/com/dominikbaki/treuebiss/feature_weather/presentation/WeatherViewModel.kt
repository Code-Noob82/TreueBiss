package com.dominikbaki.treuebiss.feature_weather.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.R
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
    data object Loading : WeatherUiState
    data class Success(val weatherData: WeatherData) : WeatherUiState

    /** Die Meldung ist eine String-Ressource, damit sie übersetzbar bleibt. */
    data class Error(@StringRes val messageRes: Int) : WeatherUiState
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val locationTracker: LocationTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    fun fetchWeatherDataForCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading

            // Berechtigung und GPS-Status getrennt prüfen, um präzise
            // Fehlermeldungen geben zu können.
            if (!locationTracker.hasLocationPermission()) {
                _uiState.value = WeatherUiState.Error(R.string.weather_error_no_permission)
                return@launch
            }

            if (!locationTracker.isLocationEnabled()) {
                _uiState.value = WeatherUiState.Error(R.string.weather_error_gps_off)
                return@launch
            }

            // Erst jetzt, wo wir sicher sind, dass alles bereit ist, rufen wir den Standort ab.
            val location = locationTracker.getCurrentLocation()
            if (location != null) {
                weatherRepository.getCurrentWeather(location.latitude, location.longitude)
                    .onSuccess { data ->
                        _uiState.value = WeatherUiState.Success(data)
                    }
                    .onFailure {
                        _uiState.value = WeatherUiState.Error(R.string.weather_error_unknown)
                    }
            } else {
                // Diese Meldung erscheint jetzt nur noch bei einem technischen Fehler
                // (z.B. kein GPS-Signal).
                _uiState.value = WeatherUiState.Error(R.string.weather_error_no_location)
            }
        }
    }
}