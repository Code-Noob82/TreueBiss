package com.dominikbaki.treuebiss.feature_vouchers.presentation.composables

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun RedeemConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gutschein einlösen?") },
        text = { Text("Der Gutschein wird jetzt als eingelöst markiert. Das lässt sich nicht rückgängig machen.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Jetzt einlösen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}