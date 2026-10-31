package app.meteopersonale.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.meteopersonale.data.SettingsStore
import app.meteopersonale.update.UpdateInfo
import app.meteopersonale.update.Updater
import kotlinx.coroutines.launch
import java.io.IOException

private val Dim = Color(0xFF9A9A9A)

/** Pagina "Aggiornamenti": controlla le Release di GitHub, scarica e installa. */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val settings = remember { SettingsStore(appCtx) }
    val scope = rememberCoroutineScope()
    val installed = remember { Updater.installedBuild(appCtx) }

    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }

    BackHandler(onBack = onBack)

    fun friendly(e: Exception): String =
        if (e is IOException) "Nessuna connessione o GitHub non raggiungibile"
        else e.message ?: "Errore"

    fun check() {
        scope.launch {
            busy = true
            info = null
            msg = "Controllo in corso…"
            try {
                val u = Updater.fetchLatest(settings.githubToken)
                info = u
                msg = if (u.build.toLong() > installed) {
                    "Disponibile la build ${u.build}"
                } else {
                    "Sei aggiornato (build $installed)"
                }
            } catch (e: Exception) {
                msg = friendly(e)
            } finally {
                busy = false
            }
        }
    }

    fun install(u: UpdateInfo) {
        if (!appCtx.packageManager.canRequestPackageInstalls()) {
            msg = "Consenti a Biximeteo di installare app, poi torna qui e riprova"
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + appCtx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        scope.launch {
            busy = true
            progress = 0f
            msg = "Scarico la build ${u.build}…"
            try {
                val apk = Updater.download(appCtx, u, settings.githubToken) { progress = it }
                msg = "Conferma l'installazione nella finestra di sistema"
                Updater.install(appCtx, apk)
            } catch (e: Exception) {
                msg = friendly(e)
            } finally {
                busy = false
                progress = null
            }
        }
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
            "Aggiornamenti",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
        )
        Text(
            "Build installata: $installed",
            color = Dim,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Button(
            onClick = { check() },
            enabled = !busy,
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        ) { Text("Controlla aggiornamenti") }

        val u = info
        if (u != null && u.build.toLong() > installed) {
            Button(
                onClick = { install(u) },
                enabled = !busy,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) { Text("Scarica e installa la build ${u.build}") }
        }

        val p = progress
        if (p != null) {
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        if (msg.isNotEmpty()) {
            Text(msg, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }

        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }
}
