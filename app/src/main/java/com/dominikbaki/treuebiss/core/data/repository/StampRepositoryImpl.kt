package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.core.data.remote.datasource.SupabaseDataSource
import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.TenantRepository
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
    private val supabaseClient: SupabaseClient,
    private val tenantRepository: TenantRepository
) : StampRepository {

    private val tenantId: String get() = tenantRepository.activeTenantId

    override suspend fun addStamp(stamp: Stamp): Int {
        // 1. Lokal in Room speichern (Offline-First-Ansatz)
        val newCount = dao.insertStampAndCount(stamp.toStampEntity())
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
        return newCount
    }

    override fun observeStamps(): Flow<List<Stamp>> {
        return dao.observeAllStamps(tenantId).map { entities ->
            entities.map { it.toStamp() }
        }
    }

    override suspend fun clearStamps() {
        // Erst der Server: schlägt das fehl, bleiben die Stempel dort liegen -
        // sichtbar im Log statt still auseinanderlaufend.
        try {
            val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            if (currentUserId != null) {
                remoteDataSource.deleteAllStamps(currentUserId, tenantId)
                Log.d("StampRepositoryImpl", "Cleared remote stamps.")
            } else {
                Log.w("StampRepositoryImpl", "User not logged in, cannot clear remote stamps")
            }
        } catch (e: Exception) {
            Log.e("StampRepositoryImpl", "Failed to clear remote stamps", e)
        }

        try {
            dao.clearStamps(tenantId)
            Log.d("StampRepositoryImpl", "Successfully cleared local stamps.")
        } catch (e: Exception) {
            Log.e("StampRepositoryImpl", "Error clearing local stamps", e)
        }
    }

    override suspend fun restoreFromRemote() {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
        if (currentUserId == null) {
            Log.w("StampRepositoryImpl", "User not logged in, cannot restore stamps")
            return
        }
        val remoteStamps = remoteDataSource.getStamps(currentUserId, tenantId)
        if (remoteStamps.isNotEmpty()) {
            dao.insertStamps(remoteStamps.map { it.toStampEntity() })
        }
        Log.d("StampRepositoryImpl", "Restored ${remoteStamps.size} stamps from Supabase")
    }
}