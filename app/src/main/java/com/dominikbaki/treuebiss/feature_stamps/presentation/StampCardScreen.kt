package com.dominikbaki.treuebiss.feature_stamps.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.core.theme.TreueBissTheme
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StampCardScreen(
    onNavigateUp: () -> Unit, // Nur die "Zurück"-Aktion
    viewModel: StampCardViewModel = hiltViewModel()
) {
    // Beobachtet den StateFlow aus dem ViewModel.
    // Bei jeder Änderung wird der Screen neu gezeichnet (recomposed).
    val stamps by viewModel.stamps.collectAsState()
    val stampCount = stamps.size
    val snackBarHostState = remember { SnackbarHostState() } // Neu für Feedback

    // NEU: Effekt, der auf das Event zum Erstellen eines Gutscheins lauscht
    LaunchedEffect(key1 = true) {
        viewModel.voucherCreatedEvent.collectLatest {
            snackBarHostState.showSnackbar(
                message = "Glückwunsch! Ein neuer Gutschein wurde erstellt.",
                duration = SnackbarDuration.Short
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Deine Stempelkarte") },
                navigationIcon =  {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"

                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
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

            Button(
                onClick = { viewModel.onAddStampClicked() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = stampCount < 10 // Button ist deaktiviert, wenn die Karte voll ist
            ) {
                Text("Stempel hinzufügen")
            }
        }
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
@Preview(showBackground = true)
@Composable
private fun StampCardScreenPreview() {
    TreueBissTheme {
        StampCardScreen(onNavigateUp = {})
    }
}