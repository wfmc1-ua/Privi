package com.redes.privati.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Cliente TCP de Privati.
 *
 * Responsabilidad mínima: crear un socket conectado al servidor.
 * Todo el protocolo de handshake (HELLO/WELCOME) queda en [ChatRepository].
 */
class ChatClient {

    /**
     * Conecta al servidor en [ip]:[port] con un timeout de [timeoutMs] ms.
     * Suspende en [Dispatchers.IO] hasta que la conexión se establece o se agota el tiempo.
     *
     * @param ip Dirección IPv4 del servidor en la red local.
     * @param port Puerto TCP del servidor.
     * @param timeoutMs Tiempo máximo de espera para la conexión en milisegundos.
     * @return [Socket] ya conectado, listo para crear un [Connection].
     * @throws java.net.SocketTimeoutException si se supera [timeoutMs].
     * @throws java.io.IOException si la conexión es rechazada o hay error de red.
     */
    suspend fun conectar(ip: String, port: Int, timeoutMs: Int = 5_000): Socket =
        withContext(Dispatchers.IO) {
            Socket().apply {
                connect(InetSocketAddress(ip, port), timeoutMs)
            }
        }
}
