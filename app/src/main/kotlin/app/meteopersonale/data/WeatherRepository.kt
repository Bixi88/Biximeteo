package app.meteopersonale.data

import android.content.Context
import app.meteopersonale.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

class WeatherRepository(private val context: Context) {

    private val ua = BuildConfig.USER_AGENT

    /**
     * Carica la previsione dalla fonte scelta; se fallisce passa automaticamente
     * all'altra (e lo segnala in Forecast.fallbackFrom).
     */
    suspend fun load(
        place: Place,
        source: WeatherSource,
        force: Boolean = false,
        maxAgeS: Long? = null
    ): Forecast =
        withContext(Dispatchers.IO) {
            try {
                fetch(place, source, force, maxAgeS)
            } catch (e: Exception) {
                val other = if (source == WeatherSource.MET_NORWAY) {
                    WeatherSource.OPEN_METEO
                } else {
                    WeatherSource.MET_NORWAY
                }
                fetch(place, other, force, maxAgeS).copy(fallbackFrom = source)
            }
        }

    private fun fetch(place: Place, source: WeatherSource, force: Boolean, maxAgeS: Long?): Forecast {
        val key = String.format(Locale.US, "%.4f,%.4f", place.lat, place.lon)
        val body = bodyFor(place, source, key, force, maxAgeS)
        val raw = when (source) {
            WeatherSource.MET_NORWAY -> MetNoParser.parse(body)
            WeatherSource.OPEN_METEO -> OpenMeteoParser.parse(body)
        }
        if (raw.isEmpty()) throw IOException("${source.label}: nessun dato")

        // L'ora precedente a quella attuale spesso non è nella previsione:
        // la si recupera dallo storico salvato nelle richieste precedenti.
        val now = Instant.now()
        val firstRaw = raw.minOf { it.time }
        val extra = readHistory(key)
            .filter { it.time < firstRaw && it.time >= now.minus(6, ChronoUnit.HOURS) }
            .map { RawPoint(it.time, it.tempC, it.windMs, it.gustMs, it.precipMm, 1, it.symbol) }
        val merged = (extra + raw).sortedBy { it.time }

        saveHistory(
            key,
            Aggregator.hours(merged).filter {
                it.time <= now && it.time >= now.minus(6, ChronoUnit.HOURS)
            }
        )

        return Forecast(
            source = source,
            hours = Aggregator.hours(merged),
            days = Aggregator.days(merged, ZoneId.systemDefault()),
            fetchedAt = now
        )
    }

    // ---------- cache delle risposte ----------

    private fun safe(key: String) = key.replace(Regex("[^0-9A-Za-z]"), "_")

    private fun cacheFile(s: WeatherSource, key: String) =
        File(context.cacheDir, "fc_${s.name}_${safe(key)}.json")

    private fun readCache(s: WeatherSource, key: String): JSONObject? {
        val f = cacheFile(s, key)
        if (!f.exists()) return null
        return runCatching { JSONObject(f.readText()) }.getOrNull()
            ?.takeIf { it.optString("key") == key }
    }

    private fun writeCache(
        s: WeatherSource, key: String, body: String,
        expires: Long, lastModified: String?
    ) {
        val o = JSONObject()
            .put("key", key)
            .put("body", body)
            .put("expires", expires)
            .put("fetchedAt", Instant.now().epochSecond)
            .put("lastModified", lastModified ?: "")
        runCatching { cacheFile(s, key).writeText(o.toString()) }
    }

    private fun bodyFor(
        place: Place, source: WeatherSource, key: String, force: Boolean, maxAgeS: Long?
    ): String {
        val nowS = Instant.now().epochSecond
        val cached = readCache(source, key)
        val youngEnough = maxAgeS != null && cached != null &&
            nowS - cached.optLong("fetchedAt") < maxAgeS
        if (!force && cached != null && (nowS < cached.optLong("expires") || youngEnough)) {
            return cached.getString("body")
        }

        val url = when (source) {
            WeatherSource.MET_NORWAY -> MetNoParser.url(place)
            WeatherSource.OPEN_METEO -> OpenMeteoParser.url(place)
        }
        val ims = if (source == WeatherSource.MET_NORWAY) {
            cached?.optString("lastModified")?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val res = try {
            Http.get(url, ua, ims)
        } catch (e: IOException) {
            null
        }

        if (res != null && cached != null && res.code == 304) {
            val body = cached.getString("body")
            writeCache(source, key, body, nextExpiry(source, res.expiresEpoch, nowS), cached.optString("lastModified"))
            return body
        }
        val newBody = res?.body
        if (res != null && newBody != null) {
            writeCache(source, key, newBody, nextExpiry(source, res.expiresEpoch, nowS), res.lastModified)
            return newBody
        }

        // Rete assente o errore: si usa la copia salvata se ha meno di 3 ore
        if (cached != null && nowS - cached.optLong("fetchedAt") < 3 * 3600) {
            return cached.getString("body")
        }
        throw IOException("${source.label}: risposta ${res?.code ?: "assente"}")
    }

    private fun nextExpiry(source: WeatherSource, headerExpires: Long?, nowS: Long): Long =
        if (source == WeatherSource.MET_NORWAY) {
            // si rispetta l'Expires di MET, ma mai sotto i 5 minuti
            maxOf(headerExpires ?: (nowS + 600), nowS + 300)
        } else {
            nowS + 900
        }

    // ---------- storico delle ultime ore ----------

    private fun historyFile(key: String) = File(context.filesDir, "history_${safe(key)}.json")

    private fun readHistory(key: String): List<HourPoint> {
        val f = historyFile(key)
        if (!f.exists()) return emptyList()
        return runCatching {
            val o = JSONObject(f.readText())
            if (o.optString("key") != key) return emptyList()
            val a = o.getJSONArray("points")
            (0 until a.length()).map { i ->
                val p = a.getJSONObject(i)
                HourPoint(
                    time = Instant.ofEpochSecond(p.getLong("t")),
                    tempC = p.getDouble("temp"),
                    precipMm = p.getDouble("p"),
                    windMs = p.getDouble("w"),
                    gustMs = if (p.has("g")) p.getDouble("g") else null,
                    symbol = p.getString("s")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveHistory(key: String, points: List<HourPoint>) {
        val a = JSONArray()
        for (p in points) {
            val o = JSONObject()
                .put("t", p.time.epochSecond)
                .put("temp", p.tempC)
                .put("p", p.precipMm)
                .put("w", p.windMs)
                .put("s", p.symbol)
            p.gustMs?.let { o.put("g", it) }
            a.put(o)
        }
        runCatching {
            historyFile(key).writeText(JSONObject().put("key", key).put("points", a).toString())
        }
    }
}
