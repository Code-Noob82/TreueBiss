package com.dominikbaki.treuebiss.data.local.dao

import com.dominikbaki.treuebiss.data.local.entity.StampEntity
import kotlinx.coroutines.flow.Flow

// Platzhalter für das Room DAO Interface
interface StampDao {
    suspend fun insert(stampEntity: StampEntity)
    fun observeFor(deviceId: String, tenantId: String): Flow<List<StampEntity>>
    suspend fun countFor(deviceId: String, tenantId: String): Int
}