package io.github.conor2803.coach.ui.charts

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp

private const val BAR_WIDTH_FRACTION = 0.6f
private const val BAR_CORNER_FRACTION = 0.25f
private const val BELOW_TARGET_ALPHA = 0.55f
private val TargetStroke = 1.dp
private val TargetDash = 6.dp
private val TargetLabelGap = 4.dp

@Composable
fun BarChart(
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    name: String = "",
    unit: String = "",
    target: Float? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    valueFormatter: (Float) -> String = { "%.0f".format(it) },
) {
    val values = points.map { it.value }
    val targetColor = MaterialTheme.colorScheme.onSurfaceVariant
    val extremes = values.filterNotNull() + listOfNotNull(target) + 0f
    val ticks = if (values.all { it == null }) PlaceholderTicks else niceTicks(extremes.min(), extremes.max(), GRID_LINE_COUNT)

    ChartCanvas(
        description = describeSeries(name, values, unit, valueFormatter),
        hasData = values.any { it != null },
        ticks = ticks,
        formatTick = valueFormatter,
        xLabels = points.map { it.label },
        xPositions = { plot -> List(points.size) { plot.left + plot.width * (it + 0.5f) / points.size } },
        selectableIndices = values.indices.filter { values[it] != null },
        tooltipText = { "${points[it].label} · ${valueFormatter(values[it]!!)}$unit" },
        modifier = modifier,
    ) { chart ->
        val slotWidth = chart.plot.width / points.size
        val barWidth = slotWidth * BAR_WIDTH_FRACTION
        val zeroY = chart.yOf(0f)
        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val tipY = zeroY + (chart.yOf(value) - zeroY) * chart.progress
            val left = chart.xs[index] - barWidth / 2f
            val barColor = if (target != null && value < target) color.copy(alpha = BELOW_TARGET_ALPHA) else color
            drawPath(
                roundedEndPath(
                    left = left,
                    top = minOf(tipY, zeroY),
                    right = left + barWidth,
                    bottom = maxOf(tipY, zeroY),
                    corner = barWidth * BAR_CORNER_FRACTION,
                    roundTop = value >= 0f,
                ),
                barColor,
            )
        }
        if (target != null) {
            val y = chart.yOf(target)
            drawLine(
                targetColor,
                Offset(chart.plot.left, y),
                Offset(chart.plot.right, y),
                strokeWidth = TargetStroke.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(TargetDash.toPx(), TargetDash.toPx())),
            )
            val layout = chart.textMeasurer.measure("${valueFormatter(target)}$unit", chart.valueStyle)
            drawText(
                layout,
                color = targetColor,
                topLeft = Offset(
                    chart.plot.right - layout.size.width,
                    (y - TargetLabelGap.toPx() - layout.size.height).coerceAtLeast(0f),
                ),
            )
        }
    }
}
