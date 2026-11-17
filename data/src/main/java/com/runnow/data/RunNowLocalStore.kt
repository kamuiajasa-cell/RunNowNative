package com.runnow.data

import android.content.Context
import com.runnow.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Penyimpanan lokal versi ringan; data di-upsert berdasarkan ID, bukan ditambahkan tiap sinkron. */
class RunNowLocalStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("runnow_native_data_v1", Context.MODE_PRIVATE)

    @Synchronized fun readActivities(): List<Act> = runCatching {
        val a = JSONArray(prefs.getString("activities", "[]"))
        buildList {
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val kind = runCatching { ActKind.valueOf(o.optString("kind")) }.getOrDefault(ActKind.OTHER)
                add(Act(
                    start = o.optLong("start"), kind = kind, min = o.optDouble("min"), km = o.optDouble("km"),
                    avgHr = o.optDouble("avgHr"), elev = o.optDouble("elev"), lid = o.optString("lid"),
                    name = o.optString("name"), maxHr = o.optDouble("maxHr"),
                    icuId = o.optString("icuId").takeIf { it.isNotBlank() },
                    load = o.optDouble("load"),
                ))
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized fun readWellness(): List<Wellness> = runCatching {
        val a = JSONArray(prefs.getString("wellness", "[]"))
        buildList {
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                add(Wellness(
                    id = o.optString("id"), restingHR = o.optDouble("restingHR"), rhrSrc = o.optString("rhrSrc"),
                    hrv = o.optDouble("hrv"), hrvSrc = o.optString("hrvSrc"), hrvSpot = o.optBoolean("hrvSpot"),
                    sleepSecs = o.optDouble("sleepSecs"), sleepScore = o.optDouble("sleepScore"),
                    steps = o.optDouble("steps"), spO2 = o.optDouble("spO2"), stress = o.optDouble("stress"),
                    stressMax = o.optDouble("stressMax"), stressNight = o.optDouble("stressNight"),
                    ctl = o.optNullable("ctl"), atl = o.optNullable("atl"), tsb = o.optNullable("tsb"),
                ))
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized fun readStreams(): Map<String, StreamSummary> = runCatching {
        val o = JSONObject(prefs.getString("streams", "{}"))
        buildMap {
            val keys = o.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val v = o.optJSONObject(key) ?: continue
                val f = v.optJSONObject("fast")
                val fade = v.optJSONObject("fade")
                val dec = v.optJSONObject("dec")
                put(key, StreamSummary(
                    fast = f?.let { StreamFast(it.optDouble("p12"), it.optDouble("med"), it.optInt("pct")) },
                    fade = fade?.let { StreamFade(it.optInt("pct")) },
                    dec = dec?.let { StreamDec(it.optInt("pct")) },
                    tiz = v.optJSONArray("tiz")?.let { arr -> (0 until arr.length()).map { arr.optInt(it) } },
                    none = v.optBoolean("none"),
                ))
            }
        }
    }.getOrDefault(emptyMap())

    @Synchronized fun write(
        activities: List<Act>,
        wellness: List<Wellness>,
        streams: Map<String, StreamSummary>,
        intervalsSyncedAtMs: Long? = null,
        watchSyncedAtMs: Long? = null,
    ) {
        val actsJson = JSONArray().apply {
            activities.takeLast(500).forEach { a ->
                put(JSONObject().apply {
                    put("start", a.start); put("kind", a.kind.name); put("min", a.min); put("km", a.km)
                    put("avgHr", a.avgHr); put("elev", a.elev); put("lid", a.lid); put("name", a.name)
                    put("maxHr", a.maxHr); put("icuId", a.icuId); put("load", a.load)
                })
            }
        }
        val wellnessJson = JSONArray().apply {
            wellness.takeLast(400).forEach { w ->
                put(JSONObject().apply {
                    put("id", w.id); put("restingHR", w.restingHR); put("rhrSrc", w.rhrSrc)
                    put("hrv", w.hrv); put("hrvSrc", w.hrvSrc); put("hrvSpot", w.hrvSpot)
                    put("sleepSecs", w.sleepSecs); put("sleepScore", w.sleepScore); put("steps", w.steps)
                    put("spO2", w.spO2); put("stress", w.stress); put("stressMax", w.stressMax); put("stressNight", w.stressNight)
                    put("ctl", w.ctl); put("atl", w.atl); put("tsb", w.tsb)
                })
            }
        }
        val streamsJson = JSONObject().apply {
            streams.entries.toList().takeLast(60).forEach { (key, value) ->
                put(key, JSONObject().apply {
                    value.fast?.let { put("fast", JSONObject().put("p12", it.p12).put("med", it.med).put("pct", it.pct)) }
                    value.fade?.let { put("fade", JSONObject().put("pct", it.pct)) }
                    value.dec?.let { put("dec", JSONObject().put("pct", it.pct)) }
                    value.tiz?.let { values -> put("tiz", JSONArray(values)) }
                    put("none", value.none)
                })
            }
        }
        prefs.edit().putString("activities", actsJson.toString())
            .putString("wellness", wellnessJson.toString())
            .putString("streams", streamsJson.toString())
            .apply {
                if (intervalsSyncedAtMs != null) putLong("intervals_synced_at", intervalsSyncedAtMs)
                if (watchSyncedAtMs != null) putLong("watch_synced_at", watchSyncedAtMs)
            }.apply()
    }

    fun intervalsSyncedAtMs(): Long = prefs.getLong("intervals_synced_at", 0L)
    fun watchSyncedAtMs(): Long = prefs.getLong("watch_synced_at", 0L)
    fun clear() = prefs.edit().clear().apply()
}

private fun JSONObject.optNullable(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
