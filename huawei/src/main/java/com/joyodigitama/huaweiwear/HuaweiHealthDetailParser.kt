package com.joyodigitama.huaweiwear

import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * The JSON files of the `Health detail data & description` folder: records `{type, startTime, endTime, timeZone, deviceCode, recordId,
 * samplePoints:[{key, startTime, endTime, unit, value}]}`, times in milliseconds (UTC; `timeZone` is only the zone the
 * phone was in, so nothing here shifts a time). `value` is a number as a string (`"82.0"`), an embedded JSON object, or
 * empty (sleep stages). The same reading often comes twice under two numeric `type`s (`7` / `500023` heart rate, `16` /
 * `400021` blood oxygen with another field name, `11` / `500026` stress): a point is recognised by its `key` plus the fields
 * either generation uses, and the collector keeps the first reading per minute (or per metric and second).
 *
 * | key                                                    | goes to                                         |
 * |--------------------------------------------------------|-------------------------------------------------|
 * | DATA_POINT_DYNAMIC_HEARTRATE, DYNAMIC_HEART_RATE (`bpm`) | step_samples.heart_rate (minute row)          |
 * | DATA_POINT_REST_HEARTRATE, DATA_POINT_NEW_REST_HEARTRATE, RESTING_HEART_RATE (`restBpm`, `oldRestBpm`) | step_samples.resting_heart_rate |
 * | BLOOD_OXYGEN_SATURATION (`avgSaturation`, `spo2`)      | step_samples.spo2                               |
 * | PROFESSIONAL_SLEEP_SHALLOW/DEEP/DREAM/WAKE/NOON        | sleep_segments                                  |
 * | STRESS_DATA (`score`), STRESS (`stressScore`)          | health_metrics `emotion`                        |
 * | HEART_RATE_VARIABILITY (`heartRateVariabilityRMSSD`)   | health_metrics `hrv`                            |
 * | OBSTRUCTIVE_SLEEP_APNEA (`osaLevel`)                   | health_metrics `sleep_apnea`                    |
 * | SKIN_TEMPERATURE (`skinTemperature`)                   | health_metrics `skin_temperature`               |
 * | VASCULAR_HEALTH (`pulseWaveVelocity`)                  | health_metrics `arterial_stiffness`             |
 * | ARRHYTHMIA_RESULT (`ppgIrregularHeartbeat`)            | health_metrics `arrhythmia`                     |
 * | WEIGHT_BODYFAT_BROAD and other weight / body keys      | scale_reading (see [HuaweiWeightParser])        |
 * | NUTRITION_RECORD (one meal: `dietaryEnergy`, macros, `meal` slot code) | JoyoRogo diary (queued, see [HuaweiDiaryParser]) |
 * | DRINK_WATER (`drinkWater` ml)                          | JoyoRogo water (queued, glasses per local day)  |
 * | USER_HEIGHT (`userHeight` cm)                          | the profile, when the phone has none            |
 *
 * Everything else (exercise intensity, goals, diet ...) is counted per key as unmapped and left alone.
 */
internal object HuaweiHealthDetailParser {
  const val KEY_SPO2 = "BLOOD_OXYGEN_SATURATION"

  /** Longest sleep stage taken as real; the export repeats and merges spans, and a day-long one is a broken one. */
  private const val MAX_SLEEP_SPAN_SEC = 24 * 3600L

  private val SLEEP_KINDS: Map<String, Int> = mapOf(
    "PROFESSIONAL_SLEEP_SHALLOW" to 0x06,
    "PROFESSIONAL_SLEEP_DEEP" to 0x07,
    "PROFESSIONAL_SLEEP_DREAM" to 0x5656,
    "PROFESSIONAL_SLEEP_WAKE" to 0x5658,
    "PROFESSIONAL_SLEEP_NOON" to 0x5659,
  )

  private val HEART_RATE_KEYS = setOf("DATA_POINT_DYNAMIC_HEARTRATE", "DYNAMIC_HEART_RATE")
  private val RESTING_KEYS = setOf("DATA_POINT_REST_HEARTRATE", "DATA_POINT_NEW_REST_HEARTRATE", "RESTING_HEART_RATE")

  /** `health_metrics` rows: key -> type, the fields that carry the number, the range it is believed in. */
  private class MetricRule(val type: String, val fields: List<String>, val range: ClosedFloatingPointRange<Double>)

