package com.dominikbaki.treuebiss.core.di

import android.content.Context
import androidx.room.Room
import com.dominikbaki.treuebiss.core.data.local.TreueBissDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-Modul, das die Datenbank und die DAOs als Singletons bereitstellt.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    /**
     * Stellt eine Singleton-Instanz der Room-Datenbank bereit.
     */
    @Provides
    @Singleton
    fun provideTreueBissDatabase(@ApplicationContext context: Context) =
        Room.databaseBuilder(
            context,
            TreueBissDatabase::class.java,
            TreueBissDatabase.DATABASE_NAME
        )
            .build()

    /**
     * Stellt das StampDao bereit, das von der Datenbankinstanz abhängt.
     */
    @Provides
    @Singleton
    fun provideStampDao(database: TreueBissDatabase) = database.stampDao()

    /**
     * Stellt das VoucherDao bereit, das von der Datenbankinstanz abhängt.
     */
    @Provides
    @Singleton
    fun provideVoucherDao(database: TreueBissDatabase) = database.voucherDao()
}