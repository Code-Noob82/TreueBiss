package com.dominikbaki.treuebiss.core.domain.repository

import com.dominikbaki.treuebiss.core.domain.models.Voucher
import kotlinx.coroutines.flow.Flow

/**
 * Port für alle Operationen, die Gutscheine betreffen.
 */
interface VoucherRepository {
    /**
     * Übernimmt einen serverseitig erzeugten Gutschein in die lokale Datenbank.
     * Angelegt wird er ausschließlich vom Server - siehe `issue_stamp`.
     */
    suspend fun cacheVoucher(voucher: Voucher)

    /** Beobachtet alle noch nicht eingelösten Gutscheine. */
    fun observeOpenVouchers(): Flow<List<Voucher>>

    /** Markiert einen Gutschein als eingelöst (lokal, danach Sync zu Supabase). */
    suspend fun redeemVoucher(voucherId: String)

    /**
     * Holt die Gutscheine dieser Geräte-Identität vom Server in die lokale
     * Datenbank. Gedacht für den ersten Start nach einer Neuinstallation.
     */
    suspend fun restoreFromRemote()
}
