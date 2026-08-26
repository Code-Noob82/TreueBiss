package com.dominikbaki.treuebiss.core.data.remote.datasource

import com.dominikbaki.treuebiss.feature_stamps.data.remote.dto.StampDto
import com.dominikbaki.treuebiss.feature_tenant.data.remote.dto.MembershipDto
import com.dominikbaki.treuebiss.feature_tenant.data.remote.dto.OfferDto
import com.dominikbaki.treuebiss.feature_tenant.data.remote.dto.TenantDto
import com.dominikbaki.treuebiss.feature_vouchers.data.remote.dto.VoucherDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kapselt alle Postgrest-Zugriffe auf die Supabase-Tabellen.
 *
 * Die Filter auf `user_id` sind redundant zu den RLS-Policies, machen die
 * Absicht aber im Code sichtbar und schützen davor, dass eine fehlende
 * Policy unbemerkt fremde Zeilen liefert.
 */
@Singleton
class SupabaseDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private companion object {
        const val TABLE_STAMPS = "stamps"
        const val TABLE_VOUCHERS = "vouchers"
        const val TABLE_TENANTS = "tenants"
        const val TABLE_OFFERS = "offers"
        const val TABLE_MEMBERSHIPS = "memberships"
    }

    /** Fügt einen neuen Stempel in die 'stamps'-Tabelle bei Supabase ein. */
    suspend fun addStampToSupabase(stamp: StampDto) {
        supabaseClient.from(TABLE_STAMPS).insert(stamp)
    }

    /** Fügt einen neuen Gutschein in die 'vouchers'-Tabelle bei Supabase ein. */
    suspend fun addVoucherToSupabase(voucher: VoucherDto) {
        supabaseClient.from(TABLE_VOUCHERS).insert(voucher)
    }

    /** Lädt alle Stempel des Nutzers - Grundlage für die Wiederherstellung. */
    suspend fun getStamps(userId: String, tenantId: String): List<StampDto> {
        return supabaseClient.from(TABLE_STAMPS).select {
            filter {
                eq("user_id", userId)
                eq("tenant_id", tenantId)
            }
        }.decodeList<StampDto>()
    }

    /** Lädt alle Gutscheine des Nutzers - Grundlage für die Wiederherstellung. */
    suspend fun getVouchers(userId: String, tenantId: String): List<VoucherDto> {
        return supabaseClient.from(TABLE_VOUCHERS).select {
            filter {
                eq("user_id", userId)
                eq("tenant_id", tenantId)
            }
        }.decodeList<VoucherDto>()
    }

    /**
     * Löscht alle Stempel des Nutzers.
     * Gegenstück zum Zurücksetzen der Karte: ohne das würde die Tabelle
     * unbegrenzt wachsen und vom lokalen Stand abdriften.
     */
    suspend fun deleteAllStamps(userId: String, tenantId: String) {
        supabaseClient.from(TABLE_STAMPS).delete {
            filter {
                eq("user_id", userId)
                eq("tenant_id", tenantId)
            }
        }
    }

    /** Markiert einen Gutschein serverseitig als eingelöst. */
    suspend fun setVoucherRedeemed(voucherId: String) {
        supabaseClient.from(TABLE_VOUCHERS).update({
            set("is_redeemed", true)
        }) {
            filter { eq("id", voucherId) }
        }
    }

    /** Lädt die Stammdaten des Betriebs (Branding, Kartenregeln). */
    suspend fun getTenant(tenantId: String): TenantDto? {
        return supabaseClient.from(TABLE_TENANTS).select {
            filter { eq("id", tenantId) }
        }.decodeList<TenantDto>().firstOrNull()
    }

    /** Lädt die Angebote des Betriebs. */
    suspend fun getOffers(tenantId: String): List<OfferDto> {
        return supabaseClient.from(TABLE_OFFERS).select {
            filter { eq("tenant_id", tenantId) }
        }.decodeList<OfferDto>()
    }

    /**
     * Legt die Mitgliedschaft an, falls sie fehlt.
     *
     * Ohne sie greifen die RLS-Policies für Stempel und Gutscheine nicht -
     * jedes Einfügen würde serverseitig abgelehnt.
     */
    suspend fun ensureMembership(userId: String, tenantId: String) {
        supabaseClient.from(TABLE_MEMBERSHIPS)
            .upsert(MembershipDto(userId = userId, tenantId = tenantId))
    }
}
