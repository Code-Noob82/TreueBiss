package com.dominikbaki.treuebiss.feature_weather.presentation.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherType

@Composable
internal fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = stringResource(R.string.weather_error_icon),
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.weather_error_prefix, message),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

// --- Hilfsfunktion zur Zuweisung von Farben und Icons ---
internal fun getWeatherVisuals(type: WeatherType): WeatherVisuals {
    return when (type) {
        is WeatherType.ClearSky, is WeatherType.MainlyClear -> WeatherVisuals(
            gradientColors = listOf(Color(0xFF4FC3F7), Color(0xFF81D4FA)),
            icon = Icons.Default.WbSunny
        )
        is WeatherType.PartlyCloudy -> WeatherVisuals(
            gradientColors = listOf(Color(0xFF78909C), Color(0xFF90A4AE)),
            icon = Icons.Default.Cloud
        )
        is WeatherType.Overcast, is WeatherType.Fog -> WeatherVisuals(
            gradientColors = listOf(Color(0xFF607D8B), Color(0xFF78909C)),
            icon = Icons.Default.Cloud
        )
        is WeatherType.Rain -> WeatherVisuals(
            gradientColors = listOf(Color(0xFF4DB6AC), Color(0xFF80CBC4)),
            icon = Icons.Default.WaterDrop
        )
        is WeatherType.Snow -> WeatherVisuals(
            gradientColors = listOf(Color(0xFF81D4FA), Color(0xFFB3E5FC)),
            icon = Icons.Default.AcUnit
        )
        is WeatherType.Thunderstorm -> WeatherVisuals(
            gradientColors = listOf(Color(0xFF7E57C2), Color(0xFF9575CD)),
            icon = Icons.Default.Bolt
        )
        is WeatherType.Unknown -> WeatherVisuals(
            gradientColors = listOf(Color(0xFFB0BEC5), Color(0xFFCFD8DC)),
            icon = Icons.AutoMirrored.Filled.Help
        )
    }
}
