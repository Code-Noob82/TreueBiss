package com.dominikbaki.treuebiss.domain.model

/**
 * Repräsentiert einen Gutschein.
 */
data class Voucher(
    val id: String,
    val title: String,
    val description: String,
    val isRedeemed: Boolean = false,
    val createdAt: Long
)
