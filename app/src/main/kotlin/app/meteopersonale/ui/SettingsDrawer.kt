package app.meteopersonale.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meteopersonale.data.WeatherSource

private val SectionColor = Color(0xFFB5ADC4)
private val LineColor = Color(0xFF2E2840)

@Composable
private fun SectionTitle2(text: String) {
    Text(
        text.uppercase(),
        color = SectionColor,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)
    )
}

/** Pannello impostazioni: si apre con uno swipe da sinistra verso destra e si chiude al contrario. */
@Composable
fun SettingsDrawer(
    source: WeatherSource,
    onSource: (WeatherSource) -> Unit,
    onWidget: () -> Unit,
    onPopup: () -> Unit,
    onReload: () -> Unit
) {
    ModalDrawerSheet(drawerContainerColor = Color(0xFF15111E)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Impostazioni", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = LineColor)

            SectionTitle2("Fonte dei dati")
            WeatherSource.values().forEach { s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSource(s) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = source == s, onClick = { onSource(s) })
                    Text(s.label, color = Color.White, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = LineColor)

            SectionTitle2("Widget")
            Text(
                "Giorni a vista, dimensione del testo, colore e luogo del widget.",
                color = SectionColor, fontSize = 14.sp
            )
            Button(
                onClick = onWidget,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 12.dp)
            ) { Text("Impostazioni widget") }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = LineColor)

            SectionTitle2("Popup")
            Text(
                "Avviso di vento e pioggia, all'ora che scegli.",
                color = SectionColor, fontSize = 14.sp
            )
            Button(
                onClick = onPopup,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 12.dp)
            ) { Text("Impostazioni popup") }
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = LineColor)

            SectionTitle2("Aggiornamenti")
            UpdateSection()
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = LineColor)

            SectionTitle2("Previsioni")
            Text("Scarica di nuovo i dati meteo adesso.", color = SectionColor, fontSize = 14.sp)
            OutlinedButton(
                onClick = onReload,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(top = 12.dp)
            ) { Text("Ricarica previsioni") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
