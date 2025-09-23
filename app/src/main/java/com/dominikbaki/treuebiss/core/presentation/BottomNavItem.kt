package com.dominikbaki.treuebiss.core.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.dominikbaki.treuebiss.core.navigation.Screen
import com.dominikbaki.treuebiss.core.presentation.branding.BrandingConfig

/**
 * Definiert die Items der Bottom Navigation.
 * Titel kommen jetzt dynamisch aus der BrandingConfig.
 */
sealed class BottomNavItem(
    open val title: String,
    val icon: ImageVector
) {
    data class Home(val branding: BrandingConfig) : BottomNavItem(
        title = branding.businessName,
        icon = Icons.Rounded.Home
    )

    data class StampCard(val branding: BrandingConfig, val cardId: String) : BottomNavItem(
        title = branding.loyaltyPointsTitle,
        icon = Icons.Rounded.ShoppingCart
    )

    data class Vouchers(val branding: BrandingConfig, val voucherId: String) : BottomNavItem(
        title = branding.vouchersTitle,
        icon = Icons.Rounded.Favorite
    )

    companion object {
        /**
         * Hilfsfunktion, um Typgleichheit zu prüfen (egal welche ID gesetzt ist).
         */
        fun isSameType(currentRoute: Screen?, item: BottomNavItem): Boolean = when {
            currentRoute is Screen.Home && item is BottomNavItem.Home -> true
            currentRoute is Screen.StampCard && item is BottomNavItem.StampCard -> true
            currentRoute is Screen.Voucher && item is BottomNavItem.Vouchers -> true
            else -> false
        }
    }
}


