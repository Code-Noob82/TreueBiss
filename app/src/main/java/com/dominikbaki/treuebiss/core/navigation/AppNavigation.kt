package com.dominikbaki.treuebiss.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dominikbaki.treuebiss.feature_home.presentation.HomeScreen
import com.dominikbaki.treuebiss.feature_stamps.presentation.StampCardScreen
import com.dominikbaki.treuebiss.feature_vouchers.presentation.VoucherScreen

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home,
        modifier = modifier
    ) {
        composable<Screen.Onboarding> {
            //OnboardingScreen(navController)
        }
        composable<Screen.Home> {
            HomeScreen(navController)
        }
        composable<Screen.StampCard> {
            StampCardScreen(navController)
        }
        composable<Screen.Voucher> {
            VoucherScreen(navController)
        }
        composable<Screen.Weather> {
            //WeatherScreen(navController)
        }
        composable<Screen.Settings> {
            //SettingsScreen(navController)
        }
    }
}