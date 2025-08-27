package com.dominikbaki.treuebiss.data.local.entity

import com.dominikbaki.treuebiss.feature_stamps.domain.model.Stamp

// Platzhalter für die Room-Entität

data class StampEntity(
    val id: String,
    val collectedAt: Long,
    val partnerId: String,
    val deviceId: String,
    val tenantId: String
) {
    fun toDomain(): Stamp = Stamp(
        id = this.id,
        collectedAt = this.collectedAt,
        partnerId = this.partnerId
    )
}

// Mapper-Funktion vom Domain-Model zur Entität
fun Stamp.toEntity(deviceId: String, tenantId: String): StampEntity = StampEntity(
    id = this.id,
    collectedAt = this.collectedAt,
    partnerId = this.partnerId,
    deviceId = deviceId,
    tenantId = tenantId
)