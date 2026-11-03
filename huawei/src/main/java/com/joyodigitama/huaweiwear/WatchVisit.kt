package com.joyodigitama.huaweiwear

/**
 * How long a watch that is not the active one waits between background visits, minutes: four sync intervals of the
 * active watch, never under two hours (a visit costs a disconnect and a connect of two watches).
 */
internal fun watchVisitIntervalMinutes(syncIntervalMinutes: Int): Long = maxOf(4L * syncIntervalMinutes, MIN_VISIT_INTERVAL_MINUTES)

internal const val MIN_VISIT_INTERVAL_MINUTES = 120L

/**
 * One `device` row with `bg_sync = 1` as [nextVisit] sees it. [bound] is the HiChain bind (a visit never starts a first
 * bind); [lastAttemptSec] is the last visit this process tried, failed or not (null: none), kept in memory only.
 */
internal class VisitCandidate(
  val address: String,
  val lastSyncSec: Long?,
  val bound: Boolean,
  val lastAttemptSec: Long?,
)

/**
 * The watch to visit now, or null. A visit is due for a watch that is not [activeAddress], is bound, and neither synced nor
 * was tried within [intervalSec]. Nothing is visited while [busy] (live workout, live heart rate, an HRV measurement, an
 * operation on the link, the user having disconnected). The watch that waited longest goes first (never synced: first),
 * ties by address.
 */
internal fun nextVisit(
  devices: List<VisitCandidate>,
  activeAddress: String?,
  nowSec: Long,
  intervalSec: Long,
  busy: Boolean,
): VisitCandidate? {
  if (busy) return null
  return devices
    .filter { it.address != activeAddress && it.bound }
    .filter { it.lastSyncSec == null || nowSec - it.lastSyncSec >= intervalSec }
    .filter { it.lastAttemptSec == null || nowSec - it.lastAttemptSec >= intervalSec }
    .minWithOrNull(compareBy<VisitCandidate> { it.lastSyncSec ?: Long.MIN_VALUE }.thenBy { it.address })
}
