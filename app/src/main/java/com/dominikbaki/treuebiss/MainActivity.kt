package com.dominikbaki.treuebiss

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.dominikbaki.treuebiss.core.navigation.AppNavigation
import com.dominikbaki.treuebiss.core.presentation.MainScreen
import com.dominikbaki.treuebiss.core.presentation.branding.DefaultBrandingConfig
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig
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
            TreueBissTheme {
                // BrandingConfig hier setzen (später dynamisch laden)
                val branding = DefaultBrandingConfig

                CompositionLocalProvider(LocalBrandingConfig provides branding) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen()
                    }
                }
            }
        }
    }
}