package com.dominikbaki.treuebiss.feature_stamps.presentation.composables

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dominikbaki.treuebiss.R

@Composable
fun DemoAddStampButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        enabled = enabled
    ) {
        Text(stringResource(R.string.stamp_card_add_demo))
    }
}