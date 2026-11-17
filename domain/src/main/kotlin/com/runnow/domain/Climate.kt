package com.runnow.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Port dari bagian "Climate-aware coach" di runsense.html (fungsi cl*, gPick). Cuaca = konteks, bukan pengukuran.
// Bagian yang menggambar layar (climPaint, climHome, climAct) menunggu Fase C; bagian jaringan ada di modul :data.

/** Lokasi untuk cuaca (CL.loc di HTML). [manual] = dipilih lewat nama kota. */
data class GeoLoc(
    val lat: Double,
    val lon: Double,
    val name: String = "",
    val manual: Boolean = false,
    val t: Long = 0L,
)

/** Variabel per jam dari Open-Meteo: [time] teks ISO lokal ("2026-11-15T08:00") dan [series] per nama variabel. */
data class WxHourly(val time: List<String>, val series: Map<String, List<Double?>>)

/** Satu respons cuaca (+ waktu simpan [t]); setara `{t, h, src}` di HTML. */
data class WxData(val t: Long, val h: WxHourly, val src: String = "Open-Meteo")

/** Nilai cuaca yang dibaca klasifikasi: rata-rata untuk satu jendela waktu atau konteks tersimpan sebuah lari. */
interface ClimVals {
    val t: Double?
    val rh: Double?
    val at: Double?
    val sr: Double
    val p: Double
    val code: Double
}

/** Hasil clAvg: rata-rata (p dan code = maksimum) selama jendela waktu. */
data class ClimAvg(
    override val t: Double?,
    override val rh: Double?,
    override val at: Double?,
    val w: Double?,
    override val sr: Double,
    override val p: Double,
    override val code: Double,
    val n: Int,
) : ClimVals

/** Konteks cuaca tersimpan untuk satu lari (CL.ctx[start]). [na] = data historis tidak tersedia. */
data class ClimCtx(
    override val t: Double? = null,
    override val rh: Double? = null,
    override val at: Double? = null,
    val w: Double? = null,
    override val sr: Double = 0.0,
    override val p: Double = 0.0,
    override val code: Double = 0.0,
    val n: Int = 0,
    val amin: Double = 0.0,
    val amax: Double = 0.0,
    val cls: String = "",
    val src: String = "",
    val at0: Long = 0L,
    val gps: Boolean = false,
    val ng: Int = 0,
    val q: String = "",
    val na: Boolean = false,
) : ClimVals

/** Titik GPS ringkas: detik sejak start, lat, lon (2 desimal). */
data class GpsPoint(val sec: Double, val lat: Double, val lon: Double)

/** CL.gps[start]: [none] = tidak ada GPS, selain itu <= 5 titik. */
data class GpsPick(val none: Boolean = false, val p: List<GpsPoint> = emptyList()) {
    val hasPoints: Boolean get() = p.isNotEmpty()
}

data class ClimBin(val lo: Double, val hi: Double, val n: Int, val med: Double, val c: Double)

/** Ambang pribadi (clPers). Bila [ok] false, [reason] menjelaskan kenapa belum cukup. */
data class ClimPers(
    val ok: Boolean,
    val n: Int,
    val cand: Int = 0,
    val reason: String = "",
    val opt: Double = 0.0,
    val optLo: Double = 0.0,
    val cool: Int? = null,
    val hot: Int? = null,
    val bins: List<ClimBin> = emptyList(),
    val conf: String = "",
)

data class ClimTh(val cool: Double, val hot: Double, val wn: Double)

data class ClimStatus(val k: String, val lb: String, val why: String)

data class ClimStat(val n: Int, val hr: Double, val pace: Double)

data class ClimCmp(val d: Int, val n: Int, val conf: String)

data class ClimProfile(val S: Map<String, ClimStat>, val cmp: Map<String, ClimCmp>)

data class ClimSlot(val better: Boolean, val txt: String)

internal const val CLIM_HV =
    "temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,precipitation,shortwave_radiation,cloud_cover,weather_code"

/** `+new Date(s)` untuk teks jam Open-Meteo (waktu lokal tanpa zona, dibaca di zona perangkat). */
internal fun wxMs(s: String, zone: ZoneId): Double =
    try {
        LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli().toDouble()
    } catch (e: Exception) {
        Double.NaN
    }

