package com.dominikbaki.treuebiss.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.dominikbaki.treuebiss.feature_stamps.data.local.dao.StampDao
import com.dominikbaki.treuebiss.feature_stamps.data.local.entity.StampEntity
import com.dominikbaki.treuebiss.feature_vouchers.data.local.dao.VoucherDao
import com.dominikbaki.treuebiss.feature_vouchers.data.local.entity.VoucherEntity
/**
 * Die zentrale Room-Datenbank-Klasse für die App.
 * Definiert die Tabellen (Entities) und stellt die DAOs bereit.
 */
@Database(
    entities = [StampEntity::class, VoucherEntity::class],
    version = 1,
    exportSchema = true
)
abstract class TreueBissDatabase : RoomDatabase() {
    abstract fun stampDao(): StampDao
    abstract fun voucherDao(): VoucherDao

    companion object {
        const val DATABASE_NAME = "treuebiss_db"
    }
}