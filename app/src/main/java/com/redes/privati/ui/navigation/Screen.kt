package com.redes.privati.ui.navigation

/**
 * Rutas de navegación de la app Privati.
 * Cada objeto encapsula el nombre de la ruta para usarse con Navigation Compose.
 */
sealed class Screen(val route: String) {
    /** Pantalla de bienvenida: elegir nombre y acción (crear/unirse). */
    object Welcome : Screen("welcome")

    /** Pantalla para unirse a una sala por NFC, código o IP manual. */
    object Join : Screen("join")

    /** Panel del host: código de sala, participantes y gestión de la sesión. */
    object HostPanel : Screen("host_panel")

    /** Hilo de conversación activo. */
    object Chat : Screen("chat")
}
