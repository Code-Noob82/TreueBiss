package com.dominikbaki.treuebiss.feature_weather.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherType

@Composable
internal fun WeatherIcon(weatherType: WeatherType, modifier: Modifier = Modifier) {
    val icon = when (weatherType) {
        WeatherType.ClearSky, WeatherType.MainlyClear -> Icons.Default.WbSunny
        WeatherType.PartlyCloudy, WeatherType.Overcast, WeatherType.Fog -> Icons.Default.Cloud
        is WeatherType.Rain -> Icons.Default.Grain // Stellt Regentropfen dar
        is WeatherType.Snow -> Icons.Default.AcUnit
        is WeatherType.Thunderstorm -> Icons.Default.Thunderstorm
        WeatherType.Unknown -> Icons.Default.HelpOutline
    }
    Icon(
        imageVector = icon,
        contentDescription = weatherType.description,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.primary
    )
}