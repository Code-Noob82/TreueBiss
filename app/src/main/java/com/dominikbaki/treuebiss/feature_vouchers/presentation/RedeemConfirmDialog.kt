package com.dominikbaki.treuebiss.feature_vouchers.presentation

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
        text = { Text("Möchtest du diesen Gutschein wirklich jetzt einlösen? Diese Aktion kann nicht rückgängig gemacht werden.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Einlösen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}