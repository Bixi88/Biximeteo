package app.meteopersonale.alert

import app.meteopersonale.data.HourPoint
import java.time.Instant
import java.time.temporal.ChronoUnit

data class RainInfo(val totalMm: Double, val from: Instant, val to: Instant)
data class WindInfo(val maxKmh: Int, val gustKmh: Int?, val from: Instant, val to: Instant)
data class AlertInfo(val rain: RainInfo?, val wind: WindInfo?) {
    val any: Boolean get() = rain != null || wind != null
}

/** Decide se c'è da avvisare per pioggia e/o vento nelle prossime ore. */
object AlertLogic {
    /** Ore guardate in avanti dall'ora del popup. */
    const val WINDOW_HOURS = 12L

    /** Un'ora conta come piovosa da questa quantità. */
    const val RAIN_HOUR_MM = 0.2

    /** Pioggia totale minima nella finestra per far comparire l'avviso. */
    const val RAIN_TOTAL_MM = 1.0

    /** Vento medio che fa scattare l'avviso (km/h). */
    const val WIND_KMH = 7.0

    /** Raffica che fa scattare l'avviso (km/h). */
    const val GUST_KMH = 50.0

    fun evaluate(hours: List<HourPoint>, now: Instant): AlertInfo {
        val start = now.truncatedTo(ChronoUnit.HOURS)
        val end = start.plus(WINDOW_HOURS, ChronoUnit.HOURS)
        val win = hours.filter { it.time >= start && it.time < end }.sortedBy { it.time }

        val wet = win.filter { it.precipMm >= RAIN_HOUR_MM }
        val total = wet.sumOf { it.precipMm }
        val rain = if (wet.isNotEmpty() && total >= RAIN_TOTAL_MM) {
            RainInfo(total, wet.first().time, wet.last().time.plus(1, ChronoUnit.HOURS))
        } else {
            null
        }

        val windy = win.filter { it.windMs * 3.6 >= WIND_KMH || (it.gustMs ?: 0.0) * 3.6 >= GUST_KMH }
        val wind = if (windy.isNotEmpty()) {
            WindInfo(
                maxKmh = Math.round(windy.maxOf { it.windMs } * 3.6).toInt(),
                gustKmh = windy.mapNotNull { it.gustMs }.maxOrNull()?.let { Math.round(it * 3.6).toInt() },
                from = windy.first().time,
                to = windy.last().time.plus(1, ChronoUnit.HOURS)
            )
        } else {
            null
        }
        return AlertInfo(rain, wind)
    }
}
