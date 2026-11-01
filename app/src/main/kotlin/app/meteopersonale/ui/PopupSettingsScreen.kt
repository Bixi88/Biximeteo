package app.meteopersonale.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.meteopersonale.alert.AlertLogic
import app.meteopersonale.alert.AlertScheduler
import app.meteopersonale.data.SettingsStore
import java.util.Locale

private val Accent = Color(0xFFD0BCFF) // lilla
private val Dim = Color(0xFF9A9A9A)

/** Pagina "Impostazioni popup": attivazione, ora di comparsa e prova. */
@Composable
fun PopupSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val settings = remember { SettingsStore(appCtx) }
    var enabled by remember { mutableStateOf(settings.alertEnabled) }
    var hour by remember { mutableIntStateOf(settings.alertHour) }
    var minute by remember { mutableIntStateOf(settings.alertMinute) }
    var msg by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    fun isIgnoringBattery(): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(appCtx.packageName)

    var ignoring by remember { mutableStateOf(isIgnoringBattery()) }
    val battLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { ignoring = isIgnoringBattery() }

    var exactOk by remember { mutableStateOf(AlertScheduler.canExact(appCtx)) }
    val exactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        exactOk = AlertScheduler.canExact(appCtx)
        AlertScheduler.schedule(appCtx)
    }

    fun enable() {
        enabled = true
        settings.alertEnabled = true
        settings.alertLastDay = -1L // nuova attivazione: può comparire anche oggi
        AlertScheduler.schedule(appCtx)
        msg = ""
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enable() else msg = "Senza il permesso per le notifiche il popup non può comparire"
    }

    fun hasNotifPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun setEnabled(on: Boolean) {
        if (!on) {
            enabled = false
            settings.alertEnabled = false
            AlertScheduler.schedule(appCtx)
        } else if (hasNotifPermission()) {
            enable()
        } else {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun pickTime() {
        TimePickerDialog(
            ctx,
            { _, h, m ->
                hour = h
                minute = m
                settings.alertHour = h
                settings.alertMinute = m
                settings.alertLastDay = -1L // orario cambiato: può ricomparire anche oggi
                AlertScheduler.schedule(appCtx)
            },
            hour, minute, true
        ).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
    ) {
        Text(
            "←",
            color = Color.White,
            fontSize = 26.sp,
            modifier = Modifier
                .clickable { onBack() }
                .padding(horizontal = 18.dp, vertical = 8.dp)
        )
        Text(
            "Impostazioni popup",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { setEnabled(!enabled) }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mostra il popup", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = { setEnabled(it) })
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clickable { pickTime() }
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Ora del popup", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(String.format(Locale.US, "%d:%02d", hour, minute), color = Accent, fontSize = 18.sp)
        }

        Text(
            "Il popup compare solo a quest'ora, e solo se nelle prossime " +
                "${AlertLogic.WINDOW_HOURS} ore ci sono vento forte (da ${AlertLogic.WIND_KMH.toInt()} km/h, " +
                "o raffiche da ${AlertLogic.GUST_KMH.toInt()} km/h) o pioggia (almeno " +
                "${AlertLogic.RAIN_TOTAL_MM.toInt()} mm in totale). Se ci sono entrambi mostra vento e pioggia.",
            color = Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Text(
            "RISPARMIO BATTERIA",
            color = Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp)
        )
        Text(
            if (ignoring) {
                "Biximeteo è escluso dal risparmio batteria: il popup può comparire puntuale."
            } else {
                "Il risparmio batteria può far ritardare o saltare il popup. Se non compare all'ora scelta, escludi Biximeteo."
            },
            color = Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (!ignoring) {
            Button(
                onClick = {
                    battLauncher.launch(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + appCtx.packageName)
                        )
                    )
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(start = 24.dp, top = 10.dp)
            ) { Text("Escludi dal risparmio batteria") }
        }

        Text(
            "ALLARME PUNTUALE",
            color = Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 6.dp)
        )
        Text(
            if (exactOk) {
                "Permesso concesso: il popup scatta all'ora esatta, anche con il telefono in standby."
            } else {
                "Senza questo permesso Android può ritardare il popup di ore con l'app chiusa. Concedilo per averlo puntuale."
            },
            color = Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        if (!exactOk && Build.VERSION.SDK_INT >= 31) {
            Button(
                onClick = {
                    exactLauncher.launch(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + appCtx.packageName)
                        )
                    )
                },
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(start = 24.dp, top = 10.dp)
            ) { Text("Consenti allarmi esatti") }
        }

        Text(
            "DIAGNOSTICA",
            color = Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 6.dp)
        )
        listOf(settings.alertSched, settings.alertFired, settings.alertResult)
            .filter { it.isNotEmpty() }
            .forEach {
                Text(it, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
            }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (!NotificationManagerCompat.from(appCtx).areNotificationsEnabled()) {
                    msg = "Le notifiche sono disattivate: attiva il popup e concedi il permesso"
                } else {
                    AlertScheduler.test(appCtx)
                    msg = "Prova inviata: tra qualche secondo compare il popup"
                }
            },
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(horizontal = 24.dp)
        ) { Text("Invia popup di prova") }

        if (msg.isNotEmpty()) {
            Text(msg, color = Dim, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
        }
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}
