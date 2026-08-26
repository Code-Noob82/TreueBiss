package com.dominikbaki.treuebiss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.core.presentation.MainScreen
import com.dominikbaki.treuebiss.core.presentation.MainViewModel
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig
import com.dominikbaki.treuebiss.core.presentation.branding.toBrandingConfig
import com.dominikbaki.treuebiss.core.theme.TreueBissTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Ab Android 15 erzwungen; hier explizit, damit sich alle
        // Versionen gleich verhalten.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Dasselbe ViewModel wie in MainScreen - hiltViewModel() liefert
            // hier den Activity-Scope, also dieselbe Instanz.
            val mainViewModel: MainViewModel = hiltViewModel()
            val tenant by mainViewModel.activeTenant.collectAsState()

            TreueBissTheme(brandPrimaryColor = tenant.primaryColor) {
                CompositionLocalProvider(LocalBrandingConfig provides tenant.toBrandingConfig()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(mainViewModel = mainViewModel)
                    }
                }
            }
        }
    }
}
