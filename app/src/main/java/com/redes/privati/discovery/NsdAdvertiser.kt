package com.redes.privati.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Anuncia la sala de chat mediante NSD/mDNS (equivalente a Bonjour/Zeroconf).
 *
 * El nombre del servicio tiene el formato "Corro-XXXX", donde XXXX es el código
 * de sala de 4 caracteres. Android puede añadir un sufijo para resolver conflictos
 * de nombre (p. ej. "Corro-X7KP (1)"); el [NsdBrowser] maneja este caso.
 *
 * Ciclo de vida esperado:
 * 1. Llamar a [start] justo después de que el servidor TCP comience a escuchar.
 * 2. Llamar a [stop] al cerrar la sala o destruir el contexto de aplicación.
 *
 * @param context Contexto de aplicación para obtener el [NsdManager].
 */
class NsdAdvertiser(private val context: Context) {

    companion object {
        /** Tipo de servicio mDNS registrado para Corro. */
        const val TIPO_SERVICIO = "_corro._tcp"

        /** Prefijo del nombre de servicio; seguido por el código de sala. */
        const val PREFIJO_NOMBRE = "Corro-"

        private const val TAG = "NsdAdvertiser"
    }

    private val nsdManager: NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    @Volatile
    private var registrationListener: NsdManager.RegistrationListener? = null

    /**
     * Registra el servicio NSD con el [codigoSala] y [puerto] indicados.
     *
     * Si el registro falla (NSD no disponible, permisos…), se ignora sin lanzar
     * excepción para que la app pueda seguir funcionando por IP manual.
     *
     * @param codigoSala Código de 4 caracteres de la sala (p. ej. "X7KP").
     * @param puerto     Puerto TCP en que escucha el servidor.
     */
    fun start(codigoSala: String, puerto: Int) {
        if (registrationListener != null) return  // ya registrado

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$PREFIJO_NOMBRE$codigoSala"
            serviceType = TIPO_SERVICIO
            setPort(puerto)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(si: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Registro NSD fallido: código de error $errorCode")
                registrationListener = null
            }

            override fun onUnregistrationFailed(si: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Baja NSD fallida: código de error $errorCode")
            }

            override fun onServiceRegistered(si: NsdServiceInfo) {
                // Android puede haber renombrado el servicio para evitar conflictos
                Log.d(TAG, "Servicio NSD registrado como «${si.serviceName}»")
            }

            override fun onServiceUnregistered(si: NsdServiceInfo) {
                Log.d(TAG, "Servicio NSD dado de baja")
                registrationListener = null
            }
        }

        try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener!!,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar el servicio NSD", e)
            registrationListener = null
        }
    }

    /**
     * Cancela el anuncio NSD. Seguro llamarlo aunque [start] no haya sido invocado
     * o haya fallado. Siempre llamar al cerrar la sala.
     */
    fun stop() {
        registrationListener?.let { listener ->
            try {
                nsdManager.unregisterService(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error al dar de baja el servicio NSD", e)
                registrationListener = null
            }
        }
    }
}
