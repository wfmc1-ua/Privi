package com.redes.privati.net

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Codificador/decodificador de tramas Privati.
 *
 * Formato de trama en la red:
 *   [4 bytes longitud big-endian][1 byte tipo][0..N bytes cuerpo]
 *
 * La longitud incluye el byte de tipo, por lo que siempre es ≥ 1.
 * Longitud máxima permitida: 2 MB. Si se supera, se cierra la conexión.
 */
object FrameCodec {

    /** Tamaño máximo del cuerpo + tipo: 2 MB. */
    private const val MAX_FRAME_BYTES = 2 * 1024 * 1024

    /**
     * Serializa [frame] y lo escribe en [out].
     * Llamar desde Dispatchers.IO; bloquea hasta que los bytes se transmiten.
     */
    fun write(out: OutputStream, frame: Frame) {
        val length = 1 + frame.body.size
        // Cabecera: 4 bytes de longitud en big-endian
        val cabecera = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(length).array()
        out.write(cabecera)
        // Tipo (1 byte)
        out.write(frame.type.id.toInt() and 0xFF)
        // Cuerpo (0..N bytes)
        if (frame.body.isNotEmpty()) out.write(frame.body)
        out.flush()
    }

    /**
     * Lee exactamente una trama de [input] y la devuelve.
     * Bloquea en la lectura; lanza [IOException] si la conexión se cierra o la
     * longitud supera el máximo permitido.
     */
    fun read(input: InputStream): Frame {
        // Leer los 4 bytes de longitud
        val lengthBytes = input.readExactly(4)
        val length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.BIG_ENDIAN).int

        if (length < 1 || length > MAX_FRAME_BYTES) {
            throw IOException("Longitud de trama inválida: $length bytes (máx. $MAX_FRAME_BYTES)")
        }

        // Primer byte: tipo de trama
        val typeId = input.readExactly(1)[0]
        val type = FrameType.fromId(typeId)

        // Resto: cuerpo de la trama
        val body = if (length > 1) input.readExactly(length - 1) else ByteArray(0)

        return Frame(type, body)
    }
}

/**
 * Extensión sobre [InputStream] que lee exactamente [n] bytes.
 * A diferencia de [InputStream.read], esta función hace reintentos hasta
 * completar el buffer, lo que es necesario con TCP (los datos pueden
 * llegar fragmentados).
 *
 * @throws EOFException si la conexión se cierra antes de leer [n] bytes.
 */
fun InputStream.readExactly(n: Int): ByteArray {
    val buf = ByteArray(n)
    var leidos = 0
    while (leidos < n) {
        val r = read(buf, leidos, n - leidos)
        if (r == -1) throw EOFException("Conexión cerrada: esperaba $n bytes, leídos $leidos")
        leidos += r
    }
    return buf
}
