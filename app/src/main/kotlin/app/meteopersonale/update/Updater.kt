package app.meteopersonale.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Una release trovata su GitHub. */
data class UpdateInfo(
    val build: Int,
    val tag: String,
    val notes: String,
    val assetApiUrl: String,
    val size: Long
)

/**
 * Aggiornamenti dalle Release di GitHub (il workflow le crea a ogni build verde):
 * controllo, download con avanzamento e installazione con PackageInstaller,
 * senza uscire dall'app. Android chiede comunque una conferma di sistema.
 */
object Updater {
    const val OWNER = "Bixi88"
    const val REPO = "Biximeteo"

    /** Numero di build installata (il workflow lo passa come versionCode). */
    fun installedBuild(ctx: Context): Long {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(pi)
    }

    private fun open(url: String, token: String?, accept: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = false
        c.setRequestProperty("Accept", accept)
        c.setRequestProperty("User-Agent", "Biximeteo")
        if (!token.isNullOrBlank()) c.setRequestProperty("Authorization", "Bearer " + token.trim())
        return c
    }

    suspend fun fetchLatest(token: String?): UpdateInfo = withContext(Dispatchers.IO) {
        val c = open(
            "https://api.github.com/repos/$OWNER/$REPO/releases/latest",
            token, "application/vnd.github+json"
        )
        val code = c.responseCode
        if (code == 401) error("Token GitHub non valido o scaduto")
        if (code == 404) error("Nessuna release trovata su GitHub")
        if (code != 200) error("Errore di GitHub ($code)")
        val o = JSONObject(c.inputStream.bufferedReader().readText())
        val tag = o.getString("tag_name")
        val build = tag.filter { it.isDigit() }.toIntOrNull() ?: 0
        val assets = o.getJSONArray("assets")
        var url = ""
        var size = 0L
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                url = a.getString("url")
                size = a.optLong("size", 0L)
                break
            }
        }
        if (url.isEmpty()) error("La release non contiene un APK")
        UpdateInfo(build, tag, o.optString("body", ""), url, size)
    }

    /** Scarica l'APK nella cache; [onProgress] va da 0 a 1. */
    suspend fun download(
        ctx: Context,
        info: UpdateInfo,
        token: String?,
        onProgress: (Float) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Biximeteo-${info.build}.apk")

        var c = open(info.assetApiUrl, token, "application/octet-stream")
        var code = c.responseCode
        var hops = 0
        while ((code in 301..303 || code == 307 || code == 308) && hops < 5) {
            val loc = c.getHeaderField("Location") ?: break
            c.disconnect()
            // il file vero sta su un altro server: lì il token non va mandato
            c = open(loc, null, "application/octet-stream")
            code = c.responseCode
            hops++
        }
        if (code != 200) error("Download non riuscito ($code)")

        val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
        var done = 0L
        c.inputStream.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        onProgress(1f)
        out
    }

    /** Passa l'APK al PackageInstaller: Android mostra la conferma "Aggiornare l'app?". */
    suspend fun install(ctx: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setSize(apk.length())
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("update.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, InstallResultReceiver::class.java), flags
            )
            session.commit(pending.intentSender)
        }
    }
}

/** Riceve l'esito dell'installazione e mostra la conferma di sistema. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(confirm)
            }
        }
    }
}
