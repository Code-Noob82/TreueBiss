package com.dominikbaki.treuebiss.core.domain.models

import kotlinx.datetime.Instant
import java.util.UUID

/**
 * Repräsentiert einen Gutschein in der Domain-Schicht (die "saubere" Logik).
 *
 * Bewusst ohne Serialisierungs-Annotationen: Das Mapping auf die
 * Supabase-Spaltennamen übernimmt [com.dominikbaki.treuebiss.feature_vouchers.data.remote.dto.VoucherDto].
 * Die alte Annotation hier wich davon ab ("created_date" statt "creation_date").
 */
data class Voucher(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant,
    /** Erstellungszeitpunkt als Unix-Timestamp in Millisekunden. */
    val creationDate: Long,
    /** Ablaufzeitpunkt als Unix-Timestamp in Millisekunden. */
    val expiresAt: Long,
    val isRedeemed: Boolean = false,
    val tenantId: String,
    val userId: String? = null
) {
    /**
     * Ein Gutschein ist abgelaufen, wenn sein Ablaufzeitpunkt überschritten ist.
     * Abgelaufene Gutscheine bleiben sichtbar (als "Abgelaufen" markiert),
     * zählen aber nicht mehr als einlösbar.
     */
    fun isExpiredAt(now: Instant): Boolean = now.toEpochMilliseconds() > expiresAt

    fun isRedeemableAt(now: Instant): Boolean = !isRedeemed && !isExpiredAt(now)
}
