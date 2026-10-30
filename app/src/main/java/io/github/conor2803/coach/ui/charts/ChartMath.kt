package io.github.conor2803.coach.ui.charts

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TICK_EPSILON = 1e-9
private const val FLAT_RANGE_PADDING_FRACTION = 0.1
private const val FLAT_RANGE_MIN_PADDING = 1.0
private const val MAX_TANGENT_MAGNITUDE = 3f

data class CubicSegment(val control1: Offset, val control2: Offset, val end: Offset)

enum class ReadinessBand { Strong, Good, Fair, Low }

fun readinessBand(score: Int): ReadinessBand = when {
    score >= 80 -> ReadinessBand.Strong
    score >= 65 -> ReadinessBand.Good
    score >= 50 -> ReadinessBand.Fair
    else -> ReadinessBand.Low
}

fun niceTicks(min: Float, max: Float, count: Int): List<Float> {
    var low = minOf(min, max).toDouble()
    var high = maxOf(min, max).toDouble()
    if (high - low < TICK_EPSILON) {
        val padding = maxOf(abs(low) * FLAT_RANGE_PADDING_FRACTION, FLAT_RANGE_MIN_PADDING)
        low -= padding
        high += padding
    }
    val rawStep = (high - low) / (count.coerceAtLeast(2) - 1)
    val magnitude = 10.0.pow(floor(log10(rawStep)))
    val fraction = rawStep / magnitude
    val step = magnitude * when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    val first = floor(low / step + TICK_EPSILON).toInt()
    val last = ceil(high / step - TICK_EPSILON).toInt()
    return (first..last).map { (it * step).toFloat() }
}

fun labelIndices(count: Int, max: Int): List<Int> {
    if (count <= 0 || max <= 0) return emptyList()
    if (count <= max) return (0 until count).toList()
    if (max == 1) return listOf(0)
    return (0 until max).map { (it * (count - 1).toDouble() / (max - 1)).roundToInt() }.distinct()
}

fun nearestIndex(x: Float, xs: List<Float>): Int =
    xs.indices.minByOrNull { abs(xs[it] - x) } ?: -1

fun clampToBounds(center: Float, extent: Float, bound: Float): Float =
    (center - extent / 2f).coerceIn(0f, maxOf(0f, bound - extent))

fun contiguousRuns(values: List<Float?>): List<List<Int>> {
    val runs = mutableListOf<MutableList<Int>>()
    var previousWasPresent = false
    values.forEachIndexed { index, value ->
        if (value == null) {
            previousWasPresent = false
        } else {
            if (!previousWasPresent) runs += mutableListOf<Int>()
            runs.last() += index
            previousWasPresent = true
        }
    }
    return runs
}

fun monotoneControlPoints(points: List<Offset>): List<CubicSegment> {
    val n = points.size
    if (n < 2) return emptyList()
    val dx = FloatArray(n - 1) { points[it + 1].x - points[it].x }
    val slope = FloatArray(n - 1) {
        if (dx[it] == 0f) 0f else (points[it + 1].y - points[it].y) / dx[it]
    }
    val tangent = FloatArray(n)
    tangent[0] = slope[0]
    tangent[n - 1] = slope[n - 2]
    for (i in 1 until n - 1) {
        tangent[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
    }
    for (k in 0 until n - 1) {
        if (slope[k] == 0f) {
            tangent[k] = 0f
            tangent[k + 1] = 0f
            continue
        }
        val a = tangent[k] / slope[k]
        val b = tangent[k + 1] / slope[k]
        val magnitudeSquared = a * a + b * b
        if (magnitudeSquared > MAX_TANGENT_MAGNITUDE * MAX_TANGENT_MAGNITUDE) {
            val tau = MAX_TANGENT_MAGNITUDE / sqrt(magnitudeSquared)
            tangent[k] = tau * a * slope[k]
            tangent[k + 1] = tau * b * slope[k]
        }
    }
    return (0 until n - 1).map { k ->
        val from = points[k]
        val to = points[k + 1]
        val third = dx[k] / 3f
        CubicSegment(
            control1 = Offset(from.x + third, from.y + tangent[k] * third),
            control2 = Offset(to.x - third, to.y - tangent[k + 1] * third),
            end = to,
        )
    }
}

fun describeSeries(
    name: String,
    values: List<Float?>,
    unit: String,
    valueFormatter: (Float) -> String,
): String {
    val subject = name.ifBlank { null }
    val present = values.filterNotNull()
    if (present.isEmpty()) return listOfNotNull(subject, "no data").joinToString(", ")
    val pointCount = if (values.size == 1) "1 point" else "${values.size} points"
    return listOfNotNull(
        subject,
        pointCount,
        "latest ${valueFormatter(present.last())}$unit",
        "lowest ${valueFormatter(present.min())}",
        "highest ${valueFormatter(present.max())}",
    ).joinToString(", ")
}
