package com.dominikbaki.treuebiss.feature_stamps.data.remote.dto

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
    @SerialName("created_at")
    val timeStamp: String,
    @SerialName("user_id")
    val userId: String
)