package io.github.conor2803.coach.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

private const val DRAW_IN_MILLIS = 700
private const val LATE_REVEAL_START = 0.85f
private const val FILL_ALPHA = 0.25f
private val DrawInEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val ChartMinHeight = 180.dp
private val EmptyChartMinHeight = 48.dp
private val AxisLabelGap = 6.dp
private val PlotTopInset = 8.dp
private val PlotEndInset = 8.dp
private val GridStroke = 1.dp
private val HairlineStroke = 1.dp
private val TooltipPaddingHorizontal = 8.dp
private val TooltipPaddingVertical = 4.dp
private val TooltipCorner = 8.dp

internal const val GRID_LINE_COUNT = 4
private const val ALL_LABELS_LIMIT = 8
private const val SPARSE_LABEL_COUNT = 5
internal val PlaceholderTicks = listOf(0f, 1f)

internal class ChartScope(
    val plot: Rect,
    val xs: List<Float>,
    val progress: Float,
    val scrubIndex: Int?,
    val textMeasurer: TextMeasurer,
    val valueStyle: TextStyle,
    val yOf: (Float) -> Float,
)

@Composable
internal fun rememberDrawInProgress(): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(DRAW_IN_MILLIS, easing = DrawInEasing)) }
    return progress.asState()
}

internal fun lateReveal(progress: Float): Float =
    ((progress - LATE_REVEAL_START) / (1f - LATE_REVEAL_START)).coerceIn(0f, 1f)

internal fun xLabelIndices(count: Int): List<Int> =
    labelIndices(count, if (count <= ALL_LABELS_LIMIT) count else SPARSE_LABEL_COUNT)

internal fun smoothPath(points: List<Offset>): Path = Path().apply {
    moveTo(points.first().x, points.first().y)
    monotoneControlPoints(points).forEach {
        cubicTo(it.control1.x, it.control1.y, it.control2.x, it.control2.y, it.end.x, it.end.y)
    }
}

internal fun roundedEndPath(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    corner: Float,
    roundTop: Boolean,
): Path {
    val radius = CornerRadius(minOf(corner, (bottom - top) / 2f, (right - left) / 2f).coerceAtLeast(0f))
    val square = CornerRadius.Zero
    return Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                topLeftCornerRadius = if (roundTop) radius else square,
                topRightCornerRadius = if (roundTop) radius else square,
                bottomLeftCornerRadius = if (roundTop) square else radius,
                bottomRightCornerRadius = if (roundTop) square else radius,
            ),
        )
    }
}

