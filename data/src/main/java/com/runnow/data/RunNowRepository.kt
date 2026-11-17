package com.runnow.data

import android.content.Context
import com.joyodigitama.huaweiwear.WatchData
import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.Profile
import com.runnow.domain.Snapshot
import com.runnow.domain.StreamData
import com.runnow.domain.StreamSummary
import com.runnow.domain.Wellness
import com.runnow.domain.summarizeStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class SyncReport(val source: String, val received: Int, val addedOrUpdated: Int, val duplicatesCollapsed: Int, val syncedAtMs: Long)

data class RepositorySnapshot(
    val snapshot: Snapshot,
    val streams: Map<String, StreamSummary>,
    val watchAvailable: Boolean,
    val watchError: String?,
    val workoutCount: Int,
    val wellnessDays: Int,
    val lastWatchSyncSec: Long?,
    val lastIntervalsSyncMs: Long?,
)

class RunNowRepository(context: Context) {
    private val app = context.applicationContext
    private val watch = WatchData(app)
    private val store = RunNowLocalStore(app)

    suspend fun load(
        profile: Profile = Profile(),
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        existing: List<Act> = store.readActivities(),
        existingWellness: List<Wellness> = store.readWellness(),
        existingStreams: Map<String, StreamSummary> = store.readStreams(),
    ): RepositorySnapshot = withContext(Dispatchers.IO) {
        val workouts = watch.workouts(500).filter { it.complete }
        val watchActs = workouts.mapNotNull { w ->
            if (w.durationSec <= 0 || w.startSec <= 0) return@mapNotNull null
            val kind = when {
                w.sportType in setOf(1, 5, 11) || w.sportName.contains("run", true) || w.sportName.contains("lari", true) -> ActKind.RUN
                w.sportType in setOf(2, 13, 14) || w.sportName.contains("walk", true) || w.sportName.contains("jalan", true) -> ActKind.WALK
                else -> ActKind.OTHER
            }
            if (kind == ActKind.RUN && w.distanceM <= 0) return@mapNotNull null
            Act(
                start = w.startSec * 1000L,
                kind = kind,
                min = w.durationSec / 60.0,
                km = w.distanceM / 1000.0,
                avgHr = (w.avgHr ?: 0).toDouble(),
                elev = 0.0,
                lid = "hw${w.id}",
                name = w.sportName.ifBlank { "Aktivitas jam" },
                maxHr = (w.maxHr ?: 0).toDouble(),
                icuId = "hw${w.id}",
                load = 0.0,
            )
        }
        val merged = mergeActivities(existing, watchActs, existingStreams)

        val wellnessByDate = existingWellness.associateBy { it.id }.toMutableMap()
        watch.days(365).forEach { d ->
            val old = wellnessByDate[d.date]
            wellnessByDate[d.date] = (old ?: Wellness(d.date)).copy(
                restingHR = d.restingHr?.toDouble()?.takeIf { it > 30 } ?: old?.restingHR ?: 0.0,
                rhrSrc = if ((d.restingHr ?: 0) > 30) "jam" else old?.rhrSrc.orEmpty(),
                steps = d.steps.toDouble().takeIf { it > 0 } ?: old?.steps ?: 0.0,
                spO2 = d.spo2Avg?.toDouble()?.takeIf { it > 0 } ?: old?.spO2 ?: 0.0,
            )
        }
        watch.sleepNights(365).forEach { n ->
            val date = Instant.ofEpochSecond(n.startSec).atZone(zone).toLocalDate().toString()
            val old = wellnessByDate[date] ?: Wellness(date)
            wellnessByDate[date] = old.copy(
                sleepSecs = n.asleepSec.toDouble().takeIf { it > 0 } ?: old.sleepSecs,
                sleepScore = n.score?.toDouble()?.takeIf { it > 0 } ?: old.sleepScore,
            )
        }
        watch.hrv(365).forEach { h ->
            val date = Instant.ofEpochSecond(h.endSec).atZone(zone).toLocalDate().toString()
            val old = wellnessByDate[date] ?: Wellness(date)
            // Keep nightly RMSSD as primary; spot values remain marked and do not replace a nightly value.
            if (h.source != "spot" || old.hrv <= 0) {
                wellnessByDate[date] = old.copy(
                    hrv = h.rmssd,
                    hrvSrc = "jam",
                    hrvSpot = h.source == "spot",
                )
            }
        }

        val streamMap = merged.streams.toMutableMap()
        workouts.forEach { w ->
            val id = "hw${w.id}"
            if (streamMap.containsKey(id) || w.sportType !in setOf(1, 5, 11)) return@forEach
            val pts = watch.workoutPoints(w.id)
            if (pts.size < 40) {
                streamMap[id] = StreamSummary.NONE
                return@forEach
            }
            val time = mutableListOf<Double>()
            val dist = mutableListOf<Double>()
            val hr = mutableListOf<Double>()
            var meters = 0.0
            var previous: com.joyodigitama.huaweiwear.WatchWorkoutPoint? = null
            pts.forEach pointLoop@{ p ->
                val prev = previous
                if (prev != null) {
                    val dt = p.tsSec - prev.tsSec
                    if (dt <= 0) return@pointLoop
                    val bySpeed = p.speed?.takeIf { it > 0 }?.times(dt)
                    val byGps = if (prev.lat != null && prev.lon != null && p.lat != null && p.lon != null) {
                        val lat1 = Math.toRadians(prev.lat)
                        val lat2 = Math.toRadians(p.lat)
                        val dLat = lat2 - lat1
                        val dLon = Math.toRadians(p.lon - prev.lon)
                        val h = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                            kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
                        (6371000.0 * 2 * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))).takeIf { it < 100.0 }
                    } else null
                    meters += (bySpeed ?: byGps ?: 0.0).coerceAtLeast(0.0)
                }
                time += p.tsSec.toDouble()
                dist += meters
                hr += (p.hr ?: 0).toDouble()
                previous = p
            }
            streamMap[id] = summarizeStream(StreamData(time, dist, hr.takeIf { values -> values.any { it > 0 } }), minN = 40) ?: StreamSummary.NONE
        }

        val wellness = wellnessByDate.values.sortedBy { it.id }.takeLast(400)
        val snapshot = Snapshot(
            profile = profile,
            acts = merged.activities,
            wellness = wellness,
            nowMs = nowMs,
            zone = zone,
            streams = streamMap,
        )
        store.write(merged.activities, wellness, streamMap)
        RepositorySnapshot(
            snapshot = snapshot,
            streams = streamMap,
            watchAvailable = watch.available(),
            watchError = watch.lastError,
            workoutCount = workouts.size,
            wellnessDays = wellness.size,
            lastWatchSyncSec = watch.overview().devices.mapNotNull { it.lastSyncSec }.maxOrNull(),
            lastIntervalsSyncMs = store.intervalsSyncedAtMs().takeIf { it > 0 },
        )
    }
    /** Tarik ulang database jam; upsert ID membuat sinkron berulang tidak menumpuk aktivitas. */
    suspend fun syncWatch(profile: Profile = Profile(), zone: ZoneId = ZoneId.systemDefault()): Pair<RepositorySnapshot, SyncReport> {
        val before = store.readActivities()
        val snapshot = load(profile, zone = zone)
        val now = System.currentTimeMillis()
        val added = (snapshot.snapshot.acts.size - before.size).coerceAtLeast(0)
        store.write(snapshot.snapshot.acts, snapshot.snapshot.wellness, snapshot.streams, watchSyncedAtMs = now)
        return snapshot to SyncReport("Huawei", snapshot.workoutCount, added, 0, now)
    }

    /** Sinkron Intervals.icu: bootstrap 400 hari, berikutnya 100 hari; nilai wellness dari jam tetap prioritas. */
    suspend fun syncIntervals(apiKey: String, profile: Profile = Profile(), zone: ZoneId = ZoneId.systemDefault()): Pair<RepositorySnapshot, SyncReport> {
        require(apiKey.isNotBlank()) { "API key kosong" }
        val existing = store.readActivities()
        val from = java.time.LocalDate.now(zone).minusDays(if (existing.isEmpty()) 400 else 100)
        val result = IntervalsIcuClient(apiKey).sync(from, java.time.LocalDate.now(zone).plusDays(1))
        val merged = mergeActivities(existing, result.activities, store.readStreams())
        val wellnessMap = store.readWellness().associateBy { it.id }.toMutableMap()
        result.wellness.forEach { incoming ->
            val old = wellnessMap[incoming.id] ?: Wellness(incoming.id)
            wellnessMap[incoming.id] = old.copy(
                ctl = incoming.ctl ?: old.ctl, atl = incoming.atl ?: old.atl, tsb = incoming.tsb ?: old.tsb,
                restingHR = if (old.rhrSrc == "jam" || incoming.restingHR <= 30) old.restingHR else incoming.restingHR,
                rhrSrc = if (old.rhrSrc == "jam") old.rhrSrc else incoming.rhrSrc,
                hrv = if (old.hrvSrc == "jam" || incoming.hrv <= 0) old.hrv else incoming.hrv,
                hrvSrc = if (old.hrvSrc == "jam") old.hrvSrc else incoming.hrvSrc,
            )
        }
        val wellness = wellnessMap.values.sortedBy { it.id }.takeLast(400)
        store.write(merged.activities, wellness, merged.streams, intervalsSyncedAtMs = result.syncedAtMs)
        val snapshot = load(profile, zone = zone, existing = merged.activities, existingWellness = wellness, existingStreams = merged.streams)
        return snapshot to SyncReport("Intervals.icu", result.activities.size, merged.addedOrUpdated, merged.collapsedWatchDuplicates, result.syncedAtMs)
    }

    fun clearLocalData() = store.clear()

}
