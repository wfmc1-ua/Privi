# Práctica final — Chat entre terminales (app «Corro»)

Eres el desarrollador de una app Android para la práctica final del Máster en Desarrollo de Software para Dispositivos Móviles (Universidad de Alicante). El proyecto ya existe en este directorio: Kotlin + Jetpack Compose + Material3, paquete `com.redes.privati`, minSdk 24, targetSdk 37, AGP 9 con version catalog (`gradle/libs.versions.toml`). Trabaja sobre él; no crees otro proyecto.

## 1. Qué hay que construir

Una app de chat cliente/servidor **sin internet** (las pruebas y la demo se hacen en mi Wi‑Fi doméstica): un móvil crea una sala y hace de **servidor TCP** en la Wi‑Fi local; el resto se une como clientes. Implementamos **los dos extremos** con `java.net.ServerSocket`/`Socket`. **Prohibido** cualquier servicio o SDK de mensajería de terceros (Firebase, Nearby Connections, XMPP, Socket.IO…).

### Requisitos mínimos (dan el aprobado; NUNCA se pueden romper)
1. Un dispositivo hace de servidor y acepta la conexión de un cliente.
2. Funciona entre dos dispositivos reales, no solo en el emulador.
3. Los dos extremos envían y reciben texto en cualquier momento, sin turnos (lectura y escritura en corrutinas independientes).
4. La conversación se ve en pantalla, distinguiendo mensajes propios y ajenos.
5. La interfaz no se bloquea (toda la red en `Dispatchers.IO`) y la desconexión del otro extremo se detecta y se muestra.
6. **Todo el código que escribas va comentado en castellano** (KDoc en clases y funciones públicas + comentarios en la lógica no obvia: protocolo, hilos, cifrado).

### Mejoras a implementar (en este orden de prioridad)
1. **Sala de chat** — varios clientes en un servidor, que reenvía a todos.
2. **Robustez** — validación de IP y puerto, reconexión automática, rechazo de clientes.
3. **Descubrimiento** — el cliente encuentra al servidor sin teclear la IP: NFC (rápido) y código de 4 caracteres (respaldo).
4. **Cifrado** — cifrado propio de los mensajes (ECDH + AES‑GCM).
5. **Imágenes** — envío de fotos además de texto.
6. **Propuesta propia: notas de voz** — grabar y enviar mensajes de audio (pendiente de acordar con el profesor).
7. (Gratis con el diseño) **Interfaz cuidada** — globos, nombres, hora, estado de la conexión.

## 2. Arquitectura

- Single‑activity, Navigation Compose, un `ViewModel` por pantalla, `StateFlow` hacia la UI.
- Corrutinas para todo: una corrutina de lectura y un `Channel` de salida por conexión (así enviar nunca bloquea a recibir).
- El servidor vive en un objeto con ámbito de aplicación (`ChatServer` creado desde un contenedor simple en `Application`), no en una pantalla, para que sobreviva a la rotación.
- Dependencias permitidas: `kotlinx-coroutines`, `kotlinx-serialization-json`, `navigation-compose`, `lifecycle-viewmodel-compose`, `coil-compose` (mostrar imágenes). Nada más sin preguntarme.

Paquetes sugeridos:
```
com.redes.privati
├─ net/        Protocol.kt (tipos de trama), FrameCodec.kt, ChatServer.kt, ChatClient.kt, Connection.kt
├─ crypto/     Handshake.kt (ECDH), SessionCipher.kt (AES-GCM), Hkdf.kt
├─ discovery/  NsdAdvertiser.kt, NsdBrowser.kt, RoomCode.kt, nfc/ (HostApduService + lector)
├─ data/       ChatRepository.kt, modelos (Message, Participant, RoomState)
└─ ui/         theme/ (tokens Corro), components/, screens/
```

## 3. Protocolo (definirlo antes de programar la red)

- TCP con **tramas con longitud**: `[4 bytes longitud big-endian][1 byte tipo][cuerpo]`. Nada de `readLine()`: las imágenes son binarias. Longitud máxima de trama: 2 MB; si se supera, se cierra la conexión.
- Tipos: `HELLO` (cliente → nombre, clave pública, código de sala), `WELCOME` (servidor → id asignado, lista de participantes, clave pública), `REJECT` (motivo: sala llena, código incorrecto, nombre repetido, expulsado), `TEXT`, `IMAGE`, `AUDIO`, `JOIN`/`LEAVE` (avisos de sistema), `PING`/`PONG`, `BYE`.
- Cuerpos de control en JSON (`kotlinx.serialization`); el cuerpo de `IMAGE` es `cabecera JSON + bytes JPEG` y el de `AUDIO` es `cabecera JSON (duración en ms) + bytes AAC/M4A`.
- Cada mensaje lleva `id` (UUID), `senderId`, `senderName`, `timestamp`. El servidor reenvía a todos **menos al emisor** y el emisor pinta su mensaje en local en el momento.

