package com.dominikbaki.treuebiss.core.domain

import com.dominikbaki.treuebiss.core.domain.models.Voucher
import kotlinx.datetime.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherTest {

    private val now = Instant.fromEpochMilliseconds(1_000_000L)

    private fun voucher(expiresAt: Long, isRedeemed: Boolean = false) = Voucher(
        createdAt = now,
        creationDate = now.toEpochMilliseconds(),
        expiresAt = expiresAt,
        isRedeemed = isRedeemed,
        tenantId = "test-tenant"
    )

    @Test
    fun `ein Gutschein mit Ablauf in der Zukunft ist einloesbar`() {
        val v = voucher(expiresAt = now.toEpochMilliseconds() + 1)
        assertFalse(v.isExpiredAt(now))
        assertTrue(v.isRedeemableAt(now))
    }

    @Test
    fun `genau zum Ablaufzeitpunkt gilt der Gutschein noch`() {
        val v = voucher(expiresAt = now.toEpochMilliseconds())
        assertFalse(v.isExpiredAt(now))
        assertTrue(v.isRedeemableAt(now))
    }

    @Test
    fun `nach dem Ablaufzeitpunkt ist er abgelaufen und nicht mehr einloesbar`() {
        val v = voucher(expiresAt = now.toEpochMilliseconds() - 1)
        assertTrue(v.isExpiredAt(now))
        assertFalse(v.isRedeemableAt(now))
    }

    @Test
    fun `ein eingeloester Gutschein ist nicht mehr einloesbar`() {
        val v = voucher(expiresAt = now.toEpochMilliseconds() + 1, isRedeemed = true)
        assertFalse(v.isRedeemableAt(now))
    }
}
