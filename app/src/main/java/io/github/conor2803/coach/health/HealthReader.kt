package io.github.conor2803.coach.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.reflect.KClass

/**
 * Reads what Garmin Connect and RingConn have written into Health Connect
 * and turns it into a short plain-text summary for the coach.
 *
 * Each section is read on its own, so one missing permission or empty data type
 * never stops the rest of the summary.
 */
class HealthReader(private val context: Context) {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }
    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayTime = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK)
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

    suspend fun missingPermissions(): Set<String> =
        PERMISSIONS - client.permissionController.getGrantedPermissions()

    suspend fun summary(now: ZonedDateTime = ZonedDateTime.now(zone)): String = buildString {
        val midnight = now.toLocalDate().atStartOfDay(zone).toInstant()
        val end = now.toInstant()

        section("Sleep (last 30h)") { sleep(end.minus(Duration.ofHours(30)), end) }
        section("Resting heart rate (7 days)") { restingHr(end.minus(Duration.ofDays(7)), end) }
        section("HRV (RMSSD)") { hrv(end) }
        section("Heart rate today") { heartRate(midnight, end) }
        section("Steps") { steps(midnight, end) }
        section("Blood oxygen (last 12h)") { spo2(end.minus(Duration.ofHours(12)), end) }
        section("Workouts (last 7 days)") { workouts(end.minus(Duration.ofDays(7)), end) }
    }

    private suspend fun StringBuilder.section(title: String, block: suspend () -> String) {
        append("• ").append(title).append(": ")
        val text = try {
            block().ifBlank { "no data" }
        } catch (e: SecurityException) {
            "permission not granted"
        } catch (e: Exception) {
            "couldn't read (${e.javaClass.simpleName})"
        }
        append(text).append('\n')
    }

    private suspend fun sleep(start: Instant, end: Instant): String {
        val sessions = readAll(SleepSessionRecord::class, start, end)
        if (sessions.isEmpty()) return ""
        return sessions.sortedBy { it.startTime }.joinToString("; ") { s ->
            val total = Duration.between(s.startTime, s.endTime)
            val stageMins = s.stages.groupBy { it.stage }.mapValues { (_, st) ->
                st.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
            }
            val asleep = stageMins.filterKeys { it !in AWAKE_STAGES }.values.sum()
            val stages = listOfNotNull(
                stageMins[SleepSessionRecord.STAGE_TYPE_DEEP]?.let { "deep ${it}m" },
                stageMins[SleepSessionRecord.STAGE_TYPE_REM]?.let { "REM ${it}m" },
                stageMins[SleepSessionRecord.STAGE_TYPE_LIGHT]?.let { "light ${it}m" },
                AWAKE_STAGES.sumOf { stageMins[it] ?: 0L }.takeIf { it > 0 }?.let { "awake ${it}m" },
            ).joinToString(", ")
            buildString {
                append("[${source(s)}] ${fmt(s.startTime)} → ${fmt(s.endTime)}, in bed ${hm(total)}")
                if (asleep > 0) append(", asleep ${hm(Duration.ofMinutes(asleep))}")
                if (stages.isNotBlank()) append(" ($stages)")
            }
        }
    }

    private suspend fun restingHr(start: Instant, end: Instant): String {
        val recs = readAll(RestingHeartRateRecord::class, start, end)
        if (recs.isEmpty()) return ""
        val byDay = recs.groupBy { it.time.atZone(zone).toLocalDate() }
            .toSortedMap()
            .mapValues { (_, r) -> r.map { it.beatsPerMinute }.average() }
        return byDay.entries.joinToString(", ") { (d, bpm) -> "${d.format(day)} ${bpm.toInt()}" } +
            " bpm"
    }

    private suspend fun hrv(end: Instant): String {
        val week = readAll(HeartRateVariabilityRmssdRecord::class, end.minus(Duration.ofDays(7)), end)
        if (week.isEmpty()) return ""
        val recent = week.filter { it.time.isAfter(end.minus(Duration.ofHours(14))) }
        val weekAvg = week.map { it.heartRateVariabilityMillis }.average()
        return buildString {
            if (recent.isNotEmpty()) {
                append("last night/today avg ${recent.map { it.heartRateVariabilityMillis }.average().toInt()} ms, ")
            } else {
                append("no reading in the last 14h, ")
            }
            append("7-day avg ${weekAvg.toInt()} ms (${week.size} readings)")
        }
    }

    private suspend fun heartRate(start: Instant, end: Instant): String {
        val agg = client.aggregate(
            AggregateRequest(
                metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_MAX),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
        )
        val avg = agg[HeartRateRecord.BPM_AVG] ?: return ""
        return "avg $avg, min ${agg[HeartRateRecord.BPM_MIN]}, max ${agg[HeartRateRecord.BPM_MAX]} bpm"
    }

    private suspend fun steps(midnight: Instant, end: Instant): String {
        // Aggregates de-duplicate when both Garmin and the ring count steps.
        suspend fun count(a: Instant, b: Instant) = client.aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(a, b))
        )[StepsRecord.COUNT_TOTAL]

        val today = count(midnight, end)
        val yesterday = count(midnight.minus(Duration.ofDays(1)), midnight)
        if (today == null && yesterday == null) return ""
        return "today ${today ?: 0}, yesterday ${yesterday ?: 0}"
    }

    private suspend fun spo2(start: Instant, end: Instant): String {
        val recs = readAll(OxygenSaturationRecord::class, start, end)
        if (recs.isEmpty()) return ""
        val values = recs.map { it.percentage.value }
        return "avg ${"%.1f".format(Locale.UK, values.average())}%, low ${"%.0f".format(Locale.UK, values.min())}%"
    }

    private suspend fun workouts(start: Instant, end: Instant): String {
        val sessions = readAll(ExerciseSessionRecord::class, start, end)
        if (sessions.isEmpty()) return "none recorded"
        return sessions.sortedBy { it.startTime }.joinToString("; ") { s ->
            val name = s.title?.takeIf { it.isNotBlank() } ?: exerciseName(s.exerciseType)
            "[${source(s)}] ${fmt(s.startTime)} $name ${hm(Duration.between(s.startTime, s.endTime))}"
        } + workoutDistances(sessions)
    }

    /** Adds distance per session where Health Connect has it. Kept separate so a failure here is harmless. */
    private suspend fun workoutDistances(sessions: List<ExerciseSessionRecord>): String {
        val parts = sessions.sortedBy { it.startTime }.mapNotNull { s ->
            val km = runCatching {
                client.aggregate(
                    AggregateRequest(
                        setOf(DistanceRecord.DISTANCE_TOTAL),
                        TimeRangeFilter.between(s.startTime, s.endTime),
                    )
                )[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
            }.getOrNull()
            km?.takeIf { it >= 0.1 }?.let { "${fmt(s.startTime)} ${"%.1f".format(Locale.UK, it)} km" }
        }
        return if (parts.isEmpty()) "" else " | distances: " + parts.joinToString(", ")
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, start: Instant, end: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = token,
                )
            )
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }

    private fun source(r: Record): String {
        val pkg = r.metadata.dataOrigin.packageName
        return when {
            "garmin" in pkg -> "Garmin"
            "ringconn" in pkg.lowercase() -> "RingConn"
            else -> pkg.substringAfterLast('.')
        }
    }

    private fun fmt(i: Instant) = i.atZone(zone).format(dayTime)

    private fun hm(d: Duration) = "${d.toHours()}h${"%02d".format(Locale.UK, d.toMinutes() % 60)}"

    private fun exerciseName(type: Int) = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "run"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "treadmill run"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "walk"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "hike"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "ride"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "indoor ride"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "strength"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "yoga"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "swim"
        else -> "workout (type $type)"
    }

    companion object {
        const val BACKGROUND_READ = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            BACKGROUND_READ,
        )

        private val AWAKE_STAGES = setOf(
            SleepSessionRecord.STAGE_TYPE_AWAKE,
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED,
        )

        fun isAvailable(context: Context) =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }
}
