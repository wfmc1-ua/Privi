package com.redes.privati.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Busca salas Corro anunciadas mediante NSD/mDNS filtrando por código de sala.
 *
 * Uso típico (desde una corrutina):
 * ```
 * val (ip, port) = browser.buscarSala("X7KP")
 * ```
 *
 * La función se suspende hasta encontrar la sala o hasta que la corrutina sea
 * cancelada. El descubrimiento y cualquier reintento pendiente se detienen
 * automáticamente al cancelar.
 *
 * **Limitación conocida:** NSD/mDNS usa multicast UDP (224.0.0.251). El emulador
 * Android corre detrás de NAT virtual y no recibe ese tráfico. Esta función
 * solo funciona entre dos dispositivos físicos en la misma red Wi-Fi.
 *
 * @param context Contexto de aplicación para obtener el [NsdManager].
 */
class NsdBrowser(context: Context) {

    companion object {
        private const val TAG = "NsdBrowser"

        /** Intentos máximos de resolución por servicio encontrado. */
        private const val MAX_INTENTOS_RESOLVE = 3

        /** Tiempo de espera entre reintentos de resolución, en ms. */
        private const val RETRASO_RESOLVE_MS = 1_000L
    }

    private val nsdManager: NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * Ámbito propio para lanzar los retardos de reintento de resolución.
     * Vive mientras exista el NsdBrowser (ámbito de aplicación).
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Busca la sala con el [codigoSala] dado y devuelve `(ip, puerto)` al encontrarla.
     *
     * - Solo se tienen en cuenta servicios de tipo [NsdAdvertiser.TIPO_SERVICIO] cuyo nombre
     *   empieza por "[NsdAdvertiser.PREFIJO_NOMBRE][codigoSala]", sin importar mayúsculas.
     *   Android puede añadir un sufijo de desambiguación como " (1)": se ignora.
     * - Cuando `resolveService` falla (p. ej. FAILURE_ALREADY_ACTIVE = error 3 en Android < 12),
     *   se reintenta hasta [MAX_INTENTOS_RESOLVE] veces con un retardo de [RETRASO_RESOLVE_MS].
     * - Se rechazan las direcciones IPv6 link-local (`fe80::xxx%ifname`) porque
     *   `java.net.Socket` no acepta el scope ID; se prefiere IPv4.
     *
     * @param codigoSala Código de 4 caracteres a buscar (p. ej. "X7KP").
     * @return Par (ip:String, puerto:Int) del servidor encontrado.
     * @throws Exception Si la búsqueda no puede iniciarse o si se supera el tiempo de espera externo.
     */
    suspend fun buscarSala(codigoSala: String): Pair<String, Int> =
        suspendCancellableCoroutine { cont ->
            // Prefijo completo para comparar (p. ej. "CORRO-X7KP")
            val prefijoObjetivo = "${NsdAdvertiser.PREFIJO_NOMBRE}${codigoSala.uppercase()}"

            // Garantiza que stopServiceDiscovery solo se llama una vez
            val stopped = AtomicBoolean(false)

            // Evita lanzar dos resoluciones simultáneas (NsdManager permite solo una a la vez)
            val resolviendo = AtomicBoolean(false)

            var discoveryListener: NsdManager.DiscoveryListener? = null

            /**
             * Detiene el descubrimiento NSD de forma segura.
             * Despacha en [scope] para no llamar a [NsdManager.stopServiceDiscovery]
             * desde dentro de un callback NSD (evita posibles deadlocks en Android < 12).
             */
            fun detenerBusqueda() {
                if (stopped.compareAndSet(false, true)) {
                    val listener = discoveryListener
                    scope.launch {
                        if (listener != null) {
                            try {
                                nsdManager.stopServiceDiscovery(listener)
                            } catch (_: Exception) {}
                        }
                    }
                }
            }

            /**
             * Intenta resolver [serviceInfo] hasta [MAX_INTENTOS_RESOLVE] veces.
             * En cada intento fallido espera [RETRASO_RESOLVE_MS] antes de reintentar.
             * Los reintentos se anulan si la corrutina es cancelada o ya se detuvo la búsqueda.
             */
            fun resolverConRetry(serviceInfo: NsdServiceInfo, intento: Int = 1) {
                val resolveListener = object : NsdManager.ResolveListener {

                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Resolución fallida (${si.serviceName}), intento $intento/$MAX_INTENTOS_RESOLVE: error $errorCode")
                        if (intento < MAX_INTENTOS_RESOLVE && cont.isActive && !stopped.get()) {
                            // Reintentar tras un retardo; el delay requiere corrutina
                            scope.launch {
                                delay(RETRASO_RESOLVE_MS)
                                if (cont.isActive && !stopped.get()) {
                                    resolverConRetry(si, intento + 1)
                                } else {
                                    resolviendo.set(false)
                                }
                            }
                        } else {
                            // Agotados los intentos; permitir que onServiceFound inicie otra resolución
                            resolviendo.set(false)
                        }
                    }

                    override fun onServiceResolved(si: NsdServiceInfo) {
                        Log.d(TAG, "Servicio resuelto: ${si.host?.hostAddress}:${si.port}")

                        val ip = extraerIpUsable(si.host?.hostAddress)
                        if (ip == null) {
                            Log.w(TAG, "Dirección no utilizable: ${si.host?.hostAddress}")
                            resolviendo.set(false)
                            return
                        }

                        detenerBusqueda()
                        if (cont.isActive) cont.resume(Pair(ip, si.port))
                    }
                }

                try {
                    nsdManager.resolveService(serviceInfo, resolveListener)
                } catch (e: Exception) {
                    Log.e(TAG, "resolveService lanzó excepción: ${e.message}")
                    resolviendo.set(false)
                }
            }

