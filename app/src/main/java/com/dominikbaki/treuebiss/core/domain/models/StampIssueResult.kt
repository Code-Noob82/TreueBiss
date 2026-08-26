package com.dominikbaki.treuebiss.core.domain.models

/**
 * Ergebnis einer serverseitigen Stempelvergabe.
 *
 * @param stampCount Anzahl der Stempel nach der Vergabe. Wurde ein Gutschein
 *   erzeugt, ist die Karte danach zurückgesetzt - dieser Wert ist dann die
 *   Zahl, die den Gutschein ausgelöst hat.
 * @param voucher Der neu entstandene Gutschein, sonst null.
 */
data class StampIssueResult(
    val stampId: String,
    val stampCount: Int,
    val voucher: Voucher?
)

/**
 * Der Kaufnachweis, gegen den ein Stempel vergeben wird.
 *
 * @param reference Eindeutige Kennung, z. B. die TSE-Transaktionsnummer eines
 *   Kassenbons. Der Server lehnt eine bereits verwendete Kennung ab.
 * @param source Woher der Nachweis stammt.
 */
data class StampProof(
    val reference: String,
    val source: Source
) {
    enum class Source(val wireValue: String) {
        Receipt("receipt"),
        Demo("demo")
    }
}

/** Der Nachweis wurde bereits eingelöst - derselbe Beleg zählt nur einmal. */
class ProofAlreadyUsedException(message: String) : Exception(message)

/** Der eingegebene Einlöse-Code passt nicht zum Betrieb. */
class InvalidRedeemCodeException(message: String) : Exception(message)

/** Der Gutschein ist bereits eingelöst, abgelaufen oder unbekannt. */
class VoucherNotRedeemableException(message: String) : Exception(message)
