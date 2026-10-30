package io.github.conor2803.coach.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass

/** [date] is the morning you woke. */
data class Night(
    val date: LocalDate,
    val start: Instant,
    val end: Instant,
    val asleepMinutes: Int,
    val deepMinutes: Int,
    val remMinutes: Int,
    val lightMinutes: Int,
    val awakeMinutes: Int,
    val source: String,
) {
    val stageMinutes: Int get() = deepMinutes + remMinutes + lightMinutes + awakeMinutes
    val inBedMinutes: Long get() = Duration.between(start, end).toMinutes()
}

data class DayValue(val date: LocalDate, val value: Double)

data class Workout(val start: Instant, val minutes: Int, val name: String, val km: Double?, val source: String)

data class HealthHistory(
    val nights: List<Night>,
    val hrv: List<DayValue>,
    val restingHr: List<DayValue>,
    val steps: List<DayValue>,
    val workouts: List<Workout>,
) {
    companion object {
        val EMPTY = HealthHistory(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

/** Reads each series on its own, so one missing permission leaves only that list empty. */
class HealthHistoryReader(private val context: Context) {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun read(
        days: Int = 30,
        now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
    ): HealthHistory {
        val zone = now.zone
        val end = now.toInstant()
        val firstDay = now.toLocalDate().minusDays(days.toLong())
        val start = firstDay.atStartOfDay(zone).toInstant()
        return HealthHistory(
            nights = attempt { nights(start, end, zone) },
            hrv = attempt {
                dailyMeans(
                    HeartRateVariabilityRmssdRecord::class, start, end, zone,
                    { it.time }, { it.heartRateVariabilityMillis },
                )
            },
            restingHr = attempt {
                dailyMeans(
                    RestingHeartRateRecord::class, start, end, zone,
                    { it.time }, { it.beatsPerMinute.toDouble() },
                )
            },
            steps = attempt { steps(firstDay, now) },
            workouts = attempt { workouts(start, end) },
        )
    }

    private suspend fun <T> attempt(block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    private suspend fun nights(start: Instant, end: Instant, zone: ZoneId): List<Night> =
        readAll(SleepSessionRecord::class, start, end)
            .filter { Duration.between(it.startTime, it.endTime) >= MIN_NIGHT }
            .map { night(it, zone) }
            .groupBy { it.date }
            .values
            .map { sameNight -> sameNight.reduce(::betterNight) }
            .sortedBy { it.date }

    private fun night(s: SleepSessionRecord, zone: ZoneId): Night {
        val minutes = s.stages.groupBy { it.stage }.mapValues { (_, st) ->
            st.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
        }
        val inBed = Duration.between(s.startTime, s.endTime).toMinutes().toInt()
        val asleep = minutes.filterKeys { it !in HealthReader.AWAKE_STAGES }.values.sum()
        return Night(
            date = s.endTime.atZone(zone).toLocalDate(),
            start = s.startTime,
            end = s.endTime,
            asleepMinutes = if (s.stages.isEmpty()) inBed else asleep,
            deepMinutes = minutes[SleepSessionRecord.STAGE_TYPE_DEEP] ?: 0,
            remMinutes = minutes[SleepSessionRecord.STAGE_TYPE_REM] ?: 0,
            lightMinutes = minutes[SleepSessionRecord.STAGE_TYPE_LIGHT] ?: 0,
            awakeMinutes = HealthReader.AWAKE_STAGES.sumOf { minutes[it] ?: 0 },
            source = sourceName(s),
        )
    }

    private suspend fun <T : Record> dailyMeans(
        type: KClass<T>,
        start: Instant,
        end: Instant,
        zone: ZoneId,
        timeOf: (T) -> Instant,
        valueOf: (T) -> Double,
    ): List<DayValue> =
        readAll(type, start, end)
            .groupBy { timeOf(it).atZone(zone).toLocalDate() }
            .map { (date, records) -> DayValue(date, records.map(valueOf).average()) }
            .sortedBy { it.date }

    private suspend fun steps(firstDay: LocalDate, now: ZonedDateTime): List<DayValue> =
        client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    firstDay.atStartOfDay(),
                    now.toLocalDate().plusDays(1).atStartOfDay(),
                ),
                timeRangeSlicer = Period.ofDays(1),
            )
        ).mapNotNull { slice ->
            slice.result[StepsRecord.COUNT_TOTAL]?.let { DayValue(slice.startTime.toLocalDate(), it.toDouble()) }
        }.sortedBy { it.date }

    private suspend fun workouts(start: Instant, end: Instant): List<Workout> =
        readAll(ExerciseSessionRecord::class, start, end).sortedBy { it.startTime }.map { s ->
            Workout(
                start = s.startTime,
                minutes = Duration.between(s.startTime, s.endTime).toMinutes().toInt(),
                name = s.title?.takeIf { it.isNotBlank() } ?: exerciseName(s.exerciseType),
                km = distanceKm(s),
                source = sourceName(s),
            )
        }

    private suspend fun distanceKm(s: ExerciseSessionRecord): Double? = runCatching {
        client.aggregate(
            AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), TimeRangeFilter.between(s.startTime, s.endTime))
        )[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
    }.getOrNull()?.takeIf { it >= 0.1 }

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

    private fun sourceName(r: Record): String {
        val pkg = r.metadata.dataOrigin.packageName
        return when {
            "garmin" in pkg -> "Garmin"
            "ringconn" in pkg.lowercase() -> "RingConn"
            else -> pkg.substringAfterLast('.')
        }
    }

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
        else -> "workout"
    }

    private companion object {
        val MIN_NIGHT: Duration = Duration.ofHours(2)
    }
}

/** The night with more recorded stage minutes wins; with equal stage minutes, the longer time in bed. */
internal fun betterNight(a: Night, b: Night): Night = when {
    a.stageMinutes != b.stageMinutes -> if (a.stageMinutes > b.stageMinutes) a else b
    else -> if (a.inBedMinutes >= b.inBedMinutes) a else b
}
