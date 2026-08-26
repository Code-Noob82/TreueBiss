package com.dominikbaki.treuebiss.feature_stamps.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.feature_stamps.presentation.composables.DemoAddStampButton
import com.dominikbaki.treuebiss.feature_stamps.presentation.composables.StampCircle
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun StampCardScreen(
    snackBarHostState: SnackbarHostState,
    paddingValues: PaddingValues,
    viewModel: StampCardViewModel = hiltViewModel()
) {
    // Die Logik zum Beobachten des States bleibt unverändert.
    val uiState by viewModel.uiState.collectAsState()
    val voucherCreatedMessage = stringResource(R.string.stamp_card_voucher_created)

    LaunchedEffect(key1 = true) {
        viewModel.voucherCreatedEvent.collectLatest {
            snackBarHostState.showSnackbar(
                message = voucherCreatedMessage,
                duration = SnackbarDuration.Short
            )
        }
    }

    // NEU: Verbessertes Layout mit besserer Struktur und optischen Elementen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Header Sektion ---
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Style, // Thematisch passendes Icon
                contentDescription = stringResource(R.string.stamp_card_icon),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.stamp_card_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (!uiState.isComplete) {
                    stringResource(R.string.stamp_card_hint_collecting, uiState.stampsPerCard)
                } else {
                    stringResource(R.string.stamp_card_hint_complete)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        // --- Stempelgitter (zentriert im verfügbaren Platz) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // Sorgt dafür, dass das Gitter den Platz füllt
            contentAlignment = Alignment.Center
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                verticalArrangement = Arrangement.spacedBy(20.dp), // Mehr Abstand
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                items(uiState.stampsPerCard) { index ->
                    val isStamped = index < uiState.stampCount
                    StampCircle(isStamped = isStamped)
                }
            }
        }

        // --- Button am unteren Rand ---
        // KORREKTUR: Der Button wird in eine Box gewrappt, um den Modifier anwenden zu können.
        Box(modifier = Modifier.padding(bottom = 16.dp)) {
            DemoAddStampButton(
                enabled = !uiState.isAddingStamp && !uiState.isComplete,
                onClick = { viewModel.onAddStampClicked() }
            )
        }
    }
}


