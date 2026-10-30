package io.github.conor2803.coach.ui.trends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.conor2803.coach.health.DayValue
import io.github.conor2803.coach.health.HealthHistory
import io.github.conor2803.coach.ui.CoachViewModel
import io.github.conor2803.coach.ui.charts.BarChart
import io.github.conor2803.coach.ui.charts.ChartPoint
import io.github.conor2803.coach.ui.charts.LineChart
import io.github.conor2803.coach.ui.charts.StackedBar
import io.github.conor2803.coach.ui.charts.StackedBarChart
import io.github.conor2803.coach.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val windows = listOf(7, 30)
private val chartHeight = 180.dp
private const val STEP_TARGET = 8_000f

@Composable
fun TrendsScreen(contentPadding: PaddingValues, viewModel: CoachViewModel) {
    val health by viewModel.health.collectAsStateWithLifecycle()
    var windowIndex by rememberSaveable { mutableIntStateOf(0) }
    val days = windows[windowIndex]
    val dates = remember(days) { lastDates(days) }
    val history = health.history

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trends", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                SingleChoiceSegmentedButtonRow {
                    windows.forEachIndexed { i, w ->
                        SegmentedButton(
                            selected = windowIndex == i,
                            onClick = { windowIndex = i },
                            shape = SegmentedButtonDefaults.itemShape(i, windows.size),
                        ) { Text("${w}d") }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(health.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!health.available) {
                Text(
                    "Health Connect isn't on this phone, so there's nothing to chart yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { SleepCard(history, dates) }
        item { HrvCard(history, dates) }
        item { RestingHrCard(history, dates) }
        item { StepsCard(history, dates) }
        item { TrainingCard(history, dates) }
    }
    }
}

@Composable
private fun SleepCard(history: HealthHistory, dates: List<LocalDate>) {
    val byDate = history.nights.associateBy { it.date }
    val bars = dates.map { d ->
        val n = byDate[d]
        StackedBar(
            label = d.shortLabel(dates.size),
            segments = if (n == null) listOf(0f, 0f, 0f) else listOf(n.deepMinutes / 60f, n.remMinutes / 60f, n.lightMinutes / 60f),
        )
    }
    val latest = dates.reversed().firstNotNullOfOrNull { byDate[it] }
    val avg = dates.mapNotNull { byDate[it]?.asleepMinutes }.takeIf { it.isNotEmpty() }?.average()
    ChartCard(
        title = "Sleep",
        headline = latest?.let { hm(it.asleepMinutes) } ?: "—",
        detail = avg?.let { "average ${hm(it.toInt())} over ${dates.size} nights" } ?: "no nights recorded",
    ) {
        StackedBarChart(
            bars = bars,
            legend = listOf(
                "Deep" to MaterialTheme.colorScheme.primary,
                "REM" to MaterialTheme.colorScheme.tertiary,
                "Light" to MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            ),
            name = "Sleep",
            unit = "h",
            valueFormatter = { "%.0f".format(it) },
            modifier = Modifier.fillMaxWidth().height(chartHeight),
        )
    }
}

@Composable
private fun HrvCard(history: HealthHistory, dates: List<LocalDate>) {
    val points = history.hrv.toPoints(dates)
    val values = points.mapNotNull { it.value }
    val usual = values.dropLast(1).takeIf { it.size >= 3 }?.average()?.toFloat()
    ChartCard(
        title = "HRV",
        headline = values.lastOrNull()?.let { "${it.toInt()} ms" } ?: "—",
        detail = usual?.let { "usual ${it.toInt()} ms · higher means better recovered" } ?: "higher means better recovered",
    ) {
        LineChart(
            points = points,
            name = "HRV",
            unit = " ms",
            baseline = usual?.let { (it * 0.95f)..(it * 1.05f) },
            modifier = Modifier.fillMaxWidth().height(chartHeight),
        )
    }
}

@Composable
private fun RestingHrCard(history: HealthHistory, dates: List<LocalDate>) {
    val points = history.restingHr.toPoints(dates)
    val values = points.mapNotNull { it.value }
    val usual = values.dropLast(1).takeIf { it.size >= 3 }?.average()?.toFloat()
    ChartCard(
        title = "Resting heart rate",
        headline = values.lastOrNull()?.let { "${it.toInt()} bpm" } ?: "—",
        detail = usual?.let { "usual ${it.toInt()} bpm · lower means more rested" } ?: "lower means more rested",
    ) {
        LineChart(
            points = points,
            name = "Resting heart rate",
            unit = " bpm",
            baseline = usual?.let { (it - 2f)..(it + 2f) },
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth().height(chartHeight),
        )
    }
}

@Composable
private fun StepsCard(history: HealthHistory, dates: List<LocalDate>) {
    val points = history.steps.toPoints(dates)
    val values = points.mapNotNull { it.value }
    ChartCard(
        title = "Steps",
        headline = values.lastOrNull()?.let { "%,d".format(it.toInt()) } ?: "—",
        detail = if (values.isEmpty()) "no steps recorded" else "${values.count { it >= STEP_TARGET }} of ${values.size} days over %,d".format(STEP_TARGET.toInt()),
    ) {
        BarChart(
            points = points,
            name = "Steps",
            target = STEP_TARGET,
            color = MaterialTheme.colorScheme.secondary,
            valueFormatter = { if (it >= 1000f) "%.1fk".format(it / 1000f) else "%.0f".format(it) },
            modifier = Modifier.fillMaxWidth().height(chartHeight),
        )
    }
}

@Composable
private fun TrainingCard(history: HealthHistory, dates: List<LocalDate>) {
    val zone = java.time.ZoneId.systemDefault()
    val inRange = history.workouts.filter { it.start.atZone(zone).toLocalDate() in dates.first()..dates.last() }
    val minutesByDate = inRange.groupBy { it.start.atZone(zone).toLocalDate() }.mapValues { (_, w) -> w.sumOf { it.minutes } }
    val points = dates.map { d -> ChartPoint(d.shortLabel(dates.size), minutesByDate[d]?.toFloat()) }
    val km = inRange.mapNotNull { it.km }.sum()
    ChartCard(
        title = "Training",
        headline = if (inRange.isEmpty()) "—" else "${inRange.size} session${if (inRange.size == 1) "" else "s"}",
        detail = if (inRange.isEmpty()) "nothing recorded" else "${inRange.sumOf { it.minutes }} min" + if (km > 0) " · %.1f km".format(km) else "",
    ) {
        BarChart(
            points = points,
            name = "Training minutes",
            unit = " min",
            modifier = Modifier.fillMaxWidth().height(chartHeight),
        )
    }
}

@Composable
private fun ChartCard(title: String, headline: String, detail: String, chart: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Text(headline, style = MaterialTheme.typography.headlineMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            chart()
        }
    }
}

private fun List<DayValue>.toPoints(dates: List<LocalDate>): List<ChartPoint> {
    val byDate = associate { it.date to it.value.toFloat() }
    return dates.map { d -> ChartPoint(d.shortLabel(dates.size), byDate[d]) }
}

private fun lastDates(days: Int, today: LocalDate = LocalDate.now()): List<LocalDate> =
    (days - 1 downTo 0).map { today.minusDays(it.toLong()) }

private fun LocalDate.shortLabel(window: Int): String =
    if (window <= 7) dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.UK).take(2) else dayOfMonth.toString()

private fun hm(minutes: Int) = "${minutes / 60}h${"%02d".format(minutes % 60)}"

@Composable
private fun <T> remember(key: Any?, calculation: () -> T): T = androidx.compose.runtime.remember(key, calculation)
