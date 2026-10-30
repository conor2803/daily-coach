package io.github.conor2803.coach.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val RingStroke = 12.dp
private val RingDefaultSize = 168.dp
private const val RING_START_ANGLE = 135f
private const val RING_SWEEP_ANGLE = 270f
private const val MAX_SCORE = 100

@Composable
fun ReadinessRing(
    score: Int,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = RingDefaultSize,
) {
    val clamped = score.coerceIn(0, MAX_SCORE)
    val colors = MaterialTheme.colorScheme
    val arcColor = when (readinessBand(clamped)) {
        ReadinessBand.Strong -> colors.primary
        ReadinessBand.Good -> colors.tertiary
        ReadinessBand.Fair -> colors.secondary
        ReadinessBand.Low -> colors.error
    }
    val trackColor = colors.surfaceContainerHighest
    val progress = rememberDrawInProgress()

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = "$label $clamped out of $MAX_SCORE" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            drawArc(trackColor, RING_START_ANGLE, RING_SWEEP_ANGLE, false, topLeft, arcSize, style = style)
            val sweep = RING_SWEEP_ANGLE * clamped / MAX_SCORE * progress.value
            if (sweep > 0f) {
                drawArc(arcColor, RING_START_ANGLE, sweep, false, topLeft, arcSize, style = style)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = (clamped * progress.value).roundToInt().toString(),
                style = MaterialTheme.typography.displaySmall,
                color = colors.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
