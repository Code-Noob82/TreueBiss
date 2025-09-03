package com.dominikbaki.treuebiss.feature_stamps.data.repository

import com.dominikbaki.treuebiss.feature_stamps.data.local.dao.StampDao
import com.dominikbaki.treuebiss.feature_stamps.data.mapper.toStamp
import com.dominikbaki.treuebiss.feature_stamps.data.mapper.toStampEntity
import com.dominikbaki.treuebiss.feature_stamps.domain.model.Stamp
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


/**
 * Konkrete Implementierung des StampRepository.
 * Kapselt den Datenzugriff auf die lokale Room-Datenbank.
 */
@Singleton
class StampRepositoryImpl @Inject constructor(
    private val dao: StampDao
) : StampRepository {

    override suspend fun addStamp(stamp: Stamp) {
        // Hier wird später auch die Synchronisation mit Supabase stattfinden
        dao.insertStamp(stamp.toStampEntity())
    }

    override fun observeStamps(): Flow<List<Stamp>> {
        return dao.observeAllStamps().map { entities ->
            entities.map { it.toStamp() }
        }
    }

    override suspend fun count(): Int {
        return dao.countStamps()
    }

    override suspend fun clearStamps() {
        dao.clearStamps() // NEU: Funktion zum Löschen aller Stempel
    }
}