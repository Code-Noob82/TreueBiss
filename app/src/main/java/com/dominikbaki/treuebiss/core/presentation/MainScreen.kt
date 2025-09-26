package com.dominikbaki.treuebiss.core.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dominikbaki.treuebiss.core.navigation.AppNavigation
import com.dominikbaki.treuebiss.core.navigation.Screen
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig
import com.dominikbaki.treuebiss.core.theme.TreueBissTheme
import com.dominikbaki.treuebiss.feature_home.presentation.HomeViewModel


// ------------------
// Mapping BottomNavItem -> Screen
// ------------------
private fun BottomNavItem.toScreen(): Screen = when (this) {
    is BottomNavItem.Home -> Screen.Home
    is BottomNavItem.StampCard -> Screen.StampCard(cardId)
    is BottomNavItem.Vouchers -> Screen.Voucher(voucherId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val homeUiState by homeViewModel.uiState.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }

    // EIN NavController (Single-Backstack)
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = remember(navBackStackEntry) {
        try {
            navBackStackEntry?.toRoute<Screen>()
        } catch (e: Exception) {
            null
        }
    }

    val branding = LocalBrandingConfig.current
    val showBars = uiState is MainUiState.Success && currentRoute !is Screen.Onboarding

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            if (showBars) {
                TopAppBar(
                    title = {
                        Text(
                            when (currentRoute) {
                                is Screen.Home -> branding.businessName
                                is Screen.StampCard -> branding.loyaltyPointsTitle
                                is Screen.Voucher -> branding.vouchersTitle
                                is Screen.Weather -> branding.weatherTitle
                                is Screen.Settings -> "Einstellungen"
                                else -> ""
                            }
                        )
                    },
                    navigationIcon = {
                        if (navController.previousBackStackEntry != null &&
                            currentRoute !is Screen.Home
                        ) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Zurück"
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentRoute is Screen.Home) {
                            IconButton(onClick = { navController.navigate(Screen.Settings) }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Einstellungen"
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (uiState is MainUiState.Success && showBars) {
                val items = listOf(
                    BottomNavItem.Home,
                    BottomNavItem.StampCard(
                        cardId = homeUiState.currentStampCardId ?: "default-card"
                    ),
                    BottomNavItem.Vouchers(
                        voucherId = homeUiState.currentVoucherId ?: "default-voucher"
                    )
                )

                NavigationBar {
                    items.forEach { item ->
                        NavigationBarItem(
                            label = { Text(item.title()) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            selected = BottomNavItem.isSameType(currentRoute, item),
                            onClick = {
                                navController.navigate(item.toScreen()) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        inclusive = false
                                    }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is MainUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is MainUiState.Success ->
                AppNavigation(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    snackBarHostState = snackBarHostState,
                    startDestination = if (state.hasCompletedOnboarding) Screen.Home else Screen.Onboarding,
                    onOnboardingFinished = mainViewModel::onOnboardingFinished,
                    paddingValues = innerPadding
                )

            is MainUiState.Error -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { mainViewModel.retryInitialAuth() }) {
                        Text("Wiederholen")
                    }
                }
            }
        }
    }
}


@Preview
@Composable
fun MainScreenPreview() {
    TreueBissTheme {
        MainScreen()
    }
}