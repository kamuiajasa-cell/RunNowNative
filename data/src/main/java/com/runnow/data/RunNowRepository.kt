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
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RepositorySnapshot(
    val snapshot: Snapshot,
    val streams: Map<String, StreamSummary>,
    val watchAvailable: Boolean,
    val watchError: String?,
    val workoutCount: Int,
    val wellnessDays: Int,
    val lastWatchSyncSec: Long?,
)

class RunNowRepository(context: Context) {
    private val app = context.applicationContext
    private val watch = WatchData(app)

    suspend fun load(
        profile: Profile = Profile(),
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        existing: List<Act> = emptyList(),
        existingWellness: List<Wellness> = emptyList(),
        existingStreams: Map<String, StreamSummary> = emptyMap(),
    ): RepositorySnapshot = withContext(Dispatchers.IO) {
        val workouts = watch.workouts(500)
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
            pts.forEach { p ->
                if (time.isNotEmpty()) {
                    val dt = (p.tsSec - time.last()).takeIf { it > 0 } ?: return@forEach
                    meters += (p.speed ?: 0.0).coerceAtLeast(0.0) * dt
                }
                time += p.tsSec.toDouble()
                dist += meters
                hr += (p.hr ?: 0).toDouble()
            }
            streamMap[id] = summarizeStream(StreamData(time, dist, hr), minN = 40) ?: StreamSummary.NONE
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
        RepositorySnapshot(
            snapshot = snapshot,
            streams = streamMap,
            watchAvailable = watch.available(),
            watchError = watch.lastError,
            workoutCount = workouts.size,
            wellnessDays = wellness.size,
            lastWatchSyncSec = watch.overview().lastSampleSec,
        )
    }
}
