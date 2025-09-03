package com.dominikbaki.treuebiss.core.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.dominikbaki.treuebiss.core.navigation.Screen

/**
 * Definiert die Navigations-Elemente für die Bottom Navigation Bar.
 * Kapselt die Route, den Titel und das Icon für jeden Tab.
 */
sealed class BottomNavItem(
    val route: Screen,
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(Screen.Home, "Home", Icons.Rounded.Home)
    object StampCard : BottomNavItem(Screen.StampCard, "Bonuskarte", Icons.Rounded.ShoppingCart)
    object Vouchers : BottomNavItem(Screen.Voucher, "Gutscheine", Icons.Rounded.Favorite)
}