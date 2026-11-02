package app.meteopersonale.data

import java.time.ZoneId
import kotlin.math.abs

object Aggregator {
    fun hours(raw: List<RawPoint>): List<HourPoint> =
        raw.filter { it.stepHours == 1 }
            .map {
                HourPoint(
                    it.time, it.tempC, it.precipMm, it.windMs, it.gustMs,
                    Symbols.adjustRain(it.symbol, it.precipMm)
                )
            }

    /** Da questo totale giornaliero (mm) l'icona del giorno diventa "pioggia forte". */
    const val DAY_HEAVY_TOTAL_MM = 40.0

    fun days(raw: List<RawPoint>, zone: ZoneId): List<DayPoint> =
        raw.groupBy { it.time.atZone(zone).toLocalDate() }
            .entries
            .sortedBy { it.key.toEpochDay() }
            .map { (date, pts) ->
                val precip = pts.sumOf { it.precipMm }
                // mm all'ora di ogni punto (i blocchi da 3/6 ore vengono riportati all'ora)
                fun rate(p: RawPoint) = p.precipMm / p.stepHours
                val wettest = pts.maxByOrNull { rate(it) }
                val peak = wettest?.let { rate(it) } ?: 0.0

                // Icona del giorno: il punto più vicino alle 13; se la giornata è piovosa
                // si usa invece l'ora più piovosa (il fenomeno più significativo).
                val noon = pts.minByOrNull {
                    val z = it.time.atZone(zone)
                    abs(z.hour * 60 + z.minute - 13 * 60)
                }!!
                var symbol = noon.symbol
                if (precip >= 0.5 && wettest != null && peak > 0) symbol = wettest.symbol

                // Intensità decisa dall'ora più piovosa (stesse soglie della lista oraria),
                // non dal totale delle 24 ore.
                symbol = Symbols.adjustRain(Symbols.toDay(symbol), peak)
                // Solo giornate davvero molto piovose: forte anche senza un'ora intensa.
                if (precip >= DAY_HEAVY_TOTAL_MM) symbol = Symbols.raiseToHeavy(symbol)

                DayPoint(
                    date = date,
                    minC = pts.minOf { it.tempC },
                    maxC = pts.maxOf { it.tempC },
                    precipMm = precip,
                    windMaxMs = pts.maxOf { it.windMs },
                    gustMaxMs = pts.mapNotNull { it.gustMs }.maxOrNull(),
                    symbol = symbol
                )
            }
}
