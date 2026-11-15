package com.runnow.domain

import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.fail

/**
 * Membandingkan processData(), coachLine(), intensityLine(), dan syncLine() Kotlin dengan kode asli runsense.html.
 * Data ada di src/test/resources/golden_process.txt (dibuat otomatis oleh gen/gen_process.js).
 */
class ProcessGoldenTest {
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

    private fun d(x: Double?): String = if (x == null) "-" else x.toString()
    private fun i(x: Int?): String = if (x == null) "-" else x.toString()
    private fun s(x: String?): String = if (x.isNullOrEmpty()) "-" else x

    /** Indeks kolom R yang berupa bilangan desimal: dibandingkan dengan toleransi, bukan teks. */
    private val numericCols = setOf(1, 2, 3, 11, 14, 18, 19, 23)

    private val names = listOf(
        "name", "ctl", "atl", "tsb", "lthr", "ltPace", "lthrAuto", "ltPaceAuto", "formLabel", "formClass", "score", "mets",
        "stage", "stageName", "est.m", "est.src", "est.n", "est.conf", "est.perf", "est.hrr",
        "goalEst.current", "goalEst.good", "goalEst.projection", "rest", "afS",
        "rec.k", "rec.status", "rec.title", "rec.duration", "rec.hr", "rec.pace", "rec.tag", "rec.score", "rec.stage", "rec.reason",
        "coach", "coach(rested)", "coach(walk)", "intensity", "intensity(rested)", "syncLine", "activities",
    )

    private fun same(col: Int, a: String, b: String): Boolean {
        if (a == b) return true
        if (col in numericCols && a != "-" && b != "-") {
            val x = a.toDoubleOrNull() ?: return false
            val y = b.toDoubleOrNull() ?: return false
            return abs(x - y) <= 1e-9 * max(1.0, max(abs(x), abs(y)))
        }
        return false
    }

    @Test
    fun processDataMatchesHtml() {
        val text = javaClass.getResource("/golden_process.txt")!!.readText()
        val datasets = LinkedHashMap<String, Ds>()
        var cur: Ds? = null
        var pending: Triple<String, Snapshot, SyncInfo>? = null
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
                    val profile = ds.profile.copy(restManual = t[5] == "1", lthrC = t[3].toInt(), ltpC = t[4].toInt())
                    val flag = if (t[11] == "-") null else t[11].split(":").let { LegFlag(it[0].toInt(), it[1].toLong()) }
                    val rw = t[15].split(":").let { RunWalkSettings(it[0] == "1", it[1].toInt(), it[2].toInt()) }
                    val settings = Settings(
                        style = t[9], plan = parsePlan(t[16]), race = dash(t[12]), raceDist = t[13].toDouble(),
                        raceGoal = dash(t[14]), rw = rw, heat = dash(t[10]), flag = flag, goal = t[2],
                    )
                    val sn = Snapshot(profile, ds.acts, ds.wl, ds.vx, ds.now, ZoneOffset.UTC, settings)
                    val sync = SyncInfo(icuName = dash(t[6]), icuLast = t[7].toLong(), hwLast = t[8].toLong())
                    pending = Triple("#${total + 1} [${ds.id}] ${line.drop(2)}", sn, sync)
                }
                'R' -> {
                    val (desc, sn, sync) = pending!!
                    total++
                    val exp = line.drop(2).split("@@")
                    try {
                        val r = sn.processData(sync)
                        val a = r.athlete
                        val rec = r.recommendation
                        val got = listOf(
                            s(a.name), d(a.ctl), d(a.atl), d(a.tsb), i(a.lthr), s(a.ltPace), i(a.lthrAuto), s(a.ltPaceAuto),
                            a.formLabel, a.formClass, i(a.score), d(a.mets), a.stage.toString(), s(a.stageName),
                            d(a.est.m), s(a.est.src), a.est.n.toString(), s(a.est.conf), d(a.est.perf), d(a.est.hrr),
                            s(r.goalEst.current), s(r.goalEst.good), s(r.goalEst.projection),
                            d(r.profile.rest), r.profile.afS.toString(),
                            rec.k, rec.status, s(rec.title), s(rec.duration), s(rec.hr), s(rec.pace), s(rec.tag),
                            rec.score.toString(), i(rec.stage), s(rec.reason),
                            s(coachLine(rec, a.formLabel)),
                            s(coachLine(rec, a.formLabel, rested = true)),
                            s(coachLine(rec, a.formLabel, walk = true)),
                            s(intensityLine(rec)),
                            s(intensityLine(rec, rested = true)),
                            s(r.syncLine),
                            if (r.activities.isEmpty()) "-" else r.activities.joinToString("##") { x ->
                                listOf(
                                    x.start.toString(), s(x.date), s(x.title), s(x.meta), x.load.toString(),
                                    s(x.type), s(x.conf), s(x.why), if (x.confirmed) "1" else "0",
                                ).joinToString("^^")
                            },
                        )
                        if (got.size != names.size || exp.size != names.size) {
                            problems += "$desc\n    jumlah kolom: harapan ${exp.size}, hasil ${got.size}, nama ${names.size}"
                        } else {
                            for (k in names.indices) {
                                if (!same(k, exp[k], got[k])) {
                                    problems += "$desc\n    ${names[k]}:\n      diharapkan: ${exp[k]}\n      didapat:    ${got[k]}"
                                }
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
