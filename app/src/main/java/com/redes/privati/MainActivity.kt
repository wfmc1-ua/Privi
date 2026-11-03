package com.redes.privati

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.redes.privati.ui.navigation.PrivatiNavGraph
import com.redes.privati.ui.theme.PrivatiTheme

/** Actividad única de Privati. Monta el grafo de navegación dentro del tema. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivatiTheme {
                PrivatiNavGraph()
            }
        }
    }
}
