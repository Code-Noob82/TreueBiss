package com.dominikbaki.treuebiss.core.domain.repository

import com.dominikbaki.treuebiss.core.domain.models.Stamp
import kotlinx.coroutines.flow.Flow

/**
 * Port für alle Operationen, die Stempel betreffen.
 */
interface StampRepository {
    /**
      * Fügt einen neuen Stempel hinzu und gibt die neue Gesamtzahl zurück.
      * Die Zahl kommt aus derselben Transaktion wie das Einfügen.
      */
    suspend fun addStamp(stamp: Stamp): Int

    /** Beobachtet alle Stempel der aktuellen Installation als Flow. */
    fun observeStamps(): Flow<List<Stamp>>

    /** Löscht alle Stempel - lokal und auf dem Server. */
    suspend fun clearStamps()

    /**
     * Holt die Stempel dieser Geräte-Identität vom Server in die lokale
     * Datenbank. Gedacht für den ersten Start nach einer Neuinstallation.
     */
    suspend fun restoreFromRemote()
}