package com.dominikbaki.treuebiss.core.di

import com.dominikbaki.treuebiss.core.data.repository.UserPreferencesRepositoryImpl
import com.dominikbaki.treuebiss.feature_stamps.data.repository.StampRepositoryImpl
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.UserPreferencesRepository
import com.dominikbaki.treuebiss.feature_vouchers.data.repository.VoucherRepositoryImpl
import com.dominikbaki.treuebiss.feature_vouchers.domain.repository.VoucherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-Modul, das die Repository-Implementierungen an ihre Interfaces bindet.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStampRepository(
        stampRepositoryImpl: StampRepositoryImpl
    ): StampRepository

    @Binds
    @Singleton
    abstract fun bindVoucherRepository(
        voucherRepositoryImpl: VoucherRepositoryImpl
    ): VoucherRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        userPreferencesRepositoryImpl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository
}