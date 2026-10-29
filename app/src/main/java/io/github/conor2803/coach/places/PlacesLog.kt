package io.github.conor2803.coach.places

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.time.Duration

data class PlaceEvent(val place: String, val type: String, val at: Long)

/**
 * Append-only log of "arrived at / left" events, kept on the phone only.
 * One JSON object per line; anything older than 14 days is dropped.
 */
object PlacesLog {
    private const val FILE = "place_events.jsonl"
    private val KEEP = Duration.ofDays(14).toMillis()
    private val lock = Any()

    fun append(context: Context, place: String, type: String, at: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val file = file(context)
            val line = JSONObject().put("place", place).put("type", type).put("at", at).toString()
            file.appendText(line + "\n")
            prune(file)
        }
    }

    fun since(context: Context, from: Long): List<PlaceEvent> = all(context).filter { it.at >= from }

    /** The last event before [time], so the coach knows where you were at the start of the day. */
    fun lastBefore(context: Context, time: Long): PlaceEvent? = all(context).lastOrNull { it.at < time }

    private fun all(context: Context): List<PlaceEvent> = synchronized(lock) {
        val file = file(context)
        if (!file.exists()) return emptyList()
        file.readLines().mapNotNull { parse(it) }.sortedBy { it.at }
    }

    private fun parse(line: String): PlaceEvent? = runCatching {
        val o = JSONObject(line)
        PlaceEvent(o.getString("place"), o.getString("type"), o.getLong("at"))
    }.getOrNull()

    private fun prune(file: File) {
        val cutoff = System.currentTimeMillis() - KEEP
        val lines = file.readLines()
        val kept = lines.filter { (parse(it)?.at ?: 0L) >= cutoff }
        if (kept.size != lines.size) file.writeText(kept.joinToString("") { it + "\n" })
    }

    private fun file(context: Context) = File(context.filesDir, FILE)
}
