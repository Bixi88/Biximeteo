package app.meteopersonale.alert

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.meteopersonale.R
import app.meteopersonale.ui.MainActivity
import app.meteopersonale.ui.WeatherIcons
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Il popup: una notifica in primo piano (heads-up) con vento e/o pioggia. */
object AlertNotifier {
    const val CHANNEL = "allerte"
    private const val ID = 1001

    private fun ensureChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Allerte vento e pioggia", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Popup con vento e pioggia previsti"
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(ch)
    }

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun show(ctx: Context, placeName: String, info: AlertInfo, zone: ZoneId) {
        val hm = DateTimeFormatter.ofPattern("H:mm").withZone(zone)
        val lines = ArrayList<String>()
        info.rain?.let {
            lines.add(
                "Pioggia dalle ${hm.format(it.from)} alle ${hm.format(it.to)} · " +
                    String.format(Locale.ITALY, "%.1f", it.totalMm) + " mm"
            )
        }
        info.wind?.let { w ->
            val gust = w.gustKmh?.let { ", raffiche $it km/h" } ?: ""
            lines.add("Vento fino a ${w.maxKmh} km/h$gust dalle ${hm.format(w.from)} alle ${hm.format(w.to)}")
        }
        if (lines.isEmpty()) return
        val title = when {
            info.rain != null && info.wind != null -> "Vento e pioggia in arrivo"
            info.rain != null -> "Sta per piovere"
            else -> "Vento forte in arrivo"
        }
        post(ctx, title, lines, placeName, info.rain != null)
    }

    /** Testo libero (usato dalla prova quando non c'è nulla da segnalare). */
    fun showText(ctx: Context, title: String, text: String, placeName: String) =
        post(ctx, title, listOf(text), placeName, false)

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, title: String, lines: List<String>, placeName: String, rain: Boolean) {
        if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) return
        ensureChannel(ctx)
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(if (rain) R.drawable.ic_stat_rain else R.drawable.ic_stat_wind)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setSubText(placeName)
            .setColor(0xFF6750A4.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
        runCatching {
            b.setLargeIcon(
                BitmapFactory.decodeResource(
                    ctx.resources, WeatherIcons.resFor(if (rain) "rain" else "cloudy")
                )
            )
        }
        NotificationManagerCompat.from(ctx).notify(ID, b.build())
    }
}
