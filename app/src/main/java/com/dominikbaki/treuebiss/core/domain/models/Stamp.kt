package com.dominikbaki.treuebiss.core.domain.models

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Repräsentiert einen Stempel in der Domain-Schicht.
 * Dies ist das Modell, das die UI und die Business-Logik verwenden.
 */
@Serializable
data class Stamp(
    @SerialName("id")
    val id: String = UUID.randomUUID().toString(), // Geniere eine lokale UUID

    // 'created_at' ist der Standard-Spaltenname in Supabase für Zeitstempel.
    @SerialName("created_at")
    val timestamp: Instant,

    // 'user_id' wird von Supabase automatisch durch die RLS Policy gesetzt.
    @SerialName("user_id")
    val userId: String? = null
)