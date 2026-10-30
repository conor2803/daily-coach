package io.github.conor2803.coach

import android.content.Context
import io.github.conor2803.coach.coach.Engine
import io.github.conor2803.coach.coach.OnDeviceModel
import org.json.JSONArray
import org.json.JSONObject

/** A saved place the phone watches for arrivals and departures. */
data class Place(val name: String, val lat: Double, val lng: Double, val radiusM: Float = 150f)

data class Advice(val headline: String, val plan: String, val at: Long)

/**
 * All app settings. Stored in app-private SharedPreferences, which only this app can read.
 * The API key never leaves the phone except in the request header to api.anthropic.com.
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("coach", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var engine: Engine
        get() = Engine.fromStored(sp.getString("engine", null))
        set(v) = sp.edit().putString("engine", v.name).apply()

    /** The Claude model; the on-device engine always runs [OnDeviceModel]. */
    var model: String
        get() = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(v) = sp.edit().putString("model", v.trim().ifBlank { DEFAULT_MODEL }).apply()

    var modelDownloadId: Long
        get() = sp.getLong("model_download_id", OnDeviceModel.NO_DOWNLOAD)
        set(v) = sp.edit().putLong("model_download_id", v).apply()

    /** Free text the coach reads every time: goals, bedtime, injuries, preferences. */
    var goals: String
        get() = sp.getString("goals", DEFAULT_GOALS) ?: DEFAULT_GOALS
        set(v) = sp.edit().putString("goals", v).apply()

    /** Comma-separated 24h times, e.g. "07:30,16:30". */
    var checkInTimes: String
        get() = sp.getString("check_in_times", DEFAULT_TIMES) ?: DEFAULT_TIMES
        set(v) = sp.edit().putString("check_in_times", v).apply()

    var places: List<Place>
        get() {
            val raw = sp.getString("places", null) ?: return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    Place(
                        name = o.getString("name"),
                        lat = o.getDouble("lat"),
                        lng = o.getDouble("lng"),
                        radiusM = o.optDouble("radius", 150.0).toFloat(),
                    )
                }
            }.getOrDefault(emptyList())
        }
        set(list) {
            val arr = JSONArray()
            list.forEach {
                arr.put(
                    JSONObject()
                        .put("name", it.name)
                        .put("lat", it.lat)
                        .put("lng", it.lng)
                        .put("radius", it.radiusM.toDouble())
                )
            }
            sp.edit().putString("places", arr.toString()).apply()
        }

    var lastAdvice: Advice?
        get() {
            val headline = sp.getString("advice_headline", null) ?: return null
            return Advice(headline, sp.getString("advice_plan", "") ?: "", sp.getLong("advice_at", 0L))
        }
        set(a) {
            if (a == null) {
                sp.edit().remove("advice_headline").remove("advice_plan").remove("advice_at").apply()
            } else {
                sp.edit()
                    .putString("advice_headline", a.headline)
                    .putString("advice_plan", a.plan)
                    .putLong("advice_at", a.at)
                    .apply()
            }
        }

    var lastError: String
        get() = sp.getString("last_error", "") ?: ""
        set(v) = sp.edit().putString("last_error", v).apply()

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
        const val DEFAULT_TIMES = "07:30,16:30"
        const val DEFAULT_GOALS =
            "Run 3-4 times a week, mostly easy, one longer run at the weekend. " +
                "Also into mountain biking and hiking. In bed by 22:30 on work nights. " +
                "Be straight with me, keep it short."
    }
}
