package com.dominikbaki.treuebiss.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dominikbaki.treuebiss.feature_stamps.data.local.dao.StampDao
import com.dominikbaki.treuebiss.feature_stamps.data.local.entity.StampEntity
import com.dominikbaki.treuebiss.feature_tenant.data.local.dao.TenantDao
import com.dominikbaki.treuebiss.feature_tenant.data.local.entity.OfferEntity
import com.dominikbaki.treuebiss.feature_tenant.data.local.entity.TenantEntity
import com.dominikbaki.treuebiss.feature_vouchers.data.local.dao.VoucherDao
import com.dominikbaki.treuebiss.feature_vouchers.data.local.entity.VoucherEntity

/**
 * Die zentrale Room-Datenbank-Klasse für die App.
 *
 * Version 2 führt die Mandantenfähigkeit ein: Stempel und Gutscheine tragen
 * eine `tenantId`, dazu kommen die Zwischenspeicher für Betrieb und Angebote.
 */
@Database(
    entities = [
        StampEntity::class,
        VoucherEntity::class,
        TenantEntity::class,
        OfferEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class TreueBissDatabase : RoomDatabase() {
    abstract fun stampDao(): StampDao
    abstract fun voucherDao(): VoucherDao
    abstract fun tenantDao(): TenantDao

    companion object {
        const val DATABASE_NAME = "treuebiss_db"
    }
}
