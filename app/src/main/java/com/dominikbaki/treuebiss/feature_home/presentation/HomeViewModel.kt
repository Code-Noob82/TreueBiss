package com.dominikbaki.treuebiss.feature_home.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.location.LocationTracker
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
    val isWeatherLoading: Boolean = true,
    val weatherData: WeatherData? = null,
    val weatherError: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val stampRepository: StampRepository,
    private val voucherRepository: VoucherRepository,
    private val weatherRepository: WeatherRepository,  // NEU: WeatherRepository
    private val locationTracker: LocationTracker // NEU: LocationTracker injiziert
) : ViewModel() {

    // NEU: Eine Variable, die sicherstellt, dass die initiale Wetterabfrage nur einmal gestartet wird.
    private var hasInitialWeatherFetched = false

    // Ein interner StateFlow nur für das Ergebnis des Wetter API-Calls
    private val _weatherState = MutableStateFlow<Result<WeatherData>?>(null)
    private val _isWeatherLoading = MutableStateFlow(false) // Startet jetzt mit false

    // Wir kombinieren die Live-Daten aus beiden Repositories in einen einzigen State.
    val uiState: StateFlow<HomeUiState> =
        combine(
            stampRepository.observeStamps(),
            voucherRepository.observeAll(includeRedeemed = false), // Nur die aktiven Gutscheine
            _weatherState,
            _isWeatherLoading
        ) { stamps, vouchers, weatherResult, isWeatherLoading ->
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
                isWeatherLoading = isWeatherLoading
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState() // Startet mit Ladezustand
        )

    /**
     * NEU: Öffentliche Funktion, die von der UI aufgerufen werden kann,
     * um die Wetterabfrage erneut zu starten.
     */
    fun onRetryWeatherFetch() {
        fetchWeather()
    }

    /**
     * Ruft den Standort ab und aktualisiert den Wetter-Status.
     * Setzt den Ladezustand vor und nach dem Aufruf.
     */
    fun fetchWeather() {

        // KORREKTUR: Wir prüfen, ob die Abfrage bereits läuft oder schon abgeschlossen ist.
        if (hasInitialWeatherFetched || _isWeatherLoading.value) {
            return // Verhindert unnötige, wiederholte Aufrufe
        }
        hasInitialWeatherFetched = true // Markieren, dass wir es jetzt versuchen

        viewModelScope.launch {
            // LOG 1: Wird die Funktion überhaupt aufgerufen?
            Log.d("HomeViewModel_Weather", "fetchWeather() called")
            _isWeatherLoading.value = true

            val location = locationTracker.getCurrentLocation()

            if (location != null) {
                // LOG 2: Haben wir einen Standort vom Tracker bekommen?
                Log.d(
                    "HomeViewModel_Weather",
                    "Location received: Lat=${location.latitude}, Lon=${location.longitude}"
                )
                val result = weatherRepository.getCurrentWeather(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                _weatherState.value = result

                // LOG 4: War der API-Aufruf erfolgreich oder ist er fehlgeschlagen?
                result
                    .onSuccess {
                        Log.d(
                            "HomeViewModel_Weather",
                            "Weather API call was successful."
                        )
                    }
                    .onFailure { error ->
                        Log.e(
                            "HomeViewModel_Weather",
                            "Weather API call failed!",
                            error
                        )
                    }

            } else {
                // LOG 3: Der Standort ist null - das ist der wahrscheinlichste Fehlerpunkt.
                Log.e(
                    "HomeViewModel_Weather",
                    "locationTracker.getCurrentLocation() returned NULL."
                )
                _weatherState.value = Result.failure(
                    Exception("Standort konnte nicht abgerufen werden.")
                )
            }
            _isWeatherLoading.value = false
        }
    }
}
