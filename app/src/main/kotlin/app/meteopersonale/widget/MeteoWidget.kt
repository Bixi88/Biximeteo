package app.meteopersonale.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Widget a 7 giorni: giorno, icona, massima (grande) e minima (piccola). */
class MeteoWidget : GlanceAppWidget() {

    // Il numero di colonne dipende dalla larghezza reale del widget
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val settings = SettingsStore(context)
        val place = settings.place
        val dark = settings.widgetDarkText

        var days: List<DayPoint> = emptyList()
        var message: String? = null
        if (place == null) {
            message = "Apri l'app per scegliere la posizione"
        } else {
            try {
                val f = WeatherRepository(context).load(place, settings.source)
                val today = LocalDate.now()
                days = f.days.filter { !it.date.isBefore(today) }.take(7)
                if (days.isEmpty()) message = "Nessun dato"
            } catch (e: Exception) {
                message = "Nessun dato"
            }
        }

        provideContent { WidgetContent(days, message, dark) }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE", Locale.ITALY)

@Composable
private fun WidgetContent(days: List<DayPoint>, message: String?, dark: Boolean) {
    val main = if (dark) Color(0xFF101010) else Color.White
    val sub = if (dark) Color(0xFF3A3A3A) else Color(0xFFE0E0E0)
    val size = LocalSize.current

    val open = actionStartActivity<MainActivity>()

    if (message != null) {
        Box(
            modifier = GlanceModifier.fillMaxSize().clickable(open),
            contentAlignment = Alignment.Center
        ) {
            Text(message, style = TextStyle(color = ColorProvider(main), fontSize = 14.sp))
        }
        return
    }

    // circa 48 dp per colonna, da 3 a 7 giorni
    val cols = (size.width.value / 48f).toInt().coerceIn(3, 7)
    val shown = days.take(cols)
    val icon = if (size.height < 90.dp) 30.dp else 38.dp

    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 4.dp).clickable(open),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        shown.forEach { d ->
            Column(
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    dayFmt.format(d.date).replaceFirstChar { it.uppercase() }.removeSuffix("."),
                    style = TextStyle(
                        color = ColorProvider(main),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                )
                Image(
                    provider = ImageProvider(WeatherIcons.resFor(d.symbol)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(icon)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${Math.round(d.maxC)}°",
                        style = TextStyle(color = ColorProvider(main), fontSize = 19.sp)
                    )
                    Text(
                        "${Math.round(d.minC)}°",
                        style = TextStyle(color = ColorProvider(sub), fontSize = 12.sp)
                    )
                }
            }
        }
    }
}
