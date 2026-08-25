package com.dominikbaki.treuebiss.feature_stamps.data.mapper

import com.dominikbaki.treuebiss.feature_stamps.data.local.entity.StampEntity
import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.feature_stamps.data.remote.dto.StampDto
import kotlinx.datetime.Instant

/**
 * Konvertiert die Datenbank-Entität in das Domain-Modell
 */
fun StampEntity.toStamp(): Stamp {
    return Stamp(
        id = this.id,
        timestamp = Instant.fromEpochMilliseconds(this.timestamp)
    )
}

/**
 * Konvertiert das Domain-Modell in die Datenbank-Entität
 */
fun Stamp.toStampEntity(): StampEntity {
    return StampEntity(
        id = this.id,
        timestamp = this.timestamp.toEpochMilliseconds()
    )
}

fun Stamp.toStampDto(currentUserId: String): StampDto {
    return StampDto(
        id = this.id,
        createdAt = this.timestamp,
        userId = currentUserId
    )
}

/**
 * Konvertiert eine von Supabase geladene Zeile in die Datenbank-Entität.
 */
fun StampDto.toStampEntity(): StampEntity {
    return StampEntity(
        id = this.id,
        timestamp = this.createdAt.toEpochMilliseconds()
    )
}