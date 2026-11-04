package com.redes.privati.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Genera un par de claves ECDH P-256 y deriva una [SessionCipher] a partir del
 * secreto compartido con el extremo remoto.
 *
 * ## Protocolo de acuerdo de claves
 * 1. Cada extremo crea su [Handshake] y comparte [publicKeyBytes] en el HELLO / WELCOME.
 * 2. Cada extremo llama a [deriveSessionCipher] con la clave pública recibida.
 * 3. ECDH produce el mismo secreto compartido en ambos extremos.
 * 4. HKDF-SHA256 convierte ese secreto en una clave AES-256, usando el código de sala
 *    como sal. Quien no conoce el código no puede derivar la clave aunque capture el tráfico.
 * 5. Los bytes de dirección (DIR_CLIENTE / DIR_SERVIDOR) separan los nonces de cada sentido.
 *
 * Esta instancia no es reutilizable: cada sesión TCP necesita un [Handshake] nuevo.
 *
 * ## Formato de la clave pública en la red
 * Se transmite como codificación X.509 DER en Base64 (sin saltos de línea).
 * Longitud: 91 bytes → ~124 caracteres Base64.
 */
class Handshake {

    private val keyPair = run {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))  // P-256
        kpg.generateKeyPair()
    }

    /**
     * Clave pública de este extremo en formato X.509 DER, lista para transmitirse.
     * Codificar en Base64 antes de incluirla en el JSON del HELLO / WELCOME.
     */
    fun publicKeyBytes(): ByteArray = keyPair.public.encoded

    /**
     * Realiza el acuerdo ECDH con la clave pública del extremo remoto ([peerKeyBytes])
     * y devuelve un [SessionCipher] listo para cifrar/descifrar tramas.
     *
     * @param peerKeyBytes Bytes X.509 DER de la clave pública recibida del remoto.
     * @param roomCode     Código de sala usado como sal del HKDF. Puede estar vacío si la
     *                     conexión es por IP manual; el cifrado sigue siendo válido.
     * @param isClient     `true` si este extremo es el cliente (envía como DIR_CLIENTE),
     *                     `false` si es el servidor (envía como DIR_SERVIDOR).
     * @return [SessionCipher] con la clave derivada y las direcciones de nonce asignadas.
     * @throws Exception Si la clave remota es inválida o el ECDH falla.
     */
    fun deriveSessionCipher(
        peerKeyBytes: ByteArray,
        roomCode: String,
        isClient: Boolean,
    ): SessionCipher {
        // Reconstruir la clave pública del remoto desde su codificación X.509 DER
        val peerPublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(peerKeyBytes))

        // ECDH: generar el secreto compartido
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(keyPair.private)
        ka.doPhase(peerPublicKey, true)
        val sharedSecret = ka.generateSecret()

        // HKDF-SHA256: ikm = secreto ECDH, salt = código de sala, info = identificador de protocolo
        val salt = roomCode.encodeToByteArray()
        val info = "Corro session key v1".encodeToByteArray()
        val sessionKey = Hkdf.deriveKey(sharedSecret, salt, info, 32)

        // El cliente envía con DIR_CLIENTE y recibe con DIR_SERVIDOR; el host a la inversa
        return if (isClient) {
            SessionCipher(
                sessionKey,
                sendDir = SessionCipher.DIR_CLIENTE,
                recvDir = SessionCipher.DIR_SERVIDOR,
            )
        } else {
            SessionCipher(
                sessionKey,
                sendDir = SessionCipher.DIR_SERVIDOR,
                recvDir = SessionCipher.DIR_CLIENTE,
            )
        }
    }
}
