package app.meteopersonale.data

/**
 * Tutte le fonti vengono ridotte ai "symbol_code" di MET Norway
 * (es. partlycloudy_day, heavyrain, rainandthunder), che poi si
 * abbinano alle icone.
 */
object Symbols {
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
