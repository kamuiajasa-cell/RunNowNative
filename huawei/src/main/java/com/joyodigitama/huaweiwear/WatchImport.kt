package com.joyodigitama.huaweiwear

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mengubah database jam (hasil sinkron, dibaca lewat [WatchData]) menjadi isi untuk `window.nativeImport` di index.html:
 * `{ wellness: [...], acts: [...], workouts: [...] }`. Bentuknya mengikuti yang dibaca index.html:
 *  - wellness: `{id: "yyyy-MM-dd", restingHR, hrv, sleepSecs (detik), sleepScore, steps, spO2}`; bidang kosong tidak dikirim,
 *    jadi tidak menimpa nilai dari sumber lain (mis. Intervals.icu).
 *  - acts: baris jurnal `[mulaiMs, jenis, menit, km, hrRata, hrMaks, null, kadens, beban, id]` (sama dengan icuActivity di index.html).
 *  - workouts: latihan rinci dengan `pts` = `[detikSejakMulai, hr, kecepatan m/s, tinggi, kadens, lat, lon]` (sama dengan icuWk).
 * Blocking: panggil dari thread IO.
 */
internal object WatchImport {
  private const val MAX_WORKOUTS = 20
  private const val WITH_POINTS = 15
  private const val MAX_POINTS = 300
  private val NULL: Any = JSONObject.NULL

  class Result(val json: JSONObject, val wellnessDays: Int, val acts: Int, val workouts: Int, val withRoute: Int)

