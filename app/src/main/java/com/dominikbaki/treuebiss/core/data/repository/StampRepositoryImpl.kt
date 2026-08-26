package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.core.data.remote.datasource.SupabaseDataSource
import com.dominikbaki.treuebiss.core.domain.models.ProofAlreadyUsedException
import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.core.domain.models.StampIssueResult
import com.dominikbaki.treuebiss.core.domain.models.StampProof
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.TenantRepository
import com.dominikbaki.treuebiss.feature_stamps.data.local.dao.StampDao
import com.dominikbaki.treuebiss.feature_stamps.data.mapper.toStamp
import com.dominikbaki.treuebiss.feature_stamps.data.mapper.toStampEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Konkrete Implementierung des StampRepository.
 *
 * Room ist hier nur noch Lesecache. Angelegt werden Stempel ausschließlich
 * serverseitig - die App hat auf `stamps` kein Schreibrecht mehr.
 */
@Singleton
class StampRepositoryImpl @Inject constructor(
    private val dao: StampDao,
    private val remoteDataSource: SupabaseDataSource,
    private val supabaseClient: SupabaseClient,
    private val tenantRepository: TenantRepository
) : StampRepository {

    private val tenantId: String get() = tenantRepository.activeTenantId

    override suspend fun issueStamp(proof: StampProof): StampIssueResult {
        val result = try {
            remoteDataSource.issueStamp(
                tenantId = tenantId,
                proofRef = proof.reference,
                source = proof.source.wireValue
            )
        } catch (e: Exception) {
            // Postgres meldet einen bereits verwendeten Beleg als
            // unique_violation. Für die UI ist das kein Fehler, sondern
            // eine Aussage: dieser Bon wurde schon eingelöst.
            if (e.isUniqueViolation()) {
                throw ProofAlreadyUsedException("Beleg bereits verwendet: ${proof.reference}")
            }
            throw e
        }

        // Den serverseitig erzeugten Stempel lokal spiegeln, damit die UI
        // sofort reagiert.
        val stamp = Stamp(
            id = result.stampId,
            timestamp = kotlinx.datetime.Clock.System.now(),
            tenantId = tenantId
        )
        dao.insertStamp(stamp.toStampEntity())

        val voucher = result.voucherId?.let { voucherId ->
            val now = kotlinx.datetime.Clock.System.now()
            Voucher(
                id = voucherId,
                createdAt = now,
                creationDate = now.toEpochMilliseconds(),
                expiresAt = result.voucherExpiresAt ?: now.toEpochMilliseconds(),
                tenantId = tenantId
            )
        }

        Log.d("StampRepositoryImpl", "Stamp issued (${result.stampCount}), voucher=${result.voucherId}")
        return StampIssueResult(
            stampId = result.stampId,
            stampCount = result.stampCount,
            voucher = voucher
        )
    }

    override fun observeStamps(): Flow<List<Stamp>> {
        return dao.observeAllStamps(tenantId).map { entities ->
            entities.map { it.toStamp() }
        }
    }

    override suspend fun clearStamps() {
        // Nur lokal: Serverseitig setzt `issue_stamp` die Karte in derselben
        // Transaktion zurück, in der der Gutschein entsteht.
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

/**
 * Erkennt den Postgres-Fehlercode für eine verletzte Eindeutigkeit (23505).
 * Der Supabase-Client verpackt ihn in die Fehlermeldung, deshalb die Textsuche.
 */
private fun Exception.isUniqueViolation(): Boolean {
    val text = (message ?: "") + (cause?.message ?: "")
    return text.contains("23505") || text.contains("duplicate key", ignoreCase = true)
}
