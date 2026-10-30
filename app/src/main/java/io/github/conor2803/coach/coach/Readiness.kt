package io.github.conor2803.coach.coach

import io.github.conor2803.coach.health.DayValue
import io.github.conor2803.coach.health.HealthHistory
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor

data class Factor(val value: Double, val baseline: Double?, val score: Int, val summary: String)

data class Readiness(val score: Int, val label: String, val sleep: Factor?, val hrv: Factor?, val restingHr: Factor?)

object ReadinessCalculator {
    private const val BASELINE_DAYS = 7L
    private const val MIN_BASELINE_VALUES = 3
    private const val FULL_SLEEP_MINUTES = 450.0
    private const val EMPTY_SLEEP_MINUTES = 240.0
    private const val SLEEP_NUDGE_MAX = 10.0
    private const val SLEEP_NUDGE_MINUTES_PER_POINT = 6.0
    private const val USUAL_SLEEP_MARGIN_MINUTES = 15
    private const val AT_BASELINE_SCORE = 70.0
    private const val HRV_POINTS_PER_PERCENT = 3.0
    private const val RESTING_HR_POINTS_PER_BPM = 4.0
    private const val USUAL_PERCENT_MARGIN = 2.0

    private const val SLEEP_WEIGHT = 0.4
    private const val HRV_WEIGHT = 0.35
    private const val RESTING_HR_WEIGHT = 0.25

    fun from(history: HealthHistory, today: LocalDate): Readiness? {
        val sleep = sleep(history, today)
        val hrv = hrv(history.hrv, today)
        val restingHr = restingHr(history.restingHr, today)
        val weighted = listOf(sleep to SLEEP_WEIGHT, hrv to HRV_WEIGHT, restingHr to RESTING_HR_WEIGHT)
            .mapNotNull { (factor, weight) -> factor?.let { it.score * weight to weight } }
        if (weighted.isEmpty()) return null
        val score = roundHalfUp(weighted.sumOf { it.first } / weighted.sumOf { it.second })
        return Readiness(score, label(score), sleep, hrv, restingHr)
    }

    private fun label(score: Int): String = when {
        score >= 80 -> "Primed"
        score >= 65 -> "Steady"
        score >= 50 -> "Take it easy"
        else -> "Rest up"
    }

    private fun sleep(history: HealthHistory, today: LocalDate): Factor? {
        val last = history.nights.firstOrNull { it.date == today }
            ?: history.nights.firstOrNull { it.date == today.minusDays(1) }
            ?: return null
        val minutes = last.asleepMinutes.toDouble()
        val baseline = baseline(
            history.nights.filter { it.date != last.date }.map { DayValue(it.date, it.asleepMinutes.toDouble()) },
            today,
        )
        val base = ((minutes - EMPTY_SLEEP_MINUTES) / (FULL_SLEEP_MINUTES - EMPTY_SLEEP_MINUTES) * 100)
            .coerceIn(0.0, 100.0)
        val nudge = baseline
            ?.let { ((minutes - it) / SLEEP_NUDGE_MINUTES_PER_POINT).coerceIn(-SLEEP_NUDGE_MAX, SLEEP_NUDGE_MAX) }
            ?: 0.0
        val score = roundHalfUp((base + nudge).coerceIn(0.0, 100.0))
        val summary = baseline?.let { sleepSummary(last.asleepMinutes, roundHalfUp(it)) } ?: hm(last.asleepMinutes)
        return Factor(minutes, baseline, score, summary)
    }

    private fun sleepSummary(minutes: Int, usual: Int): String {
        val diff = minutes - usual
        return when {
            abs(diff) < USUAL_SLEEP_MARGIN_MINUTES -> "${hm(minutes)} · usual ${hm(usual)}"
            diff > 0 -> "${hm(minutes)} · $diff min more than usual"
            else -> "${hm(minutes)} · ${-diff} min less than usual"
        }
    }

    private fun hrv(series: List<DayValue>, today: LocalDate): Factor? {
        val value = todayOrYesterday(series, today) ?: return null
        val baseline = baseline(series, today)
        val ms = "${roundHalfUp(value)} ms"
        if (baseline == null) return Factor(value, null, roundHalfUp(AT_BASELINE_SCORE), ms)
        val percent = (value - baseline) / baseline * 100
        val score = roundHalfUp((AT_BASELINE_SCORE + HRV_POINTS_PER_PERCENT * percent).coerceIn(0.0, 100.0))
        val summary = when {
            abs(percent) < USUAL_PERCENT_MARGIN -> "$ms · usual ${roundHalfUp(baseline)} ms"
            percent > 0 -> "$ms · ${roundHalfUp(percent)}% above usual"
            else -> "$ms · ${roundHalfUp(-percent)}% below usual"
        }
        return Factor(value, baseline, score, summary)
    }

    private fun restingHr(series: List<DayValue>, today: LocalDate): Factor? {
        val value = todayOrYesterday(series, today) ?: return null
        val baseline = baseline(series, today)
        val bpm = "${roundHalfUp(value)} bpm"
        if (baseline == null) return Factor(value, null, roundHalfUp(AT_BASELINE_SCORE), bpm)
        val diff = value - baseline
        val score = roundHalfUp((AT_BASELINE_SCORE - RESTING_HR_POINTS_PER_BPM * diff).coerceIn(0.0, 100.0))
        val whole = roundHalfUp(abs(diff))
        val summary = when {
            whole == 0 -> "$bpm · usual ${roundHalfUp(baseline)} bpm"
            diff > 0 -> "$bpm · $whole bpm above usual"
            else -> "$bpm · $whole bpm below usual"
        }
        return Factor(value, baseline, score, summary)
    }

    private fun todayOrYesterday(series: List<DayValue>, today: LocalDate): Double? =
        series.firstOrNull { it.date == today }?.value
            ?: series.firstOrNull { it.date == today.minusDays(1) }?.value

    private fun baseline(series: List<DayValue>, today: LocalDate): Double? =
        series.filter { it.date < today && it.date >= today.minusDays(BASELINE_DAYS) }
            .map { it.value }
            .takeIf { it.size >= MIN_BASELINE_VALUES }
            ?.average()

    private fun roundHalfUp(x: Double): Int = floor(x + 0.5).toInt()

    private fun hm(minutes: Int) = "${minutes / 60}h${"%02d".format(minutes % 60)}"
}
