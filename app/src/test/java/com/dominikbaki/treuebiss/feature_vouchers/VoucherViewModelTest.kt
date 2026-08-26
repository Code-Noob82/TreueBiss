package com.dominikbaki.treuebiss.feature_vouchers

import app.cash.turbine.test
import com.dominikbaki.treuebiss.MainDispatcherRule
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.fakes.FakeVoucherRepository
import com.dominikbaki.treuebiss.fakes.TEST_TENANT_ID
import com.dominikbaki.treuebiss.feature_vouchers.presentation.VoucherEvent
import com.dominikbaki.treuebiss.feature_vouchers.presentation.VoucherViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoucherViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val voucher = Voucher(
        createdAt = Instant.fromEpochMilliseconds(0),
        creationDate = 0,
        expiresAt = Long.MAX_VALUE,
        tenantId = TEST_TENANT_ID
    )

    private lateinit var repository: FakeVoucherRepository
    private lateinit var viewModel: VoucherViewModel

    @Before
    fun setUp() {
        repository = FakeVoucherRepository(listOf(voucher))
        viewModel = VoucherViewModel(repository)
    }

    @Test
    fun `mit richtigem Code wird eingeloest`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.events.test {
            viewModel.onRedeemConfirmed(voucher.id, "1234")
            advanceUntilIdle()

            assertTrue(awaitItem() is VoucherEvent.Redeemed)
        }
        assertEquals(listOf(voucher.id), repository.redeemed)
    }

    @Test
    fun `falscher Code loest nicht ein`() = runTest(mainDispatcherRule.dispatcher) {
        // Regression: Frueher entschied allein das Kundengeraet, ob ein
        // Gutschein verbraucht ist - ganz ohne Zutun des Betriebs.
        viewModel.events.test {
            viewModel.onRedeemConfirmed(voucher.id, "9999")
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue(event is VoucherEvent.Failed)
            assertEquals(
                R.string.voucher_error_wrong_code,
                (event as VoucherEvent.Failed).messageRes
            )
        }
        assertTrue("Der Gutschein darf nicht verbraucht sein", repository.redeemed.isEmpty())
    }

    @Test
    fun `ohne Verbindung bleibt der Gutschein erhalten`() =
        runTest(mainDispatcherRule.dispatcher) {
            repository.redeemError = IllegalStateException("offline")

            viewModel.events.test {
                viewModel.onRedeemConfirmed(voucher.id, "1234")
                advanceUntilIdle()

                val event = awaitItem()
                assertTrue(event is VoucherEvent.Failed)
                assertEquals(
                    R.string.voucher_error_offline,
                    (event as VoucherEvent.Failed).messageRes
                )
            }
            assertTrue(repository.redeemed.isEmpty())
        }

    @Test
    fun `ein zweiter Versuch waehrend der Verarbeitung wird ignoriert`() =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel.onRedeemConfirmed(voucher.id, "1234")
            viewModel.onRedeemConfirmed(voucher.id, "1234")
            advanceUntilIdle()

            assertEquals(1, repository.redeemed.size)
        }
}
