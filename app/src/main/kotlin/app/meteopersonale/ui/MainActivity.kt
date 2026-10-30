package app.meteopersonale.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.meteopersonale.data.Forecast
import app.meteopersonale.data.Place
import app.meteopersonale.data.WeatherRepository
import app.meteopersonale.data.WeatherSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    CheckScreen()
                }
            }
        }
    }
}

/** Schermata provvisoria: serve solo a verificare che i dati arrivino. */
@Composable
fun CheckScreen() {
    val ctx = LocalContext.current
    val repo = remember { WeatherRepository(ctx.applicationContext) }
    val place = remember { Place("Bellizzi", 40.62, 14.97) } // provvisorio
    var source by remember { mutableStateOf(WeatherSource.MET_NORWAY) }
    var status by remember { mutableStateOf("Carico…") }
    var forecast by remember { mutableStateOf<Forecast?>(null) }

    LaunchedEffect(source) {
        status = "Carico da ${source.label}…"
        try {
            val f = repo.load(place, source)
            forecast = f
            status = if (f.fallbackFrom != null) {
                "${f.fallbackFrom!!.label} non risponde: uso ${f.source.label}"
            } else {
                "Fonte: ${f.source.label}"
            }
        } catch (e: Exception) {
            status = "Errore: ${e.message}"
        }
    }

    val zone = ZoneId.systemDefault()
    val hf = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    val df = DateTimeFormatter.ofPattern("EEE d", Locale.ITALY)

    Column(Modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WeatherSource.values().forEach { s ->
                Button(onClick = { source = s }) { Text(s.label) }
            }
        }
        Text(status, Modifier.padding(vertical = 8.dp))

        val f = forecast
        if (f != null) {
            val from = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS)
            val to = from.plus(13, ChronoUnit.HOURS)
            val hours = f.hours.filter { it.time >= from && it.time <= to }
            LazyColumn {
                item { Text("ORE") }
                items(hours) { h ->
                    Text(
                        "${hf.format(h.time)}  ${Math.round(h.tempC)}°  " +
                            "${String.format(Locale.US, "%.1f", h.precipMm)}mm  ${h.symbol}"
                    )
                }
                item { Text("\nGIORNI") }
                items(f.days.take(7)) { d ->
                    Text(
                        "${df.format(d.date)}  ${Math.round(d.minC)}°/${Math.round(d.maxC)}°  " +
                            "${String.format(Locale.US, "%.1f", d.precipMm)}mm  ${d.symbol}"
                    )
                }
            }
        }
    }
}
