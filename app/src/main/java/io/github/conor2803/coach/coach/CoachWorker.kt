package io.github.conor2803.coach.coach

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.conor2803.coach.Prefs
import io.github.conor2803.coach.context.ContextBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One check-in: gather the day's data, ask Claude, notify.
 * Scheduled runs queue the next check-in when they finish, success or not.
 */
class CoachWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val scheduled = inputData.getBoolean(KEY_SCHEDULED, false)

        try {
            if (prefs.apiKey.isBlank()) {
                fail(prefs, "Add your Anthropic API key in the Coach app.")
                return done(scheduled)
            }
            val briefing = ContextBuilder.build(ctx)
            val advice = withContext(Dispatchers.IO) {
                ClaudeClient.coach(prefs.apiKey, prefs.model, briefing)
            }
            prefs.lastAdvice = advice
            prefs.lastError = ""
            Notifier.advice(ctx, advice)
            return done(scheduled)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < MAX_RETRIES) return Result.retry()
            fail(prefs, e.message ?: e.javaClass.simpleName)
            return done(scheduled)
        }
    }

    private fun fail(prefs: Prefs, message: String) {
        prefs.lastError = message
        Notifier.error(applicationContext, message)
    }

    /** Always succeed at the end so the chain of scheduled check-ins never breaks. */
    private fun done(scheduled: Boolean): Result {
        if (scheduled) Scheduler.queueNextFromWorker(applicationContext)
        return Result.success()
    }

    companion object {
        const val KEY_SCHEDULED = "scheduled"
        private const val MAX_RETRIES = 2
    }
}
