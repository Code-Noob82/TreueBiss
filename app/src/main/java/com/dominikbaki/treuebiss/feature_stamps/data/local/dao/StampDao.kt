package com.dominikbaki.treuebiss.feature_stamps.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
    /**
     * Fügt mehrere Stempel ein. Bestehende IDs werden ersetzt, damit die
     * Wiederherstellung vom Server wiederholbar ist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStamps(stamps: List<StampEntity>)

    @Query("SELECT * FROM stamps WHERE tenantId = :tenantId ORDER BY timestamp DESC")
    fun observeAllStamps(tenantId: String): Flow<List<StampEntity>>

    /**
     * Zählt die Gesamtzahl der Stempel in der Datenbank.
     */
    @Query("SELECT COUNT(id) FROM stamps WHERE tenantId = :tenantId")
    suspend fun countStamps(tenantId: String): Int

    @Query("DELETE FROM stamps WHERE tenantId = :tenantId")
    suspend fun clearStamps(tenantId: String)

    /**
     * Fügt einen Stempel ein und liefert die neue Gesamtzahl zurück.
     *
     * Beides läuft in einer Transaktion: Würde die UI stattdessen ihren
     * zuletzt beobachteten Stand hochzählen, könnten schnelle Klicks
     * dieselbe Zahl zweimal sehen und die 10er-Grenze verpassen.
     */
    @Transaction
    suspend fun insertStampAndCount(stamp: StampEntity): Int {
        insertStamp(stamp)
        return countStamps(stamp.tenantId)
    }
}