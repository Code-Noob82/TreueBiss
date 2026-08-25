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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dominikbaki.treuebiss.BuildConfig
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsClickableItem
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsInfoItem
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsSectionHeader
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsSwitchItem


@Composable
internal fun SettingsScreen() {
    // Statische Zustände für die UI-Demo
    var darkModeEnabled by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.padding(16.dp)
    ) {
        // --- Sektion: Allgemein ---
        item {
            SettingsSectionHeader(title = stringResource(R.string.settings_section_general))
        }
        item {
            SettingsSwitchItem(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.settings_dark_mode),
                subtitle = stringResource(R.string.settings_dark_mode_subtitle),
                checked = darkModeEnabled,
                onCheckedChange = { darkModeEnabled = it }
            )
        }
        item {
            SettingsSwitchItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_subtitle),
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )
        }

        // --- Sektion: Konto ---
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSectionHeader(title = stringResource(R.string.settings_section_account))
        }
        item {
            SettingsClickableItem(
                icon = Icons.Default.Person,
                title = stringResource(R.string.settings_edit_profile),
                subtitle = stringResource(R.string.settings_edit_profile_subtitle),
                onClick = { /* Statisch, keine Aktion */ }
            )
        }
        item {
            SettingsClickableItem(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = stringResource(R.string.settings_logout),
                subtitle = stringResource(R.string.settings_logout_subtitle),
                onClick = { /* Statisch, keine Aktion */ }
            )
        }

        // --- Sektion: Rechtliches & Info ---
        item {
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSectionHeader(title = stringResource(R.string.settings_section_legal))
        }
        item {
            SettingsClickableItem(
                icon = Icons.Default.Shield,
                title = stringResource(R.string.settings_privacy),
                onClick = { /* Statisch, keine Aktion */ }
            )
        }
        item {
            SettingsClickableItem(
                icon = Icons.AutoMirrored.Filled.Article,
                title = stringResource(R.string.settings_imprint),
                onClick = { /* Statisch, keine Aktion */ }
            )
        }
        item {
            // Statisches Info-Item ohne Klick-Aktion
            SettingsInfoItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_app_version),
                value = BuildConfig.VERSION_NAME
            )
        }
    }
}