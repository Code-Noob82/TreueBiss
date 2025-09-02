package com.dominikbaki.treuebiss.feature_vouchers.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dominikbaki.treuebiss.feature_vouchers.domain.model.Voucher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@Composable
internal fun VoucherItem(
    voucher: Voucher,
    onRedeem: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd. MMM yyyy", Locale.GERMAN)
    val expiresAtFormatted = dateFormat.format(Date(voucher.expiresAt))
    val isExpired = System.currentTimeMillis() > voucher.expiresAt

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = if (isExpired) CardDefaults.cardColors(containerColor = Color.LightGray) else CardDefaults.cardColors()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Dein Treue-Gutschein",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Gültig bis: $expiresAtFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isExpired) Color.Red else LocalContentColor.current
                )
            }
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = onRedeem,
                enabled = !isExpired
            ) {
                Text(if (isExpired) "Abgelaufen" else "Einlösen")
            }
        }
    }
}