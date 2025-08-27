package com.dominikbaki.treuebiss.core.di

import android.app.Application
import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * Stellt den globalen App-Kontext bereit.
     * Nützlich für den Zugriff auf Ressourcen, SharedPreferences, etc.
     * Hilt stellt die "Application" automatisch bereit, wir müssen sie nur weiterreichen.
     */
    @Provides
    @Singleton
    fun provideContext(app: Application): Context {
        return app.applicationContext
    }

    // Hier werden später weitere globale Abhängigkeiten wie Retrofit,
    // SupabaseClient oder die Room-Datenbank bereitgestellt.
    // Beispiel:
    // @Provides
    // @Singleton
    // fun provideSupabaseClient(): SupabaseClient { ... }
}