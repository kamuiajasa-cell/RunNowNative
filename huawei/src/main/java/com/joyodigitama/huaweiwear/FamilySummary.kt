package com.joyodigitama.huaweiwear

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** `FamilyScopes` in `HuaweiWearFamily.types.ts`: what the monitored person agreed to share. */
internal data class FamilyScopes(
  val sync: Boolean,
  val steps: Boolean,
  val sleep: Boolean,
  val restingHr: Boolean,
  val spo2: Boolean,
)

/** One stored minute of `step_samples`. */
internal class FamilyStepRow(val timestamp: Long, val steps: Int)

/**
 * What [FamilySummary] reads from the local database. Times are unix seconds, `[fromSec, toSec)`; rows imported from a
 * Huawei export (`imported = 1`) never show up, as in the cloud upload. [FamilySummary] calls the methods of a category
 * only when its scope is granted.
 */
internal interface FamilySource {
  /** `device.last_sync` of the newest synced watch. */
  fun watchSyncSec(): Long?

  /** Timestamp of the newest `step_samples` row. */
  fun newestSampleSec(fromSec: Long, toSec: Long): Long?

  fun stepSamples(fromSec: Long, toSec: Long): List<FamilyStepRow>

  /** The lowest `resting_heart_rate` above zero (`listRestingHeartRateByDay` in `src/db/queries.ts`). */
  fun restingHeartRate(fromSec: Long, toSec: Long): Int?

  /** Every `spo2` reading above zero. */
  fun spo2Readings(fromSec: Long, toSec: Long): List<Int>

  /** Sleep segments that end in the range, stage-less ones already cleaned of what was not sleep ([SleepCleaning]). */
  fun sleepSegments(fromSec: Long, toSec: Long): List<SleepSegment>
}

/** One calendar day of the ingest body; a category that was not granted or has no data is null and absent from the JSON. */
internal class FamilyDay(
  val date: String,
  val steps: Int?,
  val firstStepAt: Long?,
  val restingHr: Int?,
  val sleepMin: Int?,
  val spo2Min: Int?,
  val spo2LowCount: Int?,
  val lastSampleAt: Long?,
) {
  val hasData: Boolean
    get() = steps != null || restingHr != null || sleepMin != null || spo2Min != null || lastSampleAt != null

  fun toJson(): JSONObject = JSONObject().apply {
    put("date", date)
    steps?.let { put("steps", it) }
    firstStepAt?.let { put("firstStepAt", it) }
    restingHr?.let { put("restingHr", it) }
    sleepMin?.let { put("sleepMin", it) }
    spo2Min?.let { put("spo2Min", it) }
    spo2LowCount?.let { put("spo2LowCount", it) }
    lastSampleAt?.let { put("lastSampleAt", it) }
  }
}

/** The body of `PUT /v1/family/ingest` (docs/family-monitoring.md section 10). Times are unix ms. */
internal class FamilyPayload(
  val sentAt: Long,
  val tzOffsetMin: Int,
  val watchSyncAt: Long?,
  val battery: Int?,
  val days: List<FamilyDay>,
) {
  /** Nothing to tell the server: no watch sync or battery known and no day with data. */
  val isEmpty: Boolean get() = watchSyncAt == null && battery == null && days.isEmpty()

  fun toJson(): String = JSONObject().apply {
    put("sentAt", sentAt)
    put("tzOffsetMin", tzOffsetMin)
    put("watchSyncAt", watchSyncAt ?: JSONObject.NULL)
    put("battery", battery ?: JSONObject.NULL)
    put("days", JSONArray().also { array -> days.forEach { array.put(it.toJson()) } })
  }.toString()
}

/**
 * The daily summary the monitored person's phone sends for family monitoring: the last [DAYS] local days, only the
 * granted categories. Days are the phone's calendar in [ZoneId], so a date is the day the person lives.
 */
internal object FamilySummary {
  const val DAYS = 3

  /** Readings at or below this count as low SpO2. */
  const val SPO2_LOW_MAX = 90

