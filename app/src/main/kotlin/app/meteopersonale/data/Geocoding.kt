package app.meteopersonale.data

import org.json.JSONObject
import java.net.URLEncoder

/** Ricerca città per nome (serve solo per trasformarla in coordinate). */
object Geocoding {
    fun search(query: String, userAgent: String): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val res = Http.get(
            "https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=it&format=json",
            userAgent
        )
        val body = res.body ?: return emptyList()
        val arr = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val label = listOf(
                o.optString("name"),
                o.optString("admin1"),
                o.optString("country_code")
            ).filter { it.isNotBlank() }.joinToString(", ")
            out.add(Place(label, o.getDouble("latitude"), o.getDouble("longitude")))
        }
        return out
    }
}
