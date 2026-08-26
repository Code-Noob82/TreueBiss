package com.dominikbaki.treuebiss.feature_tenant.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.dominikbaki.treuebiss.feature_tenant.data.local.entity.OfferEntity
import com.dominikbaki.treuebiss.feature_tenant.data.local.entity.TenantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTenant(tenant: TenantEntity)

    @Query("SELECT * FROM tenants WHERE id = :tenantId")
    fun observeTenant(tenantId: String): Flow<TenantEntity?>

    @Query("SELECT * FROM offers WHERE tenantId = :tenantId")
    fun observeOffers(tenantId: String): Flow<List<OfferEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffers(offers: List<OfferEntity>)

    @Query("DELETE FROM offers WHERE tenantId = :tenantId")
    suspend fun clearOffers(tenantId: String)

    /**
     * Ersetzt die Angebote eines Betriebs vollständig. In einer Transaktion,
     * damit die UI nie einen leeren Zwischenstand sieht.
     */
    @Transaction
    suspend fun replaceOffers(tenantId: String, offers: List<OfferEntity>) {
        clearOffers(tenantId)
        insertOffers(offers)
    }
}
