package com.runnow.data

import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.Wellness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime

data class IntervalsAthlete(val name: String, val maxHr: Int?, val restingHr: Int?, val birthday: String?)
data class IntervalsSyncResult(
    val athlete: IntervalsAthlete?,
    val activities: List<Act>,
    val wellness: List<Wellness>,
    val syncedAtMs: Long,
)

class IntervalsIcuClient(private val apiKey: String, private val athleteId: String = "0") {
    init { require(apiKey.isNotBlank()) { "API key kosong" } }

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val url = URL("https://intervals.icu/api/v1/" + path.trimStart('/'))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Basic " + android.util.Base64.encodeToString("API_KEY:$apiKey".toByteArray(), android.util.Base64.NO_WRAP))
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code == 401 || code == 403) throw IllegalStateException("API key Intervals.icu ditolak")
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Intervals.icu HTTP $code")
            body
        } finally { connection.disconnect() }
    }

    suspend fun sync(from: LocalDate, through: LocalDate): IntervalsSyncResult {
        val athlete = runCatching {
            val root = JSONObject(get("athlete/$athleteId"))
            val a = root.optJSONObject("athlete") ?: root
            val name = a.optString("name").ifBlank {
                listOf(a.optString("firstname"), a.optString("lastname")).filter { it.isNotBlank() }.joinToString(" ")
            }
            IntervalsAthlete(name.take(40), a.optIntOrNull("max_hr"), a.optIntOrNull("resting_hr"), a.optString("birthday").takeIf { it.isNotBlank() })
        }.getOrNull()

        val old = from.toString()
        val newest = through.toString()
        val activitiesJson = JSONArray(get("athlete/$athleteId/activities?oldest=$old&newest=$newest"))
        val wellnessJson = runCatching { JSONArray(get("athlete/$athleteId/wellness?oldest=$old&newest=$newest")) }.getOrDefault(JSONArray())

        val activities = buildList {
            for (i in 0 until activitiesJson.length()) {
                val a = activitiesJson.optJSONObject(i) ?: continue
                val startText = a.optString("start_date_local").ifBlank { a.optString("start_date") }
                val start = runCatching { OffsetDateTime.parse(startText).toInstant().toEpochMilli() }
                    .recoverCatching { java.time.Instant.parse(startText).toEpochMilli() }.getOrNull() ?: continue
                val type = a.optString("type").ifBlank { a.optString("name") }.lowercase()
                val kind = when {
                    type.contains("run") || type.contains("lari") || type.contains("trail") -> ActKind.RUN
                    type.contains("walk") || type.contains("hike") || type.contains("jalan") -> ActKind.WALK
                    else -> ActKind.OTHER
                }
                val km = a.optDouble("distance", 0.0) / 1000.0
                val seconds = a.optDouble("moving_time", a.optDouble("elapsed_time", 0.0))
                if (seconds <= 0 || (kind == ActKind.RUN && km <= 0)) continue
                val id = a.opt("id")?.toString()?.takeIf { it != "null" }
                add(Act(
                    start = start, kind = kind, min = seconds / 60.0, km = km,
                    avgHr = a.optDouble("average_heartrate", 0.0),
                    elev = a.optDouble("total_elevation_gain", 0.0),
                    lid = id?.let { "icu_$it" } ?: "t$start",
                    name = a.optString("name").take(50),
                    maxHr = a.optDouble("max_heartrate", a.optDouble("max_hr", 0.0)),
                    icuId = id,
                    load = a.optDouble("icu_training_load", a.optDouble("training_load", a.optDouble("hrss", 0.0))),
                ))
            }
        }

        val wellness = buildList {
            for (i in 0 until wellnessJson.length()) {
                val w = wellnessJson.optJSONObject(i) ?: continue
                val id = w.optString("id").ifBlank { w.optString("date") }.take(10)
                if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(id)) continue
                add(Wellness(
                    id = id,
                    restingHR = w.optDouble("restingHR", w.optDouble("resting_hr", 0.0)),
                    rhrSrc = "intervals",
                    hrv = w.optDouble("hrv", w.optDouble("hrvSDNN", 0.0)),
                    hrvSrc = "intervals",
                    ctl = w.optNullableDouble("ctl"),
                    atl = w.optNullableDouble("atl"),
                    tsb = w.optNullableDouble("tsb"),
                ))
            }
        }
        return IntervalsSyncResult(athlete, activities, wellness, System.currentTimeMillis())
    }
}

private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key).takeIf { it > 0 }
private fun JSONObject.optNullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key).takeIf { it.isFinite() }
