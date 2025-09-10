package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DailySpecialCard(special: DailySpecial) {
    val branding = LocalBrandingConfig.current // Holt sich das Branding
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(branding.dailySpecialTitle, style = MaterialTheme.typography.titleMedium) // Aus dem BRANDING
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                special.imageUrl?.let { imageResId ->
                    Image(
                        painter = painterResource(id = imageResId),
                        contentDescription = special.title,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column {
                    Text(special.title, fontWeight = FontWeight.Bold)
                    Text(special.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}