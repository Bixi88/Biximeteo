package app.meteopersonale.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Button
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.compose.material3.Checkbox
import app.meteopersonale.widget.MeteoWidget
import app.meteopersonale.widget.WidgetUpdater
import app.meteopersonale.BuildConfig
import app.meteopersonale.data.Forecast
import app.meteopersonale.data.Geocoding
import app.meteopersonale.data.Place
import app.meteopersonale.data.SettingsStore
import app.meteopersonale.data.WeatherRepository
import app.meteopersonale.data.WeatherSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun WeatherApp(refreshTick: Int) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val settings = remember { SettingsStore(appCtx) }
    val repo = remember { WeatherRepository(appCtx) }
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    var place by remember { mutableStateOf(settings.place) }
    var source by remember { mutableStateOf(settings.source) }
    var forecast by remember { mutableStateOf<Forecast?>(null) }
    var status by remember { mutableStateOf("") }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var showPopupSettings by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showWidgetSettings by remember { mutableStateOf(false) }
    var forceTick by remember { mutableIntStateOf(0) }
    var lastForce by remember { mutableIntStateOf(0) }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun fetchGps() {
        scope.launch {
            status = "Cerco la posizione…"
            val p = LocationHelper.current(appCtx)
            if (p != null) {
                place = p
                settings.place = p
                settings.gpsPlace = p
                settings.useGps = true
            } else {
                status = "Posizione non disponibile"
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res.values.any { it }) fetchGps() else status = "Permesso di posizione negato"
    }

    fun onGps() {
        if (hasLocationPermission()) {
            fetchGps()
        } else {
            permLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // primo avvio senza località: si prova col GPS
    LaunchedEffect(Unit) {
        if (place == null) onGps()
    }

    // se si usa il GPS, a ogni ritorno nell'app si aggiorna la posizione
    LaunchedEffect(refreshTick) {
        if (refreshTick > 0 && settings.useGps && hasLocationPermission()) fetchGps()
    }

    // caricamento della previsione
    LaunchedEffect(place, source, refreshTick, forceTick) {
        val p = place ?: return@LaunchedEffect
        val force = forceTick != lastForce
        lastForce = forceTick
        status = "Aggiorno…"
        try {
            val f = repo.load(p, source, force)
            forecast = f
            // aggiorna il widget in background (non si interrompe uscendo dall'app)
            WidgetUpdater.refreshData(appCtx)
            val fb = f.fallbackFrom
            val hm = DateTimeFormatter.ofPattern("H:mm").withZone(zone)
            status = if (fb != null) {
                "${fb.label} non risponde: uso ${f.source.label}"
            } else {
                "${f.source.label} · aggiornato ${hm.format(f.fetchedAt)}"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "Nessun dato: ${e.message}"
        }
    }

    BackHandler(enabled = drawerState.currentValue == DrawerValue.Open) {
        scope.launch { drawerState.close() }
    }

    if (showWidgetSettings) WidgetSettingsScreen(onBack = { showWidgetSettings = false }) else if (showPopupSettings) PopupSettingsScreen(onBack = { showPopupSettings = false }) else ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SettingsDrawer(
                source = source,
                onSource = {
                    source = it
                    settings.source = it
                },
                onWidget = {
                    scope.launch {
                        drawerState.snapTo(DrawerValue.Closed)
                        showWidgetSettings = true
                    }
                },
                onPopup = {
                    scope.launch {
                        drawerState.snapTo(DrawerValue.Closed)
                        showPopupSettings = true
                    }
                },
                onReload = {
                    forceTick++
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
        ) {
            Header(
                title = place?.name ?: "Scegli una posizione",
                onMenu = { scope.launch { drawerState.open() } },
                onSearch = { showSearch = true },
                onGps = { onGps() }
            )
            Text(
                status,
                color = Color(0xFF8A8A8A),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            val f = forecast
            if (f != null) {
                val from = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS)
                val to = from.plus(25, ChronoUnit.HOURS) // da -1h a +24h
                HourlySection(f.hours.filter { it.time >= from && it.time <= to }, zone)
                Spacer(Modifier.height(18.dp))
                DailySection(f.days.take(10))
            }
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }

    if (showSearch) {
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<Place>>(emptyList()) }
        var msg by remember { mutableStateOf("") }

        fun doSearch() {
            if (query.isBlank()) return
            scope.launch {
                msg = "Cerco…"
                try {
                    val r = withContext(Dispatchers.IO) {
                        Geocoding.search(query, BuildConfig.USER_AGENT)
                    }
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
            onDismissRequest = { showSearch = false },
            confirmButton = { TextButton(onClick = { showSearch = false }) { Text("Chiudi") } },
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
                    if (msg.isNotEmpty()) Text(msg, color = Color(0xFF8A8A8A))
                    results.forEach { r ->
                        Text(
                            r.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    place = r
                                    settings.place = r
                                    settings.useGps = false
                                    showSearch = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        )
    }
}
