package io.github.conor2803.coach.coach

import io.github.conor2803.coach.Advice
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Minimal client for Anthropic's Messages API. */
object ClaudeClient {
    private const val URL = "https://api.anthropic.com/v1/messages"
    private val http = OkHttpClient.Builder().callTimeout(120, TimeUnit.SECONDS).build()

    private val SYSTEM = """
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

    fun coach(apiKey: String, model: String, briefing: String): Advice {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1000)
            .put("system", SYSTEM)
            .put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", briefing)),
            )

        val request = Request.Builder()
            .url(URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val raw = http.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrDefault(text.take(200))
                throw IOException("Claude API ${resp.code}: $msg")
            }
            text
        }

        val content = JSONObject(raw).getJSONArray("content")
        val reply = (0 until content.length())
            .map { content.getJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("") { it.optString("text") }
            .trim()

        return parse(reply)
    }

    /** Pulls the JSON out of the reply; falls back to plain text if Claude didn't follow the format. */
    internal fun parse(reply: String): Advice {
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
