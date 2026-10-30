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

    fun coach(apiKey: String, model: String, briefing: String): Advice {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1000)
            .put("system", CoachPrompt.SYSTEM)
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

        return CoachPrompt.parse(reply)
    }
}
