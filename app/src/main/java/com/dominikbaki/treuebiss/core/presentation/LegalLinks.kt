package com.dominikbaki.treuebiss.core.presentation

/**
 * Die rechtlichen Seiten von TreueBiss.
 *
 * Zentral, nicht pro Betrieb: Anbieter der App ist byte & Handwerk, TreueBiss
 * ist das Produkt, der Betrieb ist Kunde. Die Adressen stehen in `res/values/legal.xml` und sind dort zunächst
 * leer - die Einträge erscheinen in den Einstellungen erst, wenn eine Adresse
 * hinterlegt ist. Ein Menüpunkt, der ins Leere führt, wäre schlechter als
 * keiner.
 */
data class LegalLinks(
    val imprintUrl: String?,
    val privacyUrl: String?,
    val appPrivacyUrl: String?,
    val termsUrl: String?
) {
    val hatEintraege: Boolean
        get() = listOfNotNull(imprintUrl, privacyUrl, appPrivacyUrl, termsUrl).isNotEmpty()
}
