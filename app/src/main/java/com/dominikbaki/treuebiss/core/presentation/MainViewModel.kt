package com.dominikbaki.treuebiss.core.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.repository.AuthRepository
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.UserPreferencesRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Repräsentiert die möglichen Zustände der Haupt-UI.
 * Enthält jetzt auch einen Fehler-Zustand.
 */
sealed interface MainUiState {
    object Loading : MainUiState
    data class Success(
        val hasCompletedOnboarding: Boolean,
        val isLoggedIn: Boolean,
        val stampCount: Int,
        val voucherCount: Int,
        val currentStampCardId: String,
        val currentVoucherId: String
    ) : MainUiState
    data class Error(val message: String) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository, // Neue Abhänigkeit
    private val stampRepository: StampRepository, // Neue Abhängigkeit
    private val voucherRepository: VoucherRepository // Neue Abhängigkeit
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        initialize()
    }
    // ÜBERARBEITETE, ROBUSTERE VERSION
    private fun initialize() {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                // Schritt 1: Stelle sicher, dass der User angemeldet ist.
                // Wir warten auf den ersten "wahren" Wert vom Auth-State.
                val isLoggedIn = authRepository.observeAuthState().first { it }

                // Wenn wir hier ankommen, ist der User definitiv angemeldet.
                Log.d("MainViewModel", "User is authenticated, proceeding to load data.")

                // Schritt 2: Lade jetzt die restlichen Daten, da die Anmeldung sicher ist.
                combine(
                    userPreferencesRepository.hasCompletedOnboarding,
                    stampRepository.observeStamps(),
                    voucherRepository.observeAll(includeRedeemed = false),
                ) { hasCompletedOnboarding, stamps, vouchers ->
                    // Der isLoggedIn-Wert kommt jetzt von oben, nicht mehr direkt aus dem Flow
                    MainUiState.Success(
                        hasCompletedOnboarding = hasCompletedOnboarding,
                        isLoggedIn = isLoggedIn,
                        stampCount = stamps.size,
                        voucherCount = vouchers.size,
                        // Logik für IDs bleibt gleich
                        currentStampCardId = stamps.lastOrNull()?.id ?: "default-card",
                        currentVoucherId = vouchers.lastOrNull()?.id ?: "default-voucher"
                    )
                }.collect { successState ->
                    // Dies sollte jetzt zuverlässig erreicht werden
                    Log.d("MainViewModel", "State is now SUCCESS!")
                    _uiState.value = successState
                }

            } catch (e: Exception) {
                Log.e("MainViewModel", "Initialization failed: ${e.message}", e)
                _uiState.value = MainUiState.Error("Anmeldung fehlgeschlagen. Bitte prüfe deine Internetverbindung.")
            }
        }

        // Starte die anonyme Anmeldung parallel, falls nötig.
        // Der obere Flow wird automatisch warten, bis dieser Prozess erfolgreich war.
        viewModelScope.launch {
            if (!authRepository.observeAuthState().first()) {
                Log.d("MainViewModel", "User not authenticated, signing in anonymously...")
                authRepository.signInAnonymously()
            }
        }
    }

    fun retryInitialAuth() {
        Log.d("MainViewModel", "Retrying initialization...")
        initialize()
    }

    fun onOnboardingFinished() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
        }
    }
}