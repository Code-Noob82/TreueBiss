package com.dominikbaki.treuebiss.core.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Definiert die Items der Bottom Navigation.
 */
sealed class BottomNavItem(
    val title: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(
        title ="Home",
        icon = Icons.Rounded.Home
    )
    data class StampCard(val cardId: String) : BottomNavItem(
        title = "Bonuskarte",
        icon = Icons.Rounded.ShoppingCart
    )
    data class Vouchers(val voucherId: String) : BottomNavItem(
        title ="Gutscheine",
        icon = Icons.Rounded.Favorite
    )
}