package app.meteopersonale.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.currentState
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import org.json.JSONArray
import org.json.JSONObject
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.meteopersonale.data.DayPoint
import app.meteopersonale.data.SettingsStore
import app.meteopersonale.data.WeatherRepository
import app.meteopersonale.ui.MainActivity
import app.meteopersonale.ui.WeatherIcons
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Dati già pronti da disegnare (salvati nello stato di Glance). */
private data class WidgetData(
    val cols: List<WidgetCol>,
    val message: String?,
    val dark: Boolean,
    val scale: Float
)

private val PAYLOAD_KEY = stringPreferencesKey("payload")

/** Widget meteo: giorno, icona, temperatura grande e minima piccola, come Weawow. */
class MeteoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetUpdater.schedulePeriodic(context)
        // I dati stanno nello stato di Glance: ogni modifica ridisegna subito il widget
        // (prima venivano letti una volta sola, e il widget restava indietro di un passo).
        val payload = buildPayload(context)
        updateAppWidgetState(context, id) { it[PAYLOAD_KEY] = payload }
        provideContent {
            val raw = currentState<Preferences>()[PAYLOAD_KEY]
            val d = parsePayload(raw)
            WidgetContent(d.cols, d.message, d.dark, d.scale)
        }
    }

    companion object {
        /** Ricalcola i dati e li scrive nello stato di tutti i widget: li aggiorna subito. */
        suspend fun push(context: Context) {
            val app = context.applicationContext
            val payload = buildPayload(app)
            val widget = MeteoWidget()
            val ids = GlanceAppWidgetManager(app).getGlanceIds(MeteoWidget::class.java)
            for (id in ids) {
                updateAppWidgetState(app, id) { it[PAYLOAD_KEY] = payload }
                widget.update(app, id)
            }
        }

        private suspend fun buildPayload(context: Context): String {
            val settings = SettingsStore(context)
            val o = JSONObject()
                .put("dark", settings.widgetDarkText)
                .put("scale", settings.widgetFontScale.toDouble())
            val place = settings.widgetResolvedPlace()
            if (place == null) {
                o.put("msg", "Apri l'app per scegliere la posizione")
            } else {
                try {
                    val f = WeatherRepository(context).load(
                        place, settings.source,
                        maxAgeS = settings.widgetIntervalMin * 60L
                    )
                    val now = Instant.now()
                    // ora attuale: serve per l'icona di oggi (di sera con la luna)
                    val current = f.hours.lastOrNull {
                        it.time <= now && it.time > now.minus(2, ChronoUnit.HOURS)
                    }
                    val cols = buildWidgetColumns(f.days, current, settings.widgetDays)
                    if (cols.isEmpty()) {
                        o.put("msg", "Nessun dato")
                    } else {
                        val arr = JSONArray()
                        for (c in cols) {
                            arr.put(
                                JSONObject().put("n", c.name).put("s", c.symbol)
                                    .put("b", c.bigC).put("m", c.smallC)
                            )
                        }
                        o.put("cols", arr)
                    }
                } catch (e: Exception) {
                    o.put("msg", "Nessun dato")
                }
            }
            return o.toString()
        }

        private fun parsePayload(raw: String?): WidgetData {
            if (raw == null) return WidgetData(emptyList(), "Carico…", true, 1f)
            return try {
                val o = JSONObject(raw)
                val arr = o.optJSONArray("cols")
                val cols = ArrayList<WidgetCol>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val c = arr.getJSONObject(i)
                        cols.add(WidgetCol(c.getString("n"), c.getString("s"), c.getInt("b"), c.getInt("m")))
                    }
                }
                WidgetData(
                    cols,
                    if (o.has("msg")) o.getString("msg") else null,
                    o.optBoolean("dark", true),
                    o.optDouble("scale", 1.0).toFloat()
                )
            } catch (e: Exception) {
                WidgetData(emptyList(), "Nessun dato", true, 1f)
            }
        }
    }
}

/** Misure del widget, condivise con l'anteprima nelle impostazioni. */
object WidgetMetrics {
    const val COLUMN_DP = 56f
    const val DAY_SP = 12f
    const val MAX_SP = 14f
    const val MIN_SP = 9f
    const val ICON_DP = 24f
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ITALY)

fun widgetDayName(d: LocalDate): String =
    dayFmt.format(d).replaceFirstChar { it.uppercase() }.removeSuffix(".")

@Composable
private fun WidgetContent(cols: List<WidgetCol>, message: String?, dark: Boolean, scale: Float) {
    val main = if (dark) Color(0xFF101010) else Color.White
    val sub = if (dark) Color(0xFF444444) else Color(0xFFDDDDDD)

    val open = actionStartActivity(Intent(LocalContext.current, MainActivity::class.java))

    if (message != null) {
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(open),
            contentAlignment = Alignment.Center
        ) {
            Text(message, style = TextStyle(color = ColorProvider(main), fontSize = 13.sp))
        }
        return
    }


    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 2.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        cols.forEach { d ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    d.name,
                    style = TextStyle(
                        color = ColorProvider(main),
                        fontSize = (WidgetMetrics.DAY_SP * scale).sp,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1
                )
                Image(
                    provider = ImageProvider(WeatherIcons.resFor(d.symbol)),
                    contentDescription = null,
                    modifier = GlanceModifier.size((WidgetMetrics.ICON_DP * scale).dp)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${d.bigC}°",
                        style = TextStyle(
                            color = ColorProvider(main),
                            fontSize = (WidgetMetrics.MAX_SP * scale).sp
                        ),
                        maxLines = 1
                    )
                    Text(
                        "${d.smallC}°",
                        style = TextStyle(
                            color = ColorProvider(sub),
                            fontSize = (WidgetMetrics.MIN_SP * scale).sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
