package com.dominikbaki.treuebiss.feature_settings.presentation.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.domain.repository.ThemeMode

/**
 * Auswahl des Erscheinungsbilds.
 *
 * Bewusst drei Optionen statt eines Schalters: Bei einem Schalter bliebe
 * unklar, ob "aus" hell bedeutet oder der Systemeinstellung folgt.
 */
@Composable
internal fun ThemeModeChooser(
    icon: ImageVector,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    val beschriftung = mapOf(
        ThemeMode.System to R.string.settings_theme_system,
        ThemeMode.Hell to R.string.settings_theme_light,
        ThemeMode.Dunkel to R.string.settings_theme_dark
    )

    Column(modifier = Modifier.selectableGroup()) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        ThemeMode.entries.forEach { modus ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = modus == selected,
                        onClick = { onSelect(modus) },
                        role = Role.RadioButton
                    )
                    .padding(start = 40.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = modus == selected, onClick = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(beschriftung.getValue(modus)),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
