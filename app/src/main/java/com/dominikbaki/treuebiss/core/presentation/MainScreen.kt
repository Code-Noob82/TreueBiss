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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.dominikbaki.treuebiss.core.navigation.AppNavigation
import com.dominikbaki.treuebiss.core.navigation.Screen
import com.dominikbaki.treuebiss.core.navigation.mapRouteToScreen
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }

    // EIN NavController (Single-Backstack)
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = remember(navBackStackEntry) {
        try {
            navBackStackEntry?.toRoute<Screen>()
        } catch (e: IllegalArgumentException) {
            // Fallback, wenn die Route nicht typisiert aufgelöst werden kann.
            mapRouteToScreen(navBackStackEntry?.destination?.route)
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
                                is Screen.Settings -> stringResource(R.string.settings_title)
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
                                    contentDescription = stringResource(R.string.nav_back)
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentRoute is Screen.Home) {
                            IconButton(onClick = { navController.navigate(Screen.Settings) }) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.settings_title)
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showBars) {
                NavigationBar {
                    BottomNavItem.items.forEach { item ->
                        NavigationBarItem(
                            label = { Text(item.title()) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            selected = currentRoute == item.screen,
                            onClick = {
                                navController.navigate(item.screen) {
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
                    Text(stringResource(state.messageRes), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { mainViewModel.retryInitialAuth() }) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}