/** cD() di HTML: tanggal lokal "yyyy-MM-dd". */
fun climDate(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()

/** cH() di HTML: jam lokal "08.05". */
fun climHour(ms: Long, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    return t.hour.toString().padStart(2, '0') + "." + t.minute.toString().padStart(2, '0')
}

/** Rata-rata variabel selama [from, to]; jam yang memuat [from] ikut dihitung. */
fun clAvg(o: WxData?, from: Double, to: Double, zone: ZoneId): ClimAvg? {
    if (o == null) return null
    val times = o.h.time.map { wxMs(it, zone) }
    val ix = ArrayList<Int>()
    times.forEachIndexed { i, m -> if (m > from - 3600e3 && m <= to) ix += i }
    if (ix.isEmpty()) return null
    fun mean(k: String): Double? {
        val arr = o.h.series[k]
        val v = ix.mapNotNull { arr?.getOrNull(it) }
        return if (v.isNotEmpty()) v.fold(0.0) { a, b -> a + b } / v.size else null
    }
    fun maxOf0(k: String): Double {
        val arr = o.h.series[k]
        var m = 0.0
        for (i in ix) m = max(m, arr?.getOrNull(i) ?: 0.0)
        return m
    }
    val tt = mean("temperature_2m")
    val at = mean("apparent_temperature")
    val c = ClimAvg(
        t = tt, rh = mean("relative_humidity_2m"), at = at, w = mean("wind_speed_10m"),
        sr = mean("shortwave_radiation") ?: 0.0, p = maxOf0("precipitation"), code = maxOf0("weather_code"), n = ix.size,
    )
    return if (c.t == null && c.at == null) null else c
}

/** Suhu efektif: suhu udara asli + beban matahari; kelembapan dipisah di clCls. */
fun clV(c: ClimVals): Double = (c.t ?: c.at ?: Double.NaN) + c.sr / 150

/** Ambang: default 25/27/29, diganti ambang pribadi bila riwayat cukup. Batas keselamatan (33/38) tidak dipersonalisasi. */
fun clTh(P: ClimPers): ClimTh {
    val cool = if (P.ok && P.cool != null) P.cool.toDouble() else 25.0
    val hot = if (P.ok && P.hot != null) P.hot.toDouble() else 29.0
    val wn = if (P.ok && P.cool != null) (if (P.hot != null) (cool + hot) / 2 else cool + 2) else 27.0
    return ClimTh(cool, hot, wn)
}

fun clCls(c: ClimVals, P: ClimPers): String {
    val v = clV(c)
    val th = clTh(P)
    return if (v < th.cool) "sejuk" else if (v < th.hot) "hangat" else if ((c.rh ?: Double.NaN) >= 70) "panas & lembap" else "panas"
}

/** Kelas selalu dihitung ulang dengan ambang terbaru; konteks tanpa t/rh memakai kelas tersimpan. */
fun clK(c: ClimCtx, P: ClimPers): String = if (c.t != null && c.rh != null) clCls(c, P) else c.cls

/**
 * Ambang pribadi: efisiensi easy run (meter per detak) dibanding easy run lain +-28 hari, dikelompokkan per 2 poin
 * suhu efektif. Bin paling efisien = optimum; ambang = ujung atas bin yang efisiensinya masih dalam 1,5 / 4 poin
 * persen dari optimum.
 */
fun Snapshot.clPers(ctx: Map<Long, ClimCtx>): ClimPers {
    class Row(val ms: Long, val v: Double, val e: Double)
    class Rr(val v: Double, val r: Double)
    val stg = stage()
    val rows = ArrayList<Row>()
    for (a in acts) {
        val c = ctx[a.start]
        if (c == null || c.na || c.t == null || a.kind != ActKind.RUN || !(a.avgHr > 0) || !(a.km >= 2) || !(a.min > 0)) continue
        if (detectType(a, stg).type != "easy") continue
        rows += Row(a.start, clV(c), (a.km * 1000 / a.min) / a.avgHr)
    }
    val rr = ArrayList<Rr>()
    for (r in rows) {
        val nb = rows.filter { it !== r && abs((it.ms - r.ms).toDouble()) <= 28 * 864e5 }
        if (nb.size >= 4) rr += Rr(r.v, r.e / medianOf(nb.map { it.e }) - 1)
    }
    var reason = "baru " + rr.size + " easy run yang bisa dipakai (butuh minimal 12, tersebar di ≥ 3 rentang suhu)"
    if (rr.size >= 12) {
        val vs = rr.map { it.v }.sorted()
        val med = medianOf(vs)
        val bulk = vs.filter { abs(it - med) <= 1.5 }
        val oth = vs.filter { abs(it - med) > 1.5 }
        reason = bulk.size.toString() + " dari " + rr.size + " easy run ada di suhu efektif " + jsRound(med - 1.5) + "–" + jsRound(med + 1.5) + "°C" +
            (if (oth.isNotEmpty()) "; " + oth.size + " sisanya tersebar di " + jsRound(oth[0]) + "–" + jsRound(oth[oth.size - 1]) +
                "°C, belum ada rentang lain dengan ≥ 3 lari" else "") +
            ". Perlu ≥ 3 rentang suhu dengan ≥ 3 lari tiap rentang"
        // Kelompok dibentuk dari data: urut menurut suhu, tutup kelompok bila sudah >= 3 lari dan lebar >= span.
        fun mk(span: Double): List<ClimBin> {
            val so = rr.sortedBy { it.v }
            val gs = ArrayList<List<Rr>>()
            var cur = ArrayList<Rr>()
            for (x in so) {
                cur.add(x)
                if (cur.size >= 3 && x.v - cur[0].v >= span) {
                    gs.add(cur)
                    cur = ArrayList()
                }
            }
            if (cur.isNotEmpty()) {
                if (cur.size >= 3 || gs.isEmpty()) gs.add(cur) else gs[gs.size - 1] = gs[gs.size - 1] + cur
            }
            return gs.map { g -> ClimBin(g[0].v, g[g.size - 1].v, g.size, medianOf(g.map { it.r }) * 100, (g[0].v + g[g.size - 1].v) / 2) }
        }
        for (span in listOf(2.0, 1.0)) {
            val bins = mk(span)
            if (bins.size < 3) continue
            var bi = 0
            bins.forEachIndexed { i, b -> if (b.med > bins[bi].med) bi = i }
            val best = bins[bi]
            var cool = best.hi
            var hot = best.hi
            var sawDrop = false
            var found = false
            for (k in bi + 1 until bins.size) {
                val d = best.med - bins[k].med
                if (d <= 1.5 && !sawDrop) {
                    cool = bins[k].hi
                    hot = cool
                } else if (d <= 4) {
                    sawDrop = true
                    hot = bins[k].hi
                } else {
                    sawDrop = true
                    found = true
                    break
                }
            }
            val cv = jsRound(max(20.0, min(30.0, cool))).toInt()
            return ClimPers(
                ok = true, n = rr.size, opt = jsRound(best.c * 10) / 10.0, optLo = best.lo,
                cool = if (sawDrop) cv else null,
                hot = if (found) jsRound(max((cv + 2).toDouble(), min(34.0, hot))).toInt() else null,
                bins = bins, conf = if (rr.size < 20) "awal" else if (rr.size < 40) "cukup" else "matang",
            )
        }
    }
    return ClimPers(ok = false, n = rr.size, cand = rows.size, reason = reason)
}

/** Skor kenyamanan: makin kecil makin baik (jarak dari suhu optimum pribadi, kelebihan panas dihitung penuh). */
fun clSc(c: ClimVals, P: ClimPers): Double {
    val v = clV(c)
    return if (P.ok) (if (v < P.opt) (P.opt - v) * 0.5 else v - P.opt) else v
}

fun clPersTxt(P: ClimPers): String {
    if (!P.ok) return "Ambang masih default (25° / 29°): " + P.reason + "."
    return "Ambang pribadi dari " + P.n + " easy run (" + P.conf + "): paling efisien ±" + jsRound(P.opt) + "°C · nyaman < " +
        (if (P.cool != null) P.cool.toString() + "°C" else "25°C (default)") + " · berat ≥ " +
        (if (P.hot != null) P.hot.toString() + "°C" else "29°C (default)") + ". Suhu efektif = suhu udara + efek matahari."
}

/** HeatStressEngine: estimasi (bukan WBGT). Petir/hujan lebat menang atas skor. */
fun clStat(c: ClimVals, P: ClimPers): ClimStatus {
    val v = clV(c)
    if (c.code >= 95 || c.p >= 8) return ClimStatus("bad", "Berisiko", "ada petir atau hujan sangat lebat")
    if (v >= 38) return ClimStatus("bad", "Berisiko", "tekanan panas estimasi sangat tinggi")
    if (c.p >= 2 || v >= 33) return ClimStatus("bad", "Kurang disarankan", if (c.p >= 2) "hujan cukup deras" else "panas terik")
    val wn = clTh(P).wn
    if (v >= wn || c.p >= 0.3) return ClimStatus("wn", "Perlu penyesuaian", if (v >= wn) "hangat/panas" else "gerimis")
    return ClimStatus("ok", "Nyaman", "kondisi relatif mendukung")
}

/** PersonalClimateProfile: easy run saja, bandingkan HR ke baseline sejuk pada pace sebanding (median). */
fun Snapshot.clProfile(ctx: Map<Long, ClimCtx>, P: ClimPers): ClimProfile {
    class Pt(val hr: Double, val pace: Double)
    val stg = stage()
    val g = LinkedHashMap<String, ArrayList<Pt>>()
    for (a in acts) {
        val c = ctx[a.start]
        if (c == null) continue
        val k = clK(c, P)
        if (k.isEmpty() || a.kind != ActKind.RUN || !(a.avgHr > 0) || a.km < 2) continue
        if (detectType(a, stg).type == "easy") g.getOrPut(k) { ArrayList() }.add(Pt(a.avgHr, a.min / a.km))
    }
    val s = LinkedHashMap<String, ClimStat>()
    for ((k, v) in g) s[k] = ClimStat(v.size, medianOf(v.map { it.hr }), medianOf(v.map { it.pace }))
    val cmp = LinkedHashMap<String, ClimCmp>()
    val b = s["sejuk"]
    if (b != null && b.n >= 3) {
        for ((k, sv) in s) {
            if (k == "sejuk" || sv.n < 3 || abs(sv.pace - b.pace) > 0.5) continue
            val n = min(sv.n, b.n)
            cmp[k] = ClimCmp(jsRound(sv.hr - b.hr).toInt(), n, if (n < 6) "mulai terbaca" else if (n < 12) "cukup konsisten" else "semakin matang")
        }
    }
    return ClimProfile(s, cmp)
}

/** gOK() di HTML: pasangan lat/lon yang masuk akal (bukan 0,0). */
fun gOK(a: Double?, b: Double?): Boolean =
    a != null && b != null && a.isFinite() && b.isFinite() && (a != 0.0 || b != 0.0) && abs(a) <= 90 && abs(b) <= 180

private fun toFixed2(x: Double): Double = BigDecimal(x).setScale(2, RoundingMode.HALF_UP).toDouble()

/**
 * GPS aktivitas: simpan <= 5 titik (detik sejak start, lat, lon; 2 desimal) per lari, bukan seluruh rute.
 * [t] boleh null (waktu tidak ada): detik diperkirakan dari urutan titik dan [durSec].
 */
fun gPick(t: List<Double?>?, la: List<Double?>, lo: List<Double?>, durSec: Double): GpsPick {
    val ix = ArrayList<Int>()
    for (i in la.indices) if (gOK(la[i], lo.getOrNull(i))) ix += i
    if (ix.size < 2) return GpsPick(none = true)
    val n = min(5, ix.size)
    val p = ArrayList<GpsPoint>()
    for (k in 0 until n) {
        val i = ix[jsRound((k * (ix.size - 1)).toDouble() / (if (n - 1 == 0) 1 else n - 1)).toInt()]
        val ti = t?.getOrNull(i)
        val t0 = t?.getOrNull(0)
        val base = if (t0 == null || t0.isNaN() || t0 == 0.0) 0.0 else t0
        val sec = if (ti != null) max(0.0, ti - base) else jsRound((i.toDouble() / la.size) * durSec).toDouble()
        p += GpsPoint(sec, toFixed2(la[i]!!), toFixed2(lo[i]!!))
    }
    return GpsPick(p = p)
}

/** clGpsPts() di HTML: titik data jam [detik, hr, m/s, tinggi, kadens, lat, lon]. Nilai hilang = NaN. */
fun gpsFromWatchPoints(pts: List<DoubleArray>, durSec: Double): GpsPick =
    gPick(
        pts.map { it.getOrElse(0) { Double.NaN } },
        pts.map { it.getOrElse(5) { Double.NaN } },
        pts.map { it.getOrElse(6) { Double.NaN } },
        durSec,
    )

/**
 * Konteks cuaca satu lari dari rata-rata tiap segmen ([cs]) di titik GPS (atau lokasi terakhir bila tanpa GPS).
 * Kosong -> [ClimCtx.na]. Sama dengan bagian akhir clMatch. Nilai null di segmen dihitung 0, seperti penjumlahan di JS.
 */
fun climCtxOf(cs: List<ClimAvg>, P: ClimPers, nowMs: Long, usedGps: Boolean, nPts: Int): ClimCtx {
    if (cs.isEmpty()) return ClimCtx(na = true)
    fun mean(f: (ClimAvg) -> Double?): Double = cs.fold(0.0) { s, c -> s + (f(c) ?: 0.0) } / cs.size
    val base = ClimCtx(
        t = mean { it.t }, rh = mean { it.rh }, at = mean { it.at }, w = mean { it.w }, sr = mean { it.sr },
        p = cs.maxOf { it.p }, code = cs.maxOf { it.code }, n = cs.size,
        amin = cs.minOf { it.at ?: 0.0 }, amax = cs.maxOf { it.at ?: 0.0 },
    )
    return base.copy(
        cls = clCls(base, P), src = "Open-Meteo", at0 = nowMs, gps = usedGps, ng = nPts,
        q = if (usedGps) "titik GPS aktivitas ($nPts titik)" else "lokasi pengguna terakhir (perkiraan, tanpa GPS aktivitas)",
    )
}

/** WeatherAwareRecommendation: 1-2 jam lari terbaik dalam 40 jam ke depan (jam 05-18), dibanding kondisi sekarang. */
fun clSlots(o: WxData, dur: Double, nowC: ClimVals, P: ClimPers, now: Long, zone: ZoneId): List<ClimSlot> {
    class Cand(val m: Long, val c: ClimAvg, val s: Double)
    val out = ArrayList<Cand>()
    for (ms in o.h.time.map { wxMs(it, zone) }) {
        if (ms.isNaN()) continue
        val m = ms.toLong()
        val hr = Instant.ofEpochMilli(m).atZone(zone).hour
        if (m <= now || m > now + 40 * 3600e3 || hr < 5 || hr > 18) continue
        val c = clAvg(o, m.toDouble(), m + dur * 60e3, zone) ?: continue
        out += Cand(m, c, clSc(c, P) + (if (c.p >= 0.3) 6 else 0) + (if (c.p >= 2) 10 else 0) + (if (c.code >= 95) 50 else 0))
    }
    val sorted = out.sortedBy { it.s }
    val pick = ArrayList<Cand>()
    if (sorted.isNotEmpty()) pick += sorted[0]
    val s2 = sorted.firstOrNull { pick.isNotEmpty() && abs((it.m - pick[0].m).toDouble()) >= 2 * 3600e3 }
    if (s2 != null) pick += s2
    val base = clSc(nowC, P) + (if (nowC.p >= 0.3) 6 else 0)
    return pick.map { x ->
        val tx = x.c.t
        val ax = x.c.at
        val why = ArrayList<String>()
        why += "suhu " + jsRound(tx ?: ax ?: Double.NaN) + "°C" + (if (ax != null && tx != null) " (terasa " + jsRound(ax) + "°C)" else "")
        val nt = nowC.t
        if (nt != null && tx != null && nt - tx >= 2) why += "lebih sejuk " + jsRound(nt - tx) + "°C dari sekarang"
        if (nowC.sr - x.c.sr >= 150) why += "matahari lebih lemah"
        why += if (x.c.p < 0.3) "kering" else "ada hujan"
        val dd = (LocalDate.parse(climDate(x.m, zone)).toEpochDay() - LocalDate.parse(climDate(now, zone)).toEpochDay()).toInt()
        ClimSlot(
            better = x.s <= base - 1.5,
            txt = (if (dd == 0) "Hari ini " else if (dd == 1) "Besok " else "") + climHour(x.m, zone) + " · " + why.joinToString(", "),
        )
    }
}
