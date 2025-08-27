//package com.dominikbaki.treuebiss.data.repository
//
//import android.util.Log
//import com.dominikbaki.treuebiss.data.local.dao.StampDao
//import com.dominikbaki.treuebiss.data.local.entity.toEntity
//import com.dominikbaki.treuebiss.data.local.entity.toDomain
//import com.dominikbaki.treuebiss.data.remote.datasource.SupabaseStampDataSource
//import com.dominikbaki.treuebiss.data.util.DeviceIdProvider
//import com.dominikbaki.treuebiss.data.util.TenantProvider
//import com.dominikbaki.treuebiss.feature_stamps.domain.model.Stamp
//import com.dominikbaki.treuebiss.domain.repository.StampRepository
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.flow.Flow
//import kotlinx.coroutines.flow.emitAll
//import kotlinx.coroutines.flow.flow
//import kotlinx.coroutines.flow.map
//import kotlinx.coroutines.withContext
//import javax.inject.Inject
//import javax.inject.Singleton
///**
// * Adapter für das StampRepository, der Room als SSOT nutzt und mit Supabase synchronisiert.
// */
//@Singleton
//class StampRepositoryImpl @Inject constructor(
//    private val local: StampDao,
//    private val remote: SupabaseStampDataSource,
//    private val deviceIdProvider: DeviceIdProvider,
//    private val tenantProvider: TenantProvider
//) : StampRepository {
//
//    override suspend fun addStamp(stamp: Stamp) = withContext(Dispatchers.IO) {
//        val deviceId = deviceIdProvider.get()
//        val tenantId = tenantProvider.get()
//
//        // 1) Lokal speichern (schnelle UI)
//        local.insert(stamp.toEntity(deviceId = deviceId, tenantId = tenantId))
//        // 2) Best-effort Remote-Sync
//        try {
//            remote.upsert(stamp, deviceId, tenantId)
//        } catch (e: Exception) {
//            Log.w("StampRepository", "Remote upsert failed; will retry later", e)
//        }
//        // Rückgabewert ist Unit -> passt zur Interface-Signatur
//    }
//
//    override fun observeStamps(): Flow<List<Stamp>> = flow {
//        // suspend Aufrufe sind hier erlaubt
//        val deviceId = deviceIdProvider.get()
//        val tenantId = tenantProvider.get()
//
//        val mapped: Flow<List<Stamp>> =
//            local.observeFor(deviceId, tenantId)
//                .map { entities -> entities.map { it.toDomain() } }
//        emitAll(mapped)
//}
//    override suspend fun count(): Int = withContext(Dispatchers.IO) {
//        val deviceId = deviceIdProvider.get()
//        val tenantId = tenantProvider.get()
//        local.countFor(deviceId, tenantId)
//    }
//}