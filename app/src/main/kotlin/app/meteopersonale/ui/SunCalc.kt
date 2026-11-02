package app.meteopersonale.ui

import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Altezza del sole sull'orizzonte (formule astronomiche semplificate): basta per sapere se è notte. */
internal object SunCalc {
    fun elevationDeg(lat: Double, lon: Double, t: Instant): Double {
        val jd = t.epochSecond / 86400.0 + 2440587.5
        val n = jd - 2451545.0
        val l = (280.460 + 0.9856474 * n) % 360.0
        val g = Math.toRadians((357.528 + 0.9856003 * n) % 360.0)
        val lambda = Math.toRadians(l + 1.915 * sin(g) + 0.020 * sin(2 * g))
        val eps = Math.toRadians(23.439 - 0.0000004 * n)
        val dec = asin(sin(eps) * sin(lambda))
        val ra = atan2(cos(eps) * sin(lambda), cos(lambda))
        val gmst = (280.46061837 + 360.98564736629 * n) % 360.0
        val ha = Math.toRadians(gmst + lon) - ra
        val la = Math.toRadians(lat)
        return Math.toDegrees(asin(sin(la) * sin(dec) + cos(la) * cos(dec) * cos(ha)))
    }

    /** Notte = sole sotto l'orizzonte (-0,833° tiene conto del disco solare e della rifrazione). */
    fun isNight(lat: Double, lon: Double, t: Instant): Boolean = elevationDeg(lat, lon, t) < -0.833
}
