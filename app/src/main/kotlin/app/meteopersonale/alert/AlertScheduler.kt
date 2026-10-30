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
    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 4711, Intent(ctx, AlertReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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
        if (!s.alertEnabled) return
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTrigger(s.alertHour, s.alertMinute),
            pending(ctx)
        )
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

class AlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val s = SettingsStore(applicationContext)
        val test = inputData.getBoolean("test", false)
        val today = LocalDate.now().toEpochDay()
        if (!test) {
            if (!s.alertEnabled) return Result.success()
            if (s.alertLastDay == today) return Result.success()
        }
        val place = s.place ?: s.gpsPlace ?: return Result.success()
        val f = try {
            WeatherRepository(applicationContext).load(place, s.source, maxAgeS = 600L)
        } catch (e: Exception) {
            return if (runAttemptCount < 2) Result.retry() else Result.success()
        }
        val info = AlertLogic.evaluate(f.hours, Instant.now())
        if (info.any) {
            AlertNotifier.show(applicationContext, place.name, info, ZoneId.systemDefault())
            if (!test) s.alertLastDay = today
        } else if (test) {
            AlertNotifier.showText(
                applicationContext,
                "Nessuna allerta",
                "Nelle prossime ${AlertLogic.WINDOW_HOURS} ore non sono previsti vento forte o pioggia rilevante.",
                place.name
            )
        }
        return Result.success()
    }
}