## 4. Detalle de cada mejora

### Sala de chat
- El servidor acepta en bucle; cada cliente es un `Connection` con su corrutina de lectura.
- Lista de participantes como `StateFlow`; avisos de sistema «Ana se ha unido» / «Ana ha salido».
- El host también participa en el chat (es un participante más, sin socket).

### Robustez
- Validar IP (IPv4 con regex + rango 0–255) y puerto (1024–65535) en el campo manual, con mensaje de error en el campo.
- Heartbeat: `PING` cada 5 s; si no llega nada en 15 s, la conexión se da por muerta (esto también cubre el requisito 5 cuando se cae la Wi‑Fi y no llega FIN).
- Cliente: reconexión automática con backoff (1, 2, 4, 8 s, máx. 5 intentos) mostrando «Reconectando…»; al volver, reenviar `HELLO` con el mismo id.
- Rechazo de clientes: sala llena (máx. 8), código incorrecto, nombre repetido, y el host puede **expulsar** a alguien desde su panel.
- Cerrar sockets y cancelar corrutinas siempre en `finally`; nunca `catch` vacío.

### Descubrimiento
- **Base (funciona en todos los móviles):** el host anuncia la sala con **NSD/mDNS** (`NsdManager`, servicio `_corro._tcp`) e incluye el código de 4 caracteres en el nombre del servicio o en los atributos TXT. El cliente escribe el código, explora con NSD y se conecta al servicio que coincide. Código: 4 caracteres de un alfabeto sin ambiguos (sin 0/O, 1/I/L).
- **Rápida (NFC):** Android Beam ya no existe, así que se hace con **Host Card Emulation**: el host registra un `HostApduService` con un AID propio y el cliente usa `NfcAdapter.enableReaderMode` + `IsoDep` para enviar un `SELECT AID` y recibir `ip:puerto:código:secretoSala`. El host solo responde mientras su panel de sala está abierto.
- Si el móvil no tiene NFC (`NfcAdapter.getDefaultAdapter == null`) o está desactivado, la pantalla de Unirse oculta la opción NFC y ofrece el código. Siempre queda la IP manual como último recurso.

### Cifrado (propio, extremo a servidor)
- Tras conectar, intercambio de claves **ECDH P‑256** (`KeyPairGenerator("EC")` + `KeyAgreement("ECDH")`).
- Clave de sesión = **HKDF‑SHA256**(secreto ECDH, salt = código de sala, o secreto de sala si se unió por NFC). Así, quien no conoce el código no puede hacer de intermediario.
- Cada trama posterior: **AES‑256‑GCM**, nonce = 4 bytes de dirección + contador de 8 bytes; se rechaza cualquier trama con contador repetido o fallo de autenticación (se cierra la conexión).
- En sala, el cifrado es por salto: cada canal cliente↔host tiene su propia clave y el host descifra y vuelve a cifrar al reenviar. Documenta esto en comentarios.
- Mostrar en la cabecera de la conversación un indicador «Cifrado» con candado.

### Imágenes
- Selector con `ActivityResultContracts.PickVisualMedia` (sin permisos de almacenamiento).
- Antes de enviar: reescalar a 1280 px como máximo en el lado largo, JPEG calidad 80, en `Dispatchers.Default`.
- Burbuja de imagen con miniatura (Coil desde `ByteArray`/archivo en caché) y tamaño en el metadato («212 KB · por red local»). Al tocarla, vista a pantalla completa.

### Notas de voz (propuesta propia)
- Grabación con `MediaRecorder` (salida `MPEG_4`, codificador `AAC`, mono, 64 kbps, 44,1 kHz) a un archivo temporal en `cacheDir`. Duración máxima 60 s (≈ 500 KB, cabe en una trama).
- Permiso `RECORD_AUDIO` pedido en tiempo de ejecución la primera vez que se pulsa el micrófono, con explicación si se deniega.
- Interacción tipo WhatsApp: **mantener pulsado** el botón de micrófono para grabar y soltar para enviar; deslizar a la izquierda para cancelar. Mientras se graba: punto rojo, contador «0:07» y texto «Desliza para cancelar».
- El botón de micrófono sustituye al de enviar cuando el campo de texto está vacío.
- Notas de menos de 1 s se descartan.
- Envío como trama `AUDIO`, cifrada igual que el resto. El receptor guarda los bytes en caché.
- Burbuja de audio: botón circular play/pausa, barra de progreso (o forma de onda simple calculada con `getMaxAmplitude()` durante la grabación y enviada en la cabecera como ~30 valores) y duración. Reproducción con `MediaPlayer`; solo suena una nota a la vez y se para al salir de la pantalla.
- Liberar siempre `MediaRecorder`/`MediaPlayer` (`release()`) en `onStop`/`DisposableEffect`.

