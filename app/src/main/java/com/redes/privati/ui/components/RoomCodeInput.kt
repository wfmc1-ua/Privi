package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.theme.PrivatiShapes
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography

private const val CODE_LENGTH = 4

/**
 * Campo de entrada del código de sala de 4 caracteres.
 * Muestra 4 casillas individuales con tipografía monoespaciada grande.
 * El alfabeto válido excluye caracteres ambiguos (0, O, 1, I, L).
 *
 * @param modifier Modificador externo.
 * @param value Código actual (máx. 4 caracteres).
 * @param onValueChange Callback al modificar el código.
 * @param isError Si hay un error de validación.
 */
@Composable
fun RoomCodeInput(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean = false,
) {
    val colors = PrivatiTheme.colors

    // Subyacente invisible que gestiona el foco y el teclado
    BasicTextField(
        value = value,
        onValueChange = { raw ->
            val filtered = raw
                .uppercase()
                .filter { it.isLetterOrDigit() && it !in "0O1IL" }
                .take(CODE_LENGTH)
            onValueChange(filtered)
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            capitalization = KeyboardCapitalization.Characters,
        ),
        decorationBox = {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm, Alignment.CenterHorizontally),
            ) {
                repeat(CODE_LENGTH) { index ->
                    val char = value.getOrNull(index)
                    val isFocused = index == value.length && value.length < CODE_LENGTH
                    val borderColor = when {
                        isError -> colors.danger
                        isFocused -> colors.primary
                        else -> colors.border
                    }

                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 64.dp)
                            .clip(PrivatiShapes.card)
                            .background(colors.surfaceMuted)
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = borderColor,
                                shape = PrivatiShapes.card,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char?.toString() ?: "",
                            style = PrivatiTypography.code,
                            color = if (isError) colors.danger else colors.ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    )
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RoomCodeInputPreview() {
    PrivatiTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(PrivatiSpacing.md),
        ) {
            var code by remember { mutableStateOf("X7") }
            RoomCodeInput(value = code, onValueChange = { code = it })
            RoomCodeInput(value = "X7KP", onValueChange = {})
            RoomCodeInput(value = "XYZ", onValueChange = {}, isError = true)
        }
    }
}
