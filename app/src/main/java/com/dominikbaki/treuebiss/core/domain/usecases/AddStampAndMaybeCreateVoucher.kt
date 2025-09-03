//package com.dominikbaki.treuebiss.core.domain.usecases
//
//import com.dominikbaki.treuebiss.feature_stamps.domain.model.Stamp
//import com.dominikbaki.treuebiss.feature_vouchers.domain.model.Voucher
//import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
//import com.dominikbaki.treuebiss.feature_vouchers.domain.repository.VoucherRepository
//import java.util.UUID
//
//class AddStampAndMaybeCreateVoucher(
//    private val stampRepo: StampRepository,
//    private val voucherRepo: VoucherRepository,
//    private val time: () -> Long = { System.currentTimeMillis() }
//) {
//    suspend operator fun invoke(newStamp: Stamp) {
//        stampRepo.addStamp(newStamp)
//        val total = stampRepo.count()
//        if (total % 10 == 0) {
//            voucherRepo.create(
//                Voucher(
//                    id = UUID.randomUUID().toString(),
//                    title = "Gratis Produkt",
//                    description = "10 Stempel gesammelt",
//                    createdAt = time()
//                )
//            )
//        }
//    }
//}