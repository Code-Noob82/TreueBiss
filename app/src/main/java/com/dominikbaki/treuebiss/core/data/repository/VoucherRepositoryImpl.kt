package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.core.data.remote.datasource.SupabaseDataSource
import com.dominikbaki.treuebiss.core.domain.models.InvalidRedeemCodeException
import com.dominikbaki.treuebiss.core.domain.models.RedeemNotConfiguredException
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.models.VoucherNotRedeemableException
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

    override suspend fun redeemVoucher(voucherId: String, code: String?) {
        // Zuerst der Server: Nur dort laesst sich der Code pruefen. Lokal
        // vorab zu markieren hiesse, bei falschem Code zuruecknehmen zu
        // muessen - und bei Verbindungsverlust den Gutschein zu verlieren.
        try {
            remoteDataSource.redeemVoucher(voucherId, code)
        } catch (e: Exception) {
            throw e.toRedeemFailure(voucherId)
        }

        withContext(Dispatchers.IO) {
            try {
                dao.markAsRedeemed(voucherId)
                Log.d("VoucherRepositoryImpl", "Voucher $voucherId redeemed")
            } catch (e: Exception) {
                // Serverseitig ist der Gutschein weg; der lokale Stand holt
                // das beim naechsten Abgleich nach.
                Log.e("VoucherRepositoryImpl", "Failed to mark voucher redeemed locally", e)
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

/**
 * Übersetzt die Fehlercodes von `redeem_voucher` in Fälle, die die UI
 * unterscheiden kann. Der Supabase-Client verpackt sie in die Meldung,
 * deshalb die Textsuche.
 */
private fun Exception.toRedeemFailure(voucherId: String): Exception {
    val text = (message ?: "") + (cause?.message ?: "")
    return when {
        // Vor dem falschen Code pruefen: Beide Faelle drehen sich um den
        // Code, aber nur einer ist die Schuld des Kunden.
        text.contains("no redeem code configured") ->
            RedeemNotConfiguredException("Betrieb verlangt einen Code, hat aber keinen: $voucherId")

        text.contains("invalid redeem code") || text.contains("42501") ->
            InvalidRedeemCodeException("Falscher Einlöse-Code für $voucherId")

        text.contains("already redeemed") || text.contains("expired") ||
            text.contains("not found") ->
            VoucherNotRedeemableException(text)

        else -> this
    }
}
