package com.dominikbaki.treuebiss.feature_vouchers.domain.model

import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Repräsentiert einen Gutschein in der Domain-Schicht (die "saubere" Logik).
 *
 * @param expiresAt Zeitstempel (in Millisekunden), wann der Gutschein abläuft.
 * Standardmäßig 90 Tage nach Erstellung.
 */
data class Voucher(
    val id: String = UUID.randomUUID().toString(),
    val creationDate: Long = System.currentTimeMillis(),
    val expiresAt: Long = creationDate + TimeUnit.DAYS.toMillis(90), // NEU: Ablaufdatum hinzugefügt
    val isRedeemed: Boolean = false
)