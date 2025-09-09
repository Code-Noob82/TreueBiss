package com.dominikbaki.treuebiss.core.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.repository.AuthRepository
import com.dominikbaki.treuebiss.core.domain.repository.UserPreferencesRepository
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
    data class Success(val hasCompletedOnboarding: Boolean, val isLoggedIn: Boolean) : MainUiState
    data class Error(val message: String) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository // Neue Abhänigkeit
) : ViewModel() {

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeSuccessState()
        checkInitialAuthState()
    }

    /**
     * Kombiniert die Flows für Onboarding und Auth-Status, um den Success-State zu bilden.
     * Dieser Flow läuft kontinuierlich.
     */
    private fun observeSuccessState() {
        viewModelScope.launch {
            userPreferencesRepository.hasCompletedOnboarding
                .combine(authRepository.observeAuthState()) { hasCompletedOnboarding, isLoggedIn ->

                    if (_uiState.value !is MainUiState.Error) {
                        _uiState.value = MainUiState.Success(hasCompletedOnboarding, isLoggedIn)
                    }
                }.collect {}
        }
    }

    private fun checkInitialAuthState() {
        viewModelScope.launch {
            try {
                val sessionLoaded = withTimeoutOrNull(3000) {
                    authRepository.observeAuthState().first { it }
                }

                if (sessionLoaded == true) {
                    val logMessage = "Session successfully loaded from storage within timeout."
                    Log.d("MainViewModel", logMessage)
                } else {
                    Log.d(
                        "MainViewModel",
                        "No session loaded after 3s. Attempting anonymous sign-in."
                    )
                    authRepository.signInAnonymously()
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error checking initial auth state: ${e.message}", e)
                _uiState.value =
                    MainUiState.Error("Anmeldung fehlgeschlagen. Bitte prüfe deine Internetverbindung und starte die App neu.")
            }
        }
    }

    fun retryInitialAuth() {
        Log.d("MainViewModel", "Retrying initial authentication...")
        _uiState.value = MainUiState.Loading
        checkInitialAuthState()
    }

    fun onOnboardingFinished() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
        }
    }
}