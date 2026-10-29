package io.github.conor2803.coach.context

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Hourly forecast from Open-Meteo (free, no API key). */
object Weather {
    private val http = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()

    fun forecast(lat: Double, lng: Double, hours: Int = 16): String {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&hourly=temperature_2m,apparent_temperature,precipitation_probability,precipitation,wind_speed_10m" +
                "&daily=sunrise,sunset&forecast_days=2&timezone=auto&wind_speed_unit=mph",
            lat, lng,
        )
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("weather ${resp.code}")
            resp.body?.string() ?: throw IOException("weather: empty response")
        }
        val json = JSONObject(body)
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temp = hourly.getJSONArray("temperature_2m")
        val feels = hourly.getJSONArray("apparent_temperature")
        val rainChance = hourly.getJSONArray("precipitation_probability")
        val rain = hourly.getJSONArray("precipitation")
        val wind = hourly.getJSONArray("wind_speed_10m")

        val thisHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        val lines = mutableListOf<String>()
        for (i in 0 until times.length()) {
            val t = LocalDateTime.parse(times.getString(i))
            if (t.isBefore(thisHour)) continue
            if (lines.size >= hours) break
            lines += String.format(
                Locale.UK,
                "%s %.0f°C (feels %.0f), rain %d%% %.1fmm, wind %.0fmph",
                t.toLocalTime(),
                temp.optDouble(i),
                feels.optDouble(i),
                rainChance.optInt(i),
                rain.optDouble(i),
                wind.optDouble(i),
            )
        }

        val daily = json.getJSONObject("daily")
        val sunset = daily.getJSONArray("sunset").optString(0).substringAfter('T')
        val sunrise = daily.getJSONArray("sunrise").optString(1).substringAfter('T')
        return "sunset today $sunset, sunrise tomorrow $sunrise" + lines.joinToString("\n  ", prefix = "\n  ")
    }
}
