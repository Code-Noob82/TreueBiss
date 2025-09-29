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
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import com.dominikbaki.treuebiss.feature_weather.presentation.getWeatherIcon
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
    val branding = LocalBrandingConfig.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Gutschein-Karte ---
        ActionCard(
            modifier = Modifier.weight(1f),
            title = branding.vouchersTitle,
            subtitle = "$voucherCount verfügbar",
            icon = Icons.Filled.Redeem,
            onClick = onVoucherClick
        )

        // --- Wetter-Karte mit Zustandslogik ---
        val weatherSubtitle = when {
            isWeatherLoading -> "Wird geladen..."
            weatherError != null -> "Fehler beim Laden"
            weatherData != null -> "${weatherData.temperature.roundToInt()}°C, ${weatherData.weatherType.description}"
            else -> "Wetter anzeigen"
        }
        val weatherIcon = when {
            weatherError != null -> Icons.Default.Refresh
            weatherData != null -> getWeatherIcon(weatherData.weatherType)
            else -> Icons.Default.FilterDrama
        }

        ActionCard(
            modifier = Modifier.weight(1f),
            title = branding.weatherTitle,
            subtitle = weatherSubtitle,
            icon = weatherIcon,
            isLoading = isWeatherLoading,
            onClick = if (weatherError != null) onRetryWeatherClick else onWeatherClick
        )
    }
}