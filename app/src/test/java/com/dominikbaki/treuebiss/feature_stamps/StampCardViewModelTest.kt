package com.dominikbaki.treuebiss.feature_stamps

import com.dominikbaki.treuebiss.MainDispatcherRule
import com.dominikbaki.treuebiss.core.domain.models.Tenant
import com.dominikbaki.treuebiss.fakes.FakeStampRepository
import com.dominikbaki.treuebiss.fakes.FakeTenantRepository
import com.dominikbaki.treuebiss.fakes.FakeVoucherRepository
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
    private val STAMPS_PER_CARD = Tenant.DEFAULT_STAMPS_PER_CARD

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
        stamps = FakeStampRepository()
        vouchers = FakeVoucherRepository()
        tenants = FakeTenantRepository()
        viewModel = StampCardViewModel(stamps, vouchers, tenants)
    }

    @Test
    fun `erzeugt keinen Gutschein vor dem zehnten Stempel`() = runTest(mainDispatcherRule.dispatcher) {
        repeat(9) {
            viewModel.onAddStampClicked()
            advanceUntilIdle()
        }

        assertTrue(vouchers.created.isEmpty())
        assertEquals(0, stamps.clearCallCount)
    }

    @Test
    fun `erzeugt beim zehnten Stempel einen Gutschein und setzt die Karte zurueck`() = runTest(mainDispatcherRule.dispatcher) {
        repeat(STAMPS_PER_CARD) {
            viewModel.onAddStampClicked()
            advanceUntilIdle()
        }

        assertEquals(1, vouchers.created.size)
        assertEquals(1, stamps.clearCallCount)
    }

    @Test
    fun `der Gutschein laeuft in der Zukunft ab`() = runTest(mainDispatcherRule.dispatcher) {
        repeat(STAMPS_PER_CARD) {
            viewModel.onAddStampClicked()
            advanceUntilIdle()
        }

        val voucher = vouchers.created.single()
        assertTrue(voucher.expiresAt > voucher.creationDate)
    }

    @Test
    fun `ein zweiter Klick waehrend der Verarbeitung wird ignoriert`() = runTest(mainDispatcherRule.dispatcher) {
        // Ohne die Sperre haetten zwei schnelle Klicks denselben Zaehlerstand
        // gesehen und die Zehnergrenze verfehlt.
        viewModel.onAddStampClicked()
        viewModel.onAddStampClicked()
        advanceUntilIdle()

        assertEquals(1, stamps.currentStamps.size)
    }
}
