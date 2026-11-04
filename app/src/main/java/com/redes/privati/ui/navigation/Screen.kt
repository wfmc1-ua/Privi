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

    /** Hub principal de la sala: lista de chats, contactos y ajustes. */
    object Chats : Screen("chats")

    /** Hilo de conversación para el chat identificado por chatId. */
    object Conversation : Screen("conversation/{chatId}") {
        fun route(chatId: String) = "conversation/$chatId"
        const val ARG_CHAT_ID = "chatId"
    }
}
