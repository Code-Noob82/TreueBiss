package com.dominikbaki.treuebiss.domain.usecases

import com.dominikbaki.treuebiss.domain.model.Stamp
import com.dominikbaki.treuebiss.domain.model.Voucher
import com.dominikbaki.treuebiss.domain.repository.StampRepository
import com.dominikbaki.treuebiss.domain.repository.VoucherRepository

class AddStampAndMaybeCreateVoucher(
    private val stampRepo: StampRepository,
    private val voucherRepo: VoucherRepository,
    private val time: () -> Long = { System.currentTimeMillis() }
) {
    suspend operator fun invoke(newStamp: Stamp) {
        stampRepo.addStamp(newStamp)
        val total = stampRepo.count()
        if (total % 10 == 0) {
            voucherRepo.create(
                Voucher(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "Gratis Produkt",
                    description = "10 Stempel gesammelt",
                    createdAt = time()
                )
            )
        }
    }
}