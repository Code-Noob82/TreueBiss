package com.dominikbaki.treuebiss.feature_onboarding.presentation

import androidx.annotation.DrawableRes
import com.dominikbaki.treuebiss.R

internal sealed class OnboardingPage(
    val title: String,
    val description: String,
    @DrawableRes val imageRes: Int
) {
    object First : OnboardingPage(
        title = "Bonuspunkte sammeln",
        description = "Sammle bei jedem Einkauf Punkte und vergiss nie wieder deine Bonuskarte",
        imageRes = R.drawable.bonuskarte
    )

    object Second : OnboardingPage(
        title = "Gutscheine sichern",
        description = "Volle Karte? Erhalte automatisch einen Gutschein für deinen nächsten Einkauf.",
        imageRes = R.drawable.gutschein
    )

    object Third : OnboardingPage(
        title = "Immer informiert",
        description = "Checke das lokale Wetter, bevor du losgehst - direkt in deiner App.",
        imageRes = R.drawable.wetter
    )

    object Fourth : OnboardingPage(
        title = "Ganz wie dahäm",
        description = "Wähle deinen lokalen Dialekt und fühle dich wie zu Hause.",
        imageRes = R.drawable.einstellungen
    )
}