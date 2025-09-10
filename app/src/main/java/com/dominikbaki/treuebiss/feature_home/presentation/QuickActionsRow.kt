package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.FilterDrama

@Composable
fun QuickActionsRow(
    voucherCount: Int,
    onVoucherClick: () -> Unit,
    onWeatherClick: () -> Unit
) {
    val branding = LocalBrandingConfig.current // Holt sich das Branding
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ActionCard(
            modifier = Modifier.weight(1f),
            title = branding.vouchersTitle, // AUS BRANDING
            subtitle = "$voucherCount verfügbar",
            icon = Icons.Filled.Redeem,
            onClick = onVoucherClick
        )
        ActionCard(
            modifier = Modifier.weight(1f),
            title = branding.weatherTitle, // AUS BRANDING
            subtitle = "Grillwetter?",
            icon = Icons.Filled.FilterDrama,
            onClick = onWeatherClick
        )
    }
}