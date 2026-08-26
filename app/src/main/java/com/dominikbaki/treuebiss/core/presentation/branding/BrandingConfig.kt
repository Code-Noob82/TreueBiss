package com.dominikbaki.treuebiss.core.presentation.branding

import androidx.compose.runtime.staticCompositionLocalOf
import com.dominikbaki.treuebiss.core.domain.models.Tenant

/**
 * Die betriebsspezifischen Bezeichnungen, wie die UI sie braucht.
 * Gefüllt aus dem aktiven [Tenant] - siehe [toBrandingConfig].
 */
data class BrandingConfig(
    val businessName: String,
    val dailySpecialTitle: String,
    val loyaltyPointsTitle: String,
    val vouchersTitle: String
)

fun Tenant.toBrandingConfig() = BrandingConfig(
    businessName = name,
    dailySpecialTitle = dailySpecialTitle,
    loyaltyPointsTitle = loyaltyPointsTitle,
    vouchersTitle = vouchersTitle
)

val LocalBrandingConfig = staticCompositionLocalOf<BrandingConfig> {
    error("Keine BrandingConfig vorhanden. Bitte via CompositionLocalProvider bereitstellen.")
}
