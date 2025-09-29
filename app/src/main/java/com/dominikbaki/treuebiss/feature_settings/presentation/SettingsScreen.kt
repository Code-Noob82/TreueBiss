package com.dominikbaki.treuebiss.feature_settings.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsClickableItem
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsInfoItem
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsSectionHeader
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsSwitchItem


@Composable
internal fun SettingsScreen(
    onNavigateUp: () -> Unit
) {
    // Statische Zustände für die UI-Demo
    var darkModeEnabled by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.padding(16.dp)
    ) {
        // --- Sektion: Allgemein ---
        item {
            SettingsSectionHeader(title = "Allgemein")
        }
        item {
            SettingsSwitchItem(
                icon = Icons.Default.DarkMode,
                title = "Dunkelmodus",
                subtitle = "Reduziert die Belastung für die Augen.",
                checked = darkModeEnabled,
                onCheckedChange = { darkModeEnabled = it }
            )
        }
        item {
            SettingsSwitchItem(
                icon = Icons.Default.Notifications,
                title = "Benachrichtigungen",
                subtitle = "Angebote & Gutscheine erhalten.",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
        }

        // --- Sektion: Konto ---
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSectionHeader(title = "Konto")
        }
        item {
            SettingsClickableItem(
                icon = Icons.Default.Person,
                title = "Profil bearbeiten",
                subtitle = "Namen und persönliche Daten ändern.",
                onClick = { /* Statisch, keine Aktion */ }
            )
        }
        item {
            SettingsClickableItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = "Abmelden",
                subtitle = "Du wirst von diesem Gerät abgemeldet.",
                onClick = { /* Statisch, keine Aktion */ }
            )
        }

        // --- Sektion: Rechtliches & Info ---
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSectionHeader(title = "Rechtliches & Info")
        }
        item {
            SettingsClickableItem(
                icon = Icons.Default.Shield,
                title = "Datenschutz",
                onClick = { /* Statisch, keine Aktion */ }
            )
        }
        item {
            SettingsClickableItem(
                icon = Icons.AutoMirrored.Filled.Article,
                title = "Impressum",
                onClick = { /* Statisch, keine Aktion */ }
            )
        }
        item {
            // Statisches Info-Item ohne Klick-Aktion
            SettingsInfoItem(
                icon = Icons.Default.Info,
                title = "App-Version",
                value = "1.0.0" // Beispiel-Version
            )
        }
    }
}