internal fun DrawScope.drawLineSeries(
    xs: List<Float>,
    values: List<Float?>,
    yOf: (Float) -> Float,
    color: Color,
    strokeWidth: Float,
    progress: Float,
    revealStart: Float,
    revealEnd: Float,
    fillTop: Float = 0f,
    fillBottom: Float? = null,
) {
    val revealEdge = if (progress >= 1f) size.width else revealStart + (revealEnd - revealStart) * progress
    clipRect(left = revealStart - strokeWidth, top = 0f, right = revealEdge, bottom = size.height) {
        contiguousRuns(values).forEach { run ->
            val offsets = run.map { Offset(xs[it], yOf(values[it]!!)) }
            if (offsets.size == 1) {
                drawCircle(color, radius = strokeWidth, center = offsets.first())
                return@forEach
            }
            if (fillBottom != null) {
                val fill = smoothPath(offsets).apply {
                    lineTo(offsets.last().x, fillBottom)
                    lineTo(offsets.first().x, fillBottom)
                    close()
                }
                drawPath(
                    fill,
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = FILL_ALPHA), Color.Transparent),
                        startY = fillTop,
                        endY = fillBottom,
                    ),
                )
            }
            drawPath(
                smoothPath(offsets),
                color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

@Composable
internal fun ChartCanvas(
    description: String,
    hasData: Boolean,
    ticks: List<Float>,
    formatTick: (Float) -> String,
    xLabels: List<String>,
    xPositions: (Rect) -> List<Float>,
    selectableIndices: List<Int>,
    tooltipText: (Int) -> String,
    modifier: Modifier = Modifier,
    drawSeries: DrawScope.(ChartScope) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val valueStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface)
    val emptyStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurfaceVariant)
    val gridColor = colors.outlineVariant
    val hairlineColor = colors.outline
    val tooltipBackground = colors.surfaceContainerHighest
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val progress = rememberDrawInProgress()
    val canvasSize = remember { mutableStateOf(IntSize.Zero) }
    val scrub = remember { mutableStateOf<Int?>(null) }

    val plot = with(density) {
        val yLabelWidth = ticks.maxOfOrNull { textMeasurer.measure(formatTick(it), axisStyle).size.width } ?: 0
        val xLabelHeight = textMeasurer.measure("0", axisStyle).size.height
        Rect(
            left = yLabelWidth + AxisLabelGap.toPx(),
            top = PlotTopInset.toPx(),
            right = canvasSize.value.width - PlotEndInset.toPx(),
            bottom = canvasSize.value.height - xLabelHeight - AxisLabelGap.toPx(),
        )
    }
    val xs = xPositions(plot)

    Canvas(
        modifier = modifier
            .defaultMinSize(minHeight = if (hasData) ChartMinHeight else EmptyChartMinHeight)
            .onSizeChanged { canvasSize.value = it }
            .semantics { contentDescription = description }
            .scrubGestures(xs, selectableIndices, scrub),
    ) {
        if (!hasData) {
            val layout = textMeasurer.measure("No data yet", emptyStyle)
            drawText(
                layout,
                topLeft = Offset((size.width - layout.size.width) / 2f, (size.height - layout.size.height) / 2f),
            )
            return@Canvas
        }
        if (plot.width <= 0f || plot.height <= 0f) return@Canvas

        val yMin = ticks.first()
        val yMax = ticks.last()
        val yOf = { value: Float -> plot.bottom - (value - yMin) / (yMax - yMin) * plot.height }
        val scrubIndex = scrub.value?.takeIf { it in xs.indices }

        ticks.forEach { tick ->
            val y = yOf(tick)
            drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = GridStroke.toPx())
            val layout = textMeasurer.measure(formatTick(tick), axisStyle)
            drawText(
                layout,
                topLeft = Offset(plot.left - AxisLabelGap.toPx() - layout.size.width, y - layout.size.height / 2f),
            )
        }
        xLabelIndices(xLabels.size).forEach { index ->
            val layout = textMeasurer.measure(xLabels[index], axisStyle)
            val left = clampToBounds(xs[index], layout.size.width.toFloat(), size.width)
            drawText(layout, topLeft = Offset(left, plot.bottom + AxisLabelGap.toPx()))
        }

        if (scrubIndex != null) {
            drawLine(
                hairlineColor,
                Offset(xs[scrubIndex], plot.top),
                Offset(xs[scrubIndex], plot.bottom),
                strokeWidth = HairlineStroke.toPx(),
            )
        }

        drawSeries(ChartScope(plot, xs, progress.value, scrubIndex, textMeasurer, valueStyle, yOf))

        if (scrubIndex != null) {
            val layout = textMeasurer.measure(tooltipText(scrubIndex), valueStyle)
            val horizontal = TooltipPaddingHorizontal.toPx()
            val vertical = TooltipPaddingVertical.toPx()
            val width = layout.size.width + 2 * horizontal
            val left = clampToBounds(xs[scrubIndex], width, size.width)
            drawRoundRect(
                tooltipBackground,
                topLeft = Offset(left, 0f),
                size = Size(width, layout.size.height + 2 * vertical),
                cornerRadius = CornerRadius(TooltipCorner.toPx()),
            )
            drawText(layout, topLeft = Offset(left + horizontal, vertical))
        }
    }
}

private fun Modifier.scrubGestures(
    xs: List<Float>,
    selectable: List<Int>,
    scrub: MutableState<Int?>,
): Modifier {
    if (selectable.isEmpty()) return this
    fun nearestSelectable(x: Float): Int = selectable[nearestIndex(x, selectable.map { xs[it] })]
    return this
        .pointerInput(xs, selectable) {
            detectTapGestures { offset ->
                val index = nearestSelectable(offset.x)
                scrub.value = if (scrub.value == index) null else index
            }
        }
        .pointerInput(xs, selectable) {
            detectHorizontalDragGestures(
                onDragStart = { scrub.value = nearestSelectable(it.x) },
                onDragEnd = { scrub.value = null },
                onDragCancel = { scrub.value = null },
                onHorizontalDrag = { change, _ -> scrub.value = nearestSelectable(change.position.x) },
            )
        }
}
