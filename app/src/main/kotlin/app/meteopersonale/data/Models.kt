package app.meteopersonale.data

import java.time.Instant
import java.time.LocalDate

enum class WeatherSource(val label: String) {
    MET_NORWAY("MET Norway"),
    OPEN_METEO("Open-Meteo")
}

data class Place(val name: String, val lat: Double, val lon: Double)

/** Punto grezzo di una fonte: stepHours = durata che copre (1 = orario, fino a 6 = blocchi). */
data class RawPoint(
    val time: Instant,
    val tempC: Double,
    val windMs: Double,
    val gustMs: Double?,
    val precipMm: Double,
    val stepHours: Int,
    val symbol: String
)

data class HourPoint(
    val time: Instant,
    val tempC: Double,
    val precipMm: Double,
    val windMs: Double,
    val gustMs: Double?,
    val symbol: String
)

data class DayPoint(
    val date: LocalDate,
    val minC: Double,
    val maxC: Double,
    val precipMm: Double,
    val windMaxMs: Double,
    val gustMaxMs: Double?,
    val symbol: String
)

data class Forecast(
    val source: WeatherSource,
    val hours: List<HourPoint>,
    val days: List<DayPoint>,
    val fetchedAt: Instant,
    /** Se non nulla, la fonte scelta ha fallito e si è passati all'altra. */
    val fallbackFrom: WeatherSource? = null
)
