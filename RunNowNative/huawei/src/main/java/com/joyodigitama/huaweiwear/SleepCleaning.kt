package com.joyodigitama.huaweiwear

/**
 * What says the wearer was awake while the watch recorded a stage-less range. All times are unix seconds.
 * Same shape as `SleepEvidence` in src/features/sleep/cleanSleep.ts.
 */
internal class SleepEvidence(
  /** Stored workouts (any source). */
  val workouts: List<TimeSpan> = emptyList(),
  /** Stored spot HRV measurements (`hrv_readings.source = 'spot'`). */
  val spots: List<TimeSpan> = emptyList(),
  /** Start of every minute with at least [SleepCleaning.ACTIVE_STEPS_PER_MIN] steps, in any order. */
  val activeMinutes: List<Long> = emptyList(),
) {
  companion object {
    val NONE = SleepEvidence()
  }
}

/**
 * Watches that give no sleep stages (Huawei Watch Fit 2: only `sleep_state.bin`) store one stage-less range per
 * TruSleep state record. Those records are the watch's sensor windows, not a verdict of "asleep": a 1-minute HRV or
 * stress check, and a workout started on the watch, both open such windows (see `SleepSync.segmentsFromState`).
 *
 * This is the rule that tells them apart again, on stage-less segments only (a segment with a stage came from the
 * watch's own sleep analysis and is never touched). Same rule as `cleanUnstagedSleep` in
 * src/features/sleep/cleanSleep.ts and `SleepCleaning` in SleepCleaning.swift; keep the three and their constants together.
 *
 *  1. Awake evidence is subtracted from the stage-less ranges: a stored workout, a stored spot HRV measurement, and
 *     every minute with [ACTIVE_STEPS_PER_MIN] steps or more.
 *  2. What is left is chained into runs (pieces at most [RUN_BRIDGE_SEC] apart). A run holding less than [MIN_RUN_SEC]
 *     of sleep is dropped.
 *  3. A kept segment that follows a workout, or that follows another stage-less segment after more than
 *     [UNSTAGED_BREAK_SEC] of nothing, is marked [SleepSegment.breakBefore]; every session builder starts a new
 *     session there, whatever the 3 h session gap says.
 */
internal object SleepCleaning {
  /**
   * Bumped whenever the rules above change. The Health Connect export and the night HRV rows derived from sleep are
   * repaired once for the whole stored history after an update to a higher version (see [SleepRulesRepair]).
   */
  const val SLEEP_RULES_VERSION = 2

  /** A stage-less run needs this much sleep (gaps up to [RUN_BRIDGE_SEC] inside it are not counted) to be sleep. */
  const val MIN_RUN_SEC = 20 * 60L

  /** Stage-less pieces this close belong to one run: a minute of steps or a short state gap does not end a night. */
  const val RUN_BRIDGE_SEC = 2 * 60L

  /** A minute with this many steps is awake. Sleep, even a restless one, does not walk. */
  const val ACTIVE_STEPS_PER_MIN = 15

  /** Room left around a spot HRV measurement; the stored span is the 1-minute reading itself. */
  const val SPOT_MARGIN_SEC = 60L

  /** Two stage-less segments further apart than this are two sessions: nobody sleeps through an hour of nothing recorded. */
  const val UNSTAGED_BREAK_SEC = 60 * 60L

  /** A workout's span; a row without a usable end falls back to its active duration. */
  fun workoutSpan(start: Long, end: Long, duration: Long): TimeSpan =
    TimeSpan(start, if (end > start) end else start + maxOf(0L, duration))

  private val byStart = compareBy<SleepSegment>({ it.start }, { it.end })

  private fun mergeSpans(spans: List<TimeSpan>): List<TimeSpan> {
    val out = ArrayList<TimeSpan>()
    for (s in spans.filter { it.end > it.start }.sortedWith(compareBy({ it.start }, { it.end }))) {
      val last = out.lastOrNull()
      if (last != null && s.start <= last.end) out[out.size - 1] = TimeSpan(last.start, maxOf(last.end, s.end))
      else out.add(s)
    }
    return out
  }

