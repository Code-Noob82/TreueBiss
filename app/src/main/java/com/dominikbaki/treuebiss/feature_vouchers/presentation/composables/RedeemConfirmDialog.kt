package com.dominikbaki.treuebiss.feature_vouchers.presentation.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R

@Composable
internal fun RedeemConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.voucher_confirm_title)) },
        text = { Text(stringResource(R.string.voucher_confirm_text)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.voucher_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}