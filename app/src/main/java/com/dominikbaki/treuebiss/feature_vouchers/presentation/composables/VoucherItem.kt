package com.dominikbaki.treuebiss.feature_vouchers.presentation.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.ui.utils.DateTimeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Composable
internal fun VoucherItem(
    voucher: Voucher,
    onRedeem: () -> Unit
) {
    val expiresAtInstant = Instant.fromEpochMilliseconds(voucher.expiresAt)
    val expiresAtFormatted = DateTimeFormatter.formatInstant(expiresAtInstant)
    val isExpired = voucher.isExpiredAt(Clock.System.now())

    // NEU: Einheitliches Kartendesign mit stärkerer Abrundung und angepassten Farben
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // NEU: Dezente Alpha-Änderung für abgelaufene Gutscheine
            .alpha(if (isExpired) 0.7f else 1f),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpired) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // NEU: Icon zur visuellen Unterstützung
            Icon(
                imageVector = if (isExpired) Icons.Filled.Error else Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.voucher_status_icon),
                modifier = Modifier.size(32.dp),
                tint = if (isExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.voucher_name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpired) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.voucher_valid_until, expiresAtFormatted),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isExpired) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(
                            alpha = 0.7f
                        )
                    }
                )
            }

            // Button-Container
            Box {
                if (isExpired) {
                    TextButton(onClick = {}, enabled = false) {
                        Text(stringResource(R.string.voucher_expired))
                    }
                } else {
                    // NEU: Button-Stil an Screenshot angepasst (kleiner, andere Farbe/Form)
                    Button(
                        onClick = onRedeem,
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        // Weniger Padding für einen kompakteren Button
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.voucher_redeem))
                    }
                }
            }
        }
    }
}