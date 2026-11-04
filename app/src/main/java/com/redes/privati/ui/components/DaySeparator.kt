package com.redes.privati.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.redes.privati.ui.theme.PrivatiSpacing
import com.redes.privati.ui.theme.PrivatiTheme
import com.redes.privati.ui.theme.PrivatiTypography
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Separador de día en el hilo de conversación.
 * Muestra «Hoy», «Ayer» o la fecha en formato «d MMM» centrado entre dos líneas.
 *
 * @param modifier Modificador externo.
 * @param timestamp Marca de tiempo de cualquier mensaje del día a representar.
 */
@Composable
fun DaySeparator(
    modifier: Modifier = Modifier,
    timestamp: Long,
) {
    val colors = PrivatiTheme.colors
    val label = formatearDia(timestamp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = PrivatiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PrivatiSpacing.sm),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colors.divider,
            thickness = 1.dp,
        )
        Text(
            text = label,
            style = PrivatiTypography.caption,
            color = colors.inkMuted,
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = colors.divider,
            thickness = 1.dp,
        )
    }
}

/**
 * Convierte [timestamp] en «Hoy», «Ayer» o «d MMM» en español.
 */
fun formatearDia(timestamp: Long): String {
    val hoy = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }

    val mismoDia = hoy.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
        && hoy.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

    hoy.add(Calendar.DAY_OF_YEAR, -1)
    val eraAyer = hoy.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
        && hoy.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

    return when {
        mismoDia -> "Hoy"
        eraAyer  -> "Ayer"
        else     -> SimpleDateFormat("d MMM", Locale("es")).format(Date(timestamp))
    }
}

@Preview(name = "Claro", showBackground = true)
@Preview(name = "Oscuro", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DaySeparatorPreview() {
    PrivatiTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(PrivatiSpacing.md)
        ) {
            DaySeparator(timestamp = System.currentTimeMillis())
            DaySeparator(timestamp = System.currentTimeMillis() - 86_400_000L)
            DaySeparator(timestamp = System.currentTimeMillis() - 7 * 86_400_000L)
        }
    }
}
