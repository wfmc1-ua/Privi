package com.redes.privati.crypto

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implementación de HKDF-SHA256 (RFC 5869).
 *
 * Se usa para derivar la clave de sesión AES-256 a partir del secreto ECDH compartido.
 * El parámetro [salt] liga la clave al código de sala, de modo que un atacante que
 * intercepte el intercambio ECDH pero no conozca el código no puede derivar la clave.
 */
object Hkdf {

    private const val HMAC_ALG = "HmacSHA256"

    /**
     * HKDF-Extract: produce una «pseudorandom key» (PRK) a partir del [ikm] y el [salt].
     *
     * @param salt Bytes de sal (p. ej. el código de sala en UTF-8). Puede estar vacío.
     * @param ikm  Material de clave de entrada (el secreto ECDH compartido).
     * @return PRK de 32 bytes.
     */
    fun extract(salt: ByteArray, ikm: ByteArray): ByteArray {
        // Si la sal está vacía, se usa un bloque de ceros del tamaño del hash (RFC 5869 §2.2)
        val efectivaSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val mac = Mac.getInstance(HMAC_ALG)
        mac.init(SecretKeySpec(efectivaSalt, HMAC_ALG))
        return mac.doFinal(ikm)
    }

    /**
     * HKDF-Expand: extiende la [prk] hasta [length] bytes usando el contexto [info].
     *
     * @param prk    Clave pseudoaleatoria producida por [extract].
     * @param info   Cadena de contexto para distinguir usos de la misma PRK.
     * @param length Número de bytes a producir; máximo 255 × 32 = 8160.
     * @return Material de clave de [length] bytes.
     */
    fun expand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance(HMAC_ALG)
        val resultado = ByteArray(length)
        var t = ByteArray(0)
        var offset = 0
        var contador = 1

        while (offset < length) {
            mac.init(SecretKeySpec(prk, HMAC_ALG))
            mac.update(t)
            mac.update(info)
            mac.update(contador.toByte())
            t = mac.doFinal()
            val copiar = minOf(t.size, length - offset)
            System.arraycopy(t, 0, resultado, offset, copiar)
            offset += copiar
            contador++
        }
        return resultado
    }

    /**
     * Atajo que combina [extract] y [expand].
     *
     * @param ikm    Material de clave de entrada (secreto ECDH).
     * @param salt   Sal (código de sala en UTF-8).
     * @param info   Contexto de derivación (p. ej. "Corro session key v1").
     * @param length Longitud de la clave de salida en bytes (32 para AES-256).
     * @return Clave derivada de [length] bytes.
     */
    fun deriveKey(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val prk = extract(salt, ikm)
        return expand(prk, info, length)
    }
}
