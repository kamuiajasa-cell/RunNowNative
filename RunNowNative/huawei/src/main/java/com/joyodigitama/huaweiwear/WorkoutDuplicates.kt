package com.joyodigitama.huaweiwear

/** A workout session as the duplicate rule sees it: when, which watch recorded it (null = imported or unknown), and whether it has a GPS track. */
internal class WorkoutSpan(val start: Long, val end: Long, val device: String?, val hasGps: Boolean)

/**
 * Two sessions are the same session recorded twice when two different, known watches recorded them and they overlap by at
 * least half of the shorter one. A watch never duplicates itself, and a row without a device (imported file, unattributed
 * history) is never a duplicate: it is replaced or kept by the ordinary upsert, as before several watches existed.
 */
internal fun isCrossWatchDuplicate(incoming: WorkoutSpan, stored: WorkoutSpan): Boolean {
  val a = incoming.device ?: return false
  val b = stored.device ?: return false
  if (a == b) return false
  val overlap = minOf(incoming.end, stored.end) - maxOf(incoming.start, stored.start)
  if (overlap <= 0) return false
  val shorter = minOf(incoming.end - incoming.start, stored.end - stored.start)
  return overlap * 2 >= shorter
}

/**
 * Which copy of a duplicated session is kept: the one with a GPS track; with equal GPS-ness the one of the primary watch
 * ([primary] is `device.address` of `is_primary = 1`, null when no watch is primary); else the one stored first, which is
 * [stored]. True when [incoming] wins and replaces [stored].
 */
internal fun incomingWinsDuplicate(incoming: WorkoutSpan, stored: WorkoutSpan, primary: String?): Boolean {
  if (incoming.hasGps != stored.hasGps) return incoming.hasGps
  return primary != null && incoming.device == primary && stored.device != primary
}
