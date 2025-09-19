package com.dominikbaki.treuebiss.core.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
/**
 * Definiert alle Navigationsziele als serialisierbare Objekte.
 * Anstelle von fehleranfälligen Strings wird das Objekt selbst zur Route.
 */
@Serializable
sealed interface Screen {
    @Serializable
    @SerialName("onboarding")
    data object Onboarding : Screen
    @Serializable
    @SerialName("home")
    data object Home : Screen
    @Serializable
    @SerialName("stampCard")
    data class StampCard(val cardId: String) : Screen
    @Serializable
    @SerialName("voucher")
    data class Voucher(val voucherId: String) : Screen
    @Serializable
    @SerialName("weather")
    data object Weather : Screen
    @Serializable
    @SerialName("settings")
    data object Settings : Screen
}