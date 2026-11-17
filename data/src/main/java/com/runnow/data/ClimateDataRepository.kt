package com.runnow.data

import android.content.Context
import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.ClimateConditions
import com.runnow.domain.ClimateContext
import com.runnow.domain.ClimateRun
import com.runnow.domain.Snapshot
import com.runnow.domain.detectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class ActivityGpsPoint(val secondsFromStart: Double, val latitude: Double, val longitude: Double)
data class ClimateMatchReport(val matched: Int, val unavailable: Int, val usedLastKnownLocation: Int)

/**
 * Menjodohkan cuaca historis dengan segmen waktu/rute aktivitas. GPS diringkas maksimal 5 titik;
 * bila GPS tidak ada, lokasi tersimpan boleh dipakai dengan confidence lebih rendah.
 */
class ClimateDataRepository(context: Context, private val weather: OpenMeteoProvider = OpenMeteoProvider(context)) {
    private val prefs = context.applicationContext.getSharedPreferences("runnow_climate_context_v1", Context.MODE_PRIVATE)

    fun savedLocation(): ClimateLocation? = weather.savedLocation()
    fun saveLocation(location: ClimateLocation) = weather.saveLocation(location)

    fun contexts(): Map<Long, ClimateContext> = runCatching {
        val root = JSONObject(prefs.getString("contexts", "{}"))
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val o = root.optJSONObject(key) ?: continue
                val c = o.optJSONObject("conditions") ?: continue
                put(key.toLongOrNull() ?: continue, ClimateContext(
                    startMs = key.toLong(),
                    conditions = ClimateConditions(
                        c.num("t"), c.num("rh"), c.num("at"), c.num("w"),
                        c.optDouble("p"), c.optDouble("sr"), c.optInt("code"), c.optInt("n"),
                        c.num("amin"), c.num("amax"),
                    ),
                    source = o.optString("source", "Open-Meteo"),
                    matchedWithGps = o.optBoolean("gps"),
                    gpsPointCount = o.optInt("points"),
                    note = o.optString("note").takeIf { it.isNotBlank() },
                ))
            }
        }
    }.getOrDefault(emptyMap())

    private fun saveContext(context: ClimateContext) {
        val all = contexts().toMutableMap()
        all[context.startMs] = context
        val root = JSONObject()
        all.toSortedMap().entries.toList().takeLast(400).forEach { (start, value) ->
            val c = value.conditions
            root.put(start.toString(), JSONObject()
                .put("source", value.source).put("gps", value.matchedWithGps)
                .put("points", value.gpsPointCount).put("note", value.note)
                .put("conditions", JSONObject()
                    .put("t", c.temperatureC).put("rh", c.humidityPct).put("at", c.apparentTemperatureC)
                    .put("w", c.windKmh).put("p", c.precipitationMm).put("sr", c.solarRadiationWm2)
                    .put("code", c.weatherCode).put("n", c.samples)
                    .put("amin", c.apparentMinC).put("amax", c.apparentMaxC)))
        }
        prefs.edit().putString("contexts", root.toString()).apply()
    }

    suspend fun matchRecentRuns(
        snapshot: Snapshot,
        gps: suspend (Act) -> List<ActivityGpsPoint>?,
        limit: Int = 10,
    ): ClimateMatchReport = withContext(Dispatchers.IO) {
        val location = weather.savedLocation()
        val known = contexts().toMutableMap()
        val todo = snapshot.acts.filter {
            it.kind == ActKind.RUN && it.km >= 1.5 && snapshot.nowMs - it.start > 3_600_000L &&
                known[it.start] == null
        }.sortedByDescending { it.start }.take(limit)
        var matched = 0
        var unavailable = 0
        var fallback = 0
        for (activity in todo) {
            val points = runCatching { gps(activity) }.getOrNull()
                ?.filter { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 && (it.latitude != 0.0 || it.longitude != 0.0) }
                ?.sortedBy { it.secondsFromStart }
                ?.let { list ->
                    if (list.size <= 5) list else (0..4).map { list[(it * (list.size - 1) / 4.0).roundToInt()] }
                }
            val route = points?.takeIf { it.size >= 2 }
            val weatherPoints = if (route != null) route else location?.let { listOf(ActivityGpsPoint(0.0, it.latitude, it.longitude)) }
            if (weatherPoints.isNullOrEmpty()) { unavailable++; continue }
            val usedFallback = route == null
            val conditions = mutableListOf<ClimateConditions>()
            var failed = false
            for (i in weatherPoints.indices) {
                val p = weatherPoints[i]
                val startMs = activity.start + (p.secondsFromStart * 1000).toLong()
                val endMs = if (usedFallback) activity.start + (activity.min * 60_000).toLong()
                    else if (i + 1 < weatherPoints.size) activity.start + (weatherPoints[i + 1].secondsFromStart * 1000).toLong()
                    else activity.start + (activity.min * 60_000).toLong()
                val date = Instant.ofEpochMilli(startMs).atZone(snapshot.zone).toLocalDate()
                val loc = ClimateLocation(p.latitude, p.longitude)
                val bundle = runCatching {
                    if (snapshot.nowMs - startMs < 7L * 86_400_000L) weather.forecast(loc, pastDays = 7, forecastDays = 3)
                    else weather.archive(loc, date)
                }.getOrNull()
                if (bundle == null) { failed = true; break }
                val from = startMs - 3_600_000L
                val samples = bundle.hourly.filter { it.first > from && it.first <= endMs.coerceAtLeast(startMs + 1) }.map { it.second }
                val usable = samples.ifEmpty {
                    bundle.hourly.minByOrNull { kotlin.math.abs(it.first - startMs) }?.let { listOf(it.second) }.orEmpty()
                }
                if (usable.isEmpty()) { failed = true; break }
                fun avg(selector: (ClimateConditions) -> Double?): Double? {
                    val v = usable.mapNotNull(selector)
                    return v.takeIf { it.isNotEmpty() }?.average()
                }
                conditions += ClimateConditions(
                    temperatureC = avg { it.temperatureC },
                    humidityPct = avg { it.humidityPct },
                    apparentTemperatureC = avg { it.apparentTemperatureC },
                    windKmh = avg { it.windKmh },
                    precipitationMm = usable.maxOfOrNull { it.precipitationMm } ?: 0.0,
                    solarRadiationWm2 = avg { it.solarRadiationWm2 } ?: 0.0,
                    weatherCode = usable.maxOfOrNull { it.weatherCode } ?: 0,
                    samples = usable.size,
                    apparentMinC = usable.mapNotNull { it.apparentTemperatureC }.minOrNull(),
                    apparentMaxC = usable.mapNotNull { it.apparentTemperatureC }.maxOrNull(),
                )
            }
            if (failed || conditions.isEmpty()) { unavailable++; continue }
            fun avg(selector: (ClimateConditions) -> Double?): Double? {
                val v = conditions.mapNotNull(selector)
                return v.takeIf { it.isNotEmpty() }?.average()
            }
            val merged = ClimateConditions(
                temperatureC = avg { it.temperatureC }, humidityPct = avg { it.humidityPct },
                apparentTemperatureC = avg { it.apparentTemperatureC }, windKmh = avg { it.windKmh },
                precipitationMm = conditions.maxOf { it.precipitationMm },
                solarRadiationWm2 = avg { it.solarRadiationWm2 } ?: 0.0,
                weatherCode = conditions.maxOf { it.weatherCode }, samples = conditions.sumOf { it.samples },
                apparentMinC = conditions.mapNotNull { it.apparentMinC }.minOrNull(),
                apparentMaxC = conditions.mapNotNull { it.apparentMaxC }.maxOrNull(),
            )
            val context = ClimateContext(
                startMs = activity.start, conditions = merged, matchedWithGps = !usedFallback,
                gpsPointCount = route?.size ?: 1,
                note = if (usedFallback) "lokasi terakhir tersimpan; keyakinan lebih rendah" else "titik GPS aktivitas",
            )
            saveContext(context)
            known[activity.start] = context
            matched++
            if (usedFallback) fallback++
        }
        ClimateMatchReport(matched, unavailable, fallback)
    }

    suspend fun currentForecast(): WeatherBundle? = withContext(Dispatchers.IO) {
        val location = weather.savedLocation() ?: return@withContext null
        runCatching { weather.forecast(location, pastDays = 1, forecastDays = 3) }.getOrNull()
    }
}

private fun JSONObject.num(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