  private val METRICS: Map<String, MetricRule> = mapOf(
    "STRESS_DATA" to MetricRule(HuaweiMetricTypes.STRESS, listOf("score", "stress", "value"), 1.0..100.0),
    "STRESS" to MetricRule(HuaweiMetricTypes.STRESS, listOf("stressScore", "score", "value"), 1.0..100.0),
    "HEART_RATE_VARIABILITY" to MetricRule(HuaweiMetricTypes.HRV, listOf("heartRateVariabilityRMSSD", "rmssd", "value"), 1.0..400.0),
    "OBSTRUCTIVE_SLEEP_APNEA" to MetricRule(HuaweiMetricTypes.SLEEP_APNEA, listOf("osaLevel", "value"), 0.0..10.0),
    "SKIN_TEMPERATURE" to MetricRule(HuaweiMetricTypes.SKIN_TEMPERATURE, listOf("skinTemperature", "value"), 20.0..45.0),
    "VASCULAR_HEALTH" to MetricRule(HuaweiMetricTypes.ARTERIAL_STIFFNESS, listOf("pulseWaveVelocity", "value"), 1.0..40.0),
    "ARRHYTHMIA_RESULT" to MetricRule(HuaweiMetricTypes.ARRHYTHMIA, listOf("ppgIrregularHeartbeat", "value"), 0.0..10.0),
  )

  fun parse(record: JSONObject, out: HuaweiImportCollector) {
    val points = record.optJSONArray("samplePoints") ?: return
    val recordStart = HuaweiExportJson.epochMs(record.opt("startTime"))
    val recordEnd = HuaweiExportJson.epochMs(record.opt("endTime"))
    val weights = HuaweiWeightParser.Collected()
    val zone = record.optString("timeZone", "")
    val recordId = record.optString("recordId", "")
    for (i in 0 until points.length()) {
      val point = points.optJSONObject(i) ?: continue
      val key = point.optString("key", "")
      if (key.isEmpty()) continue
      val startMs = HuaweiExportJson.epochMs(point.opt("startTime")) ?: recordStart
      val endMs = HuaweiExportJson.epochMs(point.opt("endTime")) ?: recordEnd ?: startMs
      if (HuaweiDiaryParser.handles(key)) {
        HuaweiDiaryParser.parse(key, point.opt("value"), startMs, zone, if (recordId.isEmpty()) "$startMs" else "$recordId:$i", out)
      } else {
        parsePoint(key, point.opt("value"), startMs, endMs, out, weights)
      }
    }
    weights.emit(out)
  }

  private fun parsePoint(key: String, value: Any?, startMs: Long?, endMs: Long?, out: HuaweiImportCollector, weights: HuaweiWeightParser.Collected) {
    val sleepKind = SLEEP_KINDS[key]
    val weightKey = HuaweiWeightParser.isWeightKey(key)
    val metric = METRICS[key]
    val known = sleepKind != null || weightKey || metric != null || key in HEART_RATE_KEYS || key in RESTING_KEYS || key == KEY_SPO2
    if (!known) {
      out.unmapped(key)
      return
    }
    if (startMs == null) {
      out.rejected(key, "no-time", null)
      return
    }
    val startSec = startMs / 1000
    when {
      sleepKind != null -> {
        val endSec = (endMs ?: startMs) / 1000
        if (endSec <= startSec || endSec - startSec > MAX_SLEEP_SPAN_SEC) {
          out.rejected(key, "bad-span", "${endSec - startSec}s")
        } else {
          out.sleep(ImportedSleep(startSec, endSec, sleepKind, SleepSync.stageForRawKind(sleepKind)))
        }
      }
      weightKey -> if (!weights.add(key, value, startMs)) out.rejected(key, "no-weight", sampleOf(value))
      key in HEART_RATE_KEYS -> number(key, value, 20.0..250.0, out, "bpm", "value") { out.heartRate(startSec, it.roundToInt()) }
      key in RESTING_KEYS -> number(key, value, 20.0..200.0, out, "restBpm", "oldRestBpm", "value") { out.restingHeartRate(startSec, it.roundToInt()) }
      key == KEY_SPO2 -> number(key, value, 50.0..100.0, out, "avgSaturation", "averageSaturation", "spo2", "saturation", "value") { out.spo2(startSec, it.roundToInt()) }
      metric != null -> number(key, value, metric.range, out, *metric.fields.toTypedArray()) {
        out.metric(ImportedMetric(metric.type, startSec, it, """{"${metric.fields[0]}":${trimmed(it)},"source":"huawei-export"}"""))
      }
    }
  }

