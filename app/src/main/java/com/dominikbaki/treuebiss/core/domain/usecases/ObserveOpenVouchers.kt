package com.dominikbaki.treuebiss.core.domain.usecases

import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case zum Beobachten aller offenen (nicht eingelösten) Gutscheine.
 */
class ObserveOpenVouchers @Inject constructor(
    private val voucherRepository: VoucherRepository
) {
    operator fun invoke(): Flow<List<Voucher>> {
        // Ruft die Repository-Funktion mit dem spezifischen Filter für offene Gutscheine auf.
        return voucherRepository.observeAll(includeRedeemed = false)
    }
}