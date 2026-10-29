package io.github.conor2803.coach.coach

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.conor2803.coach.Prefs
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Check-ins run at the times set in the app (default 07:30 and 16:30).
 * Each scheduled run queues the next one, so the times can be changed any time.
 */
object Scheduler {
    const val SCHEDULED = "coach-checkin"
    const val NOW = "coach-now"

    /** Queue the next check-in only if nothing is queued yet. */
    fun ensureScheduled(context: Context) =
        enqueueNext(context, ExistingWorkPolicy.KEEP)

    /** Recalculate after the times change in settings. */
    fun reschedule(context: Context) =
        enqueueNext(context, ExistingWorkPolicy.REPLACE)

    /** Called from inside a running check-in: queue the next one after this one finishes. */
    fun queueNextFromWorker(context: Context) =
        enqueueNext(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

    fun runNow(context: Context) {
        val request = baseRequest(Duration.ZERO, scheduled = false)
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }

    fun nextRun(context: Context, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val times = parseTimes(Prefs(context).checkInTimes)
        return times
            .flatMap { listOf(now.with(it), now.plusDays(1).with(it)) }
            .filter { it.isAfter(now.plusMinutes(1)) }
            .minOrNull() ?: now.plusDays(1)
    }

    fun parseTimes(raw: String): List<LocalTime> =
        raw.split(',', ' ', ';')
            .mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
            .distinct()
            .ifEmpty { listOf(LocalTime.of(16, 30)) }

    private fun enqueueNext(context: Context, policy: ExistingWorkPolicy) {
        val now = ZonedDateTime.now()
        val delay = Duration.between(now, nextRun(context, now))
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SCHEDULED, policy, baseRequest(delay, scheduled = true))
    }

    private fun baseRequest(delay: Duration, scheduled: Boolean): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<CoachWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(CoachWorker.KEY_SCHEDULED to scheduled))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
}
