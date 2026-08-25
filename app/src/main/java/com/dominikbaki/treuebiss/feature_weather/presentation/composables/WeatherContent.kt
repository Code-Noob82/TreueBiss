package com.dominikbaki.treuebiss.feature_weather.presentation.composables

import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.feature_weather.presentation.weatherTypeLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WindPower
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import com.dominikbaki.treuebiss.feature_weather.presentation.WeatherDetails

// --- Datenklasse für die visuellen Elemente ---
internal data class WeatherVisuals(
    val gradientColors: List<Color>,
    val icon: ImageVector
)

@Composable
internal fun WeatherContent(data: WeatherData, city: String) {
    val visuals = getWeatherVisuals(data.weatherType)
    val label = weatherTypeLabel(data.weatherType)

    val backgroundBrush = Brush.verticalGradient(colors = visuals.gradientColors)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceAround
    ) {
        // --- Header ---
        Text(
            text = city,
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White // Feste Farbe für Lesbarkeit auf allen Verläufen
        )

        // --- Aktuelles Wetter ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = visuals.icon,
                contentDescription = label,
                modifier = Modifier.size(120.dp),
                tint = Color.White
            )
            Text(
                text = stringResource(R.string.weather_value_temperature, data.temperature.toInt()),
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }

        // --- Wetterdetails in einer Karte ---
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp, horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeatherDetails(
                    icon = Icons.Default.WaterDrop,
                    label = stringResource(R.string.weather_detail_humidity),
                    value = stringResource(R.string.weather_value_humidity, data.humidity)
                )
                WeatherDetails(
                    icon = Icons.Default.WindPower,
                    label = stringResource(R.string.weather_detail_wind),
                    value = stringResource(R.string.weather_value_wind, data.windSpeed.toString())
                )
                WeatherDetails(
                    icon = Icons.Default.Compress,
                    label = stringResource(R.string.weather_detail_pressure),
                    value = stringResource(R.string.weather_value_pressure, data.pressure.toString())
                )
            }
        }
    }
}