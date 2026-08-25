package com.dominikbaki.treuebiss.core.domain.usecases

import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import javax.inject.Inject

/**
 * Use Case zum Einlösen eines spezifischen Gutscheins.
 */
class RedeemVoucher @Inject constructor(
    private val voucherRepository: VoucherRepository
) {
    suspend operator fun invoke(voucherId: String) {
        // Delegiert den Aufruf direkt an das Repository.
        voucherRepository.redeemVoucher(voucherId)
    }
}