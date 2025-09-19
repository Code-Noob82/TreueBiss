package com.dominikbaki.treuebiss.core.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.dominikbaki.treuebiss.core.theme.TreueBissTheme
import com.dominikbaki.treuebiss.feature_home.presentation.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val homeUiState by homeViewModel.uiState.collectAsState()
    
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = remember(navBackStackEntry) {
        try {
            navBackStackEntry?.toRoute<Screen>()
        } catch (e: Exception) {
            null
        }
    }

    val snackBarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        topBar = {
            if (currentRoute !is Screen.Onboarding) {
                TopAppBar(
                    title = {
                        Text(
                            when (currentRoute) {
                                is Screen.Home -> "Home"
                                is Screen.StampCard -> "Bonuskarte"
                                is Screen.Voucher -> "Gutschein"
                                is Screen.Weather -> "Wetter"
                                is Screen.Settings -> "Einstellungen"
                                else -> ""
                            }
                        )
                    },
                    navigationIcon = {
                        if (navController.previousBackStackEntry != null && currentRoute !is Screen.Home
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
            if (currentRoute !is Screen.Onboarding) {
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
                            label = { Text(item.title) },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            selected = when (item) {
                                is BottomNavItem.Home -> currentRoute is Screen.Home
                                is BottomNavItem.StampCard -> currentRoute is Screen.StampCard
                                is BottomNavItem.Vouchers -> currentRoute is Screen.Voucher
                            },
                            onClick = {
                                when (item) {
                                    is BottomNavItem.Home -> {
                                        navController.navigate(Screen.Home) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }

                                    is BottomNavItem.StampCard -> {
                                        navController.navigate(Screen.StampCard(item.cardId))
                                    }

                                    is BottomNavItem.Vouchers -> {
                                        navController.navigate(Screen.Voucher(item.voucherId))
                                    }
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
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            is MainUiState.Success -> AppNavigation(
                modifier = Modifier.padding(innerPadding),
                navController = navController,
                startDestination = if (state.hasCompletedOnboarding) Screen.Home else Screen.Onboarding,
                onOnboardingFinished = mainViewModel::onOnboardingFinished,
                snackBarHostState = snackBarHostState
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