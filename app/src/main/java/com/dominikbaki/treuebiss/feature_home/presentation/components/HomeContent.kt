package com.dominikbaki.treuebiss.feature_home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominikbaki.treuebiss.feature_home.presentation.HomeUiState
import com.dominikbaki.treuebiss.feature_home.presentation.StampCardState

@Composable
fun HomeContent(
    state: HomeUiState,
    paddingValues: PaddingValues,
    onNavigateToStampCard: () -> Unit,
    onNavigateToVoucher: () -> Unit,
    onNavigateToWeather: () -> Unit,
    onRetryWeather: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        state.dailySpecial?.let { special ->
            item { DailySpecialCard(special = special) }
        }
        item {
            StampCardProgress(
                state = StampCardState(currentStamps = state.stampCount),
                onClick = onNavigateToStampCard
            )
        }
        item {
            QuickActionsRow(
                voucherCount = state.voucherCount,
                weatherData = state.weatherData,
                weatherErrorRes = state.weatherErrorRes,
                isWeatherLoading = state.isWeatherLoading,
                onVoucherClick = onNavigateToVoucher,
                onWeatherClick = onNavigateToWeather,
                onRetryWeatherClick = onRetryWeather
            )
        }
    }
}