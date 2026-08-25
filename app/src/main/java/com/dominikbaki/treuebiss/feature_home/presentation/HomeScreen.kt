package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.R
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
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val imageUrl: Int? // Ressourcen-ID oder null, falls kein Bild angezeigt werden soll
)

// ------------------
// Composable Entry Point
// ------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToStampCard: () -> Unit,
    onNavigateToVoucher: () -> Unit,
    onNavigateToWeather: () -> Unit,
    snackBarHostState: SnackbarHostState,
    paddingValues: PaddingValues
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val locationDeniedMessage = stringResource(R.string.home_location_denied)

    // --- Berechtigungs-Handling gekapselt ---
    HandleLocationPermission(
        onGranted = { viewModel.fetchWeather() },
        onDenied = {
            scope.launch {
                snackBarHostState.showSnackbar(
                    locationDeniedMessage
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
