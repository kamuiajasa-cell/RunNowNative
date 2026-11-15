package com.runnow.domain

import kotlin.math.max

data class StreamFast(val p12: Double, val med: Double, val pct: Int)

data class StreamFade(val pct: Int)

data class StreamDec(val pct: Int)

/**
 * Ringkasan stream satu lari (state.sx[id] di HTML). [none] = stream ada tetapi tidak ada temuan, atau stream
 * memang tidak tersedia (di HTML: `{none: 1}`). Bidang lain null bila temuannya tidak muncul.
 */
data class StreamSummary(
    val fast: StreamFast? = null,
    val fade: StreamFade? = null,
    val dec: StreamDec? = null,
    /** Persen waktu di Z1..Z5. */
    val tiz: List<Int>? = null,
    val none: Boolean = false,
) {
    companion object {
        val NONE = StreamSummary(none = true)
    }
}

/** Stream mentah: detik, meter kumulatif, dan HR (null bila tidak ada). Ketiganya harus sama panjang. */
data class StreamData(
    val time: List<Double>,
    val distance: List<Double>,
    val heartrate: List<Double>? = null,
)

/** Setara kebenaran (truthy) JS untuk angka: null, 0, dan NaN dianggap salah. */
private fun truthy(x: Double?): Boolean = x != null && x != 0.0 && !x.isNaN()

/**
 * summarizeStream() di HTML: start kencang (km 1-2), melambat di akhir, decoupling HR/pace, dan sebaran zona HR.
 * Mengembalikan null bila data terlalu pendek ([minN], default 300 titik) atau panjang time dan distance tidak sama.
 * Pengurai JSON stream Intervals.icu (bentuk array `{type, data}` atau objek) ada di lapisan data (Langkah 6).
 */
fun Snapshot.summarizeStream(raw: StreamData, minN: Int = 300): StreamSummary? {
    val time = raw.time
    val dist = raw.distance
    val hr = raw.heartrate
    val need = if (minN == 0) 300 else minN
    if (time.size < need || time.size != dist.size) return null

    fun mean(x: List<Double>): Double = x.sum() / x.size

    // split per km
    val sp = ArrayList<Double>()
    var k = 1
    var tP = time[0]
    var dP = dist[0]
    for (i in 1 until time.size) {
        if (dist[i] - dist[0] >= k * 1000.0) {
            val dk = (dist[i] - dP) / 1000
            if (dk > 0) sp += (time[i] - tP) / 60 / dk
            tP = time[i]
            dP = dist[i]
            k++
        }
    }

    var fast: StreamFast? = null
    var fade: StreamFade? = null
    var dec: StreamDec? = null
    var tiz: List<Int>? = null

    if (sp.size >= 4) {
        val p12 = (sp[0] + sp[1]) / 2
        val md = medianOf(sp.subList(2, sp.size))
        val pct = jsRound(((md - p12) / md) * 100)
        if (!md.isNaN() && pct >= 6) fast = StreamFast(p12, md, pct.toInt())
    }
    if (sp.size >= 6) {
        val n3 = sp.size / 3
        val mid = mean(sp.subList(n3, 2 * n3))
        val last = mean(sp.subList(sp.size - n3, sp.size))
        val pct = jsRound(((last - mid) / mid) * 100)
        if (pct >= 5) fade = StreamFade(pct.toInt())
    }

    val total = time[time.size - 1] - time[0]
    if (hr != null && hr.size == time.size) {
        if (total >= 2400) {
            val t0 = time[0] + total * 0.1
            val tm = time[0] + total * 0.55
            fun ef(lo: Double, hi: Double): Double? {
                var i0 = -1
                var i1 = -1
                for (i in time.indices) {
                    if (time[i] >= lo && i0 < 0) i0 = i
                    if (time[i] <= hi) i1 = i
                }
                if (i0 < 0 || i1 <= i0) return null
                val h = hr.subList(i0, i1 + 1).filter { it > 40 }
                val dt = time[i1] - time[i0]
                return if (h.isNotEmpty() && dt > 0) ((dist[i1] - dist[i0]) / dt) / mean(h) else null
            }
            val e1 = ef(t0, tm)
            val e2 = ef(tm, time[time.size - 1])
            if (truthy(e1) && truthy(e2)) {
                dec = StreamDec(jsRound(((e1!! - e2!!) / e1) * 100).toInt())
            }
        }
        val mx = maxHr()
        val rs = restHr()
        if (mx > rs && rs > 0) {
            val z = IntArray(5)
            var c = 0
            for (v in hr) {
                if (!(v > 40)) continue
                val r = (v - rs) / (mx - rs)
                z[if (r < 0.6) 0 else if (r < 0.7) 1 else if (r < 0.8) 2 else if (r < 0.9) 3 else 4]++
                c++
            }
            if (c > 120) tiz = z.map { jsRound((it.toDouble() / c) * 100).toInt() }
        }
    }

    return if (fast == null && fade == null && dec == null && tiz == null) StreamSummary.NONE
    else StreamSummary(fast = fast, fade = fade, dec = dec, tiz = tiz)
}

/**
 * hwSummary() di HTML: ringkasan dari titik data jam. Tiap titik = [detik, hr, m/s, ...] (indeks 0..2 dipakai),
 * sudah dijarangkan <= 300 titik. Nilai yang hilang (panjang larik < 3) diperlakukan seperti NaN di JS.
 */
fun Snapshot.hwSummary(pts: List<DoubleArray>): StreamSummary? {
    if (pts.size < 40) return null
    val time = ArrayList<Double>()
    val dist = ArrayList<Double>()
    val hr = ArrayList<Double>()
    var d = 0.0
    for (p in pts) {
        val t = p.getOrElse(0) { Double.NaN }
        if (!t.isFinite()) continue
        if (time.isNotEmpty()) {
            val dt = t - time[time.size - 1]
            if (dt <= 0) continue
            val v = p.getOrElse(2) { Double.NaN }
            d += max(0.0, if (v.isNaN()) 0.0 else v) * dt
        }
        time += t
        dist += d
        val h = p.getOrElse(1) { Double.NaN }
        hr += if (h.isNaN()) 0.0 else h
    }
    return summarizeStream(StreamData(time, dist, hr), 40)
}
