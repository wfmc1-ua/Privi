package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSizes
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

/**
 * Campo de texto del sistema de diseño Privati.
 * Forma de píldora, altura 52 dp, soporte de validación con mensaje de error.
 *
 * @param modifier Modificador externo.
 * @param value Texto actual del campo.
 * @param onValueChange Callback al modificar el texto.
 * @param placeholder Texto de ayuda cuando el campo está vacío.
 * @param label Etiqueta flotante opcional.
 * @param isError Si hay un error de validación.
 * @param errorMessage Mensaje a mostrar bajo el campo cuando [isError] es verdadero.
 * @param enabled Si el campo acepta entrada.
 * @param keyboardOptions Opciones del teclado virtual.
 */
@Composable
fun PrivatiTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    label: String = "",
    isError: Boolean = false,
    errorMessage: String = "",
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = PrivatiTheme.colors

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = placeholder,
                    style = PrivatiTypography.body,
                    color = colors.inkMuted,
                )
            },
            label = if (label.isNotEmpty()) {
                { Text(text = label, style = PrivatiTypography.label) }
            } else null,
            isError = isError,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = keyboardOptions,
            textStyle = PrivatiTypography.body,
            shape = PrivatiShapes.pill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceMuted,
                unfocusedContainerColor = colors.surfaceMuted,
                disabledContainerColor = colors.surfaceRaised,
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.border,
                errorBorderColor = colors.danger,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
                errorTextColor = colors.ink,
                cursorColor = colors.primary,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(PrivatiSizes.fieldHeight),
        )

        if (isError && errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                style = PrivatiTypography.caption,
                color = colors.danger,
            )
        }
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrivatiTextFieldPreview() {
    PrivatiTheme {
        Column {
            var text by remember { mutableStateOf("") }
            PrivatiTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = "Tu nombre",
            )
            PrivatiTextField(
                value = "192.168.1.100",
                onValueChange = {},
                label = "Dirección IP",
                isError = true,
                errorMessage = "Formato de IP no válido",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}
