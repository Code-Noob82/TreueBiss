package com.dominikbaki.treuebiss.core.presentation.branding

import androidx.compose.runtime.staticCompositionLocalOf

data class BrandingConfig(
    val businessName: String,
    val dailySpecialTitle: String,
    val loyaltyPointsTitle: String,
    val vouchersTitle: String
)

val LocalBrandingConfig = staticCompositionLocalOf<BrandingConfig> {
    error("Keine BrandingConfig vorhanden. Bitte via CompositionLocalProvider bereitstellen.")
}
