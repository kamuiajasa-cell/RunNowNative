package com.runnow.domain

import java.time.Instant
import kotlin.math.max
import kotlin.math.min

fun Snapshot.restHr(): Double = if (profile.rest > 0) profile.rest else 0.0

fun Snapshot.maxHr(): Double = when {
    profile.max > 0 -> profile.max
    profile.age > 0 -> jsRound(208 - 0.7 * profile.age).toDouble()
    else -> 0.0
}

/** Rata-rata RHR 28 hari terakhir dari wellness (min. 5 hari data); null bila kurang. */
fun Snapshot.rhr28(): Rhr28? {
    val cut = Instant.ofEpochMilli(nowMs - 28 * DAY_MS).toString().take(10)
    val v = wellness.filter { it.id >= cut && it.restingHR > 30 }.map { it.restingHR }
    if (v.size < 5) return null
    return Rhr28(avg = jsRound(v.sum() / v.size).toInt(), n = v.size)
}

/** Estimasi Max METs dari lari 56 hari terakhir (HR+pace, atau batas bawah dari pace saja). */
fun Snapshot.estMax(source: List<Act> = acts, at: Long? = null): MaxEstimate {
    val now = at?.takeIf { it != 0L } ?: nowMs
    val runs = source.filter { x ->
        x.kind == ActKind.RUN && x.km >= 1.5 && x.min >= 8 && x.start > now - 56 * DAY_MS &&
            x.min / x.km >= 3 && x.min / x.km <= 14
    }
    val rest = restHr()
    val mx = maxHr()
    val rng = mx - rest

    var hrr: Double? = null
    var n = 0
    if (rest > 0 && rng >= 40) {
        val pts = ArrayList<Pair<Double, Double>>() // (m, bobot)
        for (x in runs) {
            if (x.min < 10 || !(x.avgHr > 0)) continue
            val f = (x.avgHr - rest) / rng
            if (f < 0.45 || f > 1.02) continue
            pts += (1 + (metsOf(x.min / x.km) - 1) / min(1.0, f)) to f * f
        }
        if (pts.size >= 3) {
            val sorted = pts.sortedBy { it.first }
            val tw = sorted.sumOf { it.second }
            var acc = 0.0
            var med = sorted[0].first
            for (p in sorted) {
                acc += p.second
                if (acc >= tw / 2) { med = p.first; break }
            }
            hrr = med
            n = sorted.size
        }
    }

    var perf: Double? = null
    if (runs.size >= 3) {
        var best = 0.0
        for (x in runs) {
            val m = metsOf(x.min / x.km) / pctAt(x.min)
            if (m > best) best = m
        }
        if (best > 0) perf = best
    }

    fun cl(v: Double) = clamp(v, 4.0, 25.0)
    var m: Double? = null
    var src = ""
    var conf = ""
    if (hrr != null) {
        m = cl(max(hrr, if (perf != null) 0.9 * perf else 0.0))
        src = "HR+pace dari $n lari (56 hari)"
        conf = if (n >= 6) "baik" else "sedang"
    } else if (perf != null) {
        m = cl(0.9 * perf)
        src = "batas bawah performa pace"
        conf = "rendah"
    }
    return MaxEstimate(m = m, src = src, n = n, conf = conf, perf = perf, hrr = hrr)
}

/**
 * Tahap 1..4 dari Max METs, dengan histeresis (+-0,3 METs) di batas tahap 1/2 dan 2/3.
 * Berbeda dari HTML: tidak mengubah state; simpan [Stage.afS] ke Profile.afS sendiri.
 */
fun Snapshot.stage(): Stage {
    val e = estMax()
    val mm = e.m
    if (mm == null) {
        val s = if (acts.any { it.kind == ActKind.RUN }) 4 else 2
        return Stage(s = s, m = null, e = e, raw = null, unknown = true, afS = profile.afS)
    }
    val raw = when {
        mm < 7.9 -> 1
        mm < 9.2 -> 2
        mm < 12 -> 3
        else -> 4
    }
    var s = raw
    val prev = profile.afS
    if (prev != 0 && prev != raw && kotlin.math.abs(prev - raw) == 1) {
        val up = raw > prev
        val lo = min(prev, raw)
        if (lo < 3) {
            val b = if (lo == 1) 7.9 else 9.2
            if (if (up) mm < b + 0.3 else mm > b - 0.3) s = prev
        }
    }
    return Stage(s = s, m = mm, e = e, raw = raw, unknown = false, afS = s)
}

/** Quality hanya boleh dari tahap 3 ke atas. */
fun Snapshot.qualityEligible(): Boolean = stage().s > 2
