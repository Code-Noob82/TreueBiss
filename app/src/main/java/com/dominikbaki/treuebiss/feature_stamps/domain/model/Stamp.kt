package com.dominikbaki.treuebiss.feature_stamps.domain.model

import java.util.Date

/**
 * Repräsentiert einen einzelnen Stempel.
 */
data class Stamp(
    val id: String,
    val timestamp: Date,
    val isSynced: Boolean
)