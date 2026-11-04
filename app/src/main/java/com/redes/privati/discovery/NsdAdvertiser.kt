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

/**
 * Anuncia la sala de chat mediante NSD/mDNS (equivalente a Bonjour/Zeroconf).
 *
 * El nombre del servicio tiene el formato "Corro-XXXX", donde XXXX es el código
 * de sala de 4 caracteres. Incluye atributos TXT: code, name, host, n para que
 * los clientes puedan listar salas sin necesidad de conocer el código de antemano.
 *
 * @param context Contexto de aplicación para obtener el [NsdManager].
 */
class NsdAdvertiser(private val context: Context) {

    companion object {
        const val TIPO_SERVICIO = "_corro._tcp"
        const val PREFIJO_NOMBRE = "Corro-"
        private const val TAG = "NsdAdvertiser"
    }

    private val nsdManager: NsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null

    @Volatile private var codigoSalaGuardado: String? = null
    @Volatile private var puertoGuardado: Int = 0

    /**
     * Registra el servicio NSD con atributos TXT (code, name, host, n).
     *
     * @param codigoSala   Código de 4 caracteres de la sala.
     * @param puerto       Puerto TCP en que escucha el servidor.
     * @param nombreSala   Nombre visible de la sala (default = codigoSala).
     * @param nombreHost   Nombre del host para el atributo "host".
     * @param numConectados Número de participantes actuales para el atributo "n".
     */
    fun start(
        codigoSala: String,
        puerto: Int,
        nombreSala: String = codigoSala,
        nombreHost: String = "",
        numConectados: Int = 1,
    ) {
        if (registrationListener != null) return

        codigoSalaGuardado = codigoSala
        puertoGuardado = puerto

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$PREFIJO_NOMBRE$codigoSala"
            serviceType = TIPO_SERVICIO
            setPort(puerto)
            setAttribute("code", codigoSala)
            setAttribute("name", nombreSala)
            setAttribute("host", nombreHost)
            setAttribute("n", "$numConectados")
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
                Log.d(TAG, "Servicio NSD registrado como «${si.serviceName}»")
            }

            override fun onServiceUnregistered(si: NsdServiceInfo) {
                Log.d(TAG, "Servicio NSD dado de baja")
                registrationListener = null
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar el servicio NSD", e)
            registrationListener = null
        }
    }

    /**
     * Actualiza los atributos TXT re-registrando el servicio NSD.
     * Usado cuando cambia el nombre de la sala o el número de participantes.
     */
    fun actualizar(nombreSala: String, nombreHost: String, numConectados: Int) {
        val codigo = codigoSalaGuardado ?: return
        val puerto = puertoGuardado
        scope.launch {
            val listener = registrationListener
            if (listener != null) {
                registrationListener = null
                try { nsdManager.unregisterService(listener) } catch (_: Exception) {}
                delay(600L)
            }
            start(codigo, puerto, nombreSala, nombreHost, numConectados)
        }
    }

    /** Cancela el anuncio NSD. Seguro llamarlo aunque [start] no haya sido invocado. */
    fun stop() {
        registrationListener?.let { listener ->
            codigoSalaGuardado = null
            try {
                nsdManager.unregisterService(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error al dar de baja el servicio NSD", e)
                registrationListener = null
            }
        }
    }
}
