package com.dominikbaki.treuebiss.core.domain.repository

import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.core.domain.models.StampIssueResult
import com.dominikbaki.treuebiss.core.domain.models.StampProof
import kotlinx.coroutines.flow.Flow

/**
 * Port für alle Operationen, die Stempel betreffen.
 */
interface StampRepository {
    /**
     * Lässt einen Stempel gegen einen Kaufnachweis vergeben.
     *
     * Die Vergabe läuft ausschließlich auf dem Server: Nur dort lässt sich
     * prüfen, ob der Nachweis echt und noch unbenutzt ist. Ohne Verbindung
     * ist keine Vergabe möglich.
     *
     * @throws ProofAlreadyUsedException wenn der Nachweis schon verwendet wurde.
     */
    suspend fun issueStamp(proof: StampProof): StampIssueResult

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