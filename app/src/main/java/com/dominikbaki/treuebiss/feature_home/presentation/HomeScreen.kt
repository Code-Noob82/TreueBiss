package com.dominikbaki.treuebiss.feature_home.presentation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.feature_home.presentation.components.DailySpecialCard
import com.dominikbaki.treuebiss.feature_home.presentation.components.QuickActionsRow
import com.dominikbaki.treuebiss.feature_home.presentation.components.StampCardProgress
import kotlinx.coroutines.launch

// --- ZENTRALE BRANDING-KONFIGURATION ---
// Diese Klasse bündelt alle anpassbaren UI-Elemente.
// Sie würde aus einer zentralen Konfigurationsdatei oder API für den jeweiligen Mandanten geladen.
data class BrandingConfig(
    val businessName: String,
    val dailySpecialTitle: String,
    val loyaltyPointsTitle: String,
    val vouchersTitle: String,
    val weatherTitle: String,
    // Hier kommen später auch Farben, Logo-Resource-IDs etc. rein.
)

val LocalBrandingConfig = staticCompositionLocalOf<BrandingConfig> {
    error("Keine BrandingConfig vorhanden. Bitte via CompositionLocalProvider bereitstellen.")
}

// Annahme: Diese Daten kommen von einem ViewModel
data class StampCardState(
    val currentStamps: Int,
    val totalStamps: Int = 10
)

data class DailySpecial(
    val title: String,
    val description: String,
    val imageUrl: Int?
) // imageUrl als Resource ID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToStampCard: () -> Unit,
    onNavigateToVoucher: () -> Unit,
    onNavigateToWeather: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    // --- BRANDING-KONFIGURATION (vorerst noch hartcodiert) ---
    val branding = BrandingConfig(
        businessName = "Bäckerei Mustermann",
        dailySpecialTitle = "Schmankerl des Tages",
        loyaltyPointsTitle = "Deine Treuepunkte",
        vouchersTitle = "Meine Gutscheine",
        weatherTitle = "Wetter-Check"
    )

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- BERECHTIGUNGS-LOGIK ---
    // 1. Launcher, der das Ergebnis der Berechtigungsanfrage verarbeitet.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isGranted =
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) ||
                    permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)

        if (isGranted) {
            // Berechtigung erteilt -> Wetterdaten abrufen
            viewModel.fetchWeather()
        } else {
            // Berechtigung verweigert -> Nutzerfeedback geben
            scope.launch {
                snackbarHostState.showSnackbar("Ohne Standorterlaubnis kann das Wetter nicht angezeigt werden.")
            }
        }
    }

    // 2. Effekt, der beim ersten Start des Screens die Berechtigung prüft oder anfragt.
    LaunchedEffect(key1 = true) {
        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCoarsePermission) {
            viewModel.fetchWeather()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }
    // Stellt die Branding-Daten für alle untergeordneten UI-Elemente bereit.
    CompositionLocalProvider(LocalBrandingConfig provides branding) {
        val currentBranding = LocalBrandingConfig.current

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = currentBranding.businessName) }, // Aus BRANDING
                    actions = {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                        }
                    }
                )
            }
        ) { paddingValues ->
            // --- UI-ZUSTANDS-LOGIK ---
            // Zeigt einen Lade-Spinner nur beim allerersten Laden an.
            if (uiState.isWeatherLoading && uiState.weatherData == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Das "Angebot des Tages"
                    uiState.dailySpecial?.let { special ->
                        item {
                            DailySpecialCard(
                                special = special
                            )
                        }
                    }
                    // 2. Der Fortschritt der Stempelkarte
                    item {
                        StampCardProgress(
                            state = StampCardState(currentStamps = uiState.stampCount),
                            onClick = onNavigateToStampCard
                        )
                    }
                    // 3. Schnelle Aktionen für Gutscheine und Wetter
                    item {
                        // voucherCount aus uiState befüllen
                        QuickActionsRow(
                            voucherCount = uiState.voucherCount,
                            weatherData = uiState.weatherData,
                            weatherError = uiState.weatherError,
                            isWeatherLoading = uiState.isWeatherLoading,
                            onVoucherClick = onNavigateToVoucher,
                            onWeatherClick = onNavigateToWeather,
                            onRetryWeatherClick = viewModel::onRetryWeatherFetch
                        )
                    }
                }
            }
        }
    }
}