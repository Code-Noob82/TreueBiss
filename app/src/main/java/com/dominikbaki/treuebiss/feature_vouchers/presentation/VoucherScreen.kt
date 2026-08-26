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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.feature_vouchers.presentation.composables.EmptyState
import com.dominikbaki.treuebiss.feature_vouchers.presentation.composables.RedeemConfirmDialog
import com.dominikbaki.treuebiss.feature_vouchers.presentation.composables.VoucherDetailOverlay
import com.dominikbaki.treuebiss.feature_vouchers.presentation.composables.VoucherItem
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoucherScreen(
    paddingValues: PaddingValues,
    snackBarHostState: SnackbarHostState,
    viewModel: VoucherViewModel = hiltViewModel()
) {
    val vouchers by viewModel.vouchers.collectAsState()
    val isRedeeming by viewModel.isRedeeming.collectAsState()
    val context = LocalContext.current
    val redeemedMessage = stringResource(R.string.voucher_redeemed)

    // Der Gutschein, dessen QR-Code gerade als Overlay gezeigt wird.
    // Das Anzeigen allein verändert nichts - eingelöst wird erst nach Bestätigung.
    var voucherToShowInOverlay by remember { mutableStateOf<Voucher?>(null) }

    // Der Gutschein, für den die Einlöse-Rückfrage offen ist.
    var voucherPendingRedeem by remember { mutableStateOf<Voucher?>(null) }

    // Fehler beim Einlösen, im Dialog angezeigt.
    var redeemError by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is VoucherEvent.Redeemed -> {
                    // Nur im Erfolgsfall schließen. Bei falschem Code bleibt
                    // der Dialog stehen, damit die Verkaufskraft es sofort
                    // erneut versuchen kann.
                    redeemError = null
                    voucherPendingRedeem = null
                    voucherToShowInOverlay = null
                    snackBarHostState.showSnackbar(redeemedMessage)
                }

                is VoucherEvent.Failed -> redeemError = event.messageRes
            }
        }
    }

    // --- Logik für die Anzeige der Composables ---

    // 1. QR-Code-Overlay: nur Anzeige, plus expliziter Einlöse-Wunsch.
    voucherToShowInOverlay?.let { voucher ->
        VoucherDetailOverlay(
            voucher = voucher,
            onRedeem = { voucherPendingRedeem = voucher },
            onDismiss = { voucherToShowInOverlay = null }
        )
    }

    // 2. Rückfrage direkt vor dem einzigen Schritt, der nicht umkehrbar ist.
    voucherPendingRedeem?.let { voucher ->
        RedeemConfirmDialog(
            isRedeeming = isRedeeming,
            errorRes = redeemError,
            onConfirm = { code ->
                redeemError = null
                viewModel.onRedeemConfirmed(voucher.id, code)
            },
            onDismiss = {
                redeemError = null
                voucherPendingRedeem = null // Overlay bleibt offen
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
                contentDescription = stringResource(R.string.vouchers_icon),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = stringResource(R.string.vouchers_title),
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
                        onRedeem = { voucherToShowInOverlay = voucher }
                    )
                }
            }
        }
    }
}