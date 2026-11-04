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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.redes.privati.PrivatiApplication
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.ui.screens.chat.ChatScreen
import com.redes.privati.ui.screens.chat.ChatViewModel
import com.redes.privati.ui.screens.chats.ChatsScreen
import com.redes.privati.ui.screens.chats.ChatsViewModel
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

            // Navegar al hub de chats cuando el servidor acepte la sesión
            LaunchedEffect(roomState.connectionStatus) {
                if (roomState.connectionStatus == ConnectionStatus.ONLINE) {
                    navController.navigate(Screen.Chats.route) {
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
                    // El host puede volver al panel con el botón Atrás desde Chats
                    navController.navigate(Screen.Chats.route)
                },
                onCloseRoom = {
                    vm.cerrarSala()
                    navController.popBackStack(Screen.Welcome.route, inclusive = false)
                },
            )
        }

        composable(Screen.Chats.route) {
            val vm: ChatsViewModel = viewModel(
                factory = ChatsViewModel.Factory(app.chatRepository),
            )
            ChatsScreen(
                viewModel = vm,
                onOpenChat = { chatId ->
                    navController.navigate(Screen.Conversation.route(chatId))
                },
                onBackToPanel = {
                    // Solo el host llega aquí; vuelve al panel
                    navController.popBackStack()
                },
                onDisconnect = {
                    navController.popBackStack(Screen.Welcome.route, inclusive = false)
                },
            )
        }

        composable(
            route = Screen.Conversation.route,
            arguments = listOf(
                navArgument(Screen.Conversation.ARG_CHAT_ID) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments
                ?.getString(Screen.Conversation.ARG_CHAT_ID) ?: "sala"

            // Nombre del chat leído del repositorio en tiempo real
            val chatsActuales by app.chatRepository.chats.collectAsState()
            val chatNombre = chatsActuales.firstOrNull { it.chatId == chatId }?.nombre
                ?: if (chatId == "sala") "Chat general" else chatId

            val vm: ChatViewModel = viewModel(
                key = "chat_$chatId",
                factory = ChatViewModel.Factory(app.chatRepository, chatId),
            )
            ChatScreen(
                viewModel = vm,
                chatId = chatId,
                chatNombre = chatNombre,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
