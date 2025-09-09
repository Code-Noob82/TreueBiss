package com.dominikbaki.treuebiss.core.domain.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Repräsentiert einen Gutschein in der Domain-Schicht (die "saubere" Logik).
 */
@Serializable
data class Voucher(
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(),

    // 'created_at' ist ein echter Zeitstempel (timestamptz)
    @SerialName("created_at")
    val createdAt: Instant,

    // 'creation_date' ist eine Zahl (int8), also ein Long in Kotlin.
    // Stellt normalerweise einen Unix-Timestamp dar.
    @SerialName("created_date")
    val creationDate: Long,

    // 'expires_at' ist ebenfalls eine Zahl (int8), also ein Long.
    @SerialName("expires_at")
    val expiresAt: Long,


    @SerialName("is_redeemed")
    val isRedeemed: Boolean = false,


    @SerialName("user_id")
    val userId: String? = null
)