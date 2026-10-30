package io.github.conor2803.coach

import io.github.conor2803.coach.coach.ReadinessCalculator
import io.github.conor2803.coach.health.DayValue
import io.github.conor2803.coach.health.HealthHistory
import io.github.conor2803.coach.health.Night
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReadinessTest {
    private val today = LocalDate.of(2026, 10, 30)

    private fun night(daysAgo: Long, asleepMinutes: Int) = Night(
        date = today.minusDays(daysAgo),
        start = Instant.EPOCH,
        end = Instant.EPOCH,
        asleepMinutes = asleepMinutes,
        deepMinutes = 0, remMinutes = 0, lightMinutes = 0, awakeMinutes = 0,
        source = "Garmin",
    )

    private fun days(vararg valuesFromYesterday: Double) =
        valuesFromYesterday.mapIndexed { i, v -> DayValue(today.minusDays(i + 1L), v) }

    private fun history(
        nights: List<Night> = emptyList(),
        hrv: List<DayValue> = emptyList(),
        restingHr: List<DayValue> = emptyList(),
    ) = HealthHistory(nights, hrv, restingHr, emptyList(), emptyList())

    private fun sleepOnly(minutes: Int) = ReadinessCalculator.from(history(listOf(night(0, minutes))), today)!!.sleep!!

    @Test
    fun nothing_to_go_on_gives_no_readiness() {
        assertNull(ReadinessCalculator.from(HealthHistory.EMPTY, today))
    }

    @Test
    fun a_night_older_than_yesterday_is_not_last_nights_sleep() {
        assertNull(ReadinessCalculator.from(history(listOf(night(2, 480))), today))
    }

    @Test
    fun before_this_mornings_sleep_has_synced_last_nights_counts_and_stays_out_of_its_own_baseline() {
        val nights = listOf(night(1, 480)) + (2L..5L).map { night(it, 420) }
        val sleep = ReadinessCalculator.from(history(nights), today)!!.sleep!!
        assertEquals(480.0, sleep.value, 0.0)
        assertEquals(420.0, sleep.baseline!!, 0.0)
    }

    @Test
    fun sleep_scores_100_at_450_minutes_and_above() {
        assertEquals(100, sleepOnly(450).score)
        assertEquals(100, sleepOnly(600).score)
    }

    @Test
    fun sleep_scores_0_at_240_minutes_and_below() {
        assertEquals(0, sleepOnly(240).score)
        assertEquals(0, sleepOnly(100).score)
    }

    @Test
    fun sleep_scores_linearly_between_240_and_450() {
        assertEquals(50, sleepOnly(345).score)
    }

    @Test
    fun sleep_without_a_baseline_shows_only_the_duration() {
        val f = sleepOnly(365)
        assertNull(f.baseline)
        assertEquals("6h05", f.summary)
    }

    @Test
    fun baseline_needs_at_least_three_values() {
        val twoPrior = listOf(night(0, 400), night(1, 400), night(2, 400))
        assertNull(ReadinessCalculator.from(history(twoPrior), today)!!.sleep!!.baseline)
        val threePrior = twoPrior + night(3, 400)
        assertEquals(400.0, ReadinessCalculator.from(history(threePrior), today)!!.sleep!!.baseline!!, 0.0)
    }

    @Test
    fun baseline_excludes_today_and_anything_older_than_seven_days() {
        val nights = listOf(night(0, 600), night(1, 400), night(2, 400), night(3, 400), night(8, 100))
        assertEquals(400.0, ReadinessCalculator.from(history(nights), today)!!.sleep!!.baseline!!, 0.0)
    }

    @Test
    fun sleeping_longer_than_usual_nudges_the_score_up_and_says_so() {
        val nights = listOf(night(0, 440), night(1, 415), night(2, 415), night(3, 415))
        val f = ReadinessCalculator.from(history(nights), today)!!.sleep!!
        assertEquals(99, f.score)
        assertEquals("7h20 · 25 min more than usual", f.summary)
    }

    @Test
    fun sleeping_shorter_than_usual_nudges_the_score_down_and_says_so() {
        val nights = listOf(night(0, 345), night(1, 430), night(2, 430), night(3, 430))
        val f = ReadinessCalculator.from(history(nights), today)!!.sleep!!
        assertEquals(40, f.score)
        assertEquals("5h45 · 85 min less than usual", f.summary)
    }

    @Test
    fun sleep_close_to_usual_says_usual() {
        val nights = listOf(night(0, 365), night(1, 370), night(2, 370), night(3, 370))
        assertEquals("6h05 · usual 6h10", ReadinessCalculator.from(history(nights), today)!!.sleep!!.summary)
    }

    @Test
    fun sleep_nudge_is_capped_at_ten_points() {
        val nights = listOf(night(0, 345), night(1, 600), night(2, 600), night(3, 600))
        assertEquals(40, ReadinessCalculator.from(history(nights), today)!!.sleep!!.score)
    }

    @Test
    fun hrv_without_a_baseline_scores_70_and_shows_the_value() {
        val f = ReadinessCalculator.from(history(hrv = listOf(DayValue(today, 48.0))), today)!!.hrv!!
        assertEquals(70, f.score)
        assertEquals("48 ms", f.summary)
    }

    @Test
    fun hrv_at_baseline_scores_70() {
        val series = listOf(DayValue(today, 50.0)) + days(50.0, 50.0, 50.0)
        assertEquals(70, ReadinessCalculator.from(history(hrv = series), today)!!.hrv!!.score)
    }

    @Test
    fun hrv_gains_3_points_per_percent_above_baseline() {
        val series = listOf(DayValue(today, 52.0)) + days(50.0, 50.0, 50.0)
        val f = ReadinessCalculator.from(history(hrv = series), today)!!.hrv!!
        assertEquals(82, f.score)
        assertEquals("52 ms · 4% above usual", f.summary)
    }

    @Test
    fun hrv_loses_3_points_per_percent_below_baseline() {
        val series = listOf(DayValue(today, 45.0)) + days(50.0, 50.0, 50.0)
        val f = ReadinessCalculator.from(history(hrv = series), today)!!.hrv!!
        assertEquals(40, f.score)
        assertEquals("45 ms · 10% below usual", f.summary)
    }

    @Test
    fun hrv_score_stays_within_0_and_100() {
        val high = listOf(DayValue(today, 100.0)) + days(50.0, 50.0, 50.0)
        val low = listOf(DayValue(today, 5.0)) + days(50.0, 50.0, 50.0)
        assertEquals(100, ReadinessCalculator.from(history(hrv = high), today)!!.hrv!!.score)
        assertEquals(0, ReadinessCalculator.from(history(hrv = low), today)!!.hrv!!.score)
    }

    @Test
    fun hrv_falls_back_to_yesterday_when_today_has_no_reading() {
        val series = days(55.0, 50.0, 50.0, 50.0)
        val f = ReadinessCalculator.from(history(hrv = series), today)!!.hrv!!
        assertEquals(55.0, f.value, 0.0)
    }

    @Test
    fun hrv_two_days_old_is_not_used() {
        val series = listOf(DayValue(today.minusDays(2), 55.0))
        assertNull(ReadinessCalculator.from(history(hrv = series), today))
    }

    @Test
    fun resting_hr_without_a_baseline_scores_70() {
        val f = ReadinessCalculator.from(history(restingHr = listOf(DayValue(today, 52.0))), today)!!.restingHr!!
        assertEquals(70, f.score)
        assertEquals("52 bpm", f.summary)
    }

    @Test
    fun lower_resting_hr_scores_4_points_better_per_bpm() {
        val series = listOf(DayValue(today, 48.0)) + days(52.0, 52.0, 52.0)
        val f = ReadinessCalculator.from(history(restingHr = series), today)!!.restingHr!!
        assertEquals(86, f.score)
        assertEquals("48 bpm · 4 bpm below usual", f.summary)
    }

    @Test
    fun higher_resting_hr_scores_4_points_worse_per_bpm() {
        val series = listOf(DayValue(today, 55.0)) + days(52.0, 52.0, 52.0)
        val f = ReadinessCalculator.from(history(restingHr = series), today)!!.restingHr!!
        assertEquals(58, f.score)
        assertEquals("55 bpm · 3 bpm above usual", f.summary)
    }

    @Test
    fun overall_score_weights_sleep_hrv_and_resting_hr_40_35_25() {
        val r = ReadinessCalculator.from(
            history(
                nights = listOf(night(0, 450)),
                hrv = listOf(DayValue(today, 50.0)),
                restingHr = listOf(DayValue(today, 50.0)),
            ),
            today,
        )!!
        assertEquals(100, r.sleep!!.score)
        assertEquals(70, r.hrv!!.score)
        assertEquals(70, r.restingHr!!.score)
        assertEquals(82, r.score)
    }

    @Test
    fun missing_factors_are_left_out_and_the_weights_renormalised() {
        val r = ReadinessCalculator.from(
            history(nights = listOf(night(0, 450)), hrv = listOf(DayValue(today, 50.0))),
            today,
        )!!
        assertNull(r.restingHr)
        assertEquals(86, r.score)
    }

    @Test
    fun a_single_factor_is_the_whole_score() {
        assertEquals(70, ReadinessCalculator.from(history(restingHr = listOf(DayValue(today, 50.0))), today)!!.score)
    }

    @Test
    fun scores_round_half_up() {
        val series = listOf(DayValue(today, 52.375)) + days(52.0, 52.0, 52.0)
        assertEquals(69, ReadinessCalculator.from(history(restingHr = series), today)!!.restingHr!!.score)
    }

    private fun labelFor(restingHrScore: Int): String {
        val bpmBelow = (restingHrScore - 70) / 4.0
        val series = listOf(DayValue(today, 50.0 - bpmBelow)) + days(50.0, 50.0, 50.0)
        val r = ReadinessCalculator.from(history(restingHr = series), today)
        assertNotNull(r)
        assertEquals(restingHrScore, r!!.score)
        return r.label
    }

    @Test
    fun score_80_and_above_is_primed() = assertEquals("Primed", labelFor(82))

    @Test
    fun score_65_to_79_is_steady() {
        assertEquals("Steady", labelFor(66))
        assertEquals("Steady", labelFor(70))
    }

    @Test
    fun score_50_to_64_is_take_it_easy() = assertEquals("Take it easy", labelFor(54))

    @Test
    fun score_below_50_is_rest_up() = assertEquals("Rest up", labelFor(46))
}
