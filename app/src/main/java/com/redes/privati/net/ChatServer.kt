package com.redes.privati.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

/**
 * Servidor TCP de Privati.
 *
 * A partir de la Fase 2 acepta múltiples clientes en bucle (hasta [maxClientes]).
 * Cada socket aceptado se entrega a [onClientSocket] sin bloquear el bucle de aceptación,
 * de modo que otros clientes pueden conectarse inmediatamente.
 *
 * @param port Puerto TCP en el que escuchar.
 * @param scope Ámbito de corrutinas del servidor; su cancelación detiene el bucle.
 * @param maxClientes Número máximo de clientes simultáneos (por defecto 8).
 * @param onClientSocket Callback llamado por cada cliente aceptado; recibe el socket conectado.
 */
class ChatServer(
    private val port: Int,
    private val scope: CoroutineScope,
    private val maxClientes: Int = 8,
    private val onClientSocket: (Socket) -> Unit,
) {
    private var serverSocket: ServerSocket? = null

    /** Número de clientes aceptados y aún activos (incrementado/decrementado desde el repositorio). */
    @Volatile var clientesActivos: Int = 0

    /**
     * Inicia el bucle de aceptación en [Dispatchers.IO].
     * Acepta hasta [maxClientes] sockets simultáneos. Cuando se alcanza el límite,
     * el siguiente socket se acepta y cierra inmediatamente con REJECT (lo hace el repositorio).
     */
    fun start() {
        scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                // Bucle de aceptación: continúa mientras la corrutina esté activa
                while (isActive) {
                    val socket = serverSocket!!.accept()
                    onClientSocket(socket)
                }
            } catch (e: IOException) {
                // El socket fue cerrado externamente (stop()) o hubo un error de red.
                // No relanzamos: es el comportamiento esperado al detener el servidor.
            } finally {
                cerrarServerSocket()
            }
        }
    }

    /** Detiene el servidor cerrando el [ServerSocket], lo que desbloquea accept(). */
    fun stop() {
        cerrarServerSocket()
    }

    private fun cerrarServerSocket() {
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null
    }
}
