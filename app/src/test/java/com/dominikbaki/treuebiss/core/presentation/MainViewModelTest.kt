package com.dominikbaki.treuebiss.core.presentation

import com.dominikbaki.treuebiss.MainDispatcherRule
import com.dominikbaki.treuebiss.core.domain.repository.AuthStatus
import com.dominikbaki.treuebiss.fakes.FakeAuthRepository
import com.dominikbaki.treuebiss.fakes.FakeStampRepository
import com.dominikbaki.treuebiss.fakes.FakeTenantRepository
import com.dominikbaki.treuebiss.fakes.FakeUserPreferencesRepository
import com.dominikbaki.treuebiss.fakes.FakeVoucherRepository
import com.dominikbaki.treuebiss.fakes.TEST_TENANT_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    /** Spiegelt MainViewModel.AUTH_STATUS_TIMEOUT_MS (dort privat). */
    private val authStatusTimeoutMs = 2_000L

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        auth: FakeAuthRepository = FakeAuthRepository(),
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
        stamps: FakeStampRepository = FakeStampRepository(),
        vouchers: FakeVoucherRepository = FakeVoucherRepository(),
        tenants: FakeTenantRepository = FakeTenantRepository()
    ) = MainViewModel(prefs, auth, stamps, vouchers, tenants)

    private fun MainUiState.asSuccess(): MainUiState.Success {
        assertTrue("Erwartet: Success, war: $this", this is MainUiState.Success)
        return this as MainUiState.Success
    }

    @Test
    fun `meldet sich anonym an, wenn keine Session existiert`() =
        runTest(mainDispatcherRule.dispatcher) {
            val auth = FakeAuthRepository(AuthStatus.NotAuthenticated)
            val vm = viewModel(auth = auth)

            advanceUntilIdle()

            assertEquals(1, auth.signInCallCount)
            assertEquals(SyncStatus.Synced, vm.uiState.value.asSuccess().syncStatus)
        }

    @Test
    fun `nutzt eine bestehende Session ohne neue Anmeldung`() =
        runTest(mainDispatcherRule.dispatcher) {
            val auth = FakeAuthRepository(AuthStatus.Authenticated)
            val vm = viewModel(auth = auth)

            advanceUntilIdle()

            assertEquals(0, auth.signInCallCount)
            assertEquals(SyncStatus.Synced, vm.uiState.value.asSuccess().syncStatus)
        }

    @Test
    fun `die App ist ohne erreichbares Backend bedienbar`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Regression: Frueher blockierte eine fehlgeschlagene Anmeldung den
            // kompletten Start, obwohl Stempel und Gutscheine lokal liegen.
            val auth = FakeAuthRepository().apply {
                signInError = IllegalStateException("offline")
            }
            val vm = viewModel(auth = auth)

            advanceUntilIdle()

            val state = vm.uiState.value.asSuccess()
            assertEquals(SyncStatus.Offline, state.syncStatus)
        }

    @Test
    fun `wartet nicht auf das Sicherheitsnetz, wenn der Status sofort feststeht`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Regression: Frueher kostete jeder Kaltstart ohne Session die volle
            // Wartezeit, weil "laedt noch" und "nicht angemeldet" beide false waren.
            var signInAt = -1L
            val auth = FakeAuthRepository(AuthStatus.NotAuthenticated).apply {
                onSignInStarted = { signInAt = testScheduler.currentTime }
            }
            viewModel(auth = auth)

            advanceUntilIdle()

            assertEquals(1, auth.signInCallCount)
            assertTrue(
                "Anmeldung startete erst nach $signInAt ms",
                signInAt in 0 until authStatusTimeoutMs
            )
        }

    @Test
    fun `meldet sich nach dem Sicherheitsnetz an, wenn der Status unklar bleibt`() =
        runTest(mainDispatcherRule.dispatcher) {
            var signInAt = -1L
            val auth = FakeAuthRepository(AuthStatus.Unknown).apply {
                onSignInStarted = { signInAt = testScheduler.currentTime }
            }
            viewModel(auth = auth)

            advanceUntilIdle()

            assertEquals(1, auth.signInCallCount)
            assertTrue("Anmeldung startete nach $signInAt ms", signInAt >= authStatusTimeoutMs)
        }

    @Test
    fun `geht offline, wenn die Anmeldung haengt`() =
        runTest(mainDispatcherRule.dispatcher) {
            val auth = FakeAuthRepository().apply { signInNeverCompletes = true }
            val vm = viewModel(auth = auth)

            advanceUntilIdle()

            assertEquals(SyncStatus.Offline, vm.uiState.value.asSuccess().syncStatus)
        }

    @Test
    fun `stellt Daten nach einer Neuinstallation genau einmal wieder her`() =
        runTest(mainDispatcherRule.dispatcher) {
            val stamps = FakeStampRepository()
            val vouchers = FakeVoucherRepository()
            val prefs = FakeUserPreferencesRepository(remoteDataRestored = false)

            val vm = viewModel(prefs = prefs, stamps = stamps, vouchers = vouchers)
            advanceUntilIdle()

            assertEquals(1, stamps.restoreCallCount)
            assertEquals(1, vouchers.restoreCallCount)

            // Ein erneuter Verbindungsversuch darf sie nicht noch einmal holen.
            vm.retryConnection()
            advanceUntilIdle()

            assertEquals(1, stamps.restoreCallCount)
        }

    @Test
    fun `ein erneuter Versuch kommt nach einem Ausfall zurueck auf synchronisiert`() =
        runTest(mainDispatcherRule.dispatcher) {
            val auth = FakeAuthRepository().apply {
                signInError = IllegalStateException("offline")
            }
            val vm = viewModel(auth = auth)
            advanceUntilIdle()
            assertEquals(SyncStatus.Offline, vm.uiState.value.asSuccess().syncStatus)

            auth.signInError = null
            vm.retryConnection()
            advanceUntilIdle()

            assertEquals(SyncStatus.Synced, vm.uiState.value.asSuccess().syncStatus)
        }

    @Test
    fun `ein fehlgeschlagener Betriebs-Abgleich schaltet auf offline, blockiert aber nicht`() =
        runTest(mainDispatcherRule.dispatcher) {
            val tenants = FakeTenantRepository().apply {
                syncError = IllegalStateException("offline")
            }
            val vm = viewModel(tenants = tenants)

            advanceUntilIdle()

            val state = vm.uiState.value.asSuccess()
            assertEquals(SyncStatus.Offline, state.syncStatus)
        }

    @Test
    fun `der Betrieb steht sofort zur Verfuegung, auch ohne Serverdaten`() =
        runTest(mainDispatcherRule.dispatcher) {
            val vm = viewModel()

            // Noch vor advanceUntilIdle: Branding darf nie leer sein.
            assertEquals(TEST_TENANT_ID, vm.activeTenant.value.id)
            assertEquals(
                com.dominikbaki.treuebiss.core.domain.models.Tenant.DEFAULT_STAMPS_PER_CARD,
                vm.activeTenant.value.stampsPerCard
            )
        }
}
