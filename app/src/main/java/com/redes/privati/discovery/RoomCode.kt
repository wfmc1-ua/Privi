package com.redes.privati.discovery

/**
 * Utilidades para el código de sala de 4 caracteres.
 *
 * El alfabeto excluye caracteres visualmente ambiguos:
 * - '0' y 'O' (cero y letra O)
 * - '1', 'I' y 'L' (uno y letras I, L)
 *
 * Esto facilita la transcripción verbal o escrita del código entre usuarios.
 */
object RoomCode {

    /** Alfabeto de 32 caracteres sin ambigüedad visual. */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Longitud fija del código. */
    const val LENGTH = 4

    /**
     * Genera un código de sala aleatorio de [LENGTH] caracteres.
     */
    fun generar(): String = (1..LENGTH).map { ALPHABET.random() }.joinToString("")

    /**
     * Comprueba si [codigo] tiene exactamente [LENGTH] caracteres del [ALPHABET].
     * No distingue mayúsculas de minúsculas.
     *
     * @param codigo Cadena a validar.
     * @return `true` si el código es válido.
     */
    fun esValido(codigo: String): Boolean =
        codigo.length == LENGTH && codigo.uppercase().all { it in ALPHABET }
}
