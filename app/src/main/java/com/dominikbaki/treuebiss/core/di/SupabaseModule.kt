package com.dominikbaki.treuebiss.core.di

import com.dominikbaki.treuebiss.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        // Die Keys werden jetzt sicher aus der BuildConfig-Datei geladen
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY

        return createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseAnonKey
        ) {
            install(Auth) {
                autoLoadFromStorage = true
            }
            install(Postgrest)
        }
    }
}