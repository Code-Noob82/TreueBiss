package com.dominikbaki.treuebiss.feature_settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.BuildConfig
import com.dominikbaki.treuebiss.core.domain.repository.ThemeMode
import com.dominikbaki.treuebiss.core.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Zustand der Einstellungen.
 *
 * Die rechtlichen Seiten stehen nicht hier: Sie sind zentral und kommen aus
 * den Ressourcen, nicht vom Betrieb und nicht vom Server.
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val appVersion: String = BuildConfig.VERSION_NAME
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = userPreferencesRepository.themeMode
        .map { theme -> SettingsUiState(themeMode = theme) }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }
}
