package com.dominikbaki.treuebiss.feature_stamps.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dominikbaki.treuebiss.feature_stamps.data.local.entity.StampEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) für Stempel. Definiert die Datenbankoperationen.
 */
@Dao
interface StampDao {
    /**
     * Fügt einen neuen Stempel in die Datenbank ein.
     * Bei einem Konflikt wird der bestehende Eintrag ersetzt.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStamp(stamp: StampEntity)

    /**
     * Beobachtet alle Stempel in der Datenbank und gibt sie als Flow zurück.
     * Der Flow emittiert automatisch neue Listen, wenn sich die Daten ändern.
     */
    @Query("SELECT * FROM stamps ORDER BY timestamp DESC")
    fun observeAllStamps(): Flow<List<StampEntity>>

    /**
     * Zählt die Gesamtzahl der Stempel in der Datenbank.
     */
    @Query("SELECT COUNT(id) FROM stamps")
    suspend fun countStamps(): Int
}