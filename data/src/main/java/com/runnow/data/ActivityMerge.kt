package com.runnow.data

import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.StreamSummary

data class ActivityMergeResult(
    val activities: List<Act>,
    val streams: Map<String, StreamSummary>,
    val addedOrUpdated: Int,
    val collapsedWatchDuplicates: Int,
)

/**
 * Merge policy mirrors runsense.html:
 * - stable source id replaces an existing row, never appends a duplicate;
 * - Intervals.icu wins over Huawei for the same activity within 12 minutes and same kind;
 * - stream summaries and manually confirmed type metadata follow the surviving Intervals id.
 */
fun mergeActivities(
    existing: List<Act>,
    incoming: List<Act>,
    existingStreams: Map<String, StreamSummary> = emptyMap(),
): ActivityMergeResult {
    val rows = existing.toMutableList()
    val streams = existingStreams.toMutableMap()
    var changed = 0

    incoming.forEach { next ->
        val id = next.icuId
        val atId = if (id != null) rows.indexOfFirst { it.icuId == id } else -1
        if (atId >= 0) {
            if (rows[atId] != next) {
                rows[atId] = next
                changed++
            }
        } else {
            rows += next
            changed++
        }
    }

    fun isHuawei(a: Act) = a.icuId?.startsWith("hw", ignoreCase = true) == true
    var collapsed = 0
    val icuRows = rows.filter { it.icuId != null && !isHuawei(it) }
    val kept = rows.filterNot { watch ->
        if (!isHuawei(watch)) return@filterNot false
        val match = icuRows.firstOrNull {
            it.kind == watch.kind && kotlin.math.abs(it.start - watch.start) <= 12 * 60_000L
        } ?: return@filterNot false
        val hwId = watch.icuId.orEmpty()
        val icuId = match.icuId.orEmpty()
        if (hwId.isNotEmpty() && streams[hwId] != null && streams[icuId] == null) streams[icuId] = streams[hwId]!!
        streams.remove(hwId)
        collapsed++
        true
    }

    val sorted = kept.sortedBy { it.start }.takeLast(500)
    val activeIds = sorted.mapNotNull { it.icuId }.toSet()
    streams.keys.retainAll(activeIds)
    return ActivityMergeResult(sorted, streams, changed, collapsed)
}
