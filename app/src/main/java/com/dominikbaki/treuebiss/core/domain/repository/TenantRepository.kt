package com.dominikbaki.treuebiss.core.domain.repository

import com.dominikbaki.treuebiss.core.domain.models.Offer
import com.dominikbaki.treuebiss.core.domain.models.Tenant
import kotlinx.coroutines.flow.Flow

/**
 * Port für den Betrieb, für den dieser Build gedacht ist.
 *
 * Die ID steht fest (aus `BuildConfig.TENANT_ID`), damit lokale Daten schon
 * vor dem ersten Serverkontakt zugeordnet werden können. Vom Server kommen
 * nur Branding, Angebote und die Kartenregeln.
 */
interface TenantRepository {
    /** Die ID des aktiven Betriebs. Immer verfügbar, auch offline. */
    val activeTenantId: String

    /** Der aktive Betrieb - aus dem lokalen Zwischenspeicher, sonst [Tenant.fallback]. */
    fun observeActiveTenant(): Flow<Tenant>

    /** Angebote des aktiven Betriebs. */
    fun observeOffers(): Flow<List<Offer>>

    /** Holt Stammdaten und Angebote vom Server und legt die Mitgliedschaft an. */
    suspend fun syncFromRemote()
}
