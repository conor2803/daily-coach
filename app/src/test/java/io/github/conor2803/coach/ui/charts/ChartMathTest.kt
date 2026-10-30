package io.github.conor2803.coach.ui.charts

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartMathTest {
    private val delta = 1e-4f

    private fun assertOffset(x: Float, y: Float, actual: Offset) {
        assertEquals(x, actual.x, delta)
        assertEquals(y, actual.y, delta)
    }

    private fun assertTicks(expected: List<Float>, actual: List<Float>) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, delta) }
    }

    @Test fun niceTicks_cover_the_data_range_on_round_steps() =
        assertTicks(listOf(40f, 45f, 50f, 55f), niceTicks(41f, 55f, 4))

    @Test fun niceTicks_include_zero_when_range_starts_there() =
        assertTicks(listOf(0f, 50f, 100f), niceTicks(0f, 100f, 4))

    @Test fun niceTicks_handle_negative_ranges() =
        assertTicks(listOf(-5f, 0f, 5f, 10f), niceTicks(-3f, 9f, 4))

    @Test fun niceTicks_pad_a_flat_range_around_the_value() {
        val ticks = niceTicks(50f, 50f, 4)
        assertTicks(listOf(45f, 50f, 55f), ticks)
    }

    @Test fun niceTicks_pad_a_flat_zero_range() {
        val ticks = niceTicks(0f, 0f, 4)
        assertTrue(ticks.size >= 2)
        assertTrue(ticks.first() < 0f && ticks.last() > 0f)
    }

    @Test fun niceTicks_accept_reversed_bounds() =
        assertTicks(niceTicks(0f, 100f, 4), niceTicks(100f, 0f, 4))

    @Test fun niceTicks_always_return_at_least_two_ascending_values() {
        listOf(0, 1, 2, 7).forEach { count ->
            val ticks = niceTicks(3.2f, 3.9f, count)
            assertTrue(ticks.size >= 2)
            assertEquals(ticks.sorted(), ticks)
        }
    }

    @Test fun niceTicks_cope_with_fractional_ranges() {
        val ticks = niceTicks(0.1f, 0.9f, 4)
        assertTrue(ticks.first() <= 0.1f && ticks.last() >= 0.9f)
    }

    @Test fun labelIndices_show_every_label_when_they_fit() =
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), labelIndices(7, 8))

    @Test fun labelIndices_space_labels_evenly_when_too_many() =
        assertEquals(listOf(0, 2, 4, 6, 8), labelIndices(9, 5))

    @Test fun labelIndices_always_keep_first_and_last() {
        val indices = labelIndices(30, 5)
        assertEquals(0, indices.first())
        assertEquals(29, indices.last())
        assertEquals(5, indices.size)
    }

    @Test fun labelIndices_of_nothing_is_empty() = assertEquals(emptyList<Int>(), labelIndices(0, 5))

    @Test fun nearestIndex_picks_the_closest_x() =
        assertEquals(2, nearestIndex(9f, listOf(0f, 5f, 10f, 20f)))

    @Test fun nearestIndex_clamps_to_the_ends() {
        assertEquals(0, nearestIndex(-50f, listOf(0f, 5f, 10f)))
        assertEquals(2, nearestIndex(500f, listOf(0f, 5f, 10f)))
    }

    @Test fun nearestIndex_prefers_the_earlier_point_on_a_tie() =
        assertEquals(0, nearestIndex(5f, listOf(0f, 10f)))

    @Test fun nearestIndex_of_no_points_is_minus_one() = assertEquals(-1, nearestIndex(3f, emptyList()))

    @Test fun clampToBounds_centres_when_there_is_room() =
        assertEquals(40f, clampToBounds(50f, 20f, 100f), delta)

    @Test fun clampToBounds_keeps_the_start_inside_on_the_left() =
        assertEquals(0f, clampToBounds(2f, 20f, 100f), delta)

    @Test fun clampToBounds_keeps_the_end_inside_on_the_right() =
        assertEquals(80f, clampToBounds(99f, 20f, 100f), delta)

    @Test fun contiguousRuns_split_at_gaps() =
        assertEquals(
            listOf(listOf(0, 1), listOf(3), listOf(5, 6)),
            contiguousRuns(listOf(1f, 2f, null, 3f, null, 4f, 5f)),
        )

    @Test fun contiguousRuns_of_all_nulls_is_empty() =
        assertEquals(emptyList<List<Int>>(), contiguousRuns(listOf(null, null)))

    @Test fun monotoneControlPoints_flatten_the_tangent_at_a_peak() {
        val segments = monotoneControlPoints(listOf(Offset(0f, 0f), Offset(1f, 1f), Offset(2f, 0f)))
        assertEquals(2, segments.size)
        assertOffset(1f / 3f, 1f / 3f, segments[0].control1)
        assertOffset(2f / 3f, 1f, segments[0].control2)
        assertOffset(4f / 3f, 1f, segments[1].control1)
        assertOffset(5f / 3f, 1f / 3f, segments[1].control2)
        assertEquals(Offset(2f, 0f), segments[1].end)
    }

    @Test fun monotoneControlPoints_never_overshoot_a_steep_rise() {
        val points = listOf(Offset(0f, 0f), Offset(1f, 1f), Offset(2f, 10f))
        monotoneControlPoints(points).forEachIndexed { index, segment ->
            val low = points[index].y
            val high = points[index + 1].y
            assertTrue(segment.control1.y in low..high)
            assertTrue(segment.control2.y in low..high)
        }
    }

    @Test fun monotoneControlPoints_need_two_points() =
        assertEquals(emptyList<CubicSegment>(), monotoneControlPoints(listOf(Offset(0f, 0f))))

    @Test fun describeSeries_summarises_latest_lowest_and_highest() =
        assertEquals(
            "HRV, 7 points, latest 48 ms, lowest 41, highest 55",
            describeSeries("HRV", listOf(50f, 55f, null, 41f, 44f, 47f, 48f), " ms") { "%.0f".format(it) },
        )

    @Test fun describeSeries_uses_the_last_present_value_as_latest() =
        assertEquals(
            "3 points, latest 2, lowest 1, highest 2",
            describeSeries("", listOf(1f, 2f, null), "") { "%.0f".format(it) },
        )

    @Test fun describeSeries_says_no_data_when_every_value_is_missing() =
        assertEquals("HRV, no data", describeSeries("HRV", listOf(null, null), " ms") { "%.0f".format(it) })

    @Test fun describeSeries_uses_the_singular_for_one_point() =
        assertEquals("1 point, latest 5, lowest 5, highest 5", describeSeries("", listOf(5f), "") { "%.0f".format(it) })

    @Test fun readinessBand_switches_at_80_65_and_50() {
        assertEquals(ReadinessBand.Strong, readinessBand(80))
        assertEquals(ReadinessBand.Good, readinessBand(79))
        assertEquals(ReadinessBand.Good, readinessBand(65))
        assertEquals(ReadinessBand.Fair, readinessBand(64))
        assertEquals(ReadinessBand.Fair, readinessBand(50))
        assertEquals(ReadinessBand.Low, readinessBand(49))
    }
}
