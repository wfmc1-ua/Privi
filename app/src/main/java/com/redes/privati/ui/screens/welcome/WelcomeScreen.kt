package com.redes.privati.ui.screens.welcome

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.components.ButtonVariant
import com.redes.privati.ui.components.PrivatiButton
import com.redes.privati.ui.components.PrivatiLogo
import com.redes.privati.ui.components.PrivatiTextField
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Pantalla de bienvenida de Privati.
 * El usuario elige su nombre y decide si crea una sala (host) o se une a una existente.
 *
 * @param onCreateRoom Navegación hacia el panel del host.
 * @param onJoinRoom Navegación hacia la pantalla de unirse.
 */
@Composable
fun WelcomeScreen(
    onCreateRoom: () -> Unit = {},
    onJoinRoom: () -> Unit = {},
) {
    val colors = PrivatiTheme.colors
    var name by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }

    fun validate(): Boolean {
        nameError = name.isBlank()
        return !nameError
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        // Ilustración de círculos concéntricos decorativos
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.TopCenter),
        ) {
            val cx = center.x
            val cy = size.height * 0.4f
            val baseR = size.minDimension * 0.18f
            val ringColor = colors.primary.copy(alpha = 0.06f)
            for (i in 1..5) {
                drawCircle(
                    color = ringColor,
                    radius = baseR * i,
                    center = androidx.compose.ui.geometry.Offset(cx, cy),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = PrivatiSpacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PrivatiLogo(iconSize = 72.dp, showName = true)

            Spacer(Modifier.height(PrivatiSpacing.xl))

            Text(
                text = "Chatea en tu red local,\nsin internet.",
                style = PrivatiTypography.body,
                color = colors.inkSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.height(PrivatiSpacing.xl))

            PrivatiTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = false
                },
                placeholder = "Tu nombre",
                isError = nameError,
                errorMessage = "Escribe tu nombre para continuar",
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(PrivatiSpacing.lg))

            PrivatiButton(
                text = "Crear sala",
                onClick = { if (validate()) onCreateRoom() },
                variant = ButtonVariant.Primary,
            )

            Spacer(Modifier.height(PrivatiSpacing.sm))

            PrivatiButton(
                text = "Unirme a una sala",
                onClick = { if (validate()) onJoinRoom() },
                variant = ButtonVariant.Secondary,
            )
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WelcomeScreenPreview() {
    PrivatiTheme {
        WelcomeScreen()
    }
}
