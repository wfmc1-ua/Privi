package com.redes.privati.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.redes.privati.PrivatiApplication
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.ui.screens.chat.ChatScreen
import com.redes.privati.ui.screens.chat.ChatViewModel
import com.redes.privati.ui.screens.host.HostPanelScreen
import com.redes.privati.ui.screens.host.HostViewModel
import com.redes.privati.ui.screens.join.JoinScreen
import com.redes.privati.ui.screens.join.JoinViewModel
import com.redes.privati.ui.screens.welcome.WelcomeScreen

/**
 * Grafo de navegación de Privati.
 *
 * El nombre del usuario se captura en WelcomeScreen y se pasa a los ViewModels
 * de HostPanel y JoinScreen mediante factories manuales.
 *
 * @param navController Controlador de navegación; se crea uno nuevo por defecto.
 */
@Composable
fun PrivatiNavGraph(
    navController: NavHostController = rememberNavController(),
) {
    val app = LocalContext.current.applicationContext as PrivatiApplication

    // Nombre elegido en WelcomeScreen; se persiste a nivel de grafo para
    // poder pasarlo a los ViewModels al navegar.
    var userName by remember { mutableStateOf("") }

    NavHost(
        navController = navController,
        startDestination = Screen.Welcome.route,
    ) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onCreateRoom = { name ->
                    userName = name
                    navController.navigate(Screen.HostPanel.route)
                },
                onJoinRoom = { name ->
                    userName = name
                    navController.navigate(Screen.Join.route)
                },
            )
        }

        composable(Screen.Join.route) {
            val vm: JoinViewModel = viewModel(
                factory = JoinViewModel.Factory(userName, app.chatRepository),
            )
            val roomState by vm.roomState.collectAsState()

            // Navegar al chat cuando el servidor acepte la sesión
            LaunchedEffect(roomState.connectionStatus) {
                if (roomState.connectionStatus == ConnectionStatus.ONLINE) {
                    navController.navigate(Screen.Chat.route) {
                        // Eliminar JoinScreen del back stack: no tiene sentido volver aquí
                        popUpTo(Screen.Join.route) { inclusive = true }
                    }
                }
            }

            JoinScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Screen.HostPanel.route) {
            val vm: HostViewModel = viewModel(
                factory = HostViewModel.Factory(userName, app.chatRepository),
            )

            HostPanelScreen(
                viewModel = vm,
                onGoToChat = {
                    navController.navigate(Screen.Chat.route) {
                        // El host ya no necesita volver al panel durante el chat
                        popUpTo(Screen.HostPanel.route) { inclusive = true }
                    }
                },
                onCloseRoom = {
                    vm.cerrarSala()
                    navController.popBackStack(Screen.Welcome.route, inclusive = false)
                },
            )
        }

        composable(Screen.Chat.route) {
            val vm: ChatViewModel = viewModel(
                factory = ChatViewModel.Factory(app.chatRepository),
            )
            ChatScreen(
                viewModel = vm,
                onBack = {
                    vm.desconectar()
                    navController.popBackStack(Screen.Welcome.route, inclusive = false)
                },
            )
        }
    }
}
