package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.core.data.remote.datasource.SupabaseDataSource
import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.feature_stamps.data.local.dao.StampDao
import com.dominikbaki.treuebiss.feature_stamps.data.mapper.toStamp
import com.dominikbaki.treuebiss.feature_stamps.data.mapper.toStampDto
import com.dominikbaki.treuebiss.feature_stamps.data.mapper.toStampEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
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
    private val dao: StampDao,
    private val remoteDataSource: SupabaseDataSource,
    private val supabaseClient: SupabaseClient
) : StampRepository {

    override suspend fun addStamp(stamp: Stamp) {
        // 1. Lokal in Room speichern (Offline-First-Ansatz)
        dao.insertStamp(stamp.toStampEntity())
        // 2. Zu Supabase synchronisieren
        try {
            val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            if (currentUserId != null) {
                val stampDto = stamp.toStampDto(currentUserId)
                remoteDataSource.addStampToSupabase(stampDto)
                Log.d("StampRepositoryImpl", "Stamp ${stamp.id} synced to Supabase")
            } else {
                Log.w("StampRepositoryImpl", "User not logged in, cannot sync stamp")
            }
        } catch (e: Exception) {
            Log.e("StampRepositoryImpl", "Failed to sync stamp to supabase", e)
            // Fehlerbehandlung: Stempel als "nicht synchronisiert" markieren
        }
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
        Log.d("StampRepositoryImpl", "Clearing local stamps only...")
        try {
            dao.clearStamps() // NEU: Funktion zum Löschen aller Stempel
            Log.d("StampRepositoryImpl", "Successfully cleared local stamps.")
        } catch (e: Exception) {
            Log.e("StampRepositoryImpl", "Error clearing local stamps", e)
        }
    }
}