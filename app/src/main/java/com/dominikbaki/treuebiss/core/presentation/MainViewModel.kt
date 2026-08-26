package com.dominikbaki.treuebiss.core.presentation

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.domain.repository.AuthRepository
import com.dominikbaki.treuebiss.core.domain.repository.AuthStatus
import com.dominikbaki.treuebiss.core.domain.models.Tenant
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.TenantRepository
import com.dominikbaki.treuebiss.core.domain.repository.UserPreferencesRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Zustand der Verbindung zum Backend.
 *
 * Getrennt vom UI-Zustand: Die App ist auch ohne Backend bedienbar, deshalb
 * darf ein Verbindungsproblem den Start nicht blockieren.
 */
enum class SyncStatus { Connecting, Synced, Offline }

/**
 * Repräsentiert die möglichen Zustände der Haupt-UI.
 */
sealed interface MainUiState {
    data object Loading : MainUiState

    data class Success(
        val hasCompletedOnboarding: Boolean,
        val syncStatus: SyncStatus
    ) : MainUiState

    /** Nur für lokale Fehler - eine fehlende Backend-Verbindung gehört nicht hierher. */
    data class Error(@StringRes val messageRes: Int) : MainUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authRepository: AuthRepository,
    private val stampRepository: StampRepository,
    private val voucherRepository: VoucherRepository,
    private val tenantRepository: TenantRepository
) : ViewModel() {

    private companion object {
        /**
         * Sicherheitsnetz: So lange warten wir höchstens darauf, dass das SDK
         * einen belastbaren Anmeldestatus meldet. Im Normalfall steht der nach
         * wenigen Millisekunden fest.
         */
        const val AUTH_STATUS_TIMEOUT_MS = 2_000L

        /** Zeitlimit für die anonyme Anmeldung selbst. */
        const val SIGN_IN_TIMEOUT_MS = 15_000L
    }

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val syncStatus = MutableStateFlow(SyncStatus.Connecting)

    /**
     * Der Betrieb, für den dieser Build gedacht ist. Speist Branding und
     * Theme und steht sofort zur Verfügung - notfalls als Platzhalter,
     * solange die Serverdaten fehlen.
     */
    val activeTenant: StateFlow<Tenant> = tenantRepository.observeActiveTenant()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Tenant.fallback(tenantRepository.activeTenantId)
        )

    private var connectJob: Job? = null

    init {
        observeLocalState()
        connect()
    }

    /**
     * Der Start hängt ausschließlich an lokalen Daten. Stempelkarte und
     * Gutscheine liegen in Room; ohne Backend fehlt nur der Abgleich.
     */
    private fun observeLocalState() {
        viewModelScope.launch {
            try {
                combine(
                    userPreferencesRepository.hasCompletedOnboarding,
                    syncStatus
                ) { hasCompletedOnboarding, sync ->
                    MainUiState.Success(
                        hasCompletedOnboarding = hasCompletedOnboarding,
                        syncStatus = sync
                    )
                }.collect { _uiState.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Reading local preferences failed", e)
                _uiState.value = MainUiState.Error(R.string.error_local_data_failed)
            }
        }
    }

    /**
     * Meldet sich an und holt einmalig die Serverdaten. Läuft im Hintergrund:
     * Ein Fehler macht die App nicht unbenutzbar, er schaltet nur auf
     * [SyncStatus.Offline].
     */
    private fun connect() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            syncStatus.value = SyncStatus.Connecting
            try {
                ensureSignedIn()
                // Betrieb und Angebote holen, Mitgliedschaft sicherstellen.
                // Muss vor der Wiederherstellung laufen: Ohne Mitgliedschaft
                // lehnen die RLS-Policies spätere Schreibvorgänge ab.
                tenantRepository.syncFromRemote()
                restoreRemoteDataOnce()
                syncStatus.value = SyncStatus.Synced
            } catch (e: TimeoutCancellationException) {
                // Muss vor CancellationException stehen: Ein Timeout ist ein
                // Ausfall, kein regulaerer Abbruch des Jobs.
                Log.e("MainViewModel", "Backend connection timed out", e)
                syncStatus.value = SyncStatus.Offline
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainViewModel", "Backend unreachable, continuing offline", e)
                syncStatus.value = SyncStatus.Offline
            }
        }
    }

    /**
     * Stellt sicher, dass eine Session existiert, und wirft andernfalls.
     */
    private suspend fun ensureSignedIn() {
        // Auf den ersten belastbaren Status warten - "lädt noch" ist keiner.
        val status = withTimeoutOrNull(AUTH_STATUS_TIMEOUT_MS) {
            authRepository.observeAuthStatus().first { it != AuthStatus.Unknown }
        }
        if (status == AuthStatus.Authenticated) {
            Log.d("MainViewModel", "Existing session restored.")
            return
        }

        Log.d("MainViewModel", "No session found, signing in anonymously...")
        withTimeout(SIGN_IN_TIMEOUT_MS) {
            authRepository.signInAnonymously()
            authRepository.observeAuthStatus().first { it == AuthStatus.Authenticated }
        }
    }

    /**
     * Holt Stempel und Gutscheine einmalig vom Server - der Fall
     * "App neu installiert, Daten liegen noch im Backend".
     */
    private suspend fun restoreRemoteDataOnce() {
        if (userPreferencesRepository.hasRestoredRemoteData.first()) return

        stampRepository.restoreFromRemote()
        voucherRepository.restoreFromRemote()
        userPreferencesRepository.setRemoteDataRestored(true)
        Log.d("MainViewModel", "Remote data restored.")
    }

    /** Erneuter Verbindungsversuch, ausgelöst vom Nutzer. */
    fun retryConnection() {
        Log.d("MainViewModel", "Retrying backend connection...")
        connect()
    }

    fun onOnboardingFinished() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
        }
    }
}
