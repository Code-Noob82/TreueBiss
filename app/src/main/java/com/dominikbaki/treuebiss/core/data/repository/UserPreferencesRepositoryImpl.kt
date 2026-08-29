package com.dominikbaki.treuebiss.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dominikbaki.treuebiss.core.domain.repository.ThemeMode
import com.dominikbaki.treuebiss.core.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {
    private companion object {
        val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        val REMOTE_DATA_RESTORED_KEY = booleanPreferencesKey("remote_data_restored")
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }

    override val hasCompletedOnboarding = dataStore.data
        .map { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] == true
        }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    override val hasRestoredRemoteData = dataStore.data
        .map { preferences ->
            preferences[REMOTE_DATA_RESTORED_KEY] == true
        }

    override suspend fun setRemoteDataRestored(restored: Boolean) {
        dataStore.edit { preferences ->
            preferences[REMOTE_DATA_RESTORED_KEY] = restored
        }
    }

    override val themeMode = dataStore.data
        .map { preferences ->
            // Ein unbekannter Wert (etwa nach einer Umbenennung) darf die App
            // nicht umwerfen - dann gilt der Standard.
            runCatching { ThemeMode.valueOf(preferences[THEME_MODE_KEY] ?: "") }
                .getOrDefault(ThemeMode.System)
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }
}