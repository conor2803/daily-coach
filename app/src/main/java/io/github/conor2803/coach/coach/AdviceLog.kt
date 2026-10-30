package io.github.conor2803.coach.coach

import android.content.Context
import io.github.conor2803.coach.Advice
import org.json.JSONObject
import java.io.File
import java.time.Duration

data class LoggedAdvice(val advice: Advice, val engine: Engine)

/**
 * Append-only log of every check-in's advice, kept on the phone only.
 * One JSON object per line; anything older than 90 days is dropped.
 */
object AdviceLog {
    private const val FILE = "advice_log.jsonl"
    private val KEEP = Duration.ofDays(90).toMillis()
    private val lock = Any()

    fun append(context: Context, advice: Advice, engine: Engine) {
        synchronized(lock) {
            val file = file(context)
            file.appendText(encode(LoggedAdvice(advice, engine)) + "\n")
            prune(file)
        }
    }

    /** Newest first. */
    fun recent(context: Context, limit: Int = 30): List<LoggedAdvice> = synchronized(lock) {
        val file = file(context)
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { parse(it) }.sortedByDescending { it.advice.at }.take(limit)
    }

    internal fun encode(entry: LoggedAdvice): String = JSONObject()
        .put("headline", entry.advice.headline)
        .put("plan", entry.advice.plan)
        .put("at", entry.advice.at)
        .put("engine", entry.engine.name)
        .toString()

    internal fun parse(line: String): LoggedAdvice? = runCatching {
        val o = JSONObject(line)
        LoggedAdvice(
            Advice(o.getString("headline"), o.getString("plan"), o.getLong("at")),
            Engine.fromStored(o.optString("engine")),
        )
    }.getOrNull()

    private fun prune(file: File) {
        val cutoff = System.currentTimeMillis() - KEEP
        val lines = file.readLines()
        val kept = lines.filter { (parse(it)?.advice?.at ?: 0L) >= cutoff }
        if (kept.size != lines.size) file.writeText(kept.joinToString("") { it + "\n" })
    }

    private fun file(context: Context) = File(context.filesDir, FILE)
}
