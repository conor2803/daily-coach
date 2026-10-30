package io.github.conor2803.coach.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val SparklineMinWidth = 64.dp
private val SparklineMinHeight = 24.dp
private val SparklineStroke = 1.5.dp
private val SparklineDotRadius = 2.5.dp

@Composable
fun Sparkline(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val progress = rememberDrawInProgress()
    val present = values.filterNotNull()
    val description = describeSeries("Trend", values, "") { "%.0f".format(it) }

    Canvas(
        modifier
            .defaultMinSize(SparklineMinWidth, SparklineMinHeight)
            .semantics { contentDescription = description },
    ) {
        if (present.isEmpty()) return@Canvas
        val inset = SparklineDotRadius.toPx()
        val low = present.min()
        val high = present.max()
        val xs = List(values.size) { i ->
            if (values.size == 1) size.width / 2f else inset + (size.width - 2 * inset) * i / (values.size - 1)
        }
        val yOf = { value: Float ->
            if (high == low) size.height / 2f
            else size.height - inset - (value - low) / (high - low) * (size.height - 2 * inset)
        }
        drawLineSeries(
            xs = xs,
            values = values,
            yOf = yOf,
            color = color,
            strokeWidth = SparklineStroke.toPx(),
            progress = progress.value,
            revealStart = xs.first(),
            revealEnd = xs.last(),
        )
        val reveal = lateReveal(progress.value)
        val lastIndex = values.indexOfLast { it != null }
        if (reveal > 0f) {
            drawCircle(color.copy(alpha = reveal), inset, Offset(xs[lastIndex], yOf(values[lastIndex]!!)))
        }
    }
}
