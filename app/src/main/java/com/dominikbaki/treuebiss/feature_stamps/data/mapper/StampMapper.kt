package com.dominikbaki.treuebiss.feature_stamps.data.mapper

import com.dominikbaki.treuebiss.feature_stamps.data.local.entity.StampEntity
import com.dominikbaki.treuebiss.feature_stamps.domain.model.Stamp

/**
 * Wandelt ein StampEntity (Datenbank-Objekt) in ein Stamp-Objekt (Domain-Objekt) um.
 */
fun StampEntity.toStamp(): Stamp {
    return Stamp(
        id = id,
        timestamp = timestamp,
        isSynced = isSynced
    )
}

/**
 * Wandelt ein Stamp-Objekt (Domain-Objekt) in ein StampEntity (Datenbank-Objekt) um.
 */
fun Stamp.toStampEntity(): StampEntity {
    return StampEntity(
        id = id,
        timestamp = timestamp,
        isSynced = isSynced
    )
}