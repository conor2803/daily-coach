package io.github.conor2803.coach.ui.today

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.conor2803.coach.Advice
import io.github.conor2803.coach.Prefs
import io.github.conor2803.coach.R
import io.github.conor2803.coach.coach.AdviceLog
import io.github.conor2803.coach.coach.Engine
import io.github.conor2803.coach.coach.Factor
import io.github.conor2803.coach.coach.LoggedAdvice
import io.github.conor2803.coach.coach.OnDeviceModel
import io.github.conor2803.coach.coach.Readiness
import io.github.conor2803.coach.coach.Scheduler
import io.github.conor2803.coach.ui.CoachViewModel
import io.github.conor2803.coach.ui.charts.ReadinessRing
import io.github.conor2803.coach.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)
private val whenFmt = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)

@Composable
fun TodayScreen(tick: Int, contentPadding: PaddingValues, viewModel: CoachViewModel, openSettings: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val health by viewModel.health.collectAsStateWithLifecycle()

    var advice by remember { mutableStateOf(prefs.lastAdvice) }
    var lastError by remember { mutableStateOf(prefs.lastError) }
    var earlier by remember { mutableStateOf<List<LoggedAdvice>>(emptyList()) }
    var setupNeeded by remember { mutableStateOf<String?>(null) }

    val nowRuns by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(Scheduler.NOW) }
        .collectAsState(initial = emptyList())
    val running = nowRuns.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }

    LaunchedEffect(tick, nowRuns) {
        advice = prefs.lastAdvice
        lastError = prefs.lastError
        earlier = AdviceLog.recent(context, limit = 6).drop(if (advice != null) 1 else 0)
        setupNeeded = prefs.engine.missingSetup(prefs.apiKey, OnDeviceModel.isReady(context))
    }

    PullToRefreshBox(
        isRefreshing = health.loading,
        onRefresh = viewModel::refresh,
        modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
    ) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + Spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            Column {
                Text(
                    LocalDate.now().format(dateFmt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(greeting(), style = MaterialTheme.typography.headlineLarge)
            }
        }

        setupNeeded?.let { why ->
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Finish setup", style = MaterialTheme.typography.titleMedium)
                            Text(why, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = openSettings) { Text("Open") }
                    }
                }
            }
        }

        item { ReadinessCard(health.loading, health.available, health.readiness) }

        item {
            PlanCard(
                advice = advice,
                engine = prefs.engine,
                running = running,
                nextRun = Scheduler.nextRun(context).toLocalTime(),
                onCheckIn = { Scheduler.runNow(context) },
                onShare = { a ->
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, a.headline)
                        .putExtra(Intent.EXTRA_TEXT, "${a.headline}\n\n${a.plan}")
                    context.startActivity(Intent.createChooser(send, "Share today's plan"))
                },
            )
        }

        if (lastError.isNotBlank()) {
            item {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(lastError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        if (earlier.isNotEmpty()) {
            item {
                Text(
                    "Earlier",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = Spacing.xxs, top = Spacing.xs),
                )
            }
            items(earlier, key = { it.advice.at }) { entry ->
                ListItem(
                    headlineContent = { Text(entry.advice.headline, style = MaterialTheme.typography.bodyLarge) },
                    supportingContent = {
                        Text(Instant.ofEpochMilli(entry.advice.at).atZone(ZoneId.systemDefault()).format(whenFmt))
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.padding(vertical = 0.dp),
                )
            }
        }
    }
    }
}

@Composable
private fun ReadinessCard(loading: Boolean, available: Boolean, readiness: Readiness?) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Readiness", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            AnimatedContent(Triple(loading, available, readiness), label = "readiness") { (isLoading, isAvailable, r) ->
                when {
                    !isAvailable -> Text(
                        "Health Connect isn't on this phone, so there's no readiness score.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    isLoading && r == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Reading your watch and ring…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    r == null -> Text(
                        "No sleep, HRV or resting heart rate yet. Sync Garmin or RingConn and come back.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        ReadinessRing(score = r.score, label = r.label, size = 132.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FactorRow(R.drawable.ic_bedtime, "Sleep", r.sleep)
                            FactorRow(R.drawable.ic_bolt, "HRV", r.hrv)
                            FactorRow(R.drawable.ic_heart, "Resting HR", r.restingHr)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactorRow(icon: Int, name: String, factor: Factor?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (factor == null) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Column {
            Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                factor?.summary ?: "no data",
                style = MaterialTheme.typography.bodyMedium,
                color = if (factor == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PlanCard(
    advice: Advice?,
    engine: Engine,
    running: Boolean,
    nextRun: LocalTime,
    onCheckIn: () -> Unit,
    onShare: (Advice) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Today's plan",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.weight(1f),
                )
                if (advice != null) {
                    IconButton(onClick = { onShare(advice) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share today's plan", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            AnimatedContent(advice, label = "advice") { a ->
                if (a == null) {
                    Text(
                        "No check-in yet. Tap below and the coach will read your day and write a plan.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(a.headline, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        planLines(a.plan).forEach { line -> PlanLine(line) }
                        Text(
                            Instant.ofEpochMilli(a.at).atZone(ZoneId.systemDefault()).format(whenFmt) +
                                " · " + if (engine == Engine.ON_DEVICE) "on-device" else "Claude",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xxs))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = onCheckIn, enabled = !running) {
                    AnimatedVisibility(running) {
                        CircularProgressIndicator(Modifier.size(18.dp).padding(end = Spacing.xxs), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                    if (!running) Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(18.dp))
                    Text(if (running) "Checking in…" else "Check in now", Modifier.padding(start = Spacing.xs))
                }
                Text(
                    "Next at ${nextRun.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun PlanLine(line: String) {
    val spans = timeSpans(line)
    val styled = buildAnnotatedString {
        var cursor = 0
        spans.forEach { range ->
            append(line.substring(cursor, range.first))
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(range)) }
            cursor = range.last + 1
        }
        append(line.substring(cursor))
    }
    Text(styled, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
}

private fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
