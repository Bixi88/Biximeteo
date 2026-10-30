package app.meteopersonale.data

import java.time.ZoneId
import kotlin.math.abs

object Aggregator {
    fun hours(raw: List<RawPoint>): List<HourPoint> =
        raw.filter { it.stepHours == 1 }
            .map { HourPoint(it.time, it.tempC, it.precipMm, it.windMs, it.gustMs, it.symbol) }

    fun days(raw: List<RawPoint>, zone: ZoneId): List<DayPoint> =
        raw.groupBy { it.time.atZone(zone).toLocalDate() }
            .entries
            .sortedBy { it.key.toEpochDay() }
            .map { (date, pts) ->
                val precip = pts.sumOf { it.precipMm }
                // Icona del giorno: il punto più vicino alle 13, ma se piove
                // e quel punto è asciutto si usa il punto più piovoso.
                val noon = pts.minByOrNull {
                    val z = it.time.atZone(zone)
                    abs(z.hour * 60 + z.minute - 13 * 60)
                }!!
                var symbol = noon.symbol
                if (precip >= 0.5 && !Symbols.isWet(symbol)) {
                    val wettest = pts.maxByOrNull { it.precipMm }
                    if (wettest != null && wettest.precipMm > 0) symbol = wettest.symbol
                }
                DayPoint(
                    date = date,
                    minC = pts.minOf { it.tempC },
                    maxC = pts.maxOf { it.tempC },
                    precipMm = precip,
                    windMaxMs = pts.maxOf { it.windMs },
                    gustMaxMs = pts.mapNotNull { it.gustMs }.maxOrNull(),
                    symbol = Symbols.toDay(symbol)
                )
            }
}
