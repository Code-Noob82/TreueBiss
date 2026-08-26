package com.dominikbaki.treuebiss.feature_home.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig
import com.dominikbaki.treuebiss.feature_home.presentation.HomeUiState
import com.dominikbaki.treuebiss.feature_home.presentation.StampCardState

@Composable
fun HomeContent(
    state: HomeUiState,
    paddingValues: PaddingValues,
    onNavigateToStampCard: () -> Unit,
    onNavigateToVoucher: () -> Unit,
    modifier: Modifier = Modifier
) {
    val branding = LocalBrandingConfig.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        state.offer?.let { offer ->
            item { DailySpecialCard(offer = offer) }
        }
        item {
            StampCardProgress(
                state = StampCardState(
                    currentStamps = state.stampCount,
                    totalStamps = state.stampsPerCard
                ),
                onClick = onNavigateToStampCard
            )
        }
        item {
            ActionCard(
                modifier = Modifier.fillMaxWidth(),
                title = branding.vouchersTitle,
                subtitle = stringResource(R.string.home_vouchers_available, state.voucherCount),
                icon = Icons.Filled.Redeem,
                onClick = onNavigateToVoucher
            )
        }
    }
}
