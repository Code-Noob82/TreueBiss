package com.dominikbaki.treuebiss.feature_vouchers.domain.model

import java.util.Date

/**
 * Repräsentiert einen Gutschein.
 */
data class Voucher(
    val id: String,
    val createdAt: Date,
    val expiresAt: Date,
    val isRedeemed: Boolean
)