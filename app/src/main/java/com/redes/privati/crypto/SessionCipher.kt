package com.redes.privati.crypto

import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrador AES-256-GCM para una dirección de comunicación (un canal TCP).
 *
 * ## Esquema de nonce (12 bytes = 96 bits, requerido por GCM)
 * ```
 * [0..3]  dirección (4 bytes fijos, distingue cliente→servidor de servidor→cliente)
 * [4..11] contador  (8 bytes big-endian, empieza en 0, se incrementa por trama)
 * ```
 * El nonce es **determinístico** y no se transmite en la red: ambos extremos lo
 * calculan independientemente gracias al orden garantizado de TCP.
 * Usar la misma clave+nonce dos veces en GCM es catastrófico (revela la clave),
 * por eso el contador nunca puede repetirse mientras dure la sesión.
 *
 * ## Formato en la red (cuerpo de la trama cifrada)
 * ```
 * [ciphertext][16 bytes GCM tag]
 * ```
 * El nonce no se incluye: el receptor lo recalcula con su propio contador.
 *
 * ## Uso por salto en sala de chat
 * Cada canal cliente↔host tiene su propia instancia con su propia clave de sesión
 * y sus propios contadores. El host descifra la trama entrante y la vuelve a cifrar
 * al reenviarla; así el host actúa como único punto de desencriptación entre saltos.
 *
 * @param sessionKey  Clave AES-256 de 32 bytes derivada con HKDF.
 * @param sendDir     4 bytes que identifican la dirección de envío de este extremo.
 * @param recvDir     4 bytes que identifican la dirección de envío del extremo remoto.
 */
class SessionCipher(
    private val sessionKey: ByteArray,
    private val sendDir: ByteArray,
    private val recvDir: ByteArray,
) {
    init {
        require(sessionKey.size == 32) { "La clave de sesión debe tener 32 bytes (AES-256)" }
        require(sendDir.size == 4 && recvDir.size == 4) { "Las direcciones deben tener 4 bytes" }
    }

    private val aesKey = SecretKeySpec(sessionKey, "AES")

    private var sendCounter = 0L
    private var recvCounter = 0L

    private val sendLock = Any()
    private val recvLock = Any()

    /**
     * Cifra [plaintext] con AES-256-GCM y devuelve `ciphertext + tag (16 B)`.
     * El nonce se construye con la dirección de envío y el contador actual.
     * Thread-safe: múltiples hilos pueden llamar a [encrypt] simultáneamente.
     *
     * @param plaintext Bytes a cifrar (cuerpo de la trama en claro).
     * @return Bytes cifrados con la etiqueta GCM concatenada.
     */
    fun encrypt(plaintext: ByteArray): ByteArray = synchronized(sendLock) {
        val nonce = construirNonce(sendDir, sendCounter++)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, nonce))
        cipher.doFinal(plaintext)
    }

    /**
     * Descifra y autentica [ciphertext] con AES-256-GCM.
     * El nonce se construye con la dirección de recepción y el contador actual.
     *
     * @param ciphertext Bytes recibidos de la red (ciphertext + tag GCM de 16 B).
     * @return Plaintext original.
     * @throws SecurityException Si la autenticación falla (trama manipulada o contador erróneo).
     */
    fun decrypt(ciphertext: ByteArray): ByteArray = synchronized(recvLock) {
        val nonce = construirNonce(recvDir, recvCounter++)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, nonce))
        try {
            cipher.doFinal(ciphertext)
        } catch (e: AEADBadTagException) {
            // Contador desincronizado o trama modificada en tránsito; cerrar la conexión
            throw SecurityException("Fallo de autenticación AES-GCM en trama ${recvCounter - 1}", e)
        }
    }

    /** Construye un nonce GCM de 12 bytes: 4 de dirección + 8 de contador big-endian. */
    private fun construirNonce(dir: ByteArray, counter: Long): ByteArray {
        val nonce = ByteArray(12)
        System.arraycopy(dir, 0, nonce, 0, 4)
        ByteBuffer.wrap(nonce, 4, 8).putLong(counter)
        return nonce
    }

    companion object {
        /**
         * Bytes de dirección para el extremo cliente (sentido cliente → servidor).
         * Convención fija: ambos extremos acuerdan estas constantes sin negociación.
         */
        val DIR_CLIENTE: ByteArray = byteArrayOf(0, 0, 0, 1)

        /**
         * Bytes de dirección para el extremo servidor (sentido servidor → cliente).
         */
        val DIR_SERVIDOR: ByteArray = byteArrayOf(0, 0, 0, 2)
    }
}
