package com.dominikbaki.treuebiss.feature_vouchers.data.mapper

import com.dominikbaki.treuebiss.feature_vouchers.data.local.entity.VoucherEntity
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.feature_vouchers.data.remote.dto.VoucherDto
import kotlinx.datetime.Instant

// --- Von der Datenbank-Entität zum Domain-Modell ---
fun VoucherEntity.toVoucher(): Voucher {
    return Voucher(
        id = this.id,
        createdAt = Instant.fromEpochMilliseconds(this.creationDate),
        creationDate = this.creationDate,
        expiresAt = this.expiresAt,
        isRedeemed = this.isRedeemed

    )
}

// --- Vom Domain-Modell zur Datenbank-Entität ---
fun Voucher.toVoucherEntity(): VoucherEntity {
    return VoucherEntity(
        id = this.id,
        creationDate = this.creationDate,
        expiresAt = this.expiresAt,
        isRedeemed = this.isRedeemed
    )
}

// --- Von einer Supabase-Zeile zur Datenbank-Entität ---
fun VoucherDto.toVoucherEntity(): VoucherEntity {
    return VoucherEntity(
        id = this.id,
        creationDate = this.creationDate,
        expiresAt = this.expiresAt,
        isRedeemed = this.isRedeemed
    )
}

// --- Vom Domain-Modell zum Data Transfer Object (für Supabase) ---
fun Voucher.toVoucherDto(currentUserId: String): VoucherDto {
    return VoucherDto(
        id = this.id,
        createdAt = this.createdAt,
        creationDate = this.creationDate,
        expiresAt = this.expiresAt,
        isRedeemed = this.isRedeemed,
        userId = currentUserId
    )
}