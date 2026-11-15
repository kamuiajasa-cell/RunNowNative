package com.runnow.domain

import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.fail

/**
 * Membandingkan reviewRun(), reviewHeadline(), dan runBacktest() Kotlin dengan kode asli runsense.html.
 * Data: src/test/resources/golden_review.txt (dibuat otomatis oleh gen/gen_review.js).
 */
class ReviewGoldenTest {
    private class Ds(val id: String, val now: Long, val profile: Profile) {
        val acts = ArrayList<Act>()
        val wl = ArrayList<Wellness>()
        val vx = HashMap<Long, String>()
        val sx = HashMap<String, StreamSummary>()
    }

    private fun dash(s: String) = if (s == "-") "" else s
    private fun num(s: String) = if (s == "-") 0.0 else s.toDouble()
    private fun numOrNull(s: String): Double? = if (s == "-") null else s.toDouble()
    private fun orDash(s: String?) = if (s.isNullOrEmpty()) "-" else s

    private fun kindOf(s: String) = when (s) {
        "run" -> ActKind.RUN
        "walk" -> ActKind.WALK
        else -> ActKind.OTHER
    }

    private fun parseSx(spec: String): StreamSummary {
        if (spec == "none") return StreamSummary.NONE
        var fast: StreamFast? = null
        var fade: StreamFade? = null
        var dec: StreamDec? = null
        var tiz: List<Int>? = null
        for (part in spec.split(";")) {
            val v = part.drop(2).split(",")
            when (part[0]) {
                'f' -> fast = StreamFast(v[0].toDouble(), v[1].toDouble(), v[2].toInt())
                'a' -> fade = StreamFade(v[0].toInt())
                'd' -> dec = StreamDec(v[0].toInt())
                'z' -> tiz = v.map { it.toInt() }
            }
        }
        return StreamSummary(fast = fast, fade = fade, dec = dec, tiz = tiz)
    }

    private fun sections(r: RunReview): String =
        if (r.sections.isEmpty()) "-"
        else r.sections.joinToString("##") { s -> s.title + "^^" + s.notes.joinToString("§§") { it.k + "¦" + it.text } }

    @Test
    fun reviewsMatchHtml() {
        val text = javaClass.getResource("/golden_review.txt")!!.readText()
        val datasets = LinkedHashMap<String, Ds>()
        var cur: Ds? = null
        var sn: Snapshot? = null
        var desc = ""
        var nReview = 0
        var nBacktest = 0
        val problems = ArrayList<String>()

        fun cmp(what: String, exp: String, got: String) {
            if (exp != got) problems += "$desc\n    $what:\n      diharapkan: $exp\n      didapat:    $got"
        }

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
                    val t = line.split(" ", limit = 10)
                    val start = t[1].toLong()
                    cur!!.acts += Act(
                        start = start, kind = kindOf(t[2]), min = t[3].toDouble(), km = t[4].toDouble(),
                        avgHr = t[5].toDouble(), elev = t[6].toDouble(), lid = "hw$start", name = dash(t[9]),
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
                'X' -> line.split(" ").let { cur!!.vx[it[1].toLong()] = it[2] }
                'S' -> line.split(" ").let { cur!!.sx["hw" + it[1]] = parseSx(it[2]) }
                'E' -> Unit
                'C' -> {
                    val t = line.split(" ")
                    val ds = datasets.getValue(t[1])
                    val profile = ds.profile.copy(restManual = t[4] == "1", lthrC = t[2].toInt(), ltpC = t[3].toInt())
                    val settings = Settings(
                        style = "moderate", plan = emptyMap(), race = "", raceDist = 0.0, raceGoal = "",
                        rw = RunWalkSettings(false, 225, 30), heat = "", flag = null, goal = "5K",
                    )
                    val base = Snapshot(profile, ds.acts, ds.wl, ds.vx, ds.now, ZoneOffset.UTC, settings, ds.sx)
                    sn = base.withProcessed(base.processData())
                    desc = "[${ds.id}] lthrC=${t[2]} ltpC=${t[3]} restManual=${t[4]}"
                }
                'V' -> {
                    nReview++
                    val e = line.drop(2).split("@@")
                    val s = sn!!
                    val start = e[0].toLong()
                    val a = s.acts.first { it.start == start }
                    val rev = s.reviewRun(a)
                    val hl = reviewHeadline(rev)
                    val legacy = reviewHeadline(rev.flat)
                    val got = listOf(
                        a.start.toString(), rev.tone, orDash(rev.headline), orDash(rev.summary), sections(rev),
                        orDash(hl?.k), orDash(hl?.text), legacy.k, legacy.text,
                    )
                    val names = listOf("start", "tone", "headline", "summary", "sections", "reviewHeadline.k", "reviewHeadline.text", "legacy.k", "legacy.text")
                    for (k in names.indices) cmp("start=$start ${names[k]}", e[k], got[k])
                }
                'K' -> {
                    nBacktest++
                    val e = line.drop(2).split("@@")
                    val bt = sn!!.runBacktest()
                    cmp("backtest subtitle", e[0], bt.subtitle)
                    val st = bt.stats
                    if (st != null) {
                        cmp("backtest baris", e[1], (st + bt.recent).joinToString("##") { it.first + "^^" + it.second })
                        cmp("backtest butuh-lebih", e[2], "-")
                    } else {
                        cmp("backtest baris", e[1], "-")
                        cmp("backtest butuh-lebih", e[2], bt.tested.toString())
                    }
                }
            }
        }
        if (problems.isNotEmpty()) fail("${problems.size} selisih ($nReview review, $nBacktest backtest). Contoh pertama:\n" + problems.take(12).joinToString("\n"))
        check(nReview > 300 && nBacktest > 50) { "Data uji terlalu sedikit: $nReview review, $nBacktest backtest" }
    }
}
