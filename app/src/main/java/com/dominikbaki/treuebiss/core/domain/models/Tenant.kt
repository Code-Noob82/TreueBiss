package com.dominikbaki.treuebiss.core.domain.models

/**
 * Ein Betrieb (Mandant). Trägt alles, was pro Kunde unterschiedlich ist -
 * Bezeichnungen, Farbe und die Spielregeln der Stempelkarte.
 *
 * Die Werte kommen vom Server. Bis sie da sind (erster Start, offline) gilt
 * [fallback], damit die App nie ohne Beschriftung dasteht.
 */
data class Tenant(
    val id: String,
    val name: String,
    val loyaltyPointsTitle: String,
    val vouchersTitle: String,
    val dailySpecialTitle: String,
    /** Primärfarbe als Hex-String, z. B. "#4CAF50", oder null für den Standard. */
    val primaryColor: String?,
    val logoUrl: String?,
    val stampsPerCard: Int,
    val voucherValidityDays: Int,
    /**
     * Verlangt der Betrieb beim Einlösen einen Code? Standard ist `false`:
     * Der Kunde löst selbst ein, das Personal prüft die Bestätigung per Blick.
     */
    val requiresRedeemCode: Boolean
) {
    companion object {
        const val DEFAULT_STAMPS_PER_CARD = 10
        const val DEFAULT_VOUCHER_VALIDITY_DAYS = 90

        /**
         * Platzhalter, solange der Betrieb noch nicht geladen ist.
         * Die ID stammt aus dem Build, die Texte sind neutral gehalten.
         */
        fun fallback(id: String) = Tenant(
            id = id,
            name = "TreueBiss",
            loyaltyPointsTitle = "Treuepunkte",
            vouchersTitle = "Gutscheine",
            dailySpecialTitle = "Angebot des Tages",
            primaryColor = null,
            logoUrl = null,
            stampsPerCard = DEFAULT_STAMPS_PER_CARD,
            voucherValidityDays = DEFAULT_VOUCHER_VALIDITY_DAYS,
            requiresRedeemCode = false
        )
    }
}

/** Ein Angebot des Betriebs, ersetzt die frühere Dummy-Anzeige. */
data class Offer(
    val id: String,
    val tenantId: String,
    val title: String,
    val description: String?
)
