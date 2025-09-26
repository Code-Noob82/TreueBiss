package com.dominikbaki.treuebiss.core.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import com.dominikbaki.treuebiss.core.navigation.Screen
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig

/**
 * Definiert die Items der Bottom Navigation.
 * Titel kommen jetzt dynamisch aus der BrandingConfig.
 */
sealed class BottomNavItem(
    val icon: ImageVector
) {
    @Composable
    @ReadOnlyComposable
    abstract fun title(): String
    object Home : BottomNavItem(icon = Icons.Rounded.Home) {
        @Composable
        @ReadOnlyComposable
        override fun title(): String = "Start"
    }

    data class StampCard(val cardId: String) : BottomNavItem(icon = Icons.Rounded.ShoppingCart) {
        @Composable
        @ReadOnlyComposable
        override fun title(): String = LocalBrandingConfig.current.loyaltyPointsTitle
    }

    data class Vouchers(val voucherId: String) : BottomNavItem(icon = Icons.Rounded.Favorite) {
        @Composable
        @ReadOnlyComposable
        override fun title(): String = LocalBrandingConfig.current.vouchersTitle
    }

    companion object {
        /**
         * Hilfsfunktion, um Typgleichheit zu prüfen (egal welche ID gesetzt ist).
         */
        fun isSameType(currentRoute: Screen?, item: BottomNavItem): Boolean = when {
            currentRoute is Screen.Home && item is Home -> true
            currentRoute is Screen.StampCard && item is StampCard -> true
            currentRoute is Screen.Voucher && item is Vouchers -> true
            else -> false
        }
    }
}


