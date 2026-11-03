package com.joyodigitama.huaweiwear

import org.json.JSONObject

/**
 * Weigh-ins in the `Health detail data` files. Huawei writes them under keys such as `WEIGHT_BODYFAT_BROAD` with the
 * numbers in an embedded JSON object (`{"bodyWeight":70.2,"bodyFatRate":21.3,...}`), but the exact key and field names
 * are not documented and differ by scale and firmware. So a point counts as a weigh-in when its key says weight, body fat,
 * body composition or BMI, and every numeric field is classified by what its name says ([classify]). A value that cannot
 * be what its name says (a body fat of 400 %) is dropped; a weigh-in without a plausible weight is not stored.
 * The result is a `scale_reading` row whose `reported` JSON is a `ReportedBody` (`src/features/scale/types.ts`).
 */
internal object HuaweiWeightParser {
  private val KEY_PARTS = listOf("weight", "bodyfat", "bodycomposition", "bmi")
  private val KEY_EXCLUDED = listOf("goal", "target", "plan", "remind")

  fun isWeightKey(key: String): Boolean {
    val n = normalize(key)
    return KEY_PARTS.any { it in n } && KEY_EXCLUDED.none { it in n }
  }

  private fun normalize(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

  /**
   * The [ReportedBody] field (or `weight` / `impedance`) a numeric field called [name] holds, null when it is none of them.
   * The names are those of Huawei's `BroadBodyFat` description: `bodyWeight`, `bodyFat` (kilograms of fat: not used),
   * `bodyFatRate`, `impedance`, `bmi`, `muscleMass`, `basalMetabolism`, `moisture` (kilograms of water: not used),
   * `moistureRate`, `visceralFatLevel`, `boneSalt`, `proteinRate`, `bodyAge`; per-limb, per-frequency and skeletal values are left out.
   */
  fun classify(name: String): String? {
    val n = normalize(name)
    // The profile the scale weighed with: height cm, gender 1 male / 0 female, age in years. Not body values; they go to the profile.
    if (n == "height" || n == "gender" || n == "age") return n
    if (listOf("skeletal", "arm", "leg", "trunk", "hf", "balance", "ratio", "shape", "score", "healthy").any { it in n } && !("proteinratio" in n)) return null
    return when {
      n == "impedance" || n == "resistance" -> "impedance"
      "bmi" in n -> "bmi"
      "visceral" in n -> "visceralFat"
      "bmr" in n || "basalmetab" in n -> "bmrKcal"
      "bodyage" in n -> "bodyAge"
      "protein" in n -> "proteinPct"
      "bone" in n -> "boneKg"
      "water" in n || "moisture" in n -> if (isRate(n)) "waterPct" else null
      "fatfree" in n || "leanbody" in n || "leanmass" in n || "bodyfatfree" in n -> "leanKg"
      "muscle" in n -> if (isRate(n)) "musclePct" else "muscleKg"
      "fat" in n -> if (isRate(n)) "bodyFatPct" else null
      "weight" in n -> if ("unit" in n || "division" in n) null else "weight"
      else -> null
    }
  }

  private fun isRate(normalised: String) = listOf("rate", "percent", "pct").any { it in normalised }

  private val PCT_FIELDS = setOf("bodyFatPct", "waterPct", "musclePct", "proteinPct")

  private val RANGES: Map<String, ClosedFloatingPointRange<Double>> = mapOf(
    "bodyFatPct" to 1.0..75.0,
    "waterPct" to 20.0..90.0,
    "musclePct" to 10.0..90.0,
    "muscleKg" to 5.0..120.0,
    "boneKg" to 0.5..10.0,
    "bmi" to 8.0..80.0,
    "visceralFat" to 1.0..59.0,
    "bmrKcal" to 500.0..5000.0,
    "proteinPct" to 5.0..40.0,
    "leanKg" to 10.0..200.0,
    "bodyAge" to 5.0..99.0,
  )

  /** The weigh-ins of one record: fields of points within the same minute belong to one weighing. */
  class Collected {
    private val byMinute = LinkedHashMap<Long, Pair<Long, HashMap<String, Double>>>()

    /** False when the point carried no numeric field this parser knows. */
    fun add(key: String, value: Any?, startMs: Long): Boolean {
      val fields = HashMap<String, Double>()
      val obj = HuaweiExportJson.embeddedObject(value)
      if (obj != null) {
        for (name in obj.keys()) {
          val field = classify(name) ?: continue
          HuaweiExportJson.number(obj.opt(name))?.let { fields[field] = it }
        }
      } else {
        val field = classify(key)
        val number = HuaweiExportJson.number(value)
        if (field != null && number != null) fields[field] = number
      }
      if (fields.isEmpty()) return false
      val slot = byMinute.getOrPut(startMs / 60_000) { startMs to HashMap() }
      for ((field, number) in fields) slot.second.putIfAbsent(field, number)
      return true
    }

    fun emit(out: HuaweiImportCollector) {
      for ((ts, fields) in byMinute.values) {
        val weight = weightKg(fields["weight"])
        if (weight == null) {
          out.rejected("weight", "no-weight", fields["weight"]?.toString())
          continue
        }
        val reported = JSONObject()
        for ((field, raw) in fields) {
          val range = RANGES[field] ?: continue
          val value = if (field in PCT_FIELDS && raw > 0.0 && raw <= 1.0) raw * 100.0 else raw
          if (value in range) reported.put(field, Math.round(value * 100.0) / 100.0)
        }
        val impedance = fields["impedance"]?.takeIf { it in 50.0..5000.0 }
        out.weighIn(ImportedWeighIn(ts, weight, impedance, if (reported.length() == 0) null else reported.toString()))
        fields["height"]?.takeIf { it in 100.0..250.0 }?.let { out.profileFact("height", it, ts) }
        fields["gender"]?.takeIf { it == 0.0 || it == 1.0 }?.let { out.profileFact("male", it, ts) }
        fields["age"]?.takeIf { it in 10.0..100.0 }?.let { out.profileFact("age", it, ts) }
        out.profileFact("weight", weight, ts)
      }
    }

    /** Kilograms: a value of 10..500 is taken as kg, one of 10 000..500 000 as grams. */
    private fun weightKg(raw: Double?): Double? = when {
      raw == null -> null
      raw in 10.0..500.0 -> Math.round(raw * 100.0) / 100.0
      raw in 10_000.0..500_000.0 -> Math.round(raw / 10.0) / 100.0
      else -> null
    }
  }
}