  private fun trimmed(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

  /** Reads [value] (a plain number or the first of [names] in an embedded object) and hands it over or says why not. */
  private inline fun number(key: String, value: Any?, range: ClosedFloatingPointRange<Double>, out: HuaweiImportCollector, vararg names: String, accept: (Double) -> Unit) {
    val n = embeddedNumber(value, *names)
    if (n == null) {
      out.rejected(key, "no-number", sampleOf(value))
      return
    }
    if (n in range) accept(n) else out.rejected(key, "out-of-range", trimmed(n))
  }

  internal fun sampleOf(value: Any?): String? = value?.toString()?.replace(Regex("\\s+"), "")?.take(40)?.ifEmpty { null }

  /** The plain number in [value], or the first of [names] found in the JSON object [value] carries. */
  private fun embeddedNumber(value: Any?, vararg names: String): Double? {
    HuaweiExportJson.number(value)?.let { return it }
    if (names.isEmpty()) return null
    val obj = HuaweiExportJson.embeddedObject(value) ?: return null
    for (name in names) HuaweiExportJson.number(obj.opt(name))?.let { return it }
    return null
  }
}

/** The `health_metrics.type` ids the Health screen knows (`src/features/health/metrics.ts`). */
internal object HuaweiMetricTypes {
  const val STRESS = "emotion"
  const val HRV = "hrv"
  const val SLEEP_APNEA = "sleep_apnea"
  const val SKIN_TEMPERATURE = "skin_temperature"
  const val ARTERIAL_STIFFNESS = "arterial_stiffness"
  const val ARRHYTHMIA = "arrhythmia"
}

/**
 * The JSON files of the `Sport per minute merged data & description` folder: one record per day, `{recordDay, sportDataUserData:[{startTime,
 * endTime, sportType, sportBasicInfos:[{steps, distance (m), calorie, altitude, floor, duration, count}]}]}`. The entry
 * starts at `startTime`; its n-th info is the n-th minute (a `duration` above one skips ahead). The export repeats the
 * same minutes in several entries, so the collector keeps the largest value per minute rather than adding them up.
 * `calorie` is in small calories (cal, 1/1000 kcal), checked on a real export: a day's minutes sum to 110 000 for 2 829 steps (110 kcal),
 * and the watch's own `step_samples.calories` has the same unit.
 */
internal object HuaweiSportPerMinuteParser {
  private const val MAX_STEPS_PER_MINUTE = 400
  private const val MAX_METRES_PER_MINUTE = 1000

  /** Small calories: 60 kcal in a minute is beyond any human. */
  private const val MAX_CAL_PER_MINUTE = 60_000

  fun parse(record: JSONObject, out: HuaweiImportCollector) {
    val entries = record.optJSONArray("sportDataUserData") ?: return
    for (i in 0 until entries.length()) {
      val entry = entries.optJSONObject(i) ?: continue
      val infos = entry.optJSONArray("sportBasicInfos") ?: continue
      val firstMinuteMs = HuaweiExportJson.epochMs(entry.opt("startTime"))
      if (firstMinuteMs == null) {
        out.rejected("sportDataUserData", "no-time", null)
        continue
      }
      var minuteMs: Long = firstMinuteMs
      for (j in 0 until infos.length()) {
        val info = infos.optJSONObject(j) ?: continue
        val steps = HuaweiExportJson.number(info.opt("steps"))?.roundToInt() ?: 0
        val distance = HuaweiExportJson.number(info.opt("distance"))?.roundToInt() ?: 0
        val calories = HuaweiExportJson.number(info.opt("calorie"))?.roundToInt() ?: 0
        if (steps in 0..MAX_STEPS_PER_MINUTE && distance in 0..MAX_METRES_PER_MINUTE && calories in 0..MAX_CAL_PER_MINUTE) {
          if (steps > 0 || distance > 0 || calories > 0) out.steps(minuteMs / 60_000 * 60, steps, distance, calories)
        } else {
          out.rejected("sportBasicInfos", "out-of-range", "$steps steps/$distance m/$calories cal")
        }
        val minutes = HuaweiExportJson.number(info.opt("duration"))?.roundToInt()?.coerceIn(1, 60) ?: 1
        minuteMs += minutes * 60_000L
      }
    }
  }
}