            discoveryListener = object : NsdManager.DiscoveryListener {

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "Error al iniciar la búsqueda NSD: $errorCode")
                    if (cont.isActive) {
                        cont.resumeWithException(
                            Exception("No se pudo iniciar la búsqueda NSD (error $errorCode)")
                        )
                    }
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.w(TAG, "Error al detener la búsqueda NSD: $errorCode")
                }

                override fun onDiscoveryStarted(serviceType: String) {
                    Log.d(TAG, "Búsqueda NSD iniciada: $serviceType")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "Búsqueda NSD detenida")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Servicio encontrado: ${serviceInfo.serviceName}")

                    // Filtrar: debe ser un servicio Corro con el código buscado
                    // startsWith maneja el sufijo de desambiguación " (1)" que añade Android
                    if (!serviceInfo.serviceName.uppercase().startsWith(prefijoObjetivo)) return

                    // No lanzar una nueva resolución si ya hay una en curso
                    if (!resolviendo.compareAndSet(false, true)) return
                    if (!cont.isActive || stopped.get()) {
                        resolviendo.set(false)
                        return
                    }

                    resolverConRetry(serviceInfo)
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Servicio perdido: ${serviceInfo.serviceName}")
                }
            }

            // Detener descubrimiento y reintentos si la corrutina es cancelada externamente
            cont.invokeOnCancellation { detenerBusqueda() }

            try {
                nsdManager.discoverServices(
                    NsdAdvertiser.TIPO_SERVICIO,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error al llamar a discoverServices: ${e.message}", e)
                cont.resumeWithException(Exception("NSD no disponible: ${e.message}"))
            }
        }

    /**
     * Extrae una dirección IP utilizable para [java.net.Socket].
     *
     * Reglas:
     * - Acepta IPv4 (p. ej. "192.168.1.45")
     * - Rechaza IPv6 link-local con scope ID ('%') como "fe80::1%wlan0", porque
     *   Android no resuelve el scope de forma fiable y Socket lanzaría una excepción.
     * - Rechaza nulas o en blanco.
     *
     * @param rawAddress Cadena devuelta por [java.net.InetAddress.hostAddress], puede ser nula.
     * @return Dirección limpia lista para usar en Socket, o null si no es utilizable.
     */
    private fun extraerIpUsable(rawAddress: String?): String? {
        if (rawAddress.isNullOrBlank()) return null

        // IPv6 link-local con scope ID: "fe80::1%wlan0" → rechazar
        if (rawAddress.contains('%')) return null

        // IPv6 sin scope (p. ej. "::1") → aceptar solo si no es loopback
        // IPv4 → aceptar siempre si no es loopback
        if (rawAddress == "127.0.0.1" || rawAddress == "::1") return null

        return rawAddress
    }
}
