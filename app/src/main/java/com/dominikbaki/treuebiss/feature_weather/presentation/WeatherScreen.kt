package com.dominikbaki.treuebiss.feature_weather.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherData
import com.dominikbaki.treuebiss.feature_weather.domain.model.WeatherType

@Composable
fun WeatherScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    viewModel: WeatherViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Berechtigungs-Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) ||
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
        ) {
            viewModel.fetchWeatherDataForCurrentLocation()
        }
    }

    // Initialer Berechtigungs-Check
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.fetchWeatherDataForCurrentLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    // --- Inhalt ---
    when (val state = uiState) {
        is WeatherUiState.Loading -> LoadingContent()
        is WeatherUiState.Success -> WeatherContent(
            data = state.weatherData,
            city = state.cityName ?: "Wetter"
        )
        is WeatherUiState.Error -> ErrorContent(
            message = state.message,
            onRetry = { viewModel.fetchWeatherDataForCurrentLocation() }
        )
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(8.dp))
        Text("Wetterdaten werden geladen…")
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "❌ Fehler: $message",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text("Erneut versuchen")
        }
    }
}

@Composable
private fun WeatherContent(data: WeatherData, city: String) {
    // Hintergrundfarbe + Icon je nach Zustand
    val (bgColor, icon, label) = when (val type = data.weatherType) {
        is WeatherType.ClearSky -> Triple(Color(0xFF90CAF9), Icons.Default.WbSunny, type.description)
        is WeatherType.MainlyClear -> Triple(Color(0xFFBBDEFB), Icons.Default.WbSunny, type.description)
        is WeatherType.PartlyCloudy -> Triple(Color(0xFFB0BEC5), Icons.Default.Cloud, type.description)
        is WeatherType.Overcast -> Triple(Color(0xFF90A4AE), Icons.Default.Cloud, type.description)
        is WeatherType.Fog -> Triple(Color(0xFFE0E0E0), Icons.Default.Cloud, type.description)
        is WeatherType.Rain -> Triple(Color(0xFF80CBC4), Icons.Default.WaterDrop, "${type.description} (${type.intensity})")
        is WeatherType.Snow -> Triple(Color(0xFFB3E5FC), Icons.Default.AcUnit, "${type.description} (${type.intensity})")
        is WeatherType.Thunderstorm -> Triple(Color(0xFF9575CD), Icons.Default.Bolt, type.description)
        is WeatherType.Unknown -> Triple(Color(0xFFCFD8DC), Icons.Default.Help, type.description)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("${data.temperature}°C", style = MaterialTheme.typography.headlineLarge)
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text("Wind: ${data.windSpeed} km/h")
            Text("Luftfeuchtigkeit: ${data.humidity}%")
            Text("Luftdruck: ${data.pressure} hPa")
        }
    }
}