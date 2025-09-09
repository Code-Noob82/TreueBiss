package com.dominikbaki.treuebiss.feature_vouchers.data.remote.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Repräsentiert die Datenstruktur, wie sie an die Supabase API gesendet wird.
 * Jedes Feld, das in der Datenbank einen anderen Namen hat (snake_case),
 * MUSS die @SerialName Annotation haben.
 */
@Serializable
data class VoucherDto(
    // Die ID wird vom Client generiert und gesendet.
    @SerialName("id")
    val id: String,

    // Dieses Feld ist vom Typ Instant, da Supabase für `timestamptz`-Spalten
    // einen ISO-8601 formatierten String erwartet, den die Bibliothek
    // automatisch aus einem Instant-Objekt generiert.
    @SerialName("created_at")
    val createdAt: Instant,

    // Dieses Feld ist Long, passend zum `int8` Spaltentyp in der DB.
    @SerialName("creation_date")
    val creationDate: Long,

    // Dieses Feld ist Long, passend zum `int8` Spaltentyp in der DB.
    @SerialName("expires_at")
    val expiresAt: Long,

    // Dieses Feld ist Boolean, passend zum `bool` Spaltentyp in der DB.
    @SerialName("is_redeemed")
    val isRedeemed: Boolean = false,

    // Die User-ID wird vom Repository hinzugefügt.
    @SerialName("user_id")
    val userId: String
)