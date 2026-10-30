package app.meteopersonale.data

import org.json.JSONObject
import java.time.Duration
import java.time.Instant

object MetNoParser {
    private class Entry(
        val time: Instant,
        val temp: Double,
        val wind: Double,
        val gust: Double?,
        val p1: Double?,
        val s1: String?,
        val p6: Double?,
        val s6: String?
    )

    fun url(place: Place): String =
        String.format(
            java.util.Locale.US,
            "https://api.met.no/weatherapi/locationforecast/2.0/complete?lat=%.4f&lon=%.4f",
            place.lat, place.lon
        )

    fun parse(body: String): List<RawPoint> {
        val ts = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
        val entries = ArrayList<Entry>()
        for (i in 0 until ts.length()) {
            val o = ts.getJSONObject(i)
            val d = o.getJSONObject("data")
            val det = d.getJSONObject("instant").getJSONObject("details")
            if (!det.has("air_temperature")) continue
            val n1 = d.optJSONObject("next_1_hours")
            val n6 = d.optJSONObject("next_6_hours")
            val gust = det.optDouble("wind_speed_of_gust", Double.NaN)
            entries.add(
                Entry(
                    time = Instant.parse(o.getString("time")),
                    temp = det.getDouble("air_temperature"),
                    wind = det.optDouble("wind_speed", 0.0),
                    gust = if (gust.isNaN()) null else gust,
                    p1 = n1?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0),
                    s1 = n1?.optJSONObject("summary")?.optString("symbol_code")?.takeIf { it.isNotBlank() },
                    p6 = n6?.optJSONObject("details")?.optDouble("precipitation_amount", 0.0),
                    s6 = n6?.optJSONObject("summary")?.optString("symbol_code")?.takeIf { it.isNotBlank() }
                )
            )
        }

        val out = ArrayList<RawPoint>()
        for ((i, e) in entries.withIndex()) {
            val p1 = e.p1
            val s1 = e.s1
            val p6 = e.p6
            val s6 = e.s6
            if (p1 != null && s1 != null) {
                out.add(RawPoint(e.time, e.temp, e.wind, e.gust, p1, 1, s1))
            } else if (p6 != null && s6 != null) {
                // Nella zona a passo di 3 o 6 ore i blocchi "6h" si sovrappongono:
                // si conta solo la quota fino al punto successivo.
                val next = entries.getOrNull(i + 1)
                val step = if (next == null) 6
                else Duration.between(e.time, next.time).toHours().toInt().coerceIn(1, 6)
                out.add(RawPoint(e.time, e.temp, e.wind, e.gust, p6 * step / 6.0, step, s6))
            }
        }
        return out
    }
}

object OpenMeteoParser {
    fun url(place: Place): String =
        String.format(
            java.util.Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&hourly=temperature_2m,precipitation,weather_code,wind_speed_10m,wind_gusts_10m,is_day" +
                "&wind_speed_unit=ms&timeformat=unixtime&timezone=GMT&past_hours=3&forecast_days=10",
            place.lat, place.lon
        )

    fun parse(body: String): List<RawPoint> {
        val h = JSONObject(body).getJSONObject("hourly")
        val t = h.getJSONArray("time")
        val temp = h.getJSONArray("temperature_2m")
        val pr = h.getJSONArray("precipitation")
        val code = h.getJSONArray("weather_code")
        val ws = h.getJSONArray("wind_speed_10m")
        val wg = h.getJSONArray("wind_gusts_10m")
        val day = h.getJSONArray("is_day")
        val out = ArrayList<RawPoint>()
        for (i in 0 until t.length()) {
            if (temp.isNull(i)) continue
            val gust = wg.optDouble(i, Double.NaN)
            // In Open-Meteo "precipitation" è la somma dell'ora PRECEDENTE:
            // il valore dell'ora successiva copre l'ora che parte da t[i].
            val precip = if (i + 1 < pr.length()) pr.optDouble(i + 1, 0.0) else 0.0
            out.add(
                RawPoint(
                    time = Instant.ofEpochSecond(t.getLong(i)),
                    tempC = temp.getDouble(i),
                    windMs = ws.optDouble(i, 0.0),
                    gustMs = if (gust.isNaN()) null else gust,
                    precipMm = precip,
                    stepHours = 1,
                    symbol = Symbols.fromWmo(code.optInt(i, 0), day.optInt(i, 1) == 1)
                )
            )
        }
        return out
    }
}