  /** A night ends on date D when its last segment ends between 18:00 of D-1 and 18:00 of D, local. */
  private const val NIGHT_CUTOFF_HOUR = 18

  fun build(nowMs: Long, zone: ZoneId, scopes: FamilyScopes, source: FamilySource, battery: Int?): FamilyPayload {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val dates = List(DAYS) { today.minusDays((DAYS - 1 - it).toLong()) }
    val sleepMinutes = if (scopes.sleep) nightMinutes(dates, zone, source) else emptyMap()
    val days = dates.map { date -> day(date, zone, scopes, source, sleepMinutes[date]) }.filter { it.hasData }
    return FamilyPayload(
      sentAt = nowMs,
      tzOffsetMin = zone.rules.getOffset(Instant.ofEpochMilli(nowMs)).totalSeconds / 60,
      watchSyncAt = if (scopes.sync) source.watchSyncSec()?.times(1000) else null,
      battery = if (scopes.sync) battery else null,
      days = days,
    )
  }

  private fun day(date: LocalDate, zone: ZoneId, scopes: FamilyScopes, source: FamilySource, sleepMin: Int?): FamilyDay {
    val from = date.atStartOfDay(zone).toEpochSecond()
    val to = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
    var steps: Int? = null
    var firstStepAt: Long? = null
    if (scopes.steps) {
      val rows = source.stepSamples(from, to)
      if (rows.isNotEmpty()) {
        steps = rows.sumOf { it.steps }
        firstStepAt = rows.filter { it.steps > 0 }.minOfOrNull { it.timestamp }?.times(1000)
      }
    }
    val spo2 = if (scopes.spo2) source.spo2Readings(from, to).filter { it > 0 } else emptyList()
    return FamilyDay(
      date = date.toString(),
      steps = steps,
      firstStepAt = firstStepAt,
      restingHr = if (scopes.restingHr) source.restingHeartRate(from, to)?.takeIf { it > 0 } else null,
      sleepMin = sleepMin,
      spo2Min = spo2.minOrNull(),
      spo2LowCount = if (spo2.isEmpty()) null else spo2.count { it <= SPO2_LOW_MAX },
      lastSampleAt = if (scopes.sync) source.newestSampleSec(from, to)?.times(1000) else null,
    )
  }

  /**
   * Minutes asleep of the night that ends on each of [dates]. Blips and stray unstaged spans are dropped first (the
   * rules Today's last night uses, [withoutBlips], [withoutStrayUnstaged]) with a margin of segments around the range so
   * the edges see their neighbours; awake stretches do not count. A date without sleep is absent. Minutes are summed
   * per night window, not per session, so [SleepSegment.breakBefore] changes nothing here.
   */
  private fun nightMinutes(dates: List<LocalDate>, zone: ZoneId, source: FamilySource): Map<LocalDate, Int> {
    val windows = dates.associateWith { nightWindow(it, zone) }
    val from = windows.values.minOf { it.first } - SleepReport.SESSION_GAP_SEC
    val to = windows.values.maxOf { it.second } + SleepReport.SESSION_GAP_SEC
    val kept = withoutBlips(withoutStrayUnstaged(source.sleepSegments(from, to)))
    val minutes = HashMap<LocalDate, Int>()
    for ((date, window) in windows) {
      val asleepSec = kept
        .filter { it.end >= window.first && it.end < window.second && it.stage != "awake" }
        .sumOf { maxOf(0L, it.end - it.start) }
      val asleepMin = ((asleepSec + 30) / 60).toInt()
      if (asleepMin > 0) minutes[date] = asleepMin
    }
    return minutes
  }

  private fun nightWindow(date: LocalDate, zone: ZoneId): Pair<Long, Long> =
    date.minusDays(1).atTime(NIGHT_CUTOFF_HOUR, 0).atZone(zone).toEpochSecond() to
      date.atTime(NIGHT_CUTOFF_HOUR, 0).atZone(zone).toEpochSecond()
}
