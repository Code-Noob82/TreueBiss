package com.dominikbaki.treuebiss.core.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.dominikbaki.treuebiss.core.navigation.Screen

/**
 * Definiert die Items der Bottom Navigation.
 */
sealed class BottomNavItem(
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(
        title = "Home",
        icon = Icons.Rounded.Home
    )

    data class StampCard(val cardId: String) : BottomNavItem(
        title = "Bonuskarte",
        icon = Icons.Rounded.ShoppingCart
    )

    data class Vouchers(val voucherId: String) : BottomNavItem(
        title = "Gutscheine",
        icon = Icons.Rounded.Favorite
    )
}

/**
 * Hilfsfunktion, um Typgleichheit zu prüfen (egal welche ID gesetzt ist).
 */
internal fun isSameType(currentRoute: Screen?, item: BottomNavItem): Boolean = when {
    currentRoute is Screen.Home && item is BottomNavItem.Home -> true
    currentRoute is Screen.StampCard && item is BottomNavItem.StampCard -> true
    currentRoute is Screen.Voucher && item is BottomNavItem.Vouchers -> true
    else -> false
}


