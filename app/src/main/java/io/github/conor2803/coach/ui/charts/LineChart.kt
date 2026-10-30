package io.github.conor2803.coach.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp

data class ChartPoint(val label: String, val value: Float?)

data class StackedBar(val label: String, val segments: List<Float>)

private val LineStroke = 2.dp
private val EndDotRadius = 3.dp
private val ScrubDotRadius = 5.dp
private val EndLabelGap = 6.dp
private const val BASELINE_BAND_ALPHA = 0.14f

@Composable
fun LineChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    name: String = "",
    unit: String = "",
    baseline: ClosedFloatingPointRange<Float>? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    valueFormatter: (Float) -> String = { "%.0f".format(it) },
) {
    val values = points.map { it.value }
    val extremes = values.filterNotNull() + listOfNotNull(baseline?.start, baseline?.endInclusive)
    val ticks = if (extremes.isEmpty()) PlaceholderTicks else niceTicks(extremes.min(), extremes.max(), GRID_LINE_COUNT)

    ChartCanvas(
        description = describeSeries(name, values, unit, valueFormatter),
        hasData = values.any { it != null },
        ticks = ticks,
        formatTick = valueFormatter,
        xLabels = points.map { it.label },
        xPositions = { plot ->
            List(points.size) { i ->
                if (points.size == 1) plot.center.x else plot.left + plot.width * i / (points.size - 1)
            }
        },
        selectableIndices = values.indices.filter { values[it] != null },
        tooltipText = { "${points[it].label} · ${valueFormatter(values[it]!!)}$unit" },
        modifier = modifier,
    ) { chart ->
        if (baseline != null) {
            val top = chart.yOf(baseline.endInclusive)
            val bottom = chart.yOf(baseline.start)
            drawRect(
                color.copy(alpha = BASELINE_BAND_ALPHA),
                topLeft = Offset(chart.plot.left, top),
                size = androidx.compose.ui.geometry.Size(chart.plot.width, bottom - top),
            )
        }
        drawLineSeries(
            xs = chart.xs,
            values = values,
            yOf = chart.yOf,
            color = color,
            strokeWidth = LineStroke.toPx(),
            progress = chart.progress,
            revealStart = chart.plot.left,
            revealEnd = chart.plot.right,
            fillTop = chart.plot.top,
            fillBottom = chart.plot.bottom,
        )
        drawEndPoint(chart, values, color, unit, valueFormatter)
        chart.scrubIndex?.let { index ->
            values[index]?.let { drawCircle(color, ScrubDotRadius.toPx(), Offset(chart.xs[index], chart.yOf(it))) }
        }
    }
}

private fun DrawScope.drawEndPoint(
    chart: ChartScope,
    values: List<Float?>,
    color: Color,
    unit: String,
    valueFormatter: (Float) -> String,
) {
    val lastIndex = values.indexOfLast { it != null }
    if (lastIndex < 0) return
    val reveal = lateReveal(chart.progress)
    if (reveal <= 0f) return
    val center = Offset(chart.xs[lastIndex], chart.yOf(values[lastIndex]!!))
    drawCircle(color.copy(alpha = reveal), EndDotRadius.toPx(), center)

    val layout = chart.textMeasurer.measure("${valueFormatter(values[lastIndex]!!)}$unit", chart.valueStyle)
    val left = clampToBounds(center.x, layout.size.width.toFloat(), size.width)
    val top = (center.y - EndDotRadius.toPx() - EndLabelGap.toPx() - layout.size.height).coerceAtLeast(0f)
    drawText(layout, color = chart.valueStyle.color.copy(alpha = reveal), topLeft = Offset(left, top))
}
