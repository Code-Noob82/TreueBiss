package com.dominikbaki.treuebiss.feature_home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Refresh
import com.dominikbaki.treuebiss.feature_home.presentation.LocalBrandingConfig
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import kotlin.math.roundToInt

@Composable
fun QuickActionsRow(
    voucherCount: Int,
    weatherData: WeatherData?,
    weatherError: String?,
    isWeatherLoading: Boolean,
    onVoucherClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onRetryWeatherClick: () -> Unit
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
        val weatherSubtitle = when {
            isWeatherLoading -> "Wird geladen..."
            weatherError != null -> "Fehler"
            weatherData != null -> "${weatherData.temperature.roundToInt()}°C, ${weatherData.weatherType.description}"
            else -> "Daten laden"
        }
        val weatherIcon = if (weatherError != null) Icons.Default.Refresh else Icons.Default.FilterDrama

        ActionCard(
            modifier = Modifier.weight(1f),
            title = branding.weatherTitle, // AUS BRANDING
            subtitle = weatherSubtitle,
            icon = Icons.Filled.FilterDrama,
            onClick = if (weatherError != null) onRetryWeatherClick else onWeatherClick
        )
    }
}