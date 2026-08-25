package com.dominikbaki.treuebiss.core.domain.repository

import com.dominikbaki.treuebiss.core.domain.models.Voucher
import kotlinx.coroutines.flow.Flow

/**
 * Port für alle Operationen, die Gutscheine betreffen.
 */
interface VoucherRepository {
    /** Legt einen neuen Gutschein an (lokal, danach Sync zu Supabase). */
    suspend fun createVoucher(voucher: Voucher)

    /** Beobachtet alle noch nicht eingelösten Gutscheine. */
    fun observeOpenVouchers(): Flow<List<Voucher>>

    /** Markiert einen Gutschein als eingelöst (lokal, danach Sync zu Supabase). */
    suspend fun redeemVoucher(voucherId: String)
}
