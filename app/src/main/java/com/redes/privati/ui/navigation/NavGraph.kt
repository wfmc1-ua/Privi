package com.redes.privati.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.redes.privati.ui.screens.chat.ChatScreen
import com.redes.privati.ui.screens.host.HostPanelScreen
import com.redes.privati.ui.screens.join.JoinScreen
import com.redes.privati.ui.screens.welcome.WelcomeScreen

/**
 * Grafo de navegación de Privati.
 * Define las cuatro pantallas principales y sus transiciones.
 * En fases posteriores se añadirán los argumentos de ruta (nombre, IP, etc.).
 *
 * @param navController Controlador de navegación; se crea uno nuevo por defecto.
 */
@Composable
fun PrivatiNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route,
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onCreateRoom = { navController.navigate(Screen.HostPanel.route) },
                onJoinRoom = { navController.navigate(Screen.Join.route) },
            )
        }

        composable(Screen.Join.route) {
            JoinScreen(
                onConnected = { navController.navigate(Screen.Chat.route) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.HostPanel.route) {
            HostPanelScreen(
                onGoToChat = { navController.navigate(Screen.Chat.route) },
                onCloseRoom = { navController.popBackStack(Screen.Welcome.route, inclusive = false) },
            )
        }

        composable(Screen.Chat.route) {
            ChatScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
