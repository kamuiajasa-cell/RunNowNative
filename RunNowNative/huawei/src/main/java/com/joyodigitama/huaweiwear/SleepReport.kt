package com.joyodigitama.huaweiwear

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV

/**
 * Drops unstaged segments that lie within [SleepReport.SESSION_GAP_SEC] of a staged one. The watch stores short
 * unstaged spans around a staged night (seen in the evening before it); next to real stages they only stretch the
 * night. An unstaged nap far from any staged sleep stays. Same rule as `withoutStrayUnstaged` in src/features/today/stats.ts.
 */
internal fun withoutStrayUnstaged(segments: List<SleepSegment>): List<SleepSegment> {
  val staged = segments.filter { it.stage != null }
  return segments.filter { s ->
    s.stage != null || staged.none { t -> t.start - s.end <= SleepReport.SESSION_GAP_SEC && s.start - t.end <= SleepReport.SESSION_GAP_SEC }
  }
}

/**
 * Drops stretches of sleep (segments chained with gaps up to [SleepReport.BLIP_GAP_SEC]) holding less than
 * [SleepReport.MIN_SLEEP_SEC] asleep. A watch records a few minutes of sitting still, such as a 1-minute HRV or stress
 * check, as sleep; on its own that became the latest night or a nap, and near a night it stretched the night. Same rule
 * as `withoutBlips` in src/features/today/stats.ts. Returns the kept segments sorted by start.
 */
internal fun withoutBlips(segments: List<SleepSegment>): List<SleepSegment> {
  val kept = ArrayList<SleepSegment>(segments.size)
  val stretch = ArrayList<SleepSegment>()
  var reach = Long.MIN_VALUE
  fun close() {
    val asleep = stretch.sumOf { if (it.stage == "awake") 0L else maxOf(0L, it.end - it.start) }
    if (asleep >= SleepReport.MIN_SLEEP_SEC) kept.addAll(stretch)
    stretch.clear()
  }
  for (s in segments.sortedWith(compareBy({ it.start }, { it.end }))) {
    if (stretch.isNotEmpty() && s.start - reach > SleepReport.BLIP_GAP_SEC) close()
    reach = if (stretch.isEmpty()) s.end else maxOf(reach, s.end)
    stretch.add(s)
  }
  close()
  return kept
}

/** One night as the report describes it, seconds per stage from the stored segments. */
internal class SleepNight(
  val start: Long,
  val end: Long,
  val totalSec: Long,
  val lightSec: Long,
  val deepSec: Long,
  val remSec: Long,
  val awakeSec: Long,
) {
  /** Whether any segment carried a decoded stage. */
  val hasStages: Boolean get() = lightSec + deepSec + remSec + awakeSec > 0
}

/**
 * The morning sleep report (a notification card on the watch), as Huawei Health 17.0.8.300 behaves on the wire. DataSync event `80030001`, phone package
 * `hw.unitedevice.sleepnotification`, watch package `hw.watch.health.sleep`, level 0, gated on ExpandCapability 84.
 *
 * The event data is a TLV of UTF-8 strings:
 * `06` title, `07` description, `08` label of the "open" button, `09` label of the "ignore" button, `0a` locale
 * (`xx-YY`), `0b` URI of the "open" action, `0c` URI of the "ignore" action. The watch answers the same event id
 * with data `01` when it showed the card; anything else counts as not shown.
 */
internal object SleepReport {
  const val EVENT_ID = 80030001
  const val PHONE_PKG = "hw.unitedevice.sleepnotification"
  const val WATCH_PKG = "hw.watch.health.sleep"

  const val TAG_TITLE = 0x06
  const val TAG_DESCRIPTION = 0x07
  const val TAG_OPEN_LABEL = 0x08
  const val TAG_IGNORE_LABEL = 0x09
  const val TAG_LOCALE = 0x0A
  const val TAG_OPEN_URI = 0x0B
  const val TAG_IGNORE_URI = 0x0C

  /** Segments closer together than this belong to the same session (`SESSION_GAP_SEC` in `src/features/today/stats.ts`). */
  const val SESSION_GAP_SEC = 3 * 3600L

