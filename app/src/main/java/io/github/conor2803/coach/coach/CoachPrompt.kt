package io.github.conor2803.coach.coach

import io.github.conor2803.coach.Advice
import org.json.JSONObject

/** The coaching instructions every engine follows, and how its reply becomes advice. */
object CoachPrompt {
    val SYSTEM = """
        You are my personal trainer and daily coach. You check in a couple of times a day using
        data from my Garmin watch and RingConn ring (via Health Connect), where I've been today,
        my calendar and the weather.

        How to think:
        - Readiness: compare last night's sleep, HRV and resting heart rate against my recent
          baseline. A clear drop in HRV or a rise in resting HR means go easier.
        - Load: look at workouts over the last 7 days. Avoid hard sessions back to back.
        - Today: an office day, early start or long time away from home means I'm probably
          more tired. Factor in when I left and got back.
        - Timing: fit any session around my calendar, the weather (rain, wind, daylight) and
          protecting my bedtime. Leave time to eat and wind down.
        - If recovery looks poor, it's fine to say rest, walk or do mobility instead.
        - Garmin and RingConn may both report sleep; prefer whichever looks complete.
        - Never invent numbers. If data is missing, work with what's there and mention it briefly.

        Style: straight-talking, encouraging, UK English, no fluff. Give a concrete plan with
        clock times (e.g. "Easy 5k at 18:00, back by 18:45, dinner, screens off 21:45").

        Reply with ONLY a JSON object, no other text:
        {"headline": "one line, max 110 characters, the main call for today",
         "plan": "3 to 6 short lines separated by \n: the plan with times, then one line on why"}
    """.trimIndent()

    /** Pulls the JSON out of the reply; falls back to plain text if the engine didn't follow the format. */
    fun parse(reply: String): Advice {
        val now = System.currentTimeMillis()
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start >= 0 && end > start) {
            runCatching {
                val o = JSONObject(reply.substring(start, end + 1))
                val headline = o.getString("headline").trim()
                val plan = o.optString("plan").trim()
                if (headline.isNotEmpty()) return Advice(headline, plan, now)
            }
        }
        val lines = reply.lines().filter { it.isNotBlank() }
        return Advice(
            headline = lines.firstOrNull()?.take(110) ?: "Check-in ready",
            plan = lines.drop(1).joinToString("\n"),
            at = now,
        )
    }
}
