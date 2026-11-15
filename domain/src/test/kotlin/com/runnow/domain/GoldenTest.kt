package com.runnow.domain

// DIHASILKAN OTOMATIS dari kode asli runsense.html (baris 797-1130) yang dijalankan di Node dengan TZ=UTC.
// Jangan edit tangan; ubah skenario di gen/scen.js lalu jalankan ulang generator.

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoldenTest {
    private val now = 1794729600000L
    private val eps = 1e-9

    private fun near(exp: Double?, act: Double?, msg: String = "") {
        if (exp == null || act == null) { assertEquals(exp, act, msg); return }
        assertTrue(kotlin.math.abs(exp - act) <= eps, "$msg: diharapkan $exp, dapat $act")
    }

    private fun act(start: Long, kind: String, min: Double, km: Double, hr: Double, elev: Double, id: String, name: String, maxHr: Double, load: Double) =
        Act(start, ActKind.valueOf(kind), min, km, hr, elev, id, name, maxHr, id, load)

    private fun snap(rest: Double, max: Double, age: Int, afS: Int, acts: List<Act>, wl: List<Wellness>, vx: Map<Long, String>) =
        Snapshot(Profile(rest, max, age, afS), acts, wl, vx, now, ZoneOffset.UTC)

    private fun scenario_A_lengkap_hrv_turun_snap(): Snapshot {
        val acts = listOf<Act>(
            act(1788685200000L, "RUN", 62.4, 9.08, 159.0, 49.0, "hw1788685200000", "Lari 70", 171.0, 78.0),
            act(1788771600000L, "WALK", 47.26, 2.26, 146.0, 32.0, "hw1788771600000", "Lari 69", 158.0, 55.0),
            act(1788858000000L, "RUN", 34.98, 5.58, 155.0, 64.0, "hw1788858000000", "Lari 68", 167.0, 0.0),
            act(1788940800000L, "RUN", 17.98, 3.51, 166.0, 24.0, "hw1788940800000", "Lari 67", 178.0, 0.0),
            act(1789117200000L, "RUN", 37.46, 5.46, 145.0, 66.0, "hw1789117200000", "Lari 65", 157.0, 0.0),
            act(1789200000000L, "RUN", 42.96, 7.05, 158.0, 59.0, "hw1789200000000", "Lari 64", 170.0, 75.0),
            act(1789372800000L, "RUN", 41.63, 5.81, 155.0, 4.0, "hw1789372800000", "Lari 62", 167.0, 62.0),
            act(1789462800000L, "RUN", 23.64, 3.8, 152.0, 13.0, "hw1789462800000", "Lari 61", 164.0, 0.0),
            act(1789635600000L, "RUN", 24.61, 4.22, 173.0, 50.0, "hw1789635600000", "Lari 59", 185.0, 0.0),
            act(1789725600000L, "RUN", 26.17, 4.61, 158.0, 24.0, "hw1789725600000", "Lari 58", 170.0, 109.0),
            act(1789804800000L, "RUN", 40.1, 6.47, 158.0, 50.0, "hw1789804800000", "Lari 57", 170.0, 80.0),
            act(1789898400000L, "RUN", 57.85, 9.24, 157.0, 52.0, "hw1789898400000", "Lari 56", 169.0, 89.0),
            act(1789984800000L, "RUN", 18.18, 2.83, 174.0, 30.0, "hw1789984800000", "Lari 55", 186.0, 113.0),
            act(1790067600000L, "RUN", 24.03, 3.72, 157.0, 18.0, "hw1790067600000", "Lari 54", 169.0, 0.0),
            act(1790150400000L, "RUN", 31.06, 5.32, 146.0, 75.0, "hw1790150400000", "Lari 53", 158.0, 0.0),
            act(1790236800000L, "RUN", 44.39, 7.1, 165.0, 0.0, "hw1790236800000", "Lari 52", 177.0, 0.0),
            act(1790326800000L, "RUN", 35.16, 6.84, 143.0, 49.0, "hw1790326800000", "Lari 51", 155.0, 0.0),
            act(1790416800000L, "OTHER", 22.02, 0.0, 146.0, 32.0, "hw1790416800000", "Lari 50", 158.0, 0.0),
            act(1790499600000L, "RUN", 16.43, 2.73, 149.0, 33.0, "hw1790499600000", "Lari 49", 161.0, 95.0),
            act(1790586000000L, "RUN", 44.38, 7.85, 156.0, 25.0, "hw1790586000000", "Lari 48", 168.0, 92.0),
            act(1790672400000L, "RUN", 14.91, 2.78, 169.0, 15.0, "hw1790672400000", "Lari 47", 181.0, 104.0),
            act(1790762400000L, "RUN", 35.86, 6.87, 143.0, 17.0, "hw1790762400000", "Lari 46", 155.0, 0.0),
            act(1790928000000L, "WALK", 43.11, 2.92, 166.0, 31.0, "hw1790928000000", "Lari 44", 178.0, 0.0),
            act(1791014400000L, "RUN", 18.04, 2.83, 140.0, 77.0, "hw1791014400000", "Lari 43", 152.0, 0.0),
            act(1791104400000L, "WALK", 50.73, 1.17, 149.0, 11.0, "hw1791104400000", "Lari 42", 161.0, 0.0),
            act(1791190800000L, "OTHER", 35.56, 0.0, 156.0, 15.0, "hw1791190800000", "Lari 41", 168.0, 39.0),
            act(1791450000000L, "RUN", 23.92, 3.39, 143.0, 22.0, "hw1791450000000", "Lari 38", 155.0, 0.0),
            act(1791622800000L, "RUN", 22.38, 4.23, 171.0, 37.0, "hw1791622800000", "Lari 36", 183.0, 0.0),
            act(1791709200000L, "RUN", 47.45, 8.52, 158.0, 24.0, "hw1791709200000", "Lari 35", 170.0, 0.0),
            act(1791795600000L, "RUN", 21.86, 3.42, 141.0, 13.0, "hw1791795600000", "Lari 34", 153.0, 97.0),
            act(1791972000000L, "RUN", 36.52, 5.63, 153.0, 74.0, "hw1791972000000", "Lari 32", 165.0, 0.0),
            act(1792058400000L, "RUN", 17.16, 2.79, 164.0, 73.0, "hw1792058400000", "Lari 31", 176.0, 113.0),
            act(1792227600000L, "RUN", 36.45, 5.42, 174.0, 26.0, "hw1792227600000", "Lari 29", 186.0, 50.0),
            act(1792317600000L, "RUN", 55.96, 10.62, 155.0, 34.0, "hw1792317600000", "Lari 28", 167.0, 0.0),
            act(1792404000000L, "RUN", 13.8, 2.57, 141.0, 20.0, "hw1792404000000", "Lari 27", 153.0, 0.0),
            act(1792483200000L, "RUN", 41.96, 6.3, 161.0, 44.0, "hw1792483200000", "Lari 26", 173.0, 0.0),
            act(1792573200000L, "RUN", 25.97, 3.72, 143.0, 35.0, "hw1792573200000", "Lari 25", 155.0, 0.0),
            act(1792659600000L, "RUN", 33.81, 6.02, 151.0, 32.0, "hw1792659600000", "Lari 24", 163.0, 110.0),
            act(1792832400000L, "RUN", 12.21, 2.38, 154.0, 33.0, "hw1792832400000", "Lari 22", 166.0, 0.0),
            act(1792918800000L, "RUN", 46.69, 9.31, 153.0, 63.0, "hw1792918800000", "Lari 21", 165.0, 0.0),
            act(1793005200000L, "OTHER", 26.57, 0.0, 143.0, 68.0, "hw1793005200000", "Lari 20", 155.0, 50.0),
            act(1793354400000L, "RUN", 21.21, 4.22, 148.0, 38.0, "hw1793354400000", "Lari 16", 160.0, 0.0),
            act(1793696400000L, "RUN", 50.44, 7.2, 149.0, 12.0, "hw1793696400000", "Lari 12", 161.0, 40.0),
            act(1793872800000L, "RUN", 30.1, 4.37, 156.0, 52.0, "hw1793872800000", "Lari 10", 168.0, 86.0),
            act(1793955600000L, "RUN", 28.16, 4.78, 155.0, 25.0, "hw1793955600000", "Lari 9", 167.0, 0.0),
            act(1794038400000L, "RUN", 17.63, 2.76, 148.0, 58.0, "hw1794038400000", "Lari 8", 160.0, 0.0),
            act(1794214800000L, "RUN", 16.39, 3.26, 171.0, 69.0, "hw1794214800000", "Lari 6", 183.0, 76.0),
            act(1794297600000L, "OTHER", 42.21, 0.0, 152.0, 2.0, "hw1794297600000", "Lari 5", 164.0, 39.0),
            act(1794384000000L, "RUN", 34.1, 5.66, 149.0, 50.0, "hw1794384000000", "Lari 4", 161.0, 0.0),
            act(1794477600000L, "RUN", 24.3, 4.62, 164.0, 51.0, "hw1794477600000", "Lari 3", 176.0, 44.0),
            act(1794556800000L, "RUN", 17.57, 3.18, 148.0, 5.0, "hw1794556800000", "Lari 2", 160.0, 38.0),
            act(1794646800000L, "RUN", 51.49, 7.82, 165.0, 34.0, "hw1794646800000", "Lari 1", 177.0, 90.0),
            act(1794733200000L, "RUN", 22.63, 4.09, 173.0, 69.0, "hw1794733200000", "Lari 0", 185.0, 36.0),
        )
        val wl = listOf<Wellness>(
            Wellness("2026-10-06", restingHR = 53.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 26214.0, stress = 37.0),
            Wellness("2026-10-07", restingHR = 53.0, rhrSrc = "jam", stress = 31.0),
            Wellness("2026-10-08", restingHR = 53.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 23913.0, stress = 39.0),
            Wellness("2026-10-09", restingHR = 54.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 27502.0, stress = 40.0),
            Wellness("2026-10-10", restingHR = 53.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 22634.0, stress = 38.0),
            Wellness("2026-10-11", restingHR = 53.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 26508.0, stress = 33.0),
            Wellness("2026-10-12", restingHR = 56.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", sleepSecs = 22348.0),
            Wellness("2026-10-13", restingHR = 53.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 22558.0, stress = 39.0),
            Wellness("2026-10-14", restingHR = 55.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 22607.0, stress = 31.0),
            Wellness("2026-10-15", restingHR = 54.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 27377.0, stress = 31.0),
            Wellness("2026-10-16", restingHR = 55.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 24494.0, stress = 34.0),
            Wellness("2026-10-17", restingHR = 54.0, rhrSrc = "jam", sleepSecs = 22760.0, stress = 35.0),
            Wellness("2026-10-18", restingHR = 55.0, rhrSrc = "jam", sleepSecs = 24438.0, stress = 37.0),
            Wellness("2026-10-19", restingHR = 53.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", sleepSecs = 25785.0, stress = 39.0),
            Wellness("2026-10-20", restingHR = 52.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 27013.0, stress = 36.0),
            Wellness("2026-10-21", restingHR = 54.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 27732.0, stress = 37.0),
            Wellness("2026-10-22", restingHR = 55.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 22397.0, stress = 34.0),
            Wellness("2026-10-23", restingHR = 54.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 22363.0, stress = 38.0),
            Wellness("2026-10-24", restingHR = 54.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", sleepSecs = 24859.0, stress = 32.0),
            Wellness("2026-10-25", restingHR = 53.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 23252.0, stress = 36.0),
            Wellness("2026-10-26", hrv = 62.0, hrvSrc = "jam", sleepSecs = 22817.0, stress = 39.0),
            Wellness("2026-10-27", restingHR = 54.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", stress = 32.0),
            Wellness("2026-10-28", restingHR = 54.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", sleepSecs = 26763.0),
            Wellness("2026-10-29", restingHR = 54.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 27222.0, stress = 31.0),
            Wellness("2026-10-30", hrv = 61.0, hrvSrc = "jam", sleepSecs = 24142.0, stress = 37.0),
            Wellness("2026-10-31", restingHR = 54.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 26939.0, stress = 36.0),
            Wellness("2026-11-01", restingHR = 55.0, rhrSrc = "jam", sleepSecs = 24294.0),
            Wellness("2026-11-02", restingHR = 55.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", sleepSecs = 23903.0, stress = 37.0),
            Wellness("2026-11-03", restingHR = 56.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", sleepSecs = 26045.0),
            Wellness("2026-11-04", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 23806.0, stress = 37.0),
            Wellness("2026-11-05", restingHR = 56.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 25687.0, stress = 38.0),
            Wellness("2026-11-06", restingHR = 55.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", stress = 33.0),
            Wellness("2026-11-07", restingHR = 53.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 23002.0, stress = 39.0),
            Wellness("2026-11-08", sleepSecs = 27097.0, stress = 35.0),
            Wellness("2026-11-09", restingHR = 52.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 23335.0, stress = 37.0),
            Wellness("2026-11-10", restingHR = 56.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", stress = 36.0),
            Wellness("2026-11-11", restingHR = 54.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 25230.0, stress = 35.0),
            Wellness("2026-11-12", restingHR = 52.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 25707.0, stress = 37.0),
            Wellness("2026-11-13", restingHR = 57.0, rhrSrc = "jam", hrv = 52.0, hrvSrc = "jam", sleepSecs = 27804.0, stress = 31.0),
            Wellness("2026-11-14", restingHR = 57.0, rhrSrc = "jam", hrv = 56.0, hrvSrc = "jam", sleepSecs = 22855.0, stress = 57.0),
            Wellness("2026-11-15", restingHR = 59.0, rhrSrc = "jam", sleepSecs = 19289.0, stress = 53.0),
        )
        val vx = mapOf<Long, String>(1794556800000L to "long")
        return snap(54.0, 188.0, 35, 0, acts, wl, vx)
    }

    @Test
    fun scenario_A_lengkap_hrv_turun() {
        val sn = scenario_A_lengkap_hrv_turun_snap()
        // estMax
        val e = sn.estMax()
        near(15.223862462790837, e.m, "estMax.m")
        near(16.915402736434263, e.perf, "estMax.perf")
        near(12.970722552392866, e.hrr, "estMax.hrr")
        assertEquals("HR+pace dari 36 lari (56 hari)", e.src)
        assertEquals("baik", e.conf)
        assertEquals(36, e.n)
        // stage
        val g = sn.stage()
        assertEquals(4, g.s)
        near(15.223862462790837, g.m, "stage.m")
        assertEquals(4, g.raw)
        assertEquals(false, g.unknown)
        assertEquals(4, g.afS)
        // load
        val expLoads = listOf<Int>(78, 55, 43, 27, 37, 75, 62, 27, 42, 109, 80, 89, 113, 31, 32, 66, 33, 18, 95, 92, 104, 34, 15, 16, 18, 39, 23, 37, 62, 97, 43, 113, 50, 68, 13, 58, 25, 110, 15, 55, 50, 22, 40, 86, 34, 19, 76, 39, 37, 44, 38, 90, 36)
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(360, sn.loadDays(7))
        assertEquals(955, sn.loadDays(28))
        // rhr28 / maxHr
        assertEquals(Rhr28(54, 26), sn.rhr28())
        near(188.0, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(true, au.lock, "lock")
        assertEquals(false, au.severe, "severe")
        assertEquals(true, au.warn, "warn")
        assertEquals("HRV -12%, RHR +4 bpm, Tidur 5j21, Stres +17", au.why)
        assertEquals(-12, au.hrvPct)
        assertEquals(4, au.rhrD)
        assertEquals(56, au.hrv)
        assertEquals(59, au.rhr)
        near(5.358055555555556, au.sleepH, "sleepH")
        near(53.0, au.stress, "stress")
        assertEquals(17, au.stressD)
        // readiness
        assertEquals(27, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf<List<String>>(
            listOf(1794038400000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794214800000L.toString(), "thr", "rendah", "intensitas di atas easy (≈ 78% Max)", "false"),
            listOf(1794297600000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794384000000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794477600000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794556800000L.toString(), "long", "tinggi", "dikonfirmasi manual", "true"),
            listOf(1794646800000L.toString(), "long", "sedang", "7.8 km, 69% lebih jauh dari biasamu", "false"),
            listOf(1794733200000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }

    private fun scenario_B_tanpa_hr_tanpa_wellness_snap(): Snapshot {
        val acts = listOf<Act>(
            act(1791273600000L, "RUN", 18.74, 3.23, 0.0, 72.0, "hw1791273600000", "Lari 40", 0.0, 0.0),
            act(1791367200000L, "RUN", 11.0, 2.1, 0.0, 11.0, "hw1791367200000", "Lari 39", 0.0, 0.0),
            act(1791532800000L, "RUN", 20.89, 3.35, 0.0, 63.0, "hw1791532800000", "Lari 37", 0.0, 0.0),
            act(1791626400000L, "RUN", 35.46, 5.98, 0.0, 0.0, "hw1791626400000", "Lari 36", 0.0, 0.0),
            act(1791795600000L, "RUN", 24.33, 3.58, 0.0, 9.0, "hw1791795600000", "Lari 34", 0.0, 0.0),
            act(1792656000000L, "RUN", 29.07, 5.72, 0.0, 9.0, "hw1792656000000", "Lari 24", 0.0, 0.0),
            act(1792742400000L, "WALK", 46.79, 1.6, 0.0, 36.0, "hw1792742400000", "Lari 23", 0.0, 0.0),
            act(1792832400000L, "RUN", 21.63, 3.64, 0.0, 58.0, "hw1792832400000", "Lari 22", 0.0, 0.0),
            act(1792918800000L, "WALK", 50.25, 1.36, 0.0, 9.0, "hw1792918800000", "Lari 21", 0.0, 0.0),
            act(1793178000000L, "WALK", 20.34, 2.2, 0.0, 49.0, "hw1793178000000", "Lari 18", 0.0, 0.0),
            act(1793264400000L, "RUN", 20.07, 3.78, 0.0, 66.0, "hw1793264400000", "Lari 17", 0.0, 0.0),
            act(1793433600000L, "OTHER", 42.52, 0.0, 0.0, 72.0, "hw1793433600000", "Lari 15", 0.0, 0.0),
            act(1793610000000L, "RUN", 24.09, 3.44, 0.0, 34.0, "hw1793610000000", "Lari 13", 0.0, 0.0),
            act(1793696400000L, "RUN", 31.09, 5.99, 0.0, 43.0, "hw1793696400000", "Lari 12", 0.0, 0.0),
            act(1793865600000L, "RUN", 36.14, 5.38, 0.0, 40.0, "hw1793865600000", "Lari 10", 0.0, 0.0),
            act(1793952000000L, "RUN", 26.43, 4.73, 0.0, 48.0, "hw1793952000000", "Lari 9", 0.0, 0.0),
            act(1794045600000L, "RUN", 41.26, 6.43, 0.0, 80.0, "hw1794045600000", "Lari 8", 0.0, 0.0),
            act(1794132000000L, "RUN", 36.33, 5.96, 0.0, 42.0, "hw1794132000000", "Lari 7", 0.0, 0.0),
            act(1794218400000L, "RUN", 37.56, 6.25, 0.0, 39.0, "hw1794218400000", "Lari 6", 0.0, 0.0),
            act(1794387600000L, "RUN", 35.63, 6.23, 0.0, 50.0, "hw1794387600000", "Lari 4", 0.0, 0.0),
            act(1794477600000L, "RUN", 48.06, 6.88, 0.0, 25.0, "hw1794477600000", "Lari 3", 0.0, 0.0),
            act(1794556800000L, "RUN", 16.22, 2.31, 0.0, 9.0, "hw1794556800000", "Lari 2", 0.0, 0.0),
            act(1794729600000L, "OTHER", 23.13, 0.0, 0.0, 8.0, "hw1794729600000", "Lari 0", 0.0, 0.0),
        )
        val wl = listOf<Wellness>(
            Wellness("2026-11-11", restingHR = 52.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 25388.0, stress = 32.0),
            Wellness("2026-11-12", restingHR = 52.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 23220.0, stress = 37.0),
            Wellness("2026-11-13", restingHR = 54.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 27353.0, stress = 32.0),
            Wellness("2026-11-14", restingHR = 55.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 22334.0, stress = 39.0),
            Wellness("2026-11-15", restingHR = 56.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 27578.0, stress = 38.0),
        )
        val vx = mapOf<Long, String>(1794387600000L to "long")
        return snap(0.0, 0.0, 0, 0, acts, wl, vx)
    }

    @Test
    fun scenario_B_tanpa_hr_tanpa_wellness() {
        val sn = scenario_B_tanpa_hr_tanpa_wellness_snap()
        // estMax
        val e = sn.estMax()
        near(13.222251799506793, e.m, "estMax.m")
        near(14.691390888340882, e.perf, "estMax.perf")
        near(null, e.hrr, "estMax.hrr")
        assertEquals("batas bawah performa pace", e.src)
        assertEquals("rendah", e.conf)
        assertEquals(0, e.n)
        // stage
        val g = sn.stage()
        assertEquals(4, g.s)
        near(13.222251799506793, g.m, "stage.m")
        assertEquals(4, g.raw)
        assertEquals(false, g.unknown)
        assertEquals(4, g.afS)
        // load
        val expLoads = listOf<Int>(23, 17, 21, 40, 20, 46, 16, 24, 18, 7, 30, 34, 19, 48, 31, 35, 39, 38, 41, 44, 38, 13, 19)
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(193, sn.loadDays(7))
        assertEquals(540, sn.loadDays(28))
        // rhr28 / maxHr
        assertEquals(Rhr28(54, 5), sn.rhr28())
        near(0.0, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(false, au.lock, "lock")
        assertEquals(false, au.severe, "severe")
        assertEquals(false, au.warn, "warn")
        assertEquals("", au.why)
        assertEquals(null, au.hrvPct)
        assertEquals(null, au.rhrD)
        assertEquals(69, au.hrv)
        assertEquals(56, au.rhr)
        near(7.660555555555556, au.sleepH, "sleepH")
        near(38.0, au.stress, "stress")
        assertEquals(null, au.stressD)
        // readiness
        assertEquals(50, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf<List<String>>(
            listOf(1793952000000L.toString(), "thr", "rendah", "intensitas di atas easy (≈ 82% Max)", "false"),
            listOf(1794045600000L.toString(), "long", "sedang", "6.4 km, 36% lebih jauh dari biasamu", "false"),
            listOf(1794132000000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794218400000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794387600000L.toString(), "long", "tinggi", "dikonfirmasi manual", "true"),
            listOf(1794477600000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794556800000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794729600000L.toString(), "easy", "rendah", "bukan lari", "false"),
        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }

    private fun scenario_C_umur_saja_snap(): Snapshot {
        val acts = listOf<Act>(
            act(1789552800000L, "RUN", 12.56, 2.1, 169.0, 21.0, "hw1789552800000", "Lari 60", 181.0, 0.0),
            act(1789639200000L, "OTHER", 52.34, 0.0, 148.0, 66.0, "hw1789639200000", "Lari 59", 160.0, 0.0),
            act(1789722000000L, "RUN", 14.29, 2.76, 150.0, 67.0, "hw1789722000000", "Lari 58", 162.0, 0.0),
            act(1789894800000L, "RUN", 64.62, 9.46, 164.0, 34.0, "hw1789894800000", "Lari 56", 176.0, 0.0),
            act(1789984800000L, "RUN", 28.87, 4.14, 170.0, 44.0, "hw1789984800000", "Lari 55", 182.0, 0.0),
            act(1790064000000L, "RUN", 20.87, 3.4, 169.0, 12.0, "hw1790064000000", "Lari 54", 181.0, 0.0),
            act(1790157600000L, "OTHER", 55.54, 0.0, 154.0, 23.0, "hw1790157600000", "Lari 53", 166.0, 0.0),
            act(1790323200000L, "RUN", 27.92, 4.98, 155.0, 63.0, "hw1790323200000", "Lari 51", 167.0, 0.0),
            act(1790416800000L, "RUN", 45.48, 6.66, 175.0, 5.0, "hw1790416800000", "Lari 50", 187.0, 0.0),
            act(1790503200000L, "OTHER", 23.16, 0.0, 142.0, 2.0, "hw1790503200000", "Lari 49", 154.0, 0.0),
            act(1790589600000L, "RUN", 37.45, 6.46, 174.0, 67.0, "hw1790589600000", "Lari 48", 186.0, 0.0),
            act(1790668800000L, "RUN", 14.59, 2.37, 156.0, 67.0, "hw1790668800000", "Lari 47", 168.0, 0.0),
            act(1790758800000L, "WALK", 39.66, 1.05, 142.0, 47.0, "hw1790758800000", "Lari 46", 154.0, 0.0),
            act(1790848800000L, "OTHER", 30.1, 0.0, 170.0, 67.0, "hw1790848800000", "Lari 45", 182.0, 0.0),
            act(1791021600000L, "RUN", 35.94, 6.99, 146.0, 48.0, "hw1791021600000", "Lari 43", 158.0, 0.0),
            act(1791280800000L, "RUN", 16.53, 2.88, 169.0, 29.0, "hw1791280800000", "Lari 40", 181.0, 0.0),
            act(1791453600000L, "WALK", 32.6, 2.11, 164.0, 50.0, "hw1791453600000", "Lari 38", 176.0, 0.0),
            act(1791536400000L, "RUN", 19.45, 3.56, 173.0, 57.0, "hw1791536400000", "Lari 37", 185.0, 0.0),
            act(1791795600000L, "OTHER", 23.03, 0.0, 160.0, 21.0, "hw1791795600000", "Lari 34", 172.0, 0.0),
            act(1791885600000L, "RUN", 15.61, 2.18, 158.0, 78.0, "hw1791885600000", "Lari 33", 170.0, 0.0),
            act(1792137600000L, "OTHER", 42.54, 0.0, 172.0, 6.0, "hw1792137600000", "Lari 30", 184.0, 0.0),
            act(1792227600000L, "RUN", 31.37, 5.42, 166.0, 64.0, "hw1792227600000", "Lari 29", 178.0, 0.0),
            act(1792396800000L, "RUN", 32.96, 5.71, 164.0, 80.0, "hw1792396800000", "Lari 27", 176.0, 0.0),
            act(1792656000000L, "WALK", 34.51, 1.03, 157.0, 40.0, "hw1792656000000", "Lari 24", 169.0, 0.0),
            act(1792746000000L, "RUN", 32.23, 6.05, 158.0, 45.0, "hw1792746000000", "Lari 23", 170.0, 0.0),
            act(1792832400000L, "RUN", 36.58, 6.65, 156.0, 15.0, "hw1792832400000", "Lari 22", 168.0, 0.0),
            act(1792922400000L, "RUN", 71.65, 11.96, 151.0, 56.0, "hw1792922400000", "Lari 21", 163.0, 0.0),
            act(1793088000000L, "RUN", 39.78, 7.43, 149.0, 29.0, "hw1793088000000", "Lari 19", 161.0, 0.0),
            act(1793181600000L, "RUN", 44.62, 6.33, 152.0, 48.0, "hw1793181600000", "Lari 18", 164.0, 0.0),
            act(1793260800000L, "RUN", 31.45, 5.68, 153.0, 59.0, "hw1793260800000", "Lari 17", 165.0, 0.0),
            act(1793527200000L, "RUN", 45.04, 8.17, 143.0, 48.0, "hw1793527200000", "Lari 14", 155.0, 0.0),
            act(1793606400000L, "RUN", 38.84, 6.58, 171.0, 47.0, "hw1793606400000", "Lari 13", 183.0, 0.0),
            act(1793692800000L, "RUN", 38.46, 5.98, 169.0, 77.0, "hw1793692800000", "Lari 12", 181.0, 0.0),
            act(1793786400000L, "RUN", 15.95, 2.26, 145.0, 42.0, "hw1793786400000", "Lari 11", 157.0, 0.0),
            act(1793959200000L, "RUN", 39.77, 6.43, 161.0, 37.0, "hw1793959200000", "Lari 9", 173.0, 0.0),
            act(1794045600000L, "RUN", 49.29, 7.38, 161.0, 75.0, "hw1794045600000", "Lari 8", 173.0, 0.0),
            act(1794124800000L, "RUN", 14.28, 2.06, 169.0, 4.0, "hw1794124800000", "Lari 7", 181.0, 0.0),
            act(1794218400000L, "RUN", 25.04, 4.38, 173.0, 51.0, "hw1794218400000", "Lari 6", 185.0, 0.0),
            act(1794304800000L, "OTHER", 58.05, 0.0, 153.0, 23.0, "hw1794304800000", "Lari 5", 165.0, 0.0),
            act(1794384000000L, "RUN", 19.58, 3.65, 154.0, 47.0, "hw1794384000000", "Lari 4", 166.0, 0.0),
            act(1794560400000L, "OTHER", 53.15, 0.0, 175.0, 46.0, "hw1794560400000", "Lari 2", 187.0, 0.0),
            act(1794643200000L, "RUN", 38.94, 7.73, 174.0, 41.0, "hw1794643200000", "Lari 1", 186.0, 0.0),
        )
        val wl = listOf<Wellness>(
            Wellness("2026-10-11", restingHR = 53.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", sleepSecs = 26963.0, stress = 38.0),
            Wellness("2026-10-12", restingHR = 54.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 23646.0, stress = 36.0),
            Wellness("2026-10-13", restingHR = 52.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 23778.0, stress = 37.0),
            Wellness("2026-10-14", restingHR = 54.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 27348.0),
            Wellness("2026-10-15", restingHR = 54.0, rhrSrc = "jam", sleepSecs = 26342.0, stress = 31.0),
            Wellness("2026-10-16", restingHR = 52.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 22844.0),
            Wellness("2026-10-17", restingHR = 55.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 22922.0, stress = 40.0),
            Wellness("2026-10-18", restingHR = 56.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 26595.0, stress = 36.0),
            Wellness("2026-10-19", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 26674.0, stress = 37.0),
            Wellness("2026-10-20", restingHR = 53.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 26637.0, stress = 34.0),
            Wellness("2026-10-21", restingHR = 55.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 27408.0),
            Wellness("2026-10-22", restingHR = 55.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", stress = 39.0),
            Wellness("2026-10-23", restingHR = 52.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 23813.0, stress = 39.0),
            Wellness("2026-10-24", restingHR = 56.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 23155.0, stress = 36.0),
            Wellness("2026-10-25", restingHR = 54.0, rhrSrc = "jam", sleepSecs = 25242.0, stress = 33.0),
            Wellness("2026-10-26", restingHR = 53.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 23584.0, stress = 32.0),
            Wellness("2026-10-27", restingHR = 54.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam"),
            Wellness("2026-10-28", restingHR = 56.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", stress = 31.0),
            Wellness("2026-10-29", restingHR = 55.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", hrvSpot = true, stress = 35.0),
            Wellness("2026-10-30", restingHR = 53.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", sleepSecs = 27718.0, stress = 36.0),
            Wellness("2026-10-31", restingHR = 55.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 25319.0, stress = 34.0),
            Wellness("2026-11-01", restingHR = 56.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", stress = 31.0),
            Wellness("2026-11-02", restingHR = 53.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 23747.0),
            Wellness("2026-11-03", restingHR = 54.0, rhrSrc = "jam"),
            Wellness("2026-11-04", restingHR = 54.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 25375.0),
            Wellness("2026-11-05", restingHR = 55.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 23350.0, stress = 35.0),
            Wellness("2026-11-06", restingHR = 56.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", stress = 31.0),
            Wellness("2026-11-07", restingHR = 54.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 25229.0),
            Wellness("2026-11-08", hrv = 63.0, hrvSrc = "jam", sleepSecs = 23538.0, stress = 35.0),
            Wellness("2026-11-09", restingHR = 52.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 22984.0, stress = 32.0),
            Wellness("2026-11-10", restingHR = 52.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 23965.0, stress = 39.0),
            Wellness("2026-11-11", restingHR = 53.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 23725.0),
            Wellness("2026-11-12", hrv = 68.0, hrvSrc = "jam", sleepSecs = 23226.0, stress = 35.0),
            Wellness("2026-11-13", restingHR = 58.0, rhrSrc = "jam", hrv = 56.0, hrvSrc = "jam", sleepSecs = 22531.0, stress = 39.0),
            Wellness("2026-11-14", restingHR = 56.0, rhrSrc = "jam", hrv = 55.0, hrvSrc = "jam", sleepSecs = 25622.0, stress = 32.0),
            Wellness("2026-11-15", restingHR = 57.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 24734.0, stress = 31.0),
        )
        val vx = mapOf<Long, String>(1794218400000L to "long")
        return snap(58.0, 0.0, 42, 3, acts, wl, vx)
    }

    @Test
    fun scenario_C_umur_saja() {
        val sn = scenario_C_umur_saja_snap()
        // estMax
        val e = sn.estMax()
        near(14.25909866411313, e.m, "estMax.m")
        near(15.8434429601257, e.perf, "estMax.perf")
        near(11.05687112196114, e.hrr, "estMax.hrr")
        assertEquals("HR+pace dari 29 lari (56 hari)", e.src)
        assertEquals("baik", e.conf)
        assertEquals(29, e.n)
        // stage
        val g = sn.stage()
        assertEquals(4, g.s)
        near(14.25909866411313, g.m, "stage.m")
        assertEquals(4, g.raw)
        assertEquals(false, g.unknown)
        assertEquals(4, g.afS)
        // load
        val expLoads = listOf<Int>(23, 42, 18, 107, 53, 38, 44, 39, 92, 19, 74, 21, 14, 24, 41, 30, 11, 38, 18, 23, 34, 54, 54, 12, 47, 52, 91, 48, 58, 42, 48, 73, 70, 18, 62, 77, 26, 49, 46, 27, 43, 77)
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(268, sn.loadDays(7))
        assertEquals(1020, sn.loadDays(28))
        // rhr28 / maxHr
        assertEquals(Rhr28(54, 27), sn.rhr28())
        near(179.0, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(false, au.lock, "lock")
        assertEquals(false, au.severe, "severe")
        assertEquals(true, au.warn, "warn")
        assertEquals("HRV -11%, RHR +3 bpm", au.why)
        assertEquals(-11, au.hrvPct)
        assertEquals(3, au.rhrD)
        assertEquals(63, au.hrv)
        assertEquals(57, au.rhr)
        near(6.870555555555556, au.sleepH, "sleepH")
        near(31.0, au.stress, "stress")
        assertEquals(-4, au.stressD)
        // readiness
        assertEquals(66, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf<List<String>>(
            listOf(1793959200000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794045600000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794124800000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794218400000L.toString(), "long", "tinggi", "dikonfirmasi manual", "true"),
            listOf(1794304800000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794384000000L.toString(), "thr", "rendah", "intensitas di atas easy (≈ 79% Max)", "false"),
            listOf(1794560400000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794643200000L.toString(), "thr", "rendah", "intensitas di atas easy (≈ 84% Max)", "false"),
        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }

    private fun scenario_D_hrv_tak_berubah_snap(): Snapshot {
        val acts = listOf<Act>(
            act(1789545600000L, "RUN", 43.58, 6.81, 157.0, 30.0, "hw1789545600000", "Lari 60", 169.0, 0.0),
            act(1789639200000L, "RUN", 14.78, 2.07, 169.0, 1.0, "hw1789639200000", "Lari 59", 181.0, 0.0),
            act(1789722000000L, "RUN", 31.02, 5.09, 151.0, 7.0, "hw1789722000000", "Lari 58", 163.0, 0.0),
            act(1789812000000L, "RUN", 47.37, 7.95, 161.0, 35.0, "hw1789812000000", "Lari 57", 173.0, 47.0),
            act(1789894800000L, "RUN", 69.83, 11.94, 160.0, 50.0, "hw1789894800000", "Lari 56", 172.0, 34.0),
            act(1789984800000L, "WALK", 38.94, 3.82, 166.0, 20.0, "hw1789984800000", "Lari 55", 178.0, 96.0),
            act(1790071200000L, "RUN", 16.08, 2.34, 148.0, 68.0, "hw1790071200000", "Lari 54", 160.0, 47.0),
            act(1790150400000L, "RUN", 55.77, 7.79, 147.0, 50.0, "hw1790150400000", "Lari 53", 159.0, 0.0),
            act(1790240400000L, "RUN", 38.5, 7.26, 171.0, 41.0, "hw1790240400000", "Lari 52", 183.0, 0.0),
            act(1790499600000L, "RUN", 29.71, 4.28, 147.0, 69.0, "hw1790499600000", "Lari 49", 159.0, 89.0),
            act(1790582400000L, "RUN", 20.13, 3.86, 163.0, 2.0, "hw1790582400000", "Lari 48", 175.0, 0.0),
            act(1790676000000L, "RUN", 15.91, 2.43, 146.0, 64.0, "hw1790676000000", "Lari 47", 158.0, 0.0),
            act(1790928000000L, "RUN", 15.2, 2.7, 156.0, 68.0, "hw1790928000000", "Lari 44", 168.0, 0.0),
            act(1791014400000L, "RUN", 38.55, 6.08, 168.0, 66.0, "hw1791014400000", "Lari 43", 180.0, 81.0),
            act(1791108000000L, "RUN", 50.36, 9.42, 175.0, 41.0, "hw1791108000000", "Lari 42", 187.0, 67.0),
            act(1791187200000L, "RUN", 27.28, 4.99, 166.0, 65.0, "hw1791187200000", "Lari 41", 178.0, 75.0),
            act(1791273600000L, "RUN", 39.38, 6.61, 157.0, 38.0, "hw1791273600000", "Lari 40", 169.0, 0.0),
            act(1791363600000L, "RUN", 48.07, 7.18, 165.0, 5.0, "hw1791363600000", "Lari 39", 177.0, 94.0),
            act(1791446400000L, "RUN", 38.51, 6.81, 161.0, 24.0, "hw1791446400000", "Lari 38", 173.0, 116.0),
            act(1791705600000L, "RUN", 55.19, 9.52, 150.0, 63.0, "hw1791705600000", "Lari 35", 162.0, 0.0),
            act(1791792000000L, "RUN", 13.11, 2.53, 174.0, 44.0, "hw1791792000000", "Lari 34", 186.0, 0.0),
            act(1791878400000L, "RUN", 23.34, 4.11, 175.0, 13.0, "hw1791878400000", "Lari 33", 187.0, 105.0),
            act(1791972000000L, "OTHER", 35.3, 0.0, 156.0, 42.0, "hw1791972000000", "Lari 32", 168.0, 0.0),
            act(1792054800000L, "OTHER", 28.99, 0.0, 149.0, 51.0, "hw1792054800000", "Lari 31", 161.0, 105.0),
            act(1792137600000L, "RUN", 33.09, 5.22, 155.0, 53.0, "hw1792137600000", "Lari 30", 167.0, 41.0),
            act(1792224000000L, "RUN", 36.07, 6.7, 148.0, 63.0, "hw1792224000000", "Lari 29", 160.0, 71.0),
            act(1792310400000L, "WALK", 41.44, 1.47, 174.0, 71.0, "hw1792310400000", "Lari 28", 186.0, 0.0),
            act(1792483200000L, "RUN", 42.09, 7.34, 171.0, 17.0, "hw1792483200000", "Lari 26", 183.0, 0.0),
            act(1792663200000L, "RUN", 27.42, 4.97, 169.0, 19.0, "hw1792663200000", "Lari 24", 181.0, 53.0),
            act(1792746000000L, "RUN", 53.59, 7.47, 173.0, 41.0, "hw1792746000000", "Lari 23", 185.0, 0.0),
            act(1792832400000L, "RUN", 24.78, 4.9, 168.0, 44.0, "hw1792832400000", "Lari 22", 180.0, 0.0),
            act(1792918800000L, "RUN", 69.05, 11.95, 157.0, 43.0, "hw1792918800000", "Lari 21", 169.0, 79.0),
            act(1793008800000L, "OTHER", 33.03, 0.0, 140.0, 68.0, "hw1793008800000", "Lari 20", 152.0, 38.0),
            act(1793091600000L, "RUN", 34.88, 5.61, 174.0, 62.0, "hw1793091600000", "Lari 19", 186.0, 47.0),
            act(1793260800000L, "RUN", 31.15, 5.69, 162.0, 66.0, "hw1793260800000", "Lari 17", 174.0, 0.0),
            act(1793440800000L, "RUN", 32.89, 5.98, 173.0, 4.0, "hw1793440800000", "Lari 15", 185.0, 49.0),
            act(1793527200000L, "OTHER", 46.11, 0.0, 153.0, 36.0, "hw1793527200000", "Lari 14", 165.0, 0.0),
            act(1793700000000L, "RUN", 46.99, 7.42, 164.0, 30.0, "hw1793700000000", "Lari 12", 176.0, 0.0),
            act(1793872800000L, "WALK", 24.4, 1.98, 142.0, 73.0, "hw1793872800000", "Lari 10", 154.0, 60.0),
            act(1793952000000L, "RUN", 20.11, 2.86, 143.0, 56.0, "hw1793952000000", "Lari 9", 155.0, 110.0),
            act(1794045600000L, "RUN", 30.89, 4.73, 156.0, 21.0, "hw1794045600000", "Lari 8", 168.0, 0.0),
            act(1794124800000L, "RUN", 28.27, 5.48, 174.0, 58.0, "hw1794124800000", "Lari 7", 186.0, 0.0),
            act(1794218400000L, "RUN", 22.0, 3.25, 152.0, 57.0, "hw1794218400000", "Lari 6", 164.0, 0.0),
            act(1794301200000L, "RUN", 39.68, 6.07, 162.0, 11.0, "hw1794301200000", "Lari 5", 174.0, 91.0),
            act(1794384000000L, "OTHER", 33.13, 0.0, 146.0, 37.0, "hw1794384000000", "Lari 4", 158.0, 0.0),
            act(1794474000000L, "WALK", 31.2, 1.59, 149.0, 8.0, "hw1794474000000", "Lari 3", 161.0, 0.0),
            act(1794650400000L, "RUN", 12.69, 2.49, 141.0, 0.0, "hw1794650400000", "Lari 1", 153.0, 0.0),
            act(1794736800000L, "RUN", 45.59, 8.11, 162.0, 30.0, "hw1794736800000", "Lari 0", 174.0, 34.0),
        )
        val wl = listOf<Wellness>(
            Wellness("2026-10-11", sleepSecs = 25161.0, stress = 35.0),
            Wellness("2026-10-12", hrv = 67.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 24162.0, stress = 38.0),
            Wellness("2026-10-13", restingHR = 55.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 25955.0, stress = 34.0),
            Wellness("2026-10-14", restingHR = 54.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 22564.0, stress = 36.0),
            Wellness("2026-10-15", restingHR = 55.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 27767.0, stress = 37.0),
            Wellness("2026-10-16", restingHR = 53.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 24231.0, stress = 39.0),
            Wellness("2026-10-17", restingHR = 52.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", stress = 38.0),
            Wellness("2026-10-18", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 25054.0, stress = 33.0),
            Wellness("2026-10-19", restingHR = 52.0, rhrSrc = "jam", sleepSecs = 27769.0, stress = 35.0),
            Wellness("2026-10-20", restingHR = 52.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 26892.0, stress = 39.0),
            Wellness("2026-10-21", restingHR = 53.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 27428.0, stress = 35.0),
            Wellness("2026-10-22", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 22687.0, stress = 38.0),
            Wellness("2026-10-23", restingHR = 53.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 27407.0),
            Wellness("2026-10-24", restingHR = 52.0, rhrSrc = "jam", sleepSecs = 23571.0, stress = 35.0),
            Wellness("2026-10-25", restingHR = 53.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 27782.0, stress = 31.0),
            Wellness("2026-10-26", restingHR = 56.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 26887.0),
            Wellness("2026-10-27", restingHR = 53.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 22351.0, stress = 34.0),
            Wellness("2026-10-28", restingHR = 53.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", stress = 31.0),
            Wellness("2026-10-29", restingHR = 52.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", stress = 34.0),
            Wellness("2026-10-30", restingHR = 56.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", stress = 39.0),
            Wellness("2026-10-31", restingHR = 54.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", stress = 32.0),
            Wellness("2026-11-01", restingHR = 53.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 24329.0, stress = 32.0),
            Wellness("2026-11-02", restingHR = 52.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", sleepSecs = 23732.0, stress = 31.0),
            Wellness("2026-11-03", restingHR = 54.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", sleepSecs = 24753.0, stress = 38.0),
            Wellness("2026-11-04", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 22494.0),
            Wellness("2026-11-05", sleepSecs = 27884.0, stress = 31.0),
            Wellness("2026-11-06", restingHR = 54.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 26388.0),
            Wellness("2026-11-07", restingHR = 54.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 27339.0, stress = 31.0),
            Wellness("2026-11-08", restingHR = 53.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 26961.0, stress = 37.0),
            Wellness("2026-11-09", restingHR = 52.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 24874.0, stress = 34.0),
            Wellness("2026-11-10", restingHR = 52.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", sleepSecs = 26685.0, stress = 38.0),
            Wellness("2026-11-11", restingHR = 55.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 22994.0, stress = 33.0),
            Wellness("2026-11-12", restingHR = 56.0, rhrSrc = "jam", sleepSecs = 25148.0, stress = 38.0),
            Wellness("2026-11-13", restingHR = 55.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 27786.0, stress = 31.0),
            Wellness("2026-11-14", restingHR = 55.0, rhrSrc = "jam", sleepSecs = 26122.0, stress = 32.0),
            Wellness("2026-11-15", restingHR = 52.0, rhrSrc = "jam", sleepSecs = 24286.0, stress = 40.0),
        )
        val vx = mapOf<Long, String>(1794301200000L to "long")
        return snap(50.0, 190.0, 30, 0, acts, wl, vx)
    }

    @Test
    fun scenario_D_hrv_tak_berubah() {
        val sn = scenario_D_hrv_tak_berubah_snap()
        // estMax
        val e = sn.estMax()
        near(14.931627804568661, e.m, "estMax.m")
        near(16.590697560631845, e.perf, "estMax.perf")
        near(12.570044581618655, e.hrr, "estMax.hrr")
        assertEquals("HR+pace dari 35 lari (56 hari)", e.src)
        assertEquals("baik", e.conf)
        assertEquals(35, e.n)
        // stage
        val g = sn.stage()
        assertEquals(4, g.s)
        near(14.931627804568661, g.m, "stage.m")
        assertEquals(4, g.raw)
        assertEquals(false, g.unknown)
        assertEquals(4, g.afS)
        // load
        val expLoads = listOf<Int>(55, 23, 35, 47, 34, 96, 47, 58, 62, 89, 28, 16, 19, 81, 67, 75, 50, 94, 116, 61, 22, 105, 28, 105, 41, 71, 15, 68, 53, 89, 38, 79, 38, 47, 43, 49, 37, 67, 60, 110, 38, 48, 25, 91, 27, 11, 12, 34)
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(248, sn.loadDays(7))
        assertEquals(1079, sn.loadDays(28))
        // rhr28 / maxHr
        assertEquals(Rhr28(53, 28), sn.rhr28())
        near(190.0, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(false, au.lock, "lock")
        assertEquals(false, au.severe, "severe")
        assertEquals(false, au.warn, "warn")
        assertEquals("", au.why)
        assertEquals(1, au.hrvPct)
        assertEquals(1, au.rhrD)
        assertEquals(70, au.hrv)
        assertEquals(52, au.rhr)
        near(6.746111111111111, au.sleepH, "sleepH")
        near(40.0, au.stress, "stress")
        assertEquals(5, au.stressD)
        // readiness
        assertEquals(74, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf<List<String>>(
            listOf(1794045600000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794124800000L.toString(), "thr", "rendah", "intensitas di atas easy (≈ 78% Max)", "false"),
            listOf(1794218400000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794301200000L.toString(), "long", "tinggi", "dikonfirmasi manual", "true"),
            listOf(1794384000000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794474000000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794650400000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794736800000L.toString(), "long", "sedang", "8.1 km, 45% lebih jauh dari biasamu", "false"),
        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }

    private fun scenario_E_pemula_lambat_tahap1_snap(): Snapshot {
        val acts = listOf<Act>(
            act(1789552800000L, "RUN", 62.54, 6.49, 148.0, 40.0, "hw1789552800000", "Lari 60", 160.0, 0.0),
            act(1789718400000L, "RUN", 44.18, 4.31, 142.0, 52.0, "hw1789718400000", "Lari 58", 154.0, 0.0),
            act(1789804800000L, "RUN", 29.31, 2.99, 152.0, 68.0, "hw1789804800000", "Lari 57", 164.0, 0.0),
            act(1789981200000L, "RUN", 33.33, 3.42, 147.0, 2.0, "hw1789981200000", "Lari 55", 159.0, 0.0),
            act(1790067600000L, "RUN", 22.43, 2.19, 173.0, 53.0, "hw1790067600000", "Lari 54", 185.0, 0.0),
            act(1790240400000L, "RUN", 83.81, 7.98, 162.0, 71.0, "hw1790240400000", "Lari 52", 174.0, 0.0),
            act(1790323200000L, "RUN", 36.47, 3.14, 166.0, 20.0, "hw1790323200000", "Lari 51", 178.0, 0.0),
            act(1790499600000L, "OTHER", 35.11, 0.0, 143.0, 5.0, "hw1790499600000", "Lari 49", 155.0, 0.0),
            act(1790589600000L, "RUN", 41.57, 4.24, 153.0, 5.0, "hw1790589600000", "Lari 48", 165.0, 0.0),
            act(1790676000000L, "RUN", 76.17, 6.92, 161.0, 5.0, "hw1790676000000", "Lari 47", 173.0, 0.0),
            act(1790762400000L, "RUN", 69.01, 6.97, 149.0, 22.0, "hw1790762400000", "Lari 46", 161.0, 0.0),
            act(1790845200000L, "WALK", 55.71, 3.76, 172.0, 18.0, "hw1790845200000", "Lari 45", 184.0, 0.0),
            act(1790928000000L, "RUN", 72.45, 6.5, 163.0, 68.0, "hw1790928000000", "Lari 44", 175.0, 0.0),
            act(1791194400000L, "RUN", 72.98, 6.49, 163.0, 4.0, "hw1791194400000", "Lari 41", 175.0, 0.0),
            act(1791363600000L, "RUN", 77.16, 7.23, 143.0, 19.0, "hw1791363600000", "Lari 39", 155.0, 0.0),
            act(1791712800000L, "RUN", 45.51, 3.9, 167.0, 31.0, "hw1791712800000", "Lari 35", 179.0, 0.0),
            act(1791885600000L, "OTHER", 30.5, 0.0, 163.0, 11.0, "hw1791885600000", "Lari 33", 175.0, 0.0),
            act(1791968400000L, "OTHER", 44.8, 0.0, 165.0, 26.0, "hw1791968400000", "Lari 32", 177.0, 0.0),
            act(1792227600000L, "RUN", 83.46, 7.44, 145.0, 19.0, "hw1792227600000", "Lari 29", 157.0, 0.0),
            act(1792483200000L, "RUN", 76.23, 7.49, 148.0, 59.0, "hw1792483200000", "Lari 26", 160.0, 0.0),
            act(1792576800000L, "WALK", 23.06, 3.64, 169.0, 48.0, "hw1792576800000", "Lari 25", 181.0, 0.0),
            act(1792746000000L, "RUN", 35.14, 3.28, 153.0, 29.0, "hw1792746000000", "Lari 23", 165.0, 0.0),
            act(1792836000000L, "WALK", 41.33, 2.18, 170.0, 54.0, "hw1792836000000", "Lari 22", 182.0, 0.0),
            act(1792918800000L, "RUN", 85.07, 8.05, 154.0, 38.0, "hw1792918800000", "Lari 21", 166.0, 0.0),
            act(1793001600000L, "WALK", 42.87, 1.17, 145.0, 7.0, "hw1793001600000", "Lari 20", 157.0, 0.0),
            act(1793091600000L, "RUN", 25.97, 2.67, 150.0, 12.0, "hw1793091600000", "Lari 19", 162.0, 0.0),
            act(1793260800000L, "RUN", 32.87, 3.18, 166.0, 58.0, "hw1793260800000", "Lari 17", 178.0, 0.0),
            act(1793354400000L, "RUN", 76.57, 6.98, 173.0, 17.0, "hw1793354400000", "Lari 16", 185.0, 0.0),
            act(1793523600000L, "RUN", 41.63, 3.76, 173.0, 19.0, "hw1793523600000", "Lari 14", 185.0, 0.0),
            act(1793610000000L, "RUN", 39.48, 4.12, 161.0, 40.0, "hw1793610000000", "Lari 13", 173.0, 0.0),
            act(1793782800000L, "RUN", 67.18, 5.93, 160.0, 69.0, "hw1793782800000", "Lari 11", 172.0, 0.0),
            act(1793952000000L, "RUN", 73.15, 7.45, 147.0, 57.0, "hw1793952000000", "Lari 9", 159.0, 0.0),
            act(1794042000000L, "RUN", 74.64, 6.95, 156.0, 77.0, "hw1794042000000", "Lari 8", 168.0, 0.0),
            act(1794132000000L, "RUN", 67.32, 6.82, 141.0, 7.0, "hw1794132000000", "Lari 7", 153.0, 0.0),
            act(1794211200000L, "OTHER", 42.32, 0.0, 156.0, 19.0, "hw1794211200000", "Lari 6", 168.0, 0.0),
            act(1794297600000L, "WALK", 33.66, 3.57, 157.0, 37.0, "hw1794297600000", "Lari 5", 169.0, 0.0),
            act(1794556800000L, "RUN", 63.3, 6.33, 145.0, 50.0, "hw1794556800000", "Lari 2", 157.0, 0.0),
            act(1794646800000L, "OTHER", 39.29, 0.0, 148.0, 22.0, "hw1794646800000", "Lari 1", 160.0, 0.0),
            act(1794733200000L, "RUN", 50.36, 5.05, 172.0, 65.0, "hw1794733200000", "Lari 0", 184.0, 0.0),
        )
        val wl = listOf<Wellness>(
            Wellness("2026-10-16", restingHR = 54.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 25528.0, stress = 32.0),
            Wellness("2026-10-17", restingHR = 52.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 27628.0),
            Wellness("2026-10-18", restingHR = 55.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 25626.0, stress = 39.0),
            Wellness("2026-10-19", restingHR = 55.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 24443.0, stress = 34.0),
            Wellness("2026-10-20", restingHR = 55.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 22361.0, stress = 38.0),
            Wellness("2026-10-21", restingHR = 55.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", sleepSecs = 24225.0, stress = 31.0),
            Wellness("2026-10-22", restingHR = 53.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 23055.0, stress = 31.0),
            Wellness("2026-10-23", restingHR = 56.0, rhrSrc = "jam", sleepSecs = 24320.0),
            Wellness("2026-10-24", restingHR = 55.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 27959.0, stress = 33.0),
            Wellness("2026-10-25", restingHR = 54.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 25558.0, stress = 34.0),
            Wellness("2026-10-26", restingHR = 53.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 28045.0, stress = 31.0),
            Wellness("2026-10-27", restingHR = 52.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 27386.0, stress = 37.0),
            Wellness("2026-10-28", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 26027.0, stress = 33.0),
            Wellness("2026-10-29", restingHR = 54.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 25664.0, stress = 30.0),
            Wellness("2026-10-30", restingHR = 54.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", sleepSecs = 26757.0, stress = 31.0),
            Wellness("2026-10-31", restingHR = 54.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 26806.0, stress = 31.0),
            Wellness("2026-11-01", restingHR = 55.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 27741.0, stress = 33.0),
            Wellness("2026-11-02", restingHR = 54.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 28051.0, stress = 38.0),
            Wellness("2026-11-03", restingHR = 52.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 22730.0, stress = 32.0),
            Wellness("2026-11-04", restingHR = 53.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 24329.0, stress = 33.0),
            Wellness("2026-11-05", restingHR = 53.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 22512.0, stress = 36.0),
            Wellness("2026-11-06", restingHR = 55.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", sleepSecs = 25309.0, stress = 34.0),
            Wellness("2026-11-07", restingHR = 54.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 23803.0, stress = 33.0),
            Wellness("2026-11-08", restingHR = 53.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 24470.0),
            Wellness("2026-11-09", restingHR = 55.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 23855.0),
            Wellness("2026-11-10", restingHR = 56.0, rhrSrc = "jam", hrv = 62.0, hrvSrc = "jam", sleepSecs = 26617.0, stress = 37.0),
            Wellness("2026-11-11", restingHR = 55.0, rhrSrc = "jam", sleepSecs = 25638.0, stress = 36.0),
            Wellness("2026-11-12", restingHR = 54.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 26913.0, stress = 32.0),
            Wellness("2026-11-13", restingHR = 54.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 24050.0, stress = 31.0),
            Wellness("2026-11-14", restingHR = 53.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 26053.0, stress = 37.0),
            Wellness("2026-11-15", restingHR = 55.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", stress = 31.0),
        )
        val vx = mapOf<Long, String>(1794132000000L to "long")
        return snap(66.0, 185.0, 45, 0, acts, wl, vx)
    }

    @Test
    fun scenario_E_pemula_lambat_tahap1() {
        val sn = scenario_E_pemula_lambat_tahap1_snap()
        // estMax
        val e = sn.estMax()
        near(8.21663393647728, e.m, "estMax.m")
        near(9.129593262752532, e.perf, "estMax.perf")
        near(6.076784980384831, e.hrr, "estMax.hrr")
        assertEquals("HR+pace dari 26 lari (56 hari)", e.src)
        assertEquals("baik", e.conf)
        assertEquals(26, e.n)
        // stage
        val g = sn.stage()
        assertEquals(2, g.s)
        near(8.21663393647728, g.m, "stage.m")
        assertEquals(2, g.raw)
        assertEquals(false, g.unknown)
        assertEquals(2, g.afS)
        // load
        val expLoads = listOf<Int>(64, 39, 33, 33, 39, 117, 55, 28, 48, 104, 72, 19, 104, 104, 70, 71, 24, 36, 79, 78, 8, 40, 14, 100, 15, 28, 50, 133, 72, 54, 90, 73, 92, 58, 34, 12, 60, 31, 86)
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(281, sn.loadDays(7))
        assertEquals(1128, sn.loadDays(28))
        // rhr28 / maxHr
        assertEquals(Rhr28(54, 29), sn.rhr28())
        near(185.0, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(false, au.lock, "lock")
        assertEquals(false, au.severe, "severe")
        assertEquals(false, au.warn, "warn")
        assertEquals("", au.why)
        assertEquals(2, au.hrvPct)
        assertEquals(0, au.rhrD)
        assertEquals(66, au.hrv)
        assertEquals(55, au.rhr)
        near(7.236944444444444, au.sleepH, "sleepH")
        near(31.0, au.stress, "stress")
        assertEquals(-2, au.stressD)
        // readiness
        assertEquals(76, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf<List<String>>(
            listOf(1793952000000L.toString(), "long", "sedang", "7.5 km, 81% lebih jauh dari biasamu", "false"),
            listOf(1794042000000L.toString(), "thr", "rendah", "training load tinggi (92)", "false"),
            listOf(1794132000000L.toString(), "long", "tinggi", "dikonfirmasi manual", "true"),
            listOf(1794211200000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794297600000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794556800000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794646800000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794733200000L.toString(), "thr", "rendah", "training load tinggi (86)", "false"),
        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }

    private fun scenario_F_histeresis_naik_tertahan_snap(): Snapshot {
        val acts = listOf<Act>(
            act(1789808400000L, "WALK", 42.07, 1.14, 149.0, 0.0, "hw1789808400000", "Lari 57", 161.0, 0.0),
            act(1789981200000L, "RUN", 75.96, 7.13, 153.0, 52.0, "hw1789981200000", "Lari 55", 165.0, 0.0),
            act(1790064000000L, "RUN", 64.42, 6.31, 146.0, 15.0, "hw1790064000000", "Lari 54", 158.0, 0.0),
            act(1790236800000L, "RUN", 49.67, 5.58, 149.0, 17.0, "hw1790236800000", "Lari 52", 161.0, 0.0),
            act(1790503200000L, "RUN", 140.07, 13.76, 145.0, 45.0, "hw1790503200000", "Lari 49", 157.0, 0.0),
            act(1790586000000L, "RUN", 51.84, 5.03, 157.0, 31.0, "hw1790586000000", "Lari 48", 169.0, 0.0),
            act(1790848800000L, "RUN", 38.95, 4.35, 170.0, 75.0, "hw1790848800000", "Lari 45", 182.0, 0.0),
            act(1790931600000L, "RUN", 70.6, 7.16, 152.0, 34.0, "hw1790931600000", "Lari 44", 164.0, 0.0),
            act(1791021600000L, "RUN", 44.24, 4.89, 149.0, 4.0, "hw1791021600000", "Lari 43", 161.0, 0.0),
            act(1791187200000L, "RUN", 29.6, 3.4, 155.0, 57.0, "hw1791187200000", "Lari 41", 167.0, 0.0),
            act(1791280800000L, "RUN", 46.96, 5.44, 154.0, 68.0, "hw1791280800000", "Lari 40", 166.0, 0.0),
            act(1791446400000L, "RUN", 28.89, 2.75, 142.0, 31.0, "hw1791446400000", "Lari 38", 154.0, 0.0),
            act(1791705600000L, "RUN", 77.58, 8.49, 140.0, 78.0, "hw1791705600000", "Lari 35", 152.0, 0.0),
            act(1791968400000L, "RUN", 41.64, 4.51, 168.0, 17.0, "hw1791968400000", "Lari 32", 180.0, 0.0),
            act(1792144800000L, "RUN", 40.53, 4.18, 158.0, 1.0, "hw1792144800000", "Lari 30", 170.0, 0.0),
            act(1792396800000L, "RUN", 35.95, 3.83, 174.0, 42.0, "hw1792396800000", "Lari 27", 186.0, 0.0),
            act(1792486800000L, "OTHER", 30.85, 0.0, 150.0, 29.0, "hw1792486800000", "Lari 26", 162.0, 0.0),
            act(1792656000000L, "RUN", 23.67, 2.34, 170.0, 75.0, "hw1792656000000", "Lari 24", 182.0, 0.0),
            act(1792832400000L, "RUN", 22.21, 2.16, 148.0, 18.0, "hw1792832400000", "Lari 22", 160.0, 0.0),
            act(1793008800000L, "OTHER", 36.69, 0.0, 165.0, 57.0, "hw1793008800000", "Lari 20", 177.0, 0.0),
            act(1793088000000L, "RUN", 29.34, 3.35, 162.0, 31.0, "hw1793088000000", "Lari 19", 174.0, 0.0),
            act(1793178000000L, "RUN", 33.61, 3.66, 174.0, 59.0, "hw1793178000000", "Lari 18", 186.0, 0.0),
            act(1793264400000L, "RUN", 65.05, 6.16, 141.0, 26.0, "hw1793264400000", "Lari 17", 153.0, 0.0),
            act(1793347200000L, "OTHER", 22.23, 0.0, 161.0, 35.0, "hw1793347200000", "Lari 16", 173.0, 0.0),
            act(1793437200000L, "RUN", 55.57, 6.01, 142.0, 52.0, "hw1793437200000", "Lari 15", 154.0, 0.0),
            act(1793692800000L, "RUN", 38.68, 3.89, 143.0, 34.0, "hw1793692800000", "Lari 12", 155.0, 0.0),
            act(1793786400000L, "RUN", 33.74, 3.27, 157.0, 59.0, "hw1793786400000", "Lari 11", 169.0, 0.0),
            act(1793959200000L, "RUN", 61.49, 7.03, 146.0, 21.0, "hw1793959200000", "Lari 9", 158.0, 0.0),
            act(1794042000000L, "RUN", 25.46, 2.66, 142.0, 19.0, "hw1794042000000", "Lari 8", 154.0, 0.0),
            act(1794132000000L, "OTHER", 50.23, 0.0, 151.0, 44.0, "hw1794132000000", "Lari 7", 163.0, 0.0),
            act(1794301200000L, "WALK", 35.55, 3.72, 147.0, 14.0, "hw1794301200000", "Lari 5", 159.0, 0.0),
            act(1794384000000L, "RUN", 66.39, 6.55, 167.0, 65.0, "hw1794384000000", "Lari 4", 179.0, 0.0),
            act(1794474000000L, "OTHER", 25.17, 0.0, 172.0, 49.0, "hw1794474000000", "Lari 3", 184.0, 0.0),
            act(1794560400000L, "RUN", 84.39, 7.97, 168.0, 31.0, "hw1794560400000", "Lari 2", 180.0, 0.0),
            act(1794643200000L, "RUN", 21.67, 2.27, 146.0, 44.0, "hw1794643200000", "Lari 1", 158.0, 0.0),
            act(1794733200000L, "RUN", 100.66, 11.01, 167.0, 3.0, "hw1794733200000", "Lari 0", 179.0, 0.0),
        )
        val wl = listOf<Wellness>(
            Wellness("2026-10-16", restingHR = 54.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 23877.0, stress = 35.0),
            Wellness("2026-10-17", restingHR = 54.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 23970.0),
            Wellness("2026-10-18", restingHR = 54.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 22661.0),
            Wellness("2026-10-19", hrv = 60.0, hrvSrc = "jam", sleepSecs = 26664.0),
            Wellness("2026-10-20", restingHR = 54.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 22695.0, stress = 32.0),
            Wellness("2026-10-21", restingHR = 55.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 23910.0, stress = 39.0),
            Wellness("2026-10-22", restingHR = 53.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 24593.0, stress = 40.0),
            Wellness("2026-10-23", restingHR = 53.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 22679.0, stress = 33.0),
            Wellness("2026-10-24", restingHR = 53.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 23007.0, stress = 40.0),
            Wellness("2026-10-25", restingHR = 54.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 24407.0, stress = 33.0),
            Wellness("2026-10-26", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 25528.0),
            Wellness("2026-10-27", restingHR = 54.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 22493.0, stress = 37.0),
            Wellness("2026-10-28", restingHR = 55.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", sleepSecs = 24985.0, stress = 37.0),
            Wellness("2026-10-29", restingHR = 55.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 25537.0, stress = 32.0),
            Wellness("2026-10-30", restingHR = 53.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 22704.0, stress = 30.0),
            Wellness("2026-10-31", restingHR = 54.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 22485.0, stress = 33.0),
            Wellness("2026-11-01", restingHR = 55.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 23633.0, stress = 31.0),
            Wellness("2026-11-02", restingHR = 54.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 25930.0),
            Wellness("2026-11-03", restingHR = 55.0, rhrSrc = "jam", sleepSecs = 27764.0, stress = 39.0),
            Wellness("2026-11-04", hrv = 64.0, hrvSrc = "jam", sleepSecs = 22601.0, stress = 40.0),
            Wellness("2026-11-05", restingHR = 55.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 26594.0, stress = 33.0),
            Wellness("2026-11-06", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 22915.0),
            Wellness("2026-11-07", restingHR = 52.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 26669.0),
            Wellness("2026-11-08", restingHR = 55.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 27346.0),
            Wellness("2026-11-09", restingHR = 54.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 26380.0, stress = 39.0),
            Wellness("2026-11-10", restingHR = 55.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 27998.0, stress = 39.0),
            Wellness("2026-11-11", restingHR = 55.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 26463.0, stress = 34.0),
            Wellness("2026-11-12", restingHR = 55.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", stress = 39.0),
            Wellness("2026-11-13", restingHR = 52.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 24551.0, stress = 38.0),
            Wellness("2026-11-14", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 24322.0),
            Wellness("2026-11-15", restingHR = 54.0, rhrSrc = "jam", sleepSecs = 26365.0),
        )
        val vx = mapOf<Long, String>(1794560400000L to "long")
        return snap(62.0, 185.0, 40, 2, acts, wl, vx)
    }

    @Test
    fun scenario_F_histeresis_naik_tertahan() {
        val sn = scenario_F_histeresis_naik_tertahan_snap()
        // estMax
        val e = sn.estMax()
        near(9.278882833787467, e.m, "estMax.m")
        near(10.309869815319407, e.perf, "estMax.perf")
        near(7.078001277139207, e.hrr, "estMax.hrr")
        assertEquals("HR+pace dari 29 lari (56 hari)", e.src)
        assertEquals("baik", e.conf)
        assertEquals(29, e.n)
        // stage
        val g = sn.stage()
        assertEquals(2, g.s)
        near(9.278882833787467, g.m, "stage.m")
        assertEquals(3, g.raw)
        assertEquals(false, g.unknown)
        assertEquals(2, g.afS)
        // load
        val expLoads = listOf<Int>(15, 89, 65, 53, 137, 67, 65, 81, 48, 36, 57, 26, 67, 67, 53, 64, 25, 39, 23, 29, 42, 60, 58, 18, 51, 36, 43, 62, 23, 40, 12, 104, 20, 135, 22, 158)
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(491, sn.loadDays(7))
        assertEquals(1064, sn.loadDays(28))
        // rhr28 / maxHr
        assertEquals(Rhr28(54, 27), sn.rhr28())
        near(185.0, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(false, au.lock, "lock")
        assertEquals(false, au.severe, "severe")
        assertEquals(false, au.warn, "warn")
        assertEquals("", au.why)
        assertEquals(-1, au.hrvPct)
        assertEquals(-1, au.rhrD)
        assertEquals(65, au.hrv)
        assertEquals(54, au.rhr)
        near(7.323611111111111, au.sleepH, "sleepH")
        near(38.0, au.stress, "stress")
        assertEquals(1, au.stressD)
        // readiness
        assertEquals(51, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf<List<String>>(
            listOf(1794042000000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794132000000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794301200000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794384000000L.toString(), "long", "sedang", "6.6 km, 71% lebih jauh dari biasamu", "false"),
            listOf(1794474000000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794560400000L.toString(), "long", "tinggi", "dikonfirmasi manual", "true"),
            listOf(1794643200000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794733200000L.toString(), "long", "sedang", "11 km, 187% lebih jauh dari biasamu", "false"),
        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }

    private fun scenario_I_histeresis_turun_tertahan_snap(): Snapshot {
        val acts = listOf<Act>(
            act(1789808400000L, "WALK", 42.07, 1.14, 149.0, 0.0, "hw1789808400000", "Lari 57", 161.0, 0.0),
            act(1789981200000L, "RUN", 76.67, 7.13, 153.0, 52.0, "hw1789981200000", "Lari 55", 165.0, 0.0),
            act(1790064000000L, "RUN", 65.06, 6.31, 146.0, 15.0, "hw1790064000000", "Lari 54", 158.0, 0.0),
            act(1790236800000L, "RUN", 50.23, 5.58, 149.0, 17.0, "hw1790236800000", "Lari 52", 161.0, 0.0),
            act(1790503200000L, "RUN", 141.45, 13.76, 145.0, 45.0, "hw1790503200000", "Lari 49", 157.0, 0.0),
            act(1790586000000L, "RUN", 52.35, 5.03, 157.0, 31.0, "hw1790586000000", "Lari 48", 169.0, 0.0),
            act(1790848800000L, "RUN", 39.39, 4.35, 170.0, 75.0, "hw1790848800000", "Lari 45", 182.0, 0.0),
            act(1790931600000L, "RUN", 71.32, 7.16, 152.0, 34.0, "hw1790931600000", "Lari 44", 164.0, 0.0),
            act(1791021600000L, "RUN", 44.73, 4.89, 149.0, 4.0, "hw1791021600000", "Lari 43", 161.0, 0.0),
            act(1791187200000L, "RUN", 29.94, 3.4, 155.0, 57.0, "hw1791187200000", "Lari 41", 167.0, 0.0),
            act(1791280800000L, "RUN", 47.5, 5.44, 154.0, 68.0, "hw1791280800000", "Lari 40", 166.0, 0.0),
            act(1791446400000L, "RUN", 29.17, 2.75, 142.0, 31.0, "hw1791446400000", "Lari 38", 154.0, 0.0),
            act(1791705600000L, "RUN", 78.42, 8.49, 140.0, 78.0, "hw1791705600000", "Lari 35", 152.0, 0.0),
            act(1791968400000L, "RUN", 42.09, 4.51, 168.0, 17.0, "hw1791968400000", "Lari 32", 180.0, 0.0),
            act(1792144800000L, "RUN", 40.95, 4.18, 158.0, 1.0, "hw1792144800000", "Lari 30", 170.0, 0.0),
            act(1792396800000L, "RUN", 36.33, 3.83, 174.0, 42.0, "hw1792396800000", "Lari 27", 186.0, 0.0),
            act(1792486800000L, "OTHER", 30.85, 0.0, 150.0, 29.0, "hw1792486800000", "Lari 26", 162.0, 0.0),
            act(1792656000000L, "RUN", 23.9, 2.34, 170.0, 75.0, "hw1792656000000", "Lari 24", 182.0, 0.0),
            act(1792832400000L, "RUN", 22.43, 2.16, 148.0, 18.0, "hw1792832400000", "Lari 22", 160.0, 0.0),
            act(1793008800000L, "OTHER", 36.69, 0.0, 165.0, 57.0, "hw1793008800000", "Lari 20", 177.0, 0.0),
            act(1793088000000L, "RUN", 29.67, 3.35, 162.0, 31.0, "hw1793088000000", "Lari 19", 174.0, 0.0),
            act(1793178000000L, "RUN", 33.97, 3.66, 174.0, 59.0, "hw1793178000000", "Lari 18", 186.0, 0.0),
            act(1793264400000L, "RUN", 65.67, 6.16, 141.0, 26.0, "hw1793264400000", "Lari 17", 153.0, 0.0),
            act(1793347200000L, "OTHER", 22.23, 0.0, 161.0, 35.0, "hw1793347200000", "Lari 16", 173.0, 0.0),
            act(1793437200000L, "RUN", 56.17, 6.01, 142.0, 52.0, "hw1793437200000", "Lari 15", 154.0, 0.0),
            act(1793692800000L, "RUN", 39.07, 3.89, 143.0, 34.0, "hw1793692800000", "Lari 12", 155.0, 0.0),
            act(1793786400000L, "RUN", 34.06, 3.27, 157.0, 59.0, "hw1793786400000", "Lari 11", 169.0, 0.0),
            act(1793959200000L, "RUN", 62.2, 7.03, 146.0, 21.0, "hw1793959200000", "Lari 9", 158.0, 0.0),
            act(1794042000000L, "RUN", 25.73, 2.66, 142.0, 19.0, "hw1794042000000", "Lari 8", 154.0, 0.0),
            act(1794132000000L, "OTHER", 50.23, 0.0, 151.0, 44.0, "hw1794132000000", "Lari 7", 163.0, 0.0),
            act(1794301200000L, "WALK", 35.55, 3.72, 147.0, 14.0, "hw1794301200000", "Lari 5", 159.0, 0.0),
            act(1794384000000L, "RUN", 67.05, 6.55, 167.0, 65.0, "hw1794384000000", "Lari 4", 179.0, 0.0),
            act(1794474000000L, "OTHER", 25.17, 0.0, 172.0, 49.0, "hw1794474000000", "Lari 3", 184.0, 0.0),
            act(1794560400000L, "RUN", 85.19, 7.97, 168.0, 31.0, "hw1794560400000", "Lari 2", 180.0, 0.0),
            act(1794643200000L, "RUN", 21.9, 2.27, 146.0, 44.0, "hw1794643200000", "Lari 1", 158.0, 0.0),
            act(1794733200000L, "RUN", 101.76, 11.01, 167.0, 3.0, "hw1794733200000", "Lari 0", 179.0, 0.0),
        )
        val wl = listOf<Wellness>(
            Wellness("2026-10-16", restingHR = 54.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 23877.0, stress = 35.0),
            Wellness("2026-10-17", restingHR = 54.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 23970.0),
            Wellness("2026-10-18", restingHR = 54.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 22661.0),
            Wellness("2026-10-19", hrv = 60.0, hrvSrc = "jam", sleepSecs = 26664.0),
            Wellness("2026-10-20", restingHR = 54.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 22695.0, stress = 32.0),
            Wellness("2026-10-21", restingHR = 55.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 23910.0, stress = 39.0),
            Wellness("2026-10-22", restingHR = 53.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 24593.0, stress = 40.0),
            Wellness("2026-10-23", restingHR = 53.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 22679.0, stress = 33.0),
            Wellness("2026-10-24", restingHR = 53.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 23007.0, stress = 40.0),
            Wellness("2026-10-25", restingHR = 54.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 24407.0, stress = 33.0),
            Wellness("2026-10-26", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 25528.0),
            Wellness("2026-10-27", restingHR = 54.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 22493.0, stress = 37.0),
            Wellness("2026-10-28", restingHR = 55.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", sleepSecs = 24985.0, stress = 37.0),
            Wellness("2026-10-29", restingHR = 55.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 25537.0, stress = 32.0),
            Wellness("2026-10-30", restingHR = 53.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 22704.0, stress = 30.0),
            Wellness("2026-10-31", restingHR = 54.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 22485.0, stress = 33.0),
            Wellness("2026-11-01", restingHR = 55.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 23633.0, stress = 31.0),
            Wellness("2026-11-02", restingHR = 54.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 25930.0),
            Wellness("2026-11-03", restingHR = 55.0, rhrSrc = "jam", sleepSecs = 27764.0, stress = 39.0),
            Wellness("2026-11-04", hrv = 64.0, hrvSrc = "jam", sleepSecs = 22601.0, stress = 40.0),
            Wellness("2026-11-05", restingHR = 55.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 26594.0, stress = 33.0),
            Wellness("2026-11-06", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 22915.0),
            Wellness("2026-11-07", restingHR = 52.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 26669.0),
            Wellness("2026-11-08", restingHR = 55.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", sleepSecs = 27346.0),
            Wellness("2026-11-09", restingHR = 54.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", sleepSecs = 26380.0, stress = 39.0),
            Wellness("2026-11-10", restingHR = 55.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 27998.0, stress = 39.0),
            Wellness("2026-11-11", restingHR = 55.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 26463.0, stress = 34.0),
            Wellness("2026-11-12", restingHR = 55.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", stress = 39.0),
            Wellness("2026-11-13", restingHR = 52.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 24551.0, stress = 38.0),
            Wellness("2026-11-14", restingHR = 53.0, rhrSrc = "jam", sleepSecs = 24322.0),
            Wellness("2026-11-15", restingHR = 54.0, rhrSrc = "jam", sleepSecs = 26365.0),
        )
        val vx = mapOf<Long, String>(1794560400000L to "long")
        return snap(62.0, 185.0, 40, 3, acts, wl, vx)
    }

    @Test
    fun scenario_I_histeresis_turun_tertahan() {
        val sn = scenario_I_histeresis_turun_tertahan_snap()
        // estMax
        val e = sn.estMax()
        near(9.158991825613079, e.m, "estMax.m")
        near(10.176657584014531, e.perf, "estMax.perf")
        near(6.985842911877395, e.hrr, "estMax.hrr")
        assertEquals("HR+pace dari 29 lari (56 hari)", e.src)
        assertEquals("baik", e.conf)
        assertEquals(29, e.n)
        // stage
        val g = sn.stage()
        assertEquals(3, g.s)
        near(9.158991825613079, g.m, "stage.m")
        assertEquals(2, g.raw)
        assertEquals(false, g.unknown)
        assertEquals(3, g.afS)
        // load
        val expLoads = listOf<Int>(15, 90, 65, 54, 139, 67, 65, 82, 48, 37, 57, 27, 68, 67, 54, 65, 25, 40, 24, 29, 42, 61, 58, 18, 51, 36, 44, 62, 23, 40, 12, 105, 20, 136, 22, 160)
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(495, sn.loadDays(7))
        assertEquals(1073, sn.loadDays(28))
        // rhr28 / maxHr
        assertEquals(Rhr28(54, 27), sn.rhr28())
        near(185.0, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(false, au.lock, "lock")
        assertEquals(false, au.severe, "severe")
        assertEquals(false, au.warn, "warn")
        assertEquals("", au.why)
        assertEquals(-1, au.hrvPct)
        assertEquals(-1, au.rhrD)
        assertEquals(65, au.hrv)
        assertEquals(54, au.rhr)
        near(7.323611111111111, au.sleepH, "sleepH")
        near(38.0, au.stress, "stress")
        assertEquals(1, au.stressD)
        // readiness
        assertEquals(51, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf<List<String>>(
            listOf(1794042000000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794132000000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794301200000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794384000000L.toString(), "long", "sedang", "6.6 km, 71% lebih jauh dari biasamu", "false"),
            listOf(1794474000000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794560400000L.toString(), "long", "tinggi", "dikonfirmasi manual", "true"),
            listOf(1794643200000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794733200000L.toString(), "long", "sedang", "11 km, 187% lebih jauh dari biasamu", "false"),
        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }

    private fun scenario_G_menengah_tahap3_snap(): Snapshot {
        val acts = listOf<Act>(
            act(1789545600000L, "RUN", 32.25, 4.92, 169.0, 71.0, "hw1789545600000", "Lari 60", 181.0, 0.0),
            act(1789632000000L, "RUN", 56.58, 7.46, 157.0, 55.0, "hw1789632000000", "Lari 59", 169.0, 0.0),
            act(1789718400000L, "RUN", 17.68, 2.45, 158.0, 78.0, "hw1789718400000", "Lari 58", 170.0, 0.0),
            act(1790413200000L, "RUN", 39.3, 5.64, 149.0, 48.0, "hw1790413200000", "Lari 50", 161.0, 0.0),
            act(1790586000000L, "RUN", 39.7, 5.53, 144.0, 24.0, "hw1790586000000", "Lari 48", 156.0, 0.0),
            act(1790668800000L, "OTHER", 56.27, 0.0, 157.0, 65.0, "hw1790668800000", "Lari 47", 169.0, 0.0),
            act(1790758800000L, "RUN", 48.17, 5.9, 154.0, 31.0, "hw1790758800000", "Lari 46", 166.0, 0.0),
            act(1790841600000L, "RUN", 30.62, 4.25, 152.0, 38.0, "hw1790841600000", "Lari 45", 164.0, 0.0),
            act(1790931600000L, "WALK", 40.75, 2.53, 153.0, 38.0, "hw1790931600000", "Lari 44", 165.0, 0.0),
            act(1791018000000L, "RUN", 30.58, 4.73, 144.0, 3.0, "hw1791018000000", "Lari 43", 156.0, 0.0),
            act(1791100800000L, "RUN", 34.44, 4.93, 162.0, 4.0, "hw1791100800000", "Lari 42", 174.0, 0.0),
            act(1791194400000L, "RUN", 40.08, 5.27, 151.0, 65.0, "hw1791194400000", "Lari 41", 163.0, 0.0),
            act(1791280800000L, "RUN", 16.57, 2.06, 145.0, 3.0, "hw1791280800000", "Lari 40", 157.0, 0.0),
            act(1791360000000L, "WALK", 46.44, 2.42, 153.0, 7.0, "hw1791360000000", "Lari 39", 165.0, 0.0),
            act(1791712800000L, "RUN", 67.93, 8.09, 158.0, 65.0, "hw1791712800000", "Lari 35", 170.0, 0.0),
            act(1791795600000L, "RUN", 14.05, 2.06, 147.0, 37.0, "hw1791795600000", "Lari 34", 159.0, 0.0),
            act(1791885600000L, "RUN", 16.48, 2.45, 167.0, 27.0, "hw1791885600000", "Lari 33", 179.0, 0.0),
            act(1791968400000L, "RUN", 51.28, 7.54, 153.0, 64.0, "hw1791968400000", "Lari 32", 165.0, 0.0),
            act(1792144800000L, "WALK", 45.52, 2.15, 173.0, 58.0, "hw1792144800000", "Lari 30", 185.0, 0.0),
            act(1792310400000L, "RUN", 97.66, 13.24, 162.0, 30.0, "hw1792310400000", "Lari 28", 174.0, 0.0),
            act(1792400400000L, "OTHER", 54.76, 0.0, 161.0, 24.0, "hw1792400400000", "Lari 27", 173.0, 0.0),
            act(1792486800000L, "RUN", 58.0, 7.37, 162.0, 45.0, "hw1792486800000", "Lari 26", 174.0, 0.0),
            act(1792656000000L, "RUN", 24.36, 3.77, 164.0, 64.0, "hw1792656000000", "Lari 24", 176.0, 0.0),
            act(1792746000000L, "RUN", 49.63, 6.8, 155.0, 52.0, "hw1792746000000", "Lari 23", 167.0, 0.0),
            act(1792915200000L, "OTHER", 24.29, 0.0, 148.0, 38.0, "hw1792915200000", "Lari 21", 160.0, 0.0),
            act(1793088000000L, "RUN", 52.54, 6.85, 161.0, 17.0, "hw1793088000000", "Lari 19", 173.0, 0.0),
            act(1793268000000L, "RUN", 16.75, 2.48, 149.0, 55.0, "hw1793268000000", "Lari 17", 161.0, 0.0),
            act(1793433600000L, "RUN", 57.74, 7.61, 151.0, 59.0, "hw1793433600000", "Lari 15", 163.0, 0.0),
            act(1793520000000L, "WALK", 46.52, 2.17, 175.0, 10.0, "hw1793520000000", "Lari 14", 187.0, 0.0),
            act(1793700000000L, "RUN", 45.9, 6.07, 170.0, 59.0, "hw1793700000000", "Lari 12", 182.0, 0.0),
            act(1793786400000L, "RUN", 23.17, 3.45, 143.0, 4.0, "hw1793786400000", "Lari 11", 155.0, 0.0),
            act(1793865600000L, "RUN", 48.79, 6.17, 160.0, 20.0, "hw1793865600000", "Lari 10", 172.0, 0.0),
            act(1793952000000L, "WALK", 35.18, 2.21, 158.0, 6.0, "hw1793952000000", "Lari 9", 170.0, 0.0),
            act(1794128400000L, "RUN", 29.12, 3.86, 159.0, 12.0, "hw1794128400000", "Lari 7", 171.0, 0.0),
            act(1794218400000L, "RUN", 52.37, 6.76, 171.0, 30.0, "hw1794218400000", "Lari 6", 183.0, 0.0),
            act(1794391200000L, "OTHER", 41.07, 0.0, 155.0, 20.0, "hw1794391200000", "Lari 4", 167.0, 0.0),
            act(1794470400000L, "WALK", 43.93, 1.54, 156.0, 8.0, "hw1794470400000", "Lari 3", 168.0, 0.0),
            act(1794650400000L, "RUN", 31.4, 4.57, 145.0, 49.0, "hw1794650400000", "Lari 1", 157.0, 0.0),
            act(1794729600000L, "RUN", 32.17, 4.34, 149.0, 74.0, "hw1794729600000", "Lari 0", 161.0, 0.0),
        )
        val wl = listOf<Wellness>(
            Wellness("2026-10-16", restingHR = 54.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 23504.0),
            Wellness("2026-10-17", restingHR = 54.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 27757.0),
            Wellness("2026-10-18", restingHR = 53.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 25688.0, stress = 31.0),
            Wellness("2026-10-19", restingHR = 55.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 27486.0, stress = 39.0),
            Wellness("2026-10-20", restingHR = 55.0, rhrSrc = "jam", sleepSecs = 26754.0, stress = 31.0),
            Wellness("2026-10-21", restingHR = 56.0, rhrSrc = "jam", hrv = 66.0, hrvSrc = "jam", sleepSecs = 25104.0, stress = 39.0),
            Wellness("2026-10-22", restingHR = 54.0, rhrSrc = "jam", sleepSecs = 23881.0, stress = 34.0),
            Wellness("2026-10-23", hrv = 63.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 23862.0, stress = 32.0),
            Wellness("2026-10-24", restingHR = 53.0, rhrSrc = "jam", hrv = 61.0, hrvSrc = "jam", sleepSecs = 25071.0, stress = 31.0),
            Wellness("2026-10-25", restingHR = 53.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 25904.0, stress = 40.0),
            Wellness("2026-10-26", restingHR = 54.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 25580.0, stress = 35.0),
            Wellness("2026-10-27", restingHR = 55.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", sleepSecs = 25894.0, stress = 33.0),
            Wellness("2026-10-28", restingHR = 52.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 22591.0, stress = 36.0),
            Wellness("2026-10-29", restingHR = 55.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 26906.0, stress = 40.0),
            Wellness("2026-10-30", restingHR = 53.0, rhrSrc = "jam", hrv = 67.0, hrvSrc = "jam", stress = 34.0),
            Wellness("2026-10-31", restingHR = 54.0, rhrSrc = "jam", sleepSecs = 27073.0, stress = 30.0),
            Wellness("2026-11-01", restingHR = 53.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 25691.0, stress = 31.0),
            Wellness("2026-11-02", restingHR = 53.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 24381.0, stress = 34.0),
            Wellness("2026-11-03", restingHR = 56.0, rhrSrc = "jam", hrv = 70.0, hrvSrc = "jam", sleepSecs = 25172.0, stress = 40.0),
            Wellness("2026-11-04", restingHR = 54.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 25571.0),
            Wellness("2026-11-05", restingHR = 56.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 25217.0, stress = 38.0),
            Wellness("2026-11-06", restingHR = 54.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", sleepSecs = 27324.0),
            Wellness("2026-11-07", restingHR = 53.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 27296.0, stress = 31.0),
            Wellness("2026-11-08", restingHR = 52.0, rhrSrc = "jam", hrv = 60.0, hrvSrc = "jam", stress = 31.0),
            Wellness("2026-11-09", restingHR = 52.0, rhrSrc = "jam", sleepSecs = 25343.0, stress = 33.0),
            Wellness("2026-11-10", restingHR = 55.0, rhrSrc = "jam", hrv = 69.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 27857.0, stress = 30.0),
            Wellness("2026-11-11", restingHR = 54.0, rhrSrc = "jam", hrv = 63.0, hrvSrc = "jam", hrvSpot = true, sleepSecs = 24445.0, stress = 32.0),
            Wellness("2026-11-12", restingHR = 53.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", stress = 31.0),
            Wellness("2026-11-13", restingHR = 54.0, rhrSrc = "jam", hrv = 68.0, hrvSrc = "jam", sleepSecs = 22652.0),
            Wellness("2026-11-14", restingHR = 53.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", stress = 40.0),
            Wellness("2026-11-15", restingHR = 53.0, rhrSrc = "jam", hrv = 65.0, hrvSrc = "jam", stress = 34.0),
        )
        val vx = mapOf<Long, String>(1794218400000L to "long")
        return snap(60.0, 185.0, 40, 4, acts, wl, vx)
    }

    @Test
    fun scenario_G_menengah_tahap3() {
        val sn = scenario_G_menengah_tahap3_snap()
        // estMax
        val e = sn.estMax()
        near(11.772960725075531, e.m, "estMax.m")
        near(13.081067472306145, e.perf, "estMax.perf")
        near(10.053398058252426, e.hrr, "estMax.hrr")
        assertEquals("HR+pace dari 26 lari (56 hari)", e.src)
        assertEquals("baik", e.conf)
        assertEquals(26, e.n)
        // stage
        val g = sn.stage()
        assertEquals(3, g.s)
        near(11.772960725075531, g.m, "stage.m")
        assertEquals(3, g.raw)
        assertEquals(false, g.unknown)
        assertEquals(3, g.afS)
        // load
        val expLoads = listOf<Int>(53, 73, 23, 43, 39, 45, 59, 36, 14, 30, 49, 46, 16, 16, 90, 15, 26, 61, 16, 140, 44, 83, 36, 62, 19, 74, 18, 66, 16, 77, 22, 67, 12, 39, 89, 33, 15, 31, 35)
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(242, sn.loadDays(7))
        assertEquals(978, sn.loadDays(28))
        // rhr28 / maxHr
        assertEquals(Rhr28(54, 28), sn.rhr28())
        near(185.0, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(false, au.lock, "lock")
        assertEquals(false, au.severe, "severe")
        assertEquals(false, au.warn, "warn")
        assertEquals("", au.why)
        assertEquals(1, au.hrvPct)
        assertEquals(-1, au.rhrD)
        assertEquals(65, au.hrv)
        assertEquals(53, au.rhr)
        near(6.292222222222223, au.sleepH, "sleepH")
        near(34.0, au.stress, "stress")
        assertEquals(1, au.stressD)
        // readiness
        assertEquals(73, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf<List<String>>(
            listOf(1793865600000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1793952000000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794128400000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794218400000L.toString(), "long", "tinggi", "dikonfirmasi manual", "true"),
            listOf(1794391200000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794470400000L.toString(), "easy", "rendah", "bukan lari", "false"),
            listOf(1794650400000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
            listOf(1794729600000L.toString(), "easy", "sedang", "pace relatif merata / tidak ada pola keras", "false"),
        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }

    private fun scenario_H_kosong_snap(): Snapshot {
        val acts = listOf<Act>(

        )
        val wl = listOf<Wellness>(
            Wellness("2026-11-15", restingHR = 53.0, rhrSrc = "jam", hrv = 64.0, hrvSrc = "jam", sleepSecs = 22980.0),
        )
        val vx = mapOf<Long, String>()
        return snap(0.0, 0.0, 0, 0, acts, wl, vx)
    }

    @Test
    fun scenario_H_kosong() {
        val sn = scenario_H_kosong_snap()
        // estMax
        val e = sn.estMax()
        near(null, e.m, "estMax.m")
        near(null, e.perf, "estMax.perf")
        near(null, e.hrr, "estMax.hrr")
        assertEquals("", e.src)
        assertEquals("", e.conf)
        assertEquals(0, e.n)
        // stage
        val g = sn.stage()
        assertEquals(2, g.s)
        near(null, g.m, "stage.m")
        assertEquals(null, g.raw)
        assertEquals(true, g.unknown)
        assertEquals(0, g.afS)
        // load
        val expLoads = listOf<Int>()
        assertEquals(expLoads, sn.acts.map { sn.loadOf(it) })
        assertEquals(0, sn.loadDays(7))
        assertEquals(0, sn.loadDays(28))
        // rhr28 / maxHr
        assertNull(sn.rhr28())
        near(0.0, sn.maxHr(), "maxHr")
        // autonomic
        val au = sn.autonomic()
        assertEquals(false, au.lock, "lock")
        assertEquals(false, au.severe, "severe")
        assertEquals(false, au.warn, "warn")
        assertEquals("", au.why)
        assertEquals(null, au.hrvPct)
        assertEquals(null, au.rhrD)
        assertEquals(64, au.hrv)
        assertEquals(53, au.rhr)
        near(6.383333333333334, au.sleepH, "sleepH")
        near(null, au.stress, "stress")
        assertEquals(null, au.stressD)
        // readiness
        assertEquals(67, sn.readiness())
        // detectType (8 aktivitas terakhir)
        val expDetect = listOf<List<String>>(

        )
        val gotDetect = sn.acts.takeLast(8).map { a -> val t = sn.detectType(a, g); listOf(a.start.toString(), t.type, t.conf, t.why, t.confirmed.toString()) }
        assertEquals(expDetect, gotDetect)
    }

    @Test
    fun helpers() {
        near(14.5, metsAt(3.5), "metsAt(3.5)")
        near(14.5, metsAt(4.0), "metsAt(4)")
        near(13.899999999999999, metsAt(4.2), "metsAt(4.2)")
        near(12.0, metsAt(5.0), "metsAt(5)")
        near(11.5, metsAt(5.25), "metsAt(5.25)")
        near(9.64, metsAt(6.1), "metsAt(6.1)")
        near(7.5, metsAt(7.7), "metsAt(7.7)")
        near(5.508, metsAt(9.99), "metsAt(9.99)")
        near(4.1, metsAt(12.5), "metsAt(12.5)")
        near(3.6, metsAt(14.0), "metsAt(14)")
        near(3.6, metsAt(16.0), "metsAt(16)")
        near(12.749999999974534, paceOf(4.0), "paceOf(4)")
        near(9.999999999959982, paceOf(5.5), "paceOf(5.5)")
        near(7.9999999999163265, paceOf(7.2), "paceOf(7.2)")
        near(6.499999999974534, paceOf(9.0), "paceOf(9)")
        near(5.000000000032742, paceOf(12.0), "paceOf(12)")
        near(3.5000000000909495, paceOf(14.5), "paceOf(14.5)")
        near(1.0, pctAt(3.0), "pctAt(3)")
        near(1.0, pctAt(4.0), "pctAt(4)")
        near(0.9875, pctAt(5.0), "pctAt(5)")
        near(0.9434210526315789, pctAt(10.0), "pctAt(10)")
        near(0.825, pctAt(25.0), "pctAt(25)")
        near(0.7535714285714286, pctAt(40.0), "pctAt(40)")
        near(0.625, pctAt(67.0), "pctAt(67)")
        near(0.6108695652173913, pctAt(80.0), "pctAt(80)")
        near(0.6, pctAt(90.0), "pctAt(90)")
        near(0.6, pctAt(120.0), "pctAt(120)")
        assertEquals("4–8 mnt", durText(0.99))
        assertEquals("8–20 mnt", durText(0.9))
        assertEquals("20–30 mnt", durText(0.82))
        assertEquals("30–45 mnt", durText(0.7))
        assertEquals("45–90+ mnt", durText(0.61))
        assertEquals(">90 mnt", durText(0.5))
        assertEquals(null, band(null))
        assertEquals("Rendah", band(5.0))
        assertEquals("Menengah", band(7.9))
        assertEquals("Menengah", band(10.0))
        assertEquals("Tinggi", band(10.9))
        assertEquals("Tinggi", band(14.0))
        assertEquals("—", f1(null))
        assertEquals("0", f1(0.0))
        assertEquals("5", f1(4.95))
        assertEquals("12.3", f1(12.25))
        assertEquals("3", f1(3.04))
        assertEquals("7", f1(7.0))
        assertEquals("0", f1(-0.05))
        assertEquals("—", mp(0.0))
        assertEquals("4:59", mp(4.9833))
        assertEquals("5:30", mp(5.5))
        assertEquals("7:00", mp(6.999))
        assertEquals("12:01", mp(12.01))
        assertEquals("0:00", formatPace(0.0))
        assertEquals("00:00", formatTime(0.0))
        assertEquals("5:00", formatPace(299.5))
        assertEquals("05:00", formatTime(299.5))
        assertEquals("6:00", formatPace(360.0))
        assertEquals("06:00", formatTime(360.0))
        assertEquals("60:00", formatPace(3599.5))
        assertEquals("60:00", formatTime(3599.5))
        assertEquals("—", fmtM(0.0))
        assertEquals("12 mnt", fmtM(12.4))
        assertEquals("60 mnt", fmtM(59.5))
        assertEquals("1 j", fmtM(60.0))
        assertEquals("1 j 2 m", fmtM(61.5))
        assertEquals("2 j 5 m", fmtM(125.2))
        assertEquals("5j30", fmtHm(5.5))
        assertEquals("6j15", fmtHm(6.25))
        assertEquals("8j00", fmtHm(7.999))
    }

    @Test
    fun walkRun() {
        assertEquals(WalkRunWorkout("A", 30, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "10× (1 mnt lari + 1 mnt jalan)", "Pendinginan" to "5 mnt jalan")), walkRunWorkout(0, 1.0))
        assertEquals(WalkRunWorkout("A", 26, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "8× (1 mnt lari + 1 mnt jalan)", "Pendinginan" to "5 mnt jalan")), walkRunWorkout(0, 0.8))
        assertEquals(WalkRunWorkout("A", 20, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "5× (1 mnt lari + 1 mnt jalan)", "Pendinginan" to "5 mnt jalan")), walkRunWorkout(0, 0.5))
        assertEquals(WalkRunWorkout("A", 20, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "5× (1 mnt lari + 1 mnt jalan)", "Pendinginan" to "5 mnt jalan")), walkRunWorkout(0, 0.2))
        assertEquals(WalkRunWorkout("A", 30, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "10× (1 mnt lari + 1 mnt jalan)", "Pendinginan" to "5 mnt jalan")), walkRunWorkout(0, 0.0))
        assertEquals(WalkRunWorkout("B", 30, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "7× (2 mnt lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(1, 1.0))
        assertEquals(WalkRunWorkout("B", 27, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "6× (2 mnt lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(1, 0.8))
        assertEquals(WalkRunWorkout("B", 21, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "4× (2 mnt lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(1, 0.5))
        assertEquals(WalkRunWorkout("B", 18, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "3× (2 mnt lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(1, 0.2))
        assertEquals(WalkRunWorkout("B", 30, listOf("Pemanasan" to "5 mnt jalan", "Inti" to "7× (2 mnt lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(1, 0.0))
        assertEquals(WalkRunWorkout("C", 30, listOf("Pemanasan" to "5 mnt jalan", "Inti 1" to "6× (1 mnt lari + 30 dtk jalan)", "Inti 2" to "8× (30 dtk lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(2, 1.0))
        assertEquals(WalkRunWorkout("C", 26, listOf("Pemanasan" to "5 mnt jalan", "Inti 1" to "5× (1 mnt lari + 30 dtk jalan)", "Inti 2" to "6× (30 dtk lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(2, 0.8))
        assertEquals(WalkRunWorkout("C", 20, listOf("Pemanasan" to "5 mnt jalan", "Inti 1" to "3× (1 mnt lari + 30 dtk jalan)", "Inti 2" to "4× (30 dtk lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(2, 0.5))
        assertEquals(WalkRunWorkout("C", 20, listOf("Pemanasan" to "5 mnt jalan", "Inti 1" to "3× (1 mnt lari + 30 dtk jalan)", "Inti 2" to "4× (30 dtk lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(2, 0.2))
        assertEquals(WalkRunWorkout("C", 30, listOf("Pemanasan" to "5 mnt jalan", "Inti 1" to "6× (1 mnt lari + 30 dtk jalan)", "Inti 2" to "8× (30 dtk lari + 1 mnt jalan)", "Pendinginan" to "4 mnt jalan")), walkRunWorkout(2, 0.0))
    }
}
