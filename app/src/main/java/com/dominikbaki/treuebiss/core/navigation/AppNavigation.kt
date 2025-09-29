package com.dominikbaki.treuebiss.core.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.dominikbaki.treuebiss.feature_home.presentation.HomeScreen
import com.dominikbaki.treuebiss.feature_onboarding.presentation.OnboardingScreen
import com.dominikbaki.treuebiss.feature_settings.presentation.SettingsScreen
import com.dominikbaki.treuebiss.feature_stamps.presentation.StampCardScreen
import com.dominikbaki.treuebiss.feature_vouchers.presentation.VoucherScreen
import com.dominikbaki.treuebiss.feature_weather.presentation.WeatherScreen

/**
 * Zentrale Navigationsfunktion.
 * - Nutzt `Screen` als typsichere Route.
 * - Handhabt dynamische Argumente (z. B. cardId, voucherId).
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    snackBarHostState: SnackbarHostState,
    startDestination: Screen, // Start-Ziel ist jetzt ein Screen-Objekt
    onOnboardingFinished: () -> Unit = {}, // Event vom OnboardingScreen zum MainScreen
    paddingValues: PaddingValues
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // Animationen für alle Screens im NavHost definiert
        enterTransition = { fadeIn(animationSpec = tween(300)) },
        exitTransition = { fadeOut(animationSpec = tween(300)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) }
    ) {
        // ---------- Onboarding ----------
        composable<Screen.Onboarding> {
            OnboardingScreen(
                onFinish = {
                    onOnboardingFinished() // ViewModel aufrufen, um Onboarding zu beenden
                    navController.navigate(Screen.Home) {
                        popUpTo(Screen.Onboarding) { inclusive = true }
                    }
                }
            )
        }
        // ---------- Home ----------
        composable<Screen.Home> {
            HomeScreen(
                onNavigateToStampCard = { cardId ->
                    navController.navigate(Screen.StampCard(cardId))
                },
                onNavigateToVoucher = { voucherId ->
                    navController.navigate(Screen.Voucher(voucherId))
                },
                onNavigateToWeather = { navController.navigate(Screen.Weather) },
                onNavigateToSettings = { navController.navigate(Screen.Settings) },
                paddingValues = paddingValues
            )
        }
        // ---------- StampCard ----------
        composable<Screen.StampCard> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.StampCard>()
            StampCardScreen(
                cardId = args.cardId,
                snackBarHostState = snackBarHostState,
                paddingValues = paddingValues
            )
        }
        // ---------- Voucher ----------
        composable<Screen.Voucher> { backStackEntry ->
            val args = backStackEntry.toRoute<Screen.Voucher>()
            VoucherScreen(
                voucherId = args.voucherId,
                paddingValues = paddingValues
            )
        }
        // ---------- Weather ----------
        composable<Screen.Weather> {
            WeatherScreen(onNavigateUp = { navController.navigateUp() })
        }
        // ---------- Settings ----------
        composable<Screen.Settings> {
            SettingsScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}