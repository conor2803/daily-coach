package io.github.conor2803.coach.context

import android.content.Context
import io.github.conor2803.coach.Prefs
import io.github.conor2803.coach.coach.ReadinessCalculator
import io.github.conor2803.coach.health.HealthHistoryReader
import io.github.conor2803.coach.health.HealthReader
import io.github.conor2803.coach.places.GeofenceManager
import io.github.conor2803.coach.places.PlacesLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Gathers everything the coach needs into one plain-text briefing.
 * This exact text is what gets sent to Claude, and what "Preview data" shows.
 */
object ContextBuilder {
    private val header = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm", Locale.UK)
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

    suspend fun build(context: Context): String = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val midnight = now.toLocalDate().atStartOfDay(zone)

        buildString {
            appendLine("NOW: ${now.format(header)} (${zone.id})")
            appendLine()
            appendLine("MY GOALS AND PREFERENCES:")
            appendLine(prefs.goals.ifBlank { "(none set)" })
            appendLine()

            appendLine("HEALTH (from Health Connect: Garmin watch + RingConn ring):")
            val health = if (HealthReader.isAvailable(context)) {
                runCatching { HealthReader(context).summary(now) }
                    .getOrElse { "couldn't read Health Connect (${it.message})" }
            } else {
                "Health Connect isn't available on this phone"
            }
            appendLine(health.trimEnd())
            appendLine()

            appendLine("READINESS (worked out on the phone against my last 7 days):")
            appendLine(readiness(context, now))
            appendLine()

            appendLine("WHERE I'VE BEEN TODAY:")
            appendLine(places(context, prefs, midnight.toInstant().toEpochMilli(), zone))
            appendLine()

            appendLine("CALENDAR (now → tomorrow night):")
            val calEnd = midnight.plusDays(2).toInstant().toEpochMilli()
            appendLine(
                runCatching { CalendarReader.events(context, System.currentTimeMillis(), calEnd) }
                    .getOrElse { "couldn't read calendar" }.trim()
            )
            appendLine()

            appendLine("WEATHER (next 16 hours):")
            appendLine(weather(context, prefs))
            appendLine()

            prefs.lastAdvice?.let {
                val at = Instant.ofEpochMilli(it.at).atZone(zone)
                val ago = Duration.between(at, now).toHours()
                appendLine("YOUR LAST CHECK-IN (${ago}h ago): ${it.headline}")
            }
        }.trim()
    }

    private suspend fun readiness(context: Context, now: ZonedDateTime): String {
        if (!HealthReader.isAvailable(context)) return "not available"
        val readiness = runCatching {
            ReadinessCalculator.from(HealthHistoryReader(context).read(days = 14, now = now), now.toLocalDate())
        }.getOrNull() ?: return "not enough data yet"
        return buildString {
            append("${readiness.score}/100, ${readiness.label}.")
            readiness.sleep?.let { append(" Sleep: ${it.summary}.") }
            readiness.hrv?.let { append(" HRV: ${it.summary}.") }
            readiness.restingHr?.let { append(" Resting HR: ${it.summary}.") }
        }
    }

    private fun places(context: Context, prefs: Prefs, from: Long, zone: ZoneId): String {
        if (prefs.places.isEmpty()) return "no places set up yet"
        val sb = StringBuilder()
        PlacesLog.lastBefore(context, from)?.let {
            val where = if (it.type == "arrived") "at ${it.place}" else "not at any saved place (left ${it.place})"
            sb.append("  start of day: $where\n")
        }
        val events = PlacesLog.since(context, from)
        if (events.isEmpty()) sb.append("  no arrivals/departures logged today\n")
        events.forEach {
            val t = Instant.ofEpochMilli(it.at).atZone(zone).format(hhmm)
            sb.append("  $t ${it.type} ${it.place}\n")
        }
        sb.append("  (saved places: ${prefs.places.joinToString { it.name }})")
        return sb.toString()
    }

    private suspend fun weather(context: Context, prefs: Prefs): String {
        val loc = GeofenceManager.lastLocation(context)
        val (lat, lng) = when {
            loc != null -> loc.latitude to loc.longitude
            prefs.places.isNotEmpty() -> prefs.places.first().let { it.lat to it.lng }
            else -> return "unknown (no location yet)"
        }
        return runCatching { Weather.forecast(lat, lng) }.getOrElse { "couldn't fetch (${it.message})" }
    }
}
