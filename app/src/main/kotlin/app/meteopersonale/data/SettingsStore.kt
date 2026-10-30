package app.meteopersonale.data

import android.content.Context

/** Salva la fonte scelta e l'ultima località. */
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

    var place: Place?
        get() {
            val name = p.getString("place_name", null) ?: return null
            if (!p.contains("lat_bits") || !p.contains("lon_bits")) return null
            return Place(
                name,
                Double.fromBits(p.getLong("lat_bits", 0L)),
                Double.fromBits(p.getLong("lon_bits", 0L))
            )
        }
        set(v) {
            val e = p.edit()
            if (v == null) {
                e.remove("place_name").remove("lat_bits").remove("lon_bits")
            } else {
                e.putString("place_name", v.name)
                    .putLong("lat_bits", v.lat.toRawBits())
                    .putLong("lon_bits", v.lon.toRawBits())
            }
            e.apply()
        }
}
