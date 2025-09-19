package com.dominikbaki.treuebiss.feature_settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
internal fun SettingsScreen(
    onNavigateUp: () -> Unit
) {
    var darkModeEnabled by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Einstellungen",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Dunkelmodus") },
            supportingContent = { Text("Aktiviere den Dark Mode für die App.") },
            leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = darkModeEnabled,
                    onCheckedChange = { darkModeEnabled = it }
                )
            }
        )

        HorizontalDivider()

        ListItem(
            headlineContent = { Text("Benachrichtigungen") },
            supportingContent = { Text("Erhalte Infos zu neuen Angeboten und Gutscheinen.") },
            leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
            trailingContent = {
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }
        )

        HorizontalDivider()
    }
}