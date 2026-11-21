package com.runnow.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressTest {
    private val zone = ZoneId.of("UTC")
    // Rabu 2026-11-18, tengah hari.
    private val now = LocalDate.of(2026, 11, 18).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun ms(d: String) = LocalDate.parse(d).atTime(7, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private fun run(d: String, km: Double, min: Double) = Act(start = ms(d), kind = ActKind.RUN, min = min, km = km, avgHr = 140.0, lid = d)

    private fun snap(acts: List<Act>, settings: Settings = Settings()) =
        Snapshot(Profile(rest = 52.0, max = 185.0, age = 32), acts, emptyList(), emptyMap(), now, zone, settings)

    @Test
    fun mingguanDikelompokkanSenin() {
        val s = snap(listOf(run("2026-11-16", 5.0, 30.0), run("2026-11-15", 8.0, 50.0), run("2026-11-10", 6.0, 36.0)))
        val w = s.progressWeeks(4)
        assertEquals(4, w.size)
        assertEquals(LocalDate.parse("2026-11-16"), w.last().start)
        assertEquals(5.0, w.last().km)
        assertEquals(14.0, w[2].km) // 15 Nov (Minggu) dan 10 Nov masuk minggu Senin 9 Nov
    }

    @Test
    fun hariMinggu() {
        assertEquals(LocalDate.parse("2026-11-09"), LocalDate.parse("2026-11-15").mondayOf())
    }

    @Test
    fun formatKoma() {
        assertEquals("8", fmt1c(8.0)); assertEquals("8,5", fmt1c(8.46)); assertEquals("0,1", fmt1c(0.12))
    }

    @Test
    fun rencanaKosongNull() {
        assertNull(snap(emptyList()).planAdherence())
    }

    @Test
    fun kepatuhanRencana() {
        val plan = mapOf(1 to PlanDay("easy", 6.0), 3 to PlanDay("easy", 6.0), 0 to PlanDay("rest", 0.0))
        val s = snap(listOf(run("2026-11-16", 6.0, 40.0)), Settings(plan = plan))
        val a = assertNotNull(s.planAdherence())
        assertEquals(12.0, a.planKm); assertEquals(2, a.planN); assertEquals(1, a.doneN); assertEquals(50, a.pctN)
    }

    @Test
    fun mixKosongNull() {
        assertNull(mixOf(listOf(WeekBucket(LocalDate.parse("2026-11-16"), 0.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0))))
    }

    @Test
    fun fitButuhTigaTitik() {
        assertNull(fitSummary(listOf(FitPoint(LocalDate.parse("2026-11-01"), 40.0, 45.0))))
        val r = assertNotNull(fitSummary((0..3).map { FitPoint(LocalDate.parse("2026-11-01").plusDays(it.toLong()), 40.0 + it * 2, 45.0) }))
        assertTrue(r.dCtl >= 3)
    }
}
