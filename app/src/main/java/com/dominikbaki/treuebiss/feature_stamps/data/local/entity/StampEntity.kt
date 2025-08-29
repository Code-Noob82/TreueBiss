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
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long
)