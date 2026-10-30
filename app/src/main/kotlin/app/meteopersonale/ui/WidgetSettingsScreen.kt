package app.meteopersonale.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import app.meteopersonale.BuildConfig
import app.meteopersonale.data.DayPoint
import app.meteopersonale.data.Geocoding
import app.meteopersonale.data.Place
import app.meteopersonale.data.SettingsStore
import app.meteopersonale.data.WeatherRepository
import app.meteopersonale.data.HourPoint
import app.meteopersonale.widget.MeteoWidget
import app.meteopersonale.widget.WidgetCol
import app.meteopersonale.widget.WidgetUpdater
import app.meteopersonale.widget.buildWidgetColumns
import app.meteopersonale.widget.WidgetMetrics
import app.meteopersonale.widget.widgetDayName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Accent = Color(0xFFD0BCFF) // lilla
private val Dim = Color(0xFF9A9A9A)

private val intervals = listOf(30 to "30 minuti", 60 to "1 ora", 180 to "3 ore", 360 to "6 ore")

/** Pagina "Impostazioni widget": anteprima in tempo reale + opzioni. */
@Composable
fun WidgetSettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val settings = remember { SettingsStore(appCtx) }
    val repo = remember { WeatherRepository(appCtx) }
    val scope = rememberCoroutineScope()

    var useCurrent by remember { mutableStateOf(settings.widgetUseCurrent) }
    var wPlace by remember { mutableStateOf(settings.widgetPlace) }
    var scale by remember { mutableFloatStateOf(settings.widgetFontScale) }
    var interval by remember { mutableIntStateOf(settings.widgetIntervalMin) }
    var dark by remember { mutableStateOf(settings.widgetDarkText) }
    var daysCount by remember { mutableIntStateOf(settings.widgetDays) }
    var current by remember { mutableStateOf<HourPoint?>(null) }
    var days by remember { mutableStateOf<List<DayPoint>>(emptyList()) }
    var msg by remember { mutableStateOf("") }
    var reloadTick by remember { mutableIntStateOf(0) }
    var showSearch by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    fun refreshWidget() {
        WidgetUpdater.redraw(appCtx)
        WidgetUpdater.refreshData(appCtx)
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun findGps() {
        scope.launch {
            val p = LocationHelper.current(appCtx)
            if (p != null) settings.gpsPlace = p
            reloadTick++
            refreshWidget()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res -> if (res.values.any { it }) findGps() else msg = "Permesso di posizione negato" }

    fun chooseCurrent() {
        useCurrent = true
        settings.widgetUseCurrent = true
        if (hasLocationPermission()) {
            findGps()
        } else {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        reloadTick++
        refreshWidget()
    }

    // carica i giorni per l'anteprima (stessa località e stessa fonte del widget)
    LaunchedEffect(useCurrent, wPlace, reloadTick) {
        val p = settings.widgetResolvedPlace()
        if (p == null) {
            days = emptyList()
            msg = "Nessuna posizione: scegline una"
            return@LaunchedEffect
        }
        try {
            val f = repo.load(p, settings.source, maxAgeS = 3600L)
            val today = LocalDate.now()
            days = f.days.filter { !it.date.isBefore(today) }.take(7)
            val now = Instant.now()
            current = f.hours.lastOrNull { it.time <= now && it.time > now.minus(2, ChronoUnit.HOURS) }
            msg = ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            msg = "Nessun dato"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
    ) {
        // anteprima in alto, su uno sfondo che richiama la schermata iniziale
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        if (dark) listOf(Color(0xFFC9B8DB), Color(0xFFE6D9EC))
                        else listOf(Color(0xFF3D2A55), Color(0xFF5B4378))
                    )
                )
                .statusBarsPadding()
        ) {
            Text(
                "←",
                color = if (dark) Color(0xFF101010) else Color.White,
                fontSize = 26.sp,
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
            WidgetPreview(buildWidgetColumns(days, current, daysCount), scale, dark, msg)
            Spacer(Modifier.height(14.dp))
        }

        Spacer(Modifier.height(18.dp))

        // ---- dimensione carattere
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dimensione carattere", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(
                if (Math.abs(scale - 1.0f) < 0.01f) "Predefinito" else "${Math.round(scale * 100)}%",
                color = Dim,
                fontSize = 15.sp
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("A", color = Dim, fontSize = 13.sp)
            Slider(
                value = scale,
                onValueChange = { scale = Math.round(it * 10f) / 10f },
                onValueChangeFinished = {
                    settings.widgetFontScale = scale
                    refreshWidget()
                },
                valueRange = 0.8f..1.6f,
                steps = 7,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text("A", color = Color.White, fontSize = 24.sp)
        }

        Divider2()

        // ---- giorni a vista
        Text(
            "Giorni a vista",
            color = Color.White,
            fontSize = 17.sp,
            modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 8.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            (4..7).forEach { n ->
                val sel = daysCount == n
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (sel) Accent else Color(0xFF1E1E1E))
                        .clickable {
                            daysCount = n
                            settings.widgetDays = n
                            refreshWidget()
                        }
                        .padding(horizontal = 22.dp, vertical = 9.dp)
                ) {
                    Text("$n", color = if (sel) Color(0xFF381E72) else Color.White, fontSize = 16.sp)
                }
            }
        }
        Text(
            "Predefinito: 6",
            color = Dim,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp)
        )

        Divider2()

        // ---- testo scuro
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    dark = !dark
                    settings.widgetDarkText = dark
                    refreshWidget()
                }
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Widget testo scuro", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = dark,
                onCheckedChange = {
                    dark = it
                    settings.widgetDarkText = it
                    refreshWidget()
                }
            )
        }

        Divider2()

        // ---- luogo
        Text(
            "Luogo",
            color = Color.White,
            fontSize = 17.sp,
            modifier = Modifier.padding(start = 24.dp, top = 6.dp, bottom = 4.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { showSearch = true }
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ricerca per parola chiave", color = Color.White, fontSize = 16.sp)
                if (!useCurrent && wPlace != null) {
                    Text(wPlace!!.name, color = Accent, fontSize = 13.sp)
                }
            }
            RadioButton(selected = !useCurrent, onClick = { showSearch = true })
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { chooseCurrent() }
                .padding(horizontal = 24.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Posizione corrente", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            RadioButton(selected = useCurrent, onClick = { chooseCurrent() })
        }

        Divider2()

        // ---- intervallo di aggiornamento
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true }
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Intervallo di aggiornamento", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Column {
                Text(
                    intervals.firstOrNull { it.first == interval }?.second ?: "30 minuti",
                    color = Dim,
                    fontSize = 15.sp
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    intervals.forEach { (min, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                interval = min
                                settings.widgetIntervalMin = min
                                menuOpen = false
                                refreshWidget()
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }

    if (showSearch) {
        PlaceSearchDialog(
            onPick = { p ->
                wPlace = p
                settings.widgetPlace = p
                useCurrent = false
                settings.widgetUseCurrent = false
                showSearch = false
                reloadTick++
                refreshWidget()
            },
            onDismiss = { showSearch = false }
        )
    }
}

@Composable
private fun Divider2() {
    Spacer(Modifier.height(10.dp))
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFF222222))
    )
    Spacer(Modifier.height(10.dp))
}

