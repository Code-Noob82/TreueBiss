package com.dominikbaki.treuebiss.feature_tenant.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TenantDto(
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String,
    @SerialName("name") val name: String,
    @SerialName("loyalty_points_title") val loyaltyPointsTitle: String,
    @SerialName("vouchers_title") val vouchersTitle: String,
    @SerialName("daily_special_title") val dailySpecialTitle: String,
    @SerialName("primary_color") val primaryColor: String? = null,
    @SerialName("logo_url") val logoUrl: String? = null,
    @SerialName("stamps_per_card") val stampsPerCard: Int,
    @SerialName("voucher_validity_days") val voucherValidityDays: Int
)

@Serializable
data class OfferDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null
)

/**
 * Mitgliedschaft. Wird beim ersten erfolgreichen Verbindungsaufbau angelegt -
 * ohne sie greifen die RLS-Policies für Stempel und Gutscheine nicht.
 */
@Serializable
data class MembershipDto(
    @SerialName("user_id") val userId: String,
    @SerialName("tenant_id") val tenantId: String
)
