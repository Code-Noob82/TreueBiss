package com.dominikbaki.treuebiss.core.domain.models

import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Repräsentiert einen Stempel in der Domain-Schicht.
 * Dies ist das Modell, das die UI und die Business-Logik verwenden.
 *
 * Bewusst ohne Serialisierungs-Annotationen: Das Mapping auf die
 * Supabase-Spaltennamen übernimmt [com.dominikbaki.treuebiss.feature_stamps.data.remote.dto.StampDto].
 */
data class Stamp(
    val id: String = UUID.randomUUID().toString(), // Generiere eine lokale UUID
    val timestamp: Instant,
    val userId: String? = null
)
