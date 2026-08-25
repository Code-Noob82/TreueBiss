package com.dominikbaki.treuebiss.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}