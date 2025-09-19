package com.dominikbaki.treuebiss.core.di

import com.dominikbaki.treuebiss.core.navigation.Screen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Dieses Modul registriert die gesamte Hierarchie unseres [Screen]-Interfaces.
 * Es ist absolut notwendig, damit kotlinx.serialization weiß, wie es
 * einen String (z.B. "home") in ein konkretes Objekt (z.B. Screen.Home) umwandeln kann.
 *
 * Das Gradle-Plugin für Serialization findet dieses Modul automatisch im Classpath
 * und verwendet es für Operationen wie toRoute().
 */
val appSerializers = SerializersModule {
    polymorphic(Screen::class) {
        subclass(Screen.Onboarding::class)
        subclass(Screen.Home::class)
        subclass(Screen.StampCard::class)
        subclass(Screen.Voucher::class)
        subclass(Screen.Weather::class)
        subclass(Screen.Settings::class)
    }
}