package com.dominikbaki.treuebiss.feature_home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Refresh
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import com.dominikbaki.treuebiss.feature_weather.presentation.getWeatherIcon
import com.dominikbaki.treuebiss.feature_weather.presentation.weatherTypeLabel
import kotlin.math.roundToInt

@Composable
fun QuickActionsRow(
    voucherCount: Int,
    weatherData: WeatherData?,
    @StringRes weatherErrorRes: Int?,
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
            subtitle = stringResource(R.string.home_vouchers_available, voucherCount),
            icon = Icons.Filled.Redeem,
            onClick = onVoucherClick
        )

        // --- Wetter-Karte mit Zustandslogik ---
        val weatherSubtitle = when {
            isWeatherLoading -> stringResource(R.string.weather_tile_loading)
            weatherErrorRes != null -> stringResource(R.string.weather_tile_error)
            weatherData != null -> stringResource(
                R.string.weather_value_temperature,
                weatherData.temperature.roundToInt()
            ) + ", " + weatherTypeLabel(weatherData.weatherType)
            else -> stringResource(R.string.weather_tile_empty)
        }
        val weatherIcon = when {
            weatherErrorRes != null -> Icons.Default.Refresh
            weatherData != null -> getWeatherIcon(weatherData.weatherType)
            else -> Icons.Default.FilterDrama
        }

        ActionCard(
            modifier = Modifier.weight(1f),
            title = branding.weatherTitle,
            subtitle = weatherSubtitle,
            icon = weatherIcon,
            isLoading = isWeatherLoading,
            onClick = if (weatherErrorRes != null) onRetryWeatherClick else onWeatherClick
        )
    }
}