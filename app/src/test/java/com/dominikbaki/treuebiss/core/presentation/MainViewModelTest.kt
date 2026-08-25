package com.dominikbaki.treuebiss.core.presentation

import com.dominikbaki.treuebiss.MainDispatcherRule
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.fakes.FakeAuthRepository
import com.dominikbaki.treuebiss.fakes.FakeStampRepository
import com.dominikbaki.treuebiss.fakes.FakeUserPreferencesRepository
import com.dominikbaki.treuebiss.fakes.FakeVoucherRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        auth: FakeAuthRepository = FakeAuthRepository(),
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        stamps: FakeStampRepository = FakeStampRepository(),
        vouchers: FakeVoucherRepository = FakeVoucherRepository()
    ) = MainViewModel(prefs, auth, stamps, vouchers)

    @Test
    fun `meldet sich anonym an und erreicht den Erfolgszustand`() = runTest(mainDispatcherRule.dispatcher) {
        val auth = FakeAuthRepository(initiallyAuthenticated = false)
        val vm = viewModel(auth = auth)

        advanceUntilIdle()

        assertTrue(vm.uiState.value is MainUiState.Success)
        assertEquals(1, auth.signInCallCount)
    }

    @Test
    fun `nutzt eine bestehende Session ohne neue Anmeldung`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression: fruehere Versionen legten hier einen zweiten anonymen
        // Account an, weil sie die erste Emission des Auth-Status auswerteten.
        val auth = FakeAuthRepository(initiallyAuthenticated = true)
        val vm = viewModel(auth = auth)

        advanceUntilIdle()

        assertTrue(vm.uiState.value is MainUiState.Success)
        assertEquals(0, auth.signInCallCount)
    }

    @Test
    fun `zeigt einen Fehler statt endlos zu laden wenn die Anmeldung fehlschlaegt`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression fuer den Haenger im Ladebildschirm.
        val auth = FakeAuthRepository().apply {
            signInError = IllegalStateException("offline")
        }
        val vm = viewModel(auth = auth)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Erwartet: Error, war: $state", state is MainUiState.Error)
        assertEquals(R.string.error_sign_in_failed, (state as MainUiState.Error).messageRes)
    }

    @Test
    fun `laeuft in einen Timeout wenn die Anmeldung haengt`() = runTest(mainDispatcherRule.dispatcher) {
        val auth = FakeAuthRepository().apply { signInNeverCompletes = true }
        val vm = viewModel(auth = auth)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Erwartet: Error, war: $state", state is MainUiState.Error)
        assertEquals(R.string.error_sign_in_timeout, (state as MainUiState.Error).messageRes)
    }

    @Test
    fun `stellt Daten nach einer Neuinstallation genau einmal wieder her`() = runTest(mainDispatcherRule.dispatcher) {
        val stamps = FakeStampRepository()
        val vouchers = FakeVoucherRepository()
        val prefs = FakeUserPreferencesRepository(remoteDataRestored = false)

        val vm = viewModel(prefs = prefs, stamps = stamps, vouchers = vouchers)
        advanceUntilIdle()

        assertEquals(1, stamps.restoreCallCount)
        assertEquals(1, vouchers.restoreCallCount)

        // Ein Retry darf die Wiederherstellung nicht erneut ausloesen.
        vm.retryInitialAuth()
        advanceUntilIdle()

        assertEquals(1, stamps.restoreCallCount)
    }
}
