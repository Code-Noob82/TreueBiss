package com.dominikbaki.treuebiss.core.data.remote.datasource

import com.dominikbaki.treuebiss.feature_stamps.domain.model.Stamp

// Platzhalter für die Supabase Datenquelle
interface SupabaseStampDataSource {
    suspend fun upsert(stamp: Stamp, deviceId: String, tenantId: String)
}