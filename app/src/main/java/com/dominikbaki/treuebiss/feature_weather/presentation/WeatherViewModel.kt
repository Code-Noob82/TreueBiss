package com.dominikbaki.treuebiss.feature_weather.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    data class Success(val weatherData: WeatherData) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    init {
        // Mannheim Koordinaten
        fetchWeatherData(latitude = 49.4875, longitude = 8.4661)
    }

    fun fetchWeatherData(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            _uiState.value = WeatherUiState.Loading
            weatherRepository.getCurrentWeather(latitude, longitude)
                .onSuccess { data ->
                    _uiState.value = WeatherUiState.Success(data)
                }
                .onFailure { error ->
                    _uiState.value = WeatherUiState.Error(error.message ?: "Unbekannter Fehler")
                }
        }
    }
}