package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.core.data.remote.datasource.SupabaseDataSource
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.repository.TenantRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import com.dominikbaki.treuebiss.feature_vouchers.data.local.dao.VoucherDao
import com.dominikbaki.treuebiss.feature_vouchers.data.mapper.toVoucher
import com.dominikbaki.treuebiss.feature_vouchers.data.mapper.toVoucherEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoucherRepositoryImpl @Inject constructor(
    private val dao: VoucherDao,
    private val remoteDataSource: SupabaseDataSource,
    private val supabaseClient: SupabaseClient,
    private val tenantRepository: TenantRepository
) : VoucherRepository {

    private val tenantId: String get() = tenantRepository.activeTenantId
    override suspend fun cacheVoucher(voucher: Voucher) {
        // Nur lokal spiegeln: Angelegt hat den Gutschein die Datenbankfunktion
        // `issue_stamp`, in derselben Transaktion wie der zehnte Stempel.
        try {
            dao.insertVoucher(voucher.toVoucherEntity())
            Log.d("VoucherRepositoryImpl", "Cached voucher ${voucher.id} locally")
        } catch (e: Exception) {
            Log.e("VoucherRepositoryImpl", "Failed to cache voucher locally", e)
        }
    }

    override suspend fun redeemVoucher(voucherId: String) {
        withContext(Dispatchers.IO) {
            // --- Schritt 1: Lokal den Status ändern (bereits vorhanden) ---
            try {
                val logMessage1 = "Marking voucher as redeemed locally. ID: $voucherId"
                Log.d("VoucherRepoImpl", logMessage1)
                dao.markAsRedeemed(voucherId)
                val logMessage2 = "Successfully marked voucher as redeemed locally."
                Log.d("VoucherRepoImpl", logMessage2)
            } catch (e: Exception) {
                Log.e("VoucherRepoImpl", "Failed to mark voucher as redeemed locally", e)
                return@withContext
            }
            // --- Schritt 2: Den neuen Status an Supabase senden
            try {
                Log.d("VoucherRepoImpl", "Syncing redeemed status to Supabase. ID: $voucherId")
                remoteDataSource.setVoucherRedeemed(voucherId)
                Log.d(
                    "VoucherRepoImpl",
                    "Successfully synced redeemed status for voucher $voucherId."
                )
            } catch (e: Exception) {
                Log.e("VoucherRepoImpl", "Failed to sync redeemed status to Supabase.", e)
                // Fehlerbehandlung: Voucher als "nicht synchronisiert" markieren
            }
        }
    }

    override fun observeOpenVouchers(): Flow<List<Voucher>> {
        return dao.observeOpenVouchers(tenantId).map { entities -> entities.map { it.toVoucher() } }
    }

    override suspend fun restoreFromRemote() {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
        if (currentUserId == null) {
            Log.w("VoucherRepositoryImpl", "User not logged in, cannot restore vouchers")
            return
        }
        val remoteVouchers = remoteDataSource.getVouchers(currentUserId, tenantId)
        if (remoteVouchers.isNotEmpty()) {
            dao.insertVouchers(remoteVouchers.map { it.toVoucherEntity() })
        }
        Log.d("VoucherRepositoryImpl", "Restored ${remoteVouchers.size} vouchers from Supabase")
    }
}