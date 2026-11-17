package com.runnow.domain

import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.fail

/**
 * Membandingkan clAvg, clPers, clTh, clCls, clStat, clProfile, dan clSlots Kotlin dengan kode asli runsense.html.
 * Data: src/test/resources/golden_climate.txt (dibuat otomatis oleh gen/gen_climate.js).
 */
class ClimateGoldenTest {
    private val zone = ZoneOffset.UTC

    private fun num(s: String) = if (s == "-") 0.0 else s.toDouble()
    private fun numOrNull(s: String): Double? = if (s == "-") null else s.toDouble()
    private fun kindOf(s: String) = when (s) {
        "run" -> ActKind.RUN
        "walk" -> ActKind.WALK
        else -> ActKind.OTHER
    }

    private fun fmt(x: Double?): String = if (x == null) "-" else x.toString()

    /** Sama persis, atau sama kecuali bilangan desimal yang dibandingkan dengan toleransi. */
    private fun eq(a: String, b: String): Boolean {
        if (a == b) return true
        val x = a.split(",", ";", "=", "|", "#", "@")
        val y = b.split(",", ";", "=", "|", "#", "@")
        if (x.size != y.size) return false
        for (i in x.indices) {
            if (x[i] == y[i]) continue
            val p = x[i].toDoubleOrNull() ?: return false
            val q = y[i].toDoubleOrNull() ?: return false
            if (abs(p - q) > 1e-9 * max(1.0, max(abs(p), abs(q)))) return false
        }
        return true
    }

    private fun persLine(P: ClimPers): String {
        val bins = if (P.bins.isEmpty()) "-" else P.bins.joinToString(";") { listOf(it.lo, it.hi, it.n.toDouble(), it.med, it.c).joinToString(",") { v -> fmtInt(v) } }
        return if (P.ok) {
            listOf("1", P.n.toString(), "-", "-", fmt(P.opt), fmt(P.optLo), P.cool?.toString() ?: "-", P.hot?.toString() ?: "-", P.conf, bins)
        } else {
            listOf("0", P.n.toString(), P.cand.toString(), P.reason, "-", "-", "-", "-", "-", "-")
        }.joinToString("@@")
    }

    // Bilangan bulat dicetak tanpa ".0" agar cocok dengan JS; eq() tetap toleran.
    private fun fmtInt(v: Double): String = if (v == Math.floor(v) && abs(v) < 1e15) v.toLong().toString() else v.toString()

    private fun climLine(c: ClimAvg?): String =
        if (c == null) "null" else listOf(c.t, c.rh, c.at, c.w, c.sr, c.p, c.code, c.n.toDouble()).joinToString(",") { v -> if (v == null) "-" else fmtInt(v) }

    private val samples = listOf(
        ClimAvg(22.0, 60.0, 23.0, 5.0, 0.0, 0.0, 0.0, 1), ClimAvg(26.0, 75.0, 29.0, 3.0, 300.0, 0.0, 3.0, 1),
        ClimAvg(28.0, 80.0, 33.0, 3.0, 450.0, 0.4, 3.0, 1), ClimAvg(30.0, 55.0, 31.0, 8.0, 600.0, 0.0, 0.0, 1),
        ClimAvg(34.0, 70.0, 40.0, 2.0, 700.0, 0.0, 0.0, 2), ClimAvg(25.0, 90.0, 27.0, 1.0, 0.0, 3.0, 61.0, 1),
        ClimAvg(27.0, 65.0, 28.0, 6.0, 100.0, 0.0, 95.0, 1), ClimAvg(null, 70.0, 30.0, 4.0, 200.0, 0.0, 0.0, 1),
        ClimAvg(24.5, 50.0, 25.0, 10.0, 150.0, 0.0, 0.0, 1), ClimAvg(31.0, 72.0, 36.0, 2.0, 500.0, 9.0, 80.0, 1),
    )

    private class Ds(val id: String, val now: Long, val profile: Profile) {
        val acts = ArrayList<Act>()
        val wl = ArrayList<Wellness>()
        val vx = HashMap<Long, String>()
        val ctx = LinkedHashMap<Long, ClimCtx>()
    }

