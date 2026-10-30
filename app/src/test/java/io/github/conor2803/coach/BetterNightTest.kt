package io.github.conor2803.coach

import io.github.conor2803.coach.health.Night
import io.github.conor2803.coach.health.betterNight
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class BetterNightTest {
    private val start = Instant.parse("2026-10-29T22:30:00Z")

    private fun night(source: String, inBedMinutes: Long, stageMinutes: Int) = Night(
        date = LocalDate.of(2026, 10, 30),
        start = start,
        end = start.plusSeconds(inBedMinutes * 60),
        asleepMinutes = stageMinutes,
        deepMinutes = stageMinutes, remMinutes = 0, lightMinutes = 0, awakeMinutes = 0,
        source = source,
    )

    @Test
    fun the_night_with_more_stage_minutes_wins() {
        val garmin = night("Garmin", inBedMinutes = 450, stageMinutes = 420)
        val ring = night("RingConn", inBedMinutes = 480, stageMinutes = 300)
        assertEquals(garmin, betterNight(garmin, ring))
        assertEquals(garmin, betterNight(ring, garmin))
    }

    @Test
    fun a_night_with_stages_beats_a_longer_one_without() {
        val staged = night("Garmin", inBedMinutes = 400, stageMinutes = 380)
        val bare = night("RingConn", inBedMinutes = 480, stageMinutes = 0)
        assertEquals(staged, betterNight(bare, staged))
    }

    @Test
    fun with_equal_stage_minutes_the_longer_time_in_bed_wins() {
        val short = night("Garmin", inBedMinutes = 400, stageMinutes = 0)
        val long = night("RingConn", inBedMinutes = 460, stageMinutes = 0)
        assertEquals(long, betterNight(short, long))
        assertEquals(long, betterNight(long, short))
    }
}
