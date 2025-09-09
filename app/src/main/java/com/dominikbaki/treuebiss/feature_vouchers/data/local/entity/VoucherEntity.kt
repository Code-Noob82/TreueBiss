package com.dominikbaki.treuebiss.feature_vouchers.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "vouchers")
data class VoucherEntity(
    @PrimaryKey
    val id: String,
    val creationDate: Long,
    val expiresAt: Long,
    val isRedeemed: Boolean
)