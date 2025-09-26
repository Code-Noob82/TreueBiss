package com.dominikbaki.treuebiss.feature_vouchers.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.feature_vouchers.presentation.composables.EmptyState
import com.dominikbaki.treuebiss.feature_vouchers.presentation.composables.RedeemConfirmDialog
import com.dominikbaki.treuebiss.feature_vouchers.presentation.composables.VoucherDetailOverlay
import com.dominikbaki.treuebiss.feature_vouchers.presentation.composables.VoucherItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoucherScreen(
    voucherId: String,
    paddingValues: PaddingValues,
    viewModel: VoucherViewModel = hiltViewModel()
) {
    val vouchers by viewModel.vouchers.collectAsState()

    // Zustand für den Bestätigungsdialog
    var showConfirmDialog by remember { mutableStateOf(false) } // Dialog-Status
    var selectedVoucherIdForConfirm by remember { mutableStateOf<String?>(null) } // ausgewählter Gutschein

    // NEU: Zustand für das Detail-Overlay
    var voucherToShowInOverlay by remember { mutableStateOf<Voucher?>(null) }

    // --- Logik für die Anzeige der Composables ---

    // 1. Wenn ein Gutschein im Overlay angezeigt werden soll...
    voucherToShowInOverlay?.let { voucher ->
        VoucherDetailOverlay(
            voucher = voucher,
            onDismiss = {
                // HIER wird der Gutschein eingelöst, wenn der Nutzer das Overlay schließt
                viewModel.onRedeemClicked(voucher.id)
                voucherToShowInOverlay = null // Overlay schließen
            }
        )
    }

    // 2. Wenn der Bestätigungsdialog angezeigt werden soll...
    if (showConfirmDialog && selectedVoucherIdForConfirm != null) {
        RedeemConfirmDialog(
            onConfirm = {
                // Finde den vollständigen Gutschein zur ID
                val voucherToRedeem = vouchers.find { it.id == selectedVoucherIdForConfirm }
                if (voucherToRedeem != null) {
                    // Zeige jetzt das Overlay an
                    voucherToShowInOverlay = voucherToRedeem
                }
                // Schließe den Bestätigungsdialog
                showConfirmDialog = false
                selectedVoucherIdForConfirm = null
            },
            onDismiss = {
                showConfirmDialog = false
                selectedVoucherIdForConfirm = null
            }
        )
    }
    // NEU: Einheitliche Seitenstruktur mit Header
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp)
    ) {
        // --- Header Sektion ---
        Row(
            modifier = Modifier.padding(vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ConfirmationNumber,
                contentDescription = "Gutschein-Icon",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Meine Gutscheine",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }
        if (vouchers.isEmpty()) {
            // NEU: Aufgewerteter "Empty State"
            EmptyState()
        } else {
            // NEU: Angepasste Liste
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp) // Mehr Abstand
            ) {
                items(vouchers, key = { it.id }) { voucher ->
                    VoucherItem(
                        voucher = voucher,
                        onRedeem = {
                            selectedVoucherIdForConfirm = voucher.id
                            showConfirmDialog = true
                        }
                    )
                }
            }
        }
    }
}