  private fun dateOf(sec: Long): String = Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()).toLocalDate().toString()

  private fun round(v: Double, digits: Int): Double {
    val m = Math.pow(10.0, digits.toDouble())
    return Math.round(v * m) / m
  }

  /** Jenis menurut index.html: run, walk, ride, weight, other. */
  fun pageKind(sportType: Int): String {
    val k = WorkoutSportTypes.kind(sportType)
    return when {
      k.contains("RUN") || k.contains("TREADMILL") -> "run"
      k == "AIR_WALKER" -> "other"
      k.contains("WALK") || k.contains("HIK") -> "walk"
      k.contains("CYCL") || k == "SPINNING" -> "ride"
      k == "STRENGTH_TRAINING" -> "weight"
      else -> "other"
    }
  }

  /** Null bila belum ada database jam. */
  fun build(context: Context, days: Int = 42): Result? {
    val d = WatchData(context.applicationContext)
    if (!d.available()) return null
    val wellness = wellness(d, days)
    val acts = JSONArray()
    val workouts = JSONArray()
    var withRoute = 0
    val list = d.workouts(MAX_WORKOUTS).filter { it.complete && it.durationSec > 0 }
    list.forEachIndexed { index, w ->
      val kind = pageKind(w.sportType)
      val rows = series(d.workoutPoints(w.id))
      val cads = rows.mapNotNull { row -> row.cad?.takeIf { c -> c > 0 } }
      val cadence = if (cads.isEmpty()) 0 else Math.round(cads.average()).toInt()
      val id = "hw" + w.startSec
      // Baris jurnal; sama dengan icuActivity: lari tanpa jarak dibuang.
      if (kind != "run" || w.distanceM > 0) {
        val a = JSONArray()
        a.put(w.startSec * 1000L).put(kind).put(w.durationSec / 60.0).put(w.distanceM / 1000.0)
        a.put(w.avgHr ?: 0).put(w.maxHr ?: 0).put(NULL).put(if (kind == "run") cadence else 0).put(0).put(id)
        acts.put(a)
      }
      val o = JSONObject()
      o.put("start", w.startSec * 1000L).put("kind", kind).put("durSec", w.durationSec).put("distM", w.distanceM)
      o.put("kcal", w.calories).put("steps", w.steps).put("avgHr", w.avgHr ?: 0).put("maxHr", w.maxHr ?: 0)
      val hrs = rows.mapNotNull { row -> row.hr?.takeIf { h -> h > 40 } }
      o.put("minHr", if (hrs.isEmpty()) 0 else hrs.min())
      o.put("cad", cadence)
      var up = 0.0
      var down = 0.0
      for (i in 1 until rows.size) {
        val p = rows[i - 1].alt
        val c = rows[i].alt
        if (p != null && c != null) {
          val delta = c - p
          if (delta > 0 && delta < 50) up += delta else if (delta < 0 && delta > -50) down -= delta
        }
      }
      o.put("elevUp", Math.round(up).toInt()).put("elevDown", Math.round(down).toInt())
      o.put("load", 0).put("name", w.sportName).put("iid", id)
      if (index < WITH_POINTS && rows.size >= 3) {
        o.put("pts", ptsJson(w.startSec, rows))
        if (rows.any { row -> row.lat != null && row.lon != null }) withRoute++
      } else {
        o.put("pts", JSONArray())
      }
      workouts.put(o)
    }
    val json = JSONObject().put("wellness", wellness).put("acts", acts).put("workouts", workouts)
    return Result(json, wellness.length(), acts.length(), workouts.length(), withRoute)
  }

  private fun wellness(d: WatchData, days: Int): JSONArray {
    val rows = java.util.TreeMap<String, JSONObject>()
    fun row(date: String): JSONObject = rows.getOrPut(date) { JSONObject().put("id", date) }
    for (day in d.days(days)) {
      if (day.date.isEmpty()) continue
      day.restingHr?.let { row(day.date).put("restingHR", it) }
      if (day.steps > 0) row(day.date).put("steps", day.steps)
      day.spo2Avg?.let { row(day.date).put("spO2", it) }
    }
    // Tidur: tanggal = hari bangun; potongan di hari yang sama dijumlahkan (sama dengan WatchBridge di PolarRun).
    val sleep = HashMap<String, Long>()
    val best = HashMap<String, Pair<Long, Int>>()
    for (n in d.sleepNights(days)) {
      if (n.asleepSec <= 0) continue
      val k = dateOf(n.endSec)
      sleep[k] = (sleep[k] ?: 0L) + n.asleepSec
      val sc = n.score
      if (sc != null && n.asleepSec > (best[k]?.first ?: 0L)) best[k] = Pair(n.asleepSec, sc)
    }
    sleep.forEach { (k, v) -> row(k).put("sleepSecs", v) }
    best.forEach { (k, v) -> row(k).put("sleepScore", v.second) }
    // HRV: rata-rata RMSSD per hari bangun; utamakan hasil malam.
    val all = d.hrv(days)
    val pool = all.filter { it.source == "night" }.ifEmpty { all }
    pool.groupBy { dateOf(it.endSec) }.forEach { (k, v) -> row(k).put("hrv", round(v.map { it.rmssd }.average(), 1)) }
    val out = JSONArray()
    rows.values.forEach { out.put(it) }
    return out
  }

  private class Raw(val ts: Long) {
    var hr: Int? = null
    var speed: Double? = null
    var alt: Double? = null
    var cad: Int? = null
    var lat: Double? = null
    var lon: Double? = null
  }

  /** Titik dengan waktu sama digabung; HR, kecepatan, tinggi, dan kadens terakhir yang diketahui dibawa ke titik berikutnya (jam menyimpan tiap bidang di baris sendiri). */
  private fun series(points: List<WatchWorkoutPoint>): List<Raw> {
    val by = java.util.TreeMap<Long, Raw>()
    for (p in points) {
      val s = by.getOrPut(p.tsSec) { Raw(p.tsSec) }
      val hr = p.hr
      if (hr != null && hr > 0) s.hr = hr
      val sp = p.speed
      if (sp != null && sp >= 0) s.speed = sp
      val alt = p.alt
      if (alt != null) s.alt = alt
      val cad = p.cadence
      if (cad != null && cad > 0) s.cad = cad
      val lat = p.lat
      val lon = p.lon
      if (lat != null && lon != null) {
        s.lat = lat
        s.lon = lon
      }
    }
    val rows = by.values.toList()
    var hr: Int? = null
    var sp: Double? = null
    var alt: Double? = null
    var cad: Int? = null
    for (s in rows) {
      if (s.hr != null) hr = s.hr else s.hr = hr
      if (s.speed != null) sp = s.speed else s.speed = sp
      if (s.alt != null) alt = s.alt else s.alt = alt
      if (s.cad != null) cad = s.cad else s.cad = cad
    }
    return rows
  }

  private fun ptsJson(start: Long, rows: List<Raw>): JSONArray {
    val step = Math.ceil(rows.size / MAX_POINTS.toDouble()).toInt().coerceAtLeast(1)
    val out = JSONArray()
    var i = 0
    while (i < rows.size) {
      val s = rows[i]
      val p = JSONArray()
      p.put(maxOf(0L, s.ts - start)).put(s.hr ?: 0).put(round(s.speed ?: 0.0, 2))
      p.put(s.alt?.let { round(it, 1) } ?: NULL).put(s.cad ?: 0)
      p.put(s.lat?.let { round(it, 6) } ?: NULL).put(s.lon?.let { round(it, 6) } ?: NULL)
      out.put(p)
      i += step
    }
    return out
  }
}
