package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.feature_home.presentation.components.HomeContent

/** Fortschritt der Stempelkarte, wie ihn der HomeScreen anzeigt. */
data class StampCardState(
    val currentStamps: Int,
    val totalStamps: Int
)

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
