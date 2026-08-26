package com.dominikbaki.treuebiss.feature_stamps.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Repräsentiert einen einzelnen Stempel in der lokalen Room-Datenbank.
 *
 * @param id Der Primärschlüssel, wird von Room automatisch generiert.
 * @param timestamp Der Zeitstempel (in Millisekunden), wann der Stempel hinzugefügt wurde.
 */

@Entity(tableName = "stamps")
data class StampEntity(
    @PrimaryKey
    val id: String,
    val timestamp: Long,
    /** Der Betrieb, zu dem dieser Stempel gehört. */
    val tenantId: String
)