## 5. Diseño — sistema «Corro» (respetarlo tal cual)

Tema propio en `ui/theme` que traduce estos tokens; no uses los colores por defecto de Material.

**Principios:** blanco limpio, un único violeta de marca, todo lo que se toca es píldora o círculo; plano (solo sombra en el CTA principal y en las burbujas entrantes); máximo un botón relleno por pantalla. Textos en español, tuteo, frases cortas, sin emojis, metadatos separados por « · », estados en gerundio («NFC activado · esperando…»).

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| surface | #FFFFFF | #131318 | Fondo de pantalla, burbuja entrante |
| surface-muted | #F7F7FA | #1B1B22 | Campos y fondo del hilo |
| surface-raised | #F1F1F4 | #26262F | Burbuja entrante en oscuro, avatar de sistema |
| ink / ink-secondary / ink-muted | #1B1B1F / #4A4A55 / #6B6B76 | #F2F2F5 / #B8B8C4 / #9494A0 | Texto principal / apoyo / horas y metadatos |
| primary | #6C5CE7 | #6C5CE7 | CTA, insignias, barra de navegación |
| bubble-out | #5A49D9 | #5A49D9 | Burbuja propia (texto blanco; hora en #E4E0FF) |
| primary-tint / primary-ink | #EDEAFF / #3F2FB0 | #2A2550 / #C9C2FF | Botón secundario y su texto |
| primary-subtle / primary-wash | #F6F4FF / #F4F2FF | #1E1B33 / #221E3A | Fila destacada / botón de icono redondo |
| border / divider | #E4E4EA / #EEEEF2 | #33333D / #26262E | Bordes y separadores de 1 px |
| success | #22A05B | #3DC27A | Punto «en línea» (nunca texto) |
| danger / danger-tint / danger-ink | #D12F45 / #FDECEE / #B0263A | #D12F45 / #3A1A20 / #FF8A98 | Cerrar sala, expulsar |

Avatares (fondo/iniciales, uno fijo por persona según hash del id): melocotón #FFD9C7/#7A3418, azul #D6DEFF/#26358A, amarillo #FFE9A8/#6B4E00, verde #CFEEDD/#185C38, lila #E7E2FF/#3F2FB0, rosa #FFDCE8/#8A1F48 (en oscuro: #4A2A1E/#FFC7AE, #232D5C/#C2CEFF, #44380F/#FFE08A, #173A28/#A9E3C3, #2C2560/#C9C2FF, #4A1F31/#FFBFD6). Nombre del remitente en burbuja de grupo con el color de tinta de su avatar.

**Tipografía:** Inter (400/500/600/700) vía `ui-text-google-fonts` o fuentes en `res/font`. display 30/700 (−0.03em) · title-1 28/700 · title-2 22/700 · heading 18/700 · body-strong 16/600 · body 15/400 (1.4 en burbujas) · body-sm 14/400 · label 13/500 · caption 12/500 · micro 11/600 · code 18/600 con interletraje 0.2em (código de sala). IPs en monoespaciada.

**Forma y medidas:** radios 6 (cola de burbuja), 18 (burbuja), 28 (tarjetas), píldora completa en botones, campos, pestañas y avatares. Márgenes laterales 20 dp; entre burbujas 10 dp; CTA 56 dp de alto; campos 52; toque mínimo 44; avatares 52/46/30. Sombra del CTA: violeta suave (0 10 30, primary al 35 %).

**Componentes a crear en Compose:** `CorroButton` (primario, secundario tonal, destructivo tonal, icono redondo), `CorroTextField`, `Avatar`, `MessageBubble` (entrante blanca con cola abajo‑izquierda; propia `bubble-out` con cola abajo‑derecha; hora dentro; doble check opcional; variantes texto, imagen y audio), `StatusPill`, `ChatRow`.