  /** Shorter sessions are naps, not "last night". */
  const val MIN_NIGHT_SEC = 3 * 3600L

  /** A short session starting from this local hour (up to [EVENING_END_HOUR] next morning) is an evening doze (`EVENING_START_HOUR` in `stats.ts`). */
  const val EVENING_START_HOUR = 19

  /** An evening doze starts before this local hour; later starts are daytime naps (`EVENING_END_HOUR` in `stats.ts`). */
  const val EVENING_END_HOUR = 6

  /** An evening doze joins the session after it when that starts within this long after it ends (`EVENING_JOIN_GAP_SEC` in `stats.ts`). */
  const val EVENING_JOIN_GAP_SEC = 8 * 3600L

  /** Sleep within this of other sleep belongs to the same stretch when telling a blip from a nap (`BLIP_GAP_SEC` in `stats.ts`). */
  const val BLIP_GAP_SEC = 3600L

  /** A stretch with less sleep than this (awake excluded) is not sleep, e.g. a 1-minute HRV check (`MIN_SLEEP_SEC` in `stats.ts`). */
  const val MIN_SLEEP_SEC = 5 * 60L

  /** A report is only pushed this long after waking up at most; later it is not a morning report any more. */
  const val MAX_AGE_SEC = 8 * 3600L

  /**
   * Whether a session that started at [prevStart], ended at [prevEnd] and holds [prevAsleepSec] of sleep is an evening
   * doze that the session starting at [nextStart] continues unless a workout lies between ([nextAfterWorkout]): a short session (under
   * [MIN_NIGHT_SEC]) starting from [EVENING_START_HOUR]:00 to [EVENING_END_HOUR]:00 local time, with the next session
   * starting within [EVENING_JOIN_GAP_SEC] of its end (`joinsEveningDoze` in `stats.ts`).
   */
  fun joinsEveningDoze(prevStart: Long, prevEnd: Long, prevAsleepSec: Long, nextStart: Long, nextAfterWorkout: Boolean, zone: ZoneId): Boolean {
    if (nextAfterWorkout || prevAsleepSec >= MIN_NIGHT_SEC || nextStart - prevEnd > EVENING_JOIN_GAP_SEC) return false
    val hour = Instant.ofEpochSecond(prevStart).atZone(zone).hour
    return hour >= EVENING_START_HOUR || hour < EVENING_END_HOUR
  }

  /**
   * The latest session of [segments], chained with gaps up to [SESSION_GAP_SEC] and cut at a segment marked
   * [SleepSegment.breakBefore] (`lastSleepSession` in `stats.ts`). A short evening session before it joins it, see
   * [EVENING_JOIN_GAP_SEC]; the time between the two counts as no sleep.
   */
  fun lastNight(segments: List<SleepSegment>, zone: ZoneId = ZoneId.systemDefault()): SleepNight? {
    if (segments.isEmpty()) return null
    val sorted = withoutBlips(withoutStrayUnstaged(segments))
    if (sorted.isEmpty()) return null
    var first = sorted.size - 1
    var earliest = sorted[first].start
    while (first > 0 && !sorted[first].breakBefore && earliest - sorted[first - 1].end <= SESSION_GAP_SEC) {
      first--
      earliest = minOf(earliest, sorted[first].start)
    }
    while (first > 0) {
      // The session before: chained backwards the same way.
      var prevFirst = first - 1
      var prevStart = sorted[prevFirst].start
      while (prevFirst > 0 && !sorted[prevFirst].breakBefore && prevStart - sorted[prevFirst - 1].end <= SESSION_GAP_SEC) {
        prevFirst--
        prevStart = minOf(prevStart, sorted[prevFirst].start)
      }
      val prev = sorted.subList(prevFirst, first)
      val asleep = prev.sumOf { if (it.stage == "awake") 0L else maxOf(0L, it.end - it.start) }
      if (!joinsEveningDoze(prevStart, prev.maxOf { it.end }, asleep, earliest, sorted[first].afterWorkout, zone)) break
      first = prevFirst
      earliest = prevStart
    }
    var total = 0L
    var light = 0L
    var deep = 0L
    var rem = 0L
    var awake = 0L
    var end = 0L
    for (segment in sorted.subList(first, sorted.size)) {
      val seconds = maxOf(0L, segment.end - segment.start)
      total += seconds
      end = maxOf(end, segment.end)
      when (segment.stage) {
        "light" -> light += seconds
        "deep" -> deep += seconds
        "rem" -> rem += seconds
        "awake" -> awake += seconds
      }
    }
    return SleepNight(earliest, end, total, light, deep, rem, awake)
  }

