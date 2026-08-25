package com.dominikbaki.treuebiss.core.presentation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.navigation.Screen
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig

/**
 * Definiert die Items der Bottom Navigation.
 * Titel kommen dynamisch aus der BrandingConfig.
 */
sealed class BottomNavItem(
    val icon: ImageVector,
    val screen: Screen
) {
    @Composable
    @ReadOnlyComposable
    abstract fun title(): String

    data object Home : BottomNavItem(Icons.Rounded.Home, Screen.Home) {
        @Composable
        @ReadOnlyComposable
        override fun title(): String = stringResource(R.string.nav_home)
    }

    data object StampCard : BottomNavItem(Icons.Rounded.ShoppingCart, Screen.StampCard) {
        @Composable
        @ReadOnlyComposable
        override fun title(): String = LocalBrandingConfig.current.loyaltyPointsTitle
    }

    data object Vouchers : BottomNavItem(Icons.Rounded.Favorite, Screen.Voucher) {
        @Composable
        @ReadOnlyComposable
        override fun title(): String = LocalBrandingConfig.current.vouchersTitle
    }

    companion object {
        val items: List<BottomNavItem> = listOf(Home, StampCard, Vouchers)
    }
}
