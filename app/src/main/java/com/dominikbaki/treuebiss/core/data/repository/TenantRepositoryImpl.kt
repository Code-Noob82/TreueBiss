package com.dominikbaki.treuebiss.core.data.repository

import android.util.Log
import com.dominikbaki.treuebiss.BuildConfig
import com.dominikbaki.treuebiss.core.data.remote.datasource.SupabaseDataSource
import com.dominikbaki.treuebiss.core.domain.models.Offer
import com.dominikbaki.treuebiss.core.domain.models.Tenant
import com.dominikbaki.treuebiss.core.domain.repository.TenantRepository
import com.dominikbaki.treuebiss.feature_tenant.data.local.dao.TenantDao
import com.dominikbaki.treuebiss.feature_tenant.data.mapper.toOffer
import com.dominikbaki.treuebiss.feature_tenant.data.mapper.toOfferEntity
import com.dominikbaki.treuebiss.feature_tenant.data.mapper.toTenant
import com.dominikbaki.treuebiss.feature_tenant.data.mapper.toTenantEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TenantRepositoryImpl @Inject constructor(
    private val dao: TenantDao,
    private val remoteDataSource: SupabaseDataSource,
    private val supabaseClient: SupabaseClient
) : TenantRepository {

    override val activeTenantId: String = BuildConfig.TENANT_ID

    override fun observeActiveTenant(): Flow<Tenant> =
        dao.observeTenant(activeTenantId).map { entity ->
            entity?.toTenant() ?: Tenant.fallback(activeTenantId)
        }

    override fun observeOffers(): Flow<List<Offer>> =
        dao.observeOffers(activeTenantId).map { entities -> entities.map { it.toOffer() } }

    override suspend fun syncFromRemote() {
        val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
        if (currentUserId == null) {
            Log.w("TenantRepositoryImpl", "User not logged in, skipping tenant sync")
            return
        }

        // Zuerst die Mitgliedschaft: Ohne sie lehnen die RLS-Policies jedes
        // spätere Einfügen von Stempeln und Gutscheinen ab.
        remoteDataSource.ensureMembership(currentUserId, activeTenantId)

        val tenant = remoteDataSource.getTenant(activeTenantId)
        if (tenant == null) {
            Log.w("TenantRepositoryImpl", "Tenant $activeTenantId not found on server")
        } else {
            dao.upsertTenant(tenant.toTenantEntity())
        }

        val offers = remoteDataSource.getOffers(activeTenantId)
        dao.replaceOffers(activeTenantId, offers.map { it.toOfferEntity() })
        Log.d("TenantRepositoryImpl", "Tenant synced, ${offers.size} offers")
    }
}
