package com.joyodigitama.huaweiwear

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * The parts of `Health detail data` that go to JoyoRogo's diary and water log and to the profile, not to a JoyoFit table:
 *
 *  - `NUTRITION_RECORD`: one meal per point, `{"dietaryEnergy":546,"protein":27.2,"carbohydrates":47.8,"fat":27.2,"foodCount":300,
 *    "meal":20,"mealEatTime":1785646800,"mealRecordTime":1785679460}`. `dietaryEnergy` is kcal, `meal` the slot (10 breakfast, 20 lunch,
 *    30 dinner, 11 / 21 / 31 the snack after each; read off the eaten times of a real export), `mealEatTime` seconds. There is
 *    no food name, so the diary row is called [MEAL_NAME]. The diary day is the day of the record's `startTime` in the record's
 *    own `timeZone` (Huawei writes the start of the local day there).
 *  - `DRINK_WATER`: `{"drinkWater":250}` ml per drink; the importer sums a local day and the queue stores glasses of 250 ml.
 *  - `USER_HEIGHT`: `{"userHeight":172}` cm, 0 when never set.
 *
 * `DIET_RECORD` (`{"dietRecord":0}`, 123 in a real export) is an empty marker and stays unmapped.
 */
internal object HuaweiDiaryParser {
  const val KEY_MEAL = "NUTRITION_RECORD"
  const val KEY_WATER = "DRINK_WATER"
  const val KEY_HEIGHT = "USER_HEIGHT"

  /** The diary row's name: the export says what was eaten in kcal and grams, not what it was. */
  const val MEAL_NAME = "Makanan dari Huawei Health"

  private const val MAX_ML_PER_DRINK = 5000

  fun handles(key: String) = key == KEY_MEAL || key == KEY_WATER || key == KEY_HEIGHT

  fun parse(key: String, value: Any?, startMs: Long?, zone: String, id: String, out: HuaweiImportCollector) {
    if (startMs == null) {
      out.rejected(key, "no-time", null)
      return
    }
    val obj = HuaweiExportJson.embeddedObject(value)
    when (key) {
      KEY_HEIGHT -> {
        val cm = HuaweiExportJson.number(obj?.opt("userHeight"))
        when {
          cm == null -> out.rejected(key, "no-number", HuaweiHealthDetailParser.sampleOf(value))
          cm == 0.0 -> Unit // never set
          cm in 100.0..250.0 -> out.profileFact("height", cm, startMs)
          else -> out.rejected(key, "out-of-range", cm.toString())
        }
      }
      KEY_WATER -> {
        val ml = HuaweiExportJson.number(obj?.opt("drinkWater"))?.roundToInt()
        when {
          ml == null -> out.rejected(key, "no-number", HuaweiHealthDetailParser.sampleOf(value))
          ml in 1..MAX_ML_PER_DRINK -> out.water(localDate(startMs, zone), ml, id)
          else -> out.rejected(key, "out-of-range", ml.toString())
        }
      }
      KEY_MEAL -> meal(obj, startMs, zone, id, out)
    }
  }

  private fun meal(obj: org.json.JSONObject?, startMs: Long, zone: String, id: String, out: HuaweiImportCollector) {
    val kcal = HuaweiExportJson.number(obj?.opt("dietaryEnergy"))
    if (kcal == null) {
      out.rejected(KEY_MEAL, "no-number", HuaweiHealthDetailParser.sampleOf(obj))
      return
    }
    if (kcal <= 0.0 || kcal > 5000.0) {
      out.rejected(KEY_MEAL, "out-of-range", kcal.toString())
      return
    }
    val eatenMs = seconds(obj?.opt("mealEatTime"))?.times(1000)
    val recordedMs = seconds(obj?.opt("mealRecordTime"))?.times(1000)
    val slot = slot(HuaweiExportJson.number(obj?.opt("meal"))?.roundToInt(), eatenMs ?: recordedMs ?: startMs, zone)
    out.meal(
      ImportedMeal(
        key = id,
        date = localDate(startMs, zone),
        meal = slot,
        name = MEAL_NAME,
        kcal = Math.round(kcal * 10) / 10.0,
        protein = grams(obj?.opt("protein")),
        carbs = grams(obj?.opt("carbohydrates")),
        fat = grams(obj?.opt("fat")),
        eatenAtMs = eatenMs,
      ),
    )
  }

  private fun grams(value: Any?): Double? = HuaweiExportJson.number(value)?.takeIf { it in 0.0..500.0 }?.let { Math.round(it * 10) / 10.0 }

  /** A unix time in seconds as seconds, a millisecond one converted; null when absent or not a plausible date. */
  private fun seconds(value: Any?): Long? = HuaweiExportJson.epochMs(value)?.div(1000)

  /** JoyoRogo's slot for Huawei's `meal` code; a code not listed goes by the local hour it was eaten. */
  fun slot(code: Int?, atMs: Long, zone: String): String = when (code) {
    10 -> "sarapan"
    20 -> "makan_siang"
    30 -> "makan_malam"
    11, 21, 31 -> "snack"
    else -> when (hourOf(atMs, zone)) {
      in 4..9 -> "sarapan"
      in 10..14 -> "makan_siang"
      in 17..21 -> "makan_malam"
      else -> "snack"
    }
  }

  private fun offsetOf(zone: String): ZoneOffset {
    val text = zone.trim()
    if (text.length == 5 && (text[0] == '+' || text[0] == '-')) {
      val h = text.substring(1, 3).toIntOrNull()
      val m = text.substring(3, 5).toIntOrNull()
      if (h != null && m != null && h <= 18 && m < 60) return ZoneOffset.ofTotalSeconds((h * 3600 + m * 60) * if (text[0] == '-') -1 else 1)
    }
    return ZoneOffset.ofHours(7) // the zone JoyoRogo files days under by default
  }

  /** `YYYY-MM-DD` of [ms] on the clock of [zone] (`+0700`). */
  fun localDate(ms: Long, zone: String): String = Instant.ofEpochMilli(ms).atOffset(offsetOf(zone)).toLocalDate().toString()

  private fun hourOf(ms: Long, zone: String): Int = Instant.ofEpochMilli(ms).atOffset(offsetOf(zone)).hour
}
