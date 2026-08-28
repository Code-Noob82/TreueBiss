package com.dominikbaki.treuebiss.feature_tenant.data.mapper

import com.dominikbaki.treuebiss.core.domain.models.Offer
import com.dominikbaki.treuebiss.core.domain.models.Tenant
import com.dominikbaki.treuebiss.feature_tenant.data.local.entity.OfferEntity
import com.dominikbaki.treuebiss.feature_tenant.data.local.entity.TenantEntity
import com.dominikbaki.treuebiss.feature_tenant.data.remote.dto.OfferDto
import com.dominikbaki.treuebiss.feature_tenant.data.remote.dto.TenantDto

fun TenantDto.toTenantEntity() = TenantEntity(
    id = id,
    name = name,
    loyaltyPointsTitle = loyaltyPointsTitle,
    vouchersTitle = vouchersTitle,
    dailySpecialTitle = dailySpecialTitle,
    primaryColor = primaryColor,
    logoUrl = logoUrl,
    stampsPerCard = stampsPerCard,
    voucherValidityDays = voucherValidityDays,
    requiresRedeemCode = requiresRedeemCode
)

fun TenantEntity.toTenant() = Tenant(
    id = id,
    name = name,
    loyaltyPointsTitle = loyaltyPointsTitle,
    vouchersTitle = vouchersTitle,
    dailySpecialTitle = dailySpecialTitle,
    primaryColor = primaryColor,
    logoUrl = logoUrl,
    stampsPerCard = stampsPerCard,
    voucherValidityDays = voucherValidityDays,
    requiresRedeemCode = requiresRedeemCode
)

fun OfferDto.toOfferEntity() = OfferEntity(
    id = id,
    tenantId = tenantId,
    title = title,
    description = description
)

fun OfferEntity.toOffer() = Offer(
    id = id,
    tenantId = tenantId,
    title = title,
    description = description
)
