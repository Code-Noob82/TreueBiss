package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.feature_home.presentation.components.HandleLocationPermission
import com.dominikbaki.treuebiss.feature_home.presentation.components.HomeContent
import com.dominikbaki.treuebiss.feature_home.presentation.components.LoadingIndicator
import kotlinx.coroutines.launch

// ------------------
// UI-Models
// ------------------
data class StampCardState(
    val currentStamps: Int,
    val totalStamps: Int = 10
)

data class DailySpecial(
    val title: String,
    val description: String,
    val imageUrl: Int? // Ressourcen-ID oder null, falls kein Bild angezeigt werden soll
)

// ------------------
// Composable Entry Point
// ------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToStampCard: (cardId: String) -> Unit,
    onNavigateToVoucher: (voucherId: String) -> Unit,
    onNavigateToWeather: () -> Unit,
    onNavigateToSettings: () -> Unit,
    paddingValues: PaddingValues
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Berechtigungs-Handling gekapselt ---
    HandleLocationPermission(
        onGranted = { viewModel.fetchWeather() },
        onDenied = {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Ohne Standorterlaubnis kann das Wetter nicht angezeigt werden."
                )
            }
        }
    )
    when {
        uiState.isWeatherLoading && uiState.weatherData == null -> {
            LoadingIndicator(paddingValues = paddingValues)
        }

        else -> {
            HomeContent(
                state = uiState,
                paddingValues = paddingValues,
                onNavigateToStampCard = onNavigateToStampCard,
                onNavigateToVoucher = onNavigateToVoucher,
                onNavigateToWeather = onNavigateToWeather,
                onRetryWeather = viewModel::onRetryWeatherFetch
            )
        }
    }
}
