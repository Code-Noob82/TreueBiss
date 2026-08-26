package com.dominikbaki.treuebiss.feature_stamps

import app.cash.turbine.test
import com.dominikbaki.treuebiss.MainDispatcherRule
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.domain.models.Tenant
import com.dominikbaki.treuebiss.fakes.FakeStampRepository
import com.dominikbaki.treuebiss.fakes.FakeTenantRepository
import com.dominikbaki.treuebiss.fakes.FakeVoucherRepository
import com.dominikbaki.treuebiss.feature_stamps.presentation.StampCardEvent
import com.dominikbaki.treuebiss.feature_stamps.presentation.StampCardViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StampCardViewModelTest {

    /** Kartengröße des Test-Betriebs. */
    private val stampsPerCard = Tenant.DEFAULT_STAMPS_PER_CARD

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var stamps: FakeStampRepository
    private lateinit var vouchers: FakeVoucherRepository
    private lateinit var tenants: FakeTenantRepository
    private lateinit var viewModel: StampCardViewModel

    // Nicht als Feldinitialisierung: die läuft, bevor die Rule
    // `Dispatchers.Main` gesetzt hat, und `viewModelScope` schlägt dann fehl.
    @Before
    fun setUp() {
        stamps = FakeStampRepository(stampsPerCard = stampsPerCard)
        vouchers = FakeVoucherRepository()
        tenants = FakeTenantRepository()
        viewModel = StampCardViewModel(stamps, vouchers, tenants)
    }

    private fun scan(reference: String) {
        viewModel.onReceiptScanned(reference)
    }

    @Test
    fun `ein gescannter Beleg vergibt genau einen Stempel`() =
        runTest(mainDispatcherRule.dispatcher) {
            scan("bon-1")
            advanceUntilIdle()

            assertEquals(1, stamps.currentStamps.size)
            assertEquals(listOf("bon-1"), stamps.issuedProofs)
        }

    @Test
    fun `derselbe Beleg zaehlt nur einmal`() = runTest(mainDispatcherRule.dispatcher) {
        // Der Server lehnt einen bereits verwendeten Nachweis ab - ohne das
        // koennte ein Kunde denselben Bon beliebig oft scannen.
        scan("bon-1")
        advanceUntilIdle()

        viewModel.events.test {
            scan("bon-1")
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is StampCardEvent.Failed)
            assertEquals(R.string.stamp_error_proof_used, (event as StampCardEvent.Failed).messageRes)
        }
        assertEquals(1, stamps.currentStamps.size)
    }

    @Test
    fun `erzeugt keinen Gutschein vor der vollen Karte`() =
        runTest(mainDispatcherRule.dispatcher) {
            repeat(stampsPerCard - 1) { i ->
                scan("bon-$i")
                advanceUntilIdle()
            }

            assertTrue(vouchers.created.isEmpty())
            assertEquals(0, stamps.clearCallCount)
        }

    @Test
    fun `bei voller Karte kommt der Gutschein vom Server und die Karte wird zurueckgesetzt`() =
        runTest(mainDispatcherRule.dispatcher) {
            repeat(stampsPerCard) { i ->
                scan("bon-$i")
                advanceUntilIdle()
            }

            assertEquals(1, vouchers.created.size)
            assertEquals(1, stamps.clearCallCount)
        }

    @Test
    fun `ohne Verbindung entsteht kein Stempel, sondern eine Meldung`() =
        runTest(mainDispatcherRule.dispatcher) {
            // Regression: Stempel duerfen nicht lokal entstehen. Nur der Server
            // kann pruefen, ob ein Beleg echt und unbenutzt ist.
            stamps.issueError = IllegalStateException("offline")

            viewModel.events.test {
                scan("bon-1")
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(event is StampCardEvent.Failed)
                assertEquals(R.string.stamp_error_offline, (event as StampCardEvent.Failed).messageRes)
            }
            assertEquals(0, stamps.currentStamps.size)
        }

    @Test
    fun `ein zweiter Scan waehrend der Verarbeitung wird ignoriert`() =
        runTest(mainDispatcherRule.dispatcher) {
            scan("bon-1")
            scan("bon-2")
            advanceUntilIdle()

            assertEquals(1, stamps.currentStamps.size)
        }
}
