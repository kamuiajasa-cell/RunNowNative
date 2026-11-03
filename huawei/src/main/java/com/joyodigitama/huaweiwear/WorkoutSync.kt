// Derived from Gadgetbridge (AGPL-3.0-or-later; see NOTICE): service/devices/huawei/HuaweiWorkoutSyncManager,
// HuaweiSupportProvider.downloadWorkoutGpsFiles, requests/GetWorkoutCapability.
package com.joyodigitama.huaweiwear

import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiGpsParser
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiPacket
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.packets.Workout

/** Where [WorkoutSync] reads what is already stored and writes each finished workout. */
internal interface WorkoutSink {
  /** True when a complete workout with this start and end is stored (nothing left to download for it). */
  suspend fun isComplete(start: Long, end: Long): Boolean

  /** Stores the workout and its points, replacing an earlier copy with the same start. True if it is new or was incomplete. */
  suspend fun save(record: WorkoutRecord): Boolean
}

/** What [DynamicsSink.merge] did with the running form of one stored workout. */
internal enum class DynamicsMerge { UPDATED, UNCHANGED, NOT_STORED }

/** Where [WorkoutSync.refetchDynamics] puts the running form it read again; only the dynamics of the stored summary are touched. */
internal interface DynamicsSink {
  suspend fun merge(start: Long, dynamics: RunDynamics.Result): DynamicsMerge
}

/** A stored workout to read from the watch again: its start (unix seconds, the key), end and the watch's own number when it was stored. */
internal class RefetchTarget(val start: Long, val end: Long, val number: Int)

/** How one workout of [WorkoutSync.refetchDynamics] ended. */
internal enum class RefetchOutcome {
  /** The watch sent running form and the stored summary now has it. */
  UPDATED,

  /** The watch sent running form and the summary already had exactly that. */
  UNCHANGED,

  /** The watch still has the workout but its samples carry no running form (a walk, an older sport mode). */
  NO_DYNAMICS,

  /** The watch no longer lists the workout (or this phone has no copy recorded by this watch). */
  GONE,

  /** The watch did not answer usably, or the workout could not be stored; asking again may work. */
  FAILED,
}

/**
 * What a refetch did, per requested start (`WorkoutRefetchReport` in `HuaweiWear.types.ts`). A start the phone has no copy of that this
 * watch recorded is [RefetchOutcome.GONE] too: there is nothing on the phone to merge into.
 */
internal class RefetchReport(requested: List<Long>, read: Map<Long, RefetchOutcome>) {
  val outcomes: Map<Long, RefetchOutcome> = LinkedHashMap<Long, RefetchOutcome>().also { out ->
    for (start in requested) out[start] = read[start] ?: RefetchOutcome.GONE
  }

  fun count(outcome: RefetchOutcome) = outcomes.values.count { it == outcome }

  fun toJs(): Map<String, Any?> = mapOf(
    "requested" to outcomes.size,
    "updated" to count(RefetchOutcome.UPDATED),
    "unchanged" to count(RefetchOutcome.UNCHANGED),
    "noDynamics" to count(RefetchOutcome.NO_DYNAMICS),
    "gone" to count(RefetchOutcome.GONE),
    "failed" to count(RefetchOutcome.FAILED),
    "results" to outcomes.map { (start, outcome) -> mapOf("start" to start, "outcome" to outcome.name.lowercase()) },
  )
}

