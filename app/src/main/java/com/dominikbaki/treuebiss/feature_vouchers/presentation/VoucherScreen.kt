package com.dominikbaki.treuebiss.feature_vouchers.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoucherScreen(
    onNavigateUp: () -> Unit,
    viewModel: VoucherViewModel = hiltViewModel()
) {
    val vouchers by viewModel.vouchers.collectAsState()
    var showDialog by remember { mutableStateOf(false) } // NEU: Dialog-Status
    var selectedVoucherId by remember { mutableStateOf<String?>(null)} // Neu: ausgewählter Gutschein

    if (showDialog && selectedVoucherId != null) {
        RedeemConfirmDialog(
            onConfirm = {
                viewModel.onRedeemClicked(selectedVoucherId!!)
                showDialog = false
                selectedVoucherId = null
            },
            onDismiss = {
                showDialog = false
                selectedVoucherId = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gutscheinübersicht") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (vouchers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("Du hast aktuell keine offenen Gutscheine.",
                    style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vouchers, key = { it.id }) { voucher ->
                    VoucherItem(
                        voucher = voucher,
                        onRedeem = {
                            selectedVoucherId = voucher.id
                            showDialog = true
                        }
                    )
                }
            }
        }
    }
}