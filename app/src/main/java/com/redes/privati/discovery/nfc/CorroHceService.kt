package com.redes.privati.discovery.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * Servicio HCE (Host Card Emulation) que expone los datos de sala vía NFC.
 *
 * Mientras el host tiene el panel abierto, [salaActiva] contiene la cadena
 * "ip:puerto:codigo". Cuando un cliente acerca el móvil, el sistema Android
 * enruta el SELECT AID a este servicio y devuelve esa cadena + SW 90 00.
 * Si no hay sala activa se devuelve SW 6A 82 (File Not Found).
 *
 * AID propietario: F0 43 4F 52 52 4F 31 ("F0CORRO1").
 * El prefijo F0 pertenece al rango libre para AIDs propietarios (ISO 7816-5).
 * Debe coincidir exactamente con el valor declarado en res/xml/apduservice.xml.
 *
 * El ciclo de vida del servicio lo gestiona Android: arranca al detectar el lector
 * y se destruye al alejarse. La app no lo inicia ni para manualmente.
 */
class CorroHceService : HostApduService() {

    companion object {
        /** AID propietario de Corro (7 bytes). */
        val AID_CORRO: ByteArray = byteArrayOf(
            0xF0.toByte(), 0x43, 0x4F, 0x52, 0x52, 0x4F, 0x31,
        )

        // Respuesta ISO 7816 de éxito
        private val SW_OK = byteArrayOf(0x90.toByte(), 0x00)

        // Respuesta ISO 7816 de «fichero no encontrado» (sin sala activa)
        private val SW_NOT_FOUND = byteArrayOf(0x6A.toByte(), 0x82.toByte())

        /**
         * Cadena "ip:puerto:codigo" disponible mientras el panel del host está abierto.
         * El host panel la establece en [DisposableEffect] y la borra al salir.
         * Null = no hay sala activa; el lector recibirá SW_NOT_FOUND.
         */
        @Volatile
        var salaActiva: String? = null

        /**
         * Construye el APDU de SELECT FILE (by name) estándar para [AID_CORRO].
         * Formato: 00 A4 04 00 [Lc=7] [AID 7 bytes] 00
         */
        fun construirSelectApdu(): ByteArray = byteArrayOf(
            0x00,                    // CLA
            0xA4.toByte(),           // INS: SELECT FILE
            0x04,                    // P1: selección por nombre (AID)
            0x00,                    // P2: primera o única ocurrencia
            AID_CORRO.size.toByte(), // Lc: longitud del AID
        ) + AID_CORRO + byteArrayOf(0x00)  // Le: longitud esperada de respuesta (any)

        /**
         * Comprueba que [respuesta] termina con los bytes de estado de éxito (90 00).
         * Los dos últimos bytes del APDU de respuesta siempre son el Status Word.
         */
        fun esRespuestaOk(respuesta: ByteArray): Boolean =
            respuesta.size >= 2 &&
                respuesta[respuesta.size - 2] == 0x90.toByte() &&
                respuesta[respuesta.size - 1] == 0x00.toByte()
    }

    /**
     * El lector NFC ha enviado un APDU de comando.
     * Solo respondemos a SELECT FILE (by name); cualquier otro comando es un error.
     * Esta función se llama en un hilo de fondo del framework NFC.
     */
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        if (!esSelectPorNombre(commandApdu)) return SW_NOT_FOUND
        val sala = salaActiva ?: return SW_NOT_FOUND
        return sala.encodeToByteArray() + SW_OK
    }

    override fun onDeactivated(reason: Int) {
        // No se requiere acción al desactivarse el campo NFC
    }

    // Verifica CLA=00, INS=A4 (SELECT), P1=04 (by name)
    private fun esSelectPorNombre(apdu: ByteArray): Boolean =
        apdu.size >= 4 &&
            apdu[0] == 0x00.toByte() &&
            apdu[1] == 0xA4.toByte() &&
            apdu[2] == 0x04.toByte()
}
