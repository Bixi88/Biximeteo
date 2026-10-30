package app.meteopersonale.data

import android.content.Context

/** Salva la fonte scelta, le località e le impostazioni del widget. */
class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var source: WeatherSource
        get() = runCatching {
            WeatherSource.valueOf(p.getString("source", WeatherSource.MET_NORWAY.name)!!)
        }.getOrDefault(WeatherSource.MET_NORWAY)
        set(v) {
            p.edit().putString("source", v.name).apply()
        }

    var useGps: Boolean
        get() = p.getBoolean("use_gps", false)
        set(v) {
            p.edit().putBoolean("use_gps", v).apply()
        }

    /** Colore del testo del widget: true = scuro (come nello screenshot di Weawow). */
    var widgetDarkText: Boolean
        get() = p.getBoolean("widget_dark_text", true)
        set(v) {
            p.edit().putBoolean("widget_dark_text", v).apply()
        }

    /** Fattore della dimensione del carattere del widget (1.0 = predefinito). */
    var widgetFontScale: Float
        get() = p.getFloat("wg_scale", 1.0f)
        set(v) {
            p.edit().putFloat("wg_scale", v).apply()
        }

    /** Ogni quanti minuti il widget può richiedere dati nuovi (30, 60, 180, 360). */
    var widgetIntervalMin: Int
        get() = p.getInt("wg_interval", 30)
        set(v) {
            p.edit().putInt("wg_interval", v).apply()
        }

    /** true = il widget usa la posizione corrente (GPS), false = la città scelta per il widget. */
    var widgetUseCurrent: Boolean
        get() = p.getBoolean("wg_current", true)
        set(v) {
            p.edit().putBoolean("wg_current", v).apply()
        }

    /** Località dell'app (ultima scelta o ultima posizione GPS). */
    var place: Place?
        get() = readPlace("place_name", "lat_bits", "lon_bits")
        set(v) = writePlace("place_name", "lat_bits", "lon_bits", v)

    /** Ultima posizione GPS trovata. */
    var gpsPlace: Place?
        get() = readPlace("gps_name", "gps_lat_bits", "gps_lon_bits")
        set(v) = writePlace("gps_name", "gps_lat_bits", "gps_lon_bits", v)

    /** Città scelta per il widget con la ricerca per parola chiave. */
    var widgetPlace: Place?
        get() = readPlace("wg_name", "wg_lat_bits", "wg_lon_bits")
        set(v) = writePlace("wg_name", "wg_lat_bits", "wg_lon_bits", v)

    /** Località che il widget deve mostrare. */
    fun widgetResolvedPlace(): Place? =
        if (widgetUseCurrent) (gpsPlace ?: place) else (widgetPlace ?: place)

    private fun readPlace(n: String, la: String, lo: String): Place? {
        val name = p.getString(n, null) ?: return null
        if (!p.contains(la) || !p.contains(lo)) return null
        return Place(name, Double.fromBits(p.getLong(la, 0L)), Double.fromBits(p.getLong(lo, 0L)))
    }

    private fun writePlace(n: String, la: String, lo: String, v: Place?) {
        val e = p.edit()
        if (v == null) {
            e.remove(n).remove(la).remove(lo)
        } else {
            e.putString(n, v.name).putLong(la, v.lat.toRawBits()).putLong(lo, v.lon.toRawBits())
        }
        e.apply()
    }
}
