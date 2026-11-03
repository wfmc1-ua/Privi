package com.redes.privati.ui.screens.join

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.redes.privati.ui.components.ButtonVariant
import com.redes.privati.ui.components.NfcWaves
import com.redes.privati.ui.components.PrivatiButton
import com.redes.privati.ui.components.PrivatiTextField
import com.redes.privati.ui.components.PrivatiTopBar
import com.redes.privati.ui.components.RoomCodeInput
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Pantalla para unirse a una sala existente.
 * Ofrece tres métodos: NFC (acercar móviles), código de 4 caracteres
 * y conexión manual por IP:puerto.
 *
 * @param onConnected Navegación a la conversación tras conectar con éxito.
 * @param onBack Navegación de retroceso.
 */
@Composable
fun JoinScreen(
    onConnected: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val colors = PrivatiTheme.colors
    var code by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var ipError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            PrivatiTopBar(
                title = "Unirme a una sala",
                onNavigateBack = onBack,
            )
        },
        containerColor = colors.surface,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = PrivatiSpacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(PrivatiSpacing.xl))

            // Sección NFC
            Text(
                text = "Acerca tu móvil al del anfitrión",
                style = PrivatiTypography.heading,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "NFC activado · esperando…",
                style = PrivatiTypography.caption,
                color = colors.inkMuted,
            )

            Spacer(Modifier.height(PrivatiSpacing.lg))

            NfcWaves(isActive = true)

            Spacer(Modifier.height(PrivatiSpacing.xl))

            Text(
                text = "o escribe el código de sala",
                style = PrivatiTypography.label,
                color = colors.inkMuted,
            )

            Spacer(Modifier.height(PrivatiSpacing.md))

            RoomCodeInput(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(PrivatiSpacing.lg))

            // Enlace a conexión manual por IP
            TextButton(onClick = { showManual = !showManual }) {
                Text(
                    text = if (showManual) "Ocultar conexión por IP" else "Conectar por IP",
                    style = PrivatiTypography.label,
                    color = colors.primary,
                )
            }

            AnimatedVisibility(visible = showManual) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PrivatiTextField(
                        value = ip,
                        onValueChange = { ip = it; ipError = false },
                        placeholder = "192.168.1.100",
                        label = "Dirección IP del anfitrión",
                        isError = ipError,
                        errorMessage = "Formato de IP no válido",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    PrivatiTextField(
                        value = port,
                        onValueChange = { port = it },
                        placeholder = "9000",
                        label = "Puerto",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            PrivatiButton(
                text = "Conectar",
                onClick = onConnected,
                variant = ButtonVariant.Primary,
                enabled = code.length == 4 || (showManual && ip.isNotBlank()),
            )

            Spacer(Modifier.height(PrivatiSpacing.lg))
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinScreenPreview() {
    PrivatiTheme {
        JoinScreen()
    }
}
