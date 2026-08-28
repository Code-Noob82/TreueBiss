package com.dominikbaki.treuebiss.feature_tenant.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Lokaler Zwischenspeicher, damit Branding auch offline steht. */
@Entity(tableName = "tenants")
data class TenantEntity(
    @PrimaryKey val id: String,
    val name: String,
    val loyaltyPointsTitle: String,
    val vouchersTitle: String,
    val dailySpecialTitle: String,
    val primaryColor: String?,
    val logoUrl: String?,
    val stampsPerCard: Int,
    val voucherValidityDays: Int,
    val requiresRedeemCode: Boolean
)

@Entity(tableName = "offers")
data class OfferEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val title: String,
    val description: String?
)
