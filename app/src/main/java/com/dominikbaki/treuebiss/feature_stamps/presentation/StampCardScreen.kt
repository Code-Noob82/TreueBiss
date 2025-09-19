package com.dominikbaki.treuebiss.feature_stamps.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.feature_stamps.presentation.composables.DemoAddStampButton
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun StampCardScreen(
    cardId: String,
    snackBarHostState: SnackbarHostState,
    viewModel: StampCardViewModel = hiltViewModel()
) {
    // Die Logik zum Beobachten des States bleibt unverändert.
    val stamps by viewModel.stamps.collectAsState()
    val stampCount = stamps.size

    LaunchedEffect(key1 = true) {
        viewModel.voucherCreatedEvent.collectLatest {
            snackBarHostState.showSnackbar(
                message = "Glückwunsch! Ein neuer Gutschein wurde erstellt.",
                duration = SnackbarDuration.Short
            )
            // TODO: navController.navigate(Screen.Voucher(voucherId))
        }
    }

    // Der Screen besteht jetzt nur noch aus der reinen Inhalts-Spalte.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Padding für den Inhalt
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Deine Stempelkarte", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (stampCount < 10) {
                    "Sammle 10 Stempel für eine tolle Prämie!"
                } else {
                    "Glückwunsch! Du hast einen Gutschein erhalten."
                },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Gitter für die 10 Stempel-Kreise
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                items(10) { index ->
                    val isStamped = index < stampCount
                    StampCircle(isStamped = isStamped)
                }
            }
        }

        DemoAddStampButton(
            enabled = true,
            onClick = { viewModel.onAddStampClicked() }
        )
    }
}

@Composable
private fun StampCircle(isStamped: Boolean) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(
                if (isStamped) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
            ),
        contentAlignment = Alignment.Center
    ) {
        // Hier könnte man später ein Icon oder eine Nummer hinzufügen
    }
}

//@Preview(showBackground = true)
//@Composable
//private fun StampCardScreenPreview() {
//    TreueBissTheme {
//        // Die Vorschau wurde angepasst und benötigt 'onNavigateUp' nicht mehr.
//        StampCardScreen(cardId = 1,
//            snackarHostState = SnackbarHostState()
//        )
//    }
//}