package com.redes.privati.ui.screens.join

import android.app.Activity
import android.content.res.Configuration
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.redes.privati.data.ConnectionStatus
import com.redes.privati.discovery.nfc.CorroHceService
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
 *
 * Ofrece tres caminos:
 * 1. **NFC** (si está disponible): acercar el móvil al host para leer ip:puerto:codigo
 *    de forma automática; se usa [NfcAdapter.enableReaderMode] + [IsoDep].
 * 2. **Código de sala**: 4 caracteres → búsqueda NSD/mDNS automática.
 * 3. **IP manual**: fallback con validación de IP y puerto.
 *
 * La sección NFC se oculta completamente si el hardware no está disponible
 * o si el NFC está desactivado en Ajustes del dispositivo.
 *
 * @param viewModel ViewModel que gestiona la conexión TCP.
 * @param onBack Navegación de retroceso.
 */
@Composable
fun JoinScreen(
    viewModel: JoinViewModel,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val colors = PrivatiTheme.colors
    val roomState by viewModel.roomState.collectAsState()
    val conectando by viewModel.conectando.collectAsState()
    val buscandoPorCodigo by viewModel.buscandoPorCodigo.collectAsState()

    val ocupado = conectando || buscandoPorCodigo

    var code by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("9000") }
    var ipError by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // ── NFC ──────────────────────────────────────────────────────────────────
    // Comprobar si el hardware NFC existe y está activado. Se evalúa una vez:
    // si el usuario desactiva NFC mientras la pantalla está abierta, seguirá
    // mostrando la sección, pero enableReaderMode simplemente no llamará al callback.
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val nfcDisponible = remember { nfcAdapter != null && nfcAdapter.isEnabled }

    if (nfcDisponible) {
        // Habilitar el modo lector NFC mientras esta pantalla está en composición.
        // onDispose desactiva el modo al navegar a otra pantalla.
        DisposableEffect(Unit) {
            val activity = context as? Activity
            if (activity != null) {
                nfcAdapter!!.enableReaderMode(
                    activity,
                    { tag ->
                        // Este callback corre en un hilo de fondo del framework NFC.
                        // IsoDep.transceive() es bloqueante: OK en hilo de fondo.
                        val isoDep = IsoDep.get(tag) ?: return@enableReaderMode
                        try {
                            isoDep.connect()
                            val selectApdu = CorroHceService.construirSelectApdu()
                            val respuesta = isoDep.transceive(selectApdu)
                            if (!CorroHceService.esRespuestaOk(respuesta)) return@enableReaderMode
                            // Quitar los 2 bytes de Status Word y decodificar "ip:puerto:codigo"
                            val datos = respuesta.copyOf(respuesta.size - 2).decodeToString()
                            val partes = datos.split(":")
                            if (partes.size < 3) return@enableReaderMode
                            val ipNfc = partes[0]
                            val puertoNfc = partes[1].toIntOrNull() ?: return@enableReaderMode
                            val codigoNfc = partes[2]
                            // conectarViaNfc() lanza una corrutina en viewModelScope (hilo principal)
                            viewModel.conectarViaNfc(ipNfc, puertoNfc, codigoNfc)
                        } catch (_: Exception) {
                            // Error de comunicación NFC: ignorar; el usuario puede volver a acercar
                        } finally {
                            try { isoDep.close() } catch (_: Exception) {}
                        }
                    },
                    NfcAdapter.FLAG_READER_NFC_A or
                        NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                    null,
                )
            }
            onDispose {
                try {
                    nfcAdapter!!.disableReaderMode(context as? Activity ?: return@onDispose)
                } catch (_: Exception) {}
            }
        }
    }

    // Mostrar errores de red como Snackbar
    LaunchedEffect(Unit) {
        viewModel.error.collect { mensaje ->
            snackbarHostState.showSnackbar(mensaje)
        }
    }

    Scaffold(
        topBar = {
            PrivatiTopBar(
                title = "Unirme a una sala",
                onNavigateBack = onBack,
            )
        },
        containerColor = colors.surface,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colors.dangerTint,
                    contentColor = colors.dangerInk,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = PrivatiSpacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(PrivatiSpacing.xl))

            if (nfcDisponible) {
                // ── Sección NFC (solo si el hardware está disponible y activo) ──
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
            } else {
                // ── Sin NFC: ir directo al código ──────────────────────────────
                Text(
                    text = "Escribe el código de sala",
                    style = PrivatiTypography.heading,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(PrivatiSpacing.sm))
                Text(
                    text = "El anfitrión te lo mostrará en su pantalla",
                    style = PrivatiTypography.caption,
                    color = colors.inkMuted,
                )
                Spacer(Modifier.height(PrivatiSpacing.lg))
            }

            Spacer(Modifier.height(PrivatiSpacing.md))

            RoomCodeInput(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(PrivatiSpacing.lg))

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
                        errorMessage = "Formato no válido (ej. 192.168.1.5)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    PrivatiTextField(
                        value = port,
                        onValueChange = { port = it; portError = false },
                        placeholder = "9000",
                        label = "Puerto (1024–65535)",
                        isError = portError,
                        errorMessage = "Puerto debe ser un número entre 1024 y 65535",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            val textoBoton = when {
                buscandoPorCodigo -> "Buscando sala…"
                conectando -> "Conectando…"
                else -> "Conectar"
            }

            PrivatiButton(
                text = textoBoton,
                onClick = {
                    if (showManual && ip.isNotBlank()) {
                        val ipOk = viewModel.esIpValida(ip)
                        val puertoOk = viewModel.esPuertoValido(port)
                        ipError = !ipOk
                        portError = !puertoOk
                        if (ipOk && puertoOk) {
                            viewModel.conectar(ip.trim(), port.trim().toInt())
                        }
                    } else if (code.length == 4) {
                        viewModel.buscarPorCodigo(code.uppercase())
                    }
                },
                variant = ButtonVariant.Primary,
                enabled = !ocupado && (code.length == 4 || (showManual && ip.isNotBlank())),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PrivatiTheme.colors.surface)
                .padding(PrivatiSpacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Vista previa: JoinScreen", style = PrivatiTypography.heading)
        }
    }
}
