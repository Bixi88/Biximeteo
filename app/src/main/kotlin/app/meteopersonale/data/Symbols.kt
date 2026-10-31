package app.meteopersonale.data

/**
 * Tutte le fonti vengono ridotte ai "symbol_code" di MET Norway
 * (es. partlycloudy_day, heavyrain, rainandthunder), che poi si
 * abbinano alle icone.
 */
object Symbols {
    /**
     * L'icona di MET Norway indica lo scenario più probabile, mentre i mm sono una media:
     * può uscire "rovescio" con 0,3 mm. Qui l'icona di pioggia viene resa coerente con i mm,
     * solo verso il basso (mai più forte del dato della fonte).
     * Orario: debole sotto 2,5 mm/h, moderata sotto 7,6 mm/h. Giornaliero: 2 e 10 mm.
     */
    fun adjustRain(symbol: String, mm: Double, daily: Boolean = false): String {
        val light = if (daily) 2.0 else 2.5
        val moderate = if (daily) 10.0 else 7.6
        val sfx = listOf("_day", "_night", "_polartwilight").firstOrNull { symbol.endsWith(it) } ?: ""
        val base = symbol.removeSuffix(sfx)
        val thunder = base.endsWith("andthunder")
        val core = base.removeSuffix("andthunder")
        val shower = core.endsWith("showers")
        val kind = core.removeSuffix("showers")
        val order = listOf("lightrain", "rain", "heavyrain")
        val idx = order.indexOf(kind)
        if (idx < 0) return symbol
        val target = when {
            mm < light -> 0
            mm < moderate -> 1
            else -> 2
        }
        if (target >= idx) return symbol
        return order[target] + (if (shower) "showers" else "") +
            (if (thunder) "andthunder" else "") + sfx
    }

    private val withDayNight = setOf(
        "clearsky", "fair", "partlycloudy",
        "lightrainshowers", "rainshowers", "heavyrainshowers",
        "lightsleetshowers", "sleetshowers", "heavysleetshowers",
        "lightsnowshowers", "snowshowers", "heavysnowshowers",
        "lightrainshowersandthunder", "rainshowersandthunder", "heavyrainshowersandthunder",
        "lightssleetshowersandthunder", "sleetshowersandthunder", "heavysleetshowersandthunder",
        "lightssnowshowersandthunder", "snowshowersandthunder", "heavysnowshowersandthunder"
    )

    fun fromWmo(code: Int, isDay: Boolean): String {
        val base = when (code) {
            0 -> "clearsky"
            1 -> "fair"
            2 -> "partlycloudy"
            3 -> "cloudy"
            45, 48 -> "fog"
            51, 53, 61 -> "lightrain"
            55, 63 -> "rain"
            65 -> "heavyrain"
            56, 66 -> "lightsleet"
            57, 67 -> "sleet"
            71, 77 -> "lightsnow"
            73 -> "snow"
            75 -> "heavysnow"
            80 -> "lightrainshowers"
            81 -> "rainshowers"
            82 -> "heavyrainshowers"
            85 -> "lightsnowshowers"
            86 -> "snowshowers"
            95 -> "rainandthunder"
            96, 99 -> "heavyrainandthunder"
            else -> "cloudy"
        }
        return if (base in withDayNight) base + (if (isDay) "_day" else "_night") else base
    }

    fun isWet(symbol: String): Boolean =
        listOf("rain", "snow", "sleet", "thunder").any { symbol.contains(it) }

    fun toDay(symbol: String): String =
        symbol.replace("_night", "_day").replace("_polartwilight", "_day")
}
