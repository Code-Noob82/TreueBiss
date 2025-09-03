package com.dominikbaki.treuebiss.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dominikbaki.treuebiss.feature_home.presentation.HomeScreen
import com.dominikbaki.treuebiss.feature_onboarding.presentation.OnboardingScreen
import com.dominikbaki.treuebiss.feature_settings.presentation.SettingsScreen
import com.dominikbaki.treuebiss.feature_stamps.presentation.StampCardScreen
import com.dominikbaki.treuebiss.feature_vouchers.presentation.VoucherScreen
import com.dominikbaki.treuebiss.feature_weather.presentation.WeatherScreen

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Onboarding,
        modifier = modifier
    ) {
        composable<Screen.Onboarding> {
            OnboardingScreen(
                onFinish = {
                    // Die komplette Navigationslogik bleibt hier zentralisiert.
                    // Der Onboarding-Screen wird aus dem Backstack entfernt.
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        composable<Screen.Home> {
            HomeScreen(
                // Der HomeScreen weiß nichts vom NavController, er löst nur Events aus.
                onNavigateToStampCard = { navController.navigate(Screen.StampCard)},
                onNavigateToVoucher = { navController.navigate(Screen.Voucher)},
                onNavigateToWeather = { navController.navigate(Screen.Weather)},
                onNavigateToSettings = { navController.navigate(Screen.Settings)}
            )
        }
        composable<Screen.StampCard> {
            StampCardScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<Screen.Voucher> {
            VoucherScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<Screen.Weather> {
            WeatherScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}