/**
 * Port of HuaweiWorkoutSyncManager. GB drives callbacks through a request queue; here each step is a suspend
 * call with the same order and the same retry rules:
 *
 *  1. WorkoutCount (17/07) for the window. An error answer or a count that does not match the list asks twice
 *     for each half of the window instead (at most [MAX_SMALLER_TIMESLOT_DEPTH] levels deep).
 *  2. Per workout, in workout number order: Totals (17/08), Data (17/0A), Pace (17/0C), SwimSegments (17/0E),
 *     SpO2 (17/14), Sections (17/16), then the GPS file (`<n>_gps.bin`, service 2C or 0A).
 *  3. Every request that can answer with an error is repeated up to [MAX_RETRIES] times, then skipped and the
 *     workout is flagged `missingData` (GB shows a toast).
 *  4. A part request (data, pace, swim, SpO2, sections, GPS file) that gets no answer at all is a lost frame, not a dead link:
 *     over LE on Android 14+ the stack discards a 514-byte notification, so a reply longer than 512 bytes (the Band 11 Pro's
 *     first WorkoutData frame, JRG-337) never arrives. The frame is skipped without a retry (the watch would send the same
 *     length again), the workout is flagged `missingData`, and [MAX_LOST_FRAMES] lost frames in a row end that part. When
 *     any frame was lost a cheap request (the totals again) tells a dead link from a lost frame: no answer means the link is
 *     gone and the timeout aborts the sync as in GB, the workout kept as incomplete.
 *
 * Unlike GB the workout is not written to the database until its parts are downloaded, and the sync keeps going
 * after a workout whose totals could not be read. A workout cut short by a timeout is stored as incomplete and
 * fetched again next sync.
 */
