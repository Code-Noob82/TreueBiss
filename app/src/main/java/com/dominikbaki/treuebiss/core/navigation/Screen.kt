package com.dominikbaki.treuebiss.core.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Definiert alle Navigationsziele als serialisierbare Objekte.
 * Anstelle von fehleranfälligen Strings wird das Objekt selbst zur Route.
 */
@Serializable
sealed interface Screen {
    /** Basis-Eigenschaft für den Routennamen, wird für den Abgleich benötigt. */
    val routeBase: String

    @Serializable
    @SerialName("onboarding")
    data object Onboarding : Screen {
        override val routeBase: String = "onboarding"
    }

    @Serializable
    @SerialName("home")
    data object Home : Screen {
        override val routeBase: String = "home"
    }

    @Serializable
    @SerialName("stampCard")
    data object StampCard : Screen {
        override val routeBase: String = "stampCard"
    }

    @Serializable
    @SerialName("voucher")
    data object Voucher : Screen {
        override val routeBase: String = "voucher"
    }

    @Serializable
    @SerialName("weather")
    data object Weather : Screen {
        override val routeBase: String = "weather"
    }

    @Serializable
    @SerialName("settings")
    data object Settings : Screen {
        override val routeBase: String = "settings"
    }

    companion object {
        val allScreens: List<Screen> = listOf(
            Onboarding,
            Home,
            StampCard,
            Voucher,
            Weather,
            Settings
        )
    }
}
