package com.dominikbaki.treuebiss.core.navigation

/**
 * Wandelt einen rohen Routen-String (z.B. aus einem BackStackEntry)
 * in ein typisiertes Screen-Objekt um.
 *
 * @param route Der Routen-String, z.B. "stampCard/123" oder "home".
 * @return Das passende [Screen]-Objekt oder `null`, wenn keine Übereinstimmung gefunden wurde.
 */
fun mapRouteToScreen(route: String?): Screen? {
    if (route == null) return null

    // Finde die passende Screen-Definition aus unserer Liste `allScreens`.
    val matchedScreen = Screen.allScreens.find { screen ->
        route.startsWith(screen.routeBase)
    }

    // Basierend auf dem gefundenen Typ, erstelle das finale Objekt mit den Argumenten.
    return when (matchedScreen) {
        is Screen.StampCard -> {
            val id = route.substringAfter("cardId=", "")
            Screen.StampCard(id)
        }
        is Screen.Voucher -> {
            val id = route.substringAfter("voucherId=", "")
            Screen.Voucher(id)
        }
        else -> matchedScreen
    }
}