  /** Whether to push [night] now: a night, ending today, at most [MAX_AGE_SEC] ago and newer than the last one reported. */
  fun shouldPush(night: SleepNight, nowSec: Long, zone: ZoneId, lastReportedEnd: Long): String? {
    if (night.end <= lastReportedEnd) return "already reported (session end ${night.end} <= $lastReportedEnd)"
    if (night.totalSec < MIN_NIGHT_SEC) return "only ${night.totalSec / 60} min of sleep, a nap"
    if (nowSec - night.end > MAX_AGE_SEC) return "ended ${(nowSec - night.end) / 3600} h ago, too late for a morning report"
    val endDay = Instant.ofEpochSecond(night.end).atZone(zone).toLocalDate()
    val today = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
    if (endDay != today) return "the session ended on $endDay, not today ($today)"
    return null
  }

  /** `7 h 12 min`; minutes only below an hour. */
  fun duration(seconds: Long): String {
    val minutes = (seconds + 30) / 60
    val h = minutes / 60
    val m = minutes % 60
    return if (h == 0L) "$m min" else "$h h ${m.toString().padStart(2, '0')} min"
  }

  class Message(val title: String, val description: String, val openLabel: String, val ignoreLabel: String, val locale: String, val openUri: String, val ignoreUri: String)

  /** The text of the card. No sleep score is made up: JoyoFit stores stages, not a score. */
  fun message(night: SleepNight, zone: ZoneId, locale: Locale): Message {
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val from = Instant.ofEpochSecond(night.start).atZone(zone).format(clock)
    val to = Instant.ofEpochSecond(night.end).atZone(zone).format(clock)
    val sb = StringBuilder("${duration(night.totalSec)} asleep, $from to $to.")
    if (night.hasStages) {
      val parts = ArrayList<String>()
      if (night.deepSec > 0) parts += "Deep ${duration(night.deepSec)}"
      if (night.remSec > 0) parts += "REM ${duration(night.remSec)}"
      if (night.lightSec > 0) parts += "Light ${duration(night.lightSec)}"
      if (parts.isNotEmpty()) sb.append(' ').append(parts.joinToString(", ")).append('.')
    }
    return Message(
      title = "Sleep report",
      description = sb.toString(),
      openLabel = "Open app",
      ignoreLabel = "Ignore",
      locale = localeTag(locale),
      openUri = "joyofit://today",
      ignoreUri = "joyofit://today",
    )
  }

  /** `xx-YY` like `npn.e` / `CommonUtil.ab`: language and country, `en-US` when the locale has no country. */
  fun localeTag(locale: Locale): String {
    val language = locale.language.ifEmpty { "en" }
    val country = locale.country.ifEmpty { "US" }
    return "$language-$country"
  }

  /** The event data: tags `06..0c` as UTF-8 TLV strings. */
  fun payload(message: Message): ByteArray = HuaweiTLV()
    .put(TAG_TITLE, message.title)
    .put(TAG_DESCRIPTION, message.description)
    .put(TAG_OPEN_LABEL, message.openLabel)
    .put(TAG_IGNORE_LABEL, message.ignoreLabel)
    .put(TAG_LOCALE, message.locale)
    .put(TAG_OPEN_URI, message.openUri)
    .put(TAG_IGNORE_URI, message.ignoreUri)
    .serialize()

  /** The watch's answer: shown when the data reads as the number 1 (hex string that parses to 1). */
  fun acknowledged(data: ByteArray?): Boolean = DataSyncBytes.readInt32(data) == 1
}
