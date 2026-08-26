package com.dominikbaki.treuebiss.feature_stamps.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data Transfer Object für einen Stempel, das für die Supabase-Serialisierung verwendet wird.
 * Beinhaltet die user_id, die für RLS entscheidend ist.
 */
@Serializable
data class StampDto(
    @SerialName("id")
    val id: String,
    // Instant statt String: Supabase erwartet für `timestamptz` einen
    // ISO-8601-String, den die Bibliothek selbst erzeugt und wieder einliest.
    @SerialName("created_at")
    val createdAt: Instant,
    @SerialName("user_id")
    val userId: String,
    @SerialName("tenant_id")
    val tenantId: String
)

/**
 * Rückgabe der Datenbankfunktion `issue_stamp`.
 *
 * `voucherId` ist gesetzt, wenn dieser Stempel die Karte vollgemacht hat -
 * der Gutschein entsteht dann in derselben Transaktion.
 */
@Serializable
data class IssueStampResultDto(
    @SerialName("stamp_id") val stampId: String,
    @SerialName("stamp_count") val stampCount: Int,
    @SerialName("voucher_id") val voucherId: String? = null,
    @SerialName("voucher_expires_at") val voucherExpiresAt: Long? = null
)
