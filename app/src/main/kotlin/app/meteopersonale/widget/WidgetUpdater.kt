package app.meteopersonale.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.meteopersonale.data.SettingsStore
import app.meteopersonale.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Aggiornamento del widget indipendente dalla schermata: non si interrompe
 * quando si esce dall'app e si ripete da solo ogni 15 minuti.
 */
object WidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Ridisegna subito il widget con i dati già in cache (cambi di aspetto). */
    fun redraw(context: Context) {
        val app = context.applicationContext
        scope.launch { runCatching { MeteoWidget().updateAll(app) } }
    }

    /** Scarica i dati se servono e poi ridisegna (in background, anche a app chiusa). */
    fun refreshData(context: Context, force: Boolean = false) {
        val req = OneTimeWorkRequestBuilder<WidgetWorker>()
            .setInputData(workDataOf("force" to force))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("widget_now", ExistingWorkPolicy.REPLACE, req)
    }

    /** Ridisegno periodico (minimo consentito da Android: 15 minuti). */
    fun schedulePeriodic(context: Context) {
        val req = PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork("widget_periodic", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class WidgetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val s = SettingsStore(applicationContext)
        val place = s.widgetResolvedPlace()
        if (place != null) {
            runCatching {
                WeatherRepository(applicationContext).load(
                    place, s.source,
                    force = inputData.getBoolean("force", false),
                    maxAgeS = s.widgetIntervalMin * 60L
                )
            }
        }
        runCatching { MeteoWidget().updateAll(applicationContext) }
        return Result.success()
    }
}
