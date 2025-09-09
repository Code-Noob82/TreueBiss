package com.dominikbaki.treuebiss.core.domain.repository

import com.dominikbaki.treuebiss.core.domain.models.Stamp
import kotlinx.coroutines.flow.Flow

/**
 * Port für alle Operationen, die Stempel betreffen.
 */
interface StampRepository {
    /** Fügt einen neuen Stempel hinzu (setzt device_id/tenant_id intern). */
    suspend fun addStamp(stamp: Stamp)

    /** Beobachtet alle Stempel der aktuellen Installation als Flow. */
    fun observeStamps(): Flow<List<Stamp>>

    /** Liefert die aktuelle Anzahl Stempel (z. B. für 10er-Logik). */
    suspend fun count(): Int

    suspend fun clearStamps() // NEU: Funktion zum Löschen aller Stempel
}