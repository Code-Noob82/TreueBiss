package com.dominikbaki.treuebiss.feature_weather.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.feature_weather.presentation.composables.ErrorContent
import com.dominikbaki.treuebiss.feature_weather.presentation.composables.LoadingContent
import com.dominikbaki.treuebiss.feature_weather.presentation.composables.WeatherContent

// --- Hauptkomponente für die Wetteranzeige ---
@Composable
fun WeatherScreen(
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
            city = stringResource(R.string.weather_screen_title)
        )

        is WeatherUiState.Error -> ErrorContent(
            message = stringResource(state.messageRes),
            onRetry = { viewModel.fetchWeatherDataForCurrentLocation() }
        )
    }
}





