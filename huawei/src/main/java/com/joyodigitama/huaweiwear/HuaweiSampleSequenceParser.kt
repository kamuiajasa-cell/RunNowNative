package com.joyodigitama.huaweiwear

import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * The JSON files of the `Sample sequence data & description` folder: records `{type, startTime, endTime, summaryData, detailData,
 * ...}` whose numbers are strings (`"startTime":"1786119060000"`). The `type` is the sequence kind (the `Sequence type No.` sheet):
 *
 * | type   | content                                   | goes to                                                          |
 * |--------|-------------------------------------------|------------------------------------------------------------------|
 * | 700013 | SLEEP_DETAILS: `summaryData` has `sleepScore`, `sleepEfficiency`, bed and wake times | `sleep_scores` |
 * | 700021 | RRI: beat intervals, hex in `detailData`  | not stored: the derived RMSSD (`HEART_RATE_VARIABILITY`) is        |
 * | 700004, 700009 | arrhythmia PPG, ECG waveforms     | not stored: no ECG viewer or storage                               |
 *
 * Every other kind is counted per type as unmapped.
 */
internal object HuaweiSampleSequenceParser {
  private const val SLEEP_DETAILS = 700013

  fun parse(record: JSONObject, out: HuaweiImportCollector) {
    val type = HuaweiExportJson.number(record.opt("type"))?.roundToInt()
    if (type != SLEEP_DETAILS) {
      out.unmapped("SEQUENCE_${type ?: "UNKNOWN"}")
      return
    }
    val startMs = HuaweiExportJson.epochMs(record.opt("startTime"))
    val endMs = HuaweiExportJson.epochMs(record.opt("endTime"))
    if (startMs == null || endMs == null || endMs <= startMs) {
      out.rejected("SLEEP_DETAILS", "bad-span", null)
      return
    }
    val summary = HuaweiExportJson.embeddedObject(record.opt("summaryData"))
    val score = HuaweiExportJson.number(summary?.opt("sleepScore"))?.roundToInt()
    if (score == null) {
      out.rejected("SLEEP_DETAILS", "no-number", HuaweiHealthDetailParser.sampleOf(record.opt("summaryData")))
      return
    }
    // 0 is "not scored" (a nap or too little data): nothing to import, nothing unreadable either.
    if (score == 0) return
    if (score !in 1..100) {
      out.rejected("SLEEP_DETAILS", "out-of-range", score.toString())
      return
    }
    val efficiency = HuaweiExportJson.number(summary?.opt("sleepEfficiency"))?.roundToInt()?.takeIf { it in 0..100 }
    out.sleepScore(ImportedSleepScore(startMs / 1000, endMs / 1000, score, efficiency))
  }
}
