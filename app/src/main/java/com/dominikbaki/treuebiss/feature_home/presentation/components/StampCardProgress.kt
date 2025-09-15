package com.dominikbaki.treuebiss.feature_home.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominikbaki.treuebiss.feature_home.presentation.LocalBrandingConfig
import com.dominikbaki.treuebiss.feature_home.presentation.StampCardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StampCardProgress(state: StampCardState, onClick: () -> Unit) {
    val branding = LocalBrandingConfig.current // Holt sich das Branding
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(branding.loyaltyPointsTitle, style = MaterialTheme.typography.titleMedium) // AUS BRANDING
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.currentStamps} von ${state.totalStamps} Stempeln gesammelt",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.currentStamps.toFloat() / state.totalStamps.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}