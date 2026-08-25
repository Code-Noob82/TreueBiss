package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.core.data.remote.datasource.SupabaseDataSource
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import com.dominikbaki.treuebiss.feature_vouchers.data.local.dao.VoucherDao
import com.dominikbaki.treuebiss.feature_vouchers.data.mapper.toVoucher
import com.dominikbaki.treuebiss.feature_vouchers.data.mapper.toVoucherDto
import com.dominikbaki.treuebiss.feature_vouchers.data.mapper.toVoucherEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
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
    private val supabaseClient: SupabaseClient
) : VoucherRepository {
    override suspend fun createVoucher(voucher: Voucher) {
        // --- SCHRITT 1: LOKAL IN ROOM SPEICHERN (MIT VERBESSERTEM DEBUGGING) ---
        try {
            Log.d(
                "VoucherRepositoryImpl",
                "Attempting to insert voucher locally. ID: ${voucher.id}"
            )
            dao.insertVoucher(voucher.toVoucherEntity())
            Log.d(
                "VoucherRepositoryImpl",
                "Successfully inserted voucher locally. ID: ${voucher.id}"
            )
        } catch (e: Exception) {
            Log.e("VoucherRepositoryImpl", "Failed to insert voucher locally", e)
            return
        }
        // --- SCHRITT 2: ZU SUPABASE SYNCHRONISIEREN ---
        try {
            val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
            if (currentUserId != null) {
                val voucherDto = voucher.toVoucherDto(currentUserId)
                remoteDataSource.addVoucherToSupabase(voucherDto)
                Log.d(
                    "VoucherRepositoryImpl",
                    "Voucher ${voucher.id} synced successfully with Supabase"
                )
            } else {
                Log.w("VoucherRepositoryImpl", "User not logged in, cannot sync voucher")
            }
        } catch (e: Exception) {
            Log.e("VoucherRepositoryImpl", "Failed to sync voucher to supabase", e)
            // Fehlerbehandlung: Voucher als "nicht synchronisiert" markieren
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
                supabaseClient.postgrest["vouchers"]
                    .update({
                        set("is_redeemed", true)
                    }) {
                        filter {
                            eq("id", voucherId)
                        }
                    }
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
        return dao.observeOpenVouchers().map { entities -> entities.map { it.toVoucher() } }
    }
}