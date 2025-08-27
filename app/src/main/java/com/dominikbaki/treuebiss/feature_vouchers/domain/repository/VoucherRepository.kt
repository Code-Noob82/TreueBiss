package com.dominikbaki.treuebiss.feature_vouchers.domain.repository

import com.dominikbaki.treuebiss.feature_vouchers.domain.model.Voucher
import kotlinx.coroutines.flow.Flow

/**
 * Port für alle Operationen, die Gutscheine betreffen.
 */
interface VoucherRepository {
    /** Erstellt einen neuen Gutschein (z. B. beim 10. Stempel). */
    suspend fun create(voucher: Voucher)

    /** Beobachtet alle Gutscheine (optional filterbar nach offen/eingelöst). */
    fun observeAll(includeRedeemed: Boolean = false): Flow<List<Voucher>>

    /** Löst einen Gutschein ein. */
    suspend fun redeem(voucherId: String)

    /** Markiert abgelaufene Gutscheine (für einen Verfalls-Service). */
    suspend fun markExpiredBefore(epochMillis: Long)
}