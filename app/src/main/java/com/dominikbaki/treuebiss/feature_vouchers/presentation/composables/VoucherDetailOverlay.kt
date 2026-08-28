package com.dominikbaki.treuebiss.feature_vouchers.presentation.composables

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig
import com.dominikbaki.treuebiss.core.ui.utils.DateTimeFormatter
import kotlinx.datetime.Instant

/**
 * Zeigt den QR-Code eines Gutscheins als Vollbild-Overlay.
 *
 * Wichtig: Das Anzeigen löst den Gutschein NICHT ein. Eingelöst wird erst,
 * wenn der Nutzer [onRedeem] auslöst - so kostet ein versehentliches Öffnen
 * (oder ein Blick auf den Code) den Gutschein nicht.
 *
 * @param onRedeem Wird ausgelöst, wenn der Nutzer den Gutschein einlösen möchte.
 * @param onDismiss Schließt das Overlay, ohne etwas zu verändern.
 */
@Composable
internal fun VoucherDetailOverlay(
    voucher: Voucher,
    isRedeeming: Boolean,
    @StringRes errorRes: Int?,
    onRedeem: () -> Unit,
    onDismiss: () -> Unit
) {
    val branding = LocalBrandingConfig.current // Branding-Informationen abrufen
    val expiresAtInstant = Instant.fromEpochMilliseconds(voucher.expiresAt)
    val expiresAtFormatted = DateTimeFormatter.formatInstant(expiresAtInstant)

    // Full-Screen-Dialog, der als Overlay fungiert
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {

        // Eleganter Hintergrund mit Farbverlauf
        val gradientBrush = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                MaterialTheme.colorScheme.background
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        ) {
            // Zurück-Button statt Schließen-Icon für eine bessere UX
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.nav_back),
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 80.dp), // Mehr vertikaler Abstand
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Geschäftsname aus dem Branding
                Text(
                    text = branding.businessName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.voucher_overlay_instruction),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(48.dp))

                // Aufgewertete QR-Code Karte
                Card(
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            // Der Code trägt die Gutschein-ID - genau den Wert,
                            // den ein Kassen-Scanner zum Abgleich benötigt.
                            QrCodeImage(
                                data = voucher.id,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = stringResource(R.string.voucher_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.voucher_valid_until, expiresAtFormatted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = onRedeem,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !isRedeeming
                ) {
                    Text(stringResource(R.string.voucher_overlay_redeem))
                }
                Spacer(Modifier.height(8.dp))
                // Der Fehler gehört hierher, nicht in eine Snackbar: Das
                // Overlay ist ein eigenes Fenster, eine Snackbar dahinter
                // sieht niemand. Ohne das bliebe ein Fehlschlag stumm.
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = stringResource(R.string.voucher_overlay_safe_hint),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
