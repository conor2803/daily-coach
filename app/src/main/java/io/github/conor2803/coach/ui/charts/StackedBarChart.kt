package io.github.conor2803.coach.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

private const val BAR_WIDTH_FRACTION = 0.6f
private const val BAR_CORNER_FRACTION = 0.25f
private val LegendSwatch = 8.dp
private val LegendItemGap = 16.dp
private val LegendSwatchGap = 6.dp
private val LegendTopGap = 8.dp

@Composable
fun StackedBarChart(
    bars: List<StackedBar>,
    legend: List<Pair<String, Color>>,
    modifier: Modifier = Modifier,
    name: String = "",
    unit: String = "",
    valueFormatter: (Float) -> String = { "%.1f".format(it) },
) {
    val totals = bars.map { bar -> bar.segments.sumOf { it.coerceAtLeast(0f).toDouble() }.toFloat() }
    val hasData = totals.any { it > 0f }
    val ticks = if (hasData) niceTicks(0f, totals.max(), GRID_LINE_COUNT) else PlaceholderTicks
    val fallbackColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier) {
        ChartCanvas(
            description = describeSeries(name, totals, unit, valueFormatter),
            hasData = hasData,
            ticks = ticks,
            formatTick = valueFormatter,
            xLabels = bars.map { it.label },
            xPositions = { plot -> List(bars.size) { plot.left + plot.width * (it + 0.5f) / bars.size } },
            selectableIndices = emptyList(),
            tooltipText = { "" },
            modifier = Modifier.fillMaxWidth(),
        ) { chart ->
            val barWidth = chart.plot.width / bars.size * BAR_WIDTH_FRACTION
            val zeroY = chart.yOf(0f)
            bars.forEachIndexed { index, bar ->
                if (totals[index] <= 0f) return@forEachIndexed
                val left = chart.xs[index] - barWidth / 2f
                val tipY = zeroY + (chart.yOf(totals[index]) - zeroY) * chart.progress
                val outline = roundedEndPath(left, tipY, left + barWidth, zeroY, barWidth * BAR_CORNER_FRACTION, roundTop = true)
                clipPath(outline) {
                    var stacked = 0f
                    bar.segments.forEachIndexed { segmentIndex, raw ->
                        val segment = raw.coerceAtLeast(0f)
                        val segmentTop = zeroY + (chart.yOf(stacked + segment) - zeroY) * chart.progress
                        val segmentBottom = zeroY + (chart.yOf(stacked) - zeroY) * chart.progress
                        drawRect(
                            legend.getOrNull(segmentIndex)?.second ?: fallbackColor,
                            topLeft = Offset(left, segmentTop),
                            size = Size(barWidth, segmentBottom - segmentTop),
                        )
                        stacked += segment
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = LegendTopGap),
            horizontalArrangement = Arrangement.spacedBy(LegendItemGap),
        ) {
            legend.forEach { (label, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(LegendSwatch)) { drawCircle(color) }
                    Text(
                        label,
                        modifier = Modifier.padding(start = LegendSwatchGap),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