**Reutilización de componentes (obligatorio):**
- **Un archivo por componente** en `ui/components/`, con el mismo nombre que el composable: `CorroButton.kt`, `CorroTextField.kt`, `Avatar.kt`, `MessageBubble.kt`, `AudioBubble.kt`, `ImageBubble.kt`, `StatusPill.kt`, `ChatRow.kt`, `RoomCodeInput.kt`, `NfcWaves.kt`, `CorroTopBar.kt`, `MessageInputBar.kt`, `CorroLogo.kt`. Si un componente tiene variantes, van en el mismo archivo como parámetro (`enum class ButtonVariant { Primary, Secondary, Danger, Icon }`), no como copias.
- Los iconos propios, en `ui/icons/CorroIcons.kt` (un `ImageVector` por icono).
- Los componentes son **sin estado**: reciben datos y lambdas (`text`, `onClick`, `enabled`…) y un `modifier: Modifier = Modifier` como primer parámetro opcional. Nada de ViewModel ni lógica de red dentro.
- Todo color, tipografía, radio, espaciado y tamaño sale del tema (`CorroTheme.colors`, `CorroTheme.typography`, `CorroShapes`, `CorroSpacing`, `CorroSizes` en `ui/theme/`). **Prohibido** escribir `Color(0xFF…)`, `dp` sueltos de diseño o `RoundedCornerShape(…)` dentro de una pantalla.
- Las pantallas (`ui/screens/<Pantalla>/<Pantalla>Screen.kt`) **solo componen** componentes y layouts (`Column`, `Row`, `LazyColumn`…). Si en una pantalla necesitas un trozo de UI que se repite o que tiene estilo propio, sácalo a `ui/components/` antes de usarlo.
- Cada componente lleva su `@Preview` en claro y en oscuro, y un KDoc en castellano con para qué sirve y qué significa cada parámetro.

**Pantallas (subconjunto del prototipo):**
1. **Bienvenida** — isotipo (círculo primary con bocadillo sonriente) + «Corro», ilustración de círculos concéntricos, campo de nombre, «Crear sala» (primario) y «Unirme» (secundario).
2. **Unirse** — tarjeta NFC «Acerca tu móvil» con ondas animadas; debajo «o escribe el código» (4 casillas); enlace «Conectar por IP» que despliega IP + puerto validados.
3. **Panel del host** — código grande, IP:puerto en mono, «NFC activado · esperando…», lista de personas con opción de expulsar, «Ir al chat» y «Cerrar sala» (destructivo).
4. **Conversación** — cabecera con nombre de sala, «5 en la sala», `StatusPill` de conexión (En línea / Reconectando… / Desconectado) y candado de cifrado; hilo sobre surface-muted; barra inferior con botón de adjuntar imagen, campo de mensaje y botón de micrófono/enviar (círculo primary).

Iconos: trazo propio 24×24, sin relleno, trazo 2, extremos redondeados (como `ImageVector` o `Icons.Rounded` si se parece). Soporta modo oscuro.

## 6. Cómo trabajar

Ve por fases. **Al final de cada fase**: `./gradlew assembleDebug` sin errores, lista de pruebas manuales en dos móviles reales, y un commit con mensaje en castellano. No empieces la siguiente fase hasta que yo confirme que la anterior funciona en los móviles.

- **Fase 0** — Tema Corro (tokens) → componentes reutilizables, cada uno en su archivo y con previews → pantallas con datos falsos montadas solo con esos componentes.
- **Fase 1** — Mínimos: servidor 1↔1 por IP manual, texto bidireccional, propios/ajenos, detección de desconexión. ⚠️ Esta fase debe quedar perfecta antes de seguir.
- **Fase 2** — Sala de chat (N clientes, reenvío, avisos de sistema, lista de personas).
- **Fase 3** — Robustez (validación, heartbeat, reconexión, rechazo y expulsión).
- **Fase 4** — Descubrimiento por código con NSD.
- **Fase 5** — Cifrado ECDH + AES‑GCM.
- **Fase 6** — Imágenes.
- **Fase 7** — Notas de voz.
- **Fase 8** — NFC con HCE (lo último: es lo más frágil y depende del hardware).

Reglas: permisos mínimos en el manifiesto (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `RECORD_AUDIO`, `NFC` con `required="false"`); nada de red en el hilo principal; comentarios en castellano; si algo de esta especificación es inviable o hay una opción claramente mejor, **para y pregúntame** en vez de improvisar.

Empieza leyendo el proyecto, propón el plan de la Fase 0 con la lista de archivos que vas a crear y espera mi OK.
