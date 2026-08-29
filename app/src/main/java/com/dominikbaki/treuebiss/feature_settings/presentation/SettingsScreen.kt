package com.dominikbaki.treuebiss.feature_settings.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.presentation.LegalLinks
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsClickableItem
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsInfoItem
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.SettingsSectionHeader
import com.dominikbaki.treuebiss.feature_settings.presentation.composable.ThemeModeChooser
import kotlinx.coroutines.launch

/** Liest die zentral gepflegten Adressen; leere Eintraege gelten als fehlend. */
@Composable
private fun rememberLegalLinks(): LegalLinks = LegalLinks(
    imprintUrl = stringResource(R.string.legal_imprint_url).takeIf { it.isNotBlank() },
    privacyUrl = stringResource(R.string.legal_privacy_url).takeIf { it.isNotBlank() },
    appPrivacyUrl = stringResource(R.string.legal_app_privacy_url).takeIf { it.isNotBlank() },
    termsUrl = stringResource(R.string.legal_terms_url).takeIf { it.isNotBlank() }
)

@Composable
internal fun SettingsScreen(
    snackBarHostState: SnackbarHostState,
    paddingValues: PaddingValues,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val links = rememberLegalLinks()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fehlerText = stringResource(R.string.settings_link_failed)

    fun oeffne(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (e: ActivityNotFoundException) {
            // Ohne Browser auf dem Gerät bliebe der Tipp sonst wirkungslos.
            scope.launch { snackBarHostState.showSnackbar(fehlerText) }
        }
    }

    LazyColumn(
        // Ohne die Ränder vom Scaffold läuft der Inhalt unter die Titelleiste.
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp)
    ) {
        item { SettingsSectionHeader(title = stringResource(R.string.settings_section_appearance)) }
        item {
            ThemeModeChooser(
                icon = Icons.Default.DarkMode,
                selected = uiState.themeMode,
                onSelect = viewModel::onThemeModeSelected
            )
        }

        // Rechtliches erscheint nur, soweit Adressen hinterlegt sind. Ein
        // Menüpunkt, der ins Leere führt, wäre schlechter als keiner.
        if (links.hatEintraege) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SettingsSectionHeader(title = stringResource(R.string.settings_section_legal))
            }
            rechtsEintrag(links.privacyUrl, Icons.Default.Shield, R.string.settings_privacy, ::oeffne)
            rechtsEintrag(links.appPrivacyUrl, Icons.Default.PhonelinkLock, R.string.settings_app_privacy, ::oeffne)
            rechtsEintrag(links.termsUrl, Icons.Default.Gavel, R.string.settings_terms, ::oeffne)
            rechtsEintrag(links.imprintUrl, Icons.AutoMirrored.Filled.Article, R.string.settings_imprint, ::oeffne)
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            SettingsSectionHeader(title = stringResource(R.string.settings_section_about))
        }
        item {
            SettingsInfoItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_app_version),
                value = uiState.appVersion
            )
        }
    }
}

/** Fügt einen Eintrag nur hinzu, wenn die Adresse hinterlegt ist. */
private fun androidx.compose.foundation.lazy.LazyListScope.rechtsEintrag(
    url: String?,
    icon: ImageVector,
    titelRes: Int,
    onOeffnen: (String) -> Unit
) {
    if (url == null) return
    item {
        SettingsClickableItem(
            icon = icon,
            title = stringResource(titelRes),
            onClick = { onOeffnen(url) }
        )
    }
}
