package com.dominikbaki.treuebiss.feature_weather.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import kotlin.math.roundToInt

@Composable
internal fun WeatherDetails(data: WeatherData, city: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(city, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            WeatherIcon(weatherType = data.weatherType, modifier = Modifier.size(100.dp))

            Text(
                text = "${data.temperature.roundToInt()}°C",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = data.weatherType.description,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                WeatherInfoItem("Gefühlt", "${data.pressure.roundToInt()} hPa")
                WeatherInfoItem("Wind", "${data.windSpeed} km/h")
                WeatherInfoItem("Feuchtigkeit", "${data.humidity}%")
            }
        }
    }
}