/** Anteprima: stesse misure e stessi colori del widget vero. */
@Composable
private fun WidgetPreview(cols: List<WidgetCol>, scale: Float, dark: Boolean, msg: String) {
    val main = if (dark) Color(0xFF101010) else Color.White
    val sub = if (dark) Color(0xFF444444) else Color(0xFFDDDDDD)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        if (cols.isEmpty()) {
            Text(
                if (msg.isEmpty()) "Carico…" else msg,
                color = main,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 22.dp)
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                cols.forEach { d ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            d.name,
                            color = main,
                            fontSize = (WidgetMetrics.DAY_SP * scale).sp,
                            maxLines = 1
                        )
                        Image(
                            painter = painterResource(app.meteopersonale.ui.WeatherIcons.resFor(d.symbol)),
                            contentDescription = null,
                            modifier = Modifier.size((WidgetMetrics.ICON_DP * scale).dp)
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${d.bigC}°",
                                color = main,
                                fontSize = (WidgetMetrics.MAX_SP * scale).sp,
                                maxLines = 1
                            )
                            Text(
                                "${d.smallC}°",
                                color = sub,
                                fontSize = (WidgetMetrics.MIN_SP * scale).sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ricerca di una città per nome (geocoding di Open-Meteo). */
@Composable
fun PlaceSearchDialog(onPick: (Place) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var msg by remember { mutableStateOf("") }

    fun doSearch() {
        if (query.isBlank()) return
        scope.launch {
            msg = "Cerco…"
            try {
                val r = withContext(Dispatchers.IO) { Geocoding.search(query, BuildConfig.USER_AGENT) }
                results = r
                msg = if (r.isEmpty()) "Nessun risultato" else ""
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                msg = "Ricerca non riuscita"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        title = { Text("Cerca città") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() })
                )
                TextButton(onClick = { doSearch() }) { Text("Cerca") }
                if (msg.isNotEmpty()) Text(msg, color = Dim)
                results.forEach { r ->
                    Text(
                        r.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(r) }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }
    )
}
