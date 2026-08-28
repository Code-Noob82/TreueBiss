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

    /**
     * Löst einen Gutschein ein. Geprüft wird serverseitig gegen den
     * Einlöse-Code des Betriebs, den das Personal an der Kasse eingibt.
     *
     * @throws InvalidRedeemCodeException bei falschem Code.
     * @throws VoucherNotRedeemableException wenn der Gutschein bereits
     *   eingelöst, abgelaufen oder unbekannt ist.
     */
    suspend fun redeemVoucher(voucherId: String, code: String? = null)

    /**
     * Holt die Gutscheine dieser Geräte-Identität vom Server in die lokale
     * Datenbank. Gedacht für den ersten Start nach einer Neuinstallation.
     */
    suspend fun restoreFromRemote()
}
