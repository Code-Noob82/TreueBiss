package com.dominikbaki.treuebiss.feature_vouchers.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.dominikbaki.treuebiss.feature_vouchers.data.local.entity.VoucherEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) für Gutscheine. Definiert die Datenbankoperationen.
 */
@Dao
interface VoucherDao {
    /**
     * Fügt einen neuen Gutschein in die Datenbank ein.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: VoucherEntity)

    /**
     * Fügt mehrere Gutscheine ein. Bestehende IDs werden ersetzt, damit die
     * Wiederherstellung vom Server wiederholbar ist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVouchers(vouchers: List<VoucherEntity>)

    /**
     * Beobachtet alle nicht eingelösten Gutscheine.
     */
    @Query(
        "SELECT * FROM vouchers WHERE tenantId = :tenantId AND isRedeemed = 0 " +
            "ORDER BY creationDate DESC"
    )
    fun observeOpenVouchers(tenantId: String): Flow<List<VoucherEntity>>

    /**
     * Markiert einen spezifischen Gutschein als eingelöst.
     */
    @Query("UPDATE vouchers SET isRedeemed = 1 WHERE id = :voucherId")
    suspend fun markAsRedeemed(voucherId: String)
}