package io.github.conor2803.coach.ui.today

import org.junit.Assert.assertEquals
import org.junit.Test

class PlanTextTest {

    @Test
    fun `finds every clock time in a line`() {
        val line = "Run 18:00–18:40, dinner by 19:15"
        assertEquals(listOf("18:00", "18:40", "19:15"), timeSpans(line).map { line.substring(it) })
    }

    @Test
    fun `ignores numbers that are not times`() {
        assertEquals(emptyList<IntRange>(), timeSpans("Easy 5k, 30 minutes, HRV 48"))
        assertEquals(emptyList<IntRange>(), timeSpans("Score 25:99 is not a time"))
    }

    @Test
    fun `a one-paragraph plan is split into sentences`() {
        assertEquals(
            listOf("No structured workout today.", "Dinner around 20:00, screens off by 21:45.", "Listen to your body."),
            planLines("No structured workout today. Dinner around 20:00, screens off by 21:45. Listen to your body."),
        )
    }

    @Test
    fun `splits a plan into clean lines`() {
        assertEquals(
            listOf("Run 18:00", "Dinner 19:15", "Bed 22:30"),
            planLines("- Run 18:00\n\n• Dinner 19:15 \n* Bed 22:30"),
        )
    }
}
