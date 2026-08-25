package com.dominikbaki.treuebiss.feature_stamps.presentation.composables

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R

@Composable
internal fun StampCircle(isStamped: Boolean) {
    // NEU: Animationen für ein dynamischeres Gefühl
    val scale by animateFloatAsState(targetValue = if (isStamped) 1f else 0.9f, label = "scaleAnim")
    val color = if (isStamped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val alpha by animateFloatAsState(targetValue = if (isStamped) 1f else 0.6f, label = "alphaAnim")

    Box(
        modifier = Modifier
            .size(60.dp) // Etwas größer für bessere Sichtbarkeit
            .scale(scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
            // NEU: Ein Rand für die leeren Stempelplätze
            .border(
                width = 2.dp,
                color = if (isStamped) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isStamped) {
            // NEU: Ein Icon im Stempelkreis zur Bestätigung
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.stamp_card_stamped),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}