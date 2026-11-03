package com.redes.privati

import android.app.Application

/**
 * Clase Application de Privati.
 * Contenedor de los servicios de red con ámbito de aplicación para que sobrevivan
 * a la rotación de pantalla. Los servicios reales (ChatServer, ChatClient)
 * se instanciarán aquí a partir de la Fase 1.
 */
class PrivatiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
