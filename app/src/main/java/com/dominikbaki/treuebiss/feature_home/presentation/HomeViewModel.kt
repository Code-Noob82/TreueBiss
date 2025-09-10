package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import com.dominikbaki.treuebiss.feature_weather.domain.repository.WeatherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Ein Daten-Container, der alle dynamischen Informationen für den HomeScreen bündelt.
// UiState um Wetterdaten und Fehlerbehandlung erweitert
data class HomeUiState(
    val stampCount: Int = 0,
    val voucherCount: Int = 0,
    val dailySpecial: DailySpecial? = null, // Vorerst optional
    val isLoading: Boolean = true,
    val weatherData: WeatherData? = null,
    val weatherError: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val stampRepository: StampRepository,
    private val voucherRepository: VoucherRepository,
    private val weatherRepository: WeatherRepository  // NEU: WeatherRepository
) : ViewModel() {

    // Ein interner StateFlow nur für das Ergebnis des Wetter API-Calls
    private val _weatherState = MutableStateFlow<Result<WeatherData>?>(null)

    // Wir kombinieren die Live-Daten aus beiden Repositories in einen einzigen State.
    val uiState: StateFlow<HomeUiState> =
        combine(
            stampRepository.observeStamps(),
            voucherRepository.observeAll(includeRedeemed = false), // Nur die aktiven Gutscheine
            _weatherState
        ) { stamps, vouchers, weatherResult ->
            HomeUiState(
                stampCount = stamps.size,
                voucherCount = vouchers.size,
                dailySpecial = DailySpecial( // Dummy-Daten für das Tagesangebot
                    title = "Unser Dinkel-Kracher",
                    description = "Heute frisch aus dem Ofen, nur 3,50€!",
                    imageUrl = null
                ),
                weatherData = weatherResult?.getOrNull(),
                weatherError = weatherResult?.exceptionOrNull()?.message,
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState() // Startet mit Ladezustand
        )

    init {
        viewModelScope.launch {
            fetchWeather()
        }
    }

    private suspend fun fetchWeather() {
        val result = weatherRepository.getCurrentWeather(latitude = 49.4875, longitude = 8.4661)
        _weatherState.value = result
    }
}
