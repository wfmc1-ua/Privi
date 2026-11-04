package com.redes.privati.ui.screens.join

import android.app.Activity
import android.content.res.Configuration
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import com.redes.privati.data.SalaDescubierta
import com.redes.privati.discovery.nfc.CorroHceService
import com.redes.privati.ui.components.ButtonVariant
import com.redes.privati.ui.components.NfcJoinCard
import com.redes.privati.ui.components.PrivatiButton
import com.redes.privati.ui.components.PrivatiTextField
import com.redes.privati.ui.components.PrivatiTopBar
import com.redes.privati.ui.components.RoomCodeInput
import com.redes.privati.ui.components.RoomListItem
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

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
    val salas by viewModel.salas.collectAsState()
    val esperandoAprobacion = roomState.waitingForApproval

    val ocupado = conectando || buscandoPorCodigo

    var code by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("9000") }
    var ipError by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val nfcDisponible = remember { nfcAdapter != null && nfcAdapter.isEnabled }

    // Iniciar/detener descubrimiento NSD mientras la pantalla está visible
    DisposableEffect(Unit) {
        viewModel.iniciarDescubrimiento()
        onDispose { viewModel.detenerDescubrimiento() }
    }

    // Habilitar NFC reader mode si NFC está disponible
    if (nfcDisponible) {
        DisposableEffect(Unit) {
            val activity = context as? Activity
            if (activity != null) {
                nfcAdapter!!.enableReaderMode(
                    activity,
                    { tag ->
                        val isoDep = IsoDep.get(tag) ?: return@enableReaderMode
                        try {
                            isoDep.connect()
                            val selectApdu = CorroHceService.construirSelectApdu()
                            val respuesta = isoDep.transceive(selectApdu)
                            if (!CorroHceService.esRespuestaOk(respuesta)) return@enableReaderMode
                            val datos = respuesta.copyOf(respuesta.size - 2).decodeToString()
                            val partes = datos.split(":")
                            if (partes.size < 3) return@enableReaderMode
                            val ipNfc = partes[0]
                            val puertoNfc = partes[1].toIntOrNull() ?: return@enableReaderMode
                            val codigoNfc = partes[2]
                            val secretoNfc = if (partes.size >= 4) partes[3] else ""
                            viewModel.conectarViaNfc(ipNfc, puertoNfc, codigoNfc, secretoNfc)
                        } catch (_: Exception) {
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
        if (esperandoAprobacion) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = PrivatiSpacing.screenHorizontal),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.md),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = colors.primary,
                    )
                    Text(
                        text = "Esperando aprobación del host",
                        style = PrivatiTypography.heading,
                        color = colors.ink,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "El anfitrión recibirá una notificación y decidirá si aceptarte",
                        style = PrivatiTypography.caption,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(PrivatiSpacing.sm))
                    PrivatiButton(
                        text = "Cancelar solicitud",
                        onClick = viewModel::cancelarEspera,
                        variant = ButtonVariant.Danger,
                    )
                }
            }
            return@Scaffold
        }

        // ── Pantalla principal redesignada (Fase 14) ─────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)  // marca los insets del Scaffold como consumidos
                .imePadding(),                       // encoge el Column cuando aparece el teclado
        ) {
            // Área scrollable: NFC card + lista de salas
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = PrivatiSpacing.screenHorizontal,
                    vertical = PrivatiSpacing.md,
                ),
                verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.md),
            ) {
                item {
                    NfcJoinCard(
                        nfcActivo = nfcDisponible,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                item {
                    SectionHeader(titulo = "O elige una sala de tu red")
                }

                if (salas.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = PrivatiSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = colors.inkMuted,
                            )
                            Text(
                                text = "Buscando salas en tu red…",
                                style = PrivatiTypography.caption,
                                color = colors.inkMuted,
                            )
                        }
                    }
                } else {
                    items(salas, key = { it.code }) { sala ->
                        RoomListItem(
                            sala = sala,
                            onEntrar = { viewModel.conectarSalaDescubierta(sala) },
                            showDivider = sala != salas.last(),
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.divider)

            // ── Sección inferior fija: código + IP manual ─────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()           // reserva barra de sistema (0 si ya consumida)
                    .imePadding()                      // reserva teclado (0 si Column padre ya lo consumió)
                    .verticalScroll(rememberScrollState()) // scrollable por si la pantalla es pequeña
                    .padding(horizontal = PrivatiSpacing.screenHorizontal)
                    .padding(top = PrivatiSpacing.md, bottom = PrivatiSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
            ) {
                Text(
                    text = if (nfcDisponible) "¿Tu móvil no tiene NFC? Usa el código"
                           else "Escribe el código de sala",
                    style = PrivatiTypography.label,
                    color = colors.inkMuted,
                )

                RoomCodeInput(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier.fillMaxWidth(),
                )

                val textoBoton = when {
                    buscandoPorCodigo -> "Buscando sala…"
                    conectando -> "Conectando…"
                    else -> "Unirme"
                }

                PrivatiButton(
                    text = textoBoton,
                    onClick = {
                        if (showManual && ip.isNotBlank()) {
                            val ipOk = viewModel.esIpValida(ip)
                            val puertoOk = viewModel.esPuertoValido(port)
                            ipError = !ipOk
                            portError = !puertoOk
                            if (ipOk && puertoOk) viewModel.conectar(ip.trim(), port.trim().toInt())
                        } else if (code.length == 4) {
                            viewModel.buscarPorCodigo(code.uppercase())
                        }
                    },
                    variant = ButtonVariant.Primary,
                    enabled = !ocupado && (code.length == 4 || (showManual && ip.isNotBlank())),
                )

                TextButton(
                    onClick = { showManual = !showManual },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = if (showManual) "Ocultar conexión por IP" else "Conectar por IP",
                        style = PrivatiTypography.label,
                        color = colors.primary,
                    )
                }

                AnimatedVisibility(visible = showManual) {
                    Column(verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm)) {
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
            }
        }
    }
}

@Composable
private fun SectionHeader(titulo: String) {
    val colors = PrivatiTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
        Text(
            text = titulo,
            style = PrivatiTypography.label,
            color = colors.inkMuted,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = colors.divider)
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun JoinScreenPreview() {
    PrivatiTheme {
        // Static preview: no viewmodel
        Column(modifier = Modifier.fillMaxSize()) {
            Text("Vista previa: JoinScreen", style = PrivatiTypography.heading)
        }
    }
}
