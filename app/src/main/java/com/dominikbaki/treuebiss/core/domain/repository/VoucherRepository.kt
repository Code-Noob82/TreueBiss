package com.dominikbaki.treuebiss.core.domain.repository

import com.dominikbaki.treuebiss.core.domain.models.Voucher
import kotlinx.coroutines.flow.Flow

/**
 * Port für alle Operationen, die Gutscheine betreffen.
 */
interface VoucherRepository {
    suspend fun createVoucher(voucher: Voucher)
    fun observeAll(includeRedeemed: Boolean = false): Flow<List<Voucher>>

    suspend fun redeem(voucherId: String)

    suspend fun markExpiredBefore(epochMillis: Long)


    fun getAllVouchers(): Flow<List<Voucher>>
    suspend fun redeemVoucher(voucherId: String)
}