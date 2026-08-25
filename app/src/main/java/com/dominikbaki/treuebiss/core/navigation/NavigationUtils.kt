package com.dominikbaki.treuebiss.core.navigation

/**
 * Wandelt einen rohen Routen-String (z.B. aus einem BackStackEntry)
 * in ein typisiertes Screen-Objekt um.
 *
 * Dient als Fallback, wenn `toRoute<Screen>()` die Route nicht auflösen kann.
 *
 * @param route Der Routen-String, z.B. "stampCard" oder "home".
 * @return Das passende [Screen]-Objekt oder `null`, wenn keine Übereinstimmung gefunden wurde.
 */
fun mapRouteToScreen(route: String?): Screen? {
    if (route == null) return null
    return Screen.allScreens.find { screen -> route.startsWith(screen.routeBase) }
}
