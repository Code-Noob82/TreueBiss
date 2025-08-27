package com.dominikbaki.treuebiss.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Onboarding,
        modifier = modifier
    ) {
        composable<Screen.Onboarding> {
            //OnboardingScreen(navController)
        }
        composable<Screen.Home> {
            //HomeScreen(navController)
        }
        composable<Screen.StampCard> {
            //StampCardScreen(navController)
        }
        composable<Screen.Voucher> {
            //VoucherScreen(navController)
        }
        composable<Screen.Weather> {
            //WeatherScreen(navController)
        }
        composable<Screen.Settings> {
            //SettingsScreen(navController)
        }
    }
}