package com.joyodigitama.huaweiwear

/** One minute of `step_samples` assembled from the export; every field but [ts] is optional data. */
internal class ImportedMinute(val ts: Long) {
  var steps = 0
  var distance = 0

  /** Small calories (cal, 1/1000 kcal), the unit of `step_samples.calories`. */
  var calories = 0
  var heartRate: Int? = null
  var spo2: Int? = null
  var restingHeartRate: Int? = null
}

/** A `sleep_segments` row: [type] is the raw kind the watch uses (see [SleepSync]), [stage] its stage name. */
internal class ImportedSleep(val start: Long, val end: Long, val type: Int, val stage: String?)

/**
 * One `health_metrics` row ([type] is the id the Health screen knows: `emotion`, `hrv`, `sleep_apnea`, `skin_temperature`,
 * `arterial_stiffness`, `arrhythmia`). [ts] is unix seconds, [json] the `values_json` text.
 */
internal class ImportedMetric(val type: String, val ts: Long, val value: Double, val json: String)

/** A night's score for `sleep_scores`; times are unix seconds. */
internal class ImportedSleepScore(val start: Long, val end: Long, val score: Int, val efficiency: Int?)

/**
 * A day's totals from the `SportsHealth-Data.xls` summary: [noonSec] is 12:00 of that day in the zone the export names (unix
 * seconds), [dayStartSec] / [dayEndSec] the day's own bounds, calories in small calories. What the day's minute rows do not add
 * up to is written back (see [HuaweiImportStore.writeDayTotals]).
 */
internal class ImportedDayTotal(val dayStartSec: Long, val dayEndSec: Long, val noonSec: Long, val steps: Int, val distance: Int, val calories: Int)

/**
 * One weigh-in from the export for `scale_reading`: [tsMs] epoch milliseconds, [weightKg] kilograms, [reportedJson] the
 * `ReportedBody` object (`src/features/scale/types.ts`) of what the scale measured, null when it sent only a weight.
 */
internal class ImportedWeighIn(val tsMs: Long, val weightKg: Double, val impedanceOhm: Double?, val reportedJson: String?)

/**
 * A meal from `NUTRITION_RECORD` for JoyoRogo's diary (`food.importHistory`): [key] identifies it across imports, [date] is the
 * diary day (`YYYY-MM-DD`, the day in the record's own time zone), [meal] one of JoyoRogo's slots (`sarapan`, `makan_siang`,
 * `makan_malam`, `snack`), [eatenAtMs] when it was eaten if the record says. Macros are grams.
 */
internal class ImportedMeal(
  val key: String,
  val date: String,
  val meal: String,
  val name: String,
  val kcal: Double,
  val protein: Double?,
  val carbs: Double?,
  val fat: Double?,
  val eatenAtMs: Long?,
)

/** One thing for JoyoRogo that the importer found: [kind] `meal` or `water`, [key] its identity, [payload] the JSON the endpoint takes. */
internal class QueuedImport(val kind: String, val key: String, val payload: String)

/** A `workouts` row with its `workout_points`. Times are unix seconds, distance metres, calories kcal. */
internal class ImportedWorkout(
  val start: Long,
  val end: Long,
  val sportType: Int,
  val sportName: String,
  val duration: Int,
  val distance: Int,
  val calories: Int,
  val steps: Int,
  val avgHr: Int?,
  val maxHr: Int?,
  val summaryJson: String,
  val points: List<WorkoutPoint>,
)

/** What the parsers hand over. Timestamps are unix seconds. */
internal interface HuaweiImportCollector {
  fun heartRate(tsSec: Long, bpm: Int)
  fun restingHeartRate(tsSec: Long, bpm: Int)
  fun spo2(tsSec: Long, percent: Int)
  fun sleep(segment: ImportedSleep)
  fun metric(point: ImportedMetric)
  fun sleepScore(score: ImportedSleepScore)
  fun dayTotal(total: ImportedDayTotal)
  fun steps(minuteSec: Long, steps: Int, distance: Int, calories: Int)

  fun weighIn(weighIn: ImportedWeighIn)

  /** A meal of `NUTRITION_RECORD`; the importer queues it for JoyoRogo. */
  fun meal(meal: ImportedMeal)

  /** Water of one `DRINK_WATER` record, [ml] on the local day [date]; [key] identifies the record so a repeat is counted once. */
  fun water(date: String, ml: Int, key: String)

  /** A fact about the person (`height` cm, `male` 1.0 / 0.0, `age` years) seen at [tsMs]; the newest of each kind is kept for the profile. */
  fun profileFact(kind: String, value: Double, tsMs: Long)

  /** A sample point whose key the importer does not use. */
  fun unmapped(key: String)

  /**
   * A sample point of a known [key] that was dropped. [reason] is a short code (`no-time`, `no-number`, `out-of-range`,
   * `bad-span`) and [sample] the offending value, clipped, so the import summary can say what was unreadable.
   */
  fun rejected(key: String, reason: String, sample: String?)
}

/** Result of writing one workout; [updated] when an earlier import stored it without its track and it was filled in now. */
internal class WorkoutWrite(val added: Boolean, val points: Int, val updated: Boolean = false)

/**
 * Where the importer writes. Every method is insert-or-ignore in spirit: watch data already in the database is never
 * overwritten, and importing the same export twice changes nothing the second time. See [SqliteHuaweiImportStore].
 */
internal interface HuaweiImportStore {
  /** Merges minutes into `step_samples`; returns the rows inserted or filled in. */
  fun writeMinutes(rows: Collection<ImportedMinute>): Int

  /** Adds sleep segments that no watch-synced segment overlaps; returns the rows added. */
  fun writeSleep(segments: List<ImportedSleep>): Int

  /** Adds `health_metrics` rows that are not there yet; returns the rows added per type. */
  fun writeMetrics(points: List<ImportedMetric>): Map<String, Int>

  /** Adds night scores that no score starting at the same moment exists for; returns the rows added. */
  fun writeSleepScores(scores: List<ImportedSleepScore>): Int

  /** Adds, per day, the part of its total the `step_samples` rows of that day do not add up to, as one row from noon; returns the rows added. */
  fun writeDayTotals(totals: List<ImportedDayTotal>): Int

  /** Queues [items] for JoyoRogo in `rogo_import_queue` unless an item of that kind and key is there; returns the rows added. */
  fun queueForRogo(items: List<QueuedImport>): Int

  /** Adds weigh-ins to `scale_reading` unless one of the same moment is there; returns the rows added. */
  fun writeWeighIns(weighIns: List<ImportedWeighIn>): Int

  /** Adds the workout unless the database already has one starting within two minutes of it. */
  fun writeWorkout(workout: ImportedWorkout): WorkoutWrite
}
