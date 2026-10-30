package io.github.conor2803.coach.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import io.github.conor2803.coach.Place
import io.github.conor2803.coach.Prefs
import io.github.conor2803.coach.R
import io.github.conor2803.coach.coach.Engine
import io.github.conor2803.coach.coach.ModelState
import io.github.conor2803.coach.coach.OnDeviceModel
import io.github.conor2803.coach.coach.Scheduler
import io.github.conor2803.coach.context.ContextBuilder
import io.github.conor2803.coach.health.HealthReader
import io.github.conor2803.coach.places.GeofenceManager
import io.github.conor2803.coach.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(tick: Int, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()

    var engine by remember { mutableStateOf(prefs.engine) }
    var modelState by remember { mutableStateOf(OnDeviceModel.state(context)) }
    var apiKey by remember { mutableStateOf(prefs.apiKey) }
    var model by remember { mutableStateOf(prefs.model) }
    var goals by remember { mutableStateOf(prefs.goals) }
    var times by remember { mutableStateOf(prefs.checkInTimes) }
    var places by remember { mutableStateOf(prefs.places) }
    var newPlaceName by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var hcMissing by remember { mutableStateOf<Set<String>?>(null) }

    val hcAvailable = remember(tick) { HealthReader.isAvailable(context) }
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(tick) {
        hcMissing = if (hcAvailable) runCatching { HealthReader(context).missingPermissions() }.getOrNull() else null
        GeofenceManager.register(context)
    }

    LaunchedEffect(tick, modelState is ModelState.Downloading) {
        modelState = OnDeviceModel.state(context)
        while (modelState is ModelState.Downloading) {
            delay(1_000)
            modelState = OnDeviceModel.state(context)
        }
        if (modelState == ModelState.Verifying) modelState = OnDeviceModel.verify(context)
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

    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineLarge) }

        item {
            Group("Engine") {
                EngineChoice(
                    title = "On-device",
                    detail = "Free. Your data stays on the phone.",
                    selected = engine == Engine.ON_DEVICE,
                ) { engine = Engine.ON_DEVICE }
                EngineChoice(
                    title = "Claude",
                    detail = "Paid per check-in. Sharper advice.",
                    selected = engine == Engine.CLAUDE,
                ) { engine = Engine.CLAUDE }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AnimatedContent(engine, label = "engine-setup") { chosen ->
                    Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        when (chosen) {
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
                    }
                }
            }
        }

        item {
            Group("Coach") {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                        label = { Text("Check-in times") },
                        supportingText = { Text("24-hour, comma separated, e.g. 07:30,16:30") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Button(onClick = {
                            prefs.engine = engine
                            prefs.apiKey = apiKey
                            prefs.model = model
                            prefs.goals = goals
                            prefs.checkInTimes = times
                            Scheduler.reschedule(context)
                            message = "Saved. Check-ins at " + Scheduler.parseTimes(times).joinToString { it.toString() }
                        }) { Text("Save") }
                        TextButton(onClick = { scope.launch { preview = ContextBuilder.build(context) } }) {
                            Text("Preview what the coach reads")
                        }
                    }
                }
            }
        }

        preview?.let { text ->
            item {
                Group(if (engine == Engine.CLAUDE) "Sent to Claude" else "Read on the phone") {
                    SelectionContainer(Modifier.padding(Spacing.md)) {
                        Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    TextButton(onClick = { preview = null }, Modifier.padding(horizontal = Spacing.xs)) { Text("Hide") }
                }
            }
        }

        item {
            Group("Places") {
                Text(
                    "Stand somewhere and save it. The phone then logs when you arrive and leave.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
                places.forEach { p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        leadingContent = { Icon(Icons.Rounded.Place, contentDescription = null) },
                        trailingContent = {
                            IconButton(onClick = {
                                places = places - p
                                prefs.places = places
                                GeofenceManager.register(context)
                            }) { Icon(Icons.Rounded.Delete, contentDescription = "Remove ${p.name}") }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    )
                }
                Row(
                    Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    OutlinedTextField(
                        value = newPlaceName,
                        onValueChange = { newPlaceName = it },
                        label = { Text("Name, e.g. Home") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(enabled = newPlaceName.isNotBlank(), onClick = {
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
                            newPlaceName = ""
                            message = GeofenceManager.register(context) ?: "Saved $name"
                        }
                    }) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("Save here", Modifier.padding(start = Spacing.xxs))
                    }
                }
            }
        }

        item {
            Group("Permissions") {
                if (!hcAvailable) {
                    PermissionItem("Health Connect", "Not installed", false) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata"))
                        )
                    }
                } else {
                    val missing = hcMissing
                    val dataOk = missing != null && (missing - HealthReader.BACKGROUND_READ).isEmpty()
                    val backgroundOk = missing != null && HealthReader.BACKGROUND_READ !in missing
                    PermissionItem(
                        "Health Connect",
                        when {
                            dataOk && backgroundOk -> "Garmin and ring data, including in the background"
                            dataOk -> "Allow \"Access data in the background\" in Health Connect, or scheduled check-ins can't see your data"
                            else -> "Sleep, heart rate, HRV, steps, workouts"
                        },
                        dataOk && backgroundOk,
                    ) { hcLauncher.launch(HealthReader.PERMISSIONS) }
                }
                val foreground = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    add(Manifest.permission.READ_CALENDAR)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                PermissionItem("Location, calendar, notifications", "For weather, your day and the check-in itself", foreground.all { granted(it) }) {
                    permsLauncher.launch(foreground.toTypedArray())
                }
                PermissionItem(
                    "Location all the time",
                    "So places log while the app is closed",
                    granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    enabled = granted(Manifest.permission.ACCESS_FINE_LOCATION),
                ) { backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }
            }
        }

        item {
            Group("What leaves the phone") {
                Text(
                    buildString {
                        append("Your location goes to Open-Meteo for the weather forecast. ")
                        append(
                            if (engine == Engine.CLAUDE) "The briefing above goes to Anthropic's Claude API."
                            else "With the on-device engine, nothing else leaves the phone."
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }

        if (message.isNotBlank()) {
            item { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.xxs),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun EngineChoice(title: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
    )
}

@Composable
private fun ModelDownload(state: ModelState, onDownload: () -> Unit) {
    when (state) {
        ModelState.Ready -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("${OnDeviceModel.NAME} is on the phone", style = MaterialTheme.typography.bodyMedium)
        }
        is ModelState.Downloading -> {
            Text("Downloading ${OnDeviceModel.NAME}… " + (state.percent?.let { "$it%" } ?: "starting"), style = MaterialTheme.typography.bodyMedium)
            state.percent?.let { pct ->
                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ModelState.Verifying -> {
            Text("Checking the downloaded model…", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ModelState.Missing, is ModelState.Failed -> {
            AnimatedVisibility(state is ModelState.Failed) {
                Text((state as? ModelState.Failed)?.message.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            FilledTonalButton(onClick = onDownload) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                Text("Download model · ${OnDeviceModel.SIZE} on Wi-Fi", Modifier.padding(start = Spacing.xs))
            }
        }
    }
}

@Composable
private fun PermissionItem(title: String, detail: String, granted: Boolean, enabled: Boolean = true, onGrant: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail) },
        leadingContent = {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = if (granted) "Granted" else "Not granted",
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
        },
        trailingContent = if (granted) null else ({ TextButton(onClick = onGrant, enabled = enabled) { Text("Allow") } }),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}
