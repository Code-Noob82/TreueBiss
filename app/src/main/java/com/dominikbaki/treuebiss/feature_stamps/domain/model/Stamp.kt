package com.dominikbaki.treuebiss.feature_stamps.domain.model


/**
 * Repräsentiert einen Stempel in der Domain-Schicht.
 * Dies ist das Modell, das die UI und die Business-Logik verwenden.
 */
data class Stamp(
    val id: Int,
    val timestamp: Long,
    val isSynced: Boolean
)