package com.dominikbaki.treuebiss.core.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dominikbaki.treuebiss.core.navigation.AppNavigation
import com.dominikbaki.treuebiss.core.navigation.Screen
import com.dominikbaki.treuebiss.core.theme.TreueBissTheme

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val navController = rememberNavController()

    // Logik zur Steuerung der BottomAppBar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val shouldShowBottomBar = currentDestination?.route in listOf(
        Screen.Home::class.qualifiedName,
        Screen.StampCard::class.qualifiedName,
        Screen.Voucher::class.qualifiedName
    )

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                NavigationBar {
                    val items = listOf(
                        BottomNavItem.Home,
                        BottomNavItem.StampCard,
                        BottomNavItem.Vouchers
                    )
                    items.forEach { item ->
                        NavigationBarItem(
                            label = { Text(item.title) },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == item.route::class.qualifiedName } == true,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavigation(
            modifier = Modifier.padding(innerPadding),
            navController = navController
        )
    }
}

@Preview
@Composable
fun MainScreenPreview() {
    TreueBissTheme {
        MainScreen()
    }
}