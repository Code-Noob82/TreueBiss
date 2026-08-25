package com.dominikbaki.treuebiss.core.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.repository.AuthRepository
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.UserPreferencesRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Repräsentiert die möglichen Zustände der Haupt-UI.
 * Enthält jetzt auch einen Fehler-Zustand.
 */
sealed interface MainUiState {
    object Loading : MainUiState
    data class Success(
        val hasCompletedOnboarding: Boolean,
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

    private companion object {
        /**
         * Beim Start meldet Supabase kurz "nicht angemeldet", während eine
         * gespeicherte Session aus dem Speicher geladen wird. So lange warten
         * wir ab, bevor wir von "keine Session vorhanden" ausgehen.
         */
        const val SESSION_RESTORE_TIMEOUT_MS = 2_000L

        /** Zeitlimit für die anonyme Anmeldung selbst. */
        const val SIGN_IN_TIMEOUT_MS = 15_000L
    }

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var initJob: Job? = null

    init {
        initialize()
    }

    private fun initialize() {
        // Einen noch laufenden Versuch abbrechen. Sonst würde jeder Retry eine
        // weitere Collector-Kette auf dieselben Flows legen.
        initJob?.cancel()
        initJob = viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            try {
                // Schritt 1: Sicherstellen, dass eine gültige Session existiert.
                ensureSignedIn()

                // Schritt 2: Erst jetzt die restlichen Daten laden.
                combine(
                    userPreferencesRepository.hasCompletedOnboarding,
                    stampRepository.observeStamps(),
                    voucherRepository.observeOpenVouchers(),
                ) { hasCompletedOnboarding, stamps, vouchers ->
                    MainUiState.Success(
                        hasCompletedOnboarding = hasCompletedOnboarding,
                        stampCount = stamps.size,
                        voucherCount = vouchers.size,
                        currentStampCardId = stamps.lastOrNull()?.id ?: "default-card",
                        currentVoucherId = vouchers.lastOrNull()?.id ?: "default-voucher"
                    )
                }.collect { successState ->
                    _uiState.value = successState
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("MainViewModel", "Authentication timed out", e)
                _uiState.value = MainUiState.Error(
                    "Die Anmeldung dauert zu lange. Bitte prüfe deine Internetverbindung."
                )
            } catch (e: CancellationException) {
                // Regulärer Abbruch (z. B. durch einen Retry) - kein Fehlerzustand.
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Initialization failed: ${e.message}", e)
                _uiState.value = MainUiState.Error(
                    "Anmeldung fehlgeschlagen. Bitte prüfe deine Internetverbindung."
                )
            }
        }
    }

    /**
     * Stellt sicher, dass der Nutzer angemeldet ist, und wirft andernfalls.
     *
     * Wartet zuerst kurz auf eine gespeicherte Session, damit nicht bei jedem
     * Start ein zweiter anonymer Account angelegt wird.
     */
    private suspend fun ensureSignedIn() {
        val restoredSession = withTimeoutOrNull(SESSION_RESTORE_TIMEOUT_MS) {
            authRepository.observeAuthState().first { it }
        }
        if (restoredSession == true) {
            Log.d("MainViewModel", "Existing session restored.")
            return
        }

        Log.d("MainViewModel", "No session found, signing in anonymously...")
        withTimeout(SIGN_IN_TIMEOUT_MS) {
            authRepository.signInAnonymously()
            authRepository.observeAuthState().first { it }
        }
        Log.d("MainViewModel", "User is authenticated, proceeding to load data.")
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
