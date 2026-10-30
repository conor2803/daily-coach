package io.github.conor2803.coach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.conor2803.coach.coach.Engine
import io.github.conor2803.coach.coach.ModelState
import io.github.conor2803.coach.coach.OnDeviceModel
import io.github.conor2803.coach.coach.Scheduler
import io.github.conor2803.coach.context.CalendarReader
import io.github.conor2803.coach.context.ContextBuilder
import io.github.conor2803.coach.health.HealthReader
import io.github.conor2803.coach.places.GeofenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    /** Bumped every time the app comes to the front, so permission states refresh. */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize()) { CoachScreen(resumeTick.intValue) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

private val whenFmt = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)

@Composable
private fun CoachScreen(tick: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()

    var advice by remember { mutableStateOf(prefs.lastAdvice) }
    var lastError by remember { mutableStateOf(prefs.lastError) }
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var model by remember { mutableStateOf(prefs.model) }
    var engine by remember { mutableStateOf(prefs.engine) }
    var modelState by remember { mutableStateOf(OnDeviceModel.state(context)) }
    var goals by remember { mutableStateOf(prefs.goals) }
    var times by remember { mutableStateOf(prefs.checkInTimes) }
    var places by remember { mutableStateOf(prefs.places) }
    var newPlaceName by remember { mutableStateOf("Home") }
    var preview by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var hcMissing by remember { mutableStateOf<Set<String>?>(null) }

    val hcAvailable = remember(tick) { HealthReader.isAvailable(context) }
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    // Refresh status whenever the app is reopened.
    LaunchedEffect(tick) {
        advice = prefs.lastAdvice
        lastError = prefs.lastError
        hcMissing = if (hcAvailable) runCatching { HealthReader(context).missingPermissions() }.getOrNull() else null
        GeofenceManager.register(context)
    }

    // Follow the model download while it runs, then check the file.
    LaunchedEffect(tick, modelState is ModelState.Downloading) {
        modelState = OnDeviceModel.state(context)
        while (modelState is ModelState.Downloading) {
            delay(1_000)
            modelState = OnDeviceModel.state(context)
        }
        if (modelState == ModelState.Verifying) modelState = OnDeviceModel.verify(context)
    }

    // Reload the advice when a "check in now" run finishes.
    val nowRunsFlow = remember {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(Scheduler.NOW)
    }
    val nowRuns by nowRunsFlow.collectAsState(initial = emptyList())
    val running = nowRuns.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    LaunchedEffect(nowRuns) {
        if (nowRuns.any { it.state.isFinished }) {
            advice = prefs.lastAdvice
            lastError = prefs.lastError
        }
    }

    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedSet -> hcMissing = HealthReader.PERMISSIONS - grantedSet }

    val permsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { message = "Permissions updated" }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        message = if (ok) "Places will now log in the background" else "Pick \"Allow all the time\" for places to work"
        GeofenceManager.register(context)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Coach", style = MaterialTheme.typography.headlineMedium)

        // ---- Latest advice ----
        Section("Latest check-in") {
            val a = advice
            if (a == null) {
                Text("No check-in yet. Finish setup below, then tap Check in now.")
            } else {
                Text(a.headline, style = MaterialTheme.typography.titleMedium)
                if (a.plan.isNotBlank()) Text(a.plan)
                Text(
                    Instant.ofEpochMilli(a.at).atZone(ZoneId.systemDefault()).format(whenFmt),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (lastError.isNotBlank()) {
                Text("Last problem: $lastError", color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Scheduler.runNow(context) }, enabled = !running) {
                    Text(if (running) "Checking in…" else "Check in now")
                }
                OutlinedButton(onClick = {
                    scope.launch { preview = ContextBuilder.build(context) }
                }) { Text("Preview data") }
            }
            Text(
                "Next scheduled: " + Scheduler.nextRun(context).format(whenFmt),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        preview?.let { text ->
            Section(if (engine == Engine.CLAUDE) "What gets sent to Claude" else "What the on-device model reads") {
                SelectionContainer {
                    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                TextButton(onClick = { preview = null }) { Text("Hide") }
            }
        }

        // ---- Permissions ----
        Section("Permissions") {
            if (!hcAvailable) {
                PermissionRow("Health Connect not installed", false) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                    )
                }
            } else {
                val missing = hcMissing
                val dataOk = missing != null && (missing - HealthReader.BACKGROUND_READ).isEmpty()
                PermissionRow("Health Connect (Garmin + ring)", dataOk) {
                    hcLauncher.launch(HealthReader.PERMISSIONS)
                }
                if (missing != null && missing.isNotEmpty() && missing != HealthReader.PERMISSIONS) {
                    Text(
                        "Missing: " + missing.joinToString { it.substringAfterLast('.') },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (dataOk && HealthReader.BACKGROUND_READ in missing!!) {
                    Text(
                        "Background access isn't granted, so scheduled check-ins can't see health data. " +
                            "Allow \"Access data in the background\" for Coach in Health Connect.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            val foreground = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.READ_CALENDAR)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }
            PermissionRow("Location, calendar, notifications", foreground.all { granted(it) }) {
                permsLauncher.launch(foreground.toTypedArray())
            }
            PermissionRow(
                "Location \"all the time\" (for places)",
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                enabled = granted(Manifest.permission.ACCESS_FINE_LOCATION),
            ) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            if (!CalendarReader.hasPermission(context)) {
                Text("Calendar is optional but helps it plan around your evening.", style = MaterialTheme.typography.labelSmall)
            }
        }

        // ---- Places ----
        Section("Places") {
            Text(
                "Stand at a place and save it. The phone then logs when you arrive and leave.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (places.isEmpty()) Text("None yet")
            places.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, Modifier.weight(1f))
                    TextButton(onClick = {
                        places = places - p
                        prefs.places = places
                        GeofenceManager.register(context)
                    }) { Text("Remove") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newPlaceName,
                    onValueChange = { newPlaceName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(enabled = newPlaceName.isNotBlank(), onClick = {
                    val name = newPlaceName.trim()
                    scope.launch {
                        message = "Getting your location…"
                        val loc = GeofenceManager.currentLocation(context)
                        if (loc == null) {
                            message = "Couldn't get a location. Check location permission and GPS."
                            return@launch
                        }
                        places = places.filterNot { it.name.equals(name, ignoreCase = true) } +
                            Place(name, loc.latitude, loc.longitude)
                        prefs.places = places
                        message = GeofenceManager.register(context) ?: "Saved $name"
                    }
                }) { Text("Save here") }
            }
        }

        // ---- Settings ----
        Section("Coach settings") {
            Text("Engine", style = MaterialTheme.typography.labelLarge)
            EngineOption("On-device (free, your data stays on the phone)", engine == Engine.ON_DEVICE) {
                engine = Engine.ON_DEVICE
            }
            EngineOption("Claude (paid per check-in)", engine == Engine.CLAUDE) { engine = Engine.CLAUDE }

            when (engine) {
                Engine.ON_DEVICE -> ModelDownload(modelState) {
                    OnDeviceModel.download(context)
                    modelState = OnDeviceModel.state(context)
                }
                Engine.CLAUDE -> {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Anthropic API key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Claude model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            OutlinedTextField(
                value = goals,
                onValueChange = { goals = it },
                label = { Text("Goals, bedtime, anything it should know") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = times,
                onValueChange = { times = it },
                label = { Text("Check-in times (e.g. 07:30,16:30)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = {
                prefs.engine = engine
                prefs.apiKey = apiKey
                prefs.goals = goals
                prefs.checkInTimes = times
                prefs.model = model
                Scheduler.reschedule(context)
                message = "Saved. Check-ins at " +
                    Scheduler.parseTimes(times).joinToString { it.toString() }
            }) { Text("Save settings") }
        }

        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun EngineOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ModelDownload(state: ModelState, onDownload: () -> Unit) {
    when (state) {
        ModelState.Ready -> Text("✓ ${OnDeviceModel.NAME} downloaded")
        is ModelState.Downloading -> {
            Text("Downloading ${OnDeviceModel.NAME}… " + (state.percent?.let { "$it%" } ?: "starting"))
            LinearProgressIndicator(progress = { (state.percent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
        }
        ModelState.Verifying -> {
            Text("Checking the downloaded model…")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ModelState.Missing, is ModelState.Failed -> {
            if (state is ModelState.Failed) Text(state.message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onDownload) { Text("Download model (${OnDeviceModel.SIZE}, Wi-Fi)") }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, enabled: Boolean = true, onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text((if (granted) "✓ " else "• ") + label, Modifier.weight(1f))
        if (!granted) {
            TextButton(onClick = onGrant, enabled = enabled) { Text("Allow") }
        }
    }
}
