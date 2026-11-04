package com.redes.privati

import android.app.Application
import com.redes.privati.data.ChatRepository

/**
 * Clase Application de Privati.
 * Punto de entrada del proceso. El [ChatRepository] vive aquí para sobrevivir
 * a la rotación de pantalla y a la navegación entre destinos.
 */
class PrivatiApplication : Application() {

    /** Instancia única del repositorio; se crea la primera vez que se accede. */
    val chatRepository: ChatRepository by lazy { ChatRepository(this) }

    override fun onCreate() {
        super.onCreate()
    }
}
