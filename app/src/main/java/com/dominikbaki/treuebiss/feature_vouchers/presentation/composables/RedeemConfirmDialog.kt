package com.dominikbaki.treuebiss.feature_vouchers.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.dominikbaki.treuebiss.R

/**
 * Fragt den Einlöse-Code des Betriebs ab.
 *
 * Der Code gehört dem Personal, nicht dem Kunden - deshalb wird er verdeckt
 * eingegeben, damit er beim Tippen an der Kasse nicht mitgelesen wird.
 */
@Composable
internal fun RedeemConfirmDialog(
    isRedeeming: Boolean,
    @StringRes errorRes: Int?,
    onConfirm: (code: String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isRedeeming) onDismiss() },
        title = { Text(stringResource(R.string.voucher_confirm_title)) },
        text = {
            Column {
                Text(stringResource(R.string.voucher_confirm_text))
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(8) },
                    label = { Text(stringResource(R.string.voucher_code_label)) },
                    singleLine = true,
                    enabled = !isRedeeming,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = errorRes != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                // Der Fehler gehört in den Dialog, nicht in eine Snackbar:
                // Die läge hinter dem Dialogfenster und bliebe unsichtbar.
                errorRes?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(it),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code) },
                enabled = code.isNotBlank() && !isRedeeming
            ) {
                Text(stringResource(R.string.voucher_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRedeeming) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
