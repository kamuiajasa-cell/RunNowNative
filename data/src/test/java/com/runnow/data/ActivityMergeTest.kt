package com.runnow.data

import com.runnow.domain.Act
import com.runnow.domain.ActKind
import com.runnow.domain.StreamFast
import com.runnow.domain.StreamSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityMergeTest {
    private fun act(start: Long, id: String, kind: ActKind = ActKind.RUN, km: Double = 5.0) = Act(
        start = start, kind = kind, min = 30.0, km = km, lid = id, icuId = id,
    )

    @Test fun repeatedWatchImportDoesNotDuplicateStableId() {
        val first = act(1_000_000L, "hw42")
        val once = mergeActivities(emptyList(), listOf(first))
        val twice = mergeActivities(once.activities, listOf(first), once.streams)
        assertEquals(1, twice.activities.size)
        assertEquals(0, twice.addedOrUpdated)
    }

    @Test fun intervalsRecordReplacesHuaweiTwinWithinTwelveMinutes() {
        val watch = act(1_000_000L, "hw42")
        val intervals = act(1_600_000L, "icu-99", km = 5.1)
        val result = mergeActivities(listOf(watch), listOf(intervals))
        assertEquals(1, result.activities.size)
        assertEquals("icu-99", result.activities.single().icuId)
        assertEquals(1, result.collapsedWatchDuplicates)
    }

    @Test fun unrelatedActivitiesAreNotCollapsed() {
        val watch = act(1_000_000L, "hw42")
        val otherTime = act(1_800_001L, "icu-99")
        val otherKind = act(1_100_000L, "icu-100", ActKind.WALK)
        val result = mergeActivities(listOf(watch), listOf(otherTime, otherKind))
        assertEquals(3, result.activities.size)
        assertEquals(0, result.collapsedWatchDuplicates)
    }

    @Test fun streamSummaryMovesToIntervalsIdentity() {
        val watch = act(1_000_000L, "hw42")
        val intervals = act(1_600_000L, "icu-99")
        val summary = StreamSummary(fast = StreamFast(6.0, 6.5, 8))
        val result = mergeActivities(listOf(watch), listOf(intervals), mapOf("hw42" to summary))
        assertEquals(summary, result.streams["icu-99"])
        assertNull(result.streams["hw42"])
    }
}
