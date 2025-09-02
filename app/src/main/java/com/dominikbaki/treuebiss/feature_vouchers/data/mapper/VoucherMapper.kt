package com.dominikbaki.treuebiss.feature_vouchers.data.mapper

import com.dominikbaki.treuebiss.feature_vouchers.data.local.entity.VoucherEntity
import com.dominikbaki.treuebiss.feature_vouchers.domain.model.Voucher

fun VoucherEntity.toVoucher(): Voucher {
    return Voucher(
        id = id,
        creationDate = creationDate,
        expiresAt = expiresAt,
        isRedeemed = isRedeemed

    )
}

fun Voucher.toVoucherEntity(): VoucherEntity {
    return VoucherEntity(
        id = id,
        creationDate = creationDate,
        expiresAt = expiresAt,
        isRedeemed = isRedeemed

    )
}