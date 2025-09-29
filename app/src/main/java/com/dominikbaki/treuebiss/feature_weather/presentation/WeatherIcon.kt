package com.dominikbaki.treuebiss.feature_weather.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherType

@Composable
// Hilfsfunktion, um das passende Wetter-Icon zu bestimmen
internal fun getWeatherIcon(weatherType: WeatherType): ImageVector {
    return when (weatherType) {
        is WeatherType.ClearSky, is WeatherType.MainlyClear -> Icons.Default.WbSunny
        is WeatherType.PartlyCloudy, is WeatherType.Overcast, is WeatherType.Fog -> Icons.Default.Cloud
        is WeatherType.Rain -> Icons.Default.WaterDrop
        is WeatherType.Snow -> Icons.Default.AcUnit
        is WeatherType.Thunderstorm -> Icons.Default.Bolt
        is WeatherType.Unknown -> Icons.Default.FilterDrama
    }
}