internal class WorkoutSync(
  private val session: HuaweiSession,
  private val events: WearEvents,
  private val requestTimeoutMs: Long = WORKOUT_MS,
) {
  private val params get() = session.params
  private val downloader = FileDownloader(session, events)

  /** Workouts newly stored (or completed) by [sync] so far. */
  var saved = 0
    private set

  private class Range(val start: Int, val end: Int, val depth: Int)

  suspend fun sync(startSec: Int, endSec: Int, sink: WorkoutSink) {
    if (!serviceOffered()) return
    val newSteps = queryNewSteps()
    log("Workout: from=$startSec to=$endSec newSteps=$newSteps")
    listWorkouts(startSec, endSec) { numbers ->
      for (n in numbers) syncWorkout(n, newSteps, sink)
    }
  }

  /**
   * Reads only the data samples (17/0A) of the stored workouts in [targets] again and hands the running form in them to [sink], which
   * leaves everything else of the stored workout alone. A workout is found with a WorkoutCount (17/07) over its own time (the watch
   * keeps the list of what it holds; one that rolled off is not in it), then the watch's totals (17/08) must say the same start, so a
   * recycled workout number never merges another run's form. Nothing but the dynamics is downloaded: no pace, sections, SpO2 or GPS.
   * A workout whose frames did not all arrive is left as it was ([RefetchOutcome.FAILED]): a series built from a gap would start in
   * the wrong minute. A lost link throws, as in [sync].
   */
  suspend fun refetchDynamics(targets: List<RefetchTarget>, sink: DynamicsSink): Map<Long, RefetchOutcome> {
    val outcomes = LinkedHashMap<Long, RefetchOutcome>()
    if (!serviceOffered()) {
      for (t in targets) outcomes[t.start] = RefetchOutcome.GONE
      return outcomes
    }
    val newSteps = queryNewSteps()
    for (target in targets) {
      outcomes[target.start] = refetchOne(target, newSteps, sink)
    }
    return outcomes
  }

  private suspend fun refetchOne(target: RefetchTarget, newSteps: Boolean, sink: DynamicsSink): RefetchOutcome {
    val from = (target.start - REFETCH_SLACK_SEC).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    val to = (target.end + REFETCH_SLACK_SEC).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    var unreadable = false
    var match: Pair<Workout.WorkoutCount.Response.WorkoutNumbers, Workout.WorkoutTotals.Response>? = null
    try {
      listWorkouts(from, to) { listed ->
        // The stored number first: nearly always the one, so the totals of the others are not asked for.
        for (n in listed.sortedByDescending { it.workoutNumber.toInt() == target.number }) {
          if (match != null) return@listWorkouts
          val totals = readTotals(n)
          if (totals == null) {
            unreadable = true
            continue
          }
          if ((totals.startTime.toLong() and 0xFFFFFFFFL) == target.start) match = n to totals
        }
      }
    } catch (e: WearException) {
      // A watch that answers the count with errors all the way down ends here; a lost link is not this and still throws.
      if (e.errorCode != ErrorCodes.PROTOCOL) throw e
      log("refetch ${target.start}: ${e.message}")
      return RefetchOutcome.FAILED
    }
    val (numbers, totals) = match ?: return if (unreadable) RefetchOutcome.FAILED else RefetchOutcome.GONE

    val parts = WorkoutParts(totals)
    parts.newSteps = newSteps
    val loss = FrameLoss()
    downloadData(numbers, parts, loss)
    if (loss.lost > 0) confirmLinkAlive(numbers.workoutNumber)
    if (parts.missingData) {
      log("refetch ${target.start}: some data frames did not arrive, the stored workout is left as it was")
      return RefetchOutcome.FAILED
    }
    val dynamics = RunDynamics.of(parts.samples)
    if (dynamics == null) {
      log("refetch ${target.start}: ${parts.samples.size} samples, no running form in them")
      return RefetchOutcome.NO_DYNAMICS
    }
    return when (sink.merge(target.start, dynamics)) {
      DynamicsMerge.UPDATED -> RefetchOutcome.UPDATED
      DynamicsMerge.UNCHANGED -> RefetchOutcome.UNCHANGED
      DynamicsMerge.NOT_STORED -> RefetchOutcome.FAILED
    }
  }

  private fun serviceOffered(): Boolean {
    val supportedServices = DeviceCaps.supportedCommands
    if (supportedServices.isNotEmpty() && supportedServices[SERVICE] == null) {
      log("Workout: service 0x17 not offered by the watch, workout sync skipped")
      return false
    }
    log(
      "Workout gates: 17/07=${DeviceCaps.supports(SERVICE, 0x07)} 17/08=${DeviceCaps.supports(SERVICE, 0x08)} " +
        "17/0A=${DeviceCaps.supports(SERVICE, 0x0A)} 17/15=${DeviceCaps.supports(SERVICE, 0x15)} " +
        "gpsNewSync(SettingRelated)=${DeviceCaps.gpsNewSync} " +
        "commands17=[${supportedServices[SERVICE]?.joinToString(" ") { it.hex2() } ?: "none"}]",
    )
    return true
  }

  /**
   * WorkoutCount (17/07) over a window, as the sync asks it: an error answer or a count that does not match the list asks twice for
   * each half of the window instead (at most [MAX_SMALLER_TIMESLOT_DEPTH] levels deep). [onRange] gets the listed workouts of every
   * range that answered, sorted by workout number (GB: the timestamp based sync state depends on it).
   */
  private suspend fun listWorkouts(startSec: Int, endSec: Int, onRange: suspend (List<Workout.WorkoutCount.Response.WorkoutNumbers>) -> Unit) {
    val queue = ArrayDeque<Range>()
    queue.add(Range(startSec, endSec, 0))
    while (queue.isNotEmpty()) {
      val range = queue.removeFirst()
      val count = requestOrNull<Workout.WorkoutCount.Response>(
        Workout.WorkoutCount.Request(params, range.start, range.end),
        "WorkoutCount ${range.start}..${range.end}",
      )
      if (count == null) {
        // The Fit 4 can leave out the 0x81 container like it does for the step counts: no workouts.
        log("Workout: no count in the answer for ${range.start}..${range.end}; nothing to sync")
        continue
      }
      val listed = count.workoutNumbers
      if (count.error != null) {
        log("Workout: error ${count.error} on the count for ${range.start}..${range.end}")
        split(queue, range)
        continue
      }
      if (count.count.toInt() == 0 || listed == null) {
        log("Workout: 0 workouts in ${range.start}..${range.end}")
        continue
      }
      if (count.count.toInt() != listed.size) {
        log("Workout: count ${count.count} does not match ${listed.size} entries in ${range.start}..${range.end}")
        split(queue, range)
        continue
      }

      val numbers = listed.sortedBy { it.workoutNumber.toInt() }
      log("Workout: ${numbers.size} workouts in ${range.start}..${range.end}: ${numbers.joinToString { describe(it) }}")
      onRange(numbers)
    }
  }

  private fun split(queue: ArrayDeque<Range>, range: Range) {
    if (range.depth >= MAX_SMALLER_TIMESLOT_DEPTH) {
      throw WearException(ErrorCodes.PROTOCOL, "Workout: asked for a smaller timeslot too many times, giving up on ${range.start}..${range.end}")
    }
    val half = range.start + (range.end - range.start) / 2
    log("Workout: retrying ${range.start}..${range.end} as ${range.start}..$half and ${half + 1}..${range.end}")
    queue.add(Range(range.start, half, range.depth + 1))
    queue.add(Range(half + 1, range.end, range.depth + 1))
  }

  /** GB GetWorkoutCapability: ExpandCapability-free gate is the command itself (17/15). */
  private suspend fun queryNewSteps(): Boolean {
    if (!DeviceCaps.supports(SERVICE, 0x15)) return false
    val response = requestOrNull<Workout.WorkoutCapability.Response>(Workout.WorkoutCapability.Request(params), "WorkoutCapability")
    log("Workout capability: NewSteps: ${response?.supportNewStep}")
    return response?.supportNewStep == true
  }

  /** The watch's totals (17/08) of one listed workout, repeated up to [MAX_RETRIES] times on an error answer; null when it never answered usably. */
  private suspend fun readTotals(numbers: Workout.WorkoutCount.Response.WorkoutNumbers): Workout.WorkoutTotals.Response? {
    val wn = numbers.workoutNumber
    val label = "workout ${wn.toInt()}"
    return retrying("WorkoutTotals ${wn.toInt()}") { attempt ->
      val r = requestOrNull<Workout.WorkoutTotals.Response>(Workout.WorkoutTotals.Request(params, wn), "WorkoutTotals ${wn.toInt()}")
      when {
        r == null -> null
        r.error != null -> { log("$label totals: error ${r.error} (attempt $attempt)"); null }
        r.number != wn -> { log("$label totals: incorrect workout number ${r.number}"); null }
        else -> r
      }
    }
  }

  private suspend fun syncWorkout(numbers: Workout.WorkoutCount.Response.WorkoutNumbers, newSteps: Boolean, sink: WorkoutSink) {
    val wn = numbers.workoutNumber
    val label = "workout ${wn.toInt()}"
    val totals = readTotals(numbers)
    if (totals == null) {
      log("$label: totals failed after $MAX_RETRIES retries, moving to the next workout")
      return
    }
    log(
      "$label totals: status=${totals.status} start=${totals.startTime} end=${totals.endTime} calories=${totals.calories} " +
        "distance=${totals.distance} steps=${totals.stepCount} time=${totals.totalTime} duration=${totals.duration} type=${totals.type.toInt() and 0xFF}",
    )
    val start = totals.startTime.toLong() and 0xFFFFFFFFL
    val end = totals.endTime.toLong() and 0xFFFFFFFFL
    if (sink.isComplete(start, end)) {
      log("$label: already stored (start=$start), skipped")
      return
    }

    val parts = WorkoutParts(totals)
    parts.newSteps = newSteps
    var complete = false
    try {
      downloadParts(numbers, parts)
      complete = true
    } finally {
      // A timeout or lost link keeps what was downloaded, flagged incomplete so the next sync retries.
      val record = WorkoutBuilder.build(parts, complete)
      val isNew = withContext(NonCancellable) { sink.save(record) }
      if (isNew) saved++
      log(
        "$label: stored ${if (complete) "complete" else "incomplete"} ${record.sportName} " +
          "${record.start}..${record.end} dur=${record.duration}s dist=${record.distance}m avgHr=${record.avgHr} maxHr=${record.maxHr} " +
          "points=${record.points.size} (data=${parts.samples.size} gps=${parts.gps.size} gpsStatus=${parts.gpsStatus}) missingData=${parts.missingData}",
      )
    }
  }

  /** Frames of one workout the watch left unanswered, and how many in a row within the part being downloaded. */
  private class FrameLoss {
    var lost = 0
    var inRow = 0
    val stalled get() = inRow >= MAX_LOST_FRAMES

    fun nextPart() { inRow = 0 }
  }

  /**
   * Runs one part request; a timeout is a lost frame ([FrameLoss]) and gives null, which callers treat like a refused part.
   * Any other failure (a lost link) still throws.
   */
  private suspend fun <T> tolerant(label: String, loss: FrameLoss, parts: WorkoutParts, block: suspend () -> T?): T? =
    try {
      block().also { loss.inRow = 0 }
    } catch (e: WearException) {
      if (e.errorCode != ErrorCodes.TIMEOUT) throw e
      loss.lost++
      loss.inRow++
      parts.missingData = true
      log("$label: ${e.message}; skipping this frame")
      null
    }

  private fun stalled(loss: FrameLoss, kind: String): Boolean {
    if (!loss.stalled) return false
    log("$kind: $MAX_LOST_FRAMES frames in a row got no answer; leaving out the rest of it")
    return true
  }

  /** The data samples (17/0A) of one workout, frame by frame; a frame the watch refused or never answered flags `missingData`. */
  private suspend fun downloadData(numbers: Workout.WorkoutCount.Response.WorkoutNumbers, parts: WorkoutParts, loss: FrameLoss) {
    val wn = numbers.workoutNumber
    val label = "workout ${wn.toInt()}"
    for (i in 0 until numbers.dataCount) {
      if (stalled(loss, "$label data")) break
      val l = "WorkoutData ${wn.toInt()}/$i"
      val r = tolerant(l, loss, parts) {
        retrying(l) { attempt ->
          val r = requestOrNull<Workout.WorkoutData.Response>(Workout.WorkoutData.Request(params, wn, i.toShort(), parts.newSteps), l)
          when {
            r == null -> null
            r.error != null -> { log("$l: error ${r.error} (attempt $attempt)"); null }
            r.workoutNumber != wn -> { log("$l: incorrect workout number ${r.workoutNumber}"); null }
            r.dataNumber.toInt() != i -> { log("$l: incorrect data number ${r.dataNumber}"); null }
            else -> r
          }
        }
      }
      if (r == null) { if (loss.inRow == 0) skipped(parts, l); continue }
      log("$l: ${r.dataList.size} samples, interval ${r.header.interval}s, bitmap ${r.header.bitmap}, inner bitmap ${r.innerBitmap}")
      parts.samples.addAll(r.dataList)
    }
  }

  private suspend fun downloadParts(numbers: Workout.WorkoutCount.Response.WorkoutNumbers, parts: WorkoutParts) {
    val wn = numbers.workoutNumber
    val label = "workout ${wn.toInt()}"
    val loss = FrameLoss()

    downloadData(numbers, parts, loss)
    loss.nextPart()

    for (i in 0 until numbers.paceCount) {
      if (stalled(loss, "$label pace")) break
      val l = "WorkoutPace ${wn.toInt()}/$i"
      val r = tolerant(l, loss, parts) {
        retrying(l) { attempt ->
          val r = requestOrNull<Workout.WorkoutPace.Response>(Workout.WorkoutPace.Request(params, wn, i.toShort()), l)
          when {
            r == null -> null
            r.error != null -> { log("$l: error ${r.error} (attempt $attempt)"); null }
            r.workoutNumber != wn -> { log("$l: incorrect workout number ${r.workoutNumber}"); null }
            r.paceNumber.toInt() != i -> { log("$l: incorrect pace number ${r.paceNumber}"); null }
            else -> r
          }
        }
      }
      if (r == null) { if (loss.inRow == 0) skipped(parts, l); continue }
      log("$l: ${r.blocks.size} blocks")
      parts.paceBlocks.addAll(r.blocks)
    }

    loss.nextPart()
    for (i in 0 until numbers.segmentsCount) {
      if (stalled(loss, "$label swim segments")) break
      val l = "WorkoutSwimSegments ${wn.toInt()}/$i"
      val r = tolerant(l, loss, parts) {
        retrying(l) { attempt ->
          val r = requestOrNull<Workout.WorkoutSwimSegments.Response>(Workout.WorkoutSwimSegments.Request(params, wn, i.toShort()), l)
          when {
            r == null -> null
            r.error != null -> { log("$l: error ${r.error} (attempt $attempt)"); null }
            r.workoutNumber != wn -> { log("$l: incorrect workout number ${r.workoutNumber}"); null }
            r.segmentNumber.toInt() != i -> { log("$l: incorrect swim segment number ${r.segmentNumber}"); null }
            else -> r
          }
        }
      }
      if (r == null) { if (loss.inRow == 0) skipped(parts, l); continue }
      log("$l: ${r.blocks.size} blocks")
      parts.swimBlocks.addAll(r.blocks)
    }

    loss.nextPart()
    for (i in 0 until numbers.spO2Count) {
      if (stalled(loss, "$label SpO2")) break
      val l = "WorkoutSpO2 ${wn.toInt()}/$i"
      val r = tolerant(l, loss, parts) {
        retrying(l) { attempt ->
          val r = requestOrNull<Workout.WorkoutSpO2.Response>(Workout.WorkoutSpO2.Request(params, wn, i.toShort()), l)
          when {
            r == null -> null
            r.error != null -> { log("$l: error ${r.error} (attempt $attempt)"); null }
            else -> r
          }
        }
      }
      if (r == null) { if (loss.inRow == 0) skipped(parts, l); continue }
      log("$l: ${r.blocks.size} blocks")
      parts.spo2Blocks.addAll(r.blocks)
    }

    loss.nextPart()
    for (i in 0 until numbers.sectionsCount) {
      if (stalled(loss, "$label sections")) break
      val l = "WorkoutSections ${wn.toInt()}/$i"
      var normalError = false
      val r = tolerant(l, loss, parts) {
        retrying(l) { attempt ->
          val r = requestOrNull<Workout.WorkoutSections.Response>(Workout.WorkoutSections.Request(params, wn, i.toShort()), l)
          when {
            r == null -> null
            // GB: 0x0001E079 "seems to be normal", ignored without retries and without flagging missing data.
            r.error == SECTIONS_NORMAL_ERROR -> { normalError = true; log("$l: error 0001E079, ignored (normal)"); r }
            r.error != null -> { log("$l: error ${r.error} (attempt $attempt)"); null }
            else -> r
          }
        }
      }
      if (r == null) { if (loss.inRow == 0) skipped(parts, l); continue }
      if (normalError) continue
      log("$l: ${r.blocks.size} blocks")
      parts.sectionBlocks.addAll(r.blocks)
    }

    downloadGps(numbers, parts, loss)
    if (loss.lost > 0) confirmLinkAlive(wn)
    log("$label: downloaded, missingData=${parts.missingData}")
  }

  /** GB finishSingleWorkoutSync -> downloadWorkoutGpsFiles. */
  private suspend fun downloadGps(numbers: Workout.WorkoutCount.Response.WorkoutNumbers, parts: WorkoutParts, loss: FrameLoss) {
    val wn = numbers.workoutNumber
    val label = "workout ${wn.toInt()} GPS"
    // WorkoutCount tag 0x0e: 1 when the watch has a _gps.bin/_pdr.bin for the workout. GB ignores it (older
    // firmware does not send it); here a watch that says 0 is believed, one that sends nothing is asked.
    val reported = try {
      HuaweiTLV().parse(numbers.rawData).contains(0x0e)
    } catch (e: Exception) {
      false
    }
    log("$label: trajectoriesData=${numbers.trajectoriesData} (reported=$reported)")
    if (reported && numbers.trajectoriesData.toInt() == 0) {
      log("$label: watch reports no trajectory data, GPS download skipped")
      return
    }

    val data = try {
      downloader.download(FileRequest.workoutGps(DeviceCaps.gpsNewSync, wn))
    } catch (e: CancellationException) {
      throw e
    } catch (e: WearException) {
      if (e.errorCode == ErrorCodes.TIMEOUT) {
        // A file block longer than 512 bytes never arrives over LE on Android 14+; the rest of the workout stays.
        loss.lost++
        parts.missingData = true
        parts.gpsStatus = "failed"
        log("$label: ${e.message}; GPS file left out")
        return
      }
      // A watch without a GPS file for an indoor workout answers the init with an error; that is not a failure.
      if (e.errorCode != ErrorCodes.PROTOCOL) throw e
      log("$label: no GPS file (${e.message})")
      parts.gpsStatus = "failed"
      return
    }
    if (data.isEmpty()) {
      log("$label: GPS file empty")
      parts.gpsStatus = "empty"
      return
    }
    val points = try {
      HuaweiGpsParser.parseHuaweiGps(data)
    } catch (e: Exception) {
      log("$label: GPS file of ${data.size} bytes could not be parsed (${e.javaClass.simpleName}: ${e.message})")
      parts.gpsStatus = "failed"
      return
    }
    // The file parser does no range checks; a garbled file must not put NaN or off-planet points in the table.
    val valid = points.filter { it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    log("$label: received ${points.size} GPS points, ${valid.size} valid")
    if (valid.isEmpty()) {
      parts.gpsStatus = "empty"
      return
    }
    parts.gpsStatus = "ok"
    parts.gps.addAll(valid)
  }

  private fun skipped(parts: WorkoutParts, label: String) {
    log("$label: max tries exceeded, moving to the next part")
    parts.missingData = true
  }

  /** GB: up to [MAX_RETRIES] repeats of a request that answered with an error, then null. */
  private suspend fun <T> retrying(label: String, block: suspend (attempt: Int) -> T?): T? {
    var attempt = 0
    while (true) {
      block(attempt)?.let { return it }
      if (attempt >= MAX_RETRIES) return null
      attempt++
      log("$label: retry $attempt/$MAX_RETRIES")
    }
  }

  /**
   * After lost frames: the watch must still answer a request whose reply is short (the totals of the same workout). If it does,
   * the frames were lost one by one and the workout is stored with what arrived; if it does not, the timeout is thrown so the
   * workout is stored as incomplete and the next sync tries again.
   */
  private suspend fun confirmLinkAlive(wn: Short) {
    val l = "WorkoutTotals ${wn.toInt()} (link check)"
    requestOrNull<Workout.WorkoutTotals.Response>(Workout.WorkoutTotals.Request(params, wn), l)
    log("$l: answered, the lost frames were single replies")
  }

  /**
   * Sends [packet] and returns the typed answer, or null when the answer cannot be used (it did not parse or is
   * of another type: the watch answers some workout requests without the expected container). A timeout or a
   * lost link still throws.
   */
  private suspend inline fun <reified T : HuaweiPacket> requestOrNull(packet: HuaweiPacket, label: String): T? =
    try {
      session.request(packet, requestTimeoutMs, label).expect<T>(label)
    } catch (e: WearException) {
      if (e.errorCode != ErrorCodes.PROTOCOL) throw e
      log("$label: unusable answer (${e.message})")
      null
    }

  private fun describe(n: Workout.WorkoutCount.Response.WorkoutNumbers) =
    "#${n.workoutNumber.toInt()}(data=${n.dataCount} pace=${n.paceCount} swim=${n.segmentsCount} spo2=${n.spO2Count} " +
      "sections=${n.sectionsCount} trajectories=${n.trajectoriesData})"

  private fun log(message: String) = events.log(LogDirection.INFO, null, null, null, message)

  private companion object {
    const val SERVICE = 0x17
    const val MAX_RETRIES = 3
    const val MAX_SMALLER_TIMESLOT_DEPTH = 5
    const val SECTIONS_NORMAL_ERROR = 0x0001E079

    /** GB uses 5 s for every workout request; the Fit 4 answers the data requests slowly, so more room here. */
    const val WORKOUT_MS = 10_000L

    /** Lost frames in a row after which the rest of that part of a workout is left out. */
    const val MAX_LOST_FRAMES = 3

    /** A refetch lists the workouts of its stored start..end padded by this much, whether the watch filters the count by start or by end. */
    const val REFETCH_SLACK_SEC = 60L
  }
}
