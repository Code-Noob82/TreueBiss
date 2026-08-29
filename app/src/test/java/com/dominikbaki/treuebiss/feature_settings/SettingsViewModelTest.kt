package com.dominikbaki.treuebiss.feature_settings

import app.cash.turbine.test
import com.dominikbaki.treuebiss.MainDispatcherRule
import com.dominikbaki.treuebiss.core.domain.repository.ThemeMode
import com.dominikbaki.treuebiss.fakes.FakeUserPreferencesRepository
import com.dominikbaki.treuebiss.feature_settings.presentation.SettingsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var prefs: FakeUserPreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        prefs = FakeUserPreferencesRepository()
        viewModel = SettingsViewModel(prefs)
    }

    @Test
    fun `die Auswahl des Erscheinungsbilds wird uebernommen`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.uiState.test {
                assertEquals(ThemeMode.System, awaitItem().themeMode)

                viewModel.onThemeModeSelected(ThemeMode.Dunkel)
                advanceUntilIdle()

                assertEquals(ThemeMode.Dunkel, expectMostRecentItem().themeMode)
            }
        }
}
