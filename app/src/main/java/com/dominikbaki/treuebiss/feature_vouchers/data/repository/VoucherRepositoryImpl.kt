package com.dominikbaki.treuebiss.feature_vouchers.data.repository

import com.dominikbaki.treuebiss.feature_vouchers.data.local.dao.VoucherDao
import com.dominikbaki.treuebiss.feature_vouchers.data.mapper.toVoucher
import com.dominikbaki.treuebiss.feature_vouchers.data.mapper.toVoucherEntity
import com.dominikbaki.treuebiss.feature_vouchers.domain.model.Voucher
import com.dominikbaki.treuebiss.feature_vouchers.domain.repository.VoucherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoucherRepositoryImpl @Inject constructor(
    private val dao: VoucherDao
) : VoucherRepository {
    override suspend fun create(voucher: Voucher) {
        dao.insertVoucher(voucher.toVoucherEntity())
    }

    override fun observeAll(includeRedeemed: Boolean): Flow<List<Voucher>> {
        val voucherFlow = if (includeRedeemed) {
            // Logik für alle Gutscheine (später für eine Historie nützlich)
            // Fürs MVP konzentriere ich mich auf die offenen
            dao.observeOpenVouchers()
        } else {
            dao.observeOpenVouchers()
        }
        return voucherFlow.map { entities -> entities.map { it.toVoucher() } }
    }

    override suspend fun redeem(voucherId: String) {
        dao.markAsRedeemed(voucherId)
    }

    // Die Logik für abgelaufene Gutscheine würde hier implementiert
    override suspend fun markExpiredBefore(epochMillis: Long) {
        TODO("Not yet implemented")
    }
}