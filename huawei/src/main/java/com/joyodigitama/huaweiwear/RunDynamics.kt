package com.joyodigitama.huaweiwear

import nodomain.freeyourgadget.gadgetbridge.devices.huawei.packets.Workout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Running form the watch sends in the inner part of a workout data sample (Gadgetbridge `WorkoutData.Response.Data`): ground contact
 * time (ms), vertical oscillation (`vo`, wire value / 10), vertical ratio (`vr`, wire value / 10, percent), ground contact balance
 * (`gtb`, wire value / 100, percent) and hang time (ms). The three floats default to 0 and the shorts to -1 when the sample did not
 * carry them; both mean "no reading" here.
 *
 * [of] gives the means for `summary_json.samples` and a per-minute series for the chart (`summary_json.dynamics`). Only fields
 * with a reading appear, so a watch that sends none leaves the summary as it was.
 */
internal object RunDynamics {
  /** One bucket of the chart series: long enough to smooth a stride-by-stride reading, short enough to show a fade. */
  const val BUCKET_SEC = 60

  class Result(val averages: Map<String, Double>, val series: JSONObject?)

  private class Acc {
    var sum = 0.0
    var count = 0
    fun add(v: Double) { sum += v; count++ }
    fun mean(): Double? = if (count > 0) sum / count else null
  }

  private class Field(val key: String, val read: (Workout.WorkoutData.Response.Data) -> Double?) {
    val all = Acc()
    val buckets = HashMap<Int, Acc>()
  }

  private fun newFields() = listOf(
    Field("gct") { d -> d.groundContactTime.toInt().takeIf { it > 0 }?.toDouble() },
    Field("vo") { d -> d.vo.takeIf { it > 0f }?.toDouble() },
    Field("vr") { d -> d.vr.takeIf { it > 0f }?.toDouble() },
    Field("gtb") { d -> d.gtb.takeIf { it > 0f }?.toDouble() },
    Field("hang") { d -> d.hangTime.toInt().takeIf { it > 0 }?.toDouble() },
  )

  private val averageKeys = mapOf(
    "gct" to "avgGroundContactTime",
    "vo" to "avgVerticalOscillation",
    "vr" to "avgVerticalRatio",
    "gtb" to "avgGroundContactBalance",
    "hang" to "avgHangTimeMs",
  )

  fun of(samples: List<Workout.WorkoutData.Response.Data>): Result? {
    val local = newFields()
    var first = Long.MAX_VALUE
    for (d in samples) {
      val ts = d.timestamp.toLong() and 0xFFFFFFFFL
      if (d.timestamp != -1 && ts < first) first = ts
    }
    var any = false
    for (d in samples) {
      val ts = d.timestamp.toLong() and 0xFFFFFFFFL
      val bucket = if (d.timestamp != -1 && first != Long.MAX_VALUE) ((ts - first) / BUCKET_SEC).toInt() else null
      for (f in local) {
        val v = f.read(d) ?: continue
        any = true
        f.all.add(v)
        if (bucket != null) f.buckets.getOrPut(bucket) { Acc() }.add(v)
      }
    }
    if (!any) return null

    val averages = LinkedHashMap<String, Double>()
    for (f in local) f.all.mean()?.let { averages[averageKeys.getValue(f.key)] = it }

    val lastBucket = local.maxOfOrNull { f -> f.buckets.keys.maxOrNull() ?: -1 } ?: -1
    var series: JSONObject? = null
    if (lastBucket >= 1) {
      val out = JSONObject().put("start", first).put("stepSec", BUCKET_SEC)
      for (f in local) {
        if (f.buckets.isEmpty()) continue
        val arr = JSONArray()
        for (i in 0..lastBucket) {
          val m = f.buckets[i]?.mean()
          if (m == null) arr.put(JSONObject.NULL) else arr.put(Math.round(m * 100) / 100.0)
        }
        out.put(f.key, arr)
      }
      series = out
    }
    return Result(averages, series)
  }

  /** The summary with the running form replaced; [changed] is false when the watch's readings give what the summary already had. */
  class Merged(val json: String, val changed: Boolean)

  /**
   * Puts [result] into a stored `summary_json` and leaves every other field as it was: the five means under `samples` are replaced
   * (an older sync's contact-time mean, which also counted zero readings, goes with them) and `dynamics` is replaced or dropped
   * as the new series says. Null when [summaryJson] is not a JSON object. Running it again with the same readings changes nothing.
   */
  fun merge(summaryJson: String, result: Result): Merged? {
    val summary = try {
      JSONObject(summaryJson)
    } catch (e: org.json.JSONException) {
      return null
    }
    val samples = summary.optJSONObject("samples") ?: JSONObject()
    val before = form(samples, summary)
    for (key in averageKeys.values) samples.remove(key)
    for ((key, value) in result.averages) samples.put(key, value)
    summary.put("samples", samples)
    if (result.series != null) summary.put("dynamics", result.series) else summary.remove("dynamics")
    return Merged(summary.toString(), before != form(samples, summary))
  }

  /** The running-form part of a summary as text with sorted keys, to tell a real change from the same readings again. */
  private fun form(samples: JSONObject, summary: JSONObject): String {
    val out = StringBuilder()
    for (key in averageKeys.values.sorted()) out.append(key).append('=').append(canonical(samples.opt(key))).append(';')
    out.append(canonical(summary.opt("dynamics")))
    return out.toString()
  }

  private fun canonical(value: Any?): String = when (value) {
    is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { "$it:${canonical(value.opt(it))}" }
    is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
    JSONObject.NULL, null -> "null"
    is Number -> value.toDouble().toString()
    else -> value.toString()
  }
}
