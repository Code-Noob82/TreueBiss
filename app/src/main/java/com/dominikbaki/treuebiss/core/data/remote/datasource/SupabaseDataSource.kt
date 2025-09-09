package com.dominikbaki.treuebiss.core.data.remote.datasource

import com.dominikbaki.treuebiss.feature_stamps.data.remote.dto.StampDto
import com.dominikbaki.treuebiss.feature_vouchers.data.remote.dto.VoucherDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    /**
     * Fügt einen neuen Stempel in die 'stamps'-Tabelle bei Supabase ein.
     */
    suspend fun addStampToSupabase(stamp: StampDto) {
        supabaseClient.from("stamps").insert(stamp)
    }

    /**
     * Fügt einen neuen Gutschein in die 'vouchers'-Tabelle bei Supabase ein.
     */
    suspend fun addVoucherToSupabase(voucher: VoucherDto) {
        supabaseClient.from("vouchers").insert(voucher)
    }
}