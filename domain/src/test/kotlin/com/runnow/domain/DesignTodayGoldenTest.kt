package com.runnow.domain

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.fail

/**
 * Membandingkan designToday() Kotlin dengan kode asli runsense.html pada ratusan kombinasi data dan pengaturan.
 * Data ada di src/test/resources/golden_design.txt (dibuat otomatis oleh gen/gen_design.js).
 */
class DesignTodayGoldenTest {
    private class Ds(val id: String, val now: Long, val profile: Profile) {
        val acts = ArrayList<Act>()
        val wl = ArrayList<Wellness>()
        val vx = HashMap<Long, String>()
    }

    private fun dash(s: String) = if (s == "-") "" else s
    private fun num(s: String) = if (s == "-") 0.0 else s.toDouble()
    private fun numOrNull(s: String): Double? = if (s == "-") null else s.toDouble()

    private fun kindOf(s: String) = when (s) {
        "run" -> ActKind.RUN
        "walk" -> ActKind.WALK
        else -> ActKind.OTHER
    }

    private fun parsePlan(s: String): Map<Int, PlanDay> {
        if (s == "-") return emptyMap()
        val m = LinkedHashMap<Int, PlanDay>()
        for (e in s.split(",")) {
            val p = e.split(":")
            m[p[0].toInt()] = PlanDay(p[1], p[2].toDouble())
        }
        return m
    }

    @Test
    fun designTodayMatchesHtml() {
        val text = javaClass.getResource("/golden_design.txt")!!.readText()
        val datasets = LinkedHashMap<String, Ds>()
        var cur: Ds? = null
        var pending: Pair<String, Snapshot>? = null // (deskripsi kasus, snapshot)
        var total = 0
        val problems = ArrayList<String>()

        for (line in text.lines()) {
            if (line.isEmpty() || line.startsWith("#")) continue
            when (line[0]) {
                'D' -> {
                    val t = line.split(" ")
                    val ds = Ds(t[1], t[2].toLong(), Profile(num(t[3]), num(t[4]), t[5].toInt(), t[6].toInt()))
                    datasets[ds.id] = ds
                    cur = ds
                }
                'A' -> {
                    val t = line.split(" ")
                    val start = t[1].toLong()
                    cur!!.acts += Act(
                        start = start, kind = kindOf(t[2]), min = t[3].toDouble(), km = t[4].toDouble(),
                        avgHr = t[5].toDouble(), elev = t[6].toDouble(), lid = "hw$start", name = "Lari",
                        maxHr = t[7].toDouble(), icuId = "hw$start", load = t[8].toDouble(),
                    )
                }
                'W' -> {
                    val t = line.split(" ")
                    cur!!.wl += Wellness(
                        id = t[1],
                        restingHR = num(t[2]), rhrSrc = if (t[2] == "-") "" else "jam",
                        hrv = num(t[3]), hrvSrc = if (t[3] == "-") "" else "jam", hrvSpot = t[4] == "1",
                        sleepSecs = num(t[5]), stress = num(t[6]),
                        ctl = numOrNull(t[7]), atl = numOrNull(t[8]), tsb = numOrNull(t[9]),
                    )
                }
                'X' -> {
                    val t = line.split(" ")
                    cur!!.vx[t[1].toLong()] = t[2]
                }
                'E' -> Unit
                'C' -> {
                    val t = line.split(" ")
                    val ds = datasets.getValue(t[1])
                    val flag = if (t[4] == "-") null else t[4].split(":").let { LegFlag(it[0].toInt(), it[1].toLong()) }
                    val rw = t[8].split(":").let { RunWalkSettings(it[0] == "1", it[1].toInt(), it[2].toInt()) }
                    val settings = Settings(
                        style = dash(t[2]), plan = parsePlan(t[11]), race = dash(t[5]), raceDist = t[6].toDouble(),
                        raceGoal = dash(t[7]), rw = rw, heat = dash(t[3]), flag = flag,
                        athlete = AthleteSettings(t[9].toInt(), dash(t[10])),
                    )
                    val sn = Snapshot(ds.profile, ds.acts, ds.wl, ds.vx, ds.now, ZoneOffset.UTC, settings)
                    pending = "#${total + 1} [${ds.id}] ${line.drop(2)}" to sn
                }
                'R' -> {
                    val (desc, sn) = pending!!
                    total++
                    val exp = line.drop(2).split("@@")
                    val names = listOf(
                        "k", "status", "title", "duration", "hr", "pace", "tag", "score", "stage", "reason", "steps", "wl",
                    )
                    try {
                        val g = sn.designToday()
                        val got = listOf(
                            g.k, g.status, g.title, g.duration, g.hr, g.pace, g.tag, g.score.toString(),
                            g.stage?.toString() ?: "-", g.reason,
                            if (g.steps.isEmpty()) "-" else g.steps.joinToString("##") { it.first + "^^" + it.second },
                            g.wl?.joinToString("##") ?: "-",
                        )
                        for (i in names.indices) {
                            if (exp[i] != got[i]) {
                                problems += "$desc\n    ${names[i]}:\n      diharapkan: ${exp[i]}\n      didapat:    ${got[i]}"
                            }
                        }
                    } catch (e: Throwable) {
                        problems += "$desc\n    EXCEPTION: $e"
                    }
                }
            }
        }
        if (problems.isNotEmpty()) {
            fail("${problems.size} selisih dari $total kasus. Contoh pertama:\n" + problems.take(12).joinToString("\n"))
        }
        check(total > 100) { "Data uji terlalu sedikit: $total" }
    }
}
