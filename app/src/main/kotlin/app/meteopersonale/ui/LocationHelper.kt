package app.meteopersonale.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import app.meteopersonale.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object LocationHelper {

    /** Posizione attuale con un nome leggibile (via + località) quando disponibile. */
    @SuppressLint("MissingPermission")
    suspend fun current(ctx: Context): Place? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc: Location? = try {
            locate(ctx, lm)
        } catch (e: SecurityException) {
            null
        }
        if (loc == null) return null
        val name = withContext(Dispatchers.IO) { reverseName(ctx, loc.latitude, loc.longitude) }
        return Place(name, loc.latitude, loc.longitude)
    }

    @SuppressLint("MissingPermission")
    private suspend fun locate(ctx: Context, lm: LocationManager): Location? {
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val known = providers.mapNotNull { p ->
            try {
                lm.getLastKnownLocation(p)
            } catch (e: Exception) {
                null
            }
        }
        // una posizione recente (meno di 30 minuti) basta
        known.filter { System.currentTimeMillis() - it.time < 30 * 60 * 1000 }
            .maxByOrNull { it.time }
            ?.let { return it }

        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) }
            ?: return known.maxByOrNull { it.time }

        val fresh = withTimeoutOrNull(15000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cs = CancellationSignal()
                cont.invokeOnCancellation { cs.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    lm, provider, cs, ContextCompat.getMainExecutor(ctx)
                ) { l -> cont.resume(l) }
            }
        }
        return fresh ?: known.maxByOrNull { it.time }
    }

    @Suppress("DEPRECATION")
    private fun reverseName(ctx: Context, lat: Double, lon: Double): String {
        return try {
            if (!Geocoder.isPresent()) return "Posizione attuale"
            val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            val parts = listOfNotNull(a?.thoroughfare, a?.locality ?: a?.subAdminArea)
                .filter { it.isNotBlank() }
            if (parts.isEmpty()) "Posizione attuale" else parts.joinToString(", ")
        } catch (e: Exception) {
            "Posizione attuale"
        }
    }
}
