package com.runnow.data

import com.runnow.domain.LegFlag
import com.runnow.domain.PlanDay
import com.runnow.domain.RunWalkSettings
import com.runnow.domain.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class SettingsMemStore : KeyValueStore {
    val m = HashMap<String, String>()
    override fun read(key: String) = m[key]
    override fun write(key: String, value: String) { m[key] = value }
    override fun remove(key: String) { m.remove(key) }
}

class SettingsStoreTest {
    @Test
    fun kosongMemberiBawaan() {
        val s = SettingsStore(SettingsMemStore()).load()
        assertEquals("5K", s.goal)
        assertEquals("moderate", s.style)
        assertTrue(s.plan.isEmpty())
        assertEquals(RunWalkSettings(), s.rw)
    }

    @Test
    fun simpanLaluMuatSama() {
        val st = SettingsStore(SettingsMemStore())
        val a = Settings(
            style = "aggressive", goal = "10K", race = "2026-12-20", raceDist = 10.0, raceGoal = "55:00",
            plan = mapOf(1 to PlanDay("easy", 6.0), 6 to PlanDay("long", 14.0)),
            rw = RunWalkSettings(true, 240, 45), heat = "2026-11-17", flag = LegFlag(2, 1_700_000_000_000L),
        )
        st.save(a)
        val b = st.load()
        assertEquals(a.style, b.style); assertEquals(a.goal, b.goal); assertEquals(a.race, b.race)
        assertEquals(a.raceDist, b.raceDist); assertEquals(a.raceGoal, b.raceGoal)
        assertEquals(a.plan, b.plan); assertEquals(a.rw, b.rw); assertEquals(a.heat, b.heat); assertEquals(a.flag, b.flag)
    }

    @Test
    fun formatSamaDenganHtml() {
        val m = SettingsMemStore()
        m.write("runsense_plan", """{"1":{"t":"easy","km":6},"4":{"t":"rest","km":0},"9":{"t":"easy","km":3}}""")
        m.write("runsense_racex", """{"d":21.1,"g":"1:50:00"}""")
        m.write("runsense_rw", """{"on":true,"run":225,"walk":30}""")
        m.write("runsense_flag", """{"lv":1,"since":1700000000000}""")
        val s = SettingsStore(m).load()
        assertEquals(setOf(1, 4), s.plan.keys) // hari 9 dibuang
        assertEquals(21.1, s.raceDist); assertEquals("1:50:00", s.raceGoal)
        assertTrue(s.rw.on)
        assertNotNull(s.flag)
    }

    @Test
    fun demoMemuatData() {
        val d = DemoData.create(1_763_380_000_000L, java.time.ZoneId.of("Asia/Jakarta"))
        assertEquals(7, d.data.acts.size)
        assertEquals(13, d.data.wellness.size)
        assertEquals(7, d.settings.plan.size)
    }
}