  /** `[start, end)` minus the sorted, disjoint [awake] spans. [from] is the first span that can still overlap. */
  private fun subtract(start: Long, end: Long, awake: List<TimeSpan>, from: Int): List<TimeSpan> {
    val out = ArrayList<TimeSpan>(2)
    var at = start
    var i = from
    while (i < awake.size && awake[i].start < end) {
      if (awake[i].end > at) {
        if (awake[i].start > at) out.add(TimeSpan(at, awake[i].start))
        at = maxOf(at, awake[i].end)
        if (at >= end) break
      }
      i++
    }
    if (at < end) out.add(TimeSpan(at, end))
    return out
  }

  /** Index of the first of the sorted, disjoint [spans] that ends after [time]. */
  private fun firstEndingAfter(spans: List<TimeSpan>, time: Long): Int {
    var lo = 0
    var hi = spans.size
    while (lo < hi) {
      val mid = (lo + hi) ushr 1
      if (spans[mid].end > time) hi = mid else lo = mid + 1
    }
    return lo
  }

  /**
   * Applies the rule above and returns every segment, staged ones untouched, sorted by start. Stage-less segments come
   * back clipped to what is left of them (a copy, same `type`), without overlaps among themselves.
   */
  fun cleanUnstagedSleep(segments: List<SleepSegment>, evidence: SleepEvidence): List<SleepSegment> {
    val staged = ArrayList<SleepSegment>()
    val unstaged = ArrayList<SleepSegment>()
    for (s in segments) {
      if (s.stage != null) staged.add(s) else if (s.end > s.start) unstaged.add(s)
    }
    if (unstaged.isEmpty()) return segments.sortedWith(byStart)

    val awake = mergeSpans(
      evidence.workouts +
        evidence.spots.map { TimeSpan(it.start - SPOT_MARGIN_SEC, it.end + SPOT_MARGIN_SEC) } +
        evidence.activeMinutes.map { TimeSpan(it, it + 60) },
    )

    // Stage-less ranges overlap each other (two raw types for one night); an earlier-starting one keeps the time.
    unstaged.sortWith(byStart)
    val pieces = ArrayList<SleepSegment>()
    var cursor = Long.MIN_VALUE
    for (seg in unstaged) {
      val start = maxOf(seg.start, cursor)
      if (start >= seg.end) continue
      cursor = seg.end
      for (span in subtract(start, seg.end, awake, firstEndingAfter(awake, start))) {
        pieces.add(SleepSegment(span.start, span.end, seg.type, null))
      }
    }

    // Runs: pieces chained with gaps up to RUN_BRIDGE_SEC; short ones are not sleep.
    val kept = ArrayList<SleepSegment>(staged)
    var run = ArrayList<SleepSegment>()
    var runSec = 0L
    var reach = Long.MIN_VALUE
    fun closeRun() {
      if (runSec >= MIN_RUN_SEC) kept.addAll(run)
      run = ArrayList()
      runSec = 0
    }
    for (piece in pieces) {
      if (run.isNotEmpty() && piece.start - reach > RUN_BRIDGE_SEC) closeRun()
      run.add(piece)
      runSec += piece.end - piece.start
      reach = piece.end
    }
    closeRun()
    kept.sortWith(byStart)

    // Where a workout or a long stretch of nothing separates stage-less sleep from what came before, a new session starts.
    val workouts = mergeSpans(evidence.workouts)
    for (i in 1 until kept.size) {
      val seg = kept[i]
      if (seg.stage != null) continue
      val prev = kept[i - 1]
      if (seg.start < prev.end) continue
      val next = workouts.getOrNull(firstEndingAfter(workouts, prev.end))
      val workoutBetween = next != null && next.start < seg.start
      if (workoutBetween || (prev.stage == null && seg.start - prev.end > UNSTAGED_BREAK_SEC)) {
        kept[i] = SleepSegment(seg.start, seg.end, seg.type, null, breakBefore = true, afterWorkout = workoutBetween)
      }
    }
    return kept
  }
}
