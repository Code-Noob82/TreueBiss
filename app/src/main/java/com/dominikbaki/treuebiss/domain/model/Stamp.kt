package com.dominikbaki.treuebiss.domain.model


/**
 * Repräsentiert einen einzelnen Stempel.
 */
data class Stamp(
    val id: String,
    val collectedAt: Long,
    val partnerId: String
)
