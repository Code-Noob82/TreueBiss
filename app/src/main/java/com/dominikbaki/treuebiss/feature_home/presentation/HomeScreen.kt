package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.feature_home.presentation.components.HomeContent

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
@Composable
internal fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToStampCard: () -> Unit,
    onNavigateToVoucher: () -> Unit,
    paddingValues: PaddingValues
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeContent(
        state = uiState,
        paddingValues = paddingValues,
        onNavigateToStampCard = onNavigateToStampCard,
        onNavigateToVoucher = onNavigateToVoucher
    )
}