    @Test
    fun climateMatchesHtml() {
        val text = javaClass.getResource("/golden_climate.txt")!!.readText()
        val wx = HashMap<String, WxData>()
        var wId = ""; var wT = 0L; var wTimes: List<String> = emptyList(); var wSeries = LinkedHashMap<String, List<Double?>>()
        var cur: Ds? = null
        var sn: Snapshot? = null
        var P = ClimPers(false, 0)
        var cmpDone = 0
        val problems = ArrayList<String>()
        var desc = ""

        fun cmp(what: String, exp: String, got: String) {
            cmpDone++
            if (!eq(exp, got)) problems += "$desc\n    $what:\n      diharapkan: $exp\n      didapat:    $got"
        }

        for (line in text.lines()) {
            if (line.isEmpty() || line.startsWith("#")) continue
            val tag = line.substringBefore(" ")
            val body = if (line.length > tag.length) line.substring(tag.length + 1) else ""
            when (tag) {
                "H" -> { val t = body.split(" "); wId = t[0]; wT = t[1].toLong(); wSeries = LinkedHashMap() }
                "HT" -> wTimes = body.split(",")
                "HS" -> { val t = body.split(" "); wSeries[t[0]] = t[1].split(",").map { if (it == "~") null else it.toDouble() } }
                "HE" -> wx[wId] = WxData(wT, WxHourly(wTimes, wSeries))
                "D" -> {
                    val t = body.split(" ")
                    cur = Ds(t[0], t[1].toLong(), Profile(num(t[2]), num(t[3]), t[4].toInt(), t[5].toInt()))
                    desc = "[${t[0]}]"
                }
                "A" -> {
                    val t = body.split(" ")
                    val start = t[0].toLong()
                    cur!!.acts += Act(
                        start = start, kind = kindOf(t[1]), min = t[2].toDouble(), km = t[3].toDouble(), avgHr = t[4].toDouble(),
                        elev = t[5].toDouble(), lid = "hw$start", name = "Lari", maxHr = t[6].toDouble(), icuId = "hw$start", load = t[7].toDouble(),
                    )
                }
                "W" -> {
                    val t = body.split(" ")
                    cur!!.wl += Wellness(
                        id = t[0], restingHR = num(t[1]), rhrSrc = if (t[1] == "-") "" else "jam", hrv = num(t[2]), hrvSrc = if (t[2] == "-") "" else "jam",
                        hrvSpot = t[3] == "1", sleepSecs = num(t[4]), stress = num(t[5]), ctl = numOrNull(t[6]), atl = numOrNull(t[7]), tsb = numOrNull(t[8]),
                    )
                }
                "X" -> body.split(" ").let { cur!!.vx[it[0].toLong()] = it[1] }
                "K" -> {
                    // K memakai koma sebagai delimiter data; jangan split seluruh baris dengan spasi
                    // karena nilai cls yang sah dapat berupa "panas & lembap".
                    val start = body.substringBefore(" ").toLong()
                    val payload = body.substringAfter(" ", "")
                    if (payload == "na") cur!!.ctx[start] = ClimCtx(na = true)
                    else {
                        val v = payload.split(",")
                        cur!!.ctx[start] = ClimCtx(
                            t = numOrNull(v[0]), rh = numOrNull(v[1]), at = numOrNull(v[2]), w = numOrNull(v[3]), sr = num(v[4]), p = num(v[5]),
                            code = num(v[6]), n = v[7].toInt(), amin = num(v[8]), amax = num(v[9]), cls = v[10], gps = v[11] == "1", ng = v[12].toInt(),
                        )
                    }
                }
                "E" -> sn = Snapshot(cur!!.profile, cur!!.acts, cur!!.wl, cur!!.vx, cur!!.now, zone)
                "P" -> { P = sn!!.clPers(cur!!.ctx); cmp("clPers", body, persLine(P)) }
                "T" -> { val th = clTh(P); cmp("clTh", body, listOf(th.cool, th.hot, th.wn).joinToString(",")) }
                "X2" -> cmp("clPersTxt", body, clPersTxt(P))
                "F" -> {
                    val pr = sn!!.clProfile(cur!!.ctx, P)
                    val s = pr.S.entries.joinToString(";") { (k, v) -> "$k=${v.n},${v.hr},${v.pace}" }.ifEmpty { "-" }
                    val c = pr.cmp.entries.joinToString(";") { (k, v) -> "$k=${v.d},${v.n},${v.conf}" }.ifEmpty { "-" }
                    cmp("clProfile", body, "$s@@$c")
                }
                "C" -> {
                    val i = body.substringBefore("@@").toInt()
                    val c = samples[i]
                    val st = clStat(c, P)
                    cmp("clCls/clStat/clSc[$i]", body.substringAfter("@@"), listOf(clCls(c, P), st.k, st.lb, st.why, clSc(c, P).toString()).joinToString("@@"))
                }
                "S" -> {
                    val head = body.substringBefore("@@").split(" ")
                    val slots = clSlots(wx.getValue(head[0]), head[2].toDouble(), samples[head[1].toInt()], P, cur!!.now, zone)
                    val got = if (slots.isEmpty()) "-" else slots.joinToString("##") { (if (it.better) "1" else "0") + "|" + it.txt }
                    cmp("clSlots ${head[0]} nowC=${head[1]} dur=${head[2]}", body.substringAfter("@@"), got)
                }
                "V" -> {
                    val head = body.substringBefore("@@").split(" ")
                    val o = wx.getValue(head[0])
                    cmp("clAvg ${head[0]} ${head[1]}..${head[2]}", body.substringAfter("@@"), climLine(clAvg(o, head[1].toDouble(), head[2].toDouble(), zone)))
                }
                "Z" -> Unit
            }
        }
        if (problems.isNotEmpty()) fail("${problems.size} selisih dari $cmpDone perbandingan. Contoh pertama:\n" + problems.take(12).joinToString("\n"))
        check(cmpDone > 600) { "Data uji terlalu sedikit: $cmpDone" }
    }
}
