package com.dominikbaki.treuebiss.feature_vouchers.presentation.composables

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.presentation.branding.LocalBrandingConfig
import kotlinx.coroutines.delay

/**
 * Zeigt nach dem Einlösen eine Bestätigung, auf die das Personal an der Kasse
 * kurz schaut.
 *
 * Der Bildschirm ist bewusst zeitgebunden: Ein herunterlaufender Balken und
 * eine dauernde Animation lassen sich mit einem Screenshot nicht nachstellen.
 * Das ist kein kryptografischer Nachweis - wer Gewissheit braucht, scannt den
 * QR-Code an der Kasse. Für den Regelfall kostet es null Sekunden Personalzeit.
 *
 * @param secondsVisible Wie lange die Bestätigung als frisch gilt.
 */
@Composable
internal fun RedeemedConfirmation(
    secondsVisible: Int = 30,
    onDismiss: () -> Unit
) {
    val branding = LocalBrandingConfig.current
    var remaining by remember { mutableLongStateOf(secondsVisible.toLong()) }

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
        onDismiss()
    }

    // Dauerhafte Bewegung: ein Standbild sieht sofort anders aus.
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(140.dp)
                        .scale(scale)
                )
                Spacer(Modifier.height(32.dp))
                Text(
                    text = stringResource(R.string.voucher_redeemed_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = branding.businessName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(48.dp))
                LinearProgressIndicator(
                    progress = { remaining.toFloat() / secondsVisible },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.voucher_redeemed_countdown, remaining),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(40.dp))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.action_done))
                }
            }
        }
    }
}
