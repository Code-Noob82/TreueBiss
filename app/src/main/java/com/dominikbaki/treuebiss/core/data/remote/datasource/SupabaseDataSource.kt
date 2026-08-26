package com.dominikbaki.treuebiss.core.data.remote.datasource

import com.dominikbaki.treuebiss.feature_stamps.data.remote.dto.IssueStampResultDto
import com.dominikbaki.treuebiss.feature_stamps.data.remote.dto.StampDto
import com.dominikbaki.treuebiss.feature_tenant.data.remote.dto.MembershipDto
import com.dominikbaki.treuebiss.feature_tenant.data.remote.dto.OfferDto
import com.dominikbaki.treuebiss.feature_tenant.data.remote.dto.TenantDto
import com.dominikbaki.treuebiss.feature_vouchers.data.remote.dto.VoucherDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    /**
     * Lässt einen Stempel gegen einen Kaufnachweis vergeben.
     *
     * Die App darf in `stamps` und `vouchers` nicht schreiben - das erledigt
     * diese Datenbankfunktion, die den Nachweis prüft und bei voller Karte
     * gleich den Gutschein miterzeugt.
     */
    suspend fun issueStamp(
        tenantId: String,
        proofRef: String,
        source: String
    ): IssueStampResultDto {
        return supabaseClient.postgrest.rpc(
            function = "issue_stamp",
            parameters = buildJsonObject {
                put("p_tenant_id", tenantId)
                put("p_proof_ref", proofRef)
                put("p_source", source)
            }
        ).decodeList<IssueStampResultDto>().first()
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
     * Löst einen Gutschein ein - nur mit dem Einlöse-Code des Betriebs.
     *
     * Die App darf `vouchers` nicht aktualisieren; das erledigt diese
     * Datenbankfunktion, nachdem sie den Code geprüft hat.
     */
    suspend fun redeemVoucher(voucherId: String, code: String) {
        supabaseClient.postgrest.rpc(
            function = "redeem_voucher",
            parameters = buildJsonObject {
                put("p_voucher_id", voucherId)
                put("p_code", code)
            }
        )
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
        // ignoreDuplicates erzeugt ON CONFLICT DO NOTHING statt DO UPDATE.
        // Ein Update braeuchte zusaetzlich eine update-Policy auf memberships -
        // und es gibt an einer bestehenden Mitgliedschaft nichts zu aendern.
        supabaseClient.from(TABLE_MEMBERSHIPS).upsert(
            values = listOf(MembershipDto(userId = userId, tenantId = tenantId)),
            onConflict = "user_id,tenant_id",
            ignoreDuplicates = true
        )
    }
}
