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
    private fun initialize() {
        viewModelScope.launch {
            try {
                val initialAuthStatus = withTimeoutOrNull(3000) {
                    authRepository.observeAuthState().first()
                }
                if (initialAuthStatus == null || initialAuthStatus == false) {
                    authRepository.signInAnonymously()
                }

                combine(
                    userPreferencesRepository.hasCompletedOnboarding,
                    authRepository.observeAuthState(),
                    stampRepository.observeStamps(),
                    voucherRepository.observeAll(includeRedeemed = false),
                ) { hasCompletedOnboarding, isLoggedIn, stamps, vouchers ->
                    MainUiState.Success(
                        hasCompletedOnboarding = hasCompletedOnboarding,
                        isLoggedIn = isLoggedIn,
                        stampCount = stamps.size,
                        voucherCount = vouchers.size,
                        currentStampCardId = stamps.lastOrNull()?.id ?: "default-card",
                        currentVoucherId = vouchers.lastOrNull()?.id ?: "default-voucher"
                    )
                }.collect { successState ->
                    _uiState.value = successState
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Initialization failed: ${e.message}", e)
                _uiState.value = MainUiState.Error("Anmeldung fehlgeschlagen. Bitte prüfe deine Internetverbindung.")
            }
        }
    }

    fun retryInitialAuth() {
        Log.d("MainViewModel", "Retrying initialization...")
        _uiState.value = MainUiState.Loading
        initialize()
    }

    fun onOnboardingFinished() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
        }
    }
}