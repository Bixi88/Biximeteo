package app.meteopersonale.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.meteopersonale.data.SettingsStore
import app.meteopersonale.data.WeatherRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object AlertScheduler {
    private val stampFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM HH:mm:ss", java.util.Locale.ITALIAN)

    /** Data e ora leggibili, per la diagnostica. */
    fun stamp(ms: Long = System.currentTimeMillis()): String =
        stampFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 4711, Intent(ctx, AlertReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Su Android 12+ gli allarmi esatti richiedono un permesso speciale; prima non serve. */
    fun canExact(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
        val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun nextTrigger(hour: Int, minute: Int, from: ZonedDateTime = ZonedDateTime.now()): Long {
        var t = from.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!t.isAfter(from)) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    /** (Ri)programma la prossima comparsa del popup, o la annulla se è disattivato. */
    fun schedule(context: Context) {
        val ctx = context.applicationContext
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx))
        val s = SettingsStore(ctx)
        if (!s.alertEnabled) {
            s.alertSched = "Popup disattivato"
            return
        }
        val at = nextTrigger(s.alertHour, s.alertMinute)
        // setExact...: puntuale anche in Doze e SENZA icona sveglia (che compare solo con setAlarmClock).
        val exact = canExact(ctx) && runCatching {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx))
        }.isSuccess
        if (!exact) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx))
        s.alertSched = (if (exact) "Allarme esatto programmato per " else "Allarme NON esatto (manca il permesso) per ") + stamp(at)
    }

    /** Invia subito un popup di prova con i dati veri. */
    fun test(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueue(
            OneTimeWorkRequestBuilder<AlertWorker>().setInputData(workDataOf("test" to true)).build()
        )
    }
}

/** Scatta all'ora scelta: riprogramma il giorno dopo e controlla le previsioni. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SettingsStore(context.applicationContext).alertFired =
            "Allarme scattato il " + AlertScheduler.stamp()
        AlertScheduler.schedule(context)
        WorkManager.getInstance(context.applicationContext)
            .enqueue(OneTimeWorkRequestBuilder<AlertWorker>().build())
    }
}

/** Dopo il riavvio o l'aggiornamento dell'app gli allarmi vanno riprogrammati. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AlertScheduler.schedule(context)
    }
}

/** Controllo previsioni e notifica: usato dall'allarme, dalla prova e dalla rete di sicurezza. */
object AlertRunner {
    /** @return false se il download è fallito (si può riprovare). */
    suspend fun run(ctx: Context, test: Boolean, source: String = "allarme"): Boolean {
        val s = SettingsStore(ctx)
        val today = LocalDate.now().toEpochDay()
        if (!test) {
            if (!s.alertEnabled) return true
            if (s.alertLastDay == today) return true
        }
        val place = s.place ?: s.gpsPlace ?: return true
        val f = try {
            WeatherRepository(ctx).load(place, s.source, maxAgeS = 600L)
        } catch (e: Exception) {
            s.alertResult = AlertScheduler.stamp() + " ($source): previsioni non scaricate"
            return false
        }
        val info = AlertLogic.evaluate(f.hours, Instant.now())
        s.alertResult = AlertScheduler.stamp() + " ($source): " + when {
            info.rain != null && info.wind != null -> "avviso vento e pioggia"
            info.rain != null -> "avviso pioggia"
            info.wind != null -> "avviso vento"
            else -> "nessuna allerta"
        }
        if (info.any) {
            AlertNotifier.show(ctx, place.name, info, ZoneId.systemDefault())
            if (!test) s.alertLastDay = today
        } else if (test) {
            AlertNotifier.showText(
                ctx,
                "Nessuna allerta",
                "Nelle prossime ${AlertLogic.WINDOW_HOURS} ore non sono previsti vento forte o pioggia rilevante.",
                place.name
            )
        }
        return true
    }

    /**
     * Rete di sicurezza: se l'allarme del telefono non è partito (risparmio energetico),
     * il controllo periodico del widget fa comparire il popup entro un'ora dall'ora scelta.
     */
    suspend fun fallbackIfDue(ctx: Context) {
        val s = SettingsStore(ctx)
        if (!s.alertEnabled) return
        val now = java.time.LocalTime.now()
        val at = java.time.LocalTime.of(s.alertHour, s.alertMinute)
        if (now.isBefore(at) || now.isAfter(at.plusHours(1)) || at.plusHours(1).isBefore(at)) return
        run(ctx, false, "controllo di sicurezza")
    }
}

class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val isTest = inputData.getBoolean("test", false)
        val ok = AlertRunner.run(applicationContext, isTest, if (isTest) "prova" else "allarme")
        return if (!ok && runAttemptCount < 2) Result.retry() else Result.success()
    }
}
