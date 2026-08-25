package com.dominikbaki.treuebiss.feature_home.presentation

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.R
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
import kotlinx.datetime.Clock
import javax.inject.Inject

// Ein Daten-Container, der alle dynamischen Informationen für den HomeScreen bündelt.
// UiState um Wetterdaten und Fehlerbehandlung erweitert
data class HomeUiState(
    val stampCount: Int = 0,
    val voucherCount: Int = 0,
    val dailySpecial: DailySpecial? = null, // Vorerst optional
    val isWeatherLoading: Boolean = false, // Startwert kann false sein, da der init-Block das Laden steuert
    val weatherData: WeatherData? = null,
    /** Fehlermeldung als String-Ressource, damit sie übersetzbar bleibt. */
    @StringRes val weatherErrorRes: Int? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val stampRepository: StampRepository,
    private val voucherRepository: VoucherRepository,
    private val weatherRepository: WeatherRepository,  // NEU: WeatherRepository
    private val locationTracker: LocationTracker // NEU: LocationTracker injiziert
) : ViewModel() {


    // Ein interner StateFlow nur für das Ergebnis des Wetter API-Calls
    private val _weatherState = MutableStateFlow<WeatherData?>(null)
    private val _weatherErrorRes = MutableStateFlow<Int?>(null)
    private val _isWeatherLoading = MutableStateFlow(false) // Startet jetzt mit false

    // Wir kombinieren die Live-Daten aus beiden Repositories in einen einzigen State.
    val uiState: StateFlow<HomeUiState> =
        combine(
            stampRepository.observeStamps(),
            voucherRepository.observeOpenVouchers(),
            combine(_weatherState, _weatherErrorRes) { data, errorRes -> data to errorRes },
            _isWeatherLoading
        ) { stamps, vouchers, weather, isWeatherLoading ->
            val now = Clock.System.now()
            HomeUiState(
                stampCount = stamps.size,
                // Abgelaufene Gutscheine sind nicht mehr einlösbar und
                // dürfen die angezeigte Anzahl nicht aufblähen.
                voucherCount = vouchers.count { it.isRedeemableAt(now) },
                dailySpecial = DailySpecial( // Dummy-Daten für das Tagesangebot
                    titleRes = R.string.home_daily_special_title,
                    descriptionRes = R.string.home_daily_special_description,
                    imageUrl = null
                ),
                weatherData = weather.first,
                weatherErrorRes = weather.second,
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
     * Diese Funktion ist jetzt sowohl für den initialen als auch für manuelle Aufrufe zuständig.
     */
    fun fetchWeather() {

        // VÄNDERUNG: Wir prüfen nur noch, ob bereits eine Abfrage läuft.
        if (_isWeatherLoading.value) {
            return // Verhindert doppelte Aufrufe, während schon geladen wird.
        }

        viewModelScope.launch {
            // LOG 1: Wird die Funktion überhaupt aufgerufen?
            Log.d("HomeViewModel_Weather",
                "fetchWeather() called")
            _isWeatherLoading.value = true
            _weatherErrorRes.value = null // Fehler vom vorherigen Versuch zurücksetzen

            val location = locationTracker.getCurrentLocation()

            if (location != null) {
                // LOG 2: Haben wir einen Standort vom Tracker bekommen?
                Log.d(
                    "HomeViewModel_Weather",
                    "Location received: Lat=${location.latitude}, Lon=${location.longitude}"
                )
                weatherRepository.getCurrentWeather(
                    latitude = location.latitude,
                    longitude = location.longitude
                ).onSuccess { data ->
                    Log.d("HomeViewModel_Weather", "Weather API call was successful.")
                    _weatherState.value = data
                }.onFailure { error ->
                    Log.e("HomeViewModel_Weather", "Weather API call failed!", error)
                    _weatherErrorRes.value = R.string.weather_error_unknown
                }

            } else {
                // LOG 3: Der Standort ist null - das ist der wahrscheinlichste Fehlerpunkt.
                Log.e(
                    "HomeViewModel_Weather",
                    "locationTracker.getCurrentLocation() returned NULL."
                )
                _weatherErrorRes.value = R.string.weather_error_no_location
            }
            _isWeatherLoading.value = false
        }
    }
}
