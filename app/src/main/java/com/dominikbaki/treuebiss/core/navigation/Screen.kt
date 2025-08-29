package com.dominikbaki.treuebiss.core.navigation

import kotlinx.serialization.Serializable
/**
 * Definiert alle Navigationsziele als serialisierbare Objekte.
 * Anstelle von fehleranfälligen Strings wird das Objekt selbst zur Route.
 */
@Serializable
sealed class Screen {
    @Serializable
    data object Onboarding : Screen()
    @Serializable
    data object Home : Screen()
    @Serializable
    data object StampCard : Screen()
    @Serializable
    data object Voucher : Screen()
    @Serializable
    data object Weather : Screen()
    @Serializable
    data object Settings : Screen()
}