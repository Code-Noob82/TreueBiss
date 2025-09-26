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
        title = { Text("Gutschein anzeigen?") },
        // NEU: Angepasster Text für den neuen Ablauf
        text = { Text("Möchtest du den QR-Code zum Einlösen anzeigen? Der Gutschein wird eingelöst, sobald du das Fenster schließt.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("QR-Code anzeigen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}