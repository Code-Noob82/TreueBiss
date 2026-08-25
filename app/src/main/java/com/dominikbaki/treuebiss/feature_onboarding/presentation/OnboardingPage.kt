package com.dominikbaki.treuebiss.feature_onboarding.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dominikbaki.treuebiss.R

/**
 * Eine Seite des Onboardings. Texte als String-Ressourcen, damit sie pro
 * White-Label-Instanz austauschbar und übersetzbar sind.
 */
internal sealed class OnboardingPage(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @DrawableRes val imageRes: Int
) {
    data object First : OnboardingPage(
        titleRes = R.string.onboarding_1_title,
        descriptionRes = R.string.onboarding_1_description,
        imageRes = R.drawable.bonuskarte
    )

    data object Second : OnboardingPage(
        titleRes = R.string.onboarding_2_title,
        descriptionRes = R.string.onboarding_2_description,
        imageRes = R.drawable.gutschein
    )

    data object Third : OnboardingPage(
        titleRes = R.string.onboarding_3_title,
        descriptionRes = R.string.onboarding_3_description,
        imageRes = R.drawable.wetter
    )

    data object Fourth : OnboardingPage(
        titleRes = R.string.onboarding_4_title,
        descriptionRes = R.string.onboarding_4_description,
        imageRes = R.drawable.einstellungen
    )

    companion object {
        val pages: List<OnboardingPage> = listOf(First, Second, Third, Fourth